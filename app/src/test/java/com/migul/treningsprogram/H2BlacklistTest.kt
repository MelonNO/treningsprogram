package com.migul.treningsprogram

import com.migul.treningsprogram.data.repository.buildBlacklistNames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * H2 — the generation EXERCISE BLACKLIST must list WHOLE exercise names (commas preserved) and contain
 * ONLY real exercise names — no shattered fragments, no day-header junk.
 *
 * The previous code rebuilt the blacklist by re-parsing the rendered previous-plan string and splitting
 * the text after each ":" on commas, which (1) split names containing a comma into meaningless fragments
 * ("Dumbbell Push Press (Seated, Alternating)" → "Dumbbell Push Press (Seated" + "Alternating)") and
 * (2) harvested the day-prefixed line after the "LAST GENERATED PROGRAM …:" header ("Mon: Barbell Bench
 * Press"). [buildBlacklistNames] takes the STRUCTURED names directly, so neither failure mode is possible.
 */
class H2BlacklistTest {

    @Test fun commaContainingNamesStayIntact() {
        val previousPlan = setOf(
            "Dumbbell Push Press (Seated, Alternating)",
            "Dumbbell Calf Raise on Step (Bilateral, Slow Tempo)"
        )
        val blacklist = buildBlacklistNames(recentExercises = emptySet(), previousPlanNames = previousPlan)

        // Whole names survive — commas preserved, names not split.
        assertTrue(blacklist.contains("Dumbbell Push Press (Seated, Alternating)"))
        assertTrue(blacklist.contains("Dumbbell Calf Raise on Step (Bilateral, Slow Tempo)"))
        // The shattered fragments the old comma-split produced must NOT appear.
        assertFalse(blacklist.contains("Dumbbell Push Press (Seated"))
        assertFalse(blacklist.contains("Alternating)"))
        assertFalse(blacklist.contains("Slow Tempo)"))
        assertFalse(blacklist.contains("Dumbbell Calf Raise on Step (Bilateral"))
        // Exactly the two real names, nothing else.
        assertEquals(2, blacklist.size)
    }

    @Test fun noHeaderOrDayPrefixJunkLeaksIn() {
        // Whatever the rendered text looked like, the blacklist is built from structured names only,
        // so a "Mon: …" header/annotation can never become an entry.
        val recent = setOf("Barbell Bench Press", "Barbell Squat")
        val previousPlan = setOf("Romanian Deadlift", "Lat Pulldown")
        val blacklist = buildBlacklistNames(recent, previousPlan)

        assertFalse(blacklist.any { it.startsWith("Mon:") })
        assertFalse(blacklist.any { it.contains(":") })
        assertTrue(blacklist.contains("Barbell Bench Press"))
        assertTrue(blacklist.contains("Romanian Deadlift"))
    }

    @Test fun mergesRecentAndPreviousDedupedAndSorted() {
        val recent = setOf("Barbell Squat", "Barbell Bench Press")
        val previousPlan = setOf("Barbell Bench Press", "Romanian Deadlift") // Bench overlaps
        val blacklist = buildBlacklistNames(recent, previousPlan)

        assertEquals(
            listOf("Barbell Bench Press", "Barbell Squat", "Romanian Deadlift"),
            blacklist.toList() // SortedSet → alphabetical, de-duplicated
        )
    }

    @Test fun trimsAndDropsBlankNames() {
        val blacklist = buildBlacklistNames(
            recentExercises = setOf("  Barbell Row  ", "", "   "),
            previousPlanNames = setOf("Barbell Row") // dup after trim
        )
        assertEquals(setOf("Barbell Row"), blacklist.toSet())
    }

    @Test fun emptyInputsYieldEmptyBlacklist() {
        assertTrue(buildBlacklistNames(emptySet(), emptySet()).isEmpty())
    }
}
