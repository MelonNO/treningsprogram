package com.migul.treningsprogram.data.db.entity

import androidx.room.Entity
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
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val exerciseName: String,
    val muscleGroup: String = "",
    val setNumber: Int,
    val reps: Int,
    val weightKg: Float,
    val isWarmup: Boolean = false,
    val rpeLabel: String = "",
    val loggedAtMs: Long = 0L   // wall-clock time the set was logged; 0 = legacy/unknown
)
