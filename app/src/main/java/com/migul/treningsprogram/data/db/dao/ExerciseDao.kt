package com.migul.treningsprogram.data.db.dao

import androidx.room.*
import com.migul.treningsprogram.data.db.entity.Exercise
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercises ORDER BY muscleGroup, name")
    fun getAllExercises(): Flow<List<Exercise>>

    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(exercises: List<Exercise>)

    @Query("SELECT * FROM exercises WHERE resolvedAt = 0")
    suspend fun getUnresolved(): List<Exercise>

    /** Local-DB exercise search for the quick-access "Add exercise" picker. */
    @Query("SELECT * FROM exercises WHERE name LIKE '%' || :query || '%' ORDER BY name LIMIT 50")
    suspend fun searchByName(query: String): List<Exercise>

    @Query("SELECT * FROM exercises WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): Exercise?

    @Query("UPDATE exercises SET exerciseDbId = :dbId, matchConfidence = :confidence, matchSource = :source, resolvedAt = :resolvedAt WHERE name = :name")
    suspend fun bindByName(name: String, dbId: String?, confidence: Float, source: String, resolvedAt: Long)

    @Query("SELECT * FROM exercises ORDER BY id ASC")
    suspend fun getAllExercisesOnce(): List<Exercise>

    @Query("DELETE FROM exercises")
    suspend fun deleteAll()
}
