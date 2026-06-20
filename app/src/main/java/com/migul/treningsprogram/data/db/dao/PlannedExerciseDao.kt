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

    @Query("SELECT MAX(weekStart) FROM planned_exercises")
    suspend fun getLatestWeekStart(): Long?

    @Update
    suspend fun update(exercise: PlannedExercise)
}
