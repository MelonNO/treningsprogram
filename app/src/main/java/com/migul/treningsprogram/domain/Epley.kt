package com.migul.treningsprogram.domain

/**
 * Single source of truth for estimated one-rep-max (e1RM).
 *
 * Uses the **Epley equation**: `1RM ≈ weight × (1 + reps / 30)`.
 * This is the formula locked for the feature batch (C1 assumption G) and the same one B3's
 * stall detection relies on, so the app never shows two different "1RM" numbers for the same lift.
 * It also matches the pre-existing inline computations in `RecapTrendsFragment` and
 * `HistoryProgressFragment` (`weight × (1 + reps / 30)`), which should be refactored onto this
 * helper to keep all e1RM math in one place.
 *
 * Epley is exact at 1 rep (returns the weight) and increases with reps; it is a standard,
 * widely-used estimate. Warm-up sets must be excluded by the CALLER, per the app's stats
 * convention — this pure function does not know about warm-ups.
 */
object Epley {

    /**
     * Estimated 1RM for a single set of [weightKg] for [reps] repetitions.
     *
     * Returns [weightKg] unchanged for a 1-rep set. Returns 0.0 for non-positive reps or weight
     * (no meaningful estimate), so callers can filter those out rather than get a misleading number.
     */
    fun estimate(weightKg: Float, reps: Int): Double {
        if (weightKg <= 0f || reps <= 0) return 0.0
        return weightKg * (1.0 + reps / 30.0)
    }
}
