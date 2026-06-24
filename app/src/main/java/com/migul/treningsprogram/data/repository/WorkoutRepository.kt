package com.migul.treningsprogram.data.repository

import androidx.room.withTransaction
import com.migul.treningsprogram.data.ExerciseDbResolver
import com.migul.treningsprogram.data.ResolveHints
import com.migul.treningsprogram.data.backup.BackupScheduler
import com.migul.treningsprogram.data.db.AppDatabase
import com.migul.treningsprogram.data.db.dao.*
import com.migul.treningsprogram.data.db.entity.*
import com.migul.treningsprogram.domain.model.ExerciseRecap
import com.migul.treningsprogram.domain.model.SessionPacing
import com.migul.treningsprogram.domain.model.SessionRecap
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
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
    private val backupScheduler: BackupScheduler
) {

    companion object {
        /** Name used for the auto-created default program on fresh installs. */
        const val DEFAULT_PROGRAM_NAME = "My Program"
    }
    val allSessions: Flow<List<WorkoutSession>> = sessionDao.getAllSessions()
    val allExercises: Flow<List<Exercise>> = exerciseDao.getAllExercises()

    fun getAllCompletedSessions(): Flow<List<WorkoutSession>> = sessionDao.getAllCompleted()

    suspend fun updateSession(session: WorkoutSession) = sessionDao.update(session)

    suspend fun deleteSet(set: WorkoutSet) = setDao.delete(set)

    suspend fun getDistinctExerciseNames(): List<String> = setDao.getDistinctExerciseNames()

    suspend fun getStrengthHistory(name: String): List<StrengthPoint> = setDao.getStrengthHistory(name)

    suspend fun getWeeklyVolume(name: String): List<WeekVolume> = setDao.getWeeklyVolume(name)

    suspend fun getMuscleGroupVolume(): List<MuscleVolume> = setDao.getMuscleGroupVolume()

    /** Last-trained timestamp per muscle group, live — drives the Home recovery view (C4). */
    fun observeLastTrainedPerMuscleGroup(): Flow<List<MuscleLastTrained>> =
        setDao.observeLastTrainedPerMuscleGroup()

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

    suspend fun saveDayPlan(weekStart: Long, dayOfWeek: Int, exercises: List<PlannedExercise>) {
        val programId = ensureActiveProgramId()
        db.withTransaction {
            // B2: single-day regen must PRESERVE the week's rationale. The week's rationale is
            // stamped on every row (see savePlan); read it off any OTHER row of the same week and
            // carry it forward onto the new day's rows so the week stays consistent and the
            // Program tab keeps showing the same "why your program changed" text.
            val weekRationale = plannedDao.getForWeekInProgramOnce(programId, weekStart)
                .firstOrNull { it.dayOfWeek != dayOfWeek && it.rationale.isNotBlank() }
                ?.rationale ?: ""
            plannedDao.deleteForDayInProgram(programId, weekStart, dayOfWeek)
            plannedDao.insertAll(exercises.map { it.copy(rationale = weekRationale, programId = programId) })
        }
        // A regenerated/edited day plan is user data worth backing up.
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
