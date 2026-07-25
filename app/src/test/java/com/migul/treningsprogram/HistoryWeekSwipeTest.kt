package com.migul.treningsprogram

import com.migul.treningsprogram.domain.DateRangeFilter
import com.migul.treningsprogram.domain.HistoryBrowser
import com.migul.treningsprogram.domain.HistoryBrowser.DayState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

/**
 * History week-swipe (2026-07-25) — the pure navigation math behind swiping between weeks in
 * the History week view: previous/next resolution over the browser-VISIBLE (filter-respecting)
 * weeks, the swipe bounds (current week forward, earliest browsable week backward), seamless
 * month boundaries, and same-weekday carry-over with the future-day fallback.
 *
 * Direction convention throughout: -1 = previous/older (right swipe), +1 = next/newer (left
 * swipe) — the Program tab's gesture language.
 */
class HistoryWeekSwipeTest {

    private val zone = ZoneId.of("Europe/Oslo")
    private val cutoff = 4
    private val locale = Locale.ENGLISH

    // Fixed clock: "today" = Sat 25 Jul 2026 (its week starts Mon 20 Jul).
    private val today: LocalDate = LocalDate.of(2026, 7, 25)
    private val todayEpochDay = today.toEpochDay()
    private val curMonday: LocalDate = LocalDate.of(2026, 7, 20)

    private fun ms(date: LocalDate, hour: Int = 17): Long =
        LocalDateTime.of(date, java.time.LocalTime.of(hour, 0)).atZone(zone).toInstant().toEpochMilli()

    private fun workout(id: Long, date: LocalDate) =
        HistoryBrowser.SessionRow(id, ms(date), null, 45)

    private fun set(session: Long, name: String) =
        HistoryBrowser.SetRow(session, name, "Legs", 1, 8, 100f, false)

    private fun build(
        sessions: List<HistoryBrowser.SessionRow>,
        sets: List<HistoryBrowser.SetRow> = emptyList(),
        query: String = "",
        range: DateRangeFilter.Range? = null
    ) = HistoryBrowser.build(sessions, sets, query, range, todayEpochDay, cutoff, zone, locale)

    private fun day(date: LocalDate) = date.toEpochDay()

    // ── Previous / next resolution ─────────────────────────────────────────────────────────

    @Test
    fun `swipe walks to the adjacent visible week in both directions`() {
        val lastMonday = curMonday.minusWeeks(1)          // Mon 13 Jul
        val model = build(
            sessions = listOf(workout(1, lastMonday), workout(2, curMonday)),
            sets = listOf(set(1, "Squat"), set(2, "Squat"))
        )
        assertEquals(day(lastMonday), HistoryBrowser.adjacentWeekStart(model, day(curMonday), -1))
        assertEquals(day(curMonday), HistoryBrowser.adjacentWeekStart(model, day(lastMonday), +1))
    }

    @Test
    fun `swipe back skips entry-less gap weeks the browser does not show`() {
        val farMonday = curMonday.minusWeeks(3)           // Mon 29 Jun; 6 & 13 Jul empty
        val model = build(
            sessions = listOf(workout(1, farMonday), workout(2, curMonday)),
            sets = listOf(set(1, "Squat"), set(2, "Squat"))
        )
        // The two empty weeks between are not browsable → the swipe jumps straight across.
        assertEquals(day(farMonday), HistoryBrowser.adjacentWeekStart(model, day(curMonday), -1))
        assertEquals(day(curMonday), HistoryBrowser.adjacentWeekStart(model, day(farMonday), +1))
    }

    @Test
    fun `month boundary is seamless in both directions`() {
        // Wed 1 Jul → week Mon 29 Jun (filed under June); Wed 8 Jul → week Mon 6 Jul (July).
        val juneWeek = LocalDate.of(2026, 6, 29)
        val julyWeek = LocalDate.of(2026, 7, 6)
        val model = build(
            sessions = listOf(workout(1, LocalDate.of(2026, 7, 1)), workout(2, LocalDate.of(2026, 7, 8))),
            sets = listOf(set(1, "Squat"), set(2, "Squat"))
        )
        assertEquals(day(juneWeek), HistoryBrowser.adjacentWeekStart(model, day(julyWeek), -1))
        assertEquals(day(julyWeek), HistoryBrowser.adjacentWeekStart(model, day(juneWeek), +1))
    }

