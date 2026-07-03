package com.migul.treningsprogram

import com.migul.treningsprogram.data.db.entity.Achievement
import com.migul.treningsprogram.data.db.entity.WorkoutSession
import com.migul.treningsprogram.data.db.entity.WorkoutSet
import com.migul.treningsprogram.domain.Epley
import com.migul.treningsprogram.domain.MonthlyWrapped
import com.migul.treningsprogram.domain.RelativeStrength
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * B7 — the monthly Wrapped over fixture data: every figure matches hand-computed stats for
 * that month; warm-ups excluded; placeholders never count as training; the baseline-not-PR
 * rule holds; thin/empty months degrade (null build, no zero-page).
 */
class B7MonthlyWrappedTest {

    /** Noon local on a given date — immune to the logical-day cutoff (04:00 default). */
    private fun noon(year: Int, month: Int, day: Int): Long =
        LocalDate.of(year, month, day).atTime(12, 0)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private var nextId = 1L
    private fun session(ms: Long, minutes: Int = 60, kind: String? = null) =
        WorkoutSession(id = nextId++, dateMs = ms, durationMinutes = minutes,
            isCompleted = true, kind = kind)

    private fun set(sessionId: Long, name: String, w: Float, reps: Int, warmup: Boolean = false) =
        WorkoutSet(sessionId = sessionId, exerciseName = name, setNumber = 1,
            reps = reps, weightKg = w, isWarmup = warmup)

    private val june = MonthlyWrapped.MonthKey(2026, 6)

    @Test fun `figures match hand-computed month stats, warm-ups excluded`() {
        val may = session(noon(2026, 5, 20))
        val s1 = session(noon(2026, 6, 3), minutes = 50)
        val s2 = session(noon(2026, 6, 10), minutes = 40)
        val rest = session(noon(2026, 6, 5), kind = WorkoutSession.KIND_REST)
        val missed = session(noon(2026, 6, 6), kind = WorkoutSession.KIND_MISSED)
        val sets = listOf(
            set(may.id, "Bench Press", 60f, 5),
            set(s1.id, "Bench Press", 62.5f, 5),
            set(s1.id, "Bench Press", 100f, 5, warmup = true),   // warm-up: excluded everywhere
            set(s2.id, "Squat", 80f, 3)
        )
        val w = MonthlyWrapped.build(june, listOf(may, s1, s2, rest, missed), sets,
            emptyList(), emptyList())!!

        assertEquals(2, w.sessions)
        assertEquals(2, w.activeDays)
        assertEquals(2, w.totalSets)                                    // warm-up not counted
        assertEquals(62.5f * 5 + 80f * 3, w.totalVolumeKg, 0.001f)      // hand-computed
        assertEquals(90, w.totalMinutes)
        assertEquals(1, w.restDays)
        assertEquals(1, w.missedDays)
    }

    @Test fun `biggest PR needs a pre-month best - first-ever lifts are baselines`() {
        val may = session(noon(2026, 5, 20))
        val s1 = session(noon(2026, 6, 3))
        val sets = listOf(
            set(may.id, "Bench Press", 60f, 5),
            set(s1.id, "Bench Press", 65f, 5),     // beats May's 60 → PR, +5
            set(s1.id, "Deadlift", 140f, 3)        // FIRST EVER — baseline, never a PR
        )
        val w = MonthlyWrapped.build(june, listOf(may, s1), sets, emptyList(), emptyList())!!
        assertEquals("Bench Press", w.biggestPr!!.exerciseName)
        assertEquals(65f, w.biggestPr!!.newKg)
        assertEquals(60f, w.biggestPr!!.previousKg)
    }

