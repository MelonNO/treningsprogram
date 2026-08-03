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
 * Duration-aim calibration seams ([DURATION_AIM_BUFFER_MIN] / [durationAimMinutes] / [durationAimPhrase]).
 *
 * HISTORY: gen-calibration 2026-07 set the buffer to +12 to compensate the OLD estimator recounting
 * each day SEVERAL MINUTES LOWER than the model's own sizing. The 2026-08-03 "duration truth"
 * correction INVERTED that premise (the estimator was ~1.6-1.7x optimistic and is now calibrated to
 * real durations; prompt formula == gate formula), so the buffer dropped 12 -> 6 and is now a pure
 * asymmetry hedge: a day recounting a little OVER the ceiling is auto-trimmed (safe), a day UNDER
 * the floor is the only fatal miss. 6 is REASONED, not live-measured — re-derivation needs a live
 * pass. These pure seams are unit-tested here; attempt-1 landing rate is a live-only property.
 */
class DurationCalibrationTest {

    // ── The calibrated buffer ────────────────────────────────────────────────────────────────────────

    @Test fun buffer_isTheRatifiedValue() {
        // The buffer is a deliberate, coordinated constant (like WORK_SECONDS_PER_REP). Changing it must be
        // an intentional edit, so pin the ratified value.
        assertEquals(6, DURATION_AIM_BUFFER_MIN)
    }

    /**
     * The calibration is ASYMMETRIC-safe: a day that recounts a little OVER the ceiling is pulled back by the
     * deterministic [com.migul.treningsprogram.data.repository.trimOverflowToWindow] salvage, while a day UNDER
     * the floor is the only FATAL miss — so the aim must be strictly ABOVE the target (err HIGH), never at or
     * below it. It must also stay MODEST: with the corrected (2026-08-03) estimator the prompt formula and
     * the gate use identical arithmetic, so any large buffer just pushes days over the ceiling (the six
     * re-scored live-confirmation plans land in-window or slightly over WITHOUT extra sizing). This pins the
     * value in a sane small err-high band so a future edit that zeroed/negated it or blew it up fails loudly.
     */
    @Test fun buffer_isPositiveAndModest_errHighNotLow() {
        assertTrue("must err HIGH (aim strictly above target), never at/under it", DURATION_AIM_BUFFER_MIN > 0)
        assertTrue("must stay modest (corrected estimator ⇒ big buffers over-shoot the ceiling)",
            DURATION_AIM_BUFFER_MIN <= 10)
        // The aim is always a few minutes above the target across the whole 20–120 range.
        for (target in listOf(20, 50, 100, 120)) {
            assertTrue("target $target: aim ${durationAimMinutes(target)} must be above target",
                durationAimMinutes(target) > target)
        }
    }

    @Test fun durationAimMinutes_addsTheBufferToTheTarget() {
        assertEquals(56, durationAimMinutes(50))
        assertEquals(106, durationAimMinutes(100))
        assertEquals(126, durationAimMinutes(120))
        assertEquals(26, durationAimMinutes(20))
    }

    // ── The shared aim phrase ─────────────────────────────────────────────────────────────────────────

    @Test fun aimPhrase_statesTheBufferedAim_andTheAsymmetry() {
        val phrase = durationAimPhrase(50)
        assertTrue("names the buffered aim (56): $phrase", phrase.contains("56"))
        assertTrue("names the target (50): $phrase", phrase.contains("50"))
        assertTrue("references the floor (40): $phrase", phrase.contains("40"))
        assertTrue("references the ceiling (60): $phrase", phrase.contains("60"))
        assertTrue("says ABOVE: $phrase", phrase.contains("ABOVE"))
        assertTrue("says err HIGH: $phrase", phrase.contains("HIGH"))
        // 2026-08-03: the phrase must NOT claim the recount lands below the model's sizing — that
        // was the OLD estimator's optimism, corrected away. Prompt formula and gate now agree.
        assertFalse("must not claim the recount runs lower: $phrase", phrase.contains("BELOW"))
        // It is embeddable in either the ADD or the TRIM branch, so it must carry NEITHER uppercase imperative.
        assertFalse("phrase must not contain the ADD imperative: $phrase", phrase.contains("ADD"))
        assertFalse("phrase must not contain the TRIM imperative: $phrase", phrase.contains("TRIM"))
    }

    // ── The retry feedback now steers toward the buffered aim ─────────────────────────────────────────

    @Test fun shortUnderFeedback_referencesTheBufferedAim() {
        // 50-min target, a day the model built at 33 min. The retry must steer it toward the buffered aim
        // (62), not merely the 50-min centre — while keeping the direction-aware ADD wording intact.
        val msg = dayDurationFeedback(day = 1, estimateMinutes = 33, targetMinutes = 50)!!
        assertTrue("short under-fill must reference the buffered aim 56: $msg", msg.contains("56"))
        assertTrue("still an ADD instruction", msg.contains("ADD"))
        assertFalse("under-fill must not issue a TRIM instruction", msg.contains("TRIM"))
    }

    @Test fun longUnderFeedback_referencesTheBufferedAim() {
        // 100-min target, a day the model built at 80 min. The retry must steer toward the buffered
        // aim (112), closing the documented v1.17.0 residual (100-min landed ~88-89, under floor 90).
        // Cardio removal (2026-08-03): the steer is now resistance-only (was multi-modal cardio).
        val msg = dayDurationFeedback(day = 2, estimateMinutes = 80, targetMinutes = 100)!!
        assertTrue("long under-fill must reference the buffered aim 106: $msg", msg.contains("106"))
        assertTrue("steers resistance-only levers", msg.contains("RESISTANCE-ONLY"))
        assertTrue("rest lever named first", msg.contains("REST"))
        assertFalse("under-fill must not issue a TRIM instruction", msg.contains("TRIM"))
    }
}
