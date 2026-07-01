package com.migul.treningsprogram

import com.migul.treningsprogram.data.repository.DURATION_AIM_BUFFER_MIN
import com.migul.treningsprogram.data.repository.dayDurationFeedback
import com.migul.treningsprogram.data.repository.durationAimMinutes
import com.migul.treningsprogram.data.repository.durationAimPhrase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * gen-calibration 2026-07 — the model systematically UNDER-sizes each training day vs the app's exact
 * [com.migul.treningsprogram.domain.WorkoutTimeEstimator] recount, so a day it sizes "right at the target"
 * recounts UNDER the ±10-min floor and the strict gate burns a retry. The fix keeps the gate byte-for-byte
 * and instead tells the model to size each day a fixed buffer ABOVE the target
 * ([DURATION_AIM_BUFFER_MIN] / [durationAimMinutes] / [durationAimPhrase]) so the recount lands in-window on
 * attempt 1. These pure seams are unit-tested here; the actual attempt-1 landing rate is a live-only
 * property (see the A/B ledger).
 */
class DurationCalibrationTest {

    // ── The calibrated buffer ────────────────────────────────────────────────────────────────────────

    @Test fun buffer_isTheRatifiedValue() {
        // The buffer is a deliberate, coordinated constant (like WORK_SECONDS_PER_REP). Changing it must be
        // an intentional edit, so pin the ratified value.
        assertEquals(12, DURATION_AIM_BUFFER_MIN)
    }

    /**
     * The calibration is ASYMMETRIC-safe: a day that recounts a little OVER the ceiling is pulled back by the
     * deterministic [com.migul.treningsprogram.data.repository.trimOverflowToWindow] salvage, while a day UNDER
     * the floor is the only FATAL miss — so the aim must be strictly ABOVE the target (err HIGH), never at or
     * below it. It must also stay MODEST: the live A/B (gen-calibration-2026-07) found only ~40–50 % buffer
     * pass-through and a uniformly large ~16–25 min self-estimate under-bias at 50 min, so a bigger buffer
     * gives diminishing returns AND starts tipping the low-bias long cells over the ceiling. This pins the
     * value in a sane err-high band so a future edit that zeroed/negated it (re-introducing the under-bias) or
     * blew it up (runaway overshoot) fails loudly.
     */
    @Test fun buffer_isPositiveAndModest_errHighNotLow() {
        assertTrue("must err HIGH (aim strictly above target), never at/under it", DURATION_AIM_BUFFER_MIN > 0)
        assertTrue("must stay modest (partial pass-through ⇒ big buffers over-shoot the long cells)",
            DURATION_AIM_BUFFER_MIN <= 20)
        // The aim is always a few minutes above the target across the whole 20–120 range.
        for (target in listOf(20, 50, 100, 120)) {
            assertTrue("target $target: aim ${durationAimMinutes(target)} must be above target",
                durationAimMinutes(target) > target)
        }
    }

    @Test fun durationAimMinutes_addsTheBufferToTheTarget() {
        assertEquals(62, durationAimMinutes(50))
        assertEquals(112, durationAimMinutes(100))
        assertEquals(132, durationAimMinutes(120))
        assertEquals(32, durationAimMinutes(20))
    }

    // ── The shared aim phrase ─────────────────────────────────────────────────────────────────────────

    @Test fun aimPhrase_statesTheBufferedAim_andTheAsymmetry() {
        val phrase = durationAimPhrase(50)
        assertTrue("names the buffered aim (62): $phrase", phrase.contains("62"))
        assertTrue("names the target (50): $phrase", phrase.contains("50"))
        assertTrue("references the floor (40): $phrase", phrase.contains("40"))
        assertTrue("references the ceiling (60): $phrase", phrase.contains("60"))
        assertTrue("says ABOVE: $phrase", phrase.contains("ABOVE"))
        assertTrue("says err HIGH: $phrase", phrase.contains("HIGH"))
        assertTrue("explains the recount runs lower: $phrase", phrase.contains("BELOW", ignoreCase = true))
        // It is embeddable in either the ADD or the TRIM branch, so it must carry NEITHER uppercase imperative.
        assertFalse("phrase must not contain the ADD imperative: $phrase", phrase.contains("ADD"))
        assertFalse("phrase must not contain the TRIM imperative: $phrase", phrase.contains("TRIM"))
    }

    // ── The retry feedback now steers toward the buffered aim ─────────────────────────────────────────

    @Test fun shortUnderFeedback_referencesTheBufferedAim() {
        // 50-min target, a day the model built at 33 min. The retry must steer it toward the buffered aim
        // (62), not merely the 50-min centre — while keeping the direction-aware ADD wording intact.
        val msg = dayDurationFeedback(day = 1, estimateMinutes = 33, targetMinutes = 50)!!
        assertTrue("short under-fill must reference the buffered aim 62: $msg", msg.contains("62"))
        assertTrue("still an ADD instruction", msg.contains("ADD"))
        assertFalse("under-fill must not issue a TRIM instruction", msg.contains("TRIM"))
    }

    @Test fun longUnderFeedback_referencesTheBufferedAim() {
        // 100-min target, a day the model built at 80 min. The multi-modal retry must steer toward the
        // buffered aim (112), closing the documented v1.17.0 residual (100-min landed ~88-89, under floor 90).
        val msg = dayDurationFeedback(day = 2, estimateMinutes = 80, targetMinutes = 100)!!
        assertTrue("long under-fill must reference the buffered aim 112: $msg", msg.contains("112"))
        assertTrue("still an ADD instruction", msg.contains("ADD"))
        assertTrue("still steers MULTI-MODAL", msg.contains("MULTI-MODAL"))
        assertFalse("under-fill must not issue a TRIM instruction", msg.contains("TRIM"))
    }
}
