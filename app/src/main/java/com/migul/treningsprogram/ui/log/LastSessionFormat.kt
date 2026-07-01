package com.migul.treningsprogram.ui.log

import com.migul.treningsprogram.data.db.entity.WorkoutSet

/**
 * Item 8 — presentation polish for the "Last session" line on the logging screen.
 *
 * Content is UNCHANGED (the single most recent completed session's working sets, warm-ups already
 * excluded by the query). This only re-formats the old cramped run-on
 * `S1: 8 reps @ 60kg  •  S2: 8 reps @ 60kg …` into a cleaner per-set `reps × weight` list, and
 * collapses the common uniform case (all sets identical) into a compact `N sets · reps × weight`.
 *
 * Pure so it is unit-testable off-device.
 */
object LastSessionFormat {

    /** The muted lead-in shown before the sets (kept separate so the fragment can style it). */
    const val LABEL = "Last time"

    /** Formats just the sets portion, e.g. "8 × 60 kg   ·   8 × 60 kg   ·   7 × 62 kg". */
    fun summary(sets: List<WorkoutSet>): String {
        if (sets.isEmpty()) return ""
        val perSet = sets.map { setLabel(it.reps, it.weightKg) }
        // Uniform case (every set identical) → collapse to "N sets · <label>" for a cleaner read.
        if (sets.size > 1 && perSet.distinct().size == 1) {
            return "${sets.size} sets  ·  ${perSet.first()}"
        }
        return perSet.joinToString("   ·   ")
    }

    /** Full one-liner including the lead-in label, e.g. "Last time  ·  3 sets  ·  8 × 60 kg". */
    fun line(sets: List<WorkoutSet>): String {
        val body = summary(sets)
        return if (body.isEmpty()) "" else "$LABEL  ·  $body"
    }

    private fun setLabel(reps: Int, weightKg: Float): String =
        if (weightKg > 0f) "$reps × ${formatWeight(weightKg)} kg" else "$reps × BW"

    private fun formatWeight(w: Float): String =
        if (w == w.toInt().toFloat()) w.toInt().toString() else w.toString()
}
