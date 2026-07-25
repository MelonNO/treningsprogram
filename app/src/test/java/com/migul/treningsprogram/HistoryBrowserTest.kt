package com.migul.treningsprogram

import com.migul.treningsprogram.domain.DateRangeFilter
import com.migul.treningsprogram.domain.HistoryBrowser
import com.migul.treningsprogram.domain.HistoryBrowser.DayState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

/**
 * QoL item 04 — the History week-browser's month → week → day grouping, day-state derivation,
 * search + range filtering, and summaries. All Monday-based logical weeks (cutoff 04:00).
 */
class HistoryBrowserTest {

    private val zone = ZoneId.of("Europe/Oslo")
    private val cutoff = 4
    private val locale = Locale.ENGLISH

    // Fixed clock: "today" = Sat 25 Jul 2026 (its week starts Mon 20 Jul).
    private val today: LocalDate = LocalDate.of(2026, 7, 25)
    private val todayEpochDay = today.toEpochDay()
    private val monday: LocalDate = LocalDate.of(2026, 7, 20)

    private fun ms(date: LocalDate, hour: Int = 17): Long =
        LocalDateTime.of(date, java.time.LocalTime.of(hour, 0)).atZone(zone).toInstant().toEpochMilli()

    private fun workout(id: Long, date: LocalDate, hour: Int = 17, duration: Int = 45) =
        HistoryBrowser.SessionRow(id, ms(date, hour), null, duration)

    private fun rest(id: Long, date: LocalDate) =
        HistoryBrowser.SessionRow(id, ms(date), "REST", 0)

    private fun missed(id: Long, date: LocalDate) =
        HistoryBrowser.SessionRow(id, ms(date), "MISSED", 0)

    private fun set(
        session: Long, name: String, setNo: Int, reps: Int, kg: Float,
        warmup: Boolean = false, muscle: String = "Legs", loggedAt: Long = 0L
    ) = HistoryBrowser.SetRow(session, name, muscle, setNo, reps, kg, warmup, loggedAt)

    private fun build(
        sessions: List<HistoryBrowser.SessionRow>,
        sets: List<HistoryBrowser.SetRow> = emptyList(),
        query: String = "",
        range: DateRangeFilter.Range? = null
    ) = HistoryBrowser.build(sessions, sets, query, range, todayEpochDay, cutoff, zone, locale)

