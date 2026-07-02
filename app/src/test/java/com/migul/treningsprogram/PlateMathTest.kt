package com.migul.treningsprogram

import com.migul.treningsprogram.ui.log.PlateMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F4 — plate-loading math behind the keypad's "per side" readout: barbell
 * recognition (with dumbbell/machine vetoes), greedy decomposition, inexact
 * targets, and the below-bar / empty-bar edges.
 */
class PlateMathTest {

    // ── barbell recognition ────────────────────────────────────────────────────────────────────

    @Test fun `classic barbell lifts are recognized`() {
        listOf(
            "Barbell Bench Press", "Deadlift", "Back Squat", "Overhead Press",
            "Bent-Over Row", "Romanian Deadlift", "Hip Thrust", "Squat",
        ).forEach { assertTrue(it, PlateMath.isBarbellExercise(it)) }
    }

    @Test fun `non-barbell variants are vetoed even when the lift name matches`() {
        listOf(
            "Dumbbell Bench Press", "Goblet Squat", "Smith Machine Squat",
            "Bulgarian Split Squat", "Kettlebell Deadlift", "Cable Row",
            "Machine Overhead Press", "Trap Bar Deadlift", "Landmine Press",
        ).forEach { assertFalse(it, PlateMath.isBarbellExercise(it)) }
    }

    @Test fun `unrelated exercises are not barbell`() {
        listOf("Pull-ups", "Plank", "Running", "Lateral Raise", "").forEach {
            assertFalse(it, PlateMath.isBarbellExercise(it))
        }
    }

    // ── decomposition ─────────────────────────────────────────────────────────────────────────

    @Test fun `exact load decomposes greedily per side`() {
        // 100 kg → 40 per side → 25 + 15
        assertEquals(listOf(25f, 15f), PlateMath.perSide(100f)!!.perSide)
        assertTrue(PlateMath.perSide(100f)!!.exact)
    }

    @Test fun `small increments use the fractional plates`() {
        // 62.5 → 21.25/side → 20 + 1.25
        val l = PlateMath.perSide(62.5f)!!
        assertEquals(listOf(20f, 1.25f), l.perSide)
        assertTrue(l.exact)
    }

    @Test fun `unreachable total reports the closest achievable weight`() {
        // 61 → 20.5/side → 20 exact, 0.5 leftover → achievable 60
        val l = PlateMath.perSide(61f)!!
        assertEquals(listOf(20f), l.perSide)
        assertFalse(l.exact)
        assertEquals(60f, l.achievableTotal, 0.001f)
    }

    @Test fun `below bar weight yields null`() {
        assertNull(PlateMath.perSide(15f))
    }

    @Test fun `empty bar is exact with no plates`() {
        val l = PlateMath.perSide(20f)!!
        assertTrue(l.perSide.isEmpty())
        assertTrue(l.exact)
    }

    // ── display line ───────────────────────────────────────────────────────────────────────────

    @Test fun `display shows plates per side for barbell lifts`() {
        assertEquals("25 + 15 per side", PlateMath.display(100f, "Barbell Bench Press"))
    }

    @Test fun `display marks inexact loads with the achievable total`() {
        assertEquals("≈ 20 per side (60 kg)", PlateMath.display(61f, "Deadlift"))
    }

    @Test fun `display hides for non-barbell or below-bar`() {
        assertNull(PlateMath.display(100f, "Dumbbell Press"))
        assertNull(PlateMath.display(10f, "Deadlift"))
    }

    @Test fun `display labels the empty bar`() {
        assertEquals("Empty bar (20 kg)", PlateMath.display(20f, "Back Squat"))
    }
}
