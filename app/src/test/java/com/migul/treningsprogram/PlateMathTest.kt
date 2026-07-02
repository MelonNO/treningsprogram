package com.migul.treningsprogram

import com.migul.treningsprogram.data.db.entity.GymPreset
import com.migul.treningsprogram.ui.log.PlateMath
import com.migul.treningsprogram.ui.log.PlateMath.PlateProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F4 — plate-loading math behind the keypad's "per side" readout: barbell/dumbbell recognition,
 * greedy decomposition against a per-preset profile, inexact targets, the below-bar/empty-bar
 * edges, profile resolution from GymPreset (null = 50 mm home defaults), and plate-CSV parsing.
 */
class PlateMathTest {

    /** A standard commercial gym: 20 kg bar, full metric plates, fixed dumbbells. */
    private val gym = PlateProfile(
        barKg = 20f, dumbbellBarKg = 2f,
        plates = listOf(25f, 20f, 15f, 10f, 5f, 2.5f, 1.25f),
        loadableDumbbells = false,
    )

    // ── exercise recognition ───────────────────────────────────────────────────────────────

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

    @Test fun `dumbbell lifts are recognized as dumbbell`() {
        listOf("Dumbbell Bench Press", "DB Row", "Incline DB Press").forEach {
            assertTrue(it, PlateMath.isDumbbellExercise(it))
        }
        assertFalse(PlateMath.isDumbbellExercise("Barbell Row"))
        assertFalse(PlateMath.isDumbbellExercise("Pull-ups"))
    }

    // ── decomposition (explicit gym profile) ───────────────────────────────────────────────

    @Test fun `exact load decomposes greedily per side`() {
        // 100 kg on a 20 kg bar → 40 per side → 25 + 15
        val l = PlateMath.perSide(100f, gym.barKg, gym.plates)!!
        assertEquals(listOf(25f, 15f), l.perSide)
        assertTrue(l.exact)
    }

    @Test fun `unreachable total reports the closest achievable weight`() {
        // 61 on 20 kg bar → 20.5/side → 20 loaded, 0.5 left → achievable 60
        val l = PlateMath.perSide(61f, gym.barKg, gym.plates)!!
        assertEquals(listOf(20f), l.perSide)
        assertFalse(l.exact)
        assertEquals(60f, l.achievableTotal, 0.001f)
    }

    @Test fun `below bar weight yields null and empty bar is exact`() {
        assertNull(PlateMath.perSide(15f, gym.barKg, gym.plates))
        val empty = PlateMath.perSide(20f, gym.barKg, gym.plates)!!
        assertTrue(empty.perSide.isEmpty())
        assertTrue(empty.exact)
    }

    // ── home (default) profile: 7 kg 50 mm bar + home plate set ───────────────────────────

    @Test fun `home defaults are the 50mm setup`() {
        val d = PlateProfile.DEFAULT
        assertEquals(7f, d.barKg, 0f)
        assertEquals(2f, d.dumbbellBarKg, 0f)
        assertEquals(listOf(20f, 15f, 10f, 5f, 2f, 1.45f, 1.25f, 1f, 0.5f), d.plates)
        assertTrue(d.loadableDumbbells)
    }

    @Test fun `home bar decomposes with the home plate set`() {
        // 47 kg total on the 7 kg bar → 20/side → 20
        assertEquals("20 per side", PlateMath.display(47f, "Deadlift"))
        // 10.9 kg total → 1.95/side → 1.45 + 0.5
        assertEquals("1.45 + 0.5 per side", PlateMath.display(10.9f, "Squat"))
        assertEquals("Empty bar (7 kg)", PlateMath.display(7f, "Back Squat"))
    }

    @Test fun `dumbbell lifts get a readout on loadable-dumbbell profiles only`() {
        // Home default: 12 kg dumbbell on a 2 kg handle → 5/side → 5
        assertEquals("5 per side (dumbbell)", PlateMath.display(12f, "Dumbbell Bench Press"))
        // Gym profile: fixed dumbbells → no readout
        assertNull(PlateMath.display(12f, "Dumbbell Bench Press", gym))
    }

    @Test fun `display shows plates per side for barbell lifts on the gym profile`() {
        assertEquals("25 + 15 per side", PlateMath.display(100f, "Barbell Bench Press", gym))
        assertEquals("≈ 20 per side (60 kg)", PlateMath.display(61f, "Deadlift", gym))
        assertNull(PlateMath.display(100f, "Lat Pulldown", gym))
        assertNull(PlateMath.display(3f, "Deadlift", gym))
    }

    // ── profile resolution from GymPreset ──────────────────────────────────────────────────

    @Test fun `null preset and null fields resolve to the home defaults`() {
        assertEquals(PlateProfile.DEFAULT, PlateProfile.from(null))
        assertEquals(PlateProfile.DEFAULT, PlateProfile.from(GymPreset(name = "x")))
    }

    @Test fun `preset fields override the defaults individually`() {
        val p = PlateProfile.from(
            GymPreset(
                name = "Gym", barWeightKg = 20f, platesCsv = "25, 20, 10",
                loadableDumbbells = false,
            )
        )
        assertEquals(20f, p.barKg, 0f)
        assertEquals(listOf(25f, 20f, 10f), p.plates)
        assertFalse(p.loadableDumbbells)
        assertEquals(PlateProfile.DEFAULT.dumbbellBarKg, p.dumbbellBarKg, 0f) // untouched → default
    }

    @Test fun `blank or garbage platesCsv falls back to the default set`() {
        assertEquals(PlateProfile.DEFAULT.plates, PlateProfile.from(GymPreset(name = "x", platesCsv = "")).plates)
        assertEquals(PlateProfile.DEFAULT.plates, PlateProfile.from(GymPreset(name = "x", platesCsv = "abc")).plates)
    }

    // ── plate-CSV parsing ──────────────────────────────────────────────────────────────────

    @Test fun `parsePlates accepts mixed separators and sorts descending`() {
        assertEquals(
            listOf(20f, 10f, 5f, 1.25f),
            PlateProfile.parsePlates("5, 20; 1.25 / 10"),
        )
    }

    @Test fun `parsePlates drops invalid and non-positive tokens`() {
        assertEquals(listOf(10f, 5f), PlateProfile.parsePlates("10, x, -2, 0, 5"))
        assertTrue(PlateProfile.parsePlates("").isEmpty())
    }
}
