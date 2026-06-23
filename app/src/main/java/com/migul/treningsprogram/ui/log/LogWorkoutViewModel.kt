package com.migul.treningsprogram.ui.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    private val gson: Gson
) : ViewModel() {

    private val restTimerFallbackSeconds = 90

    private val _sessionId = MutableStateFlow<Long?>(null)
    private val _sessionStartMs = MutableStateFlow(0L)
    private val _dayOfWeek = MutableStateFlow(0)
    val workoutDayOfWeek: Int get() = _dayOfWeek.value

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

    // Per-exercise timer: resets automatically whenever currentIndex changes
    @OptIn(ExperimentalCoroutinesApi::class)
    val currentExerciseElapsedMs: StateFlow<Long> = _currentIndex
        .flatMapLatest {
            flow {
                val start = System.currentTimeMillis()
                while (true) {
                    emit(System.currentTimeMillis() - start)
                    delay(1000)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

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

    @OptIn(ExperimentalCoroutinesApi::class)
    val challengeProgress: StateFlow<String> = sets
        .combine(_todayChallenges) { currentSets, challenges ->
            if (challenges.isEmpty()) return@combine ""
            val workingSets = currentSets.filter { !it.isWarmup }
            val muscleGroups = workingSets.map { it.muscleGroup }.toSet()
            val exerciseNames = workingSets.map { it.exerciseName }.toSet()
            challenges.joinToString("   ") { ch ->
                val metNow = when (ch.id) {
                    "complete_workout" -> workingSets.isNotEmpty()
                    "sets_10"          -> workingSets.size >= 10
                    "sets_15"          -> workingSets.size >= 15
                    "sets_20"          -> workingSets.size >= 20
                    "chest_day"        -> "Chest" in muscleGroups
                    "back_day"         -> "Back" in muscleGroups
                    "leg_day"          -> "Legs" in muscleGroups
                    "arms_day"         -> "Arms" in muscleGroups
                    "core_day"         -> "Core" in muscleGroups
                    "exercises_3"      -> exerciseNames.size >= 3
                    "exercises_5"      -> exerciseNames.size >= 5
                    else               -> false
                }
                when {
                    ch.isCompleted || metNow -> "✅ ${ch.name}"
                    ch.id == "sets_10"     -> "⬜ ${ch.name} ${workingSets.size}/10"
                    ch.id == "sets_15"     -> "⬜ ${ch.name} ${workingSets.size}/15"
                    ch.id == "sets_20"     -> "⬜ ${ch.name} ${workingSets.size}/20"
                    ch.id == "exercises_3" -> "⬜ ${ch.name} ${exerciseNames.size}/3"
                    ch.id == "exercises_5" -> "⬜ ${ch.name} ${exerciseNames.size}/5"
                    else                   -> "⬜ ${ch.name}"
                }
            }
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

    fun loadSession(sessionId: Long, dayOfWeek: Int) {
        if (_sessionId.value == sessionId) return
        _sessionId.value = sessionId
        hydrateDraft(sessionId)
        val day = if (dayOfWeek > 0) dayOfWeek else currentDayOfWeek()
        _dayOfWeek.value = day
        viewModelScope.launch {
            val session = workoutRepository.getActiveSession()
            _sessionStartMs.value = session?.dateMs ?: System.currentTimeMillis()
            _todayChallenges.value = dailyChallengeManager.getTodayChallenges()
            loadGuidedPlan(day)
        }
    }

    fun resumeSession(dayOfWeek: Int = -1) {
        if (_sessionId.value != null) return
        viewModelScope.launch {
            val id = workoutRepository.startSession()
            _sessionId.value = id
            hydrateDraft(id)
            val session = workoutRepository.getActiveSession()
            _sessionStartMs.value = session?.dateMs ?: System.currentTimeMillis()
            val day = if (dayOfWeek > 0) dayOfWeek else currentDayOfWeek()
            _dayOfWeek.value = day
            _todayChallenges.value = dailyChallengeManager.getTodayChallenges()
            loadGuidedPlan(day)
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

    fun logSet(weight: Float, reps: Int, isWarmup: Boolean, rpeLabel: String) {
        val sid = _sessionId.value ?: return
        val exercise = currentExercise.value ?: return
        viewModelScope.launch {
            val existingSets = sets.value.filter { it.exerciseName == exercise.exerciseName }
            workoutRepository.addSet(
                WorkoutSet(
                    sessionId = sid,
                    exerciseName = exercise.exerciseName,
                    muscleGroup = AppDatabase.DEFAULT_EXERCISES
                        .find { it.name == exercise.exerciseName }?.muscleGroup ?: "",
                    setNumber = existingSets.size + 1,
                    reps = reps,
                    weightKg = weight,
                    isWarmup = isWarmup,
                    rpeLabel = rpeLabel,
                    loggedAtMs = System.currentTimeMillis()
                )
            )
        }
    }

    fun logFreestyleSet(exerciseName: String, weight: Float, reps: Int, isWarmup: Boolean, rpeLabel: String) {
        val sid = _sessionId.value ?: return
        viewModelScope.launch {
            val existingSets = sets.value.filter { it.exerciseName == exerciseName }
            val muscleGroup = AppDatabase.DEFAULT_EXERCISES.find { it.name == exerciseName }?.muscleGroup ?: ""
            workoutRepository.addSet(
                WorkoutSet(
                    sessionId = sid,
                    exerciseName = exerciseName,
                    muscleGroup = muscleGroup,
                    setNumber = existingSets.size + 1,
                    reps = reps,
                    weightKg = weight,
                    isWarmup = isWarmup,
                    rpeLabel = rpeLabel,
                    loggedAtMs = System.currentTimeMillis()
                )
            )
        }
    }

    fun addSet(exerciseName: String, muscleGroup: String, reps: Int, weightKg: Float) {
        val sid = _sessionId.value ?: return
        viewModelScope.launch {
            val existingSets = sets.value.filter { it.exerciseName == exerciseName }
            workoutRepository.addSet(WorkoutSet(
                sessionId = sid, exerciseName = exerciseName, muscleGroup = muscleGroup,
                setNumber = existingSets.size + 1, reps = reps, weightKg = weightKg,
                loggedAtMs = System.currentTimeMillis()
            ))
        }
    }

    fun deleteSet(set: WorkoutSet) { viewModelScope.launch { workoutRepository.deleteSet(set) } }

    fun completeWorkout() {
        val sid = _sessionId.value ?: return
        viewModelScope.launch {
            if (sets.value.none { !it.isWarmup }) {
                workoutRepository.deleteSession(sid)
                clearDraft(sid)
                _sessionAbandoned.value = true
                return@launch
            }
            val durationMs = System.currentTimeMillis() - _sessionStartMs.value
            workoutRepository.completeSession(sid, (durationMs / 60_000).toInt())
            clearDraft(sid)
            // Mark planned exercises done so week progress bar is accurate
            val loggedNames = sets.value.filter { !it.isWarmup }.map { it.exerciseName }.toSet()
            val plannedToday = workoutRepository.getPlannedForDay(thisMonday(), _dayOfWeek.value).first()
            plannedToday.filter { it.exerciseName in loggedNames && !it.isLogged }
                .forEach { workoutRepository.updatePlannedExercise(it.copy(isLogged = true)) }
            val result = gamificationRepository.processWorkoutCompletion(sid)
            _workoutResult.value = result
        }
    }

    fun clearResult() { _workoutResult.value = null }

    fun abandonSession() {
        val sid = _sessionId.value ?: return
        viewModelScope.launch {
            workoutRepository.deleteSession(sid)
            clearDraft(sid)
            _sessionAbandoned.value = true
        }
    }

    suspend fun getLastSets(exerciseName: String): List<WorkoutSet> =
        workoutRepository.getLastSetsForExercise(exerciseName, _sessionId.value ?: -1)

    fun getRestSecondsForCurrentExercise(): Int =
        currentExercise.value?.recommendedRestSeconds ?: restTimerFallbackSeconds

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
