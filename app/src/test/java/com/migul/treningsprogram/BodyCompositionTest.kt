package com.migul.treningsprogram

import com.migul.treningsprogram.domain.BodyComposition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.log10

/**
 * Body-progress batch 2026-08-04 (brief 02) — the body-fat estimators.
 *
 * The expected values are computed here from the published formulas independently of the
 * implementation, so a typo in a coefficient fails rather than being blessed by a golden number
 * copied out of the code.
 */
class BodyCompositionTest {

    private val eps = 0.0001f

    // ── Navy ──────────────────────────────────────────────────────────────────────────────────

    @Test fun `navy male matches the published formula`() {
        val h = 180f; val waist = 90f; val neck = 38f
        val expected = (495.0 / (1.0324 - 0.19077 * log10((waist - neck).toDouble()) +
            0.15456 * log10(h.toDouble())) - 450.0).toFloat()
        assertEquals(expected, BodyComposition.navy("Male", h, waist, neck, null)!!, eps)
    }

    @Test fun `navy female matches the published formula and uses hip`() {
        val h = 165f; val waist = 75f; val neck = 32f; val hip = 98f
        val expected = (495.0 / (1.29579 - 0.35004 * log10((waist + hip - neck).toDouble()) +
            0.22100 * log10(h.toDouble())) - 450.0).toFloat()
        assertEquals(expected, BodyComposition.navy("Female", h, waist, neck, hip)!!, eps)
    }

    @Test fun `navy female without a hip cannot compute (A4)`() {
        assertNull(BodyComposition.navy("Female", 165f, 75f, 32f, null))
    }

    @Test fun `navy male ignores a hip value entirely`() {
        assertEquals(
            BodyComposition.navy("Male", 180f, 90f, 38f, null),
            BodyComposition.navy("Male", 180f, 90f, 38f, 105f)
        )
    }

    @Test fun `navy returns null for non-physical or missing inputs`() {
        // neck >= waist -> log10 of a non-positive number
        assertNull(BodyComposition.navy("Male", 180f, 38f, 38f, null))
        assertNull(BodyComposition.navy("Male", 180f, 30f, 40f, null))
        // unset profile
        assertNull(BodyComposition.navy("", 180f, 90f, 38f, null))
        assertNull(BodyComposition.navy(null, 180f, 90f, 38f, null))
        // missing pieces
        assertNull(BodyComposition.navy("Male", null, 90f, 38f, null))
        assertNull(BodyComposition.navy("Male", 180f, null, 38f, null))
        assertNull(BodyComposition.navy("Male", 180f, 90f, null, null))
        // non-positive height
        assertNull(BodyComposition.navy("Male", 0f, 90f, 38f, null))
    }

    // ── RFM ───────────────────────────────────────────────────────────────────────────────────

    @Test fun `rfm uses the sex-specific intercept`() {
        assertEquals(64f - 20f * (180f / 90f), BodyComposition.rfm("Male", 180f, 90f)!!, eps)
        assertEquals(76f - 20f * (165f / 75f), BodyComposition.rfm("Female", 165f, 75f)!!, eps)
    }

    @Test fun `rfm needs no neck or hip but does need sex and height`() {
        assertEquals(24f, BodyComposition.rfm("Male", 180f, 90f)!!, eps)
        assertNull(BodyComposition.rfm("", 180f, 90f))
        assertNull(BodyComposition.rfm("Male", null, 90f))
        assertNull(BodyComposition.rfm("Male", 180f, 0f))
    }

    // ── The reported estimate ─────────────────────────────────────────────────────────────────

    @Test fun `estimate is the equal-weight average of navy and rfm (decision 3)`() {
        val navy = BodyComposition.navy("Male", 180f, 90f, 38f, null)!!
        val rfm = BodyComposition.rfm("Male", 180f, 90f)!!
        assertEquals((navy + rfm) / 2f, BodyComposition.estimate("Male", 180f, 90f, 38f)!!, eps)
    }

    @Test fun `estimate female averages both, hip included`() {
        val navy = BodyComposition.navy("Female", 165f, 75f, 32f, 98f)!!
        val rfm = BodyComposition.rfm("Female", 165f, 75f)!!
        assertEquals(
            (navy + rfm) / 2f,
            BodyComposition.estimate("Female", 165f, 75f, 32f, 98f)!!,
            eps
        )
    }

    @Test fun `estimate needs waist AND neck (decision 4)`() {
        // waist only, no neck -> RFM alone would compute, but the reported figure is the average.
        assertNull(BodyComposition.estimate("Male", 180f, 90f, null))
        assertNull(BodyComposition.estimate("Male", 180f, null, 38f))
    }

    @Test fun `estimate needs the profile (A5) - no fabricated number`() {
        assertNull(BodyComposition.estimate("", 180f, 90f, 38f))
        assertNull(BodyComposition.estimate("Male", null, 90f, 38f))
        assertNull(BodyComposition.estimate("Male", 0f, 90f, 38f))
    }

    @Test fun `estimate for a woman without a hip is null (A4)`() {
        assertNull(BodyComposition.estimate("Female", 165f, 75f, 32f, null))
    }

    @Test fun `a realistic male entry lands in a believable range`() {
        val bf = BodyComposition.estimate("Male", 180f, 85f, 38f)!!
        assertTrue("expected a plausible percentage, got $bf", bf in 8f..30f)
    }

    // ── Profile helpers ───────────────────────────────────────────────────────────────────────

    @Test fun `hip is a women-only field (decision 2)`() {
        assertTrue(BodyComposition.needsHip(BodyComposition.SEX_FEMALE))
        assertFalse(BodyComposition.needsHip(BodyComposition.SEX_MALE))
        assertFalse(BodyComposition.needsHip(""))
        assertFalse(BodyComposition.needsHip(null))
    }

    @Test fun `known sex recognises exactly the two formula inputs`() {
        assertTrue(BodyComposition.isKnownSex("Male"))
        assertTrue(BodyComposition.isKnownSex("Female"))
        assertFalse(BodyComposition.isKnownSex(""))
        assertFalse(BodyComposition.isKnownSex("male"))
        assertFalse(BodyComposition.isKnownSex(null))
    }

    // ── Input sanity ──────────────────────────────────────────────────────────────────────────

    @Test fun `plausibility guards reject obviously mistyped values`() {
        assertTrue(BodyComposition.isPlausibleHeight(180f))
        assertFalse(BodyComposition.isPlausibleHeight(18f))     // metres typed as cm
        assertFalse(BodyComposition.isPlausibleHeight(1800f))

        assertTrue(BodyComposition.isPlausibleWaist(85f))
        assertFalse(BodyComposition.isPlausibleWaist(900f))

        assertTrue(BodyComposition.isPlausibleNeck(38f))
        assertFalse(BodyComposition.isPlausibleNeck(380f))

        assertTrue(BodyComposition.isPlausibleHip(98f))
        assertFalse(BodyComposition.isPlausibleHip(9f))

        assertTrue(BodyComposition.isPlausibleWeight(82.5f))
        assertFalse(BodyComposition.isPlausibleWeight(0f))
        assertFalse(BodyComposition.isPlausibleWeight(1000f))
    }
}
