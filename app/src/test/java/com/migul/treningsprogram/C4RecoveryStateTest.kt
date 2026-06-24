package com.migul.treningsprogram

import com.migul.treningsprogram.domain.MuscleRecovery
import com.migul.treningsprogram.domain.MuscleRecovery.RecoveryState
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards the pure time-since-last-trained → recovery-state mapping for the Home
 * recovery/freshness view (unit C4). Bands (documented in [MuscleRecovery]):
 *   RECOVERING : elapsed < 48 h
 *   READY      : 48 h ≤ elapsed ≤ 7 days
 *   OVERDUE    : elapsed > 7 days
 *   UNTRAINED  : never trained (null lastTrainedMs)
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

    // ── RECOVERING → READY boundary at exactly 48 h ──────────────────────────────
    @Test fun exactly48h_isReady() {
        // 48 h is NOT < 48 h, so it crosses into READY.
        assertEquals(RecoveryState.READY, MuscleRecovery.stateFor(ago(48L * hour), now))
        assertEquals(RecoveryState.READY, MuscleRecovery.stateFor(ago(MuscleRecovery.RECOVERING_UNTIL_MS), now))
    }

    // ── READY band (48 h … 7 days) ───────────────────────────────────────────────
    @Test fun threeDaysAgo_isReady() {
        assertEquals(RecoveryState.READY, MuscleRecovery.stateFor(ago(3L * day), now))
    }

    @Test fun exactly7Days_isReady() {
        // 7 days is the inclusive upper edge of READY.
        assertEquals(RecoveryState.READY, MuscleRecovery.stateFor(ago(7L * day), now))
        assertEquals(RecoveryState.READY, MuscleRecovery.stateFor(ago(MuscleRecovery.READY_UNTIL_MS), now))
    }

    // ── READY → OVERDUE boundary just past 7 days ────────────────────────────────
    @Test fun justOver7Days_isOverdue() {
        assertEquals(RecoveryState.OVERDUE, MuscleRecovery.stateFor(ago(7L * day + 1), now))
    }

    @Test fun fourteenDaysAgo_isOverdue() {
        assertEquals(RecoveryState.OVERDUE, MuscleRecovery.stateFor(ago(14L * day), now))
    }

    // ── clock-skew / future timestamp is clamped, never crashes/goes negative ────
    @Test fun futureTimestamp_clampedToRecovering() {
        // lastTrained in the future (device clock skew) → elapsed clamped to 0 → RECOVERING.
        assertEquals(RecoveryState.RECOVERING, MuscleRecovery.stateFor(now + 5L * hour, now))
    }
}
