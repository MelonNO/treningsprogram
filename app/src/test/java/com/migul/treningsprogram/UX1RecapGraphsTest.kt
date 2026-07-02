package com.migul.treningsprogram

import com.migul.treningsprogram.data.db.dao.MuscleVolume
import com.migul.treningsprogram.domain.RecapGraphs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UX1: guards the pure data-shaping behind the Recap overview graphs (weekly volume,
 * training frequency, per-muscle distribution). Pure list/bucket math only — rendering is not
 * tested here.
 *
 * Week convention: MONDAY-based weeks over epoch-days (epoch day 0 = Thursday 1970-01-01, so
 * day 4 = Monday 1970-01-05). This replaced the old Thursday-floored `epochMs / WEEK_MS` grid.
 */
class UX1RecapGraphsTest {

    private val DAY = 86_400_000L
    private val MON0 = 4L        // Monday 1970-01-05
    private val MON1 = MON0 + 7  // the following Monday

    // ── mondayOfEpochDay: the week anchor ─────────────────────────────────────────────────

    @Test fun monday_isItsOwnWeekStart() {
        assertEquals(MON0, RecapGraphs.mondayOfEpochDay(MON0))
    }

    @Test fun everyDayOfTheWeekMapsToItsMonday() {
        for (offset in 0L..6L) {
            assertEquals("Mon+$offset", MON0, RecapGraphs.mondayOfEpochDay(MON0 + offset))
        }
        assertEquals(MON1, RecapGraphs.mondayOfEpochDay(MON0 + 7))
    }

    @Test fun sundayAndMondayFallInDifferentWeeks() {
        // Day 3 is Sunday 1970-01-04; day 4 is Monday — the old Thursday grid kept them together.
        val sundayWeek = RecapGraphs.mondayOfEpochDay(3L)
        val mondayWeek = RecapGraphs.mondayOfEpochDay(4L)
        assertTrue(sundayWeek < mondayWeek)
        assertEquals(4L, mondayWeek)
    }

    // ── weeklyVolumePoints: one entry per working set, counted per week ───────────────────

    @Test fun weeklyVolume_emptyInput_isEmpty() {
        assertTrue(RecapGraphs.weeklyVolumePoints(emptyList()).isEmpty())
    }

    @Test fun weeklyVolume_countsEverySetPerWeek() {
        // 3 sets across two days of week 0, 1 set in week 1.
        val pts = RecapGraphs.weeklyVolumePoints(listOf(MON0, MON0, MON0 + 2, MON1 + 1))
        assertEquals(2, pts.size)
        assertEquals(MON0 * DAY, pts[0].weekStartMs)
        assertEquals(3f, pts[0].value, 0f)
        assertEquals(MON1 * DAY, pts[1].weekStartMs)
        assertEquals(1f, pts[1].value, 0f)
    }

    @Test fun weeklyVolume_outputIsChronological() {
        val pts = RecapGraphs.weeklyVolumePoints(listOf(MON1 + 14, MON0, MON1))
        assertEquals(pts.map { it.weekStartMs }.sorted(), pts.map { it.weekStartMs })
        assertEquals(3, pts.size)
    }

    // ── weeklyFrequencyPoints: distinct training days bucketed into weeks ─────────────────

    @Test fun frequency_emptyInput_isEmpty() {
        assertTrue(RecapGraphs.weeklyFrequencyPoints(emptyList()).isEmpty())
    }

    @Test fun frequency_countsDistinctDaysPerWeek() {
        // 3 days in week 0 (Mon, Wed, Sat), 2 in week 1.
        val days = listOf(MON0, MON0 + 2, MON0 + 5, MON1, MON1 + 2)
        val pts = RecapGraphs.weeklyFrequencyPoints(days)
        assertEquals(2, pts.size)
        assertEquals(MON0 * DAY, pts[0].weekStartMs)
        assertEquals(3f, pts[0].value, 0f)
        assertEquals(MON1 * DAY, pts[1].weekStartMs)
        assertEquals(2f, pts[1].value, 0f)
    }

    @Test fun frequency_dedupesRepeatedDayEpochs() {
        val pts = RecapGraphs.weeklyFrequencyPoints(listOf(MON0 + 1, MON0 + 1, MON0 + 1))
        assertEquals(1, pts.size)
        assertEquals(1f, pts[0].value, 0f)
    }

    @Test fun frequency_weekStartMatchesVolumeGrid() {
        val day = MON1 + 3
        val f = RecapGraphs.weeklyFrequencyPoints(listOf(day))
        val v = RecapGraphs.weeklyVolumePoints(listOf(day))
        assertEquals(v[0].weekStartMs, f[0].weekStartMs)
        assertEquals(MON1 * DAY, f[0].weekStartMs)
    }

    @Test fun frequency_outputIsChronological() {
        val days = listOf(MON1 + 9, MON0 + 1, MON1)
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
