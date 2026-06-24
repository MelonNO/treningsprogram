package com.migul.treningsprogram.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.migul.treningsprogram.data.db.entity.XpEvent
import kotlinx.coroutines.flow.Flow

/**
 * U2: DAO for the forward-recorded XP-event log.
 */
@Dao
interface XpEventDao {
    @Insert
    suspend fun insert(e: XpEvent)

    /** XP log feed, newest first. */
    @Query("SELECT * FROM xp_events ORDER BY timestampMs DESC")
    fun observeAll(): Flow<List<XpEvent>>

    @Query("SELECT * FROM xp_events ORDER BY timestampMs DESC")
    suspend fun getAllOnce(): List<XpEvent>

    /** Reset parity: cleared by Factory Reset and "Reset workouts/stats". */
    @Query("DELETE FROM xp_events")
    suspend fun deleteAll()
}
