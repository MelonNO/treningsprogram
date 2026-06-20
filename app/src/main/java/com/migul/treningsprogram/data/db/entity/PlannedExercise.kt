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
    val recommendedRestSeconds: Int = 90
)
