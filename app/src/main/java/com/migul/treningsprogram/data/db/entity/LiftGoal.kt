package com.migul.treningsprogram.data.db.entity

import androidx.room.Entity
import com.google.gson.annotations.SerializedName
import androidx.room.PrimaryKey

/**
 * N5 — a user-declared target for a strength exercise ("100 kg bench by October").
 *
 * A goal targets either a top WORKING-SET weight ([isE1rm] = false) or an estimated 1RM
 * ([isE1rm] = true; Epley, the app's single e1RM formula). Reaching it is detected on workout
 * completion from logged working sets only (warm-ups never count) and celebrated exactly once:
 * detection only ever promotes ACTIVE → ACHIEVED.
 *
 * [targetDateMs] is optional flavor (0 = none) — per A-G5 a passed date is NOT a failure state;
 * the goal simply stays active. Achieved goals are kept as history ([STATUS_ACHIEVED]), never
 * silently deleted; abandoning is unceremonious ([STATUS_ABANDONED], hidden from lists).
 * Goals grant NO XP in v1 (A-G1), so StatsRecomputer parity is untouched.
 */
@Entity(tableName = "lift_goals")
data class LiftGoal(
    @PrimaryKey(autoGenerate = true) @SerializedName("id") val id: Long = 0,
    @SerializedName("exerciseName") val exerciseName: String,
    @SerializedName("targetWeightKg") val targetWeightKg: Float,
    @SerializedName("isE1rm") val isE1rm: Boolean = false,
    @SerializedName("targetDateMs") val targetDateMs: Long = 0L,
    @SerializedName("createdAtMs") val createdAtMs: Long = 0L,
    @SerializedName("status") val status: String = STATUS_ACTIVE,
    @SerializedName("achievedAtMs") val achievedAtMs: Long = 0L
) {
    companion object {
        const val STATUS_ACTIVE = "active"
        const val STATUS_ACHIEVED = "achieved"
        const val STATUS_ABANDONED = "abandoned"
    }
}
