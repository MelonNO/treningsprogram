package com.migul.treningsprogram

import com.migul.treningsprogram.data.db.entity.PlannedExercise
import com.migul.treningsprogram.domain.RegeneratePlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B09: mid-week regenerate preserves logged days. Verifies the pure preserve rule: a day with ≥1
 * logged exercise is detected (a single logged exercise locks the day), the locked content is
 * gathered for AI context, and the "nothing left to regenerate" guard.
 *
 * The data-deletion guarantee is structural (the persistence path only ever touches planned_exercises
 * rows for NON-logged days; logged sets/history live in other tables), and is asserted at the
 * repository/flow level — this test pins the day-selection logic feeding it.
 */
class B09RegeneratePreserveTest {

    private var nextId = 1L
    private fun ex(day: Int, name: String, logged: Boolean = false) = PlannedExercise(
        id = nextId++,
        weekStart = 0L,
        dayOfWeek = day,
        orderInDay = 0,
        exerciseName = name,
        sets = 3,
        targetReps = "8-12",
        targetWeightKg = 0f,
        isLogged = logged
    )

    @Test fun `a single logged exercise locks its whole day`() {
        val plan = listOf(
            ex(3, "Back Squat", logged = true),  // one logged exercise on Wed
            ex(3, "Romanian Deadlift", logged = false),
            ex(1, "Bench Press", logged = false),
            ex(5, "Pull-up", logged = false)
        )
        assertEquals(setOf(3), RegeneratePlanner.loggedDays(plan))
    }

    @Test fun `Wednesday-only example - only Wed is preserved`() {
        // The brief's worked example: only Wednesday logged ⇒ regenerate all days except Wednesday.
        val plan = listOf(
            ex(1, "Bench Press"),
            ex(3, "Squat", logged = true),
            ex(5, "Deadlift"),
            ex(6, "Overhead Press")
        )
        val logged = RegeneratePlanner.loggedDays(plan)
        assertEquals(setOf(3), logged)
        // Every other planned day is NOT preserved (will be regenerated).
        val allDays = plan.map { it.dayOfWeek }.toSet()
        assertEquals(setOf(1, 5, 6), allDays - logged)
    }

    @Test fun `earlier-in-week unlogged days are not preserved`() {
        // Mon has a plan but nothing logged; Wed is logged. Mon must regenerate even though earlier.
        val plan = listOf(
            ex(1, "Bench Press", logged = false),
            ex(3, "Squat", logged = true)
        )
        assertEquals(setOf(3), RegeneratePlanner.loggedDays(plan))
        assertFalse(1 in RegeneratePlanner.loggedDays(plan))
    }

    @Test fun `locked exercises include every row on a logged day`() {
        val plan = listOf(
            ex(3, "Squat", logged = true),
            ex(3, "RDL", logged = false),       // same (logged) day → still locked context
            ex(3, "Leg Curl", logged = false),
            ex(1, "Bench Press", logged = false)
        )
        val locked = RegeneratePlanner.lockedExercises(plan)
        assertEquals(3, locked.size)
        assertTrue(locked.all { it.dayOfWeek == 3 })
        assertEquals(setOf("Squat", "RDL", "Leg Curl"), locked.map { it.exerciseName }.toSet())
    }

    @Test fun `no logged days means nothing is preserved`() {
        val plan = listOf(ex(1, "Bench"), ex(3, "Squat"), ex(5, "Deadlift"))
        assertEquals(emptySet<Int>(), RegeneratePlanner.loggedDays(plan))
        assertTrue(RegeneratePlanner.lockedExercises(plan).isEmpty())
    }

    // ── nothingToRegenerate guard ────────────────────────────────────────────────────────────────

    @Test fun `regenerate is a no-op only when logged days cover the whole week`() {
        assertTrue(RegeneratePlanner.nothingToRegenerate(loggedDayCount = 4, daysPerWeek = 4))
        assertTrue(RegeneratePlanner.nothingToRegenerate(loggedDayCount = 5, daysPerWeek = 4))
        assertFalse(RegeneratePlanner.nothingToRegenerate(loggedDayCount = 3, daysPerWeek = 4))
        assertFalse(RegeneratePlanner.nothingToRegenerate(loggedDayCount = 0, daysPerWeek = 4))
        assertFalse(RegeneratePlanner.nothingToRegenerate(loggedDayCount = 0, daysPerWeek = 0))
    }

    // ── persistence DECISION: logged data is never deleted/overwritten (real production seam) ───────
    // These exercise the exact pure functions WorkoutRepository.savePlanPreservingLoggedDays runs on.

    @Test fun `a logged day is never in the set of days to clear`() {
        // Only Wed (3) logged ⇒ the regenerate clears Mon,Tue,Thu,Fri,Sat,Sun but NEVER Wed.
        val clear = RegeneratePlanner.daysToReplace(loggedDays = setOf(3))
        assertEquals(listOf(1, 2, 4, 5, 6, 7), clear)
        assertFalse("Wed must never be cleared", 3 in clear)
    }

    @Test fun `multiple logged days are all excluded from the clear set`() {
        val clear = RegeneratePlanner.daysToReplace(loggedDays = setOf(1, 3, 6))
        assertEquals(listOf(2, 4, 5, 7), clear)
        assertTrue(setOf(1, 3, 6).none { it in clear })
    }

    @Test fun `cleared days plus logged days always cover the whole week exactly once`() {
        // No day is both cleared and preserved, and every weekday is accounted for.
        for (logged in listOf(emptySet(), setOf(3), setOf(1, 2), setOf(2, 4, 6, 7))) {
            val clear = RegeneratePlanner.daysToReplace(logged).toSet()
            assertTrue("overlap between cleared and logged", (clear intersect logged).isEmpty())
            assertEquals((1..7).toSet(), clear + logged)
        }
    }

    @Test fun `the model's exercises on a logged day are dropped, never persisted over it`() {
        // The model (wrongly) emitted a row on logged Wed (3) plus rows on Mon and Fri. Only Mon/Fri
        // are persisted; the Wed row is dropped so the real logged Wed plan can never be overwritten.
        val modelOutput = listOf(
            ex(1, "New Bench"),
            ex(3, "Model Squat"),   // collides with logged Wed
            ex(5, "New Deadlift")
        )
        val persisted = RegeneratePlanner.exercisesToPersist(modelOutput, loggedDays = setOf(3))
        assertEquals(setOf(1, 5), persisted.map { it.dayOfWeek }.toSet())
        assertTrue("no exercise on the logged day may be persisted", persisted.none { it.dayOfWeek == 3 })
        assertFalse(persisted.any { it.exerciseName == "Model Squat" })
    }

    @Test fun `with no logged days every regenerated row is persisted (equivalent to full replace)`() {
        val modelOutput = listOf(ex(1, "A"), ex(3, "B"), ex(5, "C"))
        val persisted = RegeneratePlanner.exercisesToPersist(modelOutput, loggedDays = emptySet())
        assertEquals(modelOutput.size, persisted.size)
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), RegeneratePlanner.daysToReplace(emptySet()))
    }
}
