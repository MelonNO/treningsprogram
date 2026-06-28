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

    /**
     * History-timeline rows: every completed workout that has at least one working set (the existing
     * "real workout" definition, unchanged) PLUS every auto-logged REST/MISSED placeholder. Used only
     * by the History "Log" tab so rest/missed days are visible alongside workouts. Placeholders carry
     * no sets, so they are surfaced by their `kind` rather than by the working-set sub-select.
     */
    @Query("""
        SELECT * FROM workout_sessions
        WHERE kind IN ('REST', 'MISSED')
           OR (isCompleted = 1
               AND id IN (SELECT DISTINCT sessionId FROM workout_sets WHERE isWarmup = 0))
        ORDER BY dateMs DESC
    """)
    fun getHistoryTimeline(): Flow<List<WorkoutSession>>

    @Delete
    suspend fun delete(session: WorkoutSession)

    @Query("SELECT * FROM workout_sessions WHERE isCompleted = 0 ORDER BY dateMs DESC LIMIT 1")
    suspend fun getActiveSession(): WorkoutSession?

    @Query("SELECT * FROM workout_sessions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): WorkoutSession?

    @Insert
    suspend fun insert(session: WorkoutSession): Long

    @Update
    suspend fun update(session: WorkoutSession)

    @Query("DELETE FROM workout_sessions")
    suspend fun deleteAll()

    @Query("SELECT * FROM workout_sessions ORDER BY dateMs ASC")
    suspend fun getAllOnce(): List<WorkoutSession>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sessions: List<WorkoutSession>)
}
