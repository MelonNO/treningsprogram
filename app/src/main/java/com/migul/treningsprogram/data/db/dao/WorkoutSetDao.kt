package com.migul.treningsprogram.data.db.dao

import androidx.room.*
import com.migul.treningsprogram.data.db.entity.WorkoutSet
import kotlinx.coroutines.flow.Flow

data class ExercisePr(val exerciseName: String, val maxWeight: Float)

data class StrengthPoint(val dateMs: Long, val maxWeight: Float, val bestReps: Int)

data class WeekVolume(val weekStart: Long, val totalSets: Int)

data class MuscleVolume(val muscleGroup: String, val totalSets: Int)

data class RepRange(val label: String, val setCount: Int)

data class ExercisePrWithDate(val exerciseName: String, val maxWeight: Float, val dateMs: Long)

@Dao
interface WorkoutSetDao {
    @Query("SELECT * FROM workout_sets WHERE sessionId = :sessionId ORDER BY exerciseName, setNumber")
    fun getSetsForSession(sessionId: Long): Flow<List<WorkoutSet>>

    @Query("SELECT * FROM workout_sets WHERE sessionId = :sessionId ORDER BY exerciseName, setNumber")
    suspend fun getSetsForSessionOnce(sessionId: Long): List<WorkoutSet>

    @Query("""
        SELECT * FROM workout_sets
        WHERE exerciseName = :exerciseName
        AND isWarmup = 0
        AND sessionId = (
            SELECT ws2.sessionId FROM workout_sets ws2
            JOIN workout_sessions s ON ws2.sessionId = s.id
            WHERE ws2.exerciseName = :exerciseName
            AND s.isCompleted = 1
            AND ws2.sessionId != :excludeSessionId
            ORDER BY s.dateMs DESC
            LIMIT 1
        )
        ORDER BY setNumber ASC
    """)
    suspend fun getLastSetsForExercise(exerciseName: String, excludeSessionId: Long): List<WorkoutSet>

    @Query("SELECT COUNT(*) FROM workout_sets WHERE sessionId = :sessionId AND isWarmup = 0")
    suspend fun getWorkingSetCount(sessionId: Long): Int

    @Insert
    suspend fun insert(set: WorkoutSet): Long

    @Update
    suspend fun update(set: WorkoutSet)

    @Delete
    suspend fun delete(set: WorkoutSet)

    @Query("""
        SELECT MAX(weightKg) FROM workout_sets
        WHERE exerciseName = :exerciseName AND sessionId != :excludeSessionId AND isWarmup = 0
    """)
    suspend fun getPreviousMaxWeight(exerciseName: String, excludeSessionId: Long): Float?

    @Query("""
        SELECT exerciseName, MAX(weightKg) AS maxWeight
        FROM workout_sets
        WHERE weightKg > 0 AND isWarmup = 0
        GROUP BY exerciseName
        ORDER BY maxWeight DESC
        LIMIT 10
    """)
    suspend fun getTopPersonalRecords(): List<ExercisePr>

    @Query("SELECT COALESCE(SUM(CAST(reps AS REAL) * weightKg), 0.0) FROM workout_sets")
    suspend fun getTotalVolumeKg(): Float

    @Query("SELECT COUNT(*) FROM workout_sets")
    suspend fun getTotalSets(): Int

    @Query("SELECT DISTINCT exerciseName FROM workout_sets ORDER BY exerciseName ASC")
    suspend fun getDistinctExerciseNames(): List<String>

    @Query("""
        SELECT s.dateMs AS dateMs, MAX(ws.weightKg) AS maxWeight, ws.reps AS bestReps
        FROM workout_sets ws JOIN workout_sessions s ON ws.sessionId = s.id
        WHERE ws.exerciseName = :name AND s.isCompleted = 1 AND ws.weightKg > 0
        GROUP BY ws.sessionId ORDER BY s.dateMs ASC
    """)
    suspend fun getStrengthHistory(name: String): List<StrengthPoint>

    @Query("""
        SELECT (s.dateMs / 604800000 * 604800000) AS weekStart, COUNT(*) AS totalSets
        FROM workout_sets ws JOIN workout_sessions s ON ws.sessionId = s.id
        WHERE ws.exerciseName = :name AND s.isCompleted = 1
        GROUP BY weekStart ORDER BY weekStart ASC
    """)
    suspend fun getWeeklyVolume(name: String): List<WeekVolume>

    @Query("""
        SELECT muscleGroup, COUNT(*) AS totalSets FROM workout_sets
        WHERE muscleGroup != '' GROUP BY muscleGroup ORDER BY totalSets DESC
    """)
    suspend fun getMuscleGroupVolume(): List<MuscleVolume>

    @Query("""
        SELECT CASE WHEN CAST(reps AS INTEGER) <= 5 THEN 'Heavy (1-5)'
                    WHEN CAST(reps AS INTEGER) <= 12 THEN 'Moderate (6-12)'
                    ELSE 'Light (13+)' END AS label,
               COUNT(*) AS setCount FROM workout_sets GROUP BY label
    """)
    suspend fun getRepRangeDistribution(): List<RepRange>

    @Query("""
        SELECT DISTINCT (dateMs / 86400000) AS dayEpoch FROM workout_sessions
        WHERE isCompleted = 1 ORDER BY dayEpoch DESC LIMIT 120
    """)
    suspend fun getTrainingDayEpochs(): List<Long>

    @Query("""
        SELECT ws.exerciseName AS exerciseName, MAX(ws.weightKg) AS maxWeight, s.dateMs AS dateMs
        FROM workout_sets ws JOIN workout_sessions s ON ws.sessionId = s.id
        WHERE ws.weightKg > 0 AND s.isCompleted = 1
        GROUP BY ws.exerciseName ORDER BY maxWeight DESC
    """)
    suspend fun getPRsWithDate(): List<ExercisePrWithDate>

    @Query("""
        SELECT ws.exerciseName AS exerciseName, MAX(ws.weightKg) AS maxWeight, s.dateMs AS dateMs
        FROM workout_sets ws JOIN workout_sessions s ON ws.sessionId = s.id
        WHERE ws.weightKg > 0 AND s.isCompleted = 1
        GROUP BY ws.exerciseName ORDER BY maxWeight DESC
    """)
    fun observePRsWithDate(): Flow<List<ExercisePrWithDate>>

    @Query("DELETE FROM workout_sets")
    suspend fun deleteAll()

    @Query("SELECT * FROM workout_sets ORDER BY sessionId, exerciseName, setNumber")
    suspend fun getAllOnce(): List<WorkoutSet>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sets: List<WorkoutSet>)
}
