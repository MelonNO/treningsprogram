package com.migul.treningsprogram.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "gym_presets")
data class GymPreset(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val equipmentJson: String = "[]",
    val notes: String = ""
)
