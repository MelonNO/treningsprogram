package com.migul.treningsprogram.data.db.dao

import androidx.room.*
import com.migul.treningsprogram.data.db.entity.PlannedExercise
import kotlinx.coroutines.flow.Flow

@Dao
interface PlannedExerciseDao {
    @Query("SELECT * FROM planned_exercises WHERE weekStart = :weekStart ORDER BY dayOfWeek, orderInDay")
    fun getForWeek(weekStart: Long): Flow<List<PlannedExercise>>

    @Query("SELECT * FROM planned_exercises WHERE weekStart = :weekStart AND dayOfWeek = :day ORDER BY orderInDay")
    fun getForDay(weekStart: Long, day: Int): Flow<List<PlannedExercise>>

    @Query("SELECT * FROM planned_exercises WHERE weekStart = :weekStart AND dayOfWeek = :day ORDER BY orderInDay")
    suspend fun getForDayOnce(weekStart: Long, day: Int): List<PlannedExercise>

    @Query("SELECT * FROM planned_exercises WHERE weekStart = :weekStart ORDER BY dayOfWeek, orderInDay")
    suspend fun getForWeekOnce(weekStart: Long): List<PlannedExercise>

    // ── E2: program-scoped variants. The active program's id is threaded through by the repository
    //    so Home's today-view and the Program tab only ever see the active program's plan. ──────
    @Query("SELECT * FROM planned_exercises WHERE programId = :programId AND weekStart = :weekStart ORDER BY dayOfWeek, orderInDay")
    fun getForWeekInProgram(programId: Long, weekStart: Long): Flow<List<PlannedExercise>>

    @Query("SELECT * FROM planned_exercises WHERE programId = :programId AND weekStart = :weekStart AND dayOfWeek = :day ORDER BY orderInDay")
    fun getForDayInProgram(programId: Long, weekStart: Long, day: Int): Flow<List<PlannedExercise>>

    @Query("SELECT * FROM planned_exercises WHERE programId = :programId AND weekStart = :weekStart ORDER BY dayOfWeek, orderInDay")
    suspend fun getForWeekInProgramOnce(programId: Long, weekStart: Long): List<PlannedExercise>

    @Query("SELECT * FROM planned_exercises WHERE programId = :programId AND weekStart = :weekStart AND dayOfWeek = :day ORDER BY orderInDay")
    suspend fun getForDayInProgramOnce(programId: Long, weekStart: Long, day: Int): List<PlannedExercise>

    @Query("SELECT MAX(weekStart) FROM planned_exercises WHERE programId = :programId")
    suspend fun getLatestWeekStartInProgram(programId: Long): Long?

    @Query("DELETE FROM planned_exercises WHERE programId = :programId AND weekStart = :weekStart")
    suspend fun deleteForWeekInProgram(programId: Long, weekStart: Long)

    @Query("DELETE FROM planned_exercises WHERE programId = :programId AND weekStart = :weekStart AND dayOfWeek = :day")
    suspend fun deleteForDayInProgram(programId: Long, weekStart: Long, day: Int)

    @Query("DELETE FROM planned_exercises WHERE programId = :programId")
    suspend fun deleteForProgram(programId: Long)

    @Insert
    suspend fun insertAll(exercises: List<PlannedExercise>)

    @Query("DELETE FROM planned_exercises WHERE weekStart = :weekStart")
    suspend fun deleteForWeek(weekStart: Long)

    @Query("DELETE FROM planned_exercises WHERE weekStart = :weekStart AND dayOfWeek = :day")
    suspend fun deleteForDay(weekStart: Long, day: Int)

    @Query("SELECT MAX(weekStart) FROM planned_exercises")
    suspend fun getLatestWeekStart(): Long?

    @Update
    suspend fun update(exercise: PlannedExercise)

    @Query("SELECT * FROM planned_exercises ORDER BY weekStart, dayOfWeek, orderInDay")
    suspend fun getAllOnce(): List<PlannedExercise>

    @Query("DELETE FROM planned_exercises")
    suspend fun deleteAll()

    @Query("SELECT * FROM planned_exercises WHERE resolvedAt = 0")
    suspend fun getUnresolved(): List<PlannedExercise>

    @Query("UPDATE planned_exercises SET exerciseDbId = :dbId, matchConfidence = :confidence, matchSource = :source, resolvedAt = :resolvedAt WHERE exerciseName = :name")
    suspend fun bindByName(name: String, dbId: String?, confidence: Float, source: String, resolvedAt: Long)
}
