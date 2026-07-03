package com.migul.treningsprogram

import com.migul.treningsprogram.data.db.dao.StrengthPoint
import com.migul.treningsprogram.domain.Epley
import com.migul.treningsprogram.domain.RelativeStrength
import com.migul.treningsprogram.domain.RelativeStrength.WeighIn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * N3 — relative strength (e1RM ÷ nearest weigh-in). Values are hand-computed against Epley;
 * periods with no weigh-ins yield NO points (never fabricated); warm-ups are excluded upstream
 * (getStrengthHistory) and bodyweight-only sessions are skipped (A-R2).
 */
class N3RelativeStrengthTest {

    private val day = 24L * 60 * 60 * 1000

    private fun sp(dateDays: Long, weight: Float, reps: Int) =
        StrengthPoint(dateMs = dateDays * day, maxWeight = weight, bestReps = reps)

    @Test fun `hand-computed ratio - e1RM over nearest weigh-in`() {
        // 100 kg × 5 → Epley e1RM = 100 × (1 + 5/30) = 116.667; BW 80 kg 3 days earlier.
        val points = RelativeStrength.series(
            history = listOf(sp(10, 100f, 5)),
            weighIns = listOf(WeighIn(7 * day, 80f))
        )
        assertEquals(1, points.size)
        val expected = (Epley.estimate(100f, 5) / 80.0).toFloat()
        assertEquals(expected, points[0].ratio, 0.0001f)
        assertEquals(1.4583f, points[0].ratio, 0.001f)
    }

    @Test fun `nearest weigh-in wins`() {
        val points = RelativeStrength.series(
            history = listOf(sp(10, 60f, 1)),   // e1RM = 60 × (1 + 1/30) = 62 (house Epley)
            weighIns = listOf(WeighIn(2 * day, 100f), WeighIn(9 * day, 75f))
        )
        // Divided by the NEARER weigh-in (75 kg at day 9), not the farther 100 kg one.
        assertEquals((Epley.estimate(60f, 1) / 75.0).toFloat(), points[0].ratio, 0.0001f)
    }

    @Test fun `no weigh-in within 14 days - no point, nothing fabricated`() {
        val points = RelativeStrength.series(
            history = listOf(sp(100, 100f, 5)),
            weighIns = listOf(WeighIn(10 * day, 80f))   // 90 days away
        )
        assertTrue(points.isEmpty())
    }

    @Test fun `no weigh-ins at all - empty series`() {
        assertTrue(RelativeStrength.series(listOf(sp(1, 100f, 5)), emptyList()).isEmpty())
    }

    @Test fun `bodyweight-only sessions are skipped (A-R2)`() {
        val points = RelativeStrength.series(
            history = listOf(sp(10, 0f, 12), sp(11, 60f, 5)),
            weighIns = listOf(WeighIn(10 * day, 80f))
        )
        assertEquals(1, points.size)
        assertEquals(11 * day, points[0].dateMs)
    }

    @Test fun `series is chronological even when history arrives unsorted`() {
        val points = RelativeStrength.series(
            history = listOf(sp(20, 100f, 3), sp(10, 90f, 3)),
            weighIns = listOf(WeighIn(15 * day, 80f))
        )
        assertEquals(listOf(10 * day, 20 * day), points.map { it.dateMs })
    }

    @Test fun `partial coverage - only the covered period gets points`() {
        val points = RelativeStrength.series(
            history = listOf(sp(10, 100f, 5), sp(200, 110f, 5)),
            weighIns = listOf(WeighIn(9 * day, 80f))
        )
        assertEquals(1, points.size)  // the 200-day session has no weigh-in within 14 days
        assertEquals(10 * day, points[0].dateMs)
    }

    @Test fun `current line formats two decimals`() {
        val line = RelativeStrength.currentLine(
            listOf(RelativeStrength.Point(1L, 1.125f))
        )
        assertEquals("Currently 1.13× body weight", line)
    }

    @Test fun `milestones in range - only those the data actually spans`() {
        val points = listOf(
            RelativeStrength.Point(1L, 0.8f),
            RelativeStrength.Point(2L, 1.3f)
        )
        assertEquals(listOf(1.0f, 1.25f), RelativeStrength.milestonesInRange(points))
        assertTrue(RelativeStrength.milestonesInRange(emptyList()).isEmpty())
    }
}
