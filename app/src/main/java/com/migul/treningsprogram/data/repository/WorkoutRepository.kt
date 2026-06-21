package com.migul.treningsprogram.data.repository

import com.migul.treningsprogram.data.db.AppDatabase
import com.migul.treningsprogram.data.db.dao.*
import com.migul.treningsprogram.data.db.entity.*
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkoutRepository @Inject constructor(
    private val sessionDao: WorkoutSessionDao,
    private val setDao: WorkoutSetDao,
    private val exerciseDao: ExerciseDao,
    private val plannedDao: PlannedExerciseDao
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

    suspend fun ensureExercisesPopulated() {
        if (exerciseDao.count() == 0) {
            exerciseDao.insertAll(AppDatabase.DEFAULT_EXERCISES)
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
        return true
    }

    suspend fun deleteSession(sessionId: Long) {
        val session = sessionDao.getActiveSession()?.takeIf { it.id == sessionId }
            ?: return
        sessionDao.delete(session)
    }

    suspend fun getActiveSession(): WorkoutSession? = sessionDao.getActiveSession()

    fun getSetsForSession(sessionId: Long): Flow<List<WorkoutSet>> =
        setDao.getSetsForSession(sessionId)

    suspend fun getSetsForSessionOnce(sessionId: Long): List<WorkoutSet> =
        setDao.getSetsForSessionOnce(sessionId)

    suspend fun addSet(set: WorkoutSet): Long = setDao.insert(set)

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
    }

    suspend fun getLatestPlanWeekStart(): Long? = plannedDao.getLatestWeekStart()

    suspend fun updatePlannedExercise(exercise: PlannedExercise) = plannedDao.update(exercise)

    suspend fun resetAllWorkouts() {
        setDao.deleteAll()
        sessionDao.deleteAll()
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
