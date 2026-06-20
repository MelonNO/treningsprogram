package com.migul.treningsprogram.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_stats")
data class UserStats(
    @PrimaryKey val id: Int = 1,
    val totalXp: Int = 0,
    val level: Int = 1,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val totalWorkouts: Int = 0,
    val totalPrs: Int = 0,
    val lastWorkoutDateMs: Long = 0
)
