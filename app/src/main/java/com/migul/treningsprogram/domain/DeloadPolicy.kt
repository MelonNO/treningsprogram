package com.migul.treningsprogram.domain

import com.migul.treningsprogram.data.db.dao.StrengthPoint

/**
 * E2 deload trigger (decision M2): a deload is **stall/fatigue-triggered**, NOT a fixed final week.
 *
 * This is the pure (Android-free, Room-free) decision layer that turns B3's stall detection into a
 * deload on/off decision for the active program. It deliberately REUSES [StallDetector] — it does
 * not reimplement plateau detection — so "what counts as a stall" stays defined in exactly one place
 * (e1RM flat over [StallDetector.STALL_WINDOW] sessions, double-progression-aware, via [Epley]).
 *
 * ── When a deload fires ─────────────────────────────────────────────────────────────────────
 * When the number of currently-stalled lifts reaches [STALL_TRIGGER_COUNT], accumulated fatigue /
 * plateauing is judged broad enough to warrant a recovery week. One stalled lift is a localized
 * problem better fixed by a rep-scheme change or variation (B3 already surfaces that); a deload is a
 * program-wide reset, so we wait for multiple concurrent stalls before pulling that lever. This
 * keeps a single off lift from forcing a whole deload week.
 *
 * ── Exiting a deload ────────────────────────────────────────────────────────────────────────
 * A deload is a single recovery week. Once a deload week has been generated, the NEXT week clears
 * the flag (so we don't deload forever) — see [nextDeloadState]. If stalls persist after the
 * recovery week the trigger can fire again on a later evaluation.
 */
object DeloadPolicy {

    /**
     * How many lifts must be concurrently stalled (per [StallDetector]) before a program-wide
     * deload is triggered. A program-wide recovery week is a heavy intervention, so we require
     * MULTIPLE simultaneous plateaus rather than reacting to a single stalled lift.
     */
    const val STALL_TRIGGER_COUNT = 2

    /**
     * True when [stalledCount] currently-stalled lifts is enough to warrant a stall-triggered
     * deload. Pure threshold check so the trigger is unit-testable in isolation.
     */
    fun shouldTriggerDeload(stalledCount: Int): Boolean = stalledCount >= STALL_TRIGGER_COUNT

    /**
     * Decide the active program's deload flag for the week being generated.
     *
     * @param currentlyDeloading whether the program is ALREADY flagged as deloading (i.e. the week
     *   just finished was a deload week).
     * @param stalledCount number of currently-stalled lifts (from [StallDetector.stalledExercises]).
     * @return the deload flag the program should carry for the upcoming generated week.
     *
     * Rules:
     *  - If we were already deloading, the recovery week is done → exit the deload (return false),
     *    so a deload is exactly one week and never sticks.
     *  - Otherwise, enter a deload iff enough lifts are stalled ([shouldTriggerDeload]).
     */
    fun nextDeloadState(currentlyDeloading: Boolean, stalledCount: Int): Boolean =
        if (currentlyDeloading) false else shouldTriggerDeload(stalledCount)

    /**
     * Convenience: compute the stalled-lift names from raw per-exercise strength histories, reusing
     * [StallDetector]. Kept here so callers (e.g. the auto-generate trigger) have one call that maps
     * histories → stalled names → deload decision, without re-implementing detection.
     */
    fun stalledFrom(histories: Map<String, List<StrengthPoint>>): List<String> =
        StallDetector.stalledExercises(histories)
}
