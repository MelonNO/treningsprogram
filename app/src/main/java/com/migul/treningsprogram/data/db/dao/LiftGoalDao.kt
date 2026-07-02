package com.migul.treningsprogram.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.migul.treningsprogram.data.db.entity.LiftGoal
import kotlinx.coroutines.flow.Flow

@Dao
interface LiftGoalDao {

    /** Every goal, newest first — Profile's list (active + achieved history) filters by status. */
    @Query("SELECT * FROM lift_goals ORDER BY createdAtMs DESC")
    fun observeAll(): Flow<List<LiftGoal>>

    @Query("SELECT * FROM lift_goals ORDER BY createdAtMs DESC")
    suspend fun getAllOnce(): List<LiftGoal>

    @Query("SELECT * FROM lift_goals WHERE status = 'active' ORDER BY createdAtMs DESC")
    suspend fun getActiveOnce(): List<LiftGoal>

    /** Active goal for one exercise (case-insensitive name identity, same as notes/history). */
    @Query("SELECT * FROM lift_goals WHERE exerciseName = :name COLLATE NOCASE AND status = 'active' LIMIT 1")
    suspend fun getActiveForExercise(name: String): LiftGoal?

    @Insert
    suspend fun insert(goal: LiftGoal): Long

    @Insert
    suspend fun insertAll(goals: List<LiftGoal>)

    @Update
    suspend fun update(goal: LiftGoal)

    @Delete
    suspend fun delete(goal: LiftGoal)

    @Query("DELETE FROM lift_goals")
    suspend fun deleteAll()
}
