package com.migul.treningsprogram

import com.migul.treningsprogram.domain.WeightStep
import com.migul.treningsprogram.ui.log.PlateMath
import com.migul.treningsprogram.ui.log.PlateMath.PlateProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Cluster A / brief 01 — the weight field's − and + buttons step to the next weight that is both
 * sensible for the lift in front of the user and actually loadable at the ACTIVE gym.
 *
 * The user's own sanity-check pair is the backbone of this suite: **a bench press must never be
 * offered a 0.5 kg step, even at a gym stocking 0.5 kg plates; an 8 kg lateral raise must get a
 * small one.** Both are fixtures below.
 */
class WeightStepTest {

    /** A standard commercial gym: 20 kg bar, full metric plates, FIXED dumbbells. Grid: 2.5 kg. */
    private val commercial = PlateProfile(
        barKg = 20f, dumbbellBarKg = 2f,
        plates = listOf(25f, 20f, 15f, 10f, 5f, 2.5f, 1.25f),
        loadableDumbbells = false,
    )

    /** The user's 50 mm home setup (7 kg bar, plate-loaded handles, 0.5 kg plates in stock). */
    private val home = PlateProfile.DEFAULT

    /** A gym that stocks 0.5 kg plates AND plate-loaded handles — the bench-press trap. */
    private val halfKilo = PlateProfile(
        barKg = 20f, dumbbellBarKg = 2f,
        plates = listOf(20f, 10f, 5f, 2.5f, 1.25f, 0.5f),
        loadableDumbbells = true,
    )

    /** A garage with nothing but 20 kg plates: the smallest change it can make is 40 kg. */
    private val coarse = PlateProfile(
        barKg = 20f, dumbbellBarKg = 2f,
        plates = listOf(20f),
        loadableDumbbells = false,
    )

    private val bench = "Barbell Bench Press"
    private val squat = "Barbell Squat"
    /** Dumbbell by NATURE — the plan name says nothing, the exercise DB says "Dumbbell". */
    private val lateralRaise = "Lateral Raise"

    private fun up(cur: Float, name: String, p: PlateProfile?, eq: String? = null) =
        WeightStep.next(cur, up = true, exerciseName = name, profile = p, dbEquipment = eq)

    private fun down(cur: Float, name: String, p: PlateProfile?, eq: String? = null) =
        WeightStep.next(cur, up = false, exerciseName = name, profile = p, dbEquipment = eq)

    // ── the size-scaled step ──────────────────────────────────────────────────────────────────

    @Test fun `the aimed-at step scales with the load, with a floor and a ceiling`() {
        assertEquals(1f, WeightStep.idealStepKg(0f), 0.001f)     // empty field / bodyweight
        assertEquals(1f, WeightStep.idealStepKg(8f), 0.001f)     // light isolation
        assertEquals(1f, WeightStep.idealStepKg(20f), 0.001f)    // floor still binding
        assertEquals(2f, WeightStep.idealStepKg(40f), 0.001f)
        assertEquals(3f, WeightStep.idealStepKg(60f), 0.001f)
        assertEquals(5f, WeightStep.idealStepKg(100f), 0.001f)
        assertEquals(5f, WeightStep.idealStepKg(200f), 0.001f)   // ceiling
    }

    // ── the user's sanity-check pair ──────────────────────────────────────────────────────────

    @Test fun `bench press is never offered a sub-kilogram step, even where 0-5 kg plates exist`() {
        listOf(60f, 62.5f, 80f, 100f, 61f, 87.3f).forEach { start ->
            listOf(halfKilo, home, commercial).forEach { gym ->
                val plus = up(start, bench, gym)
                val minus = down(start, bench, gym)
                assertTrue(
                    "+ from $start at $gym moved ${plus - start}",
                    plus - start >= 1f
                )
                assertTrue(
                    "− from $start at $gym moved ${start - minus}",
                    start - minus >= 1f
                )
            }
        }
    }

    @Test fun `an 8 kg lateral raise gets a small step, not a 2-5 kg jump`() {
        val plus = up(8f, lateralRaise, home, "Dumbbell")
        val minus = down(8f, lateralRaise, home, "Dumbbell")
        assertTrue("+ gave $plus", plus - 8f in 0.1f..2.4f)
        assertTrue("− gave $minus", 8f - minus in 0.1f..2.4f)
        // …and both are weights the handle + this gym's plates can genuinely make.
        assertExactlyLoadable(plus, lateralRaise, home, "Dumbbell")
        assertExactlyLoadable(minus, lateralRaise, home, "Dumbbell")
    }

