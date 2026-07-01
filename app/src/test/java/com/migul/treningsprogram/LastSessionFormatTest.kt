package com.migul.treningsprogram

import com.migul.treningsprogram.data.db.entity.WorkoutSet
import com.migul.treningsprogram.ui.log.LastSessionFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Item 8 — presentation polish only; verifies the cleaner formatting still conveys the same data. */
class LastSessionFormatTest {

    private fun set(reps: Int, weight: Float, n: Int = 1) =
        WorkoutSet(sessionId = 1, exerciseName = "Bench Press", setNumber = n, reps = reps, weightKg = weight)

    @Test fun emptyIsBlank() {
        assertEquals("", LastSessionFormat.line(emptyList()))
    }

    @Test fun uniformSetsCollapse() {
        val sets = listOf(set(8, 60f, 1), set(8, 60f, 2), set(8, 60f, 3))
        assertEquals("Last time  ·  3 sets  ·  8 × 60 kg", LastSessionFormat.line(sets))
    }

    @Test fun mixedSetsListedPerSet() {
        val sets = listOf(set(8, 60f, 1), set(7, 62.5f, 2))
        val line = LastSessionFormat.line(sets)
        assertTrue(line, line.startsWith("Last time  ·  "))
        assertTrue(line, line.contains("8 × 60 kg"))
        assertTrue(line, line.contains("7 × 62.5 kg"))
    }

    @Test fun bodyweightShownAsBw() {
        val sets = listOf(set(12, 0f, 1))
        assertEquals("Last time  ·  12 × BW", LastSessionFormat.line(sets))
    }

    @Test fun singleSet_notCollapsed() {
        val sets = listOf(set(5, 100f, 1))
        assertEquals("Last time  ·  5 × 100 kg", LastSessionFormat.line(sets))
    }
}
