package com.migul.treningsprogram

import com.migul.treningsprogram.domain.TrainingDaySelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B08: rest-day-selection mode. Verifies the pure derivation the generator + UI rely on:
 * training days = complement of rest days, days/week derived, and the migration-safe fallback that
 * keeps existing (blank-CSV) users on their count and never yields a zero-day week.
 */
class B08RestDaySelectionTest {

    @Test fun `training days are the complement of rest days`() {
        assertEquals(listOf(1, 2, 3, 4, 5), TrainingDaySelection.trainingDaysFrom(setOf(6, 7)))
        assertEquals(listOf(1, 3, 5), TrainingDaySelection.trainingDaysFrom(setOf(2, 4, 6, 7)))
        assertEquals(TrainingDaySelection.ALL_DAYS, TrainingDaySelection.trainingDaysFrom(emptySet()))
    }

    @Test fun `days per week is derived from rest selection`() {
        assertEquals(5, TrainingDaySelection.daysPerWeekFrom(setOf(6, 7)))
        assertEquals(3, TrainingDaySelection.daysPerWeekFrom(setOf(2, 4, 6, 7)))
        assertEquals(7, TrainingDaySelection.daysPerWeekFrom(emptySet()))
    }

    @Test fun `csv round-trips and tolerates junk`() {
        assertEquals(setOf(6, 7), TrainingDaySelection.parseRestDays("6,7"))
        assertEquals(setOf(2, 5), TrainingDaySelection.parseRestDays(" 2 , 5 "))
        assertEquals(emptySet<Int>(), TrainingDaySelection.parseRestDays(""))
        // out-of-range / non-numeric ignored
        assertEquals(setOf(3), TrainingDaySelection.parseRestDays("3,9,0,x,-2"))
        assertEquals("6,7", TrainingDaySelection.formatRestDays(setOf(7, 6)))
        assertEquals("", TrainingDaySelection.formatRestDays(emptySet()))
    }

    @Test fun `rest-day violations are the planned days that fall on rest days`() {
        // Plan scheduled training on Mon, Wed, Sat — Sat (6) is a rest day → violation.
        assertEquals(setOf(6), TrainingDaySelection.restDayViolations(setOf(1, 3, 6), setOf(6, 7)))
        assertEquals(emptySet<Int>(), TrainingDaySelection.restDayViolations(setOf(1, 3, 5), setOf(6, 7)))
    }

    // ── scheduleViolation(): the generator's deterministic rest-day gate ──────────────────────────

    @Test fun `rest day is honored - a compliant plan passes`() {
        // Rest = Sat,Sun ⇒ training must be Mon..Fri. A plan on exactly Mon..Fri complies.
        assertNull(TrainingDaySelection.scheduleViolation(setOf(1, 2, 3, 4, 5), setOf(6, 7)))
    }

    @Test fun `training scheduled on a rest day is rejected`() {
        val reason = TrainingDaySelection.scheduleViolation(setOf(1, 2, 3, 4, 6), setOf(6, 7))
        assertNotNull(reason)
        assertTrue(reason!!.contains("rest day"))
        assertTrue(reason.contains("Sat"))
    }

    @Test fun `omitting a required training day is rejected`() {
        // Trains only Mon..Thu but Fri (a non-rest day) must also have a workout.
        val reason = TrainingDaySelection.scheduleViolation(setOf(1, 2, 3, 4), setOf(6, 7))
        assertNotNull(reason)
        assertTrue(reason!!.contains("Fri"))
    }

    @Test fun `count mode imposes no day-placement constraint`() {
        // Empty rest days ⇒ AI chooses ⇒ any day set complies.
        assertNull(TrainingDaySelection.scheduleViolation(setOf(2, 4, 6), emptySet()))
    }

    @Test fun `a logged day on a now-rest day is exempt (B09 + B08 compose)`() {
        // Rest = Sat,Sun. The user logged Sat (6) BEFORE marking it a rest day, so it is locked/exempt.
        // Training on Mon..Fri plus the echoed/locked Sat must NOT be rejected.
        assertNull(
            TrainingDaySelection.scheduleViolation(
                plannedDays = setOf(1, 2, 3, 4, 5, 6),
                restDays = setOf(6, 7),
                exemptDays = setOf(6)
            )
        )
        // A NON-exempt training day on a rest day is still rejected.
        assertNotNull(
            TrainingDaySelection.scheduleViolation(
                plannedDays = setOf(1, 2, 3, 4, 5, 7),
                restDays = setOf(6, 7),
                exemptDays = setOf(6)
            )
        )
    }

    @Test fun `a logged training day counts as covered, not missing`() {
        // Rest = Sat,Sun ⇒ train Mon..Fri. Wed (3) is logged (exempt) and not re-output by the model,
        // but it must count as covered — only Wed missing from the regenerated set is fine.
        assertNull(
            TrainingDaySelection.scheduleViolation(
                plannedDays = setOf(1, 2, 4, 5), // model regenerated Mon,Tue,Thu,Fri (not Wed)
                restDays = setOf(6, 7),
                exemptDays = setOf(3)            // Wed preserved
            )
        )
    }

    // ── effective(): mode resolution + migration safety ──────────────────────────────────────────

    @Test fun `valid rest selection is honoured and derives days per week`() {
        val eff = TrainingDaySelection.effective(restDaysCsv = "6,7", fallbackDaysPerWeek = 4)
        assertTrue(eff.isRestDayMode)
        assertEquals(setOf(6, 7), eff.restDays)
        assertEquals(5, eff.daysPerWeek) // derived, NOT the fallback 4
    }

    @Test fun `blank csv falls back to count mode with the saved days per week`() {
        // Existing-user migration: blank CSV ⇒ keep count behaviour.
        val eff = TrainingDaySelection.effective(restDaysCsv = "", fallbackDaysPerWeek = 4)
        assertFalse(eff.isRestDayMode)
        assertEquals(emptySet<Int>(), eff.restDays)
        assertEquals(4, eff.daysPerWeek)
    }

    @Test fun `all-seven-rest degenerate selection cannot produce a zero-day week`() {
        val eff = TrainingDaySelection.effective(restDaysCsv = "1,2,3,4,5,6,7", fallbackDaysPerWeek = 3)
        assertFalse(eff.isRestDayMode)
        assertEquals(3, eff.daysPerWeek) // safe fallback, never 0
    }

    @Test fun `fallback is clamped into 1 to 7`() {
        assertEquals(1, TrainingDaySelection.effective("", 0).daysPerWeek)
        assertEquals(7, TrainingDaySelection.effective("", 99).daysPerWeek)
    }
}
