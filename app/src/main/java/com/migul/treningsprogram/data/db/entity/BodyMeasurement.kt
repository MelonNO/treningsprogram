package com.migul.treningsprogram.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "body_measurements")
data class BodyMeasurement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateMs: Long,
    val weightKg: Float
)
