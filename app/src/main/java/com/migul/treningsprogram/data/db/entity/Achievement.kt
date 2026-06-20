package com.migul.treningsprogram.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "achievements")
data class Achievement(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val emoji: String,
    val isUnlocked: Boolean = false,
    val unlockedAtMs: Long = 0
)
