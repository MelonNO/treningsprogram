package com.migul.treningsprogram.domain

import com.migul.treningsprogram.data.MuscleClassifier
import com.migul.treningsprogram.data.db.entity.PlannedExercise

/**
 * Item 02 (training-data improvements 2026-08-03): missed-day muscle recovery within the week.
 *
 * Gap in the existing auto-rebalance mechanism: the app auto-logs past empty training days as MISSED
 * (v1.12.0, [RestDayBackfill]) but never notices that a missed day held the week's ONLY coverage of a
 * muscle group. Evidence from the user's real logs: the plan's single leg day fell on a missed day ⇒
 * an entire week with zero leg sets, invisible unless the user studies Stats.
 *
 * This object is the pure detection + offer-shaping half. Recovery is OFFERED, never silently
 * applied (flagged assumption A2 — flipping to silent auto-recovery is a one-line product decision):
 * the caller surfaces [Offer] as a dismissible card; accepting applies ONE of:
 *  - MOVE the missed day's whole plan to a free (unplanned) remaining day of the week — preferred,
 *    it preserves the session's duration and full coverage exactly (reuses the existing
 *    do-another-day move machinery), or
 *  - APPEND the missed muscles' key exercises (primary-first, capped at [APPEND_CAP]) to the last
 *    remaining planned day.
 *
 * No-nagging contract: a missed day whose muscles are still covered elsewhere in the week (already
 * trained earlier, or planned on a remaining day) produces NO offer. Days the user already logged are
 * never touched — the move targets an empty day and the append only ever ADDS rows to a future day
 * (same preservation rule as regen-preserves-logged). Weekdays are 1 = Monday … 7 = Sunday.
 */
object MissedMuscleRecovery {

    /** Max exercises the append path carries over. Adjustable in one place. */
    const val APPEND_CAP: Int = 3

    data class Offer(
        val weekStart: Long,
        /** The missed weekday whose muscle coverage is unrecovered. */
        val missedDay: Int,
        /** Broad muscle groups that would otherwise be zero for the week. */
        val muscles: List<String>,
        /** A free (no plan rows, not missed, not trained) remaining weekday to MOVE the plan to. */
        val moveTargetDay: Int?,
        /** The last remaining planned weekday to APPEND [appendRows] to (fallback path). */
        val appendTargetDay: Int?,
        /** The missed day's key exercises for the uncovered muscles (orderInDay order, capped). */
        val appendRows: List<PlannedExercise>
    ) {
        /** A stable per-miss key for "declining is remembered for that week". */
        val dismissKey: String get() = "$weekStart:$missedDay"
    }

    /**
     * Detects the first missed day of the week whose muscle group(s) appear on no other effective
     * day, and shapes the recovery offer. Returns null when there is nothing to recover (no missed
     * day zeroes a muscle, or the week has no remaining day to recover on).
     *
     * @param weekRows the current week's planned rows.
     * @param missedDays weekdays holding a MISSED session marker this week.
     * @param trainedDays weekdays with a completed REAL workout this week (their planned muscles
     *   count as realized coverage; they are never proposed as targets).
     * @param todayWeekday today's weekday; today counts as remaining unless already trained/missed.
     * @param handledKeys [Offer.dismissKey]s already accepted or declined — those misses are skipped
     *   (declining is remembered for the week), letting a LATER uncovered miss still surface.
     */
    fun detect(
        weekStart: Long,
        weekRows: List<PlannedExercise>,
        missedDays: Set<Int>,
        trainedDays: Set<Int>,
        todayWeekday: Int,
        handledKeys: Set<String> = emptySet()
    ): Offer? {
        if (missedDays.isEmpty() || weekRows.isEmpty()) return null
        val byDay = weekRows.groupBy { it.dayOfWeek }
        val plannedDays = byDay.keys

        fun musclesOf(day: Int): Set<String> =
            byDay[day].orEmpty()
                .map { MuscleClassifier.fromName(it.exerciseName) }
                .filter { it.isNotEmpty() && it != "Cardio" }
                .toSet()

        // Days whose planned muscles still count for the week: already trained, or still to come
        // (today or later, not missed).
        fun isEffective(day: Int): Boolean =
            day in trainedDays || (day >= todayWeekday && day !in missedDays && day !in trainedDays)

        for (missed in missedDays.sorted()) {
            if ("$weekStart:$missed" in handledKeys) continue // already offered — spent either way
            val missedRows = byDay[missed].orEmpty()
            if (missedRows.isEmpty()) continue
            val missedMuscles = musclesOf(missed)
            if (missedMuscles.isEmpty()) continue

            val coveredElsewhere = plannedDays
                .filter { it != missed && isEffective(it) }
                .flatMap { musclesOf(it) }
                .toSet()
            val uncovered = missedMuscles - coveredElsewhere
            if (uncovered.isEmpty()) continue // no nagging — coverage survives the miss

            // MOVE target: the earliest remaining weekday with NO plan rows that isn't missed or
            // already trained (a rest/unplanned slot the whole session can shift into).
            val moveTarget = (todayWeekday..7).firstOrNull { day ->
                day !in plannedDays && day !in missedDays && day !in trainedDays
            }
            // APPEND target: the LAST remaining planned day (most room for the week to absorb it).
            val appendTarget = (todayWeekday..7).filter { day ->
                day in plannedDays && day != missed && day !in missedDays && day !in trainedDays
            }.maxOrNull()
            if (moveTarget == null && appendTarget == null) return null // week is over — nothing to offer

            val appendRows = missedRows
                .filter { MuscleClassifier.fromName(it.exerciseName) in uncovered }
                .sortedBy { it.orderInDay }
                .take(APPEND_CAP)

            return Offer(
                weekStart = weekStart,
                missedDay = missed,
                muscles = uncovered.sorted(),
                moveTargetDay = moveTarget,
                appendTargetDay = appendTarget,
                appendRows = appendRows
            )
        }
        return null
    }
}
