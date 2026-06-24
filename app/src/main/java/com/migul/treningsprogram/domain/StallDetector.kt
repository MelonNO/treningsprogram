package com.migul.treningsprogram.domain

import com.migul.treningsprogram.data.db.dao.StrengthPoint

/**
 * Pure (Android-free, Room-free) plateau / stall detection for a single exercise (feature B3).
 *
 * ── What counts as a stall (the science) ───────────────────────────────────────────────────
 * A lift is "stalled" ONLY when its **estimated 1RM does not improve across a defined window of
 * consecutive recent sessions**. This is deliberately measured on e1RM ([Epley.estimate]) rather
 * than on "did the weight on the bar go up", which makes the criterion **double-progression-aware**:
 * under double progression an athlete adds reps at the same load for several sessions before adding
 * weight, and because Epley's e1RM rises with either weight OR reps, those reps-climbing sessions
 * show a rising e1RM and are correctly NOT flagged. Only a genuinely flat (or falling) e1RM —
 * neither more weight nor more reps — registers as a stall.
 *
 * Tying detection to e1RM keeps it consistent with the rest of the app: the same [Epley] helper
 * powers the strength-progress e1RM readout and the Recap & Trends PR timeline, so the app never
 * reasons about "progress" two different ways.
 *
 * ── The window ─────────────────────────────────────────────────────────────────────────────
 * The literature gives no universal session count; the operating principle is **progressive
 * overload** — training must apply a progressively greater stimulus over time, and a failure to
 * progress over repeated exposures (not one off day) is what warrants a programming change such as
 * a deload, a rep-scheme change, or an exercise variation (Kraemer & Ratamess, 2004, "Fundamentals
 * of resistance training: progression and exercise prescription", ACSM Position Stand,
 * Med Sci Sports Exerc 36(4):674–688). Reacting to a single non-improving session would
 * false-positive constantly, so we require [STALL_WINDOW] consecutive non-improving exposures —
 * three is the conventional minimum before intervening. Exercises with fewer than [STALL_WINDOW]
 * recorded sessions have too little history to judge and are never flagged (no false alarms on new
 * lifts).
 *
 * Warm-up sets are already excluded upstream by `WorkoutSetDao.getStrengthHistory`, so each
 * [StrengthPoint] here is a working-set summary (one per session); this object does not re-filter.
 *
 * Kept as a plain object on `List<StrengthPoint>` so it is JVM-unit-testable without Android.
 */
object StallDetector {

    /**
     * Number of consecutive recent sessions of an exercise that must show NO estimated-1RM
     * improvement before the lift is considered stalled.
     *
     * Basis: the progressive-overload principle (Kraemer & Ratamess 2004, ACSM progression-models
     * position stand) — intervene on a *repeated* failure to progress, not a single off session.
     * Three exposures is the conventional minimum: enough to rule out a bad day, few enough to act
     * before the plateau entrenches. Below this many sessions there is insufficient history to judge.
     */
    const val STALL_WINDOW = 3

    /**
     * Tolerance, in kg of estimated 1RM, within which two sessions count as "the same" e1RM.
     *
     * Avoids treating trivial e1RM jitter (e.g. a fractional-plate change, or a half-rep at a heavy
     * load nudging Epley by a hair) as genuine progress. A session counts as an improvement only if
     * its e1RM exceeds the running best by MORE than this margin.
     */
    const val IMPROVEMENT_EPSILON_KG = 0.5

    /**
     * True when [history] shows no meaningful estimated-1RM improvement across its last
     * [STALL_WINDOW] consecutive sessions — i.e. the lift has plateaued.
     *
     * Returns false (not stalled) when there are fewer than [STALL_WINDOW] sessions: too little
     * history to judge, so new lifts are never flagged.
     *
     * Within the window, a session "improves" only if its e1RM exceeds the best e1RM seen earlier
     * in the window by more than [IMPROVEMENT_EPSILON_KG]. If ANY session in the window improves,
     * the lift is progressing (this is the double-progression case: reps climbing at the same load
     * raises e1RM) and is NOT stalled. If NONE improves — e1RM flat or falling for the whole
     * window — it is stalled.
     */
    fun isStalled(history: List<StrengthPoint>): Boolean {
        if (history.size < STALL_WINDOW) return false
        // Most-recent STALL_WINDOW sessions, oldest→newest.
        val window = history.sortedBy { it.dateMs }.takeLast(STALL_WINDOW)
        val e1rms = window.map { Epley.estimate(it.maxWeight, it.bestReps) }
        // Walk the window; if any later session beats the running best by > epsilon, it progressed.
        var best = e1rms.first()
        for (i in 1 until e1rms.size) {
            if (e1rms[i] > best + IMPROVEMENT_EPSILON_KG) return false
            if (e1rms[i] > best) best = e1rms[i]
        }
        return true
    }

    /**
     * Convenience: the subset of [exerciseHistories] (exercise name → its strength history) that is
     * currently stalled per [isStalled], preserving the input iteration order. Used to build the
     * user-facing stall alert and the program-generation prompt's "STALLED LIFTS" block.
     */
    fun stalledExercises(exerciseHistories: Map<String, List<StrengthPoint>>): List<String> =
        exerciseHistories.entries
            .filter { isStalled(it.value) }
            .map { it.key }

    /**
     * A rule-based, API-free coaching suggestion for a stalled [exerciseName]. Names the exercise
     * and proposes the three standard plateau-breaking interventions (deload, rep-scheme change,
     * variation) so the user has a concrete next step without waiting on the AI.
     */
    fun suggestionFor(exerciseName: String): String =
        "$exerciseName has stalled — estimated 1RM hasn't improved over your last $STALL_WINDOW " +
            "sessions. Try a deload (drop ~10% and rebuild), change the rep scheme, or swap in a " +
            "variation to break the plateau."
}
