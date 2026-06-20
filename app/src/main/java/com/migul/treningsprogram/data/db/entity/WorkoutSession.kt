package com.migul.treningsprogram.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "workout_sessions")
data class WorkoutSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateMs: Long,
    val durationMinutes: Int = 0,
    val notes: String = "",
    val isCompleted: Boolean = false
)
