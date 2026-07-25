package com.migul.treningsprogram.domain

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Stage-3 items 12/13 — the shared start/end calendar range filter for the History and Progress
 * sub-tabs. A null range means "All" (the default; ranges deliberately do not persist across
 * restarts — assumptions A-12a/A-13a).
 *
 * Both ends are inclusive and evaluated against the app's logical day boundary (DayBoundary), so a
 * session logged at 01:00 after a start-date evening workout files exactly where History shows it.
 */
object DateRangeFilter {

    /** An inclusive [startEpochDay]..[endEpochDay] range of local calendar days. */
    data class Range(val startEpochDay: Long, val endEpochDay: Long)

    /**
     * Builds a Range from MaterialDatePicker's date-range selection, which reports each picked
     * calendar date as UTC midnight millis. Floor-division converts exactly to epoch days.
     */
    fun fromPickerUtc(startUtcMs: Long, endUtcMs: Long): Range {
        val s = Math.floorDiv(startUtcMs, 86_400_000L)
        val e = Math.floorDiv(endUtcMs, 86_400_000L)
        return if (s <= e) Range(s, e) else Range(e, s)
    }

    /** True when [dateMs]'s logical day falls inside [range] (null range = everything passes). */
    fun contains(
        range: Range?,
        dateMs: Long,
        cutoffHour: Int = DayBoundary.cutoffHour,
        zone: ZoneId = ZoneId.systemDefault()
    ): Boolean =
        range == null ||
            DayBoundary.logicalEpochDay(dateMs, cutoffHour, zone) in range.startEpochDay..range.endEpochDay

    /**
     * QoL item 09: the [items] whose [dateOf] timestamp falls inside [range] (both ends
     * inclusive, same logical-day evaluation as [contains]; null range = everything passes).
     * Extracted so the body-weight chart's range windowing is unit-testable exactly like the
     * strength/reps filtering (ChartTouchTest).
     */
    fun <T> filter(
        items: List<T>,
        range: Range?,
        cutoffHour: Int = DayBoundary.cutoffHour,
        zone: ZoneId = ZoneId.systemDefault(),
        dateOf: (T) -> Long
    ): List<T> =
        if (range == null) items
        else items.filter { contains(range, dateOf(it), cutoffHour, zone) }

    /** The control's display text: "All time" or "5 Jun 2026 – 2 Jul 2026" (single day collapses). */
    fun label(range: Range?): String {
        if (range == null) return "All time"
        val fmt = DateTimeFormatter.ofPattern("d MMM yyyy")
        val s = LocalDate.ofEpochDay(range.startEpochDay).format(fmt)
        val e = LocalDate.ofEpochDay(range.endEpochDay).format(fmt)
        return if (s == e) s else "$s – $e"
    }
}
