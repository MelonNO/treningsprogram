package com.migul.treningsprogram.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

/**
 * Body-progress batch 2026-08-04 (brief 02) — one tape-measurement entry (girths only).
 *
 * ## Why this is a SEPARATE table from [BodyMeasurement]
 * `body_measurements` is the body-WEIGHT series, and its `weightKg` is non-null by contract. A lot
 * of the app depends on that: `CalorieEstimator.bodyWeightFor` picks the most recent weigh-in for
 * the per-session calorie term, `WeighInReminderReceiver` suppresses the weekly nudge when a
 * weigh-in exists "today", `WeightTrend.promptLine` feeds the AI prompt, `RelativeStrength` and
 * `MonthlyWrapped` divide by it. Widening that row so a waist-only entry could live in it (i.e.
 * making `weightKg` nullable) would have silently changed all five behaviours — a waist-only entry
 * would count as a weigh-in for the reminder and could be selected as "most recent weight" with no
 * weight in it.
 *
 * So girths get their own table and body weight keeps flowing exactly as it does today, including
 * the Home quick-add (user decision 6). Logging weight together with girths in the Body tab simply
 * writes one row to each table; the user sees a single entry.
 *
 * ## Why body fat is NOT stored here
 * The percentage is derived on read by [com.migul.treningsprogram.domain.BodyComposition.estimate]
 * from the profile's CURRENT height/sex. Assumption A5 lets a user log measurements before setting
 * height/sex; deriving means those entries light up retroactively once the profile is filled in,
 * where a stored column would have frozen them at null.
 *
 * All girths are centimetres (A1) and individually optional — an entry is valid with any subset.
 */
@Entity(tableName = "body_metrics")
data class BodyMetric(
    @PrimaryKey(autoGenerate = true) @SerializedName("id") val id: Long = 0,
    @SerializedName("dateMs") val dateMs: Long,
    @SerializedName("waistCm") val waistCm: Float? = null,
    @SerializedName("neckCm") val neckCm: Float? = null,
    /** Women only (user decision 2) — a male profile never sees or writes this. */
    @SerializedName("hipCm") val hipCm: Float? = null
) {
    /** True when the entry carries nothing at all — never worth persisting. */
    val isEmpty: Boolean get() = waistCm == null && neckCm == null && hipCm == null
}
