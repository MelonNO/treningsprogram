package com.migul.treningsprogram.data.db.entity

import androidx.room.Entity
import com.google.gson.annotations.SerializedName
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "workout_sets",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSession::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId")]
)
data class WorkoutSet(
    @PrimaryKey(autoGenerate = true) @SerializedName("id") val id: Long = 0,
    @SerializedName("sessionId") val sessionId: Long,
    @SerializedName("exerciseName") val exerciseName: String,
    @SerializedName("muscleGroup") val muscleGroup: String = "",
    @SerializedName("setNumber") val setNumber: Int,
    @SerializedName("reps") val reps: Int,
    @SerializedName("weightKg") val weightKg: Float,
    @SerializedName("isWarmup") val isWarmup: Boolean = false,
    @SerializedName("rpeLabel") val rpeLabel: String = "",
    @SerializedName("loggedAtMs") val loggedAtMs: Long = 0L   // wall-clock time the set was logged; 0 = legacy/unknown
)
