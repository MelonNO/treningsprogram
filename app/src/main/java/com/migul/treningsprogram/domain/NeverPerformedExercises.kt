package com.migul.treningsprogram.domain

/**
 * Item 03 (training-data improvements 2026-08-03, REDUCED scope — exercise-level only; the
 * weekday-adherence half was cut by the user and must not be built).
 *
 * Detects planned exercises that recur across consecutive planned weeks with ZERO logged
 * performances — while their planned siblings DO get logged. Evidence from the user's real logs: a
 * shoulder-press movement (the plan's only overhead-press slot) was planned week after week and
 * performed zero times across a ~6-week sample, so an entire movement pattern existed on paper only.
 *
 * The distinction that makes this a statement about the EXERCISE and not the day: a week only counts
 * toward the streak when the exercise's planned day actually HAD logged training activity (the user
 * showed up and did other things) but zero sets of this exercise. A week whose planned day was wholly
 * missed is NEUTRAL — it neither counts nor breaks the streak (skipping a whole session is not an
 * opinion about one exercise). Any logged performance of the exercise anywhere in a week BREAKS the
 * streak.
 *
 * Pure and Android-free: callers convert DB rows into the epoch-day shapes below (weekdays are
 * 1 = Monday … 7 = Sunday app-wide; epoch-days as produced by `LocalDate.toEpochDay()`).
 * [DEFAULT_MIN_WEEKS] is the adjustable detection threshold (builder-delegated default: 3).
 */
object NeverPerformedExercises {

    /** Streak length (counted weeks) at which an exercise is flagged. Adjustable in one place. */
    const val DEFAULT_MIN_WEEKS: Int = 3

    /** One planned week: the Monday's epoch-day plus its (dayOfWeek, exerciseName) plan rows. */
    data class PlannedWeek(
        val weekStartEpochDay: Long,
        val rows: List<PlannedRow>
    )

    data class PlannedRow(val dayOfWeek: Int, val exerciseName: String)

    private fun norm(name: String): String = name.trim().lowercase()

    /**
     * Returns the display names (most-recent planned casing) of exercises planned in ≥ [minWeeks]
     * consecutive counted weeks with zero logged performances, most recent plan order first.
     *
     * @param weeks planned weeks (any order; deduplicated by weekStart, evaluated newest-first).
     * @param performedByEpochDay epoch-day → normalized (lowercased, trimmed) exercise names with at
     *   least one logged set that day (completed real sessions only). A day PRESENT in this map had
     *   training activity; an absent day had none.
     * @param todayEpochDay only planned days strictly BEFORE today are judged (today may still be
     *   trained).
     */
    fun detect(
        weeks: List<PlannedWeek>,
        performedByEpochDay: Map<Long, Set<String>>,
        todayEpochDay: Long,
        minWeeks: Int = DEFAULT_MIN_WEEKS
    ): List<String> {
        if (weeks.isEmpty()) return emptyList()
        val ordered = weeks
            .distinctBy { it.weekStartEpochDay }
            .sortedByDescending { it.weekStartEpochDay }

        // Candidate names: everything planned in the MOST RECENT week (the suggestion must be
        // actionable against the current plan), keeping that week's original casing for display.
        val latest = ordered.first()
        val candidates = LinkedHashMap<String, String>() // norm -> display
        for (row in latest.rows) candidates.putIfAbsent(norm(row.exerciseName), row.exerciseName)

        val detected = mutableListOf<String>()
        for ((normName, display) in candidates) {
            var streak = 0
            for (week in ordered) {
                val plannedDays = week.rows.filter { norm(it.exerciseName) == normName }.map { it.dayOfWeek }
                // Not planned this week ⇒ the consecutive-planned-weeks chain is broken.
                if (plannedDays.isEmpty()) break

                val weekDays = week.weekStartEpochDay until week.weekStartEpochDay + 7
                val performedThisWeek = weekDays.any { day ->
                    performedByEpochDay[day]?.contains(normName) == true
                }
                if (performedThisWeek) break // a performance resets the streak

                // Planned days that have already passed AND had logged activity (session happened,
                // this exercise skipped). Whole-day misses / future days are NEUTRAL.
                val skippedOnActiveDay = plannedDays.any { d ->
                    val epoch = week.weekStartEpochDay + (d - 1)
                    epoch < todayEpochDay && !performedByEpochDay[epoch].isNullOrEmpty()
                }
                if (skippedOnActiveDay) {
                    streak++
                    if (streak >= minWeeks) break
                }
                // else: neutral week — continue without counting or breaking.
            }
            if (streak >= minWeeks) detected.add(display)
        }
        return detected
    }

    /**
     * The generation-prompt block feeding the signal into [names] — instructs REPLACEMENT that
     * preserves the skipped exercise's muscle/movement coverage, never silent deletion of the
     * stimulus. "" when there is nothing to report so the prompt stays byte-identical for users who
     * perform their planned exercises.
     */
    fun promptBlock(names: List<String>, minWeeks: Int = DEFAULT_MIN_WEEKS): String {
        if (names.isEmpty()) return ""
        return """
══════════════════════════════════════════
NEVER-PERFORMED PLANNED EXERCISES — REPLACE THEM, PRESERVING THEIR COVERAGE (HARD)
══════════════════════════════════════════
These exercises have been planned for $minWeeks+ consecutive weeks and the user has NEVER performed them — while completing the other exercises on those same days. The user is demonstrably not doing these:
${names.joinToString("\n") { "  • $it" }}
- Do NOT prescribe any exercise named above in this plan (the app rejects a plan that re-includes one).
- Each was carrying a training purpose. REPLACE it with a DIFFERENT exercise that preserves the same primary muscle(s) and movement pattern (e.g. an unperformed seated dumbbell shoulder press → a machine shoulder press, landmine press, or another overhead-press variant the user may actually do) — never simply delete the slot, and never let its muscle group's weekly coverage drop.
"""
    }
}
