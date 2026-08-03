package com.migul.treningsprogram.data.db.entity

import androidx.room.Entity
import com.google.gson.annotations.SerializedName
import androidx.room.PrimaryKey

@Entity(tableName = "planned_exercises")
data class PlannedExercise(
    @PrimaryKey(autoGenerate = true) @SerializedName("id") val id: Long = 0,
    @SerializedName("weekStart") val weekStart: Long,
    @SerializedName("dayOfWeek") val dayOfWeek: Int,   // 1 = Monday … 7 = Sunday
    @SerializedName("orderInDay") val orderInDay: Int,
    @SerializedName("exerciseName") val exerciseName: String,
    @SerializedName("sets") val sets: Int,
    @SerializedName("targetReps") val targetReps: String,
    @SerializedName("targetWeightKg") val targetWeightKg: Float,
    @SerializedName("notes") val notes: String = "",
    @SerializedName("isLogged") val isLogged: Boolean = false,
    @SerializedName("actualWeightKg") val actualWeightKg: Float = 0f,
    @SerializedName("actualReps") val actualReps: String = "",
    @SerializedName("actualSets") val actualSets: Int = 0,
    @SerializedName("recommendedRestSeconds") val recommendedRestSeconds: Int = 90,
    @SerializedName("exerciseDbId") val exerciseDbId: String? = null,
    @SerializedName("matchConfidence") val matchConfidence: Float = -1f,
    @SerializedName("matchSource") val matchSource: String = "",
    @SerializedName("resolvedAt") val resolvedAt: Long = 0L,
    // B2: the model's own plain-language explanation of what changed and why for this week's
    // plan. Stamped onto every row of a week at save time (so any row of the week carries it);
    // "" for old plans generated before this feature, and for single-day regen that preserves
    // the week's existing rationale rather than producing a new one.
    @SerializedName("rationale") val rationale: String = "",
    // E2: which named Program this plan row belongs to. Nullable so old rows (pre-E2) deserialize
    // cleanly; the migration backfills every existing row to the default program. weekStart /
    // dayOfWeek / orderInDay keying is kept WITHIN each program, so a (programId, weekStart,
    // dayOfWeek) tuple uniquely scopes a day's plan. Plan queries default to the active program.
    @SerializedName("programId") val programId: Long? = null
)
