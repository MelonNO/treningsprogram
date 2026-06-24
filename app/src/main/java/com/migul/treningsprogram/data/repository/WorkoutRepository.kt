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
import kotlinx.coroutines.flow.Flow
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
    private val resolver: ExerciseDbResolver,
    private val backupScheduler: BackupScheduler
) {
    val allSessions: Flow<List<WorkoutSession>> = sessionDao.getAllSessions()
    val allExercises: Flow<List<Exercise>> = exerciseDao.getAllExercises()

    fun getAllCompletedSessions(): Flow<List<WorkoutSession>> = sessionDao.getAllCompleted()

    suspend fun updateSession(session: WorkoutSession) = sessionDao.update(session)

    suspend fun deleteSet(set: WorkoutSet) = setDao.delete(set)

    suspend fun getDistinctExerciseNames(): List<String> = setDao.getDistinctExerciseNames()

    suspend fun getStrengthHistory(name: String): List<StrengthPoint> = setDao.getStrengthHistory(name)

    suspend fun getWeeklyVolume(name: String): List<WeekVolume> = setDao.getWeeklyVolume(name)

    suspend fun getMuscleGroupVolume(): List<MuscleVolume> = setDao.getMuscleGroupVolume()

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

    fun getPlannedForDay(weekStart: Long, dayOfWeek: Int): Flow<List<PlannedExercise>> =
        plannedDao.getForDay(weekStart, dayOfWeek)

    fun getPlannedForWeek(weekStart: Long): Flow<List<PlannedExercise>> =
        plannedDao.getForWeek(weekStart)

    suspend fun savePlan(weekStart: Long, exercises: List<PlannedExercise>) {
        plannedDao.deleteForWeek(weekStart)
        plannedDao.insertAll(exercises)
        // A newly generated weekly plan is user data worth backing up.
        backupScheduler.requestBackup()
    }

    suspend fun saveDayPlan(weekStart: Long, dayOfWeek: Int, exercises: List<PlannedExercise>) {
        db.withTransaction {
            plannedDao.deleteForDay(weekStart, dayOfWeek)
            plannedDao.insertAll(exercises)
        }
        // A regenerated/edited day plan is user data worth backing up.
        backupScheduler.requestBackup()
    }

    suspend fun getLatestPlanWeekStart(): Long? = plannedDao.getLatestWeekStart()

    suspend fun getAllPlannedOnce(): List<PlannedExercise> = plannedDao.getAllOnce()

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

        // Adherence — match against the plan for this session's week + day, if any.
        val planned = plannedDao.getForDayOnce(mondayOf(session.dateMs), dayOfWeekOf(session.dateMs))
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
