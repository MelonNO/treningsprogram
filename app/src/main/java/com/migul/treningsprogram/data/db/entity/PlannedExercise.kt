package com.migul.treningsprogram.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "planned_exercises")
data class PlannedExercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val weekStart: Long,
    val dayOfWeek: Int,   // 1 = Monday … 7 = Sunday
    val orderInDay: Int,
    val exerciseName: String,
    val sets: Int,
    val targetReps: String,
    val targetWeightKg: Float,
    val notes: String = "",
    val isLogged: Boolean = false,
    val actualWeightKg: Float = 0f,
    val actualReps: String = "",
    val actualSets: Int = 0,
    val recommendedRestSeconds: Int = 90,
    val exerciseDbId: String? = null,
    val matchConfidence: Float = -1f,
    val matchSource: String = "",
    val resolvedAt: Long = 0L,
    // B2: the model's own plain-language explanation of what changed and why for this week's
    // plan. Stamped onto every row of a week at save time (so any row of the week carries it);
    // "" for old plans generated before this feature, and for single-day regen that preserves
    // the week's existing rationale rather than producing a new one.
    val rationale: String = "",
    // E2: which named Program this plan row belongs to. Nullable so old rows (pre-E2) deserialize
    // cleanly; the migration backfills every existing row to the default program. weekStart /
    // dayOfWeek / orderInDay keying is kept WITHIN each program, so a (programId, weekStart,
    // dayOfWeek) tuple uniquely scopes a day's plan. Plan queries default to the active program.
    val programId: Long? = null
)