    // ── mondayOf ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `mondayOf agrees with java-time across five weeks`() {
        for (i in 0L..34L) {
            val d = today.minusDays(i)
            val expected = d.with(DayOfWeek.MONDAY).toEpochDay()
            assertEquals("for $d", expected, HistoryBrowser.mondayOf(d.toEpochDay()))
        }
    }

    // ── Grouping and day states ────────────────────────────────────────────────────────────

    @Test
    fun `week groups its days Monday-based with correct states and counts`() {
        val model = build(
            sessions = listOf(
                workout(1, monday),                 // Mon workout
                rest(2, monday.plusDays(1)),        // Tue rest
                missed(3, monday.plusDays(2)),      // Wed missed
                workout(4, monday.plusDays(3))      // Thu workout
            ),
            sets = listOf(set(1, "Squat", 1, 8, 100f), set(4, "Bench Press", 1, 8, 60f, muscle = "Chest"))
        )
        val week = model.weeksByStart.getValue(monday.toEpochDay())
        assertEquals(7, week.days.size)
        assertEquals(monday.toEpochDay(), week.days.first().epochDay)
        assertEquals(DayState.WORKOUT, week.days[0].state)
        assertEquals(DayState.REST, week.days[1].state)
        assertEquals(DayState.MISSED, week.days[2].state)
        assertEquals(DayState.WORKOUT, week.days[3].state)
        assertEquals(DayState.EMPTY, week.days[4].state)     // Fri (past, no rows)
        assertEquals(DayState.EMPTY, week.days[5].state)     // Sat = today, no rows
        assertEquals(DayState.FUTURE, week.days[6].state)    // Sun is after today
        assertEquals(2, week.workoutDays)
        assertEquals(1, week.restDays)
        assertEquals(1, week.missedDays)
        assertTrue(week.isCurrent)
    }

    @Test
    fun `a workout wins the day state over a same-day placeholder`() {
        val model = build(
            sessions = listOf(rest(1, monday), workout(2, monday)),
            sets = listOf(set(2, "Squat", 1, 5, 100f))
        )
        val day = model.weeksByStart.getValue(monday.toEpochDay()).days[0]
        assertEquals(DayState.WORKOUT, day.state)
        assertEquals(1, day.sessions.size)   // the placeholder never becomes a session card
    }

    @Test
    fun `an 0100 session files under the previous logical day`() {
        val tue = monday.plusDays(1)
        val model = build(
            sessions = listOf(workout(1, tue, hour = 1)),   // 01:00 Tue < 04:00 cutoff → Monday
            sets = listOf(set(1, "Squat", 1, 5, 100f))
        )
        val week = model.weeksByStart.getValue(monday.toEpochDay())
        assertEquals(DayState.WORKOUT, week.days[0].state)
        assertEquals(DayState.EMPTY, week.days[1].state)
    }

    @Test
    fun `current week appears even with no entries and empty weeks between months are absent`() {
        val mayDate = LocalDate.of(2026, 5, 6)
        val model = build(
            sessions = listOf(workout(1, mayDate)),
            sets = listOf(set(1, "Squat", 1, 5, 100f))
        )
        // Current (empty) week is present for orientation…
        assertTrue(model.weeksByStart.getValue(monday.toEpochDay()).isCurrent)
        assertTrue(model.months.any { it.year == 2026 && it.month == 7 })
        // …but the entry-less weeks between May and July are simply absent.
        assertEquals(2, model.weeksByStart.size)
        assertEquals(2, model.months.size)
        // Months newest-first.
        assertEquals(7, model.months.first().month)
        assertEquals(5, model.months.last().month)
    }

    @Test
    fun `no sessions at all yields no history`() {
        val model = build(emptyList())
        assertFalse(model.hasAnyHistory)
        assertTrue(model.months.isEmpty())
    }

    @Test
    fun `weeks group into the month of their Monday`() {
        // Wed 1 Jul 2026 lies in the week of Mon 29 Jun → filed under June.
        val model = build(
            sessions = listOf(workout(1, LocalDate.of(2026, 7, 1))),
            sets = listOf(set(1, "Squat", 1, 5, 100f))
        )
        val june = model.months.first { it.year == 2026 && it.month == 6 }
        assertEquals(LocalDate.of(2026, 6, 29).toEpochDay(), june.weeks.single().weekStartEpochDay)
    }

    // ── Exercise summaries ─────────────────────────────────────────────────────────────────

    @Test
    fun `exercise summary counts sets, distinguishes warm-ups, and picks the top set`() {
        val model = build(
            sessions = listOf(workout(1, monday)),
            sets = listOf(
                set(1, "Squat", 1, 10, 60f, warmup = true),
                set(1, "Squat", 2, 8, 100f),
                set(1, "Squat", 3, 6, 100f),   // same weight, fewer reps — not the top set
                set(1, "Squat", 4, 5, 95f)
            )
        )
        val ex = model.weeksByStart.getValue(monday.toEpochDay()).days[0].sessions[0].exercises.single()
        assertEquals(3, ex.workingSets)
        assertEquals(1, ex.warmupSets)
        assertEquals(8, ex.topReps)
        assertEquals(100f, ex.topWeightKg)
        // First-ever lift: baseline, never PR.
        assertFalse(ex.isPr)
        assertNull(ex.priorMaxKg)
    }

    @Test
    fun `a later heavier session is flagged PR with its prior max`() {
        val model = build(
            sessions = listOf(workout(1, monday), workout(2, monday.plusDays(2))),
            sets = listOf(set(1, "Squat", 1, 8, 100f), set(2, "Squat", 1, 6, 105f))
        )
        val wed = model.weeksByStart.getValue(monday.toEpochDay()).days[2]
        val ex = wed.sessions.single().exercises.single()
        assertTrue(ex.isPr)
        assertEquals(100f, ex.priorMaxKg)
    }

    @Test
    fun `bodyweight-only exercises never flag PRs`() {
        val model = build(
            sessions = listOf(workout(1, monday), workout(2, monday.plusDays(1))),
            sets = listOf(set(1, "Push-ups", 1, 15, 0f, muscle = "Chest"),
                set(2, "Push-ups", 1, 20, 0f, muscle = "Chest"))
        )
        val tue = model.weeksByStart.getValue(monday.toEpochDay()).days[1]
        val ex = tue.sessions.single().exercises.single()
        assertFalse(ex.isPr)
        assertEquals(0f, ex.topWeightKg)
        assertEquals(20, ex.topReps)
    }

    @Test
    fun `day type derives STR RUN MIX from performed muscle groups`() {
        val model = build(
            sessions = listOf(workout(1, monday), workout(2, monday.plusDays(1)), workout(3, monday.plusDays(2))),
            sets = listOf(
                set(1, "Squat", 1, 8, 100f),
                set(2, "Treadmill Run", 1, 30, 0f, muscle = "Cardio"),
                set(3, "Bench Press", 1, 8, 60f, muscle = "Chest"),
                set(3, "Easy Jog", 1, 20, 0f, muscle = "Cardio")
            )
        )
        val days = model.weeksByStart.getValue(monday.toEpochDay()).days
        assertEquals("STR", days[0].type)
        assertEquals("RUN", days[1].type)
        assertEquals("MIX", days[2].type)
        assertNull(days[3].type)
    }

    // ── Search + range filtering ───────────────────────────────────────────────────────────

    @Test
    fun `exercise-name search keeps only weeks containing that exercise`() {
        val juneDate = LocalDate.of(2026, 6, 10)
        val model = build(
            sessions = listOf(workout(1, juneDate), workout(2, monday)),
            sets = listOf(set(1, "Squat", 1, 8, 100f), set(2, "Bench Press", 1, 8, 60f, muscle = "Chest")),
            query = "bench"
        )
        assertTrue(model.filterActive)
        assertEquals(1, model.months.size)
        assertEquals(7, model.months.single().month)
        // The unfiltered map still carries every week for the week view.
        assertTrue(model.weeksByStart.containsKey(HistoryBrowser.mondayOf(juneDate.toEpochDay())))
    }

    @Test
    fun `date-text search matches the old Sessions-list semantics`() {
        val model = build(
            sessions = listOf(workout(1, monday.plusDays(1))),   // Tue 21 Jul 2026
            sets = listOf(set(1, "Squat", 1, 8, 100f)),
            query = "21 Jul"
        )
        assertEquals(1, model.months.size)
        val tue = model.weeksByStart.getValue(monday.toEpochDay()).days[1]
        assertTrue(tue.matches)
    }

    @Test
    fun `non-matching query empties the browser but not the week map`() {
        val model = build(
            sessions = listOf(workout(1, monday)),
            sets = listOf(set(1, "Squat", 1, 8, 100f)),
            query = "deadlift"
        )
        assertTrue(model.months.isEmpty())
        assertTrue(model.hasAnyHistory)
        assertFalse(model.weeksByStart.isEmpty())
    }

    @Test
    fun `date range keeps only days inside it`() {
        val juneDate = LocalDate.of(2026, 6, 10)
        val model = build(
            sessions = listOf(workout(1, juneDate), workout(2, monday)),
            sets = listOf(set(1, "Squat", 1, 8, 100f), set(2, "Squat", 1, 8, 102f)),
            range = DateRangeFilter.Range(juneDate.toEpochDay(), juneDate.toEpochDay())
        )
        assertEquals(1, model.months.size)
        assertEquals(6, model.months.single().month)
    }

    @Test
    fun `rest and missed days match date queries but not exercise queries`() {
        val model = build(
            sessions = listOf(rest(1, monday.plusDays(1))),
            query = "21 Jul"
        )
        assertTrue(model.weeksByStart.getValue(monday.toEpochDay()).days[1].matches)

        val model2 = build(
            sessions = listOf(rest(1, monday.plusDays(1))),
            query = "squat"
        )
        assertFalse(model2.weeksByStart.getValue(monday.toEpochDay()).days[1].matches)
    }

    // ── UI helpers ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `defaultDay prefers the latest past workout, then any latest past entry`() {
        val model = build(
            sessions = listOf(workout(1, monday), rest(2, monday.plusDays(3))),
            sets = listOf(set(1, "Squat", 1, 8, 100f))
        )
        val week = model.weeksByStart.getValue(monday.toEpochDay())
        assertEquals(monday.toEpochDay(), HistoryBrowser.defaultDay(week, todayEpochDay))

        val restOnly = build(sessions = listOf(rest(2, monday.plusDays(3))))
        val week2 = restOnly.weeksByStart.getValue(monday.toEpochDay())
        assertEquals(monday.plusDays(3).toEpochDay(), HistoryBrowser.defaultDay(week2, todayEpochDay))
    }

    @Test
    fun `titles and summaries read correctly`() {
        val model = build(
            sessions = listOf(
                workout(1, monday), workout(2, monday.plusDays(1)),
                rest(3, monday.plusDays(2)), missed(4, monday.plusDays(3))
            ),
            sets = listOf(set(1, "Squat", 1, 8, 100f), set(2, "Squat", 1, 8, 100f))
        )
        val week = model.weeksByStart.getValue(monday.toEpochDay())
        assertEquals("This week", HistoryBrowser.weekTitle(week, locale))
        assertEquals("2 workouts  ·  1 rest  ·  1 missed", HistoryBrowser.weekSummary(week))
        assertEquals("July 2026", HistoryBrowser.monthLabel(model.months.single(), locale))

        val past = build(
            sessions = listOf(workout(1, LocalDate.of(2026, 6, 10))),
            sets = listOf(set(1, "Squat", 1, 8, 100f))
        )
        val pastWeek = past.weeksByStart.getValue(LocalDate.of(2026, 6, 8).toEpochDay())
        assertEquals("Week of 8 Jun", HistoryBrowser.weekTitle(pastWeek, locale))
        assertEquals("1 workout", HistoryBrowser.weekSummary(pastWeek))
    }

    @Test
    fun `multiple sessions on one day are listed chronologically`() {
        val model = build(
            sessions = listOf(workout(2, monday, hour = 18), workout(1, monday, hour = 9)),
            sets = listOf(set(1, "Squat", 1, 8, 100f), set(2, "Bench Press", 1, 8, 60f, muscle = "Chest"))
        )
        val day = model.weeksByStart.getValue(monday.toEpochDay()).days[0]
        assertEquals(listOf(1L, 2L), day.sessions.map { it.sessionId })
    }

    @Test
    fun `exercises follow logging order when loggedAtMs is present`() {
        val model = build(
            sessions = listOf(workout(1, monday)),
            sets = listOf(
                set(1, "Bench Press", 1, 8, 60f, muscle = "Chest", loggedAt = 2000L),
                set(1, "Squat", 1, 8, 100f, loggedAt = 1000L)
            )
        )
        val names = model.weeksByStart.getValue(monday.toEpochDay())
            .days[0].sessions.single().exercises.map { it.name }
        assertEquals(listOf("Squat", "Bench Press"), names)
    }
}
