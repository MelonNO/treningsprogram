package com.migul.treningsprogram.data.db.dao

import androidx.room.*
import com.migul.treningsprogram.data.db.entity.WorkoutSet
import kotlinx.coroutines.flow.Flow

data class ExercisePr(val exerciseName: String, val maxWeight: Float)

data class StrengthPoint(val dateMs: Long, val maxWeight: Float, val bestReps: Int)

/**
 * One exercise with the number of DISTINCT sessions it appears in (B03). Drives the
 * Progress-tab exercise picker's "most-trained first" ordering. Counts distinct sessions,
 * not total sets, so a session with many sets of the same exercise counts once.
 */
data class ExerciseSessionCount(val exerciseName: String, val sessionCount: Int)

data class WeekVolume(val weekStart: Long, val totalSets: Int)

data class MuscleVolume(val muscleGroup: String, val totalSets: Int)

/** Most recent completed working-set timestamp for one muscle group (recovery view, C4). */
data class MuscleLastTrained(val muscleGroup: String, val lastTrainedMs: Long)

/**
 * One exercise-session stimulus row for the weighted recovery model (U1).
 * Contains the exercise name, the session it was logged in, and the session timestamp.
 * Working-sets only (isWarmup=0), completed sessions only.
 */
data class ExerciseSessionRow(
    val exerciseName: String,
    val sessionId: Long,
    val sessionDateMs: Long
)

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

    /** Heaviest working set for [name] in any completed session strictly before [beforeMs]. */
    @Query("""
        SELECT MAX(ws.weightKg) FROM workout_sets ws
        JOIN workout_sessions s ON ws.sessionId = s.id
        WHERE ws.exerciseName = :name AND ws.isWarmup = 0
        AND s.isCompleted = 1 AND s.dateMs < :beforeMs
    """)
    suspend fun getMaxWeightBefore(name: String, beforeMs: Long): Float?

    /** Sets from the most recent completed session for [name] strictly before [beforeMs]. */
    @Query("""
        SELECT * FROM workout_sets
        WHERE exerciseName = :name AND isWarmup = 0
        AND sessionId = (
            SELECT ws2.sessionId FROM workout_sets ws2
            JOIN workout_sessions s ON ws2.sessionId = s.id
            WHERE ws2.exerciseName = :name AND s.isCompleted = 1 AND s.dateMs < :beforeMs
            ORDER BY s.dateMs DESC LIMIT 1
        )
        ORDER BY setNumber ASC
    """)
    suspend fun getLastSetsForExerciseBefore(name: String, beforeMs: Long): List<WorkoutSet>

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

    @Query("SELECT COALESCE(SUM(CAST(reps AS REAL) * weightKg), 0.0) FROM workout_sets WHERE isWarmup = 0")
    suspend fun getTotalVolumeKg(): Float

    @Query("SELECT COUNT(*) FROM workout_sets WHERE isWarmup = 0")
    suspend fun getTotalSets(): Int

    @Query("SELECT DISTINCT exerciseName FROM workout_sets ORDER BY exerciseName ASC")
    suspend fun getDistinctExerciseNames(): List<String>

    /**
     * Each exercise name with the number of distinct sessions it appears in (B03).
     * Mirrors the population source of [getDistinctExerciseNames] (all sets, no warm-up /
     * completed filter) so the picker lists exactly the same exercises — only reordered.
     * Ordering here is a stable starting point; final sort (count desc, alpha tie-break) is
     * applied by the pure [com.migul.treningsprogram.domain.ExercisePickerSort] helper.
     */
    @Query("""
        SELECT exerciseName AS exerciseName, COUNT(DISTINCT sessionId) AS sessionCount
        FROM workout_sets
        GROUP BY exerciseName
        ORDER BY exerciseName ASC
    """)
    suspend fun getExerciseSessionCounts(): List<ExerciseSessionCount>

    @Query("""
        SELECT s.dateMs AS dateMs, MAX(ws.weightKg) AS maxWeight, ws.reps AS bestReps
        FROM workout_sets ws JOIN workout_sessions s ON ws.sessionId = s.id
        WHERE ws.exerciseName = :name AND s.isCompleted = 1 AND ws.weightKg > 0 AND ws.isWarmup = 0
        GROUP BY ws.sessionId ORDER BY s.dateMs ASC
    """)
    suspend fun getStrengthHistory(name: String): List<StrengthPoint>

    @Query("""
        SELECT (s.dateMs / 604800000 * 604800000) AS weekStart, COUNT(*) AS totalSets
        FROM workout_sets ws JOIN workout_sessions s ON ws.sessionId = s.id
        WHERE ws.exerciseName = :name AND s.isCompleted = 1 AND ws.isWarmup = 0
        GROUP BY weekStart ORDER BY weekStart ASC
    """)
    suspend fun getWeeklyVolume(name: String): List<WeekVolume>

    @Query("""
        SELECT muscleGroup, COUNT(*) AS totalSets FROM workout_sets
        WHERE muscleGroup != '' AND isWarmup = 0 GROUP BY muscleGroup ORDER BY totalSets DESC
    """)
    suspend fun getMuscleGroupVolume(): List<MuscleVolume>

    /**
     * Most recent completed working-set timestamp per muscle group — drives the Home
     * recovery/freshness view (C4). Read-only; mirrors the join/filter style of
     * [getMuscleGroupVolume] / [getStrengthHistory] (warm-ups excluded, completed
     * sessions only, non-blank group). Groups never trained simply don't appear in the
     * result; the ViewModel fills those in as "untrained".
     */
    @Query("""
        SELECT ws.muscleGroup AS muscleGroup, MAX(s.dateMs) AS lastTrainedMs
        FROM workout_sets ws JOIN workout_sessions s ON ws.sessionId = s.id
        WHERE ws.isWarmup = 0 AND s.isCompleted = 1 AND ws.muscleGroup != ''
        GROUP BY ws.muscleGroup
    """)
    suspend fun getLastTrainedPerMuscleGroup(): List<MuscleLastTrained>

    @Query("""
        SELECT ws.muscleGroup AS muscleGroup, MAX(s.dateMs) AS lastTrainedMs
        FROM workout_sets ws JOIN workout_sessions s ON ws.sessionId = s.id
        WHERE ws.isWarmup = 0 AND s.isCompleted = 1 AND ws.muscleGroup != ''
        GROUP BY ws.muscleGroup
    """)
    fun observeLastTrainedPerMuscleGroup(): Flow<List<MuscleLastTrained>>

    @Query("""
        SELECT CASE WHEN CAST(reps AS INTEGER) <= 5 THEN 'Heavy (1-5)'
                    WHEN CAST(reps AS INTEGER) <= 12 THEN 'Moderate (6-12)'
                    ELSE 'Light (13+)' END AS label,
               COUNT(*) AS setCount FROM workout_sets WHERE isWarmup = 0 GROUP BY label
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
        WHERE ws.weightKg > 0 AND ws.isWarmup = 0 AND s.isCompleted = 1
        GROUP BY ws.exerciseName ORDER BY maxWeight DESC
    """)
    suspend fun getPRsWithDate(): List<ExercisePrWithDate>

    @Query("""
        SELECT ws.exerciseName AS exerciseName, MAX(ws.weightKg) AS maxWeight, s.dateMs AS dateMs
        FROM workout_sets ws JOIN workout_sessions s ON ws.sessionId = s.id
        WHERE ws.weightKg > 0 AND ws.isWarmup = 0 AND s.isCompleted = 1
        GROUP BY ws.exerciseName ORDER BY maxWeight DESC
    """)
    fun observePRsWithDate(): Flow<List<ExercisePrWithDate>>

    /**
     * All distinct (exerciseName, sessionId, sessionDateMs) rows from working sets in
     * completed sessions. Used by the weighted recovery model (U1) to build per-exercise
     * stimulus records and apply MuscleClassifier.finerMusclesFor for fine-grain taxonomy.
     *
     * Distinct per (exerciseName, sessionId) so that multiple sets of the same exercise
     * in the same session don't create duplicate records. Only the most recent sessions
     * matter for recovery; Room will stream updates whenever sets/sessions change.
     */
    @Query("""
        SELECT DISTINCT ws.exerciseName AS exerciseName,
               ws.sessionId AS sessionId,
               s.dateMs AS sessionDateMs
        FROM workout_sets ws
        JOIN workout_sessions s ON ws.sessionId = s.id
        WHERE ws.isWarmup = 0 AND s.isCompleted = 1 AND ws.exerciseName != ''
        ORDER BY s.dateMs DESC
    """)
    fun observeExerciseSessionRows(): Flow<List<ExerciseSessionRow>>

    @Query("DELETE FROM workout_sets")
    suspend fun deleteAll()

    @Query("SELECT * FROM workout_sets ORDER BY sessionId, exerciseName, setNumber")
    suspend fun getAllOnce(): List<WorkoutSet>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sets: List<WorkoutSet>)
}
