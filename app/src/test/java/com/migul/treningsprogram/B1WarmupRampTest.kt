package com.migul.treningsprogram

import com.migul.treningsprogram.ui.log.PlateMath
import com.migul.treningsprogram.ui.log.WarmupRamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B1 — warm-up ramp math: the ≈40/60/80% ladder, rounded to weights ACTUALLY loadable on the
 * active gym preset (spot-verified against PlateMath, incl. the 50 mm home-bar profile),
 * applicability via the shared heavy-compound classification, and the sensible-skip rules.
 */
class B1WarmupRampTest {

    /** The 50 mm home setup: 7 kg bar, 20/15/10/5/2/1.45/1.25/1/0.5 plate pairs. */
    private val home = PlateMath.PlateProfile.DEFAULT

    /** A commercial gym: 20 kg bar, full metric plates, fixed dumbbells. */
    private val gym = PlateMath.PlateProfile(
        barKg = 20f, dumbbellBarKg = 2f,
        plates = listOf(25f, 20f, 15f, 10f, 5f, 2.5f, 1.25f),
        loadableDumbbells = false
    )

    // ── applicability (A-W2: same classification as rest-time categories) ──────────────────────

    @Test fun `isolation, cardio and bodyweight get no suggestion`() {
        assertTrue(WarmupRamp.stepsFor("Lateral Raises", 10f, home).isEmpty())     // isolation
        assertTrue(WarmupRamp.stepsFor("Stationary Bike", 0f, home).isEmpty())     // cardio
        assertTrue(WarmupRamp.stepsFor("Rowing Machine", 50f, home).isEmpty())     // cardio machine
        assertTrue(WarmupRamp.stepsFor("Plank", 0f, home).isEmpty())               // bodyweight core
        assertTrue(WarmupRamp.stepsFor("Pull-ups", 0f, home).isEmpty())            // no working weight
    }

    @Test fun `no known working weight - no suggestion`() {
        assertTrue(WarmupRamp.stepsFor("Bench Press", 0f, home).isEmpty())
    }

    // ── ladder math + loadable rounding ─────────────────────────────────────────────────────────

    @Test fun `home bar - every step is exactly loadable and below the working weight`() {
        val steps = WarmupRamp.stepsFor("Bench Press", 60f, home)
        assertEquals(3, steps.size)
        assertEquals(listOf(5, 3, 2), steps.map { it.reps })
        steps.forEach { step ->
            assertTrue("${step.weightKg} not loadable",
                PlateMath.perSide(step.weightKg, home.barKg, home.plates)!!.exact)
            assertTrue(step.weightKg < 60f)
        }
        // Steps ascend toward the working weight.
        assertEquals(steps.map { it.weightKg }, steps.map { it.weightKg }.sorted())
        // Spot-check the exact home-profile rounding: 24 → 24 is loadable on the 7 kg bar?
        // 40% of 60 = 24 → side 8.5 → 5+2+1.45? greedy: 5,2,1.25 = 8.25 → 23.5. Verify against
        // PlateMath directly rather than hand-arithmetic:
        assertEquals(
            PlateMath.perSide(24f, home.barKg, home.plates)!!.achievableTotal, steps[0].weightKg)
    }

    @Test fun `gym bar - a step below the empty bar is skipped (A-W1)`() {
        // 40% of 40 kg = 16 kg < the 20 kg bar → that step is dropped; 60% = 24, 80% = 32 stay.
        val steps = WarmupRamp.stepsFor("Barbell Squat", 40f, gym)
        assertEquals(2, steps.size)
        assertTrue(steps.all { it.weightKg >= gym.barKg })
        assertTrue(steps.all { PlateMath.perSide(it.weightKg, gym.barKg, gym.plates)!!.exact })
    }

    @Test fun `very light working weight - shorter ladder or none, no duplicate steps`() {
        // 20 kg on the 20 kg gym bar: every fraction is below the empty bar → no ladder at all.
        assertTrue(WarmupRamp.stepsFor("Bench Press", 20f, gym).isEmpty())
        // Slightly above: duplicates collapse — never two steps at the same weight.
        val steps = WarmupRamp.stepsFor("Bench Press", 25f, gym)
        assertEquals(steps.map { it.weightKg }.distinct().size, steps.size)
    }

    @Test fun `switching preset changes the rounding (acceptance criterion)`() {
        val onHome = WarmupRamp.stepsFor("Deadlift", 100f, home)
        val onGym = WarmupRamp.stepsFor("Deadlift", 100f, gym)
        assertTrue(onHome.isNotEmpty() && onGym.isNotEmpty())
        // 40% of 100 = 40: home decomposes over the 7 kg bar, gym over the 20 kg bar.
        assertEquals(PlateMath.perSide(40f, home.barKg, home.plates)!!.achievableTotal, onHome[0].weightKg)
        assertEquals(PlateMath.perSide(40f, gym.barKg, gym.plates)!!.achievableTotal, onGym[0].weightKg)
    }

    @Test fun `loadable dumbbells decompose on the handle, fixed dumbbells use the generic grid`() {
        val homeDb = WarmupRamp.stepsFor("Dumbbell Bench Press", 30f, home)   // loadable handles
        assertTrue(homeDb.isNotEmpty())
        homeDb.forEach {
            assertTrue(PlateMath.perSide(it.weightKg, home.dumbbellBarKg, home.plates)!!.exact)
        }
        val gymDb = WarmupRamp.stepsFor("Dumbbell Bench Press", 30f, gym)     // fixed dumbbells
        gymDb.forEach { step ->
            assertEquals(0f, step.weightKg.mod(2.5f), 0.001f)
        }
    }

    @Test fun `machine compounds round on the generic grid`() {
        val steps = WarmupRamp.stepsFor("Leg Press", 200f, gym)
        assertTrue(steps.isNotEmpty())
        steps.forEach { assertEquals(0f, it.weightKg.mod(2.5f), 0.001f) }
        assertEquals(80f, steps[0].weightKg)   // 40% of 200
        assertEquals(120f, steps[1].weightKg)  // 60%
        assertEquals(160f, steps[2].weightKg)  // 80%
    }
}
