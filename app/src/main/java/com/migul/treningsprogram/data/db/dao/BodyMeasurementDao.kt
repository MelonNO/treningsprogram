package com.migul.treningsprogram.data.db.dao

import androidx.room.*
import com.migul.treningsprogram.data.db.entity.BodyMeasurement
import kotlinx.coroutines.flow.Flow

@Dao
interface BodyMeasurementDao {
    @Query("SELECT * FROM body_measurements ORDER BY dateMs DESC")
    fun getAll(): Flow<List<BodyMeasurement>>

    @Insert
    suspend fun insert(m: BodyMeasurement): Long

    @Delete
    suspend fun delete(m: BodyMeasurement)

    @Query("SELECT * FROM body_measurements ORDER BY dateMs ASC")
    suspend fun getAllOnce(): List<BodyMeasurement>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(measurements: List<BodyMeasurement>)

    @Query("DELETE FROM body_measurements")
    suspend fun deleteAll()
}
