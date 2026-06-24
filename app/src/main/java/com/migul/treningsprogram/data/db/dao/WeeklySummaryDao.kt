package com.migul.treningsprogram.data.db.dao

import androidx.room.*
import com.migul.treningsprogram.data.db.entity.WeeklySummary
import kotlinx.coroutines.flow.Flow

@Dao
interface WeeklySummaryDao {
    /** Scrollable history, newest first. */
    @Query("SELECT * FROM weekly_summaries ORDER BY createdAtMs DESC")
    fun observeAll(): Flow<List<WeeklySummary>>

    /** Once-per-week guard: 0 ⇒ no summary yet for this ISO week. */
    @Query("SELECT COUNT(*) FROM weekly_summaries WHERE weekKey = :weekKey")
    suspend fun countForWeek(weekKey: String): Int

    @Insert
    suspend fun insert(summary: WeeklySummary): Long

    @Query("SELECT * FROM weekly_summaries ORDER BY createdAtMs DESC")
    suspend fun getAllOnce(): List<WeeklySummary>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(summaries: List<WeeklySummary>)

    @Query("DELETE FROM weekly_summaries")
    suspend fun deleteAll()
}
