package com.migul.treningsprogram.data.db.dao

import androidx.room.*
import com.migul.treningsprogram.data.db.entity.GymPreset
import kotlinx.coroutines.flow.Flow

@Dao
interface GymPresetDao {
    @Query("SELECT * FROM gym_presets ORDER BY id ASC")
    fun getAll(): Flow<List<GymPreset>>

    @Query("SELECT * FROM gym_presets WHERE id = :id")
    suspend fun getById(id: Long): GymPreset?

    @Query("SELECT COUNT(*) FROM gym_presets")
    suspend fun count(): Int

    @Insert
    suspend fun insert(preset: GymPreset): Long

    @Update
    suspend fun update(preset: GymPreset)

    @Delete
    suspend fun delete(preset: GymPreset)

    @Query("DELETE FROM gym_presets")
    suspend fun deleteAll()

    @Query("SELECT * FROM gym_presets ORDER BY id ASC")
    suspend fun getAllOnce(): List<GymPreset>

    /** Insert preserving the provided id (used by backup restore to keep preset id stability). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWithId(preset: GymPreset)
}
