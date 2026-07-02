package com.migul.treningsprogram.domain

import com.migul.treningsprogram.data.db.entity.BodyMeasurement
import java.util.Locale
import kotlin.math.abs

/**
 * R3 — pure body-weight trend math shared by the Home trend line, the Stats → Progress chart
 * overlay, and the AI generation prompt's body-weight context line.
 *
 * Everything is SMOOTHED (A-B1): the "current" weight is the mean of the entries in the last
 * [SMOOTH_WINDOW_DAYS], and the direction/rate come from a least-squares fit over the last
 * [TREND_WINDOW_DAYS] — so one salty-dinner morning never flips the arrow. For very sparse data
 * (fewer than two entries inside the trend window) the fit falls back to the last two entries and
 * the rate is normalized over their ACTUAL time span, so two points months apart read as a slow
 * drift, not a cliff. Fewer than two entries ⇒ no trend at all.
 *
 * Input order does not matter (callers hold both ASC and DESC lists); functions sort internally.
 * Pure JVM — no Android imports — so it is fully unit-testable (WeightTrendTest).
 */
object WeightTrend {

    /** Entries within this many days of the newest are averaged into the "current" weight. */
    const val SMOOTH_WINDOW_DAYS = 7

    /** The direction/rate fit prefers entries inside this window (falls back to last two). */
    const val TREND_WINDOW_DAYS = 28

    /** |rate| below this many kg/week reads as "steady" rather than a direction. */
    const val STEADY_RATE_KG_PER_WEEK = 0.1f

    private const val DAY_MS = 86_400_000L

    enum class Direction { DOWN, STEADY, UP }

    data class Trend(
        /** Smoothed current weight (mean of the last [SMOOTH_WINDOW_DAYS] of entries). */
        val currentKg: Float,
        /** Signed fitted change across [spanDays] (negative = losing weight). */
        val deltaKg: Float,
        /** Actual days between the first and last entry used by the fit (>= 1). */
        val spanDays: Int,
        /** [deltaKg] normalized to a 7-day rate. */
        val ratePerWeekKg: Float
    ) {
        val direction: Direction = when {
            ratePerWeekKg <= -STEADY_RATE_KG_PER_WEEK -> Direction.DOWN
            ratePerWeekKg >= STEADY_RATE_KG_PER_WEEK -> Direction.UP
            else -> Direction.STEADY
        }
        val spanWeeks: Int get() = ((spanDays + 6) / 7).coerceAtLeast(1)
    }

    /** Smoothed current weight, or null with no entries. Works from a single entry. */
    fun smoothedCurrent(measurements: List<BodyMeasurement>): Float? {
        val sorted = measurements.sortedBy { it.dateMs }
        val newest = sorted.lastOrNull() ?: return null
        val recent = sorted.filter { newest.dateMs - it.dateMs <= SMOOTH_WINDOW_DAYS * DAY_MS }
        return recent.map { it.weightKg.toDouble() }.average().toFloat()
    }

    /** The smoothed trend, or null when fewer than two entries exist. */
    fun compute(measurements: List<BodyMeasurement>): Trend? {
        val sorted = measurements.sortedBy { it.dateMs }
        if (sorted.size < 2) return null
        val newestMs = sorted.last().dateMs
        val window = sorted.filter { newestMs - it.dateMs <= TREND_WINDOW_DAYS * DAY_MS }
        val used = if (window.size >= 2) window else sorted.takeLast(2)

        // Least-squares slope in kg/day over every point in the window — inherently smoothed.
        val xs = used.map { (it.dateMs - used.first().dateMs).toDouble() / DAY_MS }
        val ys = used.map { it.weightKg.toDouble() }
        val meanX = xs.average()
        val meanY = ys.average()
        val denom = xs.sumOf { (it - meanX) * (it - meanX) }
        val slopePerDay =
            if (denom == 0.0) 0.0
            else xs.zip(ys).sumOf { (x, y) -> (x - meanX) * (y - meanY) } / denom

        val spanDays = (xs.last() - xs.first()).toInt().coerceAtLeast(1)
        return Trend(
            currentKg = smoothedCurrent(sorted) ?: return null,
            deltaKg = (slopePerDay * spanDays).toFloat(),
            spanDays = spanDays,
            ratePerWeekKg = (slopePerDay * 7.0).toFloat()
        )
    }

    /**
     * Home trend line: "78.4 kg · ↓ 0.6 kg / 4 wks" (or "78.4 kg · → steady").
     * Null when fewer than two entries exist — the caller hides the view (explicit AC).
     */
    fun homeLine(measurements: List<BodyMeasurement>): String? {
        val t = compute(measurements) ?: return null
        val cur = fmtKg(t.currentKg)
        return if (t.direction == Direction.STEADY) {
            "$cur kg · → steady"
        } else {
            val wk = if (t.spanWeeks == 1) "wk" else "wks"
            "$cur kg · ${arrow(t.direction)} ${fmtKg(abs(t.deltaKg))} kg / ${t.spanWeeks} $wk"
        }
    }

    /**
     * The AI prompt's body-weight context line (A-B2 — plain context, no rules). Returns "" when no
     * measurements exist, which keeps the no-data prompt BYTE-IDENTICAL to the pre-R3 prompt (same
     * empty-block pattern as [com.migul.treningsprogram.data.repository.MesocycleContext.promptBlock]).
     */
    fun promptLine(measurements: List<BodyMeasurement>): String {
        val cur = smoothedCurrent(measurements) ?: return ""
        val t = compute(measurements)
            ?: return "Body weight: ${fmtKg(cur)} kg (single entry)"
        val weeks = "${t.spanWeeks} ${if (t.spanWeeks == 1) "week" else "weeks"}"
        return when (t.direction) {
            Direction.STEADY -> "Body weight: ${fmtKg(t.currentKg)} kg — stable over the last $weeks"
            Direction.DOWN -> "Body weight: ${fmtKg(t.currentKg)} kg — trending down ~${fmtKg(abs(t.deltaKg))} kg over the last $weeks"
            Direction.UP -> "Body weight: ${fmtKg(t.currentKg)} kg — trending up ~${fmtKg(abs(t.deltaKg))} kg over the last $weeks"
        }
    }

    /** Trailing-[SMOOTH_WINDOW_DAYS] mean at each entry — the chart's smoothed overlay series. */
    fun smoothedSeries(measurements: List<BodyMeasurement>): List<Pair<Long, Float>> {
        val sorted = measurements.sortedBy { it.dateMs }
        return sorted.map { m ->
            val win = sorted.filter {
                it.dateMs <= m.dateMs && m.dateMs - it.dateMs <= SMOOTH_WINDOW_DAYS * DAY_MS
            }
            m.dateMs to win.map { it.weightKg.toDouble() }.average().toFloat()
        }
    }

    fun arrow(direction: Direction): String = when (direction) {
        Direction.DOWN -> "↓"
        Direction.UP -> "↑"
        Direction.STEADY -> "→"
    }

    /** House weight format: integers bare, otherwise one decimal (locale-stable for the prompt). */
    fun fmtKg(v: Float): String =
        if (v == v.toInt().toFloat()) v.toInt().toString()
        else String.format(Locale.ROOT, "%.1f", v)
}
