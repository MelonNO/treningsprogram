package com.migul.treningsprogram

import com.migul.treningsprogram.data.db.dao.StrengthPoint
import com.migul.treningsprogram.domain.DeloadPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * E2 (decision M2): the stall/fatigue-TRIGGERED deload.
 *
 * Locks the PURE [DeloadPolicy] decision layer that turns B3's stall detection into a deload on/off
 * decision. Verifies: stalled histories → deload triggers; progressing histories → no deload; a
 * deload is exactly ONE week (auto-exits the following week); and that detection is REUSED from
 * StallDetector (not reimplemented) via [DeloadPolicy.stalledFrom].
 */
class E2DeloadPolicyTest {

    private val DAY = 86_400_000L
    private fun point(day: Int, weight: Float, reps: Int) =
        StrengthPoint(dateMs = day * DAY, maxWeight = weight, bestReps = reps)

    /** A flat (stalled) 3-session history for one lift. */
    private fun stalledHistory() = listOf(point(1, 100f, 5), point(2, 100f, 5), point(3, 100f, 5))
    /** A clearly progressing history (weight climbing) for one lift. */
    private fun progressingHistory() = listOf(point(1, 90f, 5), point(2, 95f, 5), point(3, 100f, 5))

    // ---- threshold ----

    @Test fun singleStall_doesNotTriggerDeload() {
        assertFalse(DeloadPolicy.shouldTriggerDeload(1))
    }

    @Test fun twoStalls_triggerDeload() {
        assertTrue(DeloadPolicy.shouldTriggerDeload(2))
        assertTrue(DeloadPolicy.shouldTriggerDeload(5))
    }

    @Test fun zeroStalls_noDeload() {
        assertFalse(DeloadPolicy.shouldTriggerDeload(0))
    }

    // ---- next-state machine ----

    @Test fun enterDeload_whenEnoughLiftsStalled_andNotAlreadyDeloading() {
        assertTrue(DeloadPolicy.nextDeloadState(currentlyDeloading = false, stalledCount = 3))
    }

    @Test fun progressing_doesNotEnterDeload() {
        assertFalse(DeloadPolicy.nextDeloadState(currentlyDeloading = false, stalledCount = 0))
    }

    @Test fun deloadIsExactlyOneWeek_autoExitsNextWeek() {
        // Even with stalls still present, a week that was already a deload exits the next week,
        // so a deload never sticks forever.
        assertFalse(DeloadPolicy.nextDeloadState(currentlyDeloading = true, stalledCount = 5))
    }

    // ---- reuse of StallDetector via stalledFrom ----

    @Test fun stalledFrom_reusesStallDetector_flagsOnlyStalledLifts() {
        val histories = mapOf(
            "Squat" to stalledHistory(),
            "Bench" to stalledHistory(),
            "Row" to progressingHistory()
        )
        val stalled = DeloadPolicy.stalledFrom(histories)
        assertEquals(setOf("Squat", "Bench"), stalled.toSet())
        // Two stalled lifts → deload should fire.
        assertTrue(DeloadPolicy.shouldTriggerDeload(stalled.size))
    }

    @Test fun stalledFrom_allProgressing_noDeload() {
        val histories = mapOf("Squat" to progressingHistory(), "Bench" to progressingHistory())
        val stalled = DeloadPolicy.stalledFrom(histories)
        assertTrue(stalled.isEmpty())
        assertFalse(DeloadPolicy.nextDeloadState(currentlyDeloading = false, stalledCount = stalled.size))
    }
}
