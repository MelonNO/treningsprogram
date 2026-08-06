package com.migul.treningsprogram.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.migul.treningsprogram.data.db.entity.ExerciseFeedback
import kotlinx.coroutines.flow.Flow

/**
 * Item 05 (2026-08-06) — per-exercise user feedback. Mirrors `ExerciseNoteDao`: same name-keyed,
 * case-insensitive identity, same upsert-replaces semantics (assumption A2 — one row per exercise),
 * same deleteAll/insertAll pair for backup restore.
 */
@Dao
interface ExerciseFeedbackDao {

    /** The feedback for one exercise, matched case-insensitively on the name identity. */
    @Query("SELECT * FROM exercise_feedback WHERE exerciseName = :name COLLATE NOCASE LIMIT 1")
    suspend fun getForExercise(name: String): ExerciseFeedback?

    @Query("SELECT * FROM exercise_feedback ORDER BY updatedAtMs DESC")
    suspend fun getAllOnce(): List<ExerciseFeedback>

    /** Drives the Settings → Training review-and-undo screen (improvement D), newest first. */
    @Query("SELECT * FROM exercise_feedback ORDER BY updatedAtMs DESC")
    fun observeAll(): Flow<List<ExerciseFeedback>>

    /** Upsert — giving feedback again on the same exercise replaces the previous entry. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(feedback: ExerciseFeedback)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(feedback: List<ExerciseFeedback>)

    /** Removing feedback deletes the row entirely — exercises without feedback have no row. */
    @Query("DELETE FROM exercise_feedback WHERE exerciseName = :name COLLATE NOCASE")
    suspend fun deleteForExercise(name: String)

    @Query("DELETE FROM exercise_feedback")
    suspend fun deleteAll()
}
