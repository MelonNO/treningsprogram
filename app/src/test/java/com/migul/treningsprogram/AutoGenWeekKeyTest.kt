package com.migul.treningsprogram

import com.migul.treningsprogram.data.repository.autoGenWeekKey
import com.migul.treningsprogram.data.repository.thisMonday
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale

/**
 * Guards G2: the auto-generate "which week did I last generate" marker must be
 * locale-independent. It previously used SimpleDateFormat("yyyy-'W'ww", default locale),
 * whose week numbering depends on the locale's first-day-of-week and
 * minimal-days-in-first-week, so the week boundary (and thus when auto-gen re-fires)
 * could differ between users / devices. The key is now derived from thisMonday().
 */
class AutoGenWeekKeyTest {

    private val original: Locale = Locale.getDefault()

    @Before fun setUp() { /* each test sets its own locale */ }

    @After fun tearDown() { Locale.setDefault(original) }

    @Test fun keyIsDerivedFromThisMonday() {
        assertEquals("wk-${thisMonday()}", autoGenWeekKey())
    }

    @Test fun keyIsStableAcrossLocales_withDifferentWeekConventions() {
        // US: week starts Sunday, minimalDaysInFirstWeek = 1.
        Locale.setDefault(Locale.US)
        val us = autoGenWeekKey()
        // France: week starts Monday, minimalDaysInFirstWeek = 4 — a locale that, under the
        // old SimpleDateFormat("ww") scheme, frequently produced a different week number.
        Locale.setDefault(Locale.FRANCE)
        val fr = autoGenWeekKey()
        // Arabic (Saudi): week starts Saturday — another first-day-of-week.
        Locale.setDefault(Locale("ar", "SA"))
        val sa = autoGenWeekKey()

        assertEquals("US vs FR week key must match", us, fr)
        assertEquals("US vs SA week key must match", us, sa)
    }

    @Test fun keyFormatIsTheMondayEpoch_noLocaleWeekToken() {
        Locale.setDefault(Locale.US)
        val key = autoGenWeekKey()
        assertTrue("key should be 'wk-<millis>': $key", key.startsWith("wk-"))
        val millis = key.removePrefix("wk-").toLongOrNull()
        assertTrue("suffix must be a millis epoch", millis != null && millis > 0L)
    }

    @Test fun keyIsStableAcrossRepeatedCallsInSameWeek() {
        Locale.setDefault(Locale.US)
        assertEquals(autoGenWeekKey(), autoGenWeekKey())
    }
}
