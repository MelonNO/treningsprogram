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
}
