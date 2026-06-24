package com.migul.treningsprogram

import com.migul.treningsprogram.data.db.dao.MuscleVolume
import com.migul.treningsprogram.data.db.dao.WeekVolume
import com.migul.treningsprogram.domain.RecapGraphs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UX1: guards the pure data-shaping behind the new Recap overview graphs (weekly volume,
 * training frequency, per-muscle distribution). Pure list/bucket math only — rendering is not
 * tested here. Working-sets-only invariant is upheld by the upstream DAO queries; these functions
 * never re-introduce warm-ups, so the tests assert the merge/bucket/sort behaviour.
 */
class UX1RecapGraphsTest {

    private val WEEK = RecapGraphs.WEEK_MS
    private val DAY = 86_400_000L

    // ── weeklyVolumePoints: merge per-exercise weekly volume into totals ──────────────────

    @Test fun weeklyVolume_emptyInput_isEmpty() {
        assertTrue(RecapGraphs.weeklyVolumePoints(emptyList()).isEmpty())
        assertTrue(RecapGraphs.weeklyVolumePoints(listOf(emptyList(), emptyList())).isEmpty())
    }

    @Test fun weeklyVolume_sumsAcrossExercisesPerWeek() {
        val w0 = 0L
        val w1 = WEEK
        val squat = listOf(WeekVolume(w0, 3), WeekVolume(w1, 4))
        val bench = listOf(WeekVolume(w0, 5), WeekVolume(w1, 2))
        val pts = RecapGraphs.weeklyVolumePoints(listOf(squat, bench))
        assertEquals(2, pts.size)
        assertEquals(w0, pts[0].weekStartMs)
        assertEquals(8f, pts[0].value, 0f)   // 3 + 5
        assertEquals(w1, pts[1].weekStartMs)
        assertEquals(6f, pts[1].value, 0f)   // 4 + 2
    }

    @Test fun weeklyVolume_outputIsChronological() {
        // Feed weeks out of order; result must be sorted ascending by weekStart.
        val ex = listOf(WeekVolume(2 * WEEK, 1), WeekVolume(0L, 1), WeekVolume(WEEK, 1))
        val pts = RecapGraphs.weeklyVolumePoints(listOf(ex))
        assertEquals(listOf(0L, WEEK, 2 * WEEK), pts.map { it.weekStartMs })
    }

    @Test fun weeklyVolume_disjointWeeksAcrossExercisesAllAppear() {
        val a = listOf(WeekVolume(0L, 2))
        val b = listOf(WeekVolume(WEEK, 3))
        val pts = RecapGraphs.weeklyVolumePoints(listOf(a, b))
        assertEquals(2, pts.size)
        assertEquals(2f, pts[0].value, 0f)
        assertEquals(3f, pts[1].value, 0f)
    }

    // ── weeklyFrequencyPoints: distinct training days bucketed into weeks ─────────────────

    @Test fun frequency_emptyInput_isEmpty() {
        assertTrue(RecapGraphs.weeklyFrequencyPoints(emptyList()).isEmpty())
    }

    @Test fun frequency_countsDistinctDaysPerWeek() {
        // Day epochs are days-since-unix-epoch. Week 0 = days 0..6, week 1 = days 7..13.
        val days = listOf(0L, 2L, 5L, 7L, 9L)  // 3 in week0, 2 in week1
        val pts = RecapGraphs.weeklyFrequencyPoints(days)
        assertEquals(2, pts.size)
        assertEquals(0L, pts[0].weekStartMs)
        assertEquals(3f, pts[0].value, 0f)
        assertEquals(WEEK, pts[1].weekStartMs)
        assertEquals(2f, pts[1].value, 0f)
    }

    @Test fun frequency_dedupesRepeatedDayEpochs() {
        val days = listOf(3L, 3L, 3L)
        val pts = RecapGraphs.weeklyFrequencyPoints(days)
        assertEquals(1, pts.size)
        assertEquals(1f, pts[0].value, 0f)
    }

    @Test fun frequency_weekStartMatchesVolumeGrid() {
        // A day in week 1 (e.g. day 8) must bucket to the same weekStart the volume graph uses.
        val pts = RecapGraphs.weeklyFrequencyPoints(listOf(8L))
        assertEquals(WEEK, pts[0].weekStartMs)
        // Sanity: 8 days in ms, floored to the week grid, equals one WEEK.
        assertEquals((8L * DAY) / WEEK * WEEK, pts[0].weekStartMs)
    }

    @Test fun frequency_outputIsChronological() {
        val days = listOf(20L, 1L, 8L)
        val pts = RecapGraphs.weeklyFrequencyPoints(days)
        val weeks = pts.map { it.weekStartMs }
        assertEquals(weeks.sorted(), weeks)
    }

    // ── muscleRows: pass-through normalisation, desc by sets ──────────────────────────────

    @Test fun muscle_emptyInput_isEmpty() {
        assertTrue(RecapGraphs.muscleRows(emptyList()).isEmpty())
    }

    @Test fun muscle_sortedDescendingBySets() {
        val input = listOf(
            MuscleVolume("Back", 5),
            MuscleVolume("Chest", 12),
            MuscleVolume("Legs", 8)
        )
        val rows = RecapGraphs.muscleRows(input)
        assertEquals(listOf("Chest", "Legs", "Back"), rows.map { it.muscleGroup })
        assertEquals(listOf(12, 8, 5), rows.map { it.sets })
    }

    @Test fun muscle_dropsBlankAndZero() {
        val input = listOf(
            MuscleVolume("", 9),
            MuscleVolume("Arms", 0),
            MuscleVolume("Chest", 4)
        )
        val rows = RecapGraphs.muscleRows(input)
        assertEquals(1, rows.size)
        assertEquals("Chest", rows[0].muscleGroup)
    }
}
