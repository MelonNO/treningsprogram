package com.migul.treningsprogram

import com.migul.treningsprogram.data.db.entity.PlannedExercise
import com.migul.treningsprogram.domain.RegeneratePlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Item 9 — "Generate now" from Settings (Training Profile + AI & Program) must PRESERVE already-logged
 * days instead of overwriting them, reusing the SAME keep-logged mechanism as the Program tab. This
 * pins the composition SettingsViewModel.doGenerate now performs: the days fed to the model as fixed
 * context ([RegeneratePlanner.lockedExercises]) and the days preserved by persistence
 * ([RegeneratePlanner.loggedDays] → savePlanPreservingLoggedDays) are mutually consistent, so a
 * logged day is never rebuilt or overwritten while the rest of the week rebalances around it.
 */
class Item9SettingsPreserveTest {

    private var nextId = 1L
    private fun ex(day: Int, name: String, logged: Boolean = false) = PlannedExercise(
        id = nextId++, weekStart = 0L, dayOfWeek = day, orderInDay = 0,
        exerciseName = name, sets = 3, targetReps = "8-12", targetWeightKg = 0f, isLogged = logged
    )

    @Test fun `settings generate preserves logged days and rebuilds the rest`() {
        // Mon + Wed already logged; Fri planned but not logged.
        val currentPlan = listOf(
            ex(1, "Bench", logged = true),
            ex(3, "Squat", logged = true),
            ex(3, "RDL", logged = false),   // same logged day → locked as context
            ex(5, "Deadlift", logged = false)
        )
        val loggedDays = RegeneratePlanner.loggedDays(currentPlan)
        val locked = RegeneratePlanner.lockedExercises(currentPlan)

        // The preserved days and the locked-context days agree.
        assertEquals(setOf(1, 3), loggedDays)
        assertEquals(loggedDays, locked.map { it.dayOfWeek }.toSet())

        // A model that (wrongly) re-emits the logged days is filtered so those days are never persisted.
        val modelOutput = listOf(
            ex(1, "Model Bench"),   // collides with logged Mon → dropped
            ex(3, "Model Squat"),   // collides with logged Wed → dropped
            ex(4, "New Row"),
            ex(5, "New Deadlift")
        )
        val persisted = RegeneratePlanner.exercisesToPersist(modelOutput, loggedDays)
        assertTrue("no logged day is rebuilt", persisted.none { it.dayOfWeek in loggedDays })
        assertEquals("only non-logged days rebalance", setOf(4, 5), persisted.map { it.dayOfWeek }.toSet())
    }

    @Test fun `first generation with nothing logged is a full fresh week`() {
        // Fresh install / empty week: no logged days ⇒ the Settings path behaves like a full replace.
        val currentPlan = emptyList<PlannedExercise>()
        val loggedDays = RegeneratePlanner.loggedDays(currentPlan)
        assertEquals(emptySet<Int>(), loggedDays)
        assertTrue(RegeneratePlanner.lockedExercises(currentPlan).isEmpty())
        val modelOutput = listOf(ex(1, "A"), ex(3, "B"), ex(5, "C"))
        assertEquals(modelOutput.size, RegeneratePlanner.exercisesToPersist(modelOutput, loggedDays).size)
    }
}
