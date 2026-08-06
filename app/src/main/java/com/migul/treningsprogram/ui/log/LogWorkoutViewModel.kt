package com.migul.treningsprogram.ui.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.migul.treningsprogram.data.MuscleClassifier
import com.migul.treningsprogram.data.db.AppDatabase
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.migul.treningsprogram.data.db.entity.PlannedExercise
import com.migul.treningsprogram.data.db.entity.WorkoutSet
import com.migul.treningsprogram.data.preferences.DailyChallengeManager
import com.migul.treningsprogram.data.preferences.PreferencesManager
import com.migul.treningsprogram.data.repository.GamificationRepository
import com.migul.treningsprogram.data.repository.WorkoutRepository
import com.migul.treningsprogram.data.repository.currentDayOfWeek
import com.migul.treningsprogram.data.repository.thisMonday
import com.migul.treningsprogram.domain.BeatTarget
import com.migul.treningsprogram.domain.model.DailyChallenge
import com.migul.treningsprogram.domain.model.WorkoutResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LogWorkoutViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val gamificationRepository: GamificationRepository,
    private val dailyChallengeManager: DailyChallengeManager,
    private val prefs: PreferencesManager,
    private val gson: Gson,
    private val gymPresetDao: com.migul.treningsprogram.data.db.dao.GymPresetDao,
    // N5: reach detection on completion (no XP — celebration only).
    private val goalRepository: com.migul.treningsprogram.data.repository.GoalRepository
) : ViewModel() {

    private val restTimerFallbackSeconds = 90

    // F4: the plate-calculator profile of the ACTIVE gym preset (bar weight, plate set, loadable
    // dumbbells). Brief 02: null until the preset has ACTUALLY been read — it used to start at the
    // app-wide default (the 50 mm home setup), which meant the per-side readout could describe a
    // different gym's equipment in the first moments after the screen opened. Consumers hide the
    // readout (and skip the plate-aware steps) while it is null, and re-render when it arrives.
    private val _plateProfile = MutableStateFlow<PlateMath.PlateProfile?>(null)
    val plateProfile: StateFlow<PlateMath.PlateProfile?> = _plateProfile.asStateFlow()

    init { refreshPlateProfile() }

    /**
     * Re-reads the ACTIVE gym preset. Called on every resume as well as at construction: the gym
     * can be switched while this screen sits in the back stack, and both the readout and the
     * weight +/− steps must follow the CURRENTLY selected gym, not the one selected at open time.
     */
    fun refreshPlateProfile() {
        viewModelScope.launch {
            _plateProfile.value = PlateMath.PlateProfile.from(
                gymPresetDao.getById(prefs.selectedGymPresetId)
            )
        }
    }

    private val _sessionId = MutableStateFlow<Long?>(null)
    private val _sessionStartMs = MutableStateFlow(0L)
    private val _dayOfWeek = MutableStateFlow(0)
    val workoutDayOfWeek: Int get() = _dayOfWeek.value

    // P2: when > 0, this session is performing ANOTHER day's planned workout "today". The guided plan
    // is loaded from this source day, but the session is attributed to _dayOfWeek (today). On
    // completion the move is committed (source → today logged, today's original discarded, source
    // vacated); abandoning leaves it 0 so the week is never touched.
    private var moveFromDay = 0
    private var moveCommitted = false

    // Item 10: when a move-to-today reopened today's already-logged session to APPEND into it,
    // [isReopenedAppend] is true and [appendBaseDurationMin] holds the duration already logged, so
    // completion sums both segments and abandon RESTORES (never deletes) the original session.
    private var isReopenedAppend = false
    private var appendBaseDurationMin = 0

    /** P2: true exactly once after completeWorkout committed a day-move — consumed by the fragment to
     *  ask the Program tab to rebalance the week. */
    fun consumeMoveCommitted(): Boolean = moveCommitted.also { moveCommitted = false }

    @OptIn(ExperimentalCoroutinesApi::class)
    val sets: StateFlow<List<WorkoutSet>> = _sessionId
        .filterNotNull()
        .flatMapLatest { workoutRepository.getSetsForSession(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _guidedPlan = MutableStateFlow<List<PlannedExercise>>(emptyList())
    val guidedPlan: StateFlow<List<PlannedExercise>> = _guidedPlan.asStateFlow()

    // True once loadGuidedPlan() has finished — prevents empty-plan flash from triggering freestyle mode
    private val _planLoaded = MutableStateFlow(false)
    val planLoaded: StateFlow<Boolean> = _planLoaded.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    val currentExercise: StateFlow<PlannedExercise?> =
        combine(_guidedPlan, _currentIndex) { plan, idx ->
            plan.getOrNull(idx)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val setsForCurrentExercise: StateFlow<List<WorkoutSet>> =
        combine(sets, currentExercise) { allSets, exercise ->
            if (exercise == null) emptyList()
            else allSets.filter { it.exerciseName == exercise.exerciseName }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── R7: "beat last time" target chip + live PR flash (preview only — the official award
    //    stays in processWorkoutCompletion; BeatTarget mirrors its exact rule) ─────────────────

    /** The current exercise's number to beat (null = no history = no chip). */
    private val _beatTarget = MutableStateFlow<Float?>(null)
    val beatTarget: StateFlow<Float?> = _beatTarget.asStateFlow()

    /** One event per NEW in-session best that beats the historical best — drives the chip flare. */
    private val _prFlash = MutableSharedFlow<Float>(extraBufferCapacity = 4)
    val prFlash: SharedFlow<Float> = _prFlash.asSharedFlow()

    /** Historical best per exercise, fetched once per session (excludes this session's sets). */
    private val historicalBest = mutableMapOf<String, Float?>()

    /** Best weight already flashed this session per exercise — re-flash only when beaten. */
    private val sessionFlashed = mutableMapOf<String, Float>()

    private suspend fun historicalBestFor(name: String): Float? {
        val sid = _sessionId.value ?: return null
        return historicalBest.getOrPut(name) { workoutRepository.getPreviousMaxWeight(name, sid) }
    }

    init {
        // Chip value: the CURRENT exercise's historical best, raised live once an in-session
        // working set beats it (the sets flow re-emits after every log). Re-resolves on
        // next/prev/swap so the chip always shows the shown exercise's own history; a
        // first-ever exercise resolves to null → no chip (baselines, never targets).
        viewModelScope.launch {
            combine(currentExercise, sets) { ex, all ->
                ex?.exerciseName?.let { name ->
                    name to all.filter { it.exerciseName == name && !it.isWarmup }
                        .maxOfOrNull { it.weightKg }
                }
            }
                .distinctUntilChanged()
                .collect { current ->
                    _beatTarget.value = current?.let { (name, sessionMax) ->
                        BeatTarget.chipTarget(historicalBestFor(name), sessionMax)
                    }
                }
        }
    }

    /**
     * Called after a set is persisted: fires the inline PR moment when [weight] beats the
     * exercise's historical best AND everything already lifted or flashed this session.
     * [priorSessionMax] (the working-set max logged BEFORE this set) covers resumed sessions
     * whose earlier sets predate this ViewModel — an already-beaten number never re-flashes,
     * keeping the preview in exact agreement with completion's one-PR-per-exercise credit.
     */
    private suspend fun checkPrPreview(
        name: String, weight: Float, isWarmup: Boolean, priorSessionMax: Float?
    ) {
        if (isWarmup) return
        val prev = historicalBestFor(name) ?: return  // first-ever performance: baseline, never a PR
        val alreadyBeaten = listOfNotNull(sessionFlashed[name], priorSessionMax).maxOrNull()
        if (BeatTarget.shouldFlash(weight, false, prev, alreadyBeaten)) {
            sessionFlashed[name] = weight
            _prFlash.emit(weight)
        }
    }

    // Item 5 (rest-UX batch 2026-07): per-exercise timer, wall-clock based. The elapsed value is
    // derived from a persisted "start of the current exercise" timestamp, NOT from a flow-local
    // start — the old flow re-captured "now" whenever it restarted (WhileSubscribed(5000) cancels
    // ~5 s after backgrounding), so minimizing reset the readout to 0:00. The start moment is also
    // persisted (prefs, keyed by sessionId|index) so process death mid-workout resumes with the
    // TRUE elapsed time. Switching exercise still resets: a new index writes a fresh start.
    private val _exerciseStartMs = MutableStateFlow(0L)

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentExerciseElapsedMs: StateFlow<Long> = _exerciseStartMs
        .flatMapLatest { start ->
            if (start == 0L) flowOf(0L)
            else flow {
                while (true) {
                    emit(System.currentTimeMillis() - start)
                    delay(1000)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    init {
        // Resolve the current exercise's start moment whenever (session, index) changes — but only
        // once the plan is loaded, so the transient index-0 emission during resume can't clobber
        // the persisted start of the real resumed exercise.
        viewModelScope.launch {
            combine(_sessionId, _currentIndex, _planLoaded) { sid, idx, loaded ->
                if (sid != null && loaded) sid to idx else null
            }
                .filterNotNull()
                .distinctUntilChanged()
                .collect { (sid, idx) ->
                    val now = System.currentTimeMillis()
                    val (startMs, newState) =
                        resolveExerciseTimerStart(prefs.exerciseTimerState, sid, idx, now)
                    if (newState != prefs.exerciseTimerState) prefs.exerciseTimerState = newState
                    _exerciseStartMs.value = startMs
                }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val elapsedTimeMs: StateFlow<Long> = _sessionStartMs
        .flatMapLatest { startMs ->
            if (startMs == 0L) flowOf(0L)
            else flow {
                while (true) {
                    emit(System.currentTimeMillis() - startMs)
                    delay(1000)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    private val _todayChallenges = MutableStateFlow<List<DailyChallenge>>(emptyList())

    // R4: the live progress readout shares its metric math with the completion award via
    // ChallengeProgress — the preview can no longer disagree with what completion credits.
    @OptIn(ExperimentalCoroutinesApi::class)
    val challengeProgress: StateFlow<String> = sets
        .combine(_todayChallenges) { currentSets, challenges ->
            com.migul.treningsprogram.domain.ChallengeProgress
                .liveLine(challenges, currentSets.filter { !it.isWarmup })
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    private val _workoutResult = MutableStateFlow<WorkoutResult?>(null)
    val workoutResult: StateFlow<WorkoutResult?> = _workoutResult.asStateFlow()

    private val _sessionAbandoned = MutableStateFlow(false)
    val sessionAbandoned: StateFlow<Boolean> = _sessionAbandoned.asStateFlow()

    private val _savedWeights = mutableMapOf<String, Float>()
    private val _savedReps = mutableMapOf<String, Int>()

    /** One entry per exercise the user has typed values into during the active session. */
    private data class ExerciseDraft(val weight: Float? = null, val reps: Int? = null)

    /** Persisted shape: { sessionId: { exerciseName: {weight, reps} } }. */
    private val draftMapType =
        object : TypeToken<MutableMap<String, MutableMap<String, ExerciseDraft>>>() {}.type

    fun saveCurrentExerciseValues(exerciseName: String, weight: Float, reps: Int) {
        _savedWeights[exerciseName] = weight
        _savedReps[exerciseName] = reps
        persistDraft()
    }

    fun getSavedWeight(exerciseName: String): Float? = _savedWeights[exerciseName]
    fun getSavedReps(exerciseName: String): Int? = _savedReps[exerciseName]

    /**
     * Restores entered-but-not-yet-logged weight/reps for [sessionId] from disk so a
     * process kill mid-workout doesn't revert the user's typed values to AI suggestions.
     */
    private fun hydrateDraft(sessionId: Long) {
        _savedWeights.clear()
        _savedReps.clear()
        val raw = prefs.workoutDraftJson
        if (raw.isBlank()) return
        val all: MutableMap<String, MutableMap<String, ExerciseDraft>> =
            runCatching { gson.fromJson<MutableMap<String, MutableMap<String, ExerciseDraft>>>(raw, draftMapType) }
                .getOrNull() ?: return
        val forSession = all[sessionId.toString()] ?: return
        forSession.forEach { (name, draft) ->
            draft.weight?.let { _savedWeights[name] = it }
            draft.reps?.let { _savedReps[name] = it }
        }
    }

    /** Writes the current in-memory drafts to disk under this session's id (replacing it). */
    private fun persistDraft() {
        val sid = _sessionId.value ?: return
        val raw = prefs.workoutDraftJson
        val all: MutableMap<String, MutableMap<String, ExerciseDraft>> =
            if (raw.isBlank()) mutableMapOf()
            else runCatching {
                gson.fromJson<MutableMap<String, MutableMap<String, ExerciseDraft>>>(raw, draftMapType)
            }.getOrNull() ?: mutableMapOf()
        val names = (_savedWeights.keys + _savedReps.keys).toSet()
        val forSession = names.associateWith {
            ExerciseDraft(_savedWeights[it], _savedReps[it])
        }.toMutableMap()
        all[sid.toString()] = forSession
        prefs.workoutDraftJson = gson.toJson(all, draftMapType)
    }

    /** Drops the persisted draft for [sessionId] once the session is finished/abandoned. */
    private fun clearDraft(sessionId: Long) {
        val raw = prefs.workoutDraftJson
        if (raw.isBlank()) return
        val all: MutableMap<String, MutableMap<String, ExerciseDraft>> =
            runCatching {
                gson.fromJson<MutableMap<String, MutableMap<String, ExerciseDraft>>>(raw, draftMapType)
            }.getOrNull() ?: return
        if (all.remove(sessionId.toString()) != null) {
            prefs.workoutDraftJson = if (all.isEmpty()) "" else gson.toJson(all, draftMapType)
        }
    }

    fun loadSession(sessionId: Long, dayOfWeek: Int, moveFromDay: Int = 0) {
        if (_sessionId.value == sessionId) return
        _sessionId.value = sessionId
        hydrateDraft(sessionId)
        val day = if (dayOfWeek > 0) dayOfWeek else currentDayOfWeek()
        _dayOfWeek.value = day
        this.moveFromDay = if (moveFromDay > 0 && moveFromDay != day) moveFromDay else 0
        viewModelScope.launch {
            val session = workoutRepository.getActiveSession()
            _sessionStartMs.value = session?.dateMs ?: System.currentTimeMillis()
            _todayChallenges.value = dailyChallengeManager.getTodayChallenges()
            // P2: perform the SOURCE day's plan when a move is active, but attribute to today (_dayOfWeek).
            loadGuidedPlan(if (this@LogWorkoutViewModel.moveFromDay > 0) this@LogWorkoutViewModel.moveFromDay else day)
        }
    }

    fun resumeSession(dayOfWeek: Int = -1, moveFromDay: Int = 0) {
        if (_sessionId.value != null) return
        viewModelScope.launch {
            val today = currentDayOfWeek()
            val day = if (dayOfWeek > 0) dayOfWeek else today
            val effMoveFrom = if (moveFromDay > 0 && moveFromDay != day) moveFromDay else 0
            this@LogWorkoutViewModel.moveFromDay = effMoveFrom
            // Item 10: a workout performed for ANOTHER day is attributed to today. If today already has
            // a logged workout, reopen it so this session APPENDS into it (one continuous session);
            // otherwise start a fresh session (which replaces today's planned workout on completion).
            val isMoveToToday = resolveMoveSource(effMoveFrom, day, today) > 0
            val start = if (isMoveToToday) {
                workoutRepository.startAppendableTodaySession()
            } else {
                val id = workoutRepository.startSession()
                val session = workoutRepository.getActiveSession()
                WorkoutRepository.SessionStart(
                    id, session?.dateMs ?: System.currentTimeMillis(), 0, reopened = false
                )
            }
            _sessionId.value = start.sessionId
            appendBaseDurationMin = start.baseDurationMin
            isReopenedAppend = start.reopened
            hydrateDraft(start.sessionId)
            _sessionStartMs.value = start.startedAtMs
            _dayOfWeek.value = day
            _todayChallenges.value = dailyChallengeManager.getTodayChallenges()
            // P2: perform the SOURCE day's plan when a move is active, but attribute to today (_dayOfWeek).
            loadGuidedPlan(if (effMoveFrom > 0) effMoveFrom else day)
        }
    }

    private suspend fun loadGuidedPlan(dayOfWeek: Int) {
        val plan = if (dayOfWeek > 0)
            workoutRepository.getPlannedForDay(thisMonday(), dayOfWeek).first()
        else emptyList()
        val sorted = plan.sortedBy { it.orderInDay }
        _guidedPlan.value = sorted
        val sid = _sessionId.value ?: return
        val loggedSets = workoutRepository.getSetsForSessionOnce(sid)
        _currentIndex.value = resumeIndexFor(sorted, loggedSets)
        _planLoaded.value = true
    }

    fun nextExercise() {
        val plan = _guidedPlan.value
        if (_currentIndex.value < plan.size - 1) _currentIndex.value++
    }

    companion object {
        /** Floor for a rest timer's STARTING duration after session adjustments (item 2). */
        const val MIN_REST_START_SECONDS = 15

        /**
         * Item 5: resolves the wall-clock start of the CURRENT exercise from the persisted
         * "sessionId|index|startMs" state. If the persisted state matches this (session, index)
         * and carries a sane timestamp, it is REUSED (survives backgrounding + process death);
         * otherwise a fresh start at [nowMs] is returned together with the new state to persist.
         * Pure so it is unit-testable off-device.
         */
        fun resolveExerciseTimerStart(
            persisted: String,
            sessionId: Long,
            index: Int,
            nowMs: Long
        ): Pair<Long, String> {
            val parts = persisted.split("|")
            if (parts.size == 3) {
                val sid = parts[0].toLongOrNull()
                val idx = parts[1].toIntOrNull()
                val start = parts[2].toLongOrNull()
                if (sid == sessionId && idx == index && start != null && start in 1..nowMs) {
                    return start to persisted
                }
            }
            return nowMs to "$sessionId|$index|$nowMs"
        }

        /**
         * Items 4 + 2: resolves the rest timer's STARTING duration for one set, layering the
         * session-only ±30 s adjustment on top of whichever base source is active.
         *
         * Base: [manual] non-null (manual mode) → the exercise's CATEGORY time; else the AI's
         * per-exercise suggestion [plannedRestSeconds], falling back to [fallbackSeconds] (90 s)
         * when there is no planned exercise (freestyle). [netAdjustmentSeconds] is the net of all
         * +30/−30 presses for THIS exercise THIS session; the result never drops below
         * [MIN_REST_START_SECONDS]. Pure so it is unit-testable off-device.
         */
        fun resolveRestStart(
            exerciseName: String,
            plannedRestSeconds: Int?,
            manual: com.migul.treningsprogram.domain.ManualRestTimes?,
            netAdjustmentSeconds: Int,
            fallbackSeconds: Int = 90
        ): RestStart {
            val base = manual?.restSecondsFor(exerciseName)
                ?: (plannedRestSeconds ?: fallbackSeconds)
            val seconds = (base + netAdjustmentSeconds).coerceAtLeast(MIN_REST_START_SECONDS)
            return RestStart(seconds = seconds, baseSeconds = base, isManualSource = manual != null)
        }

        /**
         * Decides which exercise to land on when resuming a session. Resume to the
         * exercise of the most recently logged set so already-logged work (including
         * sets on exercise 1) stays visible. Falls back to the first exercise with no
         * logged sets, then to 0 — never silently skipping an in-progress exercise the
         * way the old "first un-logged" rule did. Pure so it is unit-testable off-device.
         */
        /**
         * Inserts [added] immediately after [currentIndex] and renumbers orderInDay so the
         * X/Y counter and order-based logic stay consistent (Item 6). Pure + unit-testable.
         */
        fun insertAfter(
            plan: List<PlannedExercise>,
            currentIndex: Int,
            added: PlannedExercise
        ): List<PlannedExercise> {
            val list = plan.toMutableList()
            val insertAt = (currentIndex + 1).coerceIn(0, list.size)
            list.add(insertAt, added)
            return list.mapIndexed { i, ex -> if (ex.orderInDay != i) ex.copy(orderInDay = i) else ex }
        }

        /**
         * B02 — resolves the weight to pre-fill when an exercise becomes current, with NO
         * cross-exercise bleed. Returns the weight to show, or null meaning "clear the field
         * → show empty/BW". The previous exercise's value is never an input here, so a fresh
         * bodyweight exercise (no saved draft, no own history, no AI target) resolves to null
         * → "BW" instead of inheriting whatever was last typed in the field.
         *
         * Priority (highest first):
         *  1. [savedDraftWeight] — value the user typed for THIS exercise but hasn't logged
         *     yet (draft restore must survive — never override it).
         *  2. [ownLastLoggedWeight] — THIS exercise's own most recent logged weight. Preserves
         *     legitimately added weight on bodyweight work (weighted pull-ups/dips). May be
         *     null on the synchronous first pass before the DB lookup completes.
         *  3. [aiTargetWeightKg] — the planned/AI suggestion, used only when > 0.
         *  4. else null → fresh bodyweight default, shown as empty/"BW".
         *
         * Pure so it is unit-testable off-device.
         */
        fun resolveWeightDefault(
            savedDraftWeight: Float?,
            ownLastLoggedWeight: Float?,
            aiTargetWeightKg: Float
        ): Float? = when {
            savedDraftWeight != null      -> savedDraftWeight
            ownLastLoggedWeight != null   -> ownLastLoggedWeight
            aiTargetWeightKg > 0f         -> aiTargetWeightKg
            else                          -> null
        }

        /**
         * Item 10: resolves the "source day" of the auto day-move performed on completion, converging
         * both entry paths. Returns 0 when no move is needed (the workout belongs to today).
         *  • [moveFromDay] > 0  → explicit P2 "Do this workout today" — that day is the source.
         *  • else, if the performed [sessionDay] is a real day other than [today] → implicit move
         *    (the direct "Start Day Workout" path): the performed day is the source.
         *  • else (session already belongs to today, or freestyle with no day) → 0 (no move).
         * Pure so it is unit-testable off-device.
         */
        fun resolveMoveSource(moveFromDay: Int, sessionDay: Int, today: Int): Int = when {
            moveFromDay > 0 -> moveFromDay
            sessionDay in 1..7 && sessionDay != today -> sessionDay
            else -> 0
        }

        /**
         * QoL item 10: which day chip the completion celebration should bounce. A committed
         * "moved" workout is attributed to TODAY (commitDayMove vacates the source day), so
         * celebrating the stored workout day would bounce a chip the move just cleared —
         * celebrate today instead. Non-moved finishes keep the workout's own day, falling
         * back to today when it was never set.
         */
        fun celebrationDay(moveCommitted: Boolean, workoutDay: Int, today: Int): Int =
            if (moveCommitted || workoutDay !in 1..7) today else workoutDay

        fun resumeIndexFor(plan: List<PlannedExercise>, loggedSets: List<WorkoutSet>): Int {
            if (plan.isEmpty()) return 0
            if (loggedSets.isEmpty()) return 0
            val lastLoggedName = loggedSets.maxByOrNull { it.loggedAtMs }?.exerciseName
            val idx = plan.indexOfFirst { it.exerciseName == lastLoggedName }
            if (idx >= 0) return idx
            val loggedNames = loggedSets.map { it.exerciseName }.toSet()
            val firstUnlogged = plan.indexOfFirst { it.exerciseName !in loggedNames }
            return if (firstUnlogged >= 0) firstUnlogged else 0
        }
    }

    fun previousExercise() {
        if (_currentIndex.value > 0) _currentIndex.value--
    }

    /**
     * Quick-access jump (Item 6). Jumping to an already-finished exercise behaves exactly
     * like pressing Back to it — it just becomes the current index, so its logged sets are
     * shown and can be edited/added to. Nothing logged is lost because sets live in the DB
     * keyed by exercise name, independent of the current index.
     */
    fun jumpToExercise(index: Int) {
        val plan = _guidedPlan.value
        if (index in plan.indices) _currentIndex.value = index
    }

    /**
     * Inserts a new exercise into the in-session plan immediately after the current one
     * (Item 6). The rest shift down and orderInDay is renumbered so X/Y stays correct.
     * Added exercises carry no AI target (sets=0, no target reps/weight) — the user logs
     * them freely. A DB-found exercise keeps its [dbId]/[muscleGroup] for image + info;
     * a custom "Add anyway" exercise passes null dbId (placeholder image, no DB info).
     * The change is in-memory only — the saved DB plan is untouched, matching swap behavior.
     */
    fun addExerciseAfterCurrent(name: String, dbId: String?, muscleGroup: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val template = _guidedPlan.value.getOrNull(_currentIndex.value)
        val added = PlannedExercise(
            id = 0L,
            weekStart = template?.weekStart ?: thisMonday(),
            dayOfWeek = template?.dayOfWeek ?: _dayOfWeek.value,
            orderInDay = 0,           // set by renumbering in insertAfter
            exerciseName = trimmed,
            sets = 0,                 // no AI target — set counter shows "Set N", not "of T"
            targetReps = "",          // free logging
            targetWeightKg = 0f,      // shown as "BW"; user enters their own
            notes = "",
            recommendedRestSeconds = template?.recommendedRestSeconds ?: 90,
            exerciseDbId = dbId
        )
        _guidedPlan.value = insertAfter(_guidedPlan.value, _currentIndex.value, added)
    }

    suspend fun searchLocalExercises(query: String) = workoutRepository.searchExercises(query)

    /** Exercise names in this session that already have at least one logged set. */
    fun loggedExerciseNames(): Set<String> = sets.value.map { it.exerciseName }.toSet()

    val isLastExercise: Boolean get() = _currentIndex.value >= _guidedPlan.value.size - 1

    /**
     * Resolves the muscle group to STORE on a [WorkoutSet]. Exact match against the
     * bundled library wins (authoritative); otherwise fall back to name-based
     * classification so swapped progression variants and custom "Add anyway" exercises
     * — none of which are in DEFAULT_EXERCISES — still get a real group instead of "".
     * A blank result is acceptable (genuinely unclassifiable name).
     */
    private fun resolveMuscleGroup(exerciseName: String): String =
        com.migul.treningsprogram.data.MuscleGroupResolver.resolve(exerciseName)

    fun logSet(weight: Float, reps: Int, isWarmup: Boolean, rpeLabel: String) {
        val sid = _sessionId.value ?: return
        val exercise = currentExercise.value ?: return
        viewModelScope.launch {
            val existingSets = sets.value.filter { it.exerciseName == exercise.exerciseName }
            workoutRepository.addSet(
                WorkoutSet(
                    sessionId = sid,
                    exerciseName = exercise.exerciseName,
                    muscleGroup = resolveMuscleGroup(exercise.exerciseName),
                    setNumber = existingSets.size + 1,
                    reps = reps,
                    weightKg = weight,
                    isWarmup = isWarmup,
                    rpeLabel = rpeLabel,
                    loggedAtMs = System.currentTimeMillis()
                )
            )
            // R7: inline PR preview — fires after the set is real (persisted), never before.
            checkPrPreview(
                exercise.exerciseName, weight, isWarmup,
                existingSets.filter { !it.isWarmup }.maxOfOrNull { it.weightKg }
            )
        }
    }

    /**
     * B1: one tap logs the whole suggested warm-up ladder as ordinary warm-up sets for the
     * CURRENT exercise. Inserted sequentially in ONE coroutine so set numbers are computed
     * locally (three parallel logSet() calls could read the same stale sets snapshot and
     * collide on setNumber). No PR preview — warm-ups never flash (BeatTarget rule).
     */
    fun logWarmupRamp(steps: List<WarmupRamp.Step>) {
        val sid = _sessionId.value ?: return
        val exercise = currentExercise.value ?: return
        if (steps.isEmpty()) return
        viewModelScope.launch {
            val existing = sets.value.count { it.exerciseName == exercise.exerciseName }
            steps.forEachIndexed { i, step ->
                workoutRepository.addSet(
                    WorkoutSet(
                        sessionId = sid,
                        exerciseName = exercise.exerciseName,
                        muscleGroup = resolveMuscleGroup(exercise.exerciseName),
                        setNumber = existing + i + 1,
                        reps = step.reps,
                        weightKg = step.weightKg,
                        isWarmup = true,
                        rpeLabel = "",
                        loggedAtMs = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    // ── N7: per-exercise setup note (quiet line + in-place edit) ──────────────────────────────

    /** The CURRENT exercise's persistent setup note (null = none → the line is hidden). */
    private val _setupNote = MutableStateFlow<String?>(null)
    val setupNote: StateFlow<String?> = _setupNote.asStateFlow()

    init {
        // Re-resolve on every current-exercise change (next/prev/jump/SWAP) so a swapped-in
        // exercise shows ITS OWN note, never the replaced exercise's.
        viewModelScope.launch {
            currentExercise.map { it?.exerciseName }.distinctUntilChanged().collect { name ->
                _setupNote.value = name?.let { workoutRepository.getExerciseNote(it)?.note }
            }
        }
    }

    /** Saves (blank = clears) the CURRENT exercise's note and refreshes the quiet line. */
    fun saveSetupNote(text: String) {
        val name = currentExercise.value?.exerciseName ?: return
        viewModelScope.launch {
            workoutRepository.saveExerciseNote(name, text)
            _setupNote.value = text.trim().ifBlank { null }
        }
    }

    fun logFreestyleSet(exerciseName: String, weight: Float, reps: Int, isWarmup: Boolean, rpeLabel: String) {
        val sid = _sessionId.value ?: return
        viewModelScope.launch {
            val existingSets = sets.value.filter { it.exerciseName == exerciseName }
            workoutRepository.addSet(
                WorkoutSet(
                    sessionId = sid,
                    exerciseName = exerciseName,
                    muscleGroup = resolveMuscleGroup(exerciseName),
                    setNumber = existingSets.size + 1,
                    reps = reps,
                    weightKg = weight,
                    isWarmup = isWarmup,
                    rpeLabel = rpeLabel,
                    loggedAtMs = System.currentTimeMillis()
                )
            )
            // R7: freestyle sets earn the same inline PR moment (completion credits them too).
            checkPrPreview(
                exerciseName, weight, isWarmup,
                existingSets.filter { !it.isWarmup }.maxOfOrNull { it.weightKg }
            )
        }
    }

    fun addSet(exerciseName: String, muscleGroup: String, reps: Int, weightKg: Float) {
        val sid = _sessionId.value ?: return
        viewModelScope.launch {
            val existingSets = sets.value.filter { it.exerciseName == exerciseName }
            workoutRepository.addSet(WorkoutSet(
                sessionId = sid, exerciseName = exerciseName,
                muscleGroup = muscleGroup.ifBlank { resolveMuscleGroup(exerciseName) },
                setNumber = existingSets.size + 1, reps = reps, weightKg = weightKg,
                loggedAtMs = System.currentTimeMillis()
            ))
        }
    }

    /**
     * QoL item 01: deletes a misslogged set mid-workout and renumbers the exercise's remaining
     * sets (see [com.migul.treningsprogram.domain.SetRenumbering]). The sets flow re-emits, so
     * the list, counters, ramp visibility and beat targets all refresh on their own. The rest
     * timer is deliberately untouched.
     */
    fun deleteSet(set: WorkoutSet) { viewModelScope.launch { workoutRepository.deleteSetAndRenumber(set) } }

    fun completeWorkout() {
        val sid = _sessionId.value ?: return
        viewModelScope.launch {
            if (sets.value.none { !it.isWarmup }) {
                workoutRepository.deleteSession(sid)
                clearDraft(sid)
                prefs.exerciseTimerState = ""   // Item 5
                moveFromDay = 0   // P2: abandoned (no working sets) → week left unchanged
                _sessionAbandoned.value = true
                return@launch
            }
            val durationMs = System.currentTimeMillis() - _sessionStartMs.value
            // Item 10: on an appended session, add this segment's minutes to what was already logged.
            val totalDurationMin = appendBaseDurationMin + (durationMs / 60_000).toInt()
            workoutRepository.completeSession(sid, totalDurationMin)
            isReopenedAppend = false
            clearDraft(sid)
            prefs.exerciseTimerState = ""   // Item 5: the finished session's timer state is dead
            // Mark planned exercises done so week progress bar is accurate
            val loggedNames = sets.value.filter { !it.isWarmup }.map { it.exerciseName }.toSet()
            // Item 10: a workout can only be performed TODAY (Item 7 day boundary). Determine the
            // effective source/target of a "day move" that converges BOTH entry paths onto the same
            // silent behaviour:
            //  • P2 "Do this workout today" button → explicit moveFromDay (source), attributed to today.
            //  • Direct "Start Day Workout" on another day → _dayOfWeek is that other day; if it isn't
            //    today, we IMPLICITLY move it onto today so the logged session and the week agree
            //    (fixes the old "source day done / today still empty" mismatch) — no button, no prompt.
            val today = currentDayOfWeek()
            val moveSource = resolveMoveSource(moveFromDay, _dayOfWeek.value, today)
            if (moveSource > 0) {
                // COMMIT the move now (only on completion) — the performed day's plan becomes today's
                // logged session, today's original plan is discarded, and the source day is vacated.
                // The caller then rebalances the rest of the week around the now-logged today.
                workoutRepository.commitDayMove(
                    thisMonday(), sourceDay = moveSource, targetDay = today, performedNames = loggedNames
                )
                moveCommitted = true
                moveFromDay = 0
                _dayOfWeek.value = today   // session is attributed to today; Program highlight follows
            } else {
                val plannedToday = workoutRepository.getPlannedForDay(thisMonday(), _dayOfWeek.value).first()
                plannedToday.filter { it.exerciseName in loggedNames && !it.isLogged }
                    .forEach { workoutRepository.updatePlannedExercise(it.copy(isLogged = true)) }
            }
            val result = gamificationRepository.processWorkoutCompletion(sid)
            // N5: goal-reach check AFTER the official completion processing — detectReached
            // promotes ACTIVE→ACHIEVED (once) and the result dialog celebrates it. No XP (A-G1).
            val reachedGoals = goalRepository.detectReached(sid)
            // QoL item 03: on-the-fly kcal estimate for the completion dialog — reads the freshly
            // persisted duration, same estimator as Recap/Stats. Never persisted.
            val estimatedKcal = workoutRepository.estimateSessionCalories(sid)
            _workoutResult.value = result.copy(reachedGoals = reachedGoals, estimatedKcal = estimatedKcal)
        }
    }

    fun clearResult() { _workoutResult.value = null }

    fun abandonSession() {
        val sid = _sessionId.value ?: return
        viewModelScope.launch {
            if (isReopenedAppend) {
                // Item 10: this session is today's already-logged workout, reopened only to append.
                // NEVER delete it (that would destroy the original workout) — restore it to completed.
                workoutRepository.recompleteSession(sid, appendBaseDurationMin)
                isReopenedAppend = false
            } else {
                workoutRepository.deleteSession(sid)
            }
            clearDraft(sid)
            prefs.exerciseTimerState = ""   // Item 5: the abandoned session's timer state is dead
            moveFromDay = 0   // P2: abandoning leaves the week unchanged
            _sessionAbandoned.value = true
        }
    }

    suspend fun getLastSets(exerciseName: String): List<WorkoutSet> =
        workoutRepository.getLastSetsForExercise(exerciseName, _sessionId.value ?: -1)

    // ── Items 4 + 2: rest-timer starting duration ─────────────────────────────────────────────
    // Item 2: net ±30 s adjustment per exercise NAME, for THIS session only. Deliberately
    // in-memory (dies with the ViewModel / on process death) — the user chose "never persisted".
    private val sessionRestAdjustments = mutableMapOf<String, Int>()

    /** Item 2: called by the rest sheet on every +30/−30 press so the NEXT set starts adjusted. */
    fun recordRestAdjustment(exerciseName: String, deltaSeconds: Int) {
        val key = exerciseName.trim()
        sessionRestAdjustments[key] = (sessionRestAdjustments[key] ?: 0) + deltaSeconds
    }

    /**
     * The rest timer's starting duration + honest source label data for the next set.
     * [freestyleName] is the typed exercise name when logging freestyle (no planned exercise).
     */
    fun getRestStart(freestyleName: String? = null): RestStart {
        val planned = currentExercise.value
        val name = freestyleName?.trim()?.takeIf { it.isNotBlank() } ?: planned?.exerciseName ?: ""
        return resolveRestStart(
            exerciseName = name,
            plannedRestSeconds = planned?.recommendedRestSeconds,
            manual = prefs.manualRestTimes,
            netAdjustmentSeconds = sessionRestAdjustments[name] ?: 0,
            fallbackSeconds = restTimerFallbackSeconds
        )
    }

    /**
     * Replaces the exercise at the current position with [newName] for this session only.
     * The DB plan is not modified — the swap is in-memory only.
     *
     * Item 2: this must replace EVERYTHING tied to the old exercise — not just the name.
     * The image and explanation/DB info are driven by [PlannedExercise.exerciseDbId], the
     * coaching note by [PlannedExercise.notes], and the prescription by sets/targetReps/
     * targetWeightKg. We resolve the new variant's dbId and reset the note + prescription
     * so no field still shows the previous exercise's data. Calisthenics variants are
     * bodyweight, so the new prescription is a fresh bodyweight target (BW, default reps),
     * keeping the set count from the slot.
     */
    fun swapCurrentExercise(exercise: PlannedExercise, newName: String) {
        val idx = _currentIndex.value
        // Clear saved entry values for the old exercise so the new one gets fresh defaults.
        _savedWeights.remove(exercise.exerciseName)
        _savedReps.remove(exercise.exerciseName)
        persistDraft()
        viewModelScope.launch {
            val resolvedDbId = workoutRepository.findExerciseByName(newName)?.exerciseDbId
            val plan = _guidedPlan.value.toMutableList()
            if (idx < plan.size) {
                plan[idx] = plan[idx].copy(
                    exerciseName = newName,
                    exerciseDbId = resolvedDbId,        // image + DB info follow the new variant
                    notes = "",                          // old AI coaching note no longer applies
                    targetReps = exercise.targetReps.ifBlank { "8-12" },
                    targetWeightKg = 0f,                 // calisthenics variant: bodyweight
                    matchConfidence = -1f,
                    matchSource = "",
                    isLogged = false
                )
                _guidedPlan.value = plan
            }
        }
    }

    fun calculatePlates(targetKg: Float, barKg: Float = 20f): String {
        var remaining = (targetKg - barKg) / 2f
        if (remaining <= 0f) return "Bar only (${barKg.toInt()}kg)"
        val plates = listOf(20f, 15f, 10f, 5f, 2.5f, 1.25f)
        val parts = mutableListOf<String>()
        for (plate in plates) {
            val count = (remaining / plate).toInt()
            if (count > 0) {
                val label = if (plate == plate.toInt().toFloat()) "${plate.toInt()}kg" else "${plate}kg"
                parts.add("${count}x $label")
                remaining -= count * plate
            }
        }
        return if (parts.isEmpty()) "Bar only" else "Per side: ${parts.joinToString(" + ")}"
    }

    fun getExerciseNames(): List<String> = AppDatabase.DEFAULT_EXERCISES.map { it.name }
}

/**
 * Items 4 + 2: what the rest timer should start at for one set, plus the data the sheet needs
 * for an honest source label. [baseSeconds] is the un-adjusted base (AI suggestion or the user's
 * category time); [seconds] is the actual start (base ± the session's net +30/−30 adjustments);
 * [isManualSource] tells the sheet to say "Your time" instead of "AI suggested".
 */
data class RestStart(
    val seconds: Int,
    val baseSeconds: Int,
    val isManualSource: Boolean
)
