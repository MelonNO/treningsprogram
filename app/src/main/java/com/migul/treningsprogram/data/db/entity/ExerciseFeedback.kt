package com.migul.treningsprogram.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

/**
 * Item 05 (2026-08-06) — one piece of user feedback about one exercise ("too hard — can't do it
 * properly yet", optionally with the user's own words).
 *
 * Keyed by the exercise NAME, the same identity the rest of the app uses for per-exercise user data
 * (`WorkoutSet.exerciseName`, [ExerciseNote]) — so AI-named and custom "Add anyway" exercises can
 * carry feedback too. Lookups are case-insensitive (COLLATE NOCASE in the DAO); the stored key is
 * the trimmed name as first written.
 *
 * **One row per exercise** (assumption A2 — never put to the user): giving feedback again on the
 * same exercise REPLACES the previous entry rather than accumulating a log.
 *
 * [reasonKey] is a stable key from
 * [com.migul.treningsprogram.domain.ExerciseFeedbackCatalog], never display text, so wording can
 * change without rewriting stored rows or breaking older backups.
 *
 * [updatedAtMs] is when the feedback was given (refreshed on replacement). It does double duty: the
 * backup merge rule uses it to pick a winner when both devices have feedback for the same exercise,
 * and the generator is told how old the feedback is so a six-month-old "I can't balance on this"
 * fades in weight once the user has been training the ability (decision D3).
 */
@Entity(tableName = "exercise_feedback")
data class ExerciseFeedback(
    @PrimaryKey @SerializedName("exerciseName") val exerciseName: String,
    @SerializedName("reasonKey") val reasonKey: String,
    @SerializedName("note") val note: String = "",
    @SerializedName("updatedAtMs") val updatedAtMs: Long = 0L
)
