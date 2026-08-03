package com.migul.treningsprogram.data.db.entity

import androidx.room.Entity
import com.google.gson.annotations.SerializedName
import androidx.room.PrimaryKey

@Entity(tableName = "gym_presets")
data class GymPreset(
    @PrimaryKey(autoGenerate = true) @SerializedName("id") val id: Long = 0,
    @SerializedName("name") val name: String,
    @SerializedName("equipmentJson") val equipmentJson: String = "[]",
    @SerializedName("notes") val notes: String = "",
    // Plate-calculator profile (DB v18). All NULLABLE: null = "use the app default" (the user's
    // 50 mm home setup — 7 kg bar, home plate set, loadable dumbbells; see PlateProfile). Kept
    // nullable rather than defaulted so old backups (Gson leaves missing fields null) and the
    // additive migration both resolve identically through PlateProfile.from().
    @SerializedName("barWeightKg") val barWeightKg: Float? = null,
    @SerializedName("dumbbellBarWeightKg") val dumbbellBarWeightKg: Float? = null,
    @SerializedName("platesCsv") val platesCsv: String? = null,
    @SerializedName("loadableDumbbells") val loadableDumbbells: Boolean? = null,
    // Item 02 (DB v20): per-gym "exercises to avoid" — JSON array of exercise names that must
    // never appear in a plan generated for this gym (see domain/GymExclusions). NULLABLE like the
    // plate profile: null = no exclusions, and old backups (Gson leaves missing fields null) and
    // the additive migration resolve identically.
    @SerializedName("avoidExercisesJson") val avoidExercisesJson: String? = null
)
