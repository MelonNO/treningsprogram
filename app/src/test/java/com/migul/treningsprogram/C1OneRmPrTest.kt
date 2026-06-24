package com.migul.treningsprogram

import com.migul.treningsprogram.data.db.dao.StrengthPoint
import com.migul.treningsprogram.domain.Epley
import com.migul.treningsprogram.domain.OneRmTrend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Feature C1: per-exercise estimated-1RM trend + PR timeline.
 *
 * Locks the PURE derivation in [OneRmTrend] so the Recap & Trends screen behaves correctly
 * without needing Android/Room. PRs are tracked by estimated 1RM (Epley): a weight PR OR a
 * rep-PR at equal weight both register; a regression does not.
 */
class C1OneRmPrTest {

    private val DAY = 86_400_000L

    private fun point(day: Int, weight: Float, reps: Int) =
        StrengthPoint(dateMs = day * DAY, maxWeight = weight, bestReps = reps)

    // ---- Trend points ----

    @Test fun trendPoints_useEpleyAndAreChronological() {
        // Deliberately out of order to prove sorting.
        val history = listOf(
            point(3, 100f, 3),
            point(1, 100f, 1),
            point(2, 100f, 5)
        )
        val trend = OneRmTrend.trendPoints(history)

        assertEquals(3, trend.size)
        // Chronological by date.
        assertEquals(1 * DAY, trend[0].dateMs)
        assertEquals(2 * DAY, trend[1].dateMs)
        assertEquals(3 * DAY, trend[2].dateMs)
        // Each value is exactly the shared Epley estimate.
        assertEquals(Epley.estimate(100f, 1), trend[0].e1rm, 1e-9)
        assertEquals(Epley.estimate(100f, 5), trend[1].e1rm, 1e-9)
        assertEquals(Epley.estimate(100f, 3), trend[2].e1rm, 1e-9)
    }

    @Test fun trendPoints_dropNonMeaningfulSessions() {
        // weight 0 and reps 0 → Epley returns 0.0 → dropped, so the chart never plots a zero.
        val history = listOf(
            point(1, 0f, 5),
            point(2, 80f, 0),
            point(3, 80f, 5)
        )
        val trend = OneRmTrend.trendPoints(history)
        assertEquals(1, trend.size)
        assertEquals(3 * DAY, trend[0].dateMs)
    }

    // ---- PR timeline ----

    @Test fun prTimeline_weightPrRegisters() {
        val history = listOf(
            point(1, 100f, 5),
            point(2, 110f, 5) // heavier at same reps → higher e1RM → PR
        )
        val prs = OneRmTrend.prTimeline(history)
        assertEquals(2, prs.size)
        assertEquals(100f, prs[0].weightKg)
        assertEquals(110f, prs[1].weightKg)
        assertEquals(5, prs[1].reps)
    }

    @Test fun prTimeline_repPrAtEqualWeightRegisters() {
        val history = listOf(
            point(1, 100f, 5),
            point(2, 100f, 6) // same weight, more reps → higher e1RM → PR
        )
        val prs = OneRmTrend.prTimeline(history)
        assertEquals(2, prs.size)
        assertEquals(100f, prs[1].weightKg)
        assertEquals(6, prs[1].reps)
        assertTrue(prs[1].e1rm > prs[0].e1rm)
    }

    @Test fun prTimeline_regressionDoesNotRegister() {
        val history = listOf(
            point(1, 100f, 8),   // baseline
            point(2, 90f, 6),    // lighter & fewer reps → lower e1RM → NOT a PR
            point(3, 100f, 7)    // still below the baseline e1RM → NOT a PR
        )
        val prs = OneRmTrend.prTimeline(history)
        assertEquals(1, prs.size)
        assertEquals(8, prs[0].reps)
        assertEquals(100f, prs[0].weightKg)
    }

    @Test fun prTimeline_onlyStrictlyHigherE1rmCounts() {
        // A repeated identical performance is not a new PR (not strictly higher).
        val history = listOf(
            point(1, 100f, 5),
            point(2, 100f, 5)
        )
        val prs = OneRmTrend.prTimeline(history)
        assertEquals(1, prs.size)
    }

    @Test fun prTimeline_emptyHistoryYieldsEmpty() {
        assertTrue(OneRmTrend.prTimeline(emptyList()).isEmpty())
        assertTrue(OneRmTrend.trendPoints(emptyList()).isEmpty())
    }

    @Test fun prTimeline_singleSessionIsItsOwnFirstPr() {
        val prs = OneRmTrend.prTimeline(listOf(point(1, 100f, 5)))
        assertEquals(1, prs.size)
        assertEquals(Epley.estimate(100f, 5), prs[0].e1rm, 1e-9)
    }
}
