package com.migul.treningsprogram.domain

import com.migul.treningsprogram.data.MuscleClassifier

/**
 * Item 4 (rest-UX batch 2026-07): the user's own per-category rest times, active only when
 * "manual rest mode" is enabled in Settings/the wizard. `null` wherever a [ManualRestTimes]
 * is expected means AI mode (the per-exercise [recommendedRestSeconds] suggestion is used).
 *
 * Two categories, per the user's confirmed decision:
 *  - HEAVY COMPOUNDS — squat / bench / deadlift / row / press "type lifts" (see [RestTimePolicy]).
 *  - ACCESSORIES — everything else, including cardio/warm-up entries if their timer fires (A2).
 */
data class ManualRestTimes(
    val heavyCompoundSeconds: Int,
    val accessorySeconds: Int
) {
    /** The rest this user actually takes after a set of [exerciseName]. */
    fun restSecondsFor(exerciseName: String): Int =
        if (RestTimePolicy.isHeavyCompound(exerciseName)) heavyCompoundSeconds else accessorySeconds

    companion object {
        const val DEFAULT_HEAVY_SECONDS = 180      // 3:00
        const val DEFAULT_ACCESSORY_SECONDS = 90   // 1:30
        const val MIN_SECONDS = 15
        const val MAX_SECONDS = 600

        /** "m:ss" for the settings fields and the rest-sheet label (e.g. 180 → "3:00"). */
        fun formatMinSec(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)

        /**
         * Forgiving parse of a user-typed rest time. Accepts "m:ss" (also "m.ss" / "m,ss" —
         * common numeric-keyboard slips) or a plain number meaning SECONDS. Returns null for
         * blank/garbage/out-of-range input so the caller can fall back to a default. Parsed
         * values are coerced into [MIN_SECONDS]..[MAX_SECONDS].
         */
        fun parseMinSec(text: String?): Int? {
            val t = text?.trim().orEmpty()
            if (t.isEmpty()) return null
            val m = Regex("^(\\d{1,2})[:.,](\\d{1,2})$").find(t)
            val seconds = if (m != null) {
                val mins = m.groupValues[1].toIntOrNull() ?: return null
                val secs = m.groupValues[2].toIntOrNull() ?: return null
                if (secs > 59) return null
                mins * 60 + secs
            } else {
                t.toIntOrNull() ?: return null
            }
            if (seconds <= 0) return null
            return seconds.coerceIn(MIN_SECONDS, MAX_SECONDS)
        }
    }
}

/**
 * Classifies which exercises count as "heavy compounds" for manual rest times. AUTOMATIC and
 * name-derived (assumption A3 — the user never hand-tags exercises), grounded in the same
 * name-pattern approach as [MuscleClassifier].
 *
 * Heavy = the big barbell/dumbbell compound families the user named (squat / bench / deadlift /
 * row / press) plus their close compound cousins (lunge, hip thrust, clean, snatch, good morning,
 * pull-up/chin-up, dip). Everything else — isolation, core, cardio, mobility — is an accessory.
 */
object RestTimePolicy {

    // Substring match on the lowercased name. Order-independent (any hit = heavy).
    private val HEAVY_PATTERNS = listOf(
        "squat", "deadlift", "rdl", "bench", "press", "row", "lunge",
        "hip thrust", "clean", "snatch", "good morning",
        "pull-up", "pullup", "pull up", "chin-up", "chinup", "chin up", "dip"
    )

    // Overrides: names that hit a heavy pattern but are NOT heavy compound work.
    //  - "pallof press"          → anti-rotation core isolation
    //  - "calf press/raise"      → calf isolation on a press machine
    //  - "pressdown/press down"  → triceps cable isolation
    //  - "wrist"                 → forearm isolation (wrist curl over a bench etc.)
    //  - "rowing"/"rower"/"erg"  → the cardio machine, not a strength row
    private val NOT_HEAVY_PATTERNS = listOf(
        "pallof", "calf", "pressdown", "press down", "press-down", "wrist",
        "rowing", "rower", "erg"
    )

    fun isHeavyCompound(exerciseName: String): Boolean {
        val lower = exerciseName.lowercase()
        if (lower.isBlank()) return false
        // A2: anything the app treats as cardio rests as an accessory (if its timer fires at all).
        if (MuscleClassifier.displayName(exerciseName) == "Cardio") return false
        if (NOT_HEAVY_PATTERNS.any { lower.contains(it) }) return false
        return HEAVY_PATTERNS.any { lower.contains(it) }
    }
}
