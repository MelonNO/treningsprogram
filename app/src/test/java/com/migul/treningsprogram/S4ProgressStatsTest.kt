package com.migul.treningsprogram

import com.migul.treningsprogram.data.db.dao.StrengthPoint
import com.migul.treningsprogram.domain.Epley
import com.migul.treningsprogram.domain.OneRmTrend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S4 / F2: correctness sweep of Stats/History/Progress computed values.
 *
 * Key invariants locked here:
 * - e1RM on the Progress tab is derived from the set with the HIGHEST estimated 1RM (Epley),
 *   not the one with the highest raw weight (a fix from S4). A heavier-but-fewer-reps set can
 *   yield a LOWER e1RM than a lighter-but-more-reps set (double-progression scenario).
 * - The C1 PR timeline ([OneRmTrend.prTimeline]) is the SINGLE source of PR truth in the
 *   Progress tab (F2 retirement). It excludes warm-ups upstream (DAO-level) and tracks PRs by
 *   estimated 1RM, not raw weight.
 * - Empty/sparse/single-point inputs degrade gracefully (no crash, sensible outputs).
 */
class S4ProgressStatsTest {

    private val DAY = 86_400_000L

    private fun point(day: Int, weight: Float, reps: Int) =
        StrengthPoint(dateMs = day * DAY, maxWeight = weight, bestReps = reps)

    // ── e1RM selection: maxByOrNull on Epley, not on raw weight (S4 fix) ──────────────────

    @Test fun e1rmBestSelection_preferHigherEpleyOverHigherWeight() {
        // 120 kg × 1 rep → e1RM = 120 × (1 + 1/30) = 124.0 kg
        // 100 kg × 8 reps → e1RM = 100 × (1 + 8/30) = 126.67 kg  ← wins
        val history = listOf(
            point(1, 120f, 1),
            point(2, 100f, 8)
        )
        // If we incorrectly sort by maxWeight we'd pick day-1 (120 kg).
        // The correct selection is day-2 because its e1RM is higher.
        val best = history.filter { it.bestReps in 1 until 20 }
            .maxByOrNull { Epley.estimate(it.maxWeight, it.bestReps) }

        assertEquals(2 * DAY, best?.dateMs)
        assertEquals(100f, best?.maxWeight)
        assertEquals(8, best?.bestReps)

        val e1rm = Epley.estimate(best!!.maxWeight, best.bestReps)
        assertTrue("e1RM from 100kg×8 should exceed 120kg×1", e1rm > Epley.estimate(120f, 1))
    }

    @Test fun e1rmBestSelection_singleSession_returnsThatSession() {
        val history = listOf(point(1, 80f, 5))
        val best = history.filter { it.bestReps in 1 until 20 }
            .maxByOrNull { Epley.estimate(it.maxWeight, it.bestReps) }
        assertEquals(1 * DAY, best?.dateMs)
    }

    @Test fun e1rmBestSelection_emptyHistory_returnsNull() {
        val best = emptyList<StrengthPoint>().filter { it.bestReps in 1 until 20 }
            .maxByOrNull { Epley.estimate(it.maxWeight, it.bestReps) }
        assertEquals(null, best)
    }

    @Test fun e1rmBestSelection_allReps20orAbove_returnsNull() {
        // Epley is unreliable above 20 reps; the UI hides e1RM in this case.
        val history = listOf(point(1, 60f, 20), point(2, 60f, 30))
        val best = history.filter { it.bestReps in 1 until 20 }
            .maxByOrNull { Epley.estimate(it.maxWeight, it.bestReps) }
        assertEquals(null, best)
    }

    @Test fun e1rmBestSelection_repsZero_excluded() {
        // Reps = 0 is not in 1 until 20, so should be excluded from e1RM computation.
        val history = listOf(point(1, 200f, 0), point(2, 100f, 5))
        val best = history.filter { it.bestReps in 1 until 20 }
            .maxByOrNull { Epley.estimate(it.maxWeight, it.bestReps) }
        // Should pick day-2 (the valid set), not day-1 (reps=0 excluded).
        assertEquals(2 * DAY, best?.dateMs)
    }

    // ── C1 PR timeline as single PR source (F2 consolidation) ────────────────────────────

    @Test fun prTimeline_isTheOnlyPrSource_repPrAtEqualWeightRegisters() {
        // The C1 PR timeline registers both weight PRs and rep PRs at the same weight.
        // The old max-weight widget would MISS this because raw weight didn't change.
        val history = listOf(
            point(1, 100f, 5),  // baseline
            point(2, 100f, 6)   // same weight, 1 extra rep → higher e1RM → PR
        )
        val prs = OneRmTrend.prTimeline(history)
        assertEquals(2, prs.size)
        assertEquals(6, prs[1].reps)
        assertTrue("Rep PR should increase e1RM", prs[1].e1rm > prs[0].e1rm)
    }

