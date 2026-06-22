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
