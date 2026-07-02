package com.migul.treningsprogram

import com.migul.treningsprogram.domain.VolumeHeatmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * F5 — grid math for the weekly volume heatmap: Monday-based week bucketing,
 * volume-ordered rows, muscle cap, and the empty case.
 */
class VolumeHeatmapTest {

    /** Local-time epoch ms for a date (noon, away from any day-boundary edges). */
    private fun ms(y: Int, mo: Int, d: Int): Long = Calendar.getInstance().run {
        clear()
        set(y, mo - 1, d, 12, 0, 0)
        timeInMillis
    }

    @Test fun `mondayOf lands on a Monday at local midnight`() {
        // 2026-07-01 is a Wednesday → its Monday is 2026-06-29
        val monday = VolumeHeatmap.mondayOf(ms(2026, 7, 1))
        Calendar.getInstance().run {
            timeInMillis = monday
            assertEquals(Calendar.MONDAY, get(Calendar.DAY_OF_WEEK))
            assertEquals(29, get(Calendar.DAY_OF_MONTH))
            assertEquals(0, get(Calendar.HOUR_OF_DAY))
        }
    }

    @Test fun `sets bucket into the right week columns`() {
        val now = ms(2026, 7, 1)                       // week of Mon 29 Jun
        val grid = VolumeHeatmap.build(
            setDays = listOf(
                "Chest" to ms(2026, 7, 1),             // current week (last column)
                "Chest" to ms(2026, 7, 1),
                "Chest" to ms(2026, 6, 24),            // previous week
            ),
            weeks = 4, nowMs = now,
        )
        assertEquals(listOf("Chest"), grid.muscles)
        assertEquals(4, grid.weekStarts.size)
        assertEquals(listOf(0, 0, 1, 2), grid.sets[0])
        assertEquals(2, grid.maxSets)
    }

    @Test fun `rows are ordered by total volume and capped`() {
        val now = ms(2026, 7, 1)
        val setDays = buildList {
            repeat(5) { add("Legs" to now) }
            repeat(3) { add("Chest" to now) }
            repeat(9) { add("Back" to now) }
        }
        val grid = VolumeHeatmap.build(setDays, weeks = 2, nowMs = now, maxMuscles = 2)
        assertEquals(listOf("Back", "Legs"), grid.muscles)  // Chest dropped by the cap
        assertEquals(9, grid.maxSets)
    }

    @Test fun `sets older than the window are ignored`() {
        val now = ms(2026, 7, 1)
        val grid = VolumeHeatmap.build(
            setDays = listOf("Chest" to ms(2026, 1, 1)),
            weeks = 4, nowMs = now,
        )
        assertTrue(grid.muscles.isEmpty())
        assertEquals(0, grid.maxSets)
    }
}
