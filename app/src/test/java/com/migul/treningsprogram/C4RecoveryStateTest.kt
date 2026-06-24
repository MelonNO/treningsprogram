package com.migul.treningsprogram

import com.migul.treningsprogram.domain.MuscleRecovery
import com.migul.treningsprogram.domain.MuscleRecovery.RecoveryState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the pure time-since-last-trained -> recovery-state mapping for the Home
 * recovery/freshness view (unit C4). Updated for the weighted model (U1).
 *
 * Bands (documented in [MuscleRecovery]):
 *   RECOVERING : effective elapsed < 48 h
 *   READY      : 48 h <= effective elapsed <= 7 days
 *   OVERDUE    : effective elapsed > 7 days
 *   UNTRAINED  : never trained (null lastTrainedMs)
 *
 * Weighted effective elapsed: effectiveElapsed = rawElapsed / weight.
 * Default weight = 1.0 (backward-compatible); all legacy tests pass unchanged.
 */
class C4RecoveryStateTest {

    private val now = 1_000_000_000_000L  // arbitrary fixed "now"
    private val hour = 60L * 60L * 1000L
    private val day = 24L * hour

    private fun ago(ms: Long) = now - ms

    // ── never-trained / fresh install ────────────────────────────────────────────
    @Test fun nullLastTrained_isUntrained_notAnError() {
        assertEquals(RecoveryState.UNTRAINED, MuscleRecovery.stateFor(null, now))
    }

    // ── RECOVERING band (elapsed < 48 h) ─────────────────────────────────────────
    @Test fun justTrained_isRecovering() {
        assertEquals(RecoveryState.RECOVERING, MuscleRecovery.stateFor(ago(0L), now))
    }

    @Test fun oneHourAgo_isRecovering() {
        assertEquals(RecoveryState.RECOVERING, MuscleRecovery.stateFor(ago(1L * hour), now))
    }

    @Test fun justUnder48h_isRecovering() {
        assertEquals(RecoveryState.RECOVERING, MuscleRecovery.stateFor(ago(48L * hour - 1), now))
    }

    // ── RECOVERING -> READY boundary at exactly 48 h ──────────────────────────────
    @Test fun exactly48h_isReady() {
        // 48 h is NOT < 48 h, so it crosses into READY.
        assertEquals(RecoveryState.READY, MuscleRecovery.stateFor(ago(48L * hour), now))
        assertEquals(RecoveryState.READY, MuscleRecovery.stateFor(ago(MuscleRecovery.RECOVERING_UNTIL_MS), now))
    }

    // ── READY band (48 h ... 7 days) ───────────────────────────────────────────────
    @Test fun threeDaysAgo_isReady() {
        assertEquals(RecoveryState.READY, MuscleRecovery.stateFor(ago(3L * day), now))
    }

    @Test fun exactly7Days_isReady() {
        // 7 days is the inclusive upper edge of READY.
        assertEquals(RecoveryState.READY, MuscleRecovery.stateFor(ago(7L * day), now))
        assertEquals(RecoveryState.READY, MuscleRecovery.stateFor(ago(MuscleRecovery.READY_UNTIL_MS), now))
    }

    // ── READY -> OVERDUE boundary just past 7 days ────────────────────────────────
    @Test fun justOver7Days_isOverdue() {
        assertEquals(RecoveryState.OVERDUE, MuscleRecovery.stateFor(ago(7L * day + 1), now))
    }

    @Test fun fourteenDaysAgo_isOverdue() {
        assertEquals(RecoveryState.OVERDUE, MuscleRecovery.stateFor(ago(14L * day), now))
    }

    // ── clock-skew / future timestamp is clamped, never crashes/goes negative ────
    @Test fun futureTimestamp_clampedToRecovering() {
        // lastTrained in the future (device clock skew) -> elapsed clamped to 0 -> RECOVERING.
        assertEquals(RecoveryState.RECOVERING, MuscleRecovery.stateFor(now + 5L * hour, now))
    }

    // ── Weighted model (U1) ───────────────────────────────────────────────────────

    /**
     * Synergist weight (0.6) accelerates recovery: after 24 h raw, a 0.6-synergist
     * muscle has effectiveElapsed = 24/0.6 = 40 h, still RECOVERING (< 48 h).
     */
    @Test fun synergistWeight_recoversSlowerThanPrimary() {
        val trained24hAgo = ago(24L * hour)
        // Primary (1.0): 24 h elapsed -> still RECOVERING
        assertEquals(RecoveryState.RECOVERING, MuscleRecovery.stateFor(trained24hAgo, now, 1.0f))
        // Major synergist (0.6): effectiveElapsed = 24/0.6 = 40 h -> still RECOVERING (< 48 h)
        assertEquals(RecoveryState.RECOVERING, MuscleRecovery.stateFor(trained24hAgo, now, 0.6f))
    }

