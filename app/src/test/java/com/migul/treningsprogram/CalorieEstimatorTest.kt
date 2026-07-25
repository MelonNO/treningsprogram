package com.migul.treningsprogram

import com.migul.treningsprogram.data.db.entity.BodyMeasurement
import com.migul.treningsprogram.data.db.entity.WorkoutSession
import com.migul.treningsprogram.data.db.entity.WorkoutSet
import com.migul.treningsprogram.domain.CalorieEstimator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * QoL item 03 — the on-the-fly calorie estimator all three surfaces (completion dialog, Recap,
 * Stats weekly pulse) share. MET math: kcal = MET × kg × h, strength 4.0 / cardio 7.0 blended by
 * cardio set share, rounded to the nearest 10 with a 10-kcal floor.
 */
class CalorieEstimatorTest {

    private fun session(
        id: Long = 1L,
        dateMs: Long = 1_000_000L,
        durationMin: Int = 60,
        kind: String? = null,
    ) = WorkoutSession(id = id, dateMs = dateMs, durationMinutes = durationMin, isCompleted = true, kind = kind)

    private fun set(muscle: String = "Chest", warmup: Boolean = false) = WorkoutSet(
        sessionId = 1L, exerciseName = "X", muscleGroup = muscle,
        setNumber = 1, reps = 8, weightKg = 60f, isWarmup = warmup
    )

    private fun strengthSets(n: Int) = List(n) { set("Chest") }
    private fun cardioSets(n: Int) = List(n) { set("Cardio") }

    // ── MET blend: cardio vs strength weighting ───────────────────────────────────────────────

    @Test fun `strength-only hour at 75 kg is 300 kcal`() {
        // 4.0 MET × 75 kg × 1 h = 300
        assertEquals(300, CalorieEstimator.estimateSession(session(), strengthSets(12), 75f))
    }

    @Test fun `cardio-only hour at 75 kg weighs higher than strength`() {
        // 7.0 MET × 75 kg × 1 h = 525 → nearest 10 = 530
        assertEquals(530, CalorieEstimator.estimateSession(session(), cardioSets(12), 75f))
    }

    @Test fun `half-cardio session blends the two METs`() {
        // MET 5.5 × 75 × 1 h = 412.5 → 410
        val mixed = strengthSets(6) + cardioSets(6)
        assertEquals(410, CalorieEstimator.estimateSession(session(), mixed, 75f))
    }

    @Test fun `warm-up sets count toward the mix`() {
        // 2 cardio warm-ups + 2 working strength sets = 50% cardio share → same as half-cardio.
        val sets = listOf(set("Cardio", warmup = true), set("Cardio", warmup = true), set(), set())
        assertEquals(410, CalorieEstimator.estimateSession(session(), sets, 75f))
    }

    // ── Duration scaling and clamps ───────────────────────────────────────────────────────────

    @Test fun `duration scales linearly`() {
        assertEquals(150, CalorieEstimator.estimateSession(session(durationMin = 30), strengthSets(8), 75f))
    }

    @Test fun `zero stored duration falls back to 3 min per set`() {
        // 10 sets → 30 min → 4.0 × 75 × 0.5 h = 150
        assertEquals(150, CalorieEstimator.estimateSession(session(durationMin = 0), strengthSets(10), 75f))
    }

    @Test fun `absurd duration is capped at 6 hours`() {
        // 10 000 min → 360 min → 4.0 × 75 × 6 h = 1800
        assertEquals(1800, CalorieEstimator.estimateSession(session(durationMin = 10_000), strengthSets(12), 75f))
    }

    @Test fun `tiny session floors at 10 kcal, never 0`() {
        // 1 min → 4.0 × 75 × (1/60) = 5 kcal → floor 10
        assertEquals(10, CalorieEstimator.estimateSession(session(durationMin = 1), strengthSets(1), 75f))
    }

    @Test fun `absurd body weight is clamped`() {
        // 999 kg → clamp 250 → 4.0 × 250 × 1 h = 1000; 5 kg → clamp 30 → 120
        assertEquals(1000, CalorieEstimator.estimateSession(session(), strengthSets(12), 999f))
        assertEquals(120, CalorieEstimator.estimateSession(session(), strengthSets(12), 5f))
    }

    // ── Exclusions: placeholders and empty sessions show nothing ─────────────────────────────

    @Test fun `REST and MISSED placeholders get no figure`() {
        assertNull(CalorieEstimator.estimateSession(session(kind = WorkoutSession.KIND_REST), emptyList(), 75f))
        assertNull(CalorieEstimator.estimateSession(session(kind = WorkoutSession.KIND_MISSED), emptyList(), 75f))
        // Even if a placeholder somehow carried sets, the kind alone must suppress the figure.
        assertNull(CalorieEstimator.estimateSession(session(kind = WorkoutSession.KIND_REST), strengthSets(3), 75f))
    }

    @Test fun `session with no sets gets no figure`() {
        assertNull(CalorieEstimator.estimateSession(session(), emptyList(), 75f))
    }

