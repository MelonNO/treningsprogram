package com.migul.treningsprogram

import com.migul.treningsprogram.domain.ChartTouch
import com.migul.treningsprogram.domain.DateRangeFilter
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

/**
 * QoL item 09 — guards the pure math behind the chart touch-to-read gesture:
 *   • [ChartTouch.nearestIndex] — px→date-domain mapping + nearest-point snapping used by
 *     StrengthChartView / BodyWeightChartView when placing the scrub marker;
 *   • [DateRangeFilter.filter] — the generic range windowing the body-weight chart now shares
 *     with the strength/reps charts (inclusive bounds, null = all-time).
 */
class ChartTouchTest {

    private val DAY = 86_400_000L

    // Plot geometry mirroring the views: plot spans px 100..700.
    private val PL = 100f
    private val PW = 600f

    // Days 0 / 2 / 10 → plotted at px 100 / 220 / 700.
    private val dates = listOf(0L, 2 * DAY, 10 * DAY)

    // ── nearestIndex ─────────────────────────────────────────────────────────

    @Test fun empty_returnsMinusOne() {
        assertEquals(-1, ChartTouch.nearestIndex(emptyList(), PL, PW, 400f))
    }

    @Test fun singlePoint_alwaysSnapsToIt() {
        val one = listOf(5 * DAY)
        assertEquals(0, ChartTouch.nearestIndex(one, PL, PW, -999f))
        assertEquals(0, ChartTouch.nearestIndex(one, PL, PW, 100f))
        assertEquals(0, ChartTouch.nearestIndex(one, PL, PW, 9999f))
    }

    @Test fun exactHit_returnsThatPoint() {
        assertEquals(0, ChartTouch.nearestIndex(dates, PL, PW, 100f))
        assertEquals(1, ChartTouch.nearestIndex(dates, PL, PW, 220f))
        assertEquals(2, ChartTouch.nearestIndex(dates, PL, PW, 700f))
    }

    @Test fun betweenPoints_snapsToNearer() {
        // px 180 → 80 from point0 (100), 40 from point1 (220).
        assertEquals(1, ChartTouch.nearestIndex(dates, PL, PW, 180f))
        // px 140 → 40 from point0, 80 from point1.
        assertEquals(0, ChartTouch.nearestIndex(dates, PL, PW, 140f))
        // px 500 → 280 from point1, 200 from point2.
        assertEquals(2, ChartTouch.nearestIndex(dates, PL, PW, 500f))
    }

    @Test fun outsidePlot_clampsToEdgePoints() {
        assertEquals(0, ChartTouch.nearestIndex(dates, PL, PW, -50f))   // left of the plot
        assertEquals(0, ChartTouch.nearestIndex(dates, PL, PW, 0f))     // in the y-axis gutter
        assertEquals(2, ChartTouch.nearestIndex(dates, PL, PW, 5000f))  // right of the plot
    }

    @Test fun equidistantTie_resolvesToEarlierIndex() {
        // Two points at px 100 and 700; px 400 is exactly between them.
        val two = listOf(0L, 10 * DAY)
        assertEquals(0, ChartTouch.nearestIndex(two, PL, PW, 400f))
    }

    @Test fun allSameDate_degenerateSpan_noCrash_firstPoint() {
        // All points collapse onto plotLeft (the views' rd=1 fallback); nearest is index 0.
        val same = listOf(3 * DAY, 3 * DAY, 3 * DAY)
        assertEquals(0, ChartTouch.nearestIndex(same, PL, PW, 700f))
    }

    // ── DateRangeFilter.filter (BW chart range windowing) ────────────────────

    private data class M(val dateMs: Long)

    private val utc = ZoneId.of("UTC")

    @Test fun nullRange_passesEverything() {
        val items = listOf(M(0L), M(50 * DAY), M(300 * DAY))
        assertEquals(items, DateRangeFilter.filter(items, null, 0, utc) { it.dateMs })
    }

    @Test fun rangeBounds_areInclusiveBothEnds() {
        // Range = epoch days 10..12 (cutoff 0, UTC → logical day == calendar day).
        val range = DateRangeFilter.Range(10, 12)
        val items = listOf(
            M(9 * DAY + 12 * 3_600_000L),   // day 9  → out (before start)
            M(10 * DAY),                     // day 10 midnight → IN (start inclusive)
            M(11 * DAY + 6 * 3_600_000L),   // day 11 → in
            M(12 * DAY + 23 * 3_600_000L),  // day 12 23:00 → IN (end inclusive)
            M(13 * DAY)                      // day 13 → out (after end)
        )
        val kept = DateRangeFilter.filter(items, range, 0, utc) { it.dateMs }
        assertEquals(listOf(items[1], items[2], items[3]), kept)
    }

    @Test fun emptyInput_staysEmpty() {
        val range = DateRangeFilter.Range(10, 12)
        assertEquals(
            emptyList<M>(),
            DateRangeFilter.filter(emptyList<M>(), range, 0, utc) { it.dateMs }
        )
    }
}