    /**
     * Minor synergist (0.3) recovers even faster: after 24 h raw,
     * effectiveElapsed = 24/0.3 = 80 h -> READY (>= 48 h, <= 7 days).
     */
    @Test fun minorSynergist_readyFasterThanPrimary() {
        val trained24hAgo = ago(24L * hour)
        // Minor synergist (0.3): effectiveElapsed = 24/0.3 = 80 h -> READY
        assertEquals(RecoveryState.READY, MuscleRecovery.stateFor(trained24hAgo, now, 0.3f))
    }

    /**
     * At exactly 48 h / weight, the muscle crosses to READY.
     * For weight=0.6, this is rawElapsed = 48 * 0.6 = 28.8 h.
     */
    @Test fun synergistCrossesReadyAtWeightAdjustedBoundary() {
        // 48 h effective at weight 0.6 => rawElapsed = 48 * 0.6 = 28.8 h = 103680000 ms
        val rawElapsedAtBoundary = (48L * hour * 0.6f).toLong()
        val lastTrained = ago(rawElapsedAtBoundary)
        assertEquals(RecoveryState.READY, MuscleRecovery.stateFor(lastTrained, now, 0.6f))
    }

    // ── recoveryFraction ─────────────────────────────────────────────────────────

    @Test fun recoveryFraction_justTrained_isZero() {
        val fraction = MuscleRecovery.recoveryFraction(now, now, 1.0f)
        assertEquals(0f, fraction, 0.001f)
    }

    @Test fun recoveryFraction_at24h_isHalf() {
        val fraction = MuscleRecovery.recoveryFraction(ago(24L * hour), now, 1.0f)
        assertEquals(0.5f, fraction, 0.001f)
    }

    @Test fun recoveryFraction_atBoundary_isOne() {
        val fraction = MuscleRecovery.recoveryFraction(ago(48L * hour), now, 1.0f)
        assertEquals(1.0f, fraction, 0.001f)
    }

    // ── remainingRecoveryMs ───────────────────────────────────────────────────────

    @Test fun remainingRecovery_justTrained_is48h() {
        val remaining = MuscleRecovery.remainingRecoveryMs(now, now, 1.0f)
        assertEquals(48L * hour, remaining)
    }

    @Test fun remainingRecovery_atBoundary_isZero() {
        val remaining = MuscleRecovery.remainingRecoveryMs(ago(48L * hour), now, 1.0f)
        assertEquals(0L, remaining)
    }

    // ── computeRecovery ───────────────────────────────────────────────────────────

    @Test fun computeRecovery_emptyRecords_returnsNull() {
        val result = MuscleRecovery.computeRecovery(emptyList(), now)
        assertEquals(null, result)
    }

    @Test fun computeRecovery_singleRecord_recovering() {
        val record = MuscleRecovery.ExerciseStimulusRecord(
            sessionId = 1L,
            sessionDateMs = ago(10L * hour),
            exerciseName = "Bench Press",
            weight = 1.0f
        )
        val result = MuscleRecovery.computeRecovery(listOf(record), now)!!
        assertEquals(RecoveryState.RECOVERING, result.state)
        assertEquals(1L, result.lastSessionId)
    }

    @Test fun computeRecovery_prefersHigherWeight() {
        // Two records: session 1 trained 30h ago as primary (1.0), session 2 trained 5h ago as minor (0.3)
        // Session 2 is more recent but session 1 has higher weight (1.0 > 0.3) so session 1 wins.
        val primary = MuscleRecovery.ExerciseStimulusRecord(
            sessionId = 1L, sessionDateMs = ago(30L * hour),
            exerciseName = "Squat", weight = 1.0f
        )
        val minor = MuscleRecovery.ExerciseStimulusRecord(
            sessionId = 2L, sessionDateMs = ago(5L * hour),
            exerciseName = "Deadlift", weight = 0.3f
        )
        val result = MuscleRecovery.computeRecovery(listOf(primary, minor), now)!!
        assertEquals(1L, result.lastSessionId)
        assertEquals(1.0f, result.weight, 0.001f)
    }

    @Test fun computeRecovery_tieBreakByMostRecent() {
        // Same weight, different times -> most recent wins
        val older = MuscleRecovery.ExerciseStimulusRecord(
            sessionId = 1L, sessionDateMs = ago(30L * hour),
            exerciseName = "Squat", weight = 0.6f
        )
        val newer = MuscleRecovery.ExerciseStimulusRecord(
            sessionId = 2L, sessionDateMs = ago(10L * hour),
            exerciseName = "Leg Press", weight = 0.6f
        )
        val result = MuscleRecovery.computeRecovery(listOf(older, newer), now)!!
        assertEquals(2L, result.lastSessionId)
    }
}