    @Test fun `a heavy lift keeps a properly sized step at a gym with fine-grained plates`() {
        // The home 50 mm set can make 1 kg changes, and its 1.45 kg plates mean round numbers are
        // often not the nearest loadable weight. The step must still scale with the load rather
        // than degenerating into the next 1 kg rung.
        assertEquals(63f, up(60f, bench, home), 0.001f)
        assertEquals(57f, down(60f, bench, home), 0.001f)
        assertEquals(105f, up(100f, squat, home), 0.001f)
        assertEquals(95f, down(100f, squat, home), 0.001f)
        assertEquals(145f, up(140f, squat, home), 0.001f)
    }

    // ── snapping from a hand-typed, un-loadable value ─────────────────────────────────────────

    @Test fun `plus from a hand-typed 61 kg lands on the next loadable weight above it`() {
        assertEquals(62.5f, up(61f, bench, commercial), 0.001f)
    }

    @Test fun `minus from a hand-typed 61 kg lands on the next loadable weight below it`() {
        assertEquals(60f, down(61f, bench, commercial), 0.001f)
    }

    @Test fun `snapping never adds the step on top of the odd value`() {
        // 61 + 2.5 = 63.5 and 61 − 2.5 = 58.5 are both un-loadable at this gym — neither may appear.
        val plus = up(61f, bench, commercial)
        val minus = down(61f, bench, commercial)
        assertTrue(abs(plus - 63.5f) > 0.001f)
        assertTrue(abs(minus - 58.5f) > 0.001f)
        assertExactlyLoadable(plus, bench, commercial)
        assertExactlyLoadable(minus, bench, commercial)
    }

    // ── mirroring ─────────────────────────────────────────────────────────────────────────────

    @Test fun `minus mirrors plus - same size step in each direction from the same weight`() {
        listOf(40f, 45f, 50f, 60f, 70f, 75f, 80f, 100f, 120f).forEach { cur ->
            val upDelta = up(cur, bench, commercial) - cur
            val downDelta = cur - down(cur, bench, commercial)
            assertEquals("from $cur", upDelta, downDelta, 0.001f)
        }
    }

    // ── the zero floor / bodyweight field ─────────────────────────────────────────────────────

    @Test fun `minus never drives the weight below zero`() {
        listOf(0f, 0.4f, 1f, 2f, 2.5f, 5f).forEach { cur ->
            listOf(bench, lateralRaise, "Pull-Up", "Leg Press").forEach { name ->
                listOf(home, commercial, null).forEach { gym ->
                    val result = down(cur, name, gym, "Dumbbell")
                    assertTrue("$name from $cur gave $result", result >= 0f)
                }
            }
        }
        assertEquals(0f, down(0f, bench, home), 0.001f)
        assertEquals(0f, down(0f, "Pull-Up", home), 0.001f)
    }

    @Test fun `an empty or zero field still steps up, by the smallest sensible amount`() {
        // Bodyweight / unclassified work has no inventory: the generic grid's first rung.
        assertEquals(2.5f, up(0f, "Pull-Up", home), 0.001f)
        // A barbell lift's smallest real weight is the empty bar.
        assertEquals(home.barKg, up(0f, bench, home), 0.001f)
        assertEquals(commercial.barKg, up(0f, bench, commercial), 0.001f)
    }

    // ── the hard "always loadable" guarantee ──────────────────────────────────────────────────

    private val startWeights =
        listOf(0f, 0.5f, 1f, 2.5f, 7f, 8f, 12.5f, 20f, 22.5f, 31.7f, 47f, 60f, 61f, 73.3f, 100f, 142.5f)

    @Test fun `every weight the buttons produce is loadable at that gym and reads out exactly`() {
        val guaranteed = listOf(
            Triple(home, bench, null),
            Triple(home, "Dumbbell Curl", null),
            Triple(home, lateralRaise, "Dumbbell"),
            Triple(commercial, bench, null),
            Triple(halfKilo, bench, null),
            Triple(halfKilo, "Dumbbell Curl", null),
            Triple(coarse, squat, null),
        )
        guaranteed.forEach { (gym, name, eq) ->
            startWeights.forEach { start ->
                listOf(true, false).forEach { direction ->
                    val result = WeightStep.next(start, direction, name, gym, eq)
                    assertTrue("$name $start → $result", result >= 0f)
                    if (result > 0f) assertExactlyLoadable(result, name, gym, eq)
                }
            }
        }
    }

