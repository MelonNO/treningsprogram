package com.migul.treningsprogram.domain

import kotlin.math.abs

/**
 * QoL item 09 — pure, Android-free math for the touch-to-read ("scrub") gesture on the Stats
 * charts (StrengthChartView / BodyWeightChartView).
 *
 * Those views plot each point at
 *     px = plotLeft + (dateMs − minDate) / (maxDate − minDate) * plotWidth
 * (a degenerate all-same-date range falls back to a span of 1, exactly as the views' onDraw
 * does). This object re-derives that mapping so the snapping decision is JVM-unit-testable
 * (ChartTouchTest); the views only draw the marker at whatever index this returns.
 */
object ChartTouch {

    /**
     * Index of the plotted point nearest to [touchX], for points laid out with the standard
     * linear date→x mapping over [plotLeft]..[plotLeft]+[plotWidth].
     *
     *  • empty [datesMs] → -1 (no phantom markers on empty charts);
     *  • a single point → 0 wherever the touch lands;
     *  • touches left/right of the plot clamp to the first/last point naturally;
     *  • equidistant ties resolve to the earlier index.
     */
    fun nearestIndex(datesMs: List<Long>, plotLeft: Float, plotWidth: Float, touchX: Float): Int {
        if (datesMs.isEmpty()) return -1
        val minD = datesMs.min().toFloat()
        val maxD = datesMs.max().toFloat()
        val rd = if (maxD - minD > 0f) maxD - minD else 1f
        var best = 0
        var bestDist = Float.MAX_VALUE
        for (i in datesMs.indices) {
            val px = plotLeft + (datesMs[i] - minD) / rd * plotWidth
            val dist = abs(px - touchX)
            if (dist < bestDist) {
                bestDist = dist
                best = i
            }
        }
        return best
    }
}
