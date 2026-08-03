package com.migul.treningsprogram

import com.migul.treningsprogram.data.repository.currentDayOfWeek
import com.migul.treningsprogram.data.repository.thisMonday
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgramViewModelTest {

    @Test fun currentDayOfWeek_isInValidRange() {
        val day = currentDayOfWeek()
        assertTrue("Expected 1..7, got $day", day in 1..7)
    }

    @Test fun thisMonday_isNotInFuture() {
        val monday = thisMonday()
        assertTrue("thisMonday() must not be in the future", monday <= System.currentTimeMillis())
    }

    @Test fun thisMonday_isBeforeOrEqualToNow() {
        val monday = thisMonday()
        // Monday epoch is always <= today
        assertTrue(monday <= System.currentTimeMillis())
    }

    @Test fun thisMonday_plus6DaysCoversCurrentDay() {
        // thisMonday() is keyed on the LOGICAL day (DayBoundary, default 04:00 cutoff): between
        // Sunday 24:00 and Monday 03:59 local it deliberately still returns LAST week's Monday.
        // The window therefore extends past Sunday midnight by the cutoff (fixed 2026-08-03 —
        // this assertion used to end at Monday 00:00 and failed when run early Monday morning).
        val monday = thisMonday()
        val weekEnd = monday + 7L * 24 * 60 * 60 * 1000 +
            com.migul.treningsprogram.domain.DayBoundary.cutoffHour * 60L * 60 * 1000
        val now = System.currentTimeMillis()
        assertTrue("now should fall within the logical Mon-Sun window", now in monday..weekEnd)
    }
}
