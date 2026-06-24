package com.migul.treningsprogram.domain

/**
 * Pure, Android-free recovery/freshness model for a single muscle group, based on the
 * time elapsed since that group was last trained (working sets only).
 *
 * ── Why these bands (cited science) ───────────────────────────────────────────────
 * Muscle protein synthesis (MPS) — the recovery/remodelling signal after a resistance
 * bout — follows a well-characterised time course in trained individuals:
 *   • Elevated and PEAKING in the first ~24 h post-bout, then DECLINING by ~36 h, and
 *     returning toward BASELINE by ~48–72 h.
 *       MacDougall JD et al. (1995) "The time course of elevated muscle protein
 *         synthesis following heavy resistance exercise." Can J Appl Physiol 20(4):480–486.
 *       Damas F et al. (2015) "A review of resistance training-induced changes in
 *         skeletal muscle protein synthesis and their contribution to hypertrophy."
 *         Sports Med 45(6):801–807.
 *   • A conventional practical MINIMUM of ~48 h between training the SAME muscle group
 *     is widely recommended, and training a muscle ≥2×/week (i.e. a cadence on the order
 *     of every ~2–3.5 days) is favourable for hypertrophy.
 *       American College of Sports Medicine (ACSM) position stand on resistance training.
 *       Schoenfeld BJ, Ogborn D, Krieger JW (2016) "Effects of resistance training
 *         frequency on measures of muscle hypertrophy: a systematic review and
 *         meta-analysis." Sports Med 46(11):1689–1697.
 *
 * ── Chosen band boundaries (single documented set, applied to all major groups) ────
 * Mapped from the MPS time course + the ~48 h same-muscle minimum:
 *   • RECOVERING : last trained < 48 h ago. MPS is still elevated / only just returning
 *                  to baseline; the muscle is mid-remodelling and not yet "fresh". This
 *                  spans the ~24 h peak and ~36 h decline windows above.
 *   • READY      : 48 h ≤ elapsed ≤ 7 days (168 h). Recovery is essentially complete
 *                  (MPS back near baseline by ~48–72 h) and the group sits within a
 *                  normal weekly training cadence — the ideal window to train it again.
 *   • OVERDUE    : elapsed > 7 days. Substantially longer than a normal ~2×/week cadence
 *                  (Schoenfeld 2016); detraining/freshness signal that the group is being
 *                  neglected relative to a sensible frequency.
 *
 * Note: a single band set is applied to every major group. We do NOT differentiate
 * larger vs smaller muscles here — the brief allows a single documented set, and the
 * ~48 h minimum / ~7-day neglect threshold are defensible across groups.
 *
 * The recovery WINDOW thresholds are kept here as a single source of truth so the UI
 * never carries bare magic numbers.
 */
object MuscleRecovery {

    private const val HOUR_MS = 60L * 60L * 1000L
    private const val DAY_MS = 24L * HOUR_MS

    /** < 48 h since last trained → still recovering. */
    const val RECOVERING_UNTIL_MS: Long = 48L * HOUR_MS

    /** ≤ 7 days since last trained → still within a normal cadence (ready window upper edge). */
    const val READY_UNTIL_MS: Long = 7L * DAY_MS

    enum class RecoveryState {
        /** Trained within the last ~48 h — MPS elevated, mid-remodelling. */
        RECOVERING,
        /** ~48 h … 7 days — recovered and within a normal weekly cadence; ideal to train. */
        READY,
        /** > 7 days — neglected relative to a sensible training frequency. */
        OVERDUE,
        /** Never trained (no completed working sets for this group). */
        UNTRAINED
    }

    /**
     * Maps time-since-last-trained to a [RecoveryState].
     *
     * @param lastTrainedMs epoch millis of the most recent completed working set for the
     *   group, or null if the group has never been trained.
     * @param nowMs current epoch millis (injectable for testing).
     *
     * A never-trained group (or a fresh install with no history) returns [RecoveryState.UNTRAINED]
     * — a sensible, non-error state. A future-dated lastTrainedMs (clock skew) is clamped to
     * an elapsed of 0 and so reports RECOVERING rather than crashing or going negative.
     */
    fun stateFor(lastTrainedMs: Long?, nowMs: Long): RecoveryState {
        if (lastTrainedMs == null) return RecoveryState.UNTRAINED
        val elapsed = (nowMs - lastTrainedMs).coerceAtLeast(0L)
        return when {
            elapsed < RECOVERING_UNTIL_MS -> RecoveryState.RECOVERING
            elapsed <= READY_UNTIL_MS -> RecoveryState.READY
            else -> RecoveryState.OVERDUE
        }
    }
}
