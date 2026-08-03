package com.migul.treningsprogram.data.db.entity

import androidx.room.Entity
import com.google.gson.annotations.SerializedName
import androidx.room.PrimaryKey

/**
 * N7 — one small persistent setup note per exercise ("bench pin height 7, belt on top sets").
 *
 * Keyed by the exercise NAME — the same identity the app uses for history/swaps
 * (WorkoutSet.exerciseName), so custom "Add anyway" exercises can carry notes too (A-S2) and a
 * swapped-in exercise shows ITS note, not the replaced one's. Lookups are case-insensitive
 * (COLLATE NOCASE in the DAO); the stored key is the trimmed name as first written.
 *
 * [updatedAtMs] exists for the backup merge rule: when both devices have a note for the same
 * exercise, the most recently edited one wins.
 */
@Entity(tableName = "exercise_notes")
data class ExerciseNote(
    @PrimaryKey @SerializedName("exerciseName") val exerciseName: String,
    @SerializedName("note") val note: String,
    @SerializedName("updatedAtMs") val updatedAtMs: Long = 0L
)
