package com.migul.treningsprogram.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A row in the workout history timeline.
 *
 * [kind] distinguishes the three record types:
 *  - null / [KIND_WORKOUT]  — a real workout (the original, pre-feature meaning). Existing rows have
 *    no value (NULL) and are treated as workouts; real logged workouts never set [kind] explicitly,
 *    so the column stays NULL for them. A workout is only ever surfaced once it has working sets.
 *  - [KIND_REST]            — an auto-logged rest day (an empty past day that was NOT a scheduled
 *    training day).
 *  - [KIND_MISSED]          — an auto-logged missed workout (an empty past day that WAS scheduled as
 *    a training day).
 *
 * REST/MISSED rows are placeholders: they are completed, carry no sets, and must never be counted as
 * training (volume / PRs / strength / streaks / AI history). They exist only so the History timeline
 * can show that a day passed with nothing logged. The column is nullable so the additive migration is
 * trivial and old backups (whose session JSON has no `kind`) deserialize as NULL = workout — the same
 * backup-safe pattern used for [PlannedExercise.programId].
 */
@Entity(tableName = "workout_sessions")
data class WorkoutSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateMs: Long,
    val durationMinutes: Int = 0,
    val notes: String = "",
    val isCompleted: Boolean = false,
    val kind: String? = null
) {
    /** True for an auto-logged rest day. */
    val isRestDay: Boolean get() = kind == KIND_REST

    /** True for an auto-logged missed (scheduled-but-empty) day. */
    val isMissedDay: Boolean get() = kind == KIND_MISSED

    /** True for any non-workout placeholder row (rest or missed) — never counts as training. */
    val isPlaceholder: Boolean get() = isRestDay || isMissedDay

    companion object {
        /** Sentinel value for a real workout. Workouts leave [kind] NULL; this is for clarity only. */
        const val KIND_WORKOUT = "WORKOUT"
        const val KIND_REST = "REST"
        const val KIND_MISSED = "MISSED"
    }
}