    @Test fun `most improved needs two in-month sessions and a positive e1RM delta`() {
        val s1 = session(noon(2026, 6, 3))
        val s2 = session(noon(2026, 6, 17))
        val sets = listOf(
            set(s1.id, "Squat", 100f, 5),
            set(s2.id, "Squat", 100f, 8),      // same load, more reps → higher e1RM
            set(s1.id, "Bench Press", 60f, 5)  // single in-month session → not eligible
        )
        val w = MonthlyWrapped.build(june, listOf(s1, s2), sets, emptyList(), emptyList())!!
        assertEquals("Squat", w.mostImproved!!.exerciseName)
        assertEquals(Epley.estimate(100f, 5), w.mostImproved!!.fromE1rm, 0.001)
        assertEquals(Epley.estimate(100f, 8), w.mostImproved!!.toE1rm, 0.001)
    }

    @Test fun `favorite is the most-visited exercise with a deterministic tie-break`() {
        val s1 = session(noon(2026, 6, 3)); val s2 = session(noon(2026, 6, 5))
        val sets = listOf(
            set(s1.id, "Squat", 100f, 5), set(s2.id, "Squat", 100f, 5),
            set(s1.id, "Bench Press", 60f, 5)
        )
        val w = MonthlyWrapped.build(june, listOf(s1, s2), sets, emptyList(), emptyList())!!
        assertEquals("Squat", w.favorite!!.exerciseName)
        assertEquals(2, w.favorite!!.sessions)
    }

    @Test fun `achievements and body weight land in the right month`() {
        val s1 = session(noon(2026, 6, 3))
        val inJune = Achievement("a", "A", "", "x", isUnlocked = true,
            unlockedAtMs = noon(2026, 6, 10))
        val inMay = Achievement("b", "B", "", "x", isUnlocked = true,
            unlockedAtMs = noon(2026, 5, 10))
        val w = MonthlyWrapped.build(
            june, listOf(s1), listOf(set(s1.id, "Squat", 100f, 5)),
            listOf(inJune, inMay),
            listOf(
                RelativeStrength.WeighIn(noon(2026, 6, 1), 80f),
                RelativeStrength.WeighIn(noon(2026, 6, 28), 78.5f)
            )
        )!!
        assertEquals(listOf("a"), w.achievementsUnlocked.map { it.id })
        assertEquals(80f, w.bodyWeight!!.startKg)
        assertEquals(78.5f, w.bodyWeight!!.endKg)
    }

    @Test fun `a month with no real training builds to null - never a page of zeros`() {
        val rest = session(noon(2026, 6, 5), kind = WorkoutSession.KIND_REST)
        assertNull(MonthlyWrapped.build(june, listOf(rest), emptyList(), emptyList(), emptyList()))
        assertNull(MonthlyWrapped.build(june, emptyList(), emptyList(), emptyList(), emptyList()))
    }

    @Test fun `available months - only ended months with data, newest first`() {
        val april = session(noon(2026, 4, 10))
        val june6 = session(noon(2026, 6, 10))
        val julyCurrent = session(noon(2026, 7, 1))
        val nowMs = noon(2026, 7, 2)
        val months = MonthlyWrapped.availableMonths(
            listOf(april, june6, julyCurrent), nowMs
        )
        assertEquals(listOf(MonthlyWrapped.MonthKey(2026, 6), MonthlyWrapped.MonthKey(2026, 4)), months)
    }

    @Test fun `ready month is the previous logical month`() {
        assertEquals(june, MonthlyWrapped.readyMonthKey(noon(2026, 7, 2)))
        assertEquals(MonthlyWrapped.MonthKey(2025, 12),
            MonthlyWrapped.readyMonthKey(noon(2026, 1, 15)))
    }

    @Test fun `viewing is pure - inputs are never mutated`() {
        val s1 = session(noon(2026, 6, 3))
        val sessions = listOf(s1)
        val sets = listOf(set(s1.id, "Squat", 100f, 5))
        val sessionsCopy = sessions.map { it.copy() }
        val setsCopy = sets.map { it.copy() }
        MonthlyWrapped.build(june, sessions, sets, emptyList(), emptyList())
        assertEquals(sessionsCopy, sessions)
        assertEquals(setsCopy, sets)
        assertTrue(true)
    }
}
