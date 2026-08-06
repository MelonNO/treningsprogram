package com.migul.treningsprogram.domain

import com.migul.treningsprogram.data.db.dao.StrengthPoint

/**
 * Pure (Android-free, Room-free) plateau / stall detection for a single exercise (feature B3).
 *
 * ── What counts as a stall (the science) ───────────────────────────────────────────────────
 * A lift is "stalled" ONLY when it shows **no progress at all across a defined window of
 * consecutive recent sessions**. Progress is measured primarily on e1RM ([Epley.estimate]) rather
 * than on "did the weight on the bar go up", which makes the criterion **double-progression-aware**:
 * under double progression an athlete adds reps at the same load for several sessions before adding
 * weight, and because Epley's e1RM rises with either weight OR reps, those reps-climbing sessions
 * show a rising e1RM and are correctly NOT flagged.
 *
 * e1RM alone is not sufficient, though, and [isStalled] adds two corrections (item 04, 2026-08-06):
 * extra reps at the same load always count as progress even when the e1RM move is below the noise
 * epsilon, and progress after a deliberate back-off is measured from the back-off rather than from
 * the heavier session that preceded it. Only a genuinely flat window — no more weight, no more
 * reps — registers as a stall. See [isStalled] for the full rule and the reasoning.
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
     * Tolerance, in kg of BAR weight, within which two sessions count as being "at the same
     * weight". Small on purpose: 26 kg and 26 kg are the same load, 26 kg and 26.5 kg are not.
     * Only absorbs float representation noise, never a real plate change (the smallest change any
     * supported gym can make is 0.5 kg per pair = 1 kg on the bar, or 0.5 kg on a dumbbell).
     */
    const val SAME_WEIGHT_EPSILON_KG = 0.25f

    /**
     * True when [history] shows no progress at all across its last [STALL_WINDOW] consecutive
     * sessions — i.e. the lift has genuinely plateaued.
     *
     * Returns false (not stalled) when there are fewer than [STALL_WINDOW] sessions: too little
     * history to judge, so new lifts are never flagged.
     *
     * ── What counts as progress (item 04, 2026-08-06) ─────────────────────────────────────────
     * A session in the window counts as progress if EITHER holds:
     *
     *  **(a) estimated-1RM improvement** — its e1RM exceeds the running baseline by more than
     *  [IMPROVEMENT_EPSILON_KG]. This is the original criterion and still carries most cases.
     *
     *  **(b) more reps at the same weight** — it beats the rep count of any earlier session in the
     *  window performed at the same load (within [SAME_WEIGHT_EPSILON_KG]). This is the user's
     *  ratified rule: *adding reps at the same weight is progress, and must never be reported as a
     *  plateau, even when an older heavier set still scores a higher e1RM.* It is NOT redundant
     *  with (a): on a light lift an extra rep moves e1RM by less than the epsilon (8 kg × +1 rep
     *  = +0.27 kg e1RM), so rule (a) alone would silently swallow real progress on isolation work.
     *
     * ── Measuring against where you are NOW, not your best-ever ───────────────────────────────
     * The baseline used by (a) is re-seeded when a session is a **deliberate back-off** — a session
     * whose load is meaningfully below the load that set the current baseline, with no improvement
     * of its own. Backing off to a weight you can control and rebuilding is the textbook correct
     * response to a session that was too heavy, and the previous implementation made that
     * structurally impossible to distinguish from a plateau: it seeded the baseline from the FIRST
     * session in the window, so a 28 kg session followed by two progressing 26 kg sessions could
     * never beat the seed and was flagged for the whole window. Progress after a back-off is now
     * measured from the back-off, which is the principle the user confirmed.
     *
     * The re-seed is applied only AFTER (a) and (b) have both failed for that session, so it can
     * never hide an improvement — it can only stop a deliberate step down from being counted as
     * evidence of a plateau.
     *
     * ── What still flags ──────────────────────────────────────────────────────────────────────
     * A window whose weight is flat and whose reps are flat or falling triggers neither rule and is
     * still reported. Detection is narrowed to the false positives, not switched off.
     */
    fun isStalled(history: List<StrengthPoint>): Boolean {
        if (history.size < STALL_WINDOW) return false
        // Most-recent STALL_WINDOW sessions, oldest→newest.
        val window = history.sortedBy { it.dateMs }.takeLast(STALL_WINDOW)
        val e1rms = window.map { Epley.estimate(it.maxWeight, it.bestReps) }

        // Baseline for rule (a), plus the load that set it (so a back-off can be recognised).
        var best = e1rms.first()
        var baselineWeight = window.first().maxWeight

        for (i in 1 until window.size) {
            // (a) e1RM improvement over the current baseline.
            if (e1rms[i] > best + IMPROVEMENT_EPSILON_KG) return false
            // (b) more reps than an earlier session in the window at the same weight.
            for (j in 0 until i) {
                if (kotlin.math.abs(window[i].maxWeight - window[j].maxWeight) <= SAME_WEIGHT_EPSILON_KG &&
                    window[i].bestReps > window[j].bestReps
                ) return false
            }
            // No progress this session. Re-seed on a deliberate back-off; otherwise track the best.
            if (window[i].maxWeight < baselineWeight - SAME_WEIGHT_EPSILON_KG) {
                best = e1rms[i]
                baselineWeight = window[i].maxWeight
            } else if (e1rms[i] > best) {
                best = e1rms[i]
                baselineWeight = window[i].maxWeight
            }
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
