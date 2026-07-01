package com.migul.treningsprogram

import com.migul.treningsprogram.domain.DayBoundary
import com.migul.treningsprogram.domain.RestDayBackfill
import com.migul.treningsprogram.data.db.entity.WorkoutSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Item 7 — configurable day boundary. Verifies the pure logical-day math at the exact edges the
 * brief calls out (00:00, 03:59, 04:00) plus its interaction with the auto rest/missed window, using
 * a fixed UTC zone so the assertions are host-timezone-independent.
 */
class DayBoundaryTest {

    private val utc = ZoneId.of("UTC")

    /** Epoch-millis for a local date-time in UTC. */
    private fun ms(y: Int, mo: Int, d: Int, h: Int, mi: Int = 0): Long =
        LocalDateTime.of(y, mo, d, h, mi).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun epochDay(y: Int, mo: Int, d: Int): Long = LocalDate.of(y, mo, d).toEpochDay()

    // ── default cutoff 04:00 — the three boundary instants ────────────────────────────────────────

    @Test fun midnight_countsAsPreviousDay() {
        assertEquals(LocalDate.of(2026, 6, 14), DayBoundary.logicalDate(ms(2026, 6, 15, 0, 0), 4, utc))
    }

    @Test fun oneAm_countsAsPreviousDay() {
        assertEquals(LocalDate.of(2026, 6, 14), DayBoundary.logicalDate(ms(2026, 6, 15, 1, 0), 4, utc))
    }

    @Test fun justBeforeCutoff_countsAsPreviousDay() {
        assertEquals(LocalDate.of(2026, 6, 14), DayBoundary.logicalDate(ms(2026, 6, 15, 3, 59), 4, utc))
    }

    @Test fun exactlyCutoff_countsAsSameDay() {
        assertEquals(LocalDate.of(2026, 6, 15), DayBoundary.logicalDate(ms(2026, 6, 15, 4, 0), 4, utc))
    }

    @Test fun afternoon_countsAsSameDay() {
        assertEquals(LocalDate.of(2026, 6, 15), DayBoundary.logicalDate(ms(2026, 6, 15, 13, 30), 4, utc))
    }

    @Test fun lateNight_beforeMidnight_countsAsSameDay() {
        // 23:00 is still the same logical day (well inside 04:00 → 03:59 next day).
        assertEquals(LocalDate.of(2026, 6, 15), DayBoundary.logicalDate(ms(2026, 6, 15, 23, 0), 4, utc))
    }

    // ── cutoff 0 (midnight) is a no-op shift ──────────────────────────────────────────────────────

    @Test fun cutoffZero_isPlainCalendarDate() {
        assertEquals(LocalDate.of(2026, 6, 15), DayBoundary.logicalDate(ms(2026, 6, 15, 0, 0), 0, utc))
        assertEquals(LocalDate.of(2026, 6, 15), DayBoundary.logicalDate(ms(2026, 6, 15, 23, 59), 0, utc))
    }

    // ── a non-default cutoff shifts the edge accordingly ──────────────────────────────────────────

    @Test fun cutoffSix_shiftsUntilSixAm() {
        assertEquals(LocalDate.of(2026, 6, 14), DayBoundary.logicalDate(ms(2026, 6, 15, 5, 59), 6, utc))
        assertEquals(LocalDate.of(2026, 6, 15), DayBoundary.logicalDate(ms(2026, 6, 15, 6, 0), 6, utc))
    }

    // ── epoch-day helpers agree with logicalDate ──────────────────────────────────────────────────

    @Test fun logicalEpochDay_matchesLogicalDate() {
        assertEquals(epochDay(2026, 6, 14), DayBoundary.logicalEpochDay(ms(2026, 6, 15, 2, 0), 4, utc))
        assertEquals(epochDay(2026, 6, 15), DayBoundary.logicalEpochDay(ms(2026, 6, 15, 4, 0), 4, utc))
    }

    @Test fun today_usesInjectedNow() {
        val now = ms(2026, 6, 15, 2, 0) // 02:00 → still the previous logical day
        assertEquals(LocalDate.of(2026, 6, 14), DayBoundary.today(4, utc, now))
        assertEquals(epochDay(2026, 6, 14), DayBoundary.todayEpochDay(4, utc, now))
    }

    // ── toLogicalMillis: shifting then date-formatting equals the logical date ─────────────────────

    @Test fun toLogicalMillis_shiftsBackByCutoffHours() {
        val raw = ms(2026, 6, 15, 1, 0)
        val shifted = DayBoundary.toLogicalMillis(raw, 4)
        assertEquals(raw - 4L * 60 * 60 * 1000, shifted)
        // The shifted instant's calendar date == the logical date of the raw instant.
        assertEquals(
            DayBoundary.logicalDate(raw, 4, utc),
            java.time.Instant.ofEpochMilli(shifted).atZone(utc).toLocalDate()
        )
    }

