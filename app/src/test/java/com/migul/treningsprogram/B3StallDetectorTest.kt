package com.migul.treningsprogram

import com.migul.treningsprogram.data.db.dao.StrengthPoint
import com.migul.treningsprogram.domain.StallDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Feature B3: plateau / stall detection.
 *
 * Locks the PURE [StallDetector] without needing Android/Room. A lift is stalled only when its
 * estimated 1RM (Epley) shows no improvement across the last [StallDetector.STALL_WINDOW]
 * consecutive sessions — the criterion is double-progression-aware (reps climbing at the same
 * weight raise e1RM and must NOT flag) and lifts with too little history are never flagged.
 */
class B3StallDetectorTest {

    private val DAY = 86_400_000L

    private fun point(day: Int, weight: Float, reps: Int) =
        StrengthPoint(dateMs = day * DAY, maxWeight = weight, bestReps = reps)

    // ---- Genuine stall: flat est-1RM across the window ----

    @Test fun flatWeightAndReps_acrossWindow_isStalled() {
        // Same weight, same reps for 3 sessions → e1RM never moves → stalled.
        val history = listOf(
            point(1, 100f, 5),
            point(2, 100f, 5),
            point(3, 100f, 5)
        )
        assertTrue(StallDetector.isStalled(history))
    }

    @Test fun stallAtTailEvenAfterEarlierProgress_isStalled() {
        // Improved early, then flat for the last window → only the recent window matters → stalled.
        val history = listOf(
            point(1, 80f, 5),
            point(2, 90f, 5),
            point(3, 100f, 5),
            point(4, 100f, 5),
            point(5, 100f, 5)
        )
        assertTrue(StallDetector.isStalled(history))
    }

    @Test fun regressing_isStalled() {
        // e1RM falling across the window is also "not improving" → stalled (needs intervention).
        val history = listOf(
            point(1, 100f, 5),
            point(2, 95f, 5),
            point(3, 90f, 5)
        )
        assertTrue(StallDetector.isStalled(history))
    }

    // ---- NOT stalled: double progression (reps climbing at same weight) ----

    @Test fun repsClimbingAtSameWeight_doubleProgression_isNotStalled() {
        // Classic double progression: weight on the bar unchanged, reps going up each session.
        // e1RM rises (Epley grows with reps), so this must NOT be flagged as a stall.
        val history = listOf(
            point(1, 100f, 6),
            point(2, 100f, 7),
            point(3, 100f, 8)
        )
        assertFalse(StallDetector.isStalled(history))
    }

    @Test fun weightIncrease_isNotStalled() {
        // Added load → e1RM up → progressing → not stalled.
        val history = listOf(
            point(1, 100f, 5),
            point(2, 100f, 5),
            point(3, 102.5f, 5)
        )
        assertFalse(StallDetector.isStalled(history))
    }

    @Test fun improvementOnLatestSession_breaksStall() {
        // Flat for two, then a PR on the most recent session → not stalled.
        val history = listOf(
            point(1, 100f, 5),
            point(2, 100f, 5),
            point(3, 105f, 5)
        )
        assertFalse(StallDetector.isStalled(history))
    }

    // ---- Insufficient history: never flagged ----

    @Test fun fewerThanWindowSessions_isNotStalled() {
        val twoFlat = listOf(point(1, 100f, 5), point(2, 100f, 5))
        assertFalse(StallDetector.isStalled(twoFlat))
        assertFalse(StallDetector.isStalled(listOf(point(1, 100f, 5))))
        assertFalse(StallDetector.isStalled(emptyList()))
    }

    @Test fun windowConstantIsThreeSessions() {
        // Documents the chosen window; if this changes, the science rationale must be revisited.
        assertEquals(3, StallDetector.STALL_WINDOW)
    }

    // ---- Edge: epsilon guards against trivial jitter ----

    @Test fun subEpsilonE1rmDrift_isStalled() {
        // A few extra grams of e1RM (well under the epsilon) is not real progress → still stalled.
        // 100kg×5 → e1RM 116.67; 100.1kg×5 → 116.78 (~0.12kg up, < 0.5kg epsilon).
        val history = listOf(
            point(1, 100f, 5),
            point(2, 100.1f, 5),
            point(3, 100.1f, 5)
        )
        assertTrue(StallDetector.isStalled(history))
    }

    @Test fun unsortedInput_isHandled() {
        // Detector sorts by date internally; flat window still detected regardless of input order.
        val history = listOf(
            point(3, 100f, 5),
            point(1, 100f, 5),
            point(2, 100f, 5)
        )
        assertTrue(StallDetector.isStalled(history))
    }

    // ---- stalledExercises / suggestion helpers ----

    @Test fun stalledExercises_filtersAndPreservesOrder() {
        val flat = listOf(point(1, 100f, 5), point(2, 100f, 5), point(3, 100f, 5))
        val progressing = listOf(point(1, 100f, 5), point(2, 100f, 6), point(3, 100f, 7))
        val map = linkedMapOf(
            "Bench Press" to flat,
            "Squat" to progressing,
            "Deadlift" to flat
        )
        assertEquals(listOf("Bench Press", "Deadlift"), StallDetector.stalledExercises(map))
    }

    @Test fun suggestion_namesExerciseAndProposesInterventions() {
        val msg = StallDetector.suggestionFor("Overhead Press")
        assertTrue(msg.contains("Overhead Press"))
        assertTrue(msg.contains("deload"))
        assertTrue(msg.contains("rep scheme") || msg.contains("rep-scheme"))
        assertTrue(msg.contains("variation"))
    }
}