    // ── Bounds ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `forward stops at the current week and backward at the earliest browsable week`() {
        val oldest = curMonday.minusWeeks(2)
        val model = build(
            sessions = listOf(workout(1, oldest), workout(2, curMonday)),
            sets = listOf(set(1, "Squat"), set(2, "Squat"))
        )
        assertNull(HistoryBrowser.adjacentWeekStart(model, day(curMonday), +1))   // no future weeks
        assertNull(HistoryBrowser.adjacentWeekStart(model, day(oldest), -1))      // no earlier weeks
    }

    @Test
    fun `the empty current week is still the forward bound for orientation`() {
        // Only past history, but the browser always shows the current week too.
        val past = curMonday.minusWeeks(1)
        val model = build(sessions = listOf(workout(1, past)), sets = listOf(set(1, "Squat")))
        assertEquals(day(curMonday), HistoryBrowser.adjacentWeekStart(model, day(past), +1))
        assertNull(HistoryBrowser.adjacentWeekStart(model, day(curMonday), +1))
    }

    // ── Filter-respecting navigation ───────────────────────────────────────────────────────

    @Test
    fun `with a search active the swipe walks only matching weeks and their edges bound it`() {
        val w1 = curMonday.minusWeeks(3)   // Squat
        val w2 = curMonday.minusWeeks(2)   // Bench only — filtered out by "squat"
        val w3 = curMonday.minusWeeks(1)   // Squat
        val model = build(
            sessions = listOf(workout(1, w1), workout(2, w2), workout(3, w3)),
            sets = listOf(set(1, "Squat"), set(2, "Bench Press"), set(3, "Squat")),
            query = "squat"
        )
        // Sanity: filter shows exactly the two squat weeks.
        assertEquals(listOf(day(w1), day(w3)), HistoryBrowser.visibleWeekStarts(model))
        // The bench-only week is skipped, and the filtered edges are the bounds.
        assertEquals(day(w1), HistoryBrowser.adjacentWeekStart(model, day(w3), -1))
        assertEquals(day(w3), HistoryBrowser.adjacentWeekStart(model, day(w1), +1))
        assertNull(HistoryBrowser.adjacentWeekStart(model, day(w3), +1))
        assertNull(HistoryBrowser.adjacentWeekStart(model, day(w1), -1))
    }

    @Test
    fun `a date range bounds the swipe to the weeks it shows`() {
        val w1 = curMonday.minusWeeks(2)
        val w2 = curMonday.minusWeeks(1)
        val model = build(
            sessions = listOf(workout(1, w1), workout(2, w2), workout(3, curMonday)),
            sets = listOf(set(1, "Squat"), set(2, "Squat"), set(3, "Squat")),
            range = DateRangeFilter.Range(day(w1), day(w2.plusDays(6)))
        )
        assertEquals(listOf(day(w1), day(w2)), HistoryBrowser.visibleWeekStarts(model))
        assertNull(HistoryBrowser.adjacentWeekStart(model, day(w2), +1))   // current week filtered out
        assertEquals(day(w1), HistoryBrowser.adjacentWeekStart(model, day(w2), -1))
    }

    @Test
    fun `an open week the filter no longer shows still swipes to the nearest visible week`() {
        val w1 = curMonday.minusWeeks(2)   // Squat
        val w2 = curMonday.minusWeeks(1)   // Bench — open, then filtered away by "squat"
        val w3 = curMonday                 // Squat
        val model = build(
            sessions = listOf(workout(1, w1), workout(2, w2), workout(3, w3)),
            sets = listOf(set(1, "Squat"), set(2, "Bench Press"), set(3, "Squat")),
            query = "squat"
        )
        assertEquals(day(w1), HistoryBrowser.adjacentWeekStart(model, day(w2), -1))
        assertEquals(day(w3), HistoryBrowser.adjacentWeekStart(model, day(w2), +1))
    }

    // ── Same-weekday carry-over ────────────────────────────────────────────────────────────

    @Test
    fun `the same weekday stays selected across a swipe even when it is empty`() {
        val past = curMonday.minusWeeks(1)
        val model = build(
            sessions = listOf(workout(1, past), workout(2, curMonday.plusDays(2))),
            sets = listOf(set(1, "Squat"), set(2, "Squat"))
        )
        val pastWeek = model.weeksByStart.getValue(day(past))
        // Viewing Wed of the current week → Wed of the previous week, although Wed there is empty.
        val wed = day(curMonday.plusDays(2))
        val carried = HistoryBrowser.carriedDay(pastWeek, wed, todayEpochDay)
        assertEquals(day(past.plusDays(2)), carried)
        assertEquals(DayState.EMPTY, pastWeek.days[2].state)
    }

    @Test
    fun `a future weekday in the current week falls back to the default day`() {
        val past = curMonday.minusWeeks(1)
        val model = build(
            sessions = listOf(workout(1, past.plusDays(6)), workout(2, curMonday)),
            sets = listOf(set(1, "Squat"), set(2, "Squat"))
        )
        val currentWeek = model.weeksByStart.getValue(day(curMonday))
        // Viewing SUNDAY of the past week; the current week's Sunday (26 Jul) is in the future
        // (today = Sat 25 Jul) → the week's normal default pick (its Monday workout).
        val sunday = day(past.plusDays(6))
        val carried = HistoryBrowser.carriedDay(currentWeek, sunday, todayEpochDay)
        assertEquals(HistoryBrowser.defaultDay(currentWeek, todayEpochDay), carried)
        assertEquals(day(curMonday), carried)
    }

    @Test
    fun `today itself is not a future day and carries over`() {
        val past = curMonday.minusWeeks(1)
        val model = build(
            sessions = listOf(workout(1, past.plusDays(5)), workout(2, curMonday)),
            sets = listOf(set(1, "Squat"), set(2, "Squat"))
        )
        val currentWeek = model.weeksByStart.getValue(day(curMonday))
        // Saturday of the past week → Saturday of the current week (= today, allowed).
        val carried = HistoryBrowser.carriedDay(currentWeek, day(past.plusDays(5)), todayEpochDay)
        assertEquals(todayEpochDay, carried)
    }
}
