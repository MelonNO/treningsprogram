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
        val monday = thisMonday()
        val sunday = monday + 6L * 24 * 60 * 60 * 1000
        val now = System.currentTimeMillis()
        assertTrue("now should fall within Mon-Sun window", now in monday..sunday + 24 * 60 * 60 * 1000)
    }
}
