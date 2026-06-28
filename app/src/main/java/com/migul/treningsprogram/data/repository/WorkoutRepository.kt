package com.migul.treningsprogram.data.repository

import androidx.room.withTransaction
import com.migul.treningsprogram.data.ExerciseDbResolver
import com.migul.treningsprogram.data.ResolveHints
import com.migul.treningsprogram.data.backup.BackupScheduler
import com.migul.treningsprogram.data.db.AppDatabase
import com.migul.treningsprogram.data.db.dao.*
import com.migul.treningsprogram.data.db.entity.*
import com.migul.treningsprogram.data.preferences.PreferencesManager
import com.migul.treningsprogram.domain.DayPlanEditor
import com.migul.treningsprogram.domain.RestDayBackfill
import com.migul.treningsprogram.domain.TrainingDaySelection
import com.migul.treningsprogram.domain.model.ExerciseRecap
import com.migul.treningsprogram.domain.model.SessionPacing
import com.migul.treningsprogram.domain.model.SessionRecap
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class WorkoutRepository @Inject constructor(
    private val db: AppDatabase,
    private val sessionDao: WorkoutSessionDao,
    private val setDao: WorkoutSetDao,
    private val exerciseDao: ExerciseDao,
    private val plannedDao: PlannedExerciseDao,
    private val programDao: ProgramDao,
    private val resolver: ExerciseDbResolver,
    private val backupScheduler: BackupScheduler,
    private val preferencesManager: PreferencesManager
) {

    companion object {
        /** Name used for the auto-created default program on fresh installs. */
        const val DEFAULT_PROGRAM_NAME = "My Program"
    }

    /**
     * Serializes E1 manual day-plan list mutations (add / delete / reorder). Each such edit is a
     * read-modify-write of a whole day's rows; running two concurrently (rapid taps) would let the
     * second read a stale snapshot and clobber the first's change (lost edit). The mutex forces them
     * to apply one at a time, in order, each reading the freshly-persisted state. Field-only edits
     * (editExercise) are a single in-place @Update and don't need it.
     */
    private val dayEditMutex = Mutex()

    /**
     * Serializes [autoLogRestDays] so overlapping launch/foreground triggers (onCreate + onStart, or
     * a config-change recreate) can't both read the same pre-insert snapshot and double-insert the
     * same rest/missed day. With the lock, the second caller sees the first's freshly-inserted rows.
     */
    private val restDayBackfillMutex = Mutex()
    val allSessions: Flow<List<WorkoutSession>> = sessionDao.getAllSessions()
    val allExercises: Flow<List<Exercise>> = exerciseDao.getAllExercises()

    fun getAllCompletedSessions(): Flow<List<WorkoutSession>> = sessionDao.getAllCompleted()

    /** History-timeline stream: real workouts plus auto-logged REST/MISSED days (Log tab only). */
    fun getHistoryTimeline(): Flow<List<WorkoutSession>> = sessionDao.getHistoryTimeline()

    suspend fun updateSession(session: WorkoutSession) = sessionDao.update(session)

    suspend fun deleteSet(set: WorkoutSet) = setDao.delete(set)

    suspend fun getDistinctExerciseNames(): List<String> = setDao.getDistinctExerciseNames()

    /** Each exercise with its distinct-session count, for the B03 most-trained-first picker. */
    suspend fun getExerciseSessionCounts(): List<ExerciseSessionCount> =
        setDao.getExerciseSessionCounts()

    suspend fun getStrengthHistory(name: String): List<StrengthPoint> = setDao.getStrengthHistory(name)

    suspend fun getWeeklyVolume(name: String): List<WeekVolume> = setDao.getWeeklyVolume(name)

    suspend fun getMuscleGroupVolume(): List<MuscleVolume> = setDao.getMuscleGroupVolume()

    /** Last-trained timestamp per muscle group, live — drives the Home recovery view (C4). */
    fun observeLastTrainedPerMuscleGroup(): Flow<List<MuscleLastTrained>> =
        setDao.observeLastTrainedPerMuscleGroup()

    /**
     * Live stream of all (exerciseName, sessionId, sessionDateMs) rows from working sets
     * in completed sessions. Used by the weighted recovery model (U1) to derive fine-grain
     * per-muscle recovery from MuscleClassifier.finerMusclesFor.
     */
    fun observeExerciseSessionRows(): Flow<List<ExerciseSessionRow>> =
        setDao.observeExerciseSessionRows()

    suspend fun getRepRangeDistribution(): List<RepRange> = setDao.getRepRangeDistribution()

    suspend fun getTrainingDayEpochs(): List<Long> = setDao.getTrainingDayEpochs()

    suspend fun getPRsWithDate(): List<ExercisePrWithDate> = setDao.getPRsWithDate()

    fun observePRsWithDate(): Flow<List<ExercisePrWithDate>> = setDao.observePRsWithDate()

    suspend fun getTotalSets(): Int = setDao.getTotalSets()

    suspend fun getTotalVolumeKg(): Float = setDao.getTotalVolumeKg()

    /** Local-DB exercise search for the quick-access "Add exercise" picker (Item 6). */
    suspend fun searchExercises(query: String): List<Exercise> =
        if (query.isBlank()) emptyList() else exerciseDao.searchByName(query.trim())

    suspend fun findExerciseByName(name: String): Exercise? = exerciseDao.findByName(name)

    suspend fun ensureExercisesPopulated() {
        if (exerciseDao.count() == 0) {
            val now = System.currentTimeMillis()
            val resolved = AppDatabase.DEFAULT_EXERCISES.map { ex ->
                val result = resolver.resolve(ex.name, ResolveHints(muscle = ex.muscleGroup, equipment = ex.equipment))
                ex.copy(
                    exerciseDbId = result?.dbId,
                    matchConfidence = result?.confidence ?: -1f,
                    matchSource = result?.source?.name?.lowercase() ?: "none",
                    resolvedAt = now
                )
            }
            exerciseDao.insertAll(resolved)
        }
    }

    suspend fun backfillPlannedExercises() {
        val unresolved = plannedDao.getUnresolved()
        if (unresolved.isEmpty()) return
        val now = System.currentTimeMillis()
        unresolved.map { it.exerciseName }.toSet().forEach { name ->
            val result = resolver.resolve(name, ResolveHints())
            plannedDao.bindByName(
                name = name,
                dbId = result?.dbId,
                confidence = result?.confidence ?: -1f,
                source = result?.source?.name?.lowercase() ?: "none",
                resolvedAt = now
            )
        }
    }

    suspend fun startSession(): Long {
        val existing = sessionDao.getActiveSession()
        if (existing != null) return existing.id
        return sessionDao.insert(WorkoutSession(dateMs = System.currentTimeMillis()))
    }

    suspend fun completeSession(sessionId: Long, durationMinutes: Int): Boolean {
        val session = sessionDao.getActiveSession() ?: return false
        if (setDao.getWorkingSetCount(sessionId) == 0) {
            sessionDao.delete(session)
            return false
        }
        sessionDao.update(session.copy(isCompleted = true, durationMinutes = durationMinutes))
        // Completing a workout (and the achievement/XP unlock that immediately follows it) is the
        // single biggest user-data change — request a coalesced backup.
        backupScheduler.requestBackup()
        return true
    }

    suspend fun deleteSession(sessionId: Long) {
        val session = sessionDao.getActiveSession()?.takeIf { it.id == sessionId }
            ?: return
        sessionDao.delete(session)
    }

    suspend fun getActiveSession(): WorkoutSession? = sessionDao.getActiveSession()

    suspend fun getSessionById(id: Long): WorkoutSession? = sessionDao.getById(id)

    suspend fun getPreviousMaxWeight(exerciseName: String, excludeSessionId: Long): Float? =
        setDao.getPreviousMaxWeight(exerciseName, excludeSessionId)

    fun getSetsForSession(sessionId: Long): Flow<List<WorkoutSet>> =
        setDao.getSetsForSession(sessionId)

    suspend fun getSetsForSessionOnce(sessionId: Long): List<WorkoutSet> =
        setDao.getSetsForSessionOnce(sessionId)

    suspend fun addSet(set: WorkoutSet): Long {
        val id = setDao.insert(set)
        // Each logged set is user data; debounce collapses a rapid flurry of set logs into one push.
        backupScheduler.requestBackup()
        return id
    }

    suspend fun getRecentSessions(count: Int = 20): List<WorkoutSession> =
        sessionDao.getRecentCompleted(count)

    suspend fun getLastSetsForExercise(exerciseName: String, excludeSessionId: Long = -1): List<WorkoutSet> =
        setDao.getLastSetsForExercise(exerciseName, excludeSessionId)

    // ── Auto-log rest / missed days ──────────────────────────────────────────────────────────────

    /**
     * Auto-logs a REST or MISSED placeholder for every past day that has nothing logged, so a day
     * that passes with no workout still appears in History (clearly as rest vs missed) and the user
     * can see their consistency. Safe to call on every app launch/foreground:
     *
     *  - The window is `max(lastLoggedWorkout, featureFirstRun) + 1 … yesterday` (today excluded);
     *    [RestDayBackfill.daysToFill] does the day-boundary math.
     *  - First ever run only stamps [PreferencesManager.restDayFeatureFirstRunMs] and fills nothing
     *    (the window collapses to empty), so we never invent history before the feature existed.
     *  - Idempotent: any day that already has a session of ANY kind is skipped, so re-runs never
     *    duplicate. A [restDayBackfillMutex] guards against concurrent triggers.
     *  - Placeholders are completed, have no sets, and are marked [WorkoutSession.KIND_REST]/
     *    [WorkoutSession.KIND_MISSED] so every set-joined aggregate (volume / PR / strength / streaks /
     *    AI history) naturally excludes them.
     *
     * Pure date/classification logic lives in [RestDayBackfill]; this method only does the Android I/O
     * (read prefs + sessions + plan, convert millis ⇄ local date, batch-insert) off the main thread.
     */
    suspend fun autoLogRestDays() = restDayBackfillMutex.withLock {
        val zone = java.time.ZoneId.systemDefault()
        fun epochDayOf(ms: Long): Long =
            java.time.Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().toEpochDay()

        // First run: stamp the floor and stop. The window would be empty anyway (firstRun == today),
        // but returning here keeps the "never backfill before the feature existed" rule explicit.
        val firstRunMs = preferencesManager.restDayFeatureFirstRunMs
        if (firstRunMs == 0L) {
            preferencesManager.restDayFeatureFirstRunMs = System.currentTimeMillis()
            return@withLock
        }

        val todayEpoch = java.time.LocalDate.now(zone).toEpochDay()
        val featureFirstRunEpoch = epochDayOf(firstRunMs)

        val allSessionsOnce = sessionDao.getAllOnce()
        // Idempotency set: every day that already has a row of ANY kind (workout, in-progress,
        // rest, missed). Last real workout day = floor for the window (placeholders don't count).
        val existingEpochs = allSessionsOnce.map { epochDayOf(it.dateMs) }.toSet()
        val lastLoggedEpoch = allSessionsOnce
            .filter { !it.isPlaceholder }
            .maxOfOrNull { epochDayOf(it.dateMs) }

        val daysToFill = RestDayBackfill.daysToFill(
            todayEpoch = todayEpoch,
            lastLoggedEpoch = lastLoggedEpoch,
            featureFirstRunEpoch = featureFirstRunEpoch,
            existingRecordEpochs = existingEpochs
        )
        if (daysToFill.isEmpty()) return@withLock

        // Classification inputs. Rest-day mode keys off restDaysCsv; count mode uses the weekdays the
        // active program's plan actually trains (planned_exercises.dayOfWeek) as the best signal.
        val restDays = TrainingDaySelection.parseRestDays(preferencesManager.restDaysCsv)
        val activeProgramId = programDao.getActiveOnce()?.id
        val plannedTrainingWeekdays: Set<Int> = plannedDao.getAllOnce()
            .filter { activeProgramId == null || it.programId == activeProgramId }
            .map { it.dayOfWeek }
            .toSet()

        val newRows = daysToFill.map { epoch ->
            val date = java.time.LocalDate.ofEpochDay(epoch)
            val weekday = date.dayOfWeek.value // 1 = Monday … 7 = Sunday
            val kind = RestDayBackfill.classify(weekday, restDays, plannedTrainingWeekdays)
            // Noon local avoids any day-boundary ambiguity when this dateMs is later mapped back.
            val dateMs = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
            WorkoutSession(dateMs = dateMs, isCompleted = true, kind = kind)
        }
        sessionDao.insertAll(newRows)
    }

    // ── E2: program model ──────────────────────────────────────────────────────────────────────

    /** Live list of all saved programs (oldest first), for the switcher UI. */
    fun observePrograms(): Flow<List<Program>> = programDao.observeAll()

    /** Live active program (drives Home + Program tab). null only transiently before bootstrap. */
    fun observeActiveProgram(): Flow<Program?> = programDao.observeActive()

    suspend fun getActiveProgramOnce(): Program? = programDao.getActiveOnce()

    /**
     * Returns the active program's id, creating a default active program on first use (covers fresh
     * installs, where the DB is created at the current version and the v12→v13 migration never runs,
     * so no program was seeded). Idempotent: once a program exists it is reused. If programs exist
     * but none is flagged active (shouldn't happen), the oldest is promoted.
     */
    suspend fun ensureActiveProgramId(): Long = db.withTransaction {
        programDao.getActiveOnce()?.let { return@withTransaction it.id }
        val existing = programDao.getAllOnce()
        if (existing.isNotEmpty()) {
            val first = existing.first()
            programDao.setActive(first.id)
            return@withTransaction first.id
        }
        val id = programDao.insert(
            Program(name = DEFAULT_PROGRAM_NAME, createdAtMs = System.currentTimeMillis(), isActive = true)
        )
        // Adopt any pre-existing unscoped plan rows (defensive; the migration normally backfills).
        plannedDao.getAllOnce().filter { it.programId == null }.forEach {
            plannedDao.update(it.copy(programId = id))
        }
        id
    }

    /** Save the CURRENT plan as a new named program and switch to it, copying this week's rows. */
    suspend fun saveCurrentAsProgram(name: String): Long {
        val sourceId = ensureActiveProgramId()
        val newId = db.withTransaction {
            val id = programDao.insert(
                Program(name = name.trim().ifBlank { "Program" }, createdAtMs = System.currentTimeMillis())
            )
            // Copy every plan row of the source program into the new one (fresh ids via id = 0).
            val rows = plannedDao.getAllOnce().filter { it.programId == sourceId }
            if (rows.isNotEmpty()) {
                plannedDao.insertAll(rows.map { it.copy(id = 0, programId = id) })
            }
            programDao.setActive(id)
            id
        }
        backupScheduler.requestBackup()
        return newId
    }

    /** Create a brand-new EMPTY program (no plan) and make it active. */
    suspend fun createProgram(name: String): Long {
        val id = programDao.insert(
            Program(name = name.trim().ifBlank { "Program" }, createdAtMs = System.currentTimeMillis())
        )
        programDao.setActive(id)
        backupScheduler.requestBackup()
        return id
    }

    suspend fun switchActiveProgram(programId: Long) {
        programDao.setActive(programId)
        backupScheduler.requestBackup()
    }

    suspend fun renameProgram(programId: Long, name: String) {
        val p = programDao.getById(programId) ?: return
        programDao.update(p.copy(name = name.trim().ifBlank { p.name }))
        backupScheduler.requestBackup()
    }

    suspend fun deleteProgram(programId: Long) {
        db.withTransaction {
            val all = programDao.getAllOnce()
            // Never delete the last program — there must always be one active program.
            if (all.size <= 1) return@withTransaction
            val target = all.firstOrNull { it.id == programId } ?: return@withTransaction
            plannedDao.deleteForProgram(programId)
            programDao.delete(target)
            if (target.isActive) {
                programDao.getAllOnce().firstOrNull()?.let { programDao.setActive(it.id) }
            }
        }
        backupScheduler.requestBackup()
    }

    suspend fun updateProgram(program: Program) {
        programDao.update(program)
        backupScheduler.requestBackup()
    }

    /** Set/clear the active program's stall-triggered deload flag (M2). */
    suspend fun setActiveDeload(active: Boolean) {
        val id = ensureActiveProgramId()
        programDao.setDeload(id, active)
    }

    /**
     * E2/M2: the lifts that are currently stalled per B3's [com.migul.treningsprogram.domain.StallDetector],
     * computed over every logged exercise's strength history. Reused by the deload trigger so the
     * deload decision is grounded in the same plateau detection B3 surfaces (no re-implementation).
     */
    suspend fun computeStalledLifts(): List<String> {
        val histories = setDao.getDistinctExerciseNames().associateWith { name ->
            setDao.getStrengthHistory(name)
        }
        return com.migul.treningsprogram.domain.DeloadPolicy.stalledFrom(histories)
    }

    /**
     * 1-based week index within the active program's mesocycle block. Returns 1 when the program is
     * not a block. Derived from [Program.blockStartWeek] vs the current Monday, counting elapsed
     * calendar weeks (clamped to ≥1 and ≤ mesocycleWeeks so it never reports past the block end).
     */
    fun weekInBlock(program: Program, currentMonday: Long): Int =
        Program.weekInBlock(program.mesocycleWeeks, program.blockStartWeek, currentMonday)

    /**
     * Deload-/mesocycle-aware [MesocycleContext] for an on-demand regeneration of the week starting
     * [monday] (E2 L1 + M2). Computes the stall-triggered deload decision (reusing B3's StallDetector).
     * Deload is a once-per-week state machine: re-generating the SAME week (it already has a plan) must
     * KEEP an active deload rather than re-running the exit branch and dropping it mid-week. Shared by
     * the Program-tab preserve-logged regen and the Settings full-week regen so they stay consistent.
     */
    suspend fun buildRegenMesocycle(monday: Long): MesocycleContext {
        val active = ensureActiveProgramId().let { getActiveProgramOnce() }
        val stalledLifts = computeStalledLifts()
        val replacingCurrentWeek = getActiveProgramPlanForWeek(monday).isNotEmpty()
        val isDeload = com.migul.treningsprogram.domain.DeloadPolicy.nextDeloadStateForRegen(
            currentlyDeloading = active?.isDeloadActive ?: false,
            stalledCount = stalledLifts.size,
            replacingCurrentWeek = replacingCurrentWeek
        )
        return active?.let { p ->
            MesocycleContext(
                mesocycleWeeks = p.mesocycleWeeks,
                weekInBlock = weekInBlock(p, monday),
                isDeload = isDeload,
                stalledLifts = stalledLifts
            )
        } ?: MesocycleContext(isDeload = isDeload, stalledLifts = stalledLifts)
    }

    // ── Plan queries (program-scoped, defaulting to the active program) ──────────────────────────

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getPlannedForDay(weekStart: Long, dayOfWeek: Int): Flow<List<PlannedExercise>> =
        programDao.observeActive().flatMapLatest { program ->
            if (program == null) emptyFlow()
            else plannedDao.getForDayInProgram(program.id, weekStart, dayOfWeek)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getPlannedForWeek(weekStart: Long): Flow<List<PlannedExercise>> =
        programDao.observeActive().flatMapLatest { program ->
            if (program == null) emptyFlow()
            else plannedDao.getForWeekInProgram(program.id, weekStart)
        }

    suspend fun savePlan(weekStart: Long, exercises: List<PlannedExercise>) {
        val programId = ensureActiveProgramId()
        db.withTransaction {
            plannedDao.deleteForWeekInProgram(programId, weekStart)
            plannedDao.insertAll(exercises.map { it.copy(programId = programId) })
        }
        // A newly generated weekly plan is user data worth backing up.
        backupScheduler.requestBackup()
    }

    /**
     * B09: persist a preserve-logged-days regeneration. Replaces the PLAN of every day NOT in
     * [loggedDays] with [exercises], and leaves every logged day's rows completely untouched
     * (including their isLogged flag + actuals). Days the new plan does not cover are cleared, so a
     * regeneration that rebalances training off an unlogged-but-previously-planned day removes that
     * stale plan.
     *
     * HARD GUARANTEE (B09): this only ever touches the planned_exercises table. Logged SETS and
     * workout HISTORY live in workout_sessions / workout_sets and are never referenced here, so no
     * regeneration can delete logged data. [exercises] is defensively filtered to the non-logged days.
     */
    suspend fun savePlanPreservingLoggedDays(
        weekStart: Long,
        exercises: List<PlannedExercise>,
        loggedDays: Set<Int>
    ) {
        val programId = ensureActiveProgramId()
        db.withTransaction {
            // Pure decision (unit-tested): only NON-logged days are cleared, and only NON-logged-day
            // rows are inserted — logged days' planned rows are untouched, and logged sets/history
            // (separate tables) are never referenced.
            com.migul.treningsprogram.domain.RegeneratePlanner.daysToReplace(loggedDays).forEach { day ->
                plannedDao.deleteForDayInProgram(programId, weekStart, day)
            }
            plannedDao.insertAll(
                com.migul.treningsprogram.domain.RegeneratePlanner.exercisesToPersist(exercises, loggedDays)
                    .map { it.copy(programId = programId) }
            )
        }
        backupScheduler.requestBackup()
    }

    /**
     * P2: commit a "do another day's workout today" MOVE on workout completion. Moves [sourceDay]'s
     * plan rows into [targetDay]'s slot (the performed exercises in [performedNames] marked logged),
     * DISCARDS [targetDay]'s original plan, and CLEARS [sourceDay] (vacated — the caller then runs a
     * week rebalance that regenerates it and the other non-logged days). Only ever touches
     * planned_exercises; the logged session/sets live in other tables and are never referenced.
     *
     * Atomic + idempotent-ish: a no-op when [sourceDay] has no rows. Mirrors [saveDayPlan]'s
     * rationale-preservation so the week keeps its "why your program changed" text.
     */
    suspend fun commitDayMove(
        weekStart: Long,
        sourceDay: Int,
        targetDay: Int,
        performedNames: Set<String>
    ) {
        if (sourceDay == targetDay) return
        val programId = ensureActiveProgramId()
        db.withTransaction {
            val sourceRows = plannedDao.getForDayInProgramOnce(programId, weekStart, sourceDay)
            if (sourceRows.isEmpty()) return@withTransaction
            val weekRows = plannedDao.getForWeekInProgramOnce(programId, weekStart)
            val weekRationale = weekRows.firstOrNull { it.rationale.isNotBlank() }?.rationale ?: ""
            // Discard target's original plan and clear the vacated source day.
            plannedDao.deleteForDayInProgram(programId, weekStart, targetDay)
            plannedDao.deleteForDayInProgram(programId, weekStart, sourceDay)
            val moved = com.migul.treningsprogram.domain.DayMovePlanner
                .movedRows(sourceRows, targetDay, performedNames, weekRationale)
                .map { it.copy(weekStart = weekStart, programId = programId) }
            plannedDao.insertAll(moved)
        }
        backupScheduler.requestBackup()
    }

    suspend fun saveDayPlan(weekStart: Long, dayOfWeek: Int, exercises: List<PlannedExercise>) {
        val programId = ensureActiveProgramId()
        db.withTransaction {
            // B2: single-day regen must PRESERVE the week's rationale. The week's rationale is
            // stamped on every row (see savePlan); read it off any row of the same week and
            // carry it forward onto the new day's rows so the week stays consistent and the
            // Program tab keeps showing the same "why your program changed" text. Prefer another
            // day's copy, but fall back to the edited day's OWN rationale — otherwise a week whose
            // only populated day is the one being saved (e.g. a single-workout-day program) would
            // silently drop its rationale on every manual edit.
            val weekRows = plannedDao.getForWeekInProgramOnce(programId, weekStart)
            val weekRationale = (weekRows.firstOrNull { it.dayOfWeek != dayOfWeek && it.rationale.isNotBlank() }
                ?: weekRows.firstOrNull { it.rationale.isNotBlank() })
                ?.rationale ?: ""
            plannedDao.deleteForDayInProgram(programId, weekStart, dayOfWeek)
            plannedDao.insertAll(exercises.map { it.copy(rationale = weekRationale, programId = programId) })
        }
        // A regenerated/edited day plan is user data worth backing up.
        backupScheduler.requestBackup()
    }

    /**
     * E1: atomic read-modify-write of one day's plan list for a manual add / delete / reorder.
     *
     * The transform is applied to the day's CURRENT persisted rows (read inside the lock), not to a
     * UI StateFlow snapshot, so concurrent rapid edits can't clobber each other with stale data.
     * [transform] must produce a list already keyed with deterministic orderInDay — callers use
     * [DayPlanEditor], which guarantees that. Mirrors [saveDayPlan]'s rationale-preservation.
     */
    suspend fun editDayPlan(
        weekStart: Long,
        dayOfWeek: Int,
        transform: (current: List<PlannedExercise>) -> List<PlannedExercise>
    ) {
        val programId = ensureActiveProgramId()
        dayEditMutex.withLock {
            db.withTransaction {
                val weekRows = plannedDao.getForWeekInProgramOnce(programId, weekStart)
                val current = weekRows.filter { it.dayOfWeek == dayOfWeek }.sortedBy { it.orderInDay }
                val updated = transform(current)
                val weekRationale = (weekRows.firstOrNull { it.dayOfWeek != dayOfWeek && it.rationale.isNotBlank() }
                    ?: weekRows.firstOrNull { it.rationale.isNotBlank() })
                    ?.rationale ?: ""
                plannedDao.deleteForDayInProgram(programId, weekStart, dayOfWeek)
                plannedDao.insertAll(
                    updated.map { it.copy(dayOfWeek = dayOfWeek, rationale = weekRationale, programId = programId) }
                )
            }
        }
        backupScheduler.requestBackup()
    }

    suspend fun getLatestPlanWeekStart(): Long? {
        val programId = programDao.getActiveOnce()?.id ?: return plannedDao.getLatestWeekStart()
        return plannedDao.getLatestWeekStartInProgram(programId)
    }

    suspend fun getAllPlannedOnce(): List<PlannedExercise> = plannedDao.getAllOnce()

    /** Active program's plan for [weekStart] (E2-scoped; used by the generation "previous plan" context). */
    suspend fun getActiveProgramPlanForWeek(weekStart: Long): List<PlannedExercise> {
        val programId = programDao.getActiveOnce()?.id ?: return plannedDao.getForWeekOnce(weekStart)
        return plannedDao.getForWeekInProgramOnce(programId, weekStart)
    }

    suspend fun updatePlannedExercise(exercise: PlannedExercise) = plannedDao.update(exercise)

    /**
     * E1 field edit (sets/reps/weight/notes) of a single planned-exercise row, serialized under the
     * same [dayEditMutex] as the structural list edits. This keeps the in-place @Update from landing
     * mid-way through a structural delete+reinsert of the same day (which would otherwise lose the
     * field edit by targeting a row id that the rewrite had just replaced).
     */
    suspend fun editPlannedExerciseFields(exercise: PlannedExercise) = dayEditMutex.withLock {
        plannedDao.update(exercise)
    }

    suspend fun resetAllWorkouts() {
        setDao.deleteAll()
        sessionDao.deleteAll()
    }

    /**
     * Builds the session-scoped recap. Returns null for sessions with no working
     * sets (consistent with the app's "real workout" threshold).
     */
    suspend fun buildSessionRecap(sessionId: Long): SessionRecap? {
        val session = sessionDao.getById(sessionId) ?: return null
        val working = setDao.getSetsForSessionOnce(sessionId).filter { !it.isWarmup }
        if (working.isEmpty()) return null

        val prByName = setDao.getPRsWithDate().associateBy { it.exerciseName }

        // Preserve the order exercises first appeared in the session.
        val exercises = working
            .groupBy { it.exerciseName }
            .map { (name, sets) ->
                val muscle = sets.firstOrNull { it.muscleGroup.isNotBlank() }?.muscleGroup ?: ""
                val topWeight = sets.maxOf { it.weightKg }
                val topReps = sets.filter { it.weightKg == topWeight }.maxOf { it.reps }
                val volume = sets.sumOf { it.reps.toDouble() * it.weightKg }.toFloat()
                // Compare only against sessions before this one, so a historical
                // session shows the PR it earned at the time (not relative to later lifts).
                val prevMax = setDao.getMaxWeightBefore(name, session.dateMs)
                // First-ever performance (prevMax == null) is the baseline, never a PR.
                val isPr = topWeight > 0f && GamificationRepository.isWeightPr(topWeight, prevMax)
                val lastSets = setDao.getLastSetsForExerciseBefore(name, session.dateMs)
                val prevTopWeight = lastSets.maxOfOrNull { it.weightKg }
                val prevTopReps = prevTopWeight?.let { w ->
                    lastSets.filter { it.weightKg == w }.maxOf { it.reps }
                }
                val pr = prByName[name]
                ExerciseRecap(
                    exerciseName = name,
                    muscleGroup = muscle,
                    isCardio = muscle.equals("Cardio", ignoreCase = true),
                    sets = sets.size,
                    topWeightKg = topWeight,
                    topReps = topReps,
                    totalReps = sets.sumOf { it.reps },
                    volumeKg = volume,
                    prevTopWeightKg = prevTopWeight,
                    prevTopReps = prevTopReps,
                    isPrThisSession = isPr,
                    existingPrWeightKg = pr?.maxWeight,
                    existingPrDateMs = pr?.dateMs
                )
            }

        val focusMuscle = working.filter { it.muscleGroup.isNotBlank() }
            .groupBy { it.muscleGroup }.maxByOrNull { it.value.size }?.key ?: ""
        val muscleVolume = working.filter { it.muscleGroup.isNotBlank() }
            .groupBy { it.muscleGroup }
            .map { it.key to it.value.size }
            .sortedByDescending { it.second }

        val effortCounts = working.filter { it.rpeLabel.isNotBlank() }
            .groupingBy { it.rpeLabel }.eachCount()
        val effort = listOf("Easy", "Moderate", "Hard")
            .mapNotNull { lbl -> effortCounts[lbl]?.let { lbl to it } }

        // Adherence — match against the ACTIVE program's plan for this session's week + day, if any
        // (E2: plans are program-scoped, so adherence must use the active program's plan, falling
        // back to the legacy all-program query only when no program exists yet).
        val recapProgramId = programDao.getActiveOnce()?.id
        val planned = if (recapProgramId != null)
            plannedDao.getForDayInProgramOnce(recapProgramId, mondayOf(session.dateMs), dayOfWeekOf(session.dateMs))
        else plannedDao.getForDayOnce(mondayOf(session.dateMs), dayOfWeekOf(session.dateMs))
        val plannedSets = if (planned.isEmpty()) null else planned.sumOf { it.sets }
        val estimatedMinutes = if (planned.isEmpty()) null else {
            // ~40s of work per set + the prescribed rest after it.
            val seconds = planned.sumOf { p -> p.sets * (40 + p.recommendedRestSeconds) }
            (seconds / 60.0).roundToInt()
        }
        val performed = working.map { it.exerciseName.lowercase() }.toSet()
        val skipped = planned.filter { it.exerciseName.lowercase() !in performed }
            .map { it.exerciseName }

        // Pacing — measurable only from the gaps between timestamped sets.
        val stamped = working.filter { it.loggedAtMs > 0 }.sortedBy { it.loggedAtMs }
        val pacing = if (stamped.size < 2) null else {
            val idleCeiling = 300  // seconds; a gap longer than 5 min counts the excess as idle
            val gaps = stamped.zipWithNext { a, b -> ((b.loggedAtMs - a.loggedAtMs) / 1000L).toInt() }
                .filter { it > 0 }
            if (gaps.isEmpty()) null else {
                val restSecs = gaps.sumOf { minOf(it, idleCeiling) }
                val idleSecs = gaps.sumOf { maxOf(0, it - idleCeiling) }
                val normalGaps = gaps.filter { it <= idleCeiling }
                val avgRest = (if (normalGaps.isNotEmpty()) normalGaps.average() else gaps.average()).roundToInt()
                val targetRest = if (planned.isEmpty()) null
                    else planned.map { it.recommendedRestSeconds }.average().roundToInt()
                SessionPacing(
                    gapCount = gaps.size,
                    avgRestSeconds = avgRest,
                    targetRestSeconds = targetRest,
                    restSeconds = restSecs,
                    idleSeconds = idleSecs,
                    longPauseCount = gaps.count { it > idleCeiling }
                )
            }
        }

        return SessionRecap(
            session = session,
            focusMuscle = focusMuscle,
            durationMinutes = session.durationMinutes,
            totalVolumeKg = working.sumOf { it.reps.toDouble() * it.weightKg }.toFloat(),
            totalSets = working.size,
            exercises = exercises,
            muscleVolume = muscleVolume,
            effort = effort,
            plannedSets = plannedSets,
            estimatedMinutes = estimatedMinutes,
            skippedExercises = skipped,
            pacing = pacing
        )
    }
}

fun thisMonday(): Long {
    val cal = Calendar.getInstance().apply {
        firstDayOfWeek = Calendar.MONDAY
        set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    // If locale placed us in the future, step back one week
    if (cal.timeInMillis > System.currentTimeMillis()) cal.add(Calendar.WEEK_OF_YEAR, -1)
    return cal.timeInMillis
}

/**
 * Locale-independent key identifying the current training week, used to mark when the
 * weekly plan was last auto-generated. Derived from [thisMonday] (the same Monday the
 * plan itself is keyed on) rather than `SimpleDateFormat("yyyy-'W'ww")`, whose week
 * numbering depends on the device's locale (first-day-of-week / min-days-in-first-week)
 * and so could shift the boundary between users. Format: "wk-<epochMillisOfMonday>".
 */
fun autoGenWeekKey(): String = "wk-${thisMonday()}"

fun currentDayOfWeek(): Int {
    val day = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
    return when (day) {
        Calendar.MONDAY -> 1
        Calendar.TUESDAY -> 2
        Calendar.WEDNESDAY -> 3
        Calendar.THURSDAY -> 4
        Calendar.FRIDAY -> 5
        Calendar.SATURDAY -> 6
        Calendar.SUNDAY -> 7
        else -> 1
    }
}

/** Monday 00:00 of the week containing [ms]. */
fun mondayOf(ms: Long): Long {
    val cal = Calendar.getInstance().apply {
        firstDayOfWeek = Calendar.MONDAY
        timeInMillis = ms
        set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    if (cal.timeInMillis > ms) cal.add(Calendar.WEEK_OF_YEAR, -1)
    return cal.timeInMillis
}

/** 1 = Monday … 7 = Sunday for the day containing [ms]. */
fun dayOfWeekOf(ms: Long): Int {
    val cal = Calendar.getInstance().apply { timeInMillis = ms }
    return when (cal.get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY -> 1
        Calendar.TUESDAY -> 2
        Calendar.WEDNESDAY -> 3
        Calendar.THURSDAY -> 4
        Calendar.FRIDAY -> 5
        Calendar.SATURDAY -> 6
        Calendar.SUNDAY -> 7
        else -> 1
    }
}
