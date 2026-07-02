package com.migul.treningsprogram

import com.migul.treningsprogram.notify.ReminderScheduler
import com.migul.treningsprogram.ui.common.Changelog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/** F3/F6 — pure scheduling + changelog-window math. */
class ReminderSchedulerTest {

    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int): Long = Calendar.getInstance().run {
        clear()
        set(y, mo - 1, d, h, mi, 0)
        timeInMillis
    }

    @Test fun `trigger later today when the time is still ahead`() {
        val now = at(2026, 7, 2, 9, 0)
        assertEquals(at(2026, 7, 2, 17, 0), ReminderScheduler.nextTrigger(17, 0, now))
    }

    @Test fun `trigger rolls to tomorrow when the time has passed`() {
        val now = at(2026, 7, 2, 18, 30)
        assertEquals(at(2026, 7, 3, 17, 0), ReminderScheduler.nextTrigger(17, 0, now))
    }

    @Test fun `trigger exactly at the boundary rolls to tomorrow`() {
        val now = at(2026, 7, 2, 17, 0)
        assertEquals(at(2026, 7, 3, 17, 0), ReminderScheduler.nextTrigger(17, 0, now))
    }

    // ── R2 weekly (weigh-in) trigger ───────────────────────────────────────────────────────────

    @Test fun `weekly trigger later this week`() {
        // 2026-07-02 is a Thursday (day 4). Target: Saturday (6) 09:00.
        val now = at(2026, 7, 2, 9, 0)
        val trigger = ReminderScheduler.nextWeeklyTrigger(6, 9, 0, now)
        assertEquals(at(2026, 7, 4, 9, 0), trigger)
    }

    @Test fun `weekly trigger same day later time`() {
        // Thursday 08:00 → Thursday (4) 09:00 is still ahead today.
        val now = at(2026, 7, 2, 8, 0)
        assertEquals(at(2026, 7, 2, 9, 0), ReminderScheduler.nextWeeklyTrigger(4, 9, 0, now))
    }

    @Test fun `weekly trigger same day passed time rolls a full week`() {
        // Thursday 10:00 → next Thursday 09:00.
        val now = at(2026, 7, 2, 10, 0)
        assertEquals(at(2026, 7, 9, 9, 0), ReminderScheduler.nextWeeklyTrigger(4, 9, 0, now))
    }

    @Test fun `weekly trigger wraps past the weekend`() {
        // Thursday → Monday (1) 09:00 = 2026-07-06.
        val now = at(2026, 7, 2, 9, 0)
        assertEquals(at(2026, 7, 6, 9, 0), ReminderScheduler.nextWeeklyTrigger(1, 9, 0, now))
    }

    @Test fun `weekly trigger handles sunday as day 7`() {
        // Thursday → Sunday (7) 20:30 = 2026-07-05.
        val now = at(2026, 7, 2, 9, 0)
        assertEquals(at(2026, 7, 5, 20, 30), ReminderScheduler.nextWeeklyTrigger(7, 20, 30, now))
    }

    // ── F6 changelog window ────────────────────────────────────────────────────────────────────

    @Test fun `entriesSince returns only newer versions, newest first, capped`() {
        val since56 = Changelog.entriesSince(56)
        assertTrue(since56.isNotEmpty())
        assertTrue(since56.all { it.versionCode > 56 })
        assertEquals(since56.map { it.versionCode }.sortedDescending(), since56.map { it.versionCode })
        assertTrue(Changelog.entriesSince(0).size <= 3)
    }

    @Test fun `entriesSince is empty when up to date`() {
        val newest = Changelog.ENTRIES.maxOf { it.versionCode }
        assertTrue(Changelog.entriesSince(newest).isEmpty())
    }
}
