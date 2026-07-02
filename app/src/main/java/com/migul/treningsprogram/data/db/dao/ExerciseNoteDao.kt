package com.migul.treningsprogram.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.migul.treningsprogram.data.db.entity.ExerciseNote

@Dao
interface ExerciseNoteDao {

    /** The note for one exercise, matched case-insensitively on the name identity. */
    @Query("SELECT * FROM exercise_notes WHERE exerciseName = :name COLLATE NOCASE LIMIT 1")
    suspend fun getForExercise(name: String): ExerciseNote?

    @Query("SELECT * FROM exercise_notes")
    suspend fun getAllOnce(): List<ExerciseNote>

    /** Upsert — a note edit replaces the previous text for that exercise. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: ExerciseNote)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(notes: List<ExerciseNote>)

    /** Clearing a note removes the row entirely — exercises without notes have no row at all. */
    @Query("DELETE FROM exercise_notes WHERE exerciseName = :name COLLATE NOCASE")
    suspend fun deleteForExercise(name: String)

    @Query("DELETE FROM exercise_notes")
    suspend fun deleteAll()
}