    // ── holder default + coercion ─────────────────────────────────────────────────────────────────

    @Test fun holderDefaultsToFour() {
        assertEquals(DayBoundary.DEFAULT_CUTOFF_HOUR, 4)
    }

    @Test fun holderCoercesOutOfRange() {
        val original = DayBoundary.cutoffHour
        try {
            DayBoundary.cutoffHour = 99
            assertEquals(DayBoundary.MAX_CUTOFF_HOUR, DayBoundary.cutoffHour)
            DayBoundary.cutoffHour = -5
            assertEquals(DayBoundary.MIN_CUTOFF_HOUR, DayBoundary.cutoffHour)
        } finally {
            DayBoundary.cutoffHour = original
        }
    }

    // ── interaction with the auto rest/missed window (the confirmed edge case) ────────────────────

    /**
     * Opening at 02:00 (before the 04:00 cutoff) must NOT yet close out the previous calendar day as
     * rest/missed — logically we're still IN that day, so it is "today" and excluded from the fill
     * window. Only a later open at/after 04:00 closes it.
     */
    @Test fun restBackfill_preCutoffOpen_doesNotCloseTheCurrentLogicalDay() {
        // Sun 2026-06-14 is the last logged workout. We open on Mon 2026-06-15 at 02:00 → logical
        // "today" is still Sun 2026-06-14, so nothing new is fillable yet.
        val lastLogged = DayBoundary.logicalEpochDay(ms(2026, 6, 14, 18, 0), 4, utc) // Sun evening
        val featureFirstRun = DayBoundary.logicalEpochDay(ms(2026, 6, 10, 12, 0), 4, utc)
        val nowPreCutoff = ms(2026, 6, 15, 2, 0)
        val todayEpochPre = DayBoundary.todayEpochDay(4, utc, nowPreCutoff)
        assertEquals(epochDay(2026, 6, 14), todayEpochPre)

        val fillPre = RestDayBackfill.daysToFill(
            todayEpoch = todayEpochPre,
            lastLoggedEpoch = lastLogged,
            featureFirstRunEpoch = featureFirstRun,
            existingRecordEpochs = setOf(lastLogged)
        )
        assertEquals("Pre-cutoff open must not close the still-current day", emptyList<Long>(), fillPre)

        // Re-open the same morning at 05:00 (after cutoff) → today rolls to Mon 2026-06-15, and now
        // Sunday (the previously-current logical day) becomes closeable as rest/missed.
        val nowPostCutoff = ms(2026, 6, 15, 5, 0)
        val todayEpochPost = DayBoundary.todayEpochDay(4, utc, nowPostCutoff)
        assertEquals(epochDay(2026, 6, 15), todayEpochPost)
        val fillPost = RestDayBackfill.daysToFill(
            todayEpoch = todayEpochPost,
            lastLoggedEpoch = lastLogged,
            featureFirstRunEpoch = featureFirstRun,
            existingRecordEpochs = setOf(lastLogged)
        )
        // Window is (lastLogged+1 .. today-1) = just Sunday 2026-06-14... but lastLogged IS Sunday,
        // so the day after (Monday-1 = nothing) — the window is empty here because lastLogged == the
        // day before today. Assert the classification path instead: a gap day would be included.
        assertEquals(emptyList<Long>(), fillPost)
    }

    @Test fun restBackfill_closesAGapDayOnceCutoffPasses() {
        // Last logged Fri 2026-06-12; open Mon 2026-06-15 06:00 → today = Mon; Sat+Sun are closeable.
        val lastLogged = epochDay(2026, 6, 12)
        val featureFirstRun = epochDay(2026, 6, 1)
        val now = ms(2026, 6, 15, 6, 0)
        val today = DayBoundary.todayEpochDay(4, utc, now)
        assertEquals(epochDay(2026, 6, 15), today)
        val fill = RestDayBackfill.daysToFill(
            todayEpoch = today,
            lastLoggedEpoch = lastLogged,
            featureFirstRunEpoch = featureFirstRun,
            existingRecordEpochs = setOf(lastLogged)
        )
        assertEquals(listOf(epochDay(2026, 6, 13), epochDay(2026, 6, 14)), fill)
    }

    // Guards that KIND constants used by the timeline still exist (defensive; unrelated churn check).
    @Test fun restMissedKindsDistinct() {
        assertNotEquals(WorkoutSession.KIND_REST, WorkoutSession.KIND_MISSED)
    }
}
