package com.migul.treningsprogram

import com.migul.treningsprogram.domain.WeightStep
import com.migul.treningsprogram.ui.log.PlateMath
import com.migul.treningsprogram.ui.log.PlateMath.PlateProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cluster A / brief 02 — the per-side plate readout is correct **whenever it is visible**.
 *
 * The screen renders exactly one decision, [PlateMath.displayForField]: the line for whatever is
 * CURRENTLY in the weight field, at the CURRENTLY selected gym, or nothing at all. Because that
 * decision is a pure function of (field text, exercise, active profile), "stale" stops being a
 * reachable state — there is no second copy of the weight or the gym to drift from.
 *
 * The original symptom (+/− with the calculator pad open left the line describing the old weight)
 * is the "follows the field text" case below; the unobserved second cause (the line rendered
 * before the active gym had loaded, describing a DIFFERENT gym) is the "unresolved profile" case.
 */
class PlateReadoutFreshnessTest {

    private val commercial = PlateProfile(
        barKg = 20f, dumbbellBarKg = 2f,
        plates = listOf(25f, 20f, 15f, 10f, 5f, 2.5f, 1.25f),
        loadableDumbbells = false,
    )

    /** The 50 mm home setup — different bar, different plates, plate-loaded handles. */
    private val home = PlateProfile.DEFAULT

    private val bench = "Barbell Bench Press"

    @Test fun `the readout is hidden until the active gym has actually been resolved`() {
        // null profile = the preset read hasn't come back yet. Showing the app-wide default here
        // is what would briefly describe a different gym's equipment.
        assertNull(PlateMath.displayForField("62.5", bench, null))
        assertNull(PlateMath.displayForField("100", bench, null))
        // …and it is not simply always null: with a resolved gym the same input renders.
        assertNotNull(PlateMath.displayForField("62.5", bench, commercial))
    }

    @Test fun `a blank or non-numeric field has no breakdown to show`() {
        assertNull(PlateMath.displayForField("", bench, commercial))
        assertNull(PlateMath.displayForField("   ", bench, commercial))
        assertNull(PlateMath.displayForField(null, bench, commercial))
        assertNull(PlateMath.displayForField("BW", bench, commercial))
    }

    @Test fun `the readout describes the weight currently in the field`() {
        val at60 = PlateMath.displayForField("60", bench, commercial)
        val at62_5 = PlateMath.displayForField("62.5", bench, commercial)
        assertEquals("20 per side", at60)
        assertEquals("20 + 1.25 per side", at62_5)
        assertTrue(at60 != at62_5)
        // Same answer as rendering the number directly — the field text is the only input.
        assertEquals(PlateMath.display(62.5f, bench, commercial), at62_5)
    }

    @Test fun `the readout describes the currently selected gym`() {
        val atCommercial = PlateMath.displayForField("61", bench, commercial)
        val atHome = PlateMath.displayForField("61", bench, home)
        assertNotNull(atCommercial)
        assertNotNull(atHome)
        assertTrue("both gyms rendered '$atCommercial'", atCommercial != atHome)
    }

    @Test fun `an un-loadable typed weight is shown as approximate, and a press fixes it`() {
        // The user hand-types 61 kg: this gym cannot make it, and the readout says so.
        assertEquals("≈ 20 per side (60 kg)", PlateMath.displayForField("61", bench, commercial))
        // Pressing + lands on a weight it CAN make — and the readout for that weight is exact.
        val stepped = WeightStep.next(61f, up = true, exerciseName = bench, profile = commercial)
        val line = PlateMath.displayForField(format(stepped), bench, commercial)
        assertNotNull(line)
        assertTrue("stepped to $stepped → '$line'", !line!!.startsWith("≈"))
    }

    @Test fun `the readout is hidden - not invented - when it cannot be computed`() {
        // Fixed dumbbells: no inventory, so no breakdown may be shown at all.
        assertNull(PlateMath.displayForField("12", "Dumbbell Curl", commercial))
        // Below the bar there is nothing to hang.
        assertNull(PlateMath.displayForField("5", bench, commercial))
        // Not a plate-loaded lift.
        assertNull(PlateMath.displayForField("60", "Leg Press", commercial))
    }

    @Test fun `switching exercise switches the breakdown`() {
        // Same weight, same gym: a barbell lift loads onto the bar, a dumbbell onto the handle.
        val barbell = PlateMath.displayForField("22", bench, home)
        val dumbbell = PlateMath.displayForField("22", "Dumbbell Curl", home)
        assertNotNull(barbell)
        assertNotNull(dumbbell)
        assertTrue("both rendered '$barbell'", barbell != dumbbell)
    }

    @Test fun `every weight the buttons produce reads out exactly, at every gym`() {
        listOf(commercial, home).forEach { gym ->
            listOf(0f, 8f, 20f, 47f, 61f, 61.4f, 100f).forEach { start ->
                listOf(true, false).forEach { up ->
                    val stepped = WeightStep.next(start, up, bench, gym)
                    if (stepped <= 0f) return@forEach
                    val line = PlateMath.displayForField(format(stepped), bench, gym)
                    assertNotNull("no readout after $start → $stepped at $gym", line)
                    assertTrue("$start → $stepped reads out as '$line'", !line!!.startsWith("≈"))
                }
            }
        }
    }

    /** Mirrors LogWorkoutFragment.formatWeight — what actually lands in the field. */
    private fun format(w: Float): String =
        if (w == w.toInt().toFloat()) w.toInt().toString() else w.toString()
}
