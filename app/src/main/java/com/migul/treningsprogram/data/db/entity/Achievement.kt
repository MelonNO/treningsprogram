package com.migul.treningsprogram.data.db.entity

import androidx.room.Entity
import com.google.gson.annotations.SerializedName
import androidx.room.PrimaryKey

@Entity(tableName = "achievements")
data class Achievement(
    @PrimaryKey @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("description") val description: String,
    @SerializedName("emoji") val emoji: String,
    @SerializedName("isUnlocked") val isUnlocked: Boolean = false,
    @SerializedName("unlockedAtMs") val unlockedAtMs: Long = 0
)
