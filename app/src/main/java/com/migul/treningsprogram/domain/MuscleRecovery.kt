package com.migul.treningsprogram.domain

/**
 * Pure, Android-free recovery/freshness model for the finer-grain muscle taxonomy (U1).
 *
 * ── Why these bands (cited science) ───────────────────────────────────────────────
 * Muscle protein synthesis (MPS) — the recovery/remodelling signal after a resistance
 * bout — follows a well-characterised time course in trained individuals:
 *   • Elevated and PEAKING in the first ~24 h post-bout, then DECLINING by ~36 h, and
 *     returning toward BASELINE by ~48-72 h.
 *       MacDougall JD et al. (1995) "The time course of elevated muscle protein
 *         synthesis following heavy resistance exercise." Can J Appl Physiol 20(4):480-486.
 *       Damas F et al. (2015) "A review of resistance training-induced changes in
 *         skeletal muscle protein synthesis and their contribution to hypertrophy."
 *         Sports Med 45(6):801-807.
 *   • A conventional practical MINIMUM of ~48 h between training the SAME muscle group
 *     is widely recommended, and training a muscle >=2x/week (i.e. a cadence on the order
 *     of every ~2-3.5 days) is favourable for hypertrophy.
 *       American College of Sports Medicine (ACSM) position stand on resistance training.
 *       Schoenfeld BJ, Ogborn D, Krieger JW (2016) "Effects of resistance training
 *         frequency on measures of muscle hypertrophy: a systematic review and
 *         meta-analysis." Sports Med 46(11):1689-1697.
 *
 * ── Chosen band boundaries (applied to all muscle groups) ────
 * Mapped from the MPS time course + the ~48 h same-muscle minimum:
 *   • RECOVERING : effective elapsed < 48 h. MPS is still elevated / only just returning
 *                  to baseline; the muscle is mid-remodelling and not yet "fresh".
 *   • READY      : 48 h <= elapsed <= 7 days (168 h). Recovery is essentially complete
 *                  (MPS back near baseline by ~48-72 h) and the group sits within a
 *                  normal weekly training cadence -- the ideal window to train it again.
 *   • OVERDUE    : elapsed > 7 days. Substantially longer than a normal ~2x/week cadence
 *                  (Schoenfeld 2016); detraining/freshness signal that the group is being
 *                  neglected relative to a sensible frequency.
 *
 * ── Weighted effective-elapsed (U1) ─────────────────────────────────────────────────
 * A muscle may be taxed by multiple exercises at different weights (1.0 primary,
 * 0.6 major synergist, 0.3 minor synergist). The "effective elapsed" for that muscle
 * accounts for how much it was actually stressed:
 *
 *   effectiveElapsed = rawElapsed / weight
 *
 * So a muscle hit as a 0.6-synergist has effectiveElapsed = rawElapsed / 0.6 =
 * 1.67 * rawElapsed -- it recovers faster from a synergist stimulus than from a primary
 * stimulus.  In practice: a bench-press session fully stresses Front Delts (1.0) but
 * only partially stresses Triceps (0.6) -- after 24 h the Triceps effective-elapsed is
 * 24/0.6 = 40 h, already closer to the 48 h ready threshold than Chest (24/1.0 = 24 h).
 *
 * This is consistent with the "dose-response" principle: lower stimulus dose => shorter
 * recovery need. The scaling keeps the same 48 h / 7-day absolute thresholds but adjusts
 * the effective elapsed proportionally to the dose.
 *
 * The recovery WINDOW thresholds are kept here as a single source of truth so the UI
 * never carries bare magic numbers.
 */
object MuscleRecovery {

    private const val HOUR_MS = 60L * 60L * 1000L
    private const val DAY_MS = 24L * HOUR_MS

    /** < 48 h effective elapsed since last trained -> still recovering. */
    const val RECOVERING_UNTIL_MS: Long = 48L * HOUR_MS

    /** <= 7 days effective elapsed -> still within a normal cadence (ready window upper edge). */
    const val READY_UNTIL_MS: Long = 7L * DAY_MS

    enum class RecoveryState {
        /** Trained within the last ~48 h effective -- MPS elevated, mid-remodelling. */
        RECOVERING,
        /** ~48 h ... 7 days effective -- recovered and within a normal weekly cadence; ideal to train. */
        READY,
        /** > 7 days effective -- neglected relative to a sensible training frequency. */
        OVERDUE,
        /** Never trained (no completed working sets for this muscle). */
        UNTRAINED
    }

