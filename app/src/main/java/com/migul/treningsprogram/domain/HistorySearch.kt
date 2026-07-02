package com.migul.treningsprogram.domain

import java.text.SimpleDateFormat
import java.time.ZoneId
import java.util.Date
import java.util.Locale

/**
 * F3 (v1.24.1) — the History "Sessions" list filter, extracted pure so the default-entry,
 * date-range, and search paths are unit-testable.
 *
 * A session passes when it falls inside the (optional) calendar range AND its LOGICAL day
 * (DayBoundary, item 7) formatted as "dd MMM yyyy EEE" contains the typed query — the same
 * logical day the list card displays, so searching a date finds the session where History
 * actually files it. A blank query passes everything.
 */
object HistorySearch {

    /** The format a typed query is matched against, e.g. "02 Jul 2026 Thu". */
    const val MATCH_PATTERN = "dd MMM yyyy EEE"

    fun matches(
        dateMs: Long,
        query: String,
        range: DateRangeFilter.Range?,
        cutoffHour: Int = DayBoundary.cutoffHour,
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault()
    ): Boolean {
        if (!DateRangeFilter.contains(range, dateMs, cutoffHour, zone)) return false
        if (query.isBlank()) return true
        val fmt = SimpleDateFormat(MATCH_PATTERN, locale).apply {
            timeZone = java.util.TimeZone.getTimeZone(zone)
        }
        return fmt.format(Date(DayBoundary.toLogicalMillis(dateMs, cutoffHour)))
            .contains(query, ignoreCase = true)
    }
}