    // ── Body-weight selection (brief A2) ──────────────────────────────────────────────────────

    @Test fun `no weigh-ins at all falls back to 75 kg`() {
        assertEquals(75f, CalorieEstimator.bodyWeightFor(1_000L, emptyList()))
    }

    @Test fun `picks the most recent weigh-in at or before the session`() {
        val weighIns = listOf(
            BodyMeasurement(dateMs = 100L, weightKg = 70f),
            BodyMeasurement(dateMs = 500L, weightKg = 80f),
            BodyMeasurement(dateMs = 900L, weightKg = 90f),   // after the session — ignored
        )
        assertEquals(80f, CalorieEstimator.bodyWeightFor(600L, weighIns))
    }

    @Test fun `weigh-in exactly on the session instant counts`() {
        val weighIns = listOf(BodyMeasurement(dateMs = 600L, weightKg = 82f))
        assertEquals(82f, CalorieEstimator.bodyWeightFor(600L, weighIns))
    }

    @Test fun `all weigh-ins after the session uses the earliest one, not 75`() {
        val weighIns = listOf(
            BodyMeasurement(dateMs = 900L, weightKg = 88f),
            BodyMeasurement(dateMs = 700L, weightKg = 84f),
        )
        assertEquals(84f, CalorieEstimator.bodyWeightFor(100L, weighIns))
    }

    @Test fun `typo weigh-in is clamped to the plausible band`() {
        assertEquals(30f, CalorieEstimator.bodyWeightFor(600L, listOf(BodyMeasurement(dateMs = 1L, weightKg = 7.5f))))
        assertEquals(250f, CalorieEstimator.bodyWeightFor(600L, listOf(BodyMeasurement(dateMs = 1L, weightKg = 750f))))
    }

    // ── Weekly total (Monday-based logical week, same math as WeekDelta) ─────────────────────

    private val MON = 4L   // Monday 1970-01-05 (epoch day 0 = Thursday) — same anchor as WeekDeltaTest

    @Test fun `weekly total sums only this Monday-week's sessions`() {
        val inputs = listOf(
            // In-week strength hour → 300
            CalorieEstimator.SessionInput(session(id = 1), strengthSets(10), MON),
            // In-week cardio hour → 530
            CalorieEstimator.SessionInput(session(id = 2), cardioSets(10), MON + 2),
            // Last week — excluded
            CalorieEstimator.SessionInput(session(id = 3), strengthSets(10), MON - 3),
            // Next week — excluded
            CalorieEstimator.SessionInput(session(id = 4), strengthSets(10), MON + 7),
        )
        assertEquals(830, CalorieEstimator.weeklyTotal(inputs, emptyList(), todayEpochDay = MON + 4))
    }

    @Test fun `placeholders contribute zero to the weekly total`() {
        val inputs = listOf(
            CalorieEstimator.SessionInput(session(id = 1), strengthSets(10), MON),
            CalorieEstimator.SessionInput(session(id = 2, kind = WorkoutSession.KIND_REST), emptyList(), MON + 1),
            CalorieEstimator.SessionInput(session(id = 3, kind = WorkoutSession.KIND_MISSED), emptyList(), MON + 2),
        )
        assertEquals(300, CalorieEstimator.weeklyTotal(inputs, emptyList(), todayEpochDay = MON + 4))
    }

    @Test fun `empty week totals zero`() {
        assertEquals(0, CalorieEstimator.weeklyTotal(emptyList(), emptyList(), todayEpochDay = MON))
    }

    @Test fun `weekly total equals the sum of the per-session figures`() {
        val a = CalorieEstimator.SessionInput(session(id = 1, durationMin = 45), strengthSets(8), MON)
        val b = CalorieEstimator.SessionInput(session(id = 2, durationMin = 25), cardioSets(4), MON + 3)
        val expected = listOf(a, b).sumOf {
            CalorieEstimator.estimateSession(it.session, it.sets, 75f)!!
        }
        assertEquals(expected, CalorieEstimator.weeklyTotal(listOf(a, b), emptyList(), todayEpochDay = MON + 5))
    }

    @Test fun `weekly total uses each session's own historical body weight`() {
        val weighIns = listOf(BodyMeasurement(dateMs = 500L, weightKg = 100f))
        val early = CalorieEstimator.SessionInput(session(id = 1, dateMs = 100L), strengthSets(10), MON)   // before any weigh-in → earliest (100 kg)
        val late = CalorieEstimator.SessionInput(session(id = 2, dateMs = 900L), strengthSets(10), MON + 1) // after weigh-in → 100 kg
        // 4.0 × 100 × 1 h = 400 each
        assertEquals(800, CalorieEstimator.weeklyTotal(listOf(early, late), weighIns, todayEpochDay = MON + 3))
    }

    // ── Presentation ──────────────────────────────────────────────────────────────────────────

    @Test fun `format reads as an approximation`() {
        assertEquals("~ 320 kcal", CalorieEstimator.format(320))
    }
}
