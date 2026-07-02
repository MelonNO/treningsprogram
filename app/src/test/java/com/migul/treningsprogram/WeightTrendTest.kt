package com.migul.treningsprogram

import com.migul.treningsprogram.data.db.entity.BodyMeasurement
import com.migul.treningsprogram.domain.WeightTrend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** R3 — smoothed body-weight trend math (Home line, chart overlay, AI prompt line). */
class WeightTrendTest {

    private val day = 86_400_000L
    private fun m(dayIdx: Int, kg: Float) = BodyMeasurement(dateMs = dayIdx * day, weightKg = kg)

    // ── guards ────────────────────────────────────────────────────────────────────────────────

    @Test fun fewerThanTwoEntriesHasNoTrend() {
        assertNull(WeightTrend.compute(emptyList()))
        assertNull(WeightTrend.compute(listOf(m(0, 80f))))
        assertNull(WeightTrend.homeLine(listOf(m(0, 80f))))
    }

    @Test fun singleEntryStillHasASmoothedCurrent() {
        assertEquals(80f, WeightTrend.smoothedCurrent(listOf(m(0, 80f)))!!, 0.001f)
    }

    // ── direction + smoothing ─────────────────────────────────────────────────────────────────

    @Test fun steadyDeclineReadsDown() {
        // Weekly weigh-ins losing ~0.5 kg/week over 4 weeks.
        val data = listOf(m(0, 80f), m(7, 79.5f), m(14, 79f), m(21, 78.5f), m(28, 78f))
        val t = WeightTrend.compute(data)!!
        assertEquals(WeightTrend.Direction.DOWN, t.direction)
        assertTrue(t.ratePerWeekKg < -0.4f && t.ratePerWeekKg > -0.6f)
    }

    @Test fun steadyGainReadsUp() {
        val data = listOf(m(0, 70f), m(7, 70.4f), m(14, 70.8f), m(21, 71.2f))
        assertEquals(WeightTrend.Direction.UP, WeightTrend.compute(data)!!.direction)
    }

    @Test fun flatWithNoiseReadsSteady_singleSpikeNeverFlipsTheArrow() {
        // A-B1: daily noise around 78 kg with one salty-dinner spike — steady, not UP.
        val data = listOf(
            m(0, 78.1f), m(2, 77.9f), m(4, 78.2f), m(6, 77.8f),
            m(8, 78.0f), m(10, 78.6f), m(12, 78.0f), m(14, 78.05f)
        )
        assertEquals(WeightTrend.Direction.STEADY, WeightTrend.compute(data)!!.direction)
    }

    @Test fun smoothedCurrentAveragesTheLastWindow() {
        // Last 7 days: 78.0 and 78.4 -> 78.2 (older entries outside the window are excluded).
        val data = listOf(m(0, 90f), m(20, 78.0f), m(24, 78.4f))
        assertEquals(78.2f, WeightTrend.smoothedCurrent(data)!!, 0.01f)
    }

    // ── sparse data honesty ───────────────────────────────────────────────────────────────────

    @Test fun twoPointsMonthsApartReadAsSlowDriftNotACliff() {
        // 3 kg over ~12 weeks ≈ 0.25 kg/week — must NOT look dramatic.
        val t = WeightTrend.compute(listOf(m(0, 81f), m(84, 78f)))!!
        assertTrue("rate should be gentle, was ${t.ratePerWeekKg}", abs(t.ratePerWeekKg) < 0.3f)
        assertEquals(84, t.spanDays)
        assertEquals(12, t.spanWeeks)
    }

    @Test fun unsortedInputIsHandled() {
        val data = listOf(m(14, 79f), m(0, 80f), m(7, 79.5f))
        assertEquals(WeightTrend.Direction.DOWN, WeightTrend.compute(data)!!.direction)
    }

    // ── display strings ───────────────────────────────────────────────────────────────────────

    @Test fun homeLineFormatsWeightArrowAndSpan() {
        val data = listOf(m(0, 80f), m(7, 79.5f), m(14, 79f), m(21, 78.5f), m(28, 78f))
        val line = WeightTrend.homeLine(data)!!
        // smoothed current = mean of the last-7-day entries (78.5, 78) = 78.2/78.3
        assertTrue(line, line.startsWith("78."))
        assertTrue(line, " kg · " in line)
        assertTrue(line, "↓" in line)
        assertTrue(line, "wk" in line)
    }

    @Test fun homeLineSteadyState() {
        val line = WeightTrend.homeLine(listOf(m(0, 78f), m(7, 78.05f), m(14, 78f)))!!
        assertTrue(line, "steady" in line)
    }

    // ── AI prompt line (A-B2: context only; "" with no data keeps the prompt byte-identical) ──

    @Test fun promptLineEmptyWithoutData() {
        assertEquals("", WeightTrend.promptLine(emptyList()))
    }

    @Test fun promptLineSingleEntry() {
        val line = WeightTrend.promptLine(listOf(m(0, 80.5f)))
        assertTrue(line, line.startsWith("Body weight: 80.5 kg"))
        assertTrue(line, "single entry" in line)
    }

    @Test fun promptLineNamesDirectionAndSpan() {
        val data = listOf(m(0, 80f), m(7, 79.5f), m(14, 79f), m(21, 78.5f), m(28, 78f))
        val line = WeightTrend.promptLine(data)
        assertTrue(line, line.startsWith("Body weight: 78."))
        assertTrue(line, "trending down" in line)
        assertTrue(line, "week" in line)
    }

    @Test fun promptLineStableState() {
        val line = WeightTrend.promptLine(listOf(m(0, 78f), m(7, 78.05f), m(14, 78f)))
        assertTrue(line, "stable" in line)
    }

    // ── chart overlay ─────────────────────────────────────────────────────────────────────────

    @Test fun smoothedSeriesIsTrailingWindowMean() {
        val data = listOf(m(0, 80f), m(3, 79f), m(12, 78f))
        val series = WeightTrend.smoothedSeries(data)
        assertEquals(3, series.size)
        assertEquals(80f, series[0].second, 0.01f)     // only itself in the trailing window
        assertEquals(79.5f, series[1].second, 0.01f)   // mean(80, 79) — 3 days apart
        assertEquals(78f, series[2].second, 0.01f)     // day 3 is 9 days back — outside the 7-day window
    }
}