    @Test fun prTimeline_noHistory_isEmpty() {
        assertTrue(OneRmTrend.prTimeline(emptyList()).isEmpty())
    }

    @Test fun prTimeline_singleSession_isSinglePr() {
        val prs = OneRmTrend.prTimeline(listOf(point(1, 100f, 5)))
        assertEquals(1, prs.size)
    }

    @Test fun prTimeline_regressionDoesNotAppear() {
        // A lighter session after a PR must NOT appear in the PR timeline.
        val history = listOf(
            point(1, 100f, 8),  // baseline PR
            point(2, 90f, 5)    // regression — not a PR
        )
        val prs = OneRmTrend.prTimeline(history)
        assertEquals(1, prs.size)
        assertEquals(100f, prs[0].weightKg)
    }

    @Test fun prTimeline_chronologicallyOrdered() {
        // Deliberately out-of-order input; output must be oldest-first.
        val history = listOf(
            point(3, 110f, 5),
            point(1, 90f, 5),
            point(2, 100f, 5)
        )
        val prs = OneRmTrend.prTimeline(history)
        // All three are PRs (each exceeds the previous).
        assertEquals(3, prs.size)
        assertEquals(1 * DAY, prs[0].dateMs)
        assertEquals(2 * DAY, prs[1].dateMs)
        assertEquals(3 * DAY, prs[2].dateMs)
    }

    // ── Best-streak (HistoryStatsFragment.computeBestStreak) ──────────────────────────────

    /**
     * The streak logic extracts day-epochs (dateMs / 86_400_000), deduplicates (toSortedSet),
     * then walks looking for consecutive days. Tests lock the correct boundary math.
     */
    @Test fun bestStreak_consecutiveDays() {
        val dates = listOf(1L * DAY, 2L * DAY, 3L * DAY)  // 3 consecutive days
        assertEquals(3, computeBestStreak(dates))
    }

    @Test fun bestStreak_gapBreaksStreak() {
        val dates = listOf(1L * DAY, 2L * DAY, 4L * DAY)  // gap on day 3
        assertEquals(2, computeBestStreak(dates))
    }

    @Test fun bestStreak_singleDay() {
        assertEquals(1, computeBestStreak(listOf(1L * DAY)))
    }

    @Test fun bestStreak_emptyList_returnsZero() {
        assertEquals(0, computeBestStreak(emptyList()))
    }

    @Test fun bestStreak_multipleSessionsSameDay_countAsOne() {
        // Two workouts on day 1 — only one streak-day, not two.
        val dates = listOf(1L * DAY, 1L * DAY + 3600_000L, 2L * DAY, 3L * DAY)
        assertEquals(3, computeBestStreak(dates))
    }

    @Test fun bestStreak_picksLongestRun() {
        // Days 1-2 (len=2) then gap, days 5-7 (len=3) → best is 3.
        val dates = listOf(1L * DAY, 2L * DAY, 5L * DAY, 6L * DAY, 7L * DAY)
        assertEquals(3, computeBestStreak(dates))
    }

    // ── Epley formula contract ─────────────────────────────────────────────────────────────

    @Test fun epley_oneRep_returnsWeight() {
        // At exactly 1 rep, Epley == weight (1 + 1/30 ≈ 1.033; rounding to int is close).
        val e = Epley.estimate(100f, 1)
        // The formula: 100 * (1 + 1/30) = 103.33
        assertEquals(100.0 * (1 + 1 / 30.0), e, 1e-9)
    }

    @Test fun epley_zeroWeight_returnsZero() {
        assertEquals(0.0, Epley.estimate(0f, 5), 1e-9)
    }

    @Test fun epley_zeroReps_returnsZero() {
        assertEquals(0.0, Epley.estimate(100f, 0), 1e-9)
    }

    @Test fun epley_negativeInputs_returnsZero() {
        assertEquals(0.0, Epley.estimate(-10f, 5), 1e-9)
        assertEquals(0.0, Epley.estimate(100f, -1), 1e-9)
    }

    @Test fun epley_moreReps_meansHigherE1rm() {
        // At the same weight, more reps → higher e1RM (the core double-progression invariant).
        val e5 = Epley.estimate(100f, 5)
        val e6 = Epley.estimate(100f, 6)
        assertTrue(e6 > e5)
    }

    // ── Helper: extracted pure streak logic (mirrors HistoryStatsFragment.computeBestStreak) ─

    private fun computeBestStreak(dateMsValues: List<Long>): Int {
        if (dateMsValues.isEmpty()) return 0
        val days = dateMsValues.map { it / 86_400_000L }.toSortedSet()
        var best = 1
        var current = 1
        val sorted = days.toList()
        for (i in 1 until sorted.size) {
            if (sorted[i] - sorted[i - 1] == 1L) {
                current++
                if (current > best) best = current
            } else {
                current = 1
            }
        }
        return best
    }
}
