package com.migul.treningsprogram

import com.migul.treningsprogram.data.repository.GamificationRepository
import com.migul.treningsprogram.domain.BeatTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R7 — "beat last time". Locks the preview logic to the completion-time PR rule so what
 * flashes mid-workout is exactly what the completion dialog later credits (brief AC).
 */
class BeatTargetTest {

    // ── Agreement with completion detection (GamificationRepository.isWeightPr) ──────────────

    @Test
    fun `isPr agrees with completion's isWeightPr across the whole grid`() {
        val weights = listOf(0f, 20f, 59.9f, 60f, 60.1f, 62.5f, 100f)
        val previous = listOf(null, 0f, 20f, 60f, 100f)
        for (w in weights) for (p in previous) {
            assertEquals(
                "weight=$w previousMax=$p",
                GamificationRepository.isWeightPr(w, p),
                BeatTarget.isPr(w, p)
            )
        }
    }

    @Test
    fun `first ever performance is a baseline never a PR`() {
        assertFalse(BeatTarget.isPr(100f, null))
    }

    @Test
    fun `equalling the previous best is not a PR`() {
        assertFalse(BeatTarget.isPr(60f, 60f))
    }

    @Test
    fun `strictly beating the previous best is a PR`() {
        assertTrue(BeatTarget.isPr(60.5f, 60f))
    }

    // ── shouldFlash: the inline moment fires once per NEW best ───────────────────────────────

    @Test
    fun `warmup sets never flash even above the historical best`() {
        assertFalse(BeatTarget.shouldFlash(100f, isWarmup = true, previousMax = 60f, sessionBestFlashed = null))
    }

    @Test
    fun `no history never flashes`() {
        assertFalse(BeatTarget.shouldFlash(100f, isWarmup = false, previousMax = null, sessionBestFlashed = null))
    }

    @Test
    fun `below or equal to history never flashes`() {
        assertFalse(BeatTarget.shouldFlash(59f, isWarmup = false, previousMax = 60f, sessionBestFlashed = null))
        assertFalse(BeatTarget.shouldFlash(60f, isWarmup = false, previousMax = 60f, sessionBestFlashed = null))
    }

    @Test
    fun `flashes once per new best not once per set`() {
        // First set above history → flash.
        assertTrue(BeatTarget.shouldFlash(62.5f, isWarmup = false, previousMax = 60f, sessionBestFlashed = null))
        // Repeating the same weight → silent (already flashed at 62.5).
        assertFalse(BeatTarget.shouldFlash(62.5f, isWarmup = false, previousMax = 60f, sessionBestFlashed = 62.5f))
        // Above history but below the session's flashed best → silent.
        assertFalse(BeatTarget.shouldFlash(61f, isWarmup = false, previousMax = 60f, sessionBestFlashed = 62.5f))
        // Beating the NEW number → flashes again.
        assertTrue(BeatTarget.shouldFlash(65f, isWarmup = false, previousMax = 60f, sessionBestFlashed = 62.5f))
    }

    // ── chipTarget: the number the chip shows ─────────────────────────────────────────────────

    @Test
    fun `no history means no chip even after in-session sets`() {
        assertTrue(BeatTarget.chipTarget(previousMax = null, sessionBestFlashed = null) == null)
        // A first session sets baselines, never targets — logging heavy doesn't create a chip.
        assertTrue(BeatTarget.chipTarget(previousMax = null, sessionBestFlashed = 80f) == null)
    }

    @Test
    fun `chip shows the historical best before any in-session PR`() {
        assertTrue(BeatTarget.chipTarget(previousMax = 60f, sessionBestFlashed = null) == 60f)
    }

    @Test
    fun `chip raises to the in-session best once beaten`() {
        assertTrue(BeatTarget.chipTarget(previousMax = 60f, sessionBestFlashed = 62.5f) == 62.5f)
    }

    @Test
    fun `chip keeps the historical best while the session is still below it`() {
        assertTrue(BeatTarget.chipTarget(previousMax = 60f, sessionBestFlashed = 55f) == 60f)
    }
}
