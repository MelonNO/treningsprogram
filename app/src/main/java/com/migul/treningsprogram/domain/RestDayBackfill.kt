package com.migul.treningsprogram.domain

import com.migul.treningsprogram.data.db.entity.WorkoutSession

/**
 * Pure, Android-free core of the "auto-log a rest day when a day passes with nothing logged" feature
 * (so the date-window math and REST-vs-MISSED classification are fully unit-testable on the JVM).
 *
 * Days are represented as **epoch-days** (`Long`) — whole local days since the Unix epoch, exactly as
 * produced by `java.time.LocalDate.toEpochDay()`. Working in epoch-days makes the window arithmetic
 * day-boundary-correct and DST-proof; the repository converts millis ⇄ epoch-day with `LocalDate`.
 * Weekdays are 1 = Monday … 7 = Sunday, matching the rest of the app
 * (e.g. [TrainingDaySelection] and [com.migul.treningsprogram.data.db.entity.PlannedExercise.dayOfWeek]).
 */
object RestDayBackfill {

    /**
     * First epoch-day eligible for backfill (inclusive): one day after the later of the last logged
     * workout and the feature's first-run date. The feature-first-run floor is what stops the very
     * first launch after the update from retroactively inventing rest/missed days for the whole period
     * before the feature existed; once the user logs a workout, that workout becomes the floor instead.
     */
    fun windowStartEpoch(lastLoggedEpoch: Long?, featureFirstRunEpoch: Long): Long =
        maxOf(lastLoggedEpoch ?: Long.MIN_VALUE, featureFirstRunEpoch) + 1

    /**
     * Every epoch-day that should be auto-logged on this run, ascending. The window runs from
     * [windowStartEpoch] up to and INCLUDING yesterday ([todayEpoch] − 1); **today is always excluded**
     * (the user may still log it). Idempotent: any day already present in [existingRecordEpochs] — a
     * day that already has ANY session of any kind (workout, in-progress, rest, or missed) — is
     * skipped, so re-running on every launch never creates duplicates.
     *
     * @param todayEpoch local epoch-day of "now".
     * @param lastLoggedEpoch local epoch-day of the most recent real (logged) workout, or null if none.
     * @param featureFirstRunEpoch local epoch-day the feature first ran (persisted once).
     * @param existingRecordEpochs local epoch-days that already have a session row of any kind.
     */
    fun daysToFill(
        todayEpoch: Long,
        lastLoggedEpoch: Long?,
        featureFirstRunEpoch: Long,
        existingRecordEpochs: Set<Long>
    ): List<Long> {
        val start = windowStartEpoch(lastLoggedEpoch, featureFirstRunEpoch)
        val lastDay = todayEpoch - 1 // yesterday — today is always excluded
        if (start > lastDay) return emptyList()
        return (start..lastDay).filter { it !in existingRecordEpochs }
    }

    /**
     * Classifies an empty past [weekday] as a rest day vs a missed (scheduled) workout, returning
     * [WorkoutSession.KIND_REST] or [WorkoutSession.KIND_MISSED].
     *
     *  - REST-DAY mode ([restDays] non-empty): a weekday is a training day iff it is NOT a rest day
     *    (see [TrainingDaySelection.trainingDaysFrom]). An empty rest day ⇒ REST; an empty training
     *    day ⇒ MISSED.
     *  - COUNT mode ([restDays] empty): there is no fixed weekday→training mapping, so the best
     *    available signal is used — the set of weekdays the active plan actually assigned exercises to
     *    ([plannedTrainingWeekdays], from `planned_exercises.dayOfWeek`). A weekday the plan trains ⇒
     *    MISSED; anything else ⇒ REST. If the plan signal can't determine it (no plan ⇒ empty set),
     *    the day defaults to REST, never MISSED.
     */
    fun classify(
        weekday: Int,
        restDays: Set<Int>,
        plannedTrainingWeekdays: Set<Int>
    ): String =
        if (restDays.isNotEmpty()) {
            if (weekday in restDays) WorkoutSession.KIND_REST else WorkoutSession.KIND_MISSED
        } else {
            if (weekday in plannedTrainingWeekdays) WorkoutSession.KIND_MISSED else WorkoutSession.KIND_REST
        }
}
