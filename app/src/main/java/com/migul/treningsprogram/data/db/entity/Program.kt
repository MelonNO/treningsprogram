package com.migul.treningsprogram.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * E2: a named, saveable training program the user can switch between (e.g. a full-gym program vs.
 * a travel/home-gym program). Exactly one program is [isActive] at a time; the active program
 * drives Home's "today" view and the Program tab. Plans (rows in `planned_exercises`) are scoped
 * to a program via [PlannedExercise.programId]; the weekStart/dayOfWeek/orderInDay keying is kept
 * WITHIN each program, so the rest of the app's time-keying is unchanged.
 *
 * ── Mesocycle block (periodization) ─────────────────────────────────────────────────────────
 * A program may run as a multi-week mesocycle block. [mesocycleWeeks] > 0 marks the program as
 * periodized; [blockStartWeek] is the Monday-epoch ([WorkoutRepository.thisMonday]) the current
 * block started on, so the app can derive "week N of the block" = how many training weeks have
 * elapsed since [blockStartWeek]. Per decision L1, week-to-week progression INSIDE the block is the
 * app's existing weekly AI adaptation (not a fixed ramp); these fields only convey block position
 * to the generation prompt so the model knows it is producing week N of a mesocycle.
 *
 * ── Deload state (M2) ───────────────────────────────────────────────────────────────────────
 * [isDeloadActive] is a stall/fatigue-triggered deload flag (NOT a fixed final week). When B3's
 * StallDetector flags accumulated plateaus, the active program is put into a deload state; that
 * flag is signalled into the generation prompt (so the AI produces a deload week) and surfaced to
 * the user on Home and the Program tab. It is cleared once a non-deload week is generated.
 *
 * ── Frozen (assumption N) ───────────────────────────────────────────────────────────────────
 * [isFrozen] = the user has opted this program out of automatic weekly AI re-adaptation. When set,
 * the auto-generate trigger skips this program (its plan stays as-is until the user regenerates).
 */
@Entity(tableName = "programs")
data class Program(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAtMs: Long,
    /** Exactly one program has this true at a time (enforced by ProgramDao.setActive). */
    val isActive: Boolean = false,
    /** > 0 ⇒ this program is a periodized mesocycle block of this many weeks. 0 ⇒ plain program. */
    val mesocycleWeeks: Int = 0,
    /** Monday-epoch the current mesocycle block started on (0 ⇒ not started / not a block). */
    val blockStartWeek: Long = 0L,
    /** True while a stall/fatigue-triggered deload is in effect for this program (M2). */
    val isDeloadActive: Boolean = false,
    /** Assumption N: true ⇒ skip automatic weekly AI re-adaptation for this program. */
    val isFrozen: Boolean = false
) {
    companion object {
        /**
         * 1-based week index within a mesocycle block, pure so it is unit-testable without Room.
         * Returns 1 for a non-block program ([mesocycleWeeks] ≤ 0 or no [blockStartWeek]). Otherwise
         * counts elapsed calendar weeks from [blockStartWeek] to [currentMonday], clamped to
         * 1..[mesocycleWeeks] so it never reports before the block start or past its end.
         */
        fun weekInBlock(mesocycleWeeks: Int, blockStartWeek: Long, currentMonday: Long): Int {
            if (mesocycleWeeks <= 0 || blockStartWeek <= 0L) return 1
            val weekMs = 7L * 24 * 60 * 60 * 1000
            val elapsed = ((currentMonday - blockStartWeek) / weekMs).toInt()
            return (elapsed + 1).coerceIn(1, mesocycleWeeks)
        }
    }
}
