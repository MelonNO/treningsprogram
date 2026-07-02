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
