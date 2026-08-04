package com.migul.treningsprogram.data.db.dao

import androidx.room.*
import com.migul.treningsprogram.data.db.entity.BodyMetric
import kotlinx.coroutines.flow.Flow

/**
 * Body-progress batch 2026-08-04 (brief 02). Mirrors [BodyMeasurementDao]'s surface exactly —
 * including its two ordering directions (`getAll()` DESC for the UI stream, `getAllOnce()` ASC for
 * export) — so the two series are handled identically everywhere they are consumed together.
 */
@Dao
interface BodyMetricDao {
    @Query("SELECT * FROM body_metrics ORDER BY dateMs DESC")
    fun getAll(): Flow<List<BodyMetric>>

    @Insert
    suspend fun insert(m: BodyMetric): Long

    @Delete
    suspend fun delete(m: BodyMetric)

    @Query("SELECT * FROM body_metrics ORDER BY dateMs ASC")
    suspend fun getAllOnce(): List<BodyMetric>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(metrics: List<BodyMetric>)

    @Query("DELETE FROM body_metrics")
    suspend fun deleteAll()
}
