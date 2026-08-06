package com.migul.treningsprogram

import com.migul.treningsprogram.data.db.dao.StrengthPoint
import com.migul.treningsprogram.domain.Epley
import com.migul.treningsprogram.domain.ExerciseTrendLabel
import com.migul.treningsprogram.domain.StallDetector
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Item 04 (2026-08-06) — "adding reps at a lower weight must not be called a plateau".
 *
 * The user was told their dumbbell incline bench press had plateaued while they were doing exactly
 * the right thing: one session at 28 kg that was too heavy, a back-off to 26 kg, then 26 kg again
 * with more reps. Two independent causes were confirmed by diagnosis and both are covered here:
 *
 *  * **The comparison was anchored to the first session in the window.** [StallDetector.isStalled]
 *    seeded its baseline from the oldest session in the window — the 28 kg one — so the two
 *    genuinely-progressing 26 kg sessions could never beat it. A back-off followed by a rebuild was
 *    STRUCTURALLY guaranteed to read as a plateau.
 *  * **An extra rep can move e1RM by less than the noise epsilon.** On light isolation work
 *    (8 kg × +1 rep = +0.27 kg e1RM, epsilon 0.5) rule-by-e1RM alone swallowed real rep progress.
 *
 * [B3StallDetectorTest] still holds the original behaviour that must NOT regress — a genuinely flat
 * window is still reported. This file adds the corrections and the boundary cases.
 */
class Item04PlateauRepProgressTest {

    private val DAY = 86_400_000L

    private fun point(day: Int, weight: Float, reps: Int) =
        StrengthPoint(dateMs = day * DAY, maxWeight = weight, bestReps = reps)

    // ── The user's own reported case ─────────────────────────────────────────────────────────

    @Test fun backOffThenRepsClimbing_isNotStalled_theUsersCase() {
        // Too heavy at 28 kg → back off to 26 kg → 26 kg again with more reps. This is the
        // textbook correct response to a too-heavy session and must never be called a plateau.
        val history = listOf(
            point(1, 28f, 5),
            point(2, 26f, 6),
            point(3, 26f, 8)
        )
        assertFalse(StallDetector.isStalled(history))
    }

    @Test fun backOffThenRepsClimbing_stillNotStalled_whenOldHeavierSetScoresHigherE1rm() {
        // The confirmed rule (4f): reps at the same weight count as progress EVEN WHEN an older,
        // heavier set still scores a higher estimated 1RM. Pin that the premise really holds here,
        // so this test cannot silently degrade into the easy case.
        val heavier = Epley.estimate(28f, 5)
        val latest = Epley.estimate(26f, 7)
        assertTrue(
            "premise: the old 28 kg set must still out-score the newest 26 kg set",
            heavier > latest
        )
        val history = listOf(
            point(1, 28f, 5),
            point(2, 26f, 6),
            point(3, 26f, 7)
        )
        assertFalse(StallDetector.isStalled(history))
    }

    @Test fun backOffThenClimbingBackInWeight_isNotStalled() {
        // Rebuilding by adding weight again after the back-off, without yet reaching the old peak.
        val history = listOf(
            point(1, 28f, 5),
            point(2, 26f, 6),
            point(3, 27f, 6)
        )
        assertFalse(StallDetector.isStalled(history))
    }

    // ── Rep progress that e1RM alone could not see ───────────────────────────────────────────

    @Test fun lightIsolationLift_extraRepsBelowE1rmEpsilon_isNotStalled() {
        // 8 kg lateral raise: each extra rep moves e1RM by 8 × 1/30 = 0.27 kg, under the 0.5 kg
        // improvement epsilon. Pin that the e1RM move really is sub-epsilon, then assert the lift
        // is NOT flagged — the old e1RM-only rule reported this as a plateau.
        val a = Epley.estimate(8f, 10)
        val b = Epley.estimate(8f, 11)
        assertTrue(
            "premise: one extra rep at 8 kg must move e1RM by less than the epsilon",
            b - a < StallDetector.IMPROVEMENT_EPSILON_KG
        )
        val history = listOf(
            point(1, 8f, 10),
            point(2, 8f, 11),
            point(3, 8f, 12)
        )
        assertFalse(StallDetector.isStalled(history))
    }

    @Test fun repsClimbingAtUnchangedWeight_isProgress() {
        val history = listOf(
            point(1, 60f, 5),
            point(2, 60f, 6),
            point(3, 60f, 7)
        )
        assertFalse(StallDetector.isStalled(history))
    }

