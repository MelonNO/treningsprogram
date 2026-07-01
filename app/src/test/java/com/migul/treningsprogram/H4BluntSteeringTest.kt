package com.migul.treningsprogram

import com.migul.treningsprogram.data.repository.dayDurationFeedback
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P2 (generation-quality overhaul 2026-07) — GOAL-GENERAL, DURATION-DERIVED per-day time-budget feedback.
 *
 * The old hypertrophy-only "120 s rest on EVERY set + a FULL ~19–20 working sets → ~46 min" hammer
 * ([hypertrophyRestDirective], and the `hypertrophy` branch of [dayDurationFeedback]) was DELETED: it
 * hard-coded a fixed set count / minute target / 120 s ceiling that bounded a session at ~45–50 min
 * regardless of the chosen duration and only worked for hypertrophy. This test guards the replacement:
 *  - [dayDurationFeedback] keeps the reject CONDITION byte-for-byte (est < target−10 || est > target+10),
 *  - its wording is now goal-general + duration-derived — no fixed "19–20 sets", "46–47 min", or "120 s",
 *  - under-time days still ADD (rest → reps → sets → accessory), over-time days still TRIM.
 */
class H4BluntSteeringTest {

    private val target = 50  // window 40–60

    // ── the wording is goal-general and carries NO fixed set/minute/rest constants ──────────────────

    @Test fun underTimeFeedback_isGoalGeneral_noFixedConstants_stillAddsNotTrims() {
        val msg = dayDurationFeedback(day = 3, estimateMinutes = 33, targetMinutes = target)
        requireNotNull(msg)
        // Rest is still named as the #1 lever, and the day is still told to ADD (never TRIM).
        assertTrue("must name REST as the #1 lever: $msg", msg.contains("REST", ignoreCase = true))
        assertTrue("must still instruct ADD: $msg", msg.contains("ADD"))
        assertTrue("must still say UNDER: $msg", msg.contains("UNDER"))
        assertFalse("under-time must not issue a TRIM instruction: $msg", msg.contains("TRIM"))
        assertTrue("must name the day and estimate: $msg", msg.contains("Day 3") && msg.contains("33"))
        // The DELETED hypertrophy hammer must be GONE — no fixed 120 s rest, no fixed 19–20 sets, no 46 min.
        assertFalse("must not hard-code 120 s rest: $msg", msg.contains("120"))
        assertFalse("must not hard-code a ~19–20 set count: $msg", msg.contains("19–20"))
        assertFalse("must not tell the model to stop at 15–17 sets: $msg", msg.contains("15–17"))
        assertFalse("must not hard-code a ~46 min target: $msg", msg.contains("46"))
        // It must instead size to the session DURATION (goal-general framing).
        assertTrue("must size to the session duration: $msg", msg.contains("DURATION", ignoreCase = true))
    }

    @Test fun underTimeFeedback_sameWordingRegardlessOfGoal() {
        // There is no longer a per-goal branch: the feedback is identical for any goal at the same numbers.
        val a = dayDurationFeedback(day = 2, estimateMinutes = 25, targetMinutes = 40)
        val b = dayDurationFeedback(day = 2, estimateMinutes = 25, targetMinutes = 40)
        requireNotNull(a)
        assertTrue("goal-general wording is deterministic", a == b)
        assertFalse("no hard-coded 120 s anywhere: $a", a.contains("120"))
    }

    // ── reject CONDITION + over-time branch must be byte-for-byte unchanged ─────────────────────────

    @Test fun rejectCondition_isUnchanged_boundaryInclusive() {
        assertNull("centre accepted", dayDurationFeedback(1, target, target))
        assertNull("floor (target−10) accepted", dayDurationFeedback(1, target - 10, target))
        assertNull("ceiling (target+10) accepted", dayDurationFeedback(1, target + 10, target))
        requireNotNull(dayDurationFeedback(1, target - 11, target)) { "one under the floor → reject" }
        requireNotNull(dayDurationFeedback(1, target + 11, target)) { "one over the ceiling → reject" }
    }

    @Test fun overTimeBranch_trimsNotAdds_noForced120() {
        val over = dayDurationFeedback(1, target + 11, target)
        requireNotNull(over)
        assertTrue("over-time must instruct TRIM: $over", over.contains("TRIM"))
        assertTrue("over-time must say OVER: $over", over.contains("OVER"))
        assertFalse("over-time must not instruct ADD: $over", over.contains("ADD"))
        assertFalse("over-time must not force 120 s: $over", over.contains("120"))
    }
}
