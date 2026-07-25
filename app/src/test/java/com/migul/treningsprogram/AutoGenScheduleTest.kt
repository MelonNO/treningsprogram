package com.migul.treningsprogram

import com.migul.treningsprogram.domain.AutoGenSchedule
import com.migul.treningsprogram.domain.DayBoundary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Item 06: the unattended-run schedule computation. The contract: the next run lands at calendar
 * Monday cutoffHour:20 local — the first safe instant of the new LOGICAL week (thisMonday() keys
 * the plan through the DayBoundary cutoff, so running before it would generate for the old week) —
 * strictly in the future, for arbitrary "now".
 */
class AutoGenScheduleTest {

    private val zone: ZoneId = ZoneId.of("Europe/Oslo")

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int): Long =
        ZonedDateTime.of(y, m, d, h, min, 0, 0, zone).toInstant().toEpochMilli()

    // 2026-07-20 was a Monday; 2026-07-22 a Wednesday; 2026-07-27 the following Monday.

    @Test fun midWeek_landsOnNextMondayAtCutoffPlusMargin() {
        val next = AutoGenSchedule.nextRunAtMs(at(2026, 7, 22, 12, 0), cutoffHour = 4, zone = zone)
        assertEquals(at(2026, 7, 27, 4, 20), next)
    }

    @Test fun sundayNight_landsHoursAway_onMondayMorning() {
        val now = at(2026, 7, 26, 23, 50)
        val next = AutoGenSchedule.nextRunAtMs(now, cutoffHour = 4, zone = zone)
        assertEquals(at(2026, 7, 27, 4, 20), next)
        assertTrue("Sunday-night delay should be hours, not a week", next - now < 5L * 60 * 60 * 1000)
    }

    @Test fun mondayBeforeTheSlot_runsLaterToday() {
        val next = AutoGenSchedule.nextRunAtMs(at(2026, 7, 27, 3, 0), cutoffHour = 4, zone = zone)
        assertEquals(at(2026, 7, 27, 4, 20), next)
    }

    @Test fun mondayExactlyAtTheSlot_isStrictlyFuture_soNextWeek() {
        val next = AutoGenSchedule.nextRunAtMs(at(2026, 7, 27, 4, 20), cutoffHour = 4, zone = zone)
        assertEquals(at(2026, 8, 3, 4, 20), next)
    }

    @Test fun mondayAfterTheSlot_rollsToNextWeek() {
        val next = AutoGenSchedule.nextRunAtMs(at(2026, 7, 27, 5, 0), cutoffHour = 4, zone = zone)
        assertEquals(at(2026, 8, 3, 4, 20), next)
    }

    @Test fun midnightCutoff_runsAt0020() {
        val next = AutoGenSchedule.nextRunAtMs(at(2026, 7, 24, 9, 0), cutoffHour = 0, zone = zone)
        assertEquals(at(2026, 7, 27, 0, 20), next)
    }

    @Test fun maxCutoff_runsAt0620_stillMondayMorning() {
        val next = AutoGenSchedule.nextRunAtMs(at(2026, 7, 24, 9, 0), cutoffHour = 6, zone = zone)
        assertEquals(at(2026, 7, 27, 6, 20), next)
    }

    @Test fun sweep_arbitraryInstants_alwaysStrictlyFuture_withinAWeek_andLogicalMonday() {
        for (cutoff in intArrayOf(DayBoundary.MIN_CUTOFF_HOUR, 3, DayBoundary.DEFAULT_CUTOFF_HOUR, DayBoundary.MAX_CUTOFF_HOUR)) {
            var now = at(2026, 1, 1, 0, 0)
            repeat(80) {
                val next = AutoGenSchedule.nextRunAtMs(now, cutoffHour = cutoff, zone = zone)
                assertTrue("must be strictly future (cutoff=$cutoff now=$now)", next > now)
                assertTrue(
                    "at most ~a week out (cutoff=$cutoff now=$now)",
                    next - now <= 7L * 24 * 60 * 60 * 1000 + 60_000
                )
                // The whole point: the run instant belongs to a LOGICAL Monday, so thisMonday()
                // inside the worker resolves to the NEW week.
                assertEquals(
                    "logical day at run time must be Monday (cutoff=$cutoff now=$now)",
                    DayOfWeek.MONDAY,
                    DayBoundary.logicalDate(next, cutoff, zone).dayOfWeek
                )
                // Calendar wall-clock is cutoff:20 local.
                val local = Instant.ofEpochMilli(next).atZone(zone)
                assertEquals(cutoff, local.hour)
                assertEquals(AutoGenSchedule.RUN_MINUTE_MARGIN, local.minute)
                now += 13L * 60 * 60 * 1000   // step 13h to hit every weekday/hour combination
            }
        }
    }

    @Test fun delayIsAtMinusNow() {
        val now = at(2026, 7, 22, 12, 0)
        assertEquals(
            AutoGenSchedule.nextRunAtMs(now, 4, zone) - now,
            AutoGenSchedule.nextRunDelayMs(now, 4, zone)
        )
    }

    @Test fun dstSpringForwardWeek_stillComputesAValidMondaySlot() {
        // Europe/Oslo springs forward Sun 2026-03-29 02:00→03:00; the following Monday is 03-30.
        val next = AutoGenSchedule.nextRunAtMs(at(2026, 3, 28, 12, 0), cutoffHour = 4, zone = zone)
        assertEquals(at(2026, 3, 30, 4, 20), next)
    }
}
