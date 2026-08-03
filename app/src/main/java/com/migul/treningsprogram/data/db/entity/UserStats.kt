package com.migul.treningsprogram.data.db.entity

import androidx.room.Entity
import com.google.gson.annotations.SerializedName
import androidx.room.PrimaryKey

@Entity(tableName = "user_stats")
data class UserStats(
    @PrimaryKey @SerializedName("id") val id: Int = 1,
    @SerializedName("totalXp") val totalXp: Int = 0,
    @SerializedName("level") val level: Int = 1,
    @SerializedName("currentStreak") val currentStreak: Int = 0,
    @SerializedName("bestStreak") val bestStreak: Int = 0,
    @SerializedName("totalWorkouts") val totalWorkouts: Int = 0,
    @SerializedName("totalPrs") val totalPrs: Int = 0,
    @SerializedName("lastWorkoutDateMs") val lastWorkoutDateMs: Long = 0
)
