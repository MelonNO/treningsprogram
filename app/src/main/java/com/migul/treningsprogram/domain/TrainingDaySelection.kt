package com.migul.treningsprogram.domain

/**
 * B08: pure helpers for the two day-selection modes (no Android / DB dependency, so the whole
 * rest-day → training-day derivation is unit-testable on the JVM harness).
 *
 *  - REST-DAY mode (the new default): the user picks specific REST weekdays; training is planned on
 *    every remaining weekday and days/week is DERIVED from the selection.
 *  - COUNT mode (pre-B08): the user picks a NUMBER of training days and the AI chooses which
 *    weekdays are rest.
 *
 * Weekdays are 1 = Monday … 7 = Sunday throughout, matching [com.migul.treningsprogram.data.db.entity.PlannedExercise.dayOfWeek].
 */
object TrainingDaySelection {

    val ALL_DAYS: List<Int> = (1..7).toList()

    private val SHORT_NAMES = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    /** Short weekday label for messages, e.g. 1 → "Mon". */
    fun dayName(day: Int): String = SHORT_NAMES.getOrElse(day - 1) { "Day $day" }

    fun dayNames(days: Collection<Int>): String =
        days.sorted().joinToString(", ") { dayName(it) }

    /** Training weekdays = every weekday NOT chosen as rest, ascending. */
    fun trainingDaysFrom(restDays: Set<Int>): List<Int> =
        ALL_DAYS.filter { it !in restDays }

    /** Days/week derived from the rest-day selection (7 − rest days). */
    fun daysPerWeekFrom(restDays: Set<Int>): Int =
        trainingDaysFrom(restDays).size

    /** Parse a stored CSV like "6,7" into the rest-day set, ignoring blanks / junk / out-of-range. */
    fun parseRestDays(csv: String): Set<Int> =
        csv.split(",").mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..7 }.toSet()

    /** Canonical CSV for storage, ascending (empty set → ""). */
    fun formatRestDays(restDays: Set<Int>): String =
        restDays.filter { it in 1..7 }.sorted().joinToString(",")

    /** Days a generated plan put training on that are actually REST days (a B08 hard violation). */
    fun restDayViolations(plannedDays: Set<Int>, restDays: Set<Int>): Set<Int> =
        plannedDays intersect restDays

    /**
     * The rejection reason if [plannedDays] violates the pinned [restDays] — training was scheduled on
     * a rest day, OR a required (non-rest) training day was omitted — or null when the schedule
     * complies. Empty [restDays] ⇒ null (count mode imposes no day-placement constraint). Used by the
     * generator's deterministic, retryable rest-day check so a bad day-placement is fixed on retry.
     *
     * [exemptDays] are days that are PRESERVED regardless of the current rest-day setting (B09: days
     * the user already logged this week). They are excluded from the "trained on a rest day" check
     * (a day they already trained is kept even if it is now a rest day) and counted as already covered
     * for the "missing training day" check (their plan is preserved, not regenerated).
     */
    fun scheduleViolation(plannedDays: Set<Int>, restDays: Set<Int>, exemptDays: Set<Int> = emptySet()): String? {
        if (restDays.isEmpty()) return null
        val expected = trainingDaysFrom(restDays).toSet()
        val onRest = restDayViolations(plannedDays - exemptDays, restDays)
        val missing = expected - plannedDays - exemptDays
        return when {
            onRest.isNotEmpty() ->
                "Scheduled training on rest day(s) ${dayNames(onRest)}. " +
                    "Train ONLY on ${dayNames(expected)} (those are the user's chosen training days)."
            missing.isNotEmpty() ->
                "No training scheduled on ${dayNames(missing)}. " +
                    "Every non-rest day must have a workout — output exactly these training days: ${dayNames(expected)}."
            else -> null
        }
    }

    /** The effective generation inputs for a given persisted state. */
    data class Effective(val restDays: Set<Int>, val daysPerWeek: Int) {
        /** True when specific rest days are pinned (the generator must honour them exactly). */
        val isRestDayMode: Boolean get() = restDays.isNotEmpty()
    }

    /**
     * Resolves the (restDays, daysPerWeek) a generation call should use from the persisted CSV.
     *
     *  - A valid REST-DAY selection (CSV non-blank, leaving 1..6 training days) is honoured and
     *    days/week is derived from it.
     *  - Anything else — blank CSV (count mode / existing users), or a degenerate selection that
     *    would leave 0 training days (all 7 chosen as rest) — falls back to COUNT mode with
     *    [fallbackDaysPerWeek]. This guarantees a misconfiguration can never produce a zero-day week
     *    and that existing users (blank CSV) keep their saved count behaviour.
     */
    fun effective(restDaysCsv: String, fallbackDaysPerWeek: Int): Effective {
        val rest = parseRestDays(restDaysCsv)
        val derived = daysPerWeekFrom(rest)
        if (rest.isNotEmpty() && derived in 1..6) {
            return Effective(rest, derived)
        }
        return Effective(emptySet(), fallbackDaysPerWeek.coerceIn(1, 7))
    }
}