    @Test fun `repeated presses stay on loadable weights in both directions`() {
        var w = 61f      // deliberately starting off the grid
        repeat(6) {
            val next = up(w, bench, home)
            assertTrue("$w → $next", next > w)
            assertExactlyLoadable(next, bench, home)
            w = next
        }
        repeat(6) {
            val next = down(w, bench, home)
            assertTrue("$w → $next", next < w)
            assertExactlyLoadable(next, bench, home)
            w = next
        }
    }

    @Test fun `the smallest achievable change is used when the gym cannot make a smaller one`() {
        // 20 kg plates only: 20 (empty bar) → 60 → 100. The aimed-at step is 1–5 kg; the button
        // still has to do something, so it takes the smallest change the gym can actually make.
        assertEquals(60f, up(20f, squat, coarse), 0.001f)
        assertEquals(20f, down(60f, squat, coarse), 0.001f)
        assertEquals(100f, up(60f, squat, coarse), 0.001f)
    }

    @Test fun `a press never moves by less than the gym's smallest achievable change`() {
        listOf(home, commercial, halfKilo, coarse).forEach { gym ->
            listOf(20f, 40f, 60f, 100f).forEach { start ->
                val plus = up(start, bench, gym)
                val minus = down(start, bench, gym)
                assertTrue("+ stalled at $start ($gym)", plus > start)
                assertTrue("− stalled at $start ($gym)", minus < start)
            }
        }
    }

    // ── where the app has no inventory ────────────────────────────────────────────────────────

    @Test fun `fixed dumbbells use the sensible 2 kg default`() {
        assertEquals(10f, up(8f, "Dumbbell Lateral Raise", commercial), 0.001f)
        assertEquals(6f, down(8f, "Dumbbell Lateral Raise", commercial), 0.001f)
        // …and never a plate breakdown, because there is no inventory to guarantee one.
        assertEquals(null, PlateMath.display(10f, "Dumbbell Lateral Raise", commercial))
    }

    @Test fun `machines and unclassified lifts use the sensible 2-5 kg default`() {
        assertEquals(52.5f, up(50f, "Leg Press", commercial), 0.001f)
        assertEquals(47.5f, down(50f, "Leg Press", commercial), 0.001f)
        assertEquals(105f, up(100f, "Cable Row", home), 0.001f)
        // An AI-invented name nothing recognises still steps sensibly.
        assertEquals(22.5f, up(20f, "Reverse Hyper Sled Drag", home), 0.001f)
    }

    @Test fun `an unresolved gym profile falls back to the generic grid, never another gym's plates`() {
        assertEquals(62.5f, up(60f, bench, null), 0.001f)
        assertEquals(57.5f, down(60f, bench, null), 0.001f)
        assertEquals(10f, up(8f, "Dumbbell Curl", null), 0.001f)
    }

    // ── the behaviour follows the ACTIVE gym ──────────────────────────────────────────────────

    @Test fun `the same lift steps differently at gyms with different plates`() {
        val atCommercial = up(100f, squat, commercial)
        val atCoarse = up(100f, squat, coarse)
        assertEquals(105f, atCommercial, 0.001f)
        assertEquals(140f, atCoarse, 0.001f)
        assertTrue(abs(atCommercial - atCoarse) > 0.001f)

        // …including from the same odd hand-typed value.
        assertTrue(abs(up(61f, bench, commercial) - up(61f, bench, home)) > 0.001f)
    }

    @Test fun `dumbbell-by-nature lifts follow the handle, not the bar`() {
        // "Lateral Raise" has no dumbbell word in it — only the exercise DB knows. With that
        // knowledge it loads onto the 2 kg handle; without it, it is just an unclassified lift.
        val withDb = up(8f, lateralRaise, home, "Dumbbell")
        val withoutDb = up(8f, lateralRaise, home, null)
        assertExactlyLoadable(withDb, lateralRaise, home, "Dumbbell")
        assertEquals(10f, withoutDb, 0.001f)      // generic 2.5 kg grid
        assertTrue(abs(withDb - withoutDb) > 0.001f)
    }

    // ── helpers ───────────────────────────────────────────────────────────────────────────────

    /**
     * The guarantee in the only form that matters on screen: the readout under the field shows a
     * breakdown for this exact weight, with no "≈" (which would mean the gym cannot make it).
     */
    private fun assertExactlyLoadable(
        weight: Float,
        name: String,
        profile: PlateProfile,
        dbEquipment: String? = null,
    ) {
        assertTrue("$name $weight kg", WeightStep.isLoadable(weight, name, profile, dbEquipment))
        val readout = PlateMath.display(weight, name, profile, dbEquipment)
        assertNotNull("no readout for $name at $weight kg", readout)
        assertTrue("$name at $weight kg reads out as '$readout'", !readout!!.startsWith("≈"))
    }
}
