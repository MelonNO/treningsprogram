package com.migul.treningsprogram

import com.migul.treningsprogram.domain.BeatTarget
import com.migul.treningsprogram.domain.HomeTargets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * N1 — the Home "number to beat" derivation AGREES with the in-workout Beat chip's initial
 * target ([BeatTarget.chipTarget] with no session best) for every input, including the
 * baseline rule (no history ⇒ no target, never 0).
 */
class N1HomeTargetsTest {

    @Test fun `no history - no target (baselines, never targets)`() {
        assertNull(HomeTargets.targetFor(null))
    }

    @Test fun `history - target is the historical best working weight`() {
        assertEquals(60f, HomeTargets.targetFor(60f))
        assertEquals(2.5f, HomeTargets.targetFor(2.5f))
    }

    @Test fun `agreement with the Beat chip across representative inputs`() {
        for (prev in listOf(null, 0f, 2.5f, 60f, 142.5f)) {
            assertEquals(
                "previousMax=$prev",
                BeatTarget.chipTarget(prev, sessionBestFlashed = null),
                HomeTargets.targetFor(prev)
            )
        }
    }

    @Test fun `after a PR last session the new best IS the target on both surfaces`() {
        // A PR raises the stored best; both Home and the chip then read that same new best.
        val newBest = 62.5f
        assertEquals(newBest, HomeTargets.targetFor(newBest))
        assertEquals(newBest, BeatTarget.chipTarget(newBest, null))
    }
}
