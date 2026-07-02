package com.migul.treningsprogram

import com.migul.treningsprogram.domain.DateRangeFilter
import com.migul.treningsprogram.domain.HistorySearch
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

/**
 * F3 (v1.24.1) regression — the History "Sessions" list filter paths.
 *
 * Covers the three ways the list is driven: default entry (blank query, no range),
 * the calendar range, and the typed date search — including the logical-day (item 7)
 * boundary behavior both filters share.
 */
class HistorySearchTest {

    private val zone = ZoneId.of("Europe/Oslo")
    private val locale = Locale.UK
    private val cutoff = 4 // app default "day starts at 04:00"

    private fun ms(dt: LocalDateTime): Long = dt.atZone(zone).toInstant().toEpochMilli()

    // Thu 02 Jul 2026, 17:45 local — the stage-4 sweep's session.
    private val session = ms(LocalDateTime.of(2026, 7, 2, 17, 45))

    private fun matches(dateMs: Long, query: String = "", range: DateRangeFilter.Range? = null) =
        HistorySearch.matches(dateMs, query, range, cutoff, zone, locale)

    // ── Default entry: blank query, no range ────────────────────────────────

    @Test
    fun defaultEntry_passesEverything() {
        assertTrue(matches(session))
        assertTrue(matches(ms(LocalDateTime.of(2020, 1, 1, 12, 0))))
    }

    // ── Search path ──────────────────────────────────────────────────────────

    @Test
    fun search_matchesMonthDayYearAndWeekday_caseInsensitive() {
        assertTrue(matches(session, query = "Jul"))
        assertTrue(matches(session, query = "jul"))
        assertTrue(matches(session, query = "02 Jul 2026"))
        assertTrue(matches(session, query = "Thu"))
        assertTrue(matches(session, query = "2026"))
    }

    @Test
    fun search_nonDateTerm_doesNotMatch() {
        // e.g. an exercise/muscle name — search is date-only.
        assertFalse(matches(session, query = "Back"))
        assertFalse(matches(session, query = "Aug"))
    }

    @Test
    fun search_matchesTheLogicalDayShownInTheList() {
        // 01:30 on Jul 3 files under logical day Jul 2 (cutoff 04:00) — exactly what the card shows.
        val lateNight = ms(LocalDateTime.of(2026, 7, 3, 1, 30))
        assertTrue(matches(lateNight, query = "02 Jul"))
        assertFalse(matches(lateNight, query = "03 Jul"))
    }

    // ── Range path ───────────────────────────────────────────────────────────

    private fun range(start: LocalDate, end: LocalDate) =
        DateRangeFilter.Range(start.toEpochDay(), end.toEpochDay())

    @Test
    fun range_isInclusiveOnBothEnds() {
        val r = range(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 2))
        assertTrue(matches(session, range = r)) // end day, inclusive
        assertTrue(matches(ms(LocalDateTime.of(2026, 7, 1, 9, 0)), range = r)) // start day
        assertFalse(matches(ms(LocalDateTime.of(2026, 6, 30, 9, 0)), range = r))
        assertFalse(matches(ms(LocalDateTime.of(2026, 7, 3, 9, 0)), range = r))
    }

    @Test
    fun range_usesLogicalDays() {
        // 01:30 on Jul 3 is logical Jul 2 → inside a range ending Jul 2.
        val lateNight = ms(LocalDateTime.of(2026, 7, 3, 1, 30))
        assertTrue(matches(lateNight, range = range(LocalDate.of(2026, 7, 2), LocalDate.of(2026, 7, 2))))
        assertFalse(matches(lateNight, range = range(LocalDate.of(2026, 7, 3), LocalDate.of(2026, 7, 3))))
    }

    @Test
    fun rangeAndSearch_combine() {
        val r = range(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 2))
        assertTrue(matches(session, query = "Thu", range = r))
        assertFalse(matches(session, query = "Wed", range = r)) // in range, query misses
        val outside = ms(LocalDateTime.of(2026, 6, 25, 17, 0)) // Thu, but out of range
        assertFalse(matches(outside, query = "Thu", range = r))
    }
}
