package com.migul.treningsprogram.data.db.entity

import androidx.room.Entity
import com.google.gson.annotations.SerializedName
import androidx.room.PrimaryKey

@Entity(tableName = "exercises")
data class Exercise(
    @PrimaryKey(autoGenerate = true) @SerializedName("id") val id: Long = 0,
    @SerializedName("name") val name: String,
    @SerializedName("muscleGroup") val muscleGroup: String,
    @SerializedName("equipment") val equipment: String = "",
    @SerializedName("exerciseDbId") val exerciseDbId: String? = null,
    @SerializedName("matchConfidence") val matchConfidence: Float = -1f,
    @SerializedName("matchSource") val matchSource: String = "",
    @SerializedName("resolvedAt") val resolvedAt: Long = 0L
)
