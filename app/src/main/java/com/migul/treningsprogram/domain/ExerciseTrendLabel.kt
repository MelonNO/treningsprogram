package com.migul.treningsprogram.domain

/**
 * Item 04 / improvement C (2026-08-06) — the single place that turns a lift's recent history into
 * the one-line trend label handed to the program generator ("EXERCISE TRENDS" block).
 *
 * ── Why this exists ────────────────────────────────────────────────────────────────────────
 * The app used to carry TWO rival plateau definitions that could disagree about the same lift:
 *
 *  * [StallDetector] — rep-aware, e1RM-based, drives the Stats → Progress "Plateau detected" card.
 *  * an inline, **weight-only** rule in the prompt builder, which called any lift whose first and
 *    last weight sat within ±2.5 kg "PLATEAUED". It could not see reps at all.
 *
 * So a user who backed off from 28 kg to 26 kg and was adding reps — progressing, and shown as
 * progressing on Progress — was still announced to the AI as PLATEAUED, and the next program was
 * planned around a plateau that was not happening.
 *
 * The fix is structural rather than a tweak to the threshold: **only [StallDetector] decides what
 * is a plateau.** [label] takes that verdict as an input and is not allowed to invent its own.
 * The weight delta survives only to choose how a *non*-plateaued lift is described. Pulling this
 * out of the prompt builder is what makes the invariant testable — see the test that asserts no
 * `stalled = false` input can ever produce the word PLATEAUED, for any weight delta.
 */
object ExerciseTrendLabel {

    /** The word the generator's prompt uses for a plateaued lift. Asserted on in tests. */
    const val PLATEAU_WORD = "PLATEAUED"

    /**
     * Trend label for one exercise.
     *
     * @param stalled the [StallDetector] verdict for this lift — the ONLY source of a plateau
     *   claim, so this label and the Progress card can never contradict each other.
     * @param weightDeltaKg last session's top weight minus the first session's, over the window
     *   rendered in the prompt.
     * @param sessions how many sessions the window covers.
     * @param lastWeightKg the most recent session's top weight.
     */
    fun label(stalled: Boolean, weightDeltaKg: Float, sessions: Int, lastWeightKg: Float): String =
        when {
            stalled ->
                "$PLATEAU_WORD ($sessions sessions at ~${lastWeightKg}kg — no added weight and no added reps)"
            weightDeltaKg > 2.5f ->
                "PROGRESSING (+${weightDeltaKg}kg over $sessions sessions)"
            // Weight down but NOT stalled: reps are climbing at the lighter load. That is a
            // deliberate back-off being rebuilt, not a decline — say so, so the generator keeps
            // building from where the user is now instead of yanking the weight back up.
            weightDeltaKg < -2.5f ->
                "REGRESSING (${weightDeltaKg}kg) but reps still improving — treat as a deliberate " +
                    "back-off and keep building from the current weight"
            else ->
                "PROGRESSING (reps improving at ~${lastWeightKg}kg over $sessions sessions)"
        }
}
