package com.migul.treningsprogram

import com.migul.treningsprogram.data.preferences.isoWeekKey
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * Guards the WEEK_FMT half of G2: the weekly-challenge rotation key (DailyChallengeManager)
 * must be locale-independent. It previously used SimpleDateFormat("yyyy-'W'ww", default
 * locale), so both the week boundary AND the digit script depended on the device locale —
 * shifting which challenges appear (the key seeds the selection RNG) and when they reset.
 * isoWeekKey() now pins Monday-first / minimal-4-days week rules and Locale.ROOT.
 */
class DailyChallengeWeekKeyTest {

    private val original: Locale = Locale.getDefault()

    @After fun tearDown() { Locale.setDefault(original) }

    private fun date(iso: String) =
        SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).parse(iso)!!

    @Test fun keyIsIdenticalAcrossLocalesWithDifferentWeekConventions() {
        val d = date("2026-06-23")
        Locale.setDefault(Locale.US)            // Sunday-first, minimalDaysInFirstWeek = 1
        val us = isoWeekKey(d)
        Locale.setDefault(Locale.FRANCE)        // Monday-first, minimalDaysInFirstWeek = 4
        val fr = isoWeekKey(d)
        Locale.setDefault(Locale("ar", "SA"))   // Saturday-first + Arabic-Indic digits
        val ar = isoWeekKey(d)
        assertEquals(us, fr)
        assertEquals(us, ar)
    }

    @Test fun keyUsesLatinDigitsEvenUnderArabicLocale() {
        Locale.setDefault(Locale("ar", "SA"))
        val key = isoWeekKey(date("2026-06-23"))
        // \d matches ASCII 0-9 only — Arabic-Indic digits would fail this.
        assertTrue("expected ASCII yyyy-Www, got $key", key.matches(Regex("""\d{4}-W\d{2}""")))
    }

    @Test fun keyMatchesIsoWeekForKnownDate() {
        Locale.setDefault(Locale.US) // prove the result ignores the default locale
        val ld = LocalDate.of(2026, 6, 23)
        val expected = "%04d-W%02d".format(
            ld.get(WeekFields.ISO.weekBasedYear()),
            ld.get(WeekFields.ISO.weekOfWeekBasedYear())
        )
        assertEquals(expected, isoWeekKey(date("2026-06-23")))
    }
}
