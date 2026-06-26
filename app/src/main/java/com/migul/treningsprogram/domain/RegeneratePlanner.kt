package com.migul.treningsprogram.domain

import com.migul.treningsprogram.data.db.entity.PlannedExercise

/**
 * B09: pure helpers for the mid-week "regenerate, preserving logged days" flow (no Android / DB
 * dependency, so the preserve rule is unit-testable on the JVM harness).
 *
 * A day is LOGGED (preserved) when it has ≥1 planned exercise marked [PlannedExercise.isLogged] —
 * the app's existing "this day is done" signal (it drives the Program tab's green chips + week
 * progress, and is set on workout completion in LogWorkoutViewModel). A SINGLE logged exercise locks
 * the whole day (matching the brief: "not a completed workout").
 *
 * IMPORTANT: this layer only ever decides which PLANNED days to keep vs replace. Logged SETS and
 * workout HISTORY live in the separate workout_sessions / workout_sets tables and are never read or
 * written here or by the persistence path it feeds — so no regenerate can delete logged data.
 */
object RegeneratePlanner {

    /** Weekdays (1..7) that have ≥1 logged exercise in [weekPlan] — preserved as-is on regenerate. */
    fun loggedDays(weekPlan: List<PlannedExercise>): Set<Int> =
        weekPlan.filter { it.isLogged }.map { it.dayOfWeek }.toSet()

    /**
     * The already-trained rows to KEEP and to feed back to the AI as fixed context for rebalancing.
     * These are every row on a logged day (not only the rows whose own isLogged is true), so the AI
     * sees the full shape of what was trained that day.
     */
    fun lockedExercises(weekPlan: List<PlannedExercise>): List<PlannedExercise> {
        val logged = loggedDays(weekPlan)
        return weekPlan.filter { it.dayOfWeek in logged }
    }

    /**
     * True when there is nothing left to (re)generate because the user has already logged at least
     * as many days as the week is meant to contain. The caller should no-op with a friendly message
     * rather than burning an AI call and rewriting an already-complete week.
     *
     * Works in both modes: in rest-day mode [daysPerWeek] is the derived training-day count; in count
     * mode it is the chosen number. (loggedDayCount can only exceed daysPerWeek if the user trained on
     * extra days; that too means there is nothing the regenerate needs to add.)
     */
    fun nothingToRegenerate(loggedDayCount: Int, daysPerWeek: Int): Boolean =
        daysPerWeek > 0 && loggedDayCount >= daysPerWeek

    // ── The actual persistence DECISION (the seam savePlanPreservingLoggedDays runs on) ─────────────
    // Extracted as pure functions so the "logged data is never deleted/overwritten" guarantee is
    // tested against the REAL production logic (mirroring how the AiRepository seam is made testable),
    // not a hand-copied mirror.

    /**
     * The weekdays a preserve-logged regenerate will CLEAR and replace — every NON-logged weekday.
     * A logged day is NEVER in this set, so its planned rows (with isLogged + actuals) are never
     * deleted. The repository only ever issues deletes for these days, against the planned_exercises
     * table; logged SETS / sessions live in other tables and are never referenced.
     */
    fun daysToReplace(loggedDays: Set<Int>): List<Int> =
        (1..7).filter { it !in loggedDays }

    /**
     * The exercises a preserve-logged regenerate will actually persist: the regenerated rows that fall
     * on a NON-logged day. Any row the model emitted for a logged day is dropped here, so a logged
     * day's plan can never be overwritten by the regeneration.
     */
    fun exercisesToPersist(newExercises: List<PlannedExercise>, loggedDays: Set<Int>): List<PlannedExercise> =
        newExercises.filter { it.dayOfWeek !in loggedDays }
}