    /**
     * Maps time-since-last-trained to a [RecoveryState].
     *
     * @param lastTrainedMs epoch millis of the most recent completed working set for the
     *   muscle, or null if the muscle has never been trained.
     * @param nowMs current epoch millis (injectable for testing).
     * @param weight contribution weight of the exercise that trained the muscle (1.0 =
     *   primary, 0.6 = major synergist, 0.3 = minor synergist). Defaults to 1.0 so that
     *   existing callers without a weight argument get identical behaviour.
     *
     * A never-trained muscle returns [RecoveryState.UNTRAINED] -- a sensible, non-error
     * state. A future-dated lastTrainedMs (clock skew) is clamped to elapsed = 0 and so
     * reports RECOVERING rather than crashing or going negative.
     */
    fun stateFor(lastTrainedMs: Long?, nowMs: Long, weight: Float = 1.0f): RecoveryState {
        if (lastTrainedMs == null) return RecoveryState.UNTRAINED
        val rawElapsed = (nowMs - lastTrainedMs).coerceAtLeast(0L)
        val effectiveElapsed = if (weight in 0.999f..1.001f) rawElapsed
                               else if (weight > 0f) (rawElapsed / weight.toDouble()).toLong()
                               else rawElapsed
        return when {
            effectiveElapsed < RECOVERING_UNTIL_MS -> RecoveryState.RECOVERING
            effectiveElapsed <= READY_UNTIL_MS     -> RecoveryState.READY
            else                                   -> RecoveryState.OVERDUE
        }
    }

    /**
     * For a RECOVERING muscle, returns the fraction of recovery completed:
     *   0.0 = just trained (deep in recovery)
     *   1.0 = about to become ready (almost done recovering)
     *
     * Clamped to [0, 1]. Call this only when state == RECOVERING.
     */
    fun recoveryFraction(lastTrainedMs: Long, nowMs: Long, weight: Float = 1.0f): Float {
        val rawElapsed = (nowMs - lastTrainedMs).coerceAtLeast(0L)
        val effectiveElapsed = if (weight in 0.999f..1.001f) rawElapsed
                               else if (weight > 0f) (rawElapsed / weight.toDouble()).toLong()
                               else rawElapsed
        return (effectiveElapsed.toFloat() / RECOVERING_UNTIL_MS).coerceIn(0f, 1f)
    }

    /**
     * Remaining recovery time in millis for a RECOVERING muscle.
     * Returns 0 if already past the RECOVERING_UNTIL_MS threshold.
     */
    fun remainingRecoveryMs(lastTrainedMs: Long, nowMs: Long, weight: Float = 1.0f): Long {
        val rawElapsed = (nowMs - lastTrainedMs).coerceAtLeast(0L)
        val effectiveElapsed = if (weight in 0.999f..1.001f) rawElapsed
                               else if (weight > 0f) (rawElapsed / weight.toDouble()).toLong()
                               else rawElapsed
        return (RECOVERING_UNTIL_MS - effectiveElapsed).coerceAtLeast(0L)
    }

    // ── Weighted multi-exercise model ────────────────────────────────────────────────

    /**
     * One exercise-stimulus record: a logged exercise in one session, with the
     * session timestamp and the exercise's contribution weight to the relevant fine-grain
     * muscle (from MuscleClassifier.finerMusclesFor).
     */
    data class ExerciseStimulusRecord(
        val sessionId: Long,
        val sessionDateMs: Long,
        val exerciseName: String,
        val weight: Float   // 1.0 / 0.6 / 0.3
    )

    /**
     * Recovery result for one fine-grain muscle.
     *
     * @param state     the recovery state
     * @param lastTrainedMs    epoch ms of the most relevant session
     * @param lastSessionId    id of the session to navigate to on tap
     * @param weight           the weight of the stimulus that drove this result
     * @param recoveryFraction 0..1: how far through the 48 h recovery window (1.0 = almost ready)
     * @param remainingMs      remaining recovery millis (0 if not RECOVERING)
     */
    data class MuscleRecoveryResult(
        val state: RecoveryState,
        val lastTrainedMs: Long,
        val lastSessionId: Long,
        val weight: Float,
        val recoveryFraction: Float,
        val remainingMs: Long
    )

    /**
     * Given all stimulus records for one fine-grain muscle (from multiple sessions and
     * exercises), picks the most-relevant record using the following priority:
     *   1. Highest stimulus weight (primary beats synergist).
     *   2. Among records with equal weight, the most recent session wins.
     *
     * Returns null if the list is empty (muscle never trained).
     */
    fun computeRecovery(
        records: List<ExerciseStimulusRecord>,
        nowMs: Long
    ): MuscleRecoveryResult? {
        if (records.isEmpty()) return null

        val best = records.maxWithOrNull(
            compareBy<ExerciseStimulusRecord> { it.weight }
                .thenBy { it.sessionDateMs }
        ) ?: return null

        val state = stateFor(best.sessionDateMs, nowMs, best.weight)
        val fraction = recoveryFraction(best.sessionDateMs, nowMs, best.weight)
        val remaining = remainingRecoveryMs(best.sessionDateMs, nowMs, best.weight)

        return MuscleRecoveryResult(
            state = state,
            lastTrainedMs = best.sessionDateMs,
            lastSessionId = best.sessionId,
            weight = best.weight,
            recoveryFraction = fraction,
            remainingMs = remaining
        )
    }
}
