package com.migul.treningsprogram

import com.migul.treningsprogram.data.db.entity.WorkoutSession
import com.migul.treningsprogram.domain.RestDayBackfill
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Auto-log rest/missed days: the pure date-window math and REST-vs-MISSED classification the launch/
 * foreground backfill relies on. Days are epoch-days (LocalDate.toEpochDay()); here we use a fixed
 * base so the arithmetic is easy to read.
 */
class RestDayBackfillTest {

    private val today = 20_000L // arbitrary "today" epoch-day
    private val yesterday = today - 1

    // ── window: boundary + today-excluded ────────────────────────────────────────────────────────

    @Test fun `window runs from firstRun+1 up to and including yesterday`() {
        // No prior workouts; feature first ran 5 days ago; nothing logged since.
        val firstRun = today - 5
        val days = RestDayBackfill.daysToFill(
            todayEpoch = today,
            lastLoggedEpoch = null,
            featureFirstRunEpoch = firstRun,
            existingRecordEpochs = emptySet()
        )
        // firstRun+1 .. yesterday  ⇒  today-4 .. today-1
        assertEquals(listOf(today - 4, today - 3, today - 2, today - 1), days)
    }

    @Test fun `today is always excluded`() {
        val days = RestDayBackfill.daysToFill(
            todayEpoch = today,
            lastLoggedEpoch = null,
            featureFirstRunEpoch = today - 3,
            existingRecordEpochs = emptySet()
        )
        assertTrue("today must never be filled", today !in days)
        assertEquals(yesterday, days.last())
    }

    @Test fun `first ever run with firstRun == today fills nothing`() {
        // Mirrors the very first launch after the update: window collapses to empty.
        val days = RestDayBackfill.daysToFill(
            todayEpoch = today,
            lastLoggedEpoch = today - 30, // old workout far in the past
            featureFirstRunEpoch = today, // stamped now
            existingRecordEpochs = emptySet()
        )
        assertEquals(emptyList<Long>(), days)
    }

    // ── featureFirstRun clamp vs lastLogged floor ────────────────────────────────────────────────

    @Test fun `featureFirstRun clamps the window - days before the feature existed are never invented`() {
        // Last real workout was long ago, but the feature only started 2 days ago: only the 1 empty
        // day since first-run (yesterday) is filled, NOT the whole gap back to the old workout.
        val days = RestDayBackfill.daysToFill(
            todayEpoch = today,
            lastLoggedEpoch = today - 100,
            featureFirstRunEpoch = today - 2,
            existingRecordEpochs = emptySet()
        )
        assertEquals(listOf(today - 1), days)
    }

    @Test fun `last logged workout is the floor once it is newer than firstRun`() {
        // Feature ran 10 days ago; a workout was logged 4 days ago ⇒ fill the 3-day gap since it.
        val days = RestDayBackfill.daysToFill(
            todayEpoch = today,
            lastLoggedEpoch = today - 4,
            featureFirstRunEpoch = today - 10,
            existingRecordEpochs = emptySet()
        )
        assertEquals(listOf(today - 3, today - 2, today - 1), days)
    }

    // ── idempotency ──────────────────────────────────────────────────────────────────────────────

    @Test fun `days that already have a record are skipped (idempotent re-run)`() {
        // First run filled today-4..today-1; a later same-day re-run must add nothing.
        val already = setOf(today - 4, today - 3, today - 2, today - 1)
        val days = RestDayBackfill.daysToFill(
            todayEpoch = today,
            lastLoggedEpoch = null,
            featureFirstRunEpoch = today - 5,
            existingRecordEpochs = already
        )
        assertEquals(emptyList<Long>(), days)
    }

    @Test fun `only the genuinely empty days in the window are filled`() {
        // today-3 already has a session (workout or earlier placeholder) ⇒ skip just that one.
        val days = RestDayBackfill.daysToFill(
            todayEpoch = today,
            lastLoggedEpoch = null,
            featureFirstRunEpoch = today - 5,
            existingRecordEpochs = setOf(today - 3)
        )
        assertEquals(listOf(today - 4, today - 2, today - 1), days)
    }

    // ── classification: REST-DAY mode ────────────────────────────────────────────────────────────

    @Test fun `rest-day mode - empty rest day is REST, empty training day is MISSED`() {
        val restDays = setOf(6, 7) // Sat, Sun are rest
        // Sunday (7) empty ⇒ a planned rest day ⇒ REST
        assertEquals(WorkoutSession.KIND_REST, RestDayBackfill.classify(7, restDays, emptySet()))
        // Wednesday (3) empty ⇒ a training day with nothing logged ⇒ MISSED
        assertEquals(WorkoutSession.KIND_MISSED, RestDayBackfill.classify(3, restDays, emptySet()))
    }

    // ── classification: COUNT mode (planned-weekday signal) ──────────────────────────────────────

    @Test fun `count mode - planned weekday is MISSED, unplanned weekday is REST`() {
        val planned = setOf(1, 3, 5) // plan trains Mon/Wed/Fri
        assertEquals(WorkoutSession.KIND_MISSED, RestDayBackfill.classify(3, emptySet(), planned)) // Wed
        assertEquals(WorkoutSession.KIND_REST, RestDayBackfill.classify(2, emptySet(), planned))   // Tue
    }

    @Test fun `count mode with no plan signal defaults every empty day to REST`() {
        // No rest days AND no plan ⇒ can't determine training days ⇒ default REST (never MISSED).
        for (weekday in 1..7) {
            assertEquals(WorkoutSession.KIND_REST, RestDayBackfill.classify(weekday, emptySet(), emptySet()))
        }
    }

    @Test fun `rest-day mode takes precedence over the count-mode plan signal`() {
        // When rest days are set, the plan signal is ignored (mode is decided by restDays non-empty).
        val restDays = setOf(7) // only Sunday is rest
        val planned = setOf(7)  // plan (stale) lists Sunday — must NOT make Sunday MISSED
        assertEquals(WorkoutSession.KIND_REST, RestDayBackfill.classify(7, restDays, planned))
    }
}