    // ── Detection must NOT be switched off ───────────────────────────────────────────────────

    @Test fun flatWeightAndFlatReps_isStillReported() {
        val history = listOf(
            point(1, 60f, 8),
            point(2, 60f, 8),
            point(3, 60f, 8)
        )
        assertTrue(StallDetector.isStalled(history))
    }

    @Test fun flatWeightAndFallingReps_isStillReported() {
        val history = listOf(
            point(1, 60f, 8),
            point(2, 60f, 7),
            point(3, 60f, 6)
        )
        assertTrue(StallDetector.isStalled(history))
    }

    @Test fun fallingWeightAndFallingReps_isStillReported() {
        // A genuine decline, not a back-off-and-rebuild: nothing improves anywhere in the window.
        val history = listOf(
            point(1, 60f, 8),
            point(2, 57f, 7),
            point(3, 55f, 6)
        )
        assertTrue(StallDetector.isStalled(history))
    }

    @Test fun backOffThenTrulyFlat_isStillReported() {
        // Documents the boundary of the fix: the back-off itself is not what flags this — the two
        // identical sessions after it are. Nothing improves at the new weight, so it is a plateau.
        val history = listOf(
            point(1, 28f, 6),
            point(2, 26f, 6),
            point(3, 26f, 6)
        )
        assertTrue(StallDetector.isStalled(history))
    }

    @Test fun onlyTheMostRecentWindowCounts() {
        // Older progress must not mask a current plateau.
        val history = listOf(
            point(1, 40f, 5),
            point(2, 50f, 6),
            point(3, 60f, 8),
            point(4, 60f, 8),
            point(5, 60f, 8)
        )
        assertTrue(StallDetector.isStalled(history))
    }

    @Test fun tooLittleHistory_isNeverFlagged() {
        assertFalse(StallDetector.isStalled(listOf(point(1, 60f, 8), point(2, 60f, 8))))
        assertFalse(StallDetector.isStalled(emptyList()))
    }

    // ── Improvement C: the AI label and the Progress card share ONE plateau definition ───────

    @Test fun trendLabel_saysPlateauedOnlyWhenStallDetectorDoesSo() {
        // The invariant that closes the two-rival-definitions bug: no non-stalled lift may ever be
        // announced to the generator as PLATEAUED, whatever its weight delta — including the
        // ±2.5 kg band that the old weight-only rule labelled PLATEAUED unconditionally.
        val deltas = listOf(-30f, -5f, -2.5f, -2f, -0.5f, 0f, 0.5f, 2f, 2.5f, 5f, 30f)
        for (d in deltas) {
            val progressing = ExerciseTrendLabel.label(
                stalled = false, weightDeltaKg = d, sessions = 4, lastWeightKg = 26f
            )
            assertFalse(
                "a non-stalled lift must never be labelled PLATEAUED (delta $d): $progressing",
                progressing.contains(ExerciseTrendLabel.PLATEAU_WORD)
            )
            val stalled = ExerciseTrendLabel.label(
                stalled = true, weightDeltaKg = d, sessions = 4, lastWeightKg = 26f
            )
            assertTrue(
                "a stalled lift must always be labelled PLATEAUED (delta $d): $stalled",
                stalled.contains(ExerciseTrendLabel.PLATEAU_WORD)
            )
        }
    }

    @Test fun trendLabel_backOffIsDescribedAsRebuilding_notAsPlainDecline() {
        // The user's shape lands in the weight-down branch; the generator must be told to keep
        // building from the current weight rather than to treat it as a decline to be undone.
        val label = ExerciseTrendLabel.label(
            stalled = false, weightDeltaKg = -4f, sessions = 3, lastWeightKg = 26f
        )
        assertFalse(label.contains(ExerciseTrendLabel.PLATEAU_WORD))
        assertTrue(label.contains("back-off"))
        assertTrue(label.contains("reps still improving"))
    }

    @Test fun trendLabel_flatWeightButProgressing_isNotCalledAPlateau() {
        // Double progression: weight unchanged (delta 0), reps climbing. The old rule called this
        // PLATEAUED purely because the weight had not moved.
        val label = ExerciseTrendLabel.label(
            stalled = false, weightDeltaKg = 0f, sessions = 4, lastWeightKg = 60f
        )
        assertFalse(label.contains(ExerciseTrendLabel.PLATEAU_WORD))
        assertTrue(label.contains("PROGRESSING"))
    }
}
