package com.migul.treningsprogram

import com.migul.treningsprogram.data.repository.dayDurationFeedback
import com.migul.treningsprogram.data.repository.hypertrophyRestDirective
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * H4 FIX-B (v1.10.4) — BLUNT, hypertrophy-scoped rest steering.
 *
 * Live testing showed the v1.10.3 soft "raise rest toward the band max" wording does NOT move the model:
 * 8/8 hypertrophy generations under-filled (days 30–44 min) because the model keeps standard short
 * isolation rest (60–90 s). The proven fix (2/2 live runs landed ALL days in-window) is to flatly force
 * recommendedRestSeconds=120 on EVERY working set incl. isolation at ~18–20 sets ⇒ days land ~42–48 min,
 * clearing the 40-min floor WITHIN the volume caps. validateProgram allows hypertrophy rest ≤120 s.
 *
 * The model's behaviour can't be unit-tested without a live call, but the two pure prompt seams CAN be:
 *  - [hypertrophyRestDirective] — the blunt directive injected into the generation prompt.
 *  - [dayDurationFeedback] — the per-day retry feedback (reject CONDITION + over-time branch unchanged;
 *    only the under-time WORDING is now blunt and hypertrophy-aware).
 */
class H4BluntSteeringTest {

    private val target = 50  // window 40–60

    // ── hypertrophyRestDirective ────────────────────────────────────────────────────────────────────

    @Test fun hypertrophyDirective_forcesBoth120Rest_andFullSetCount() {
        val d = hypertrophyRestDirective("Hypertrophy", target)
        assertTrue("directive must be present for hypertrophy", d.isNotBlank())
        // Lever 1: 120 s rest on EVERY working set incl. isolation.
        assertTrue("must force 120 s rest: $d", d.contains("120"))
        assertTrue("must scope to EVERY working set: $d", d.contains("EVERY working set"))
        assertTrue("must name isolation explicitly: $d", d.contains("isolation"))
        // Lever 2 (the live-proven addition): FORCE the full set count — aim higher, don't minimize.
        assertTrue("must push a FULL ~19–20 working sets: $d", d.contains("19–20"))
        assertTrue("must forbid under-filling at 15–17 sets: $d", d.contains("15–17"))
        assertTrue("must AIM for ~46–47 min, above the floor: $d", d.contains("46–47"))
        assertTrue("must name the actual floor: $d", d.contains("${target - 10}-min floor"))
        // The failed "minimize to clear the floor" framing must be GONE.
        assertFalse("must not tell the model to merely clear the floor: $d", d.contains("CLEAR the"))
        assertFalse("must not warn against chasing the target (that minimized volume): $d", d.contains("do NOT chase"))
    }

    @Test fun nonHypertrophyGoals_getNoBluntDirective() {
        assertEquals("", hypertrophyRestDirective("Strength", target))
        assertEquals("", hypertrophyRestDirective("Endurance", 60))
        assertEquals("", hypertrophyRestDirective("Weight Loss", 45))
        assertEquals("", hypertrophyRestDirective("General Fitness", target))
    }

    // ── dayDurationFeedback: under-time wording is now blunt (hypertrophy) ──────────────────────────

    @Test fun underTimeFeedback_hypertrophy_names120Rest_andFullSetCount_stillAddsNotTrim() {
        val msg = dayDurationFeedback(day = 3, estimateMinutes = 33, targetMinutes = target, hypertrophy = true)
        requireNotNull(msg)
        assertTrue("must name the 120 s rest directive: $msg", msg.contains("120"))
        assertTrue("must name REST as the lever: $msg", msg.contains("REST", ignoreCase = true))
        assertTrue("must name isolation explicitly: $msg", msg.contains("isolation", ignoreCase = true))
        // Live-proven addition: the under-time feedback must also push the FULL set count, not just rest.
        assertTrue("must push a FULL ~19–20 working sets: $msg", msg.contains("19–20"))
        assertTrue("must forbid stopping at 15–17 sets: $msg", msg.contains("15–17"))
        // The G1/H3 contract is preserved: still ADD, still UNDER, never TRIM, names day + estimate.
        assertTrue("must still instruct ADD: $msg", msg.contains("ADD"))
        assertTrue("must still say UNDER: $msg", msg.contains("UNDER"))
        assertFalse("under-time must not issue a TRIM instruction: $msg", msg.contains("TRIM"))
        assertTrue("must name the day and estimate: $msg", msg.contains("Day 3") && msg.contains("33"))
    }

    @Test fun underTimeFeedback_nonHypertrophy_doesNotHardcode120() {
        // Endurance/weight-loss keep their shorter rest bands; the blunt 120 directive must NOT appear,
        // but REST is still named as the #1 lever and the day is still told to ADD.
        val msg = dayDurationFeedback(day = 2, estimateMinutes = 25, targetMinutes = 40, hypertrophy = false)
        requireNotNull(msg)
        assertFalse("non-hypertrophy under-time must not force 120 s: $msg", msg.contains("120"))
        assertTrue("still names REST as the #1 lever: $msg", msg.contains("REST", ignoreCase = true))
        assertTrue("still instructs ADD: $msg", msg.contains("ADD"))
        assertFalse("still never issues TRIM: $msg", msg.contains("TRIM"))
    }

    // ── reject CONDITION + over-time branch must be byte-for-byte unchanged ─────────────────────────

    @Test fun rejectCondition_isUnchanged_boundaryInclusive() {
        // Default param (hypertrophy=true) — the existing 3-arg call sites' behaviour.
        assertNull("centre accepted", dayDurationFeedback(1, target, target))
        assertNull("floor (target−10) accepted", dayDurationFeedback(1, target - 10, target))
        assertNull("ceiling (target+10) accepted", dayDurationFeedback(1, target + 10, target))
        requireNotNull(dayDurationFeedback(1, target - 11, target)) { "one under the floor → reject" }
        requireNotNull(dayDurationFeedback(1, target + 11, target)) { "one over the ceiling → reject" }
    }

    @Test fun overTimeBranch_unchanged_trimsNotAdds_noForced120() {
        val over = dayDurationFeedback(1, target + 11, target)
        requireNotNull(over)
        assertTrue("over-time must instruct TRIM: $over", over.contains("TRIM"))
        assertTrue("over-time must say OVER: $over", over.contains("OVER"))
        assertFalse("over-time must not instruct ADD: $over", over.contains("ADD"))
        assertFalse("over-time must not force 120 s: $over", over.contains("120"))
    }
}
