package com.migul.treningsprogram.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "exercises")
data class Exercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val muscleGroup: String,
    val equipment: String = "",
    val exerciseDbId: String? = null,
    val matchConfidence: Float = -1f,
    val matchSource: String = "",
    val resolvedAt: Long = 0L
)
