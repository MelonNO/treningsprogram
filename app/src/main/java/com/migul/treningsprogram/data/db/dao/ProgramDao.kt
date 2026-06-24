package com.migul.treningsprogram.data.db.dao

import androidx.room.*
import com.migul.treningsprogram.data.db.entity.Program
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgramDao {

    @Query("SELECT * FROM programs ORDER BY createdAtMs ASC")
    fun observeAll(): Flow<List<Program>>

    @Query("SELECT * FROM programs ORDER BY createdAtMs ASC")
    suspend fun getAllOnce(): List<Program>

    @Query("SELECT * FROM programs WHERE isActive = 1 LIMIT 1")
    fun observeActive(): Flow<Program?>

    @Query("SELECT * FROM programs WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveOnce(): Program?

    @Query("SELECT * FROM programs WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): Program?

    @Query("SELECT COUNT(*) FROM programs")
    suspend fun count(): Int

    @Insert
    suspend fun insert(program: Program): Long

    /** Restore path: insert preserving the explicit id (the merge engine assigns final ids). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWithId(program: Program)

    @Query("DELETE FROM programs")
    suspend fun deleteAll()

    @Update
    suspend fun update(program: Program)

    @Delete
    suspend fun delete(program: Program)

    @Query("UPDATE programs SET isActive = 0")
    suspend fun clearActiveFlags()

    @Query("UPDATE programs SET isActive = 1 WHERE id = :id")
    suspend fun markActive(id: Long)

    @Query("UPDATE programs SET isDeloadActive = :active WHERE id = :id")
    suspend fun setDeload(id: Long, active: Boolean)

    /**
     * Make [id] the one and only active program, atomically. Clears every other program's
     * active flag first so the "exactly one active" invariant always holds.
     */
    @Transaction
    suspend fun setActive(id: Long) {
        clearActiveFlags()
        markActive(id)
    }
}
