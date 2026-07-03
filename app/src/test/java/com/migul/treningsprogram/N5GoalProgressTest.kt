package com.migul.treningsprogram

import com.migul.treningsprogram.data.db.entity.WorkoutSet
import com.migul.treningsprogram.domain.Epley
import com.migul.treningsprogram.domain.GoalProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * N5 — pure goal math: session bests (warm-ups NEVER count), reach detection for both goal
 * kinds (A-G2), progress %, and the one-step Home nudge. Goals are absolute weights — no
 * body-weight input exists anywhere in these paths (the no-weigh-in-needed AC).
 */
class N5GoalProgressTest {

    private fun set(weight: Float, reps: Int, warmup: Boolean = false) = WorkoutSet(
        sessionId = 1L, exerciseName = "Bench Press", muscleGroup = "Chest",
        setNumber = 1, reps = reps, weightKg = weight, isWarmup = warmup
    )

    // ── session bests ───────────────────────────────────────────────────────────────────────

    @Test fun `warm-ups never contribute to session bests`() {
        val bests = GoalProgress.sessionBests(
            listOf(set(100f, 1, warmup = true), set(60f, 5, warmup = false))
        )
        assertEquals(60f, bests.bestWeightKg)
        assertEquals(Epley.estimate(60f, 5), bests.bestE1rm!!, 0.0001)
    }

    @Test fun `all-warm-up session has no bests and reaches nothing`() {
        val bests = GoalProgress.sessionBests(listOf(set(100f, 5, warmup = true)))
        assertNull(bests.bestWeightKg)
        assertNull(bests.bestE1rm)
        assertFalse(GoalProgress.isReached(isE1rm = false, targetKg = 50f, bests = bests))
        assertFalse(GoalProgress.isReached(isE1rm = true, targetKg = 50f, bests = bests))
    }

    // ── reach detection (A-G2) ──────────────────────────────────────────────────────────────

    @Test fun `weight goal - reached when a working set meets or exceeds the target`() {
        val exact = GoalProgress.sessionBests(listOf(set(100f, 1)))
        val below = GoalProgress.sessionBests(listOf(set(97.5f, 1)))
        assertTrue(GoalProgress.isReached(false, 100f, exact))
        assertFalse(GoalProgress.isReached(false, 100f, below))
    }

    @Test fun `e1RM goal - reached via the session e1RM, not the raw weight`() {
        // 90 kg × 5 → e1RM = 105 ≥ 100 target, even though no set was at 100 kg.
        val bests = GoalProgress.sessionBests(listOf(set(90f, 5)))
        assertTrue(GoalProgress.isReached(isE1rm = true, targetKg = 100f, bests = bests))
        // The same session does NOT reach a 100 kg WEIGHT goal.
        assertFalse(GoalProgress.isReached(isE1rm = false, targetKg = 100f, bests = bests))
    }

    // ── progress ────────────────────────────────────────────────────────────────────────────

    @Test fun `progress percent and line`() {
        assertEquals(87, GoalProgress.progressPercent(87f, 100f))
        assertEquals("87% of the way to 100 kg", GoalProgress.progressLine(87f, 100f))
        assertEquals(0, GoalProgress.progressPercent(null, 100f))
        assertEquals(100, GoalProgress.progressPercent(120f, 100f))   // clamped
        assertEquals(0, GoalProgress.progressPercent(50f, 0f))        // degenerate target
    }

    // ── nudge ───────────────────────────────────────────────────────────────────────────────

    @Test fun `nudge fires within one progression step and not before`() {
        assertNull(GoalProgress.nudgeLine("Bench Press", 90f, 100f, false))       // 10 kg away
        assertNull(GoalProgress.nudgeLine("Bench Press", null, 100f, false))      // no history
        val near = GoalProgress.nudgeLine("Bench Press", 97.5f, 100f, false)
        assertTrue(near!!, near.contains("2.5 kg from your 100 kg goal"))
        val met = GoalProgress.nudgeLine("Bench Press", 102.5f, 100f, false)
        assertTrue(met!!, met.contains("within reach"))
    }

    @Test fun `nudge labels e1RM goals honestly`() {
        val line = GoalProgress.nudgeLine("Squat", 99f, 100f, isE1rm = true)
        assertTrue(line!!, line.contains("est. 1RM goal"))
    }

    // ── date flavor (A-G5: flavor, never a failure state) ───────────────────────────────────

    @Test fun `date flavor - month only same year, month + year otherwise, none for zero`() {
        assertNull(GoalProgress.dateFlavor(0L))
        val nowMs = 1_780_000_000_000L // fixed instant for determinism
        val sameYear = nowMs + 30L * 24 * 60 * 60 * 1000
        val flavor = GoalProgress.dateFlavor(sameYear, nowMs)
        assertTrue(flavor!!, flavor.startsWith("by "))
        val nextYear = nowMs + 400L * 24 * 60 * 60 * 1000
        val flavor2 = GoalProgress.dateFlavor(nextYear, nowMs)
        assertTrue(flavor2!!, Regex("by .+ \\d{4}").matches(flavor2))
    }
}
