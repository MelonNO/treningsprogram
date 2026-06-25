package com.migul.treningsprogram.domain

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pure, Android-free helpers for the X-axis date labels drawn by
 * [com.migul.treningsprogram.ui.history.StrengthChartView] (UX1 readability pass).
 *
 * Kept out of the View so the label-selection / formatting logic is JVM-unit-testable
 * (see ChartAxisTest). The View only positions and draws the strings these functions return.
 *
 * Date formatting uses the device default Locale so the Stats area matches the rest of the app
 * (which formats with `Locale.getDefault()` throughout).
 */
object ChartAxis {

    private const val DAY_MS = 86_400_000L
    private const val YEAR_MS = 365L * DAY_MS

    /**
     * Up to three tick labels for a chronological series of epoch-ms timestamps:
     * first, (optional) middle, and last. The pattern is chosen by span so labels stay short:
     *   • span < ~1 year  → "d MMM"   (e.g. "4 Jun")
     *   • span ≥ ~1 year  → "MMM yy"  (e.g. "Jun 25")
     *
     * Returns:
     *   • []                for empty input,
     *   • [one label]       for a single timestamp,
     *   • [first, last]     for two,
     *   • [first, mid, last] for three or more (mid = the median-by-position point).
     *
     * The caller (the View) decides whether it has room to draw the middle label.
     */
    fun dateLabels(datesMs: List<Long>): List<String> {
        if (datesMs.isEmpty()) return emptyList()
        val sorted = datesMs.sorted()
        val span = sorted.last() - sorted.first()
        val pattern = if (span >= YEAR_MS) "MMM yy" else "d MMM"
        val fmt = SimpleDateFormat(pattern, Locale.getDefault())
        fun lbl(ms: Long) = fmt.format(Date(ms))

        return when (sorted.size) {
            1 -> listOf(lbl(sorted.first()))
            2 -> listOf(lbl(sorted.first()), lbl(sorted.last()))
            else -> listOf(
                lbl(sorted.first()),
                lbl(sorted[sorted.size / 2]),
                lbl(sorted.last())
            )
        }
    }
}
