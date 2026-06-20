package com.migul.treningsprogram.data.db.dao

import androidx.room.*
import com.migul.treningsprogram.data.db.entity.WorkoutSession
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutSessionDao {
    @Query("SELECT * FROM workout_sessions ORDER BY dateMs DESC")
    fun getAllSessions(): Flow<List<WorkoutSession>>

    @Query("""
        SELECT * FROM workout_sessions WHERE isCompleted = 1
        AND id IN (SELECT DISTINCT sessionId FROM workout_sets WHERE isWarmup = 0)
        ORDER BY dateMs DESC
    """)
    fun getAllCompleted(): Flow<List<WorkoutSession>>

    @Query("""
        SELECT * FROM workout_sessions WHERE isCompleted = 1
        AND id IN (SELECT DISTINCT sessionId FROM workout_sets WHERE isWarmup = 0)
        ORDER BY dateMs DESC LIMIT :limit
    """)
    suspend fun getRecentCompleted(limit: Int): List<WorkoutSession>

    @Delete
    suspend fun delete(session: WorkoutSession)

    @Query("SELECT * FROM workout_sessions WHERE isCompleted = 0 ORDER BY dateMs DESC LIMIT 1")
    suspend fun getActiveSession(): WorkoutSession?

    @Insert
    suspend fun insert(session: WorkoutSession): Long

    @Update
    suspend fun update(session: WorkoutSession)

    @Query("DELETE FROM workout_sessions")
    suspend fun deleteAll()
}
