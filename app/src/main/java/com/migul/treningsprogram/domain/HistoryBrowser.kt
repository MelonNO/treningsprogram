package com.migul.treningsprogram.domain

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * QoL item 04 — the History sub-tab's month → week → day browser, extracted pure so the
 * grouping, day-state derivation, search/range filtering, and week/day summaries are
 * unit-testable off-device.
 *
 * Weeks are Monday-based logical weeks (DayBoundary), consistent with the rest of the app.
 * REST/MISSED placeholder sessions carry no sets and surface only as day STATES — they never
 * contribute exercises, PRs, or training counts (project invariant).
 *
 * Filtering (item requirement: search + date-range must survive):
 *  - the calendar range keeps a day when its logical epoch-day falls inside it (inclusive);
 *  - a non-blank query keeps a day when its logical date formatted as
 *    [HistorySearch.MATCH_PATTERN] ("dd MMM yyyy EEE") contains the query — the same date
 *    semantics the old Sessions list had — OR any exercise performed that day contains it
 *    (a strict capability superset of the old search).
 *  A week stays visible while any of its days matches; months follow their weeks.
 */
object HistoryBrowser {

    // ── Inputs (plain data, decoupled from Room entities) ─────────────────────────────────

    /** One History-timeline session row. [kind] null = real workout, else "REST"/"MISSED". */
    data class SessionRow(
        val id: Long,
        val dateMs: Long,
        val kind: String?,
        val durationMinutes: Int
    )

    /** One logged set (warm-ups included — the browser distinguishes them itself). */
    data class SetRow(
        val sessionId: Long,
        val exerciseName: String,
        val muscleGroup: String,
        val setNumber: Int,
        val reps: Int,
        val weightKg: Float,
        val isWarmup: Boolean,
        val loggedAtMs: Long = 0L
    )

    // ── Output model ───────────────────────────────────────────────────────────────────────

    enum class DayState { WORKOUT, REST, MISSED, EMPTY, FUTURE }

    /** What one exercise looked like within one session (performed reality, not plan). */
    data class ExerciseSummary(
        val name: String,
        val muscleGroup: String,
        val workingSets: Int,
        val warmupSets: Int,
        /** Best working set: heaviest weight, then most reps at that weight. */
        val topReps: Int,
        val topWeightKg: Float,
        /** Session-level PR flag per the ratified baseline rule (HistoryPrFlags). */
        val isPr: Boolean,
        /** Best working weight before this session (null = first-ever = baseline). */
        val priorMaxKg: Float?
    )

    data class SessionSummary(
        val sessionId: Long,
        val dateMs: Long,
        val durationMinutes: Int,
        val exercises: List<ExerciseSummary>
    )

    data class Day(
        val epochDay: Long,
        val state: DayState,
        /** "STR" / "RUN" / "MIX" for workout days (same vocabulary as the Program chips). */
        val type: String?,
        /** Real workout sessions only (REST/MISSED surface via [state]). */
        val sessions: List<SessionSummary>,
        /** Passes the active search + range filters (false for day rows with no entries). */
        val matches: Boolean
    )

    data class Week(
        val weekStartEpochDay: Long,
        /** Exactly 7 entries, Monday..Sunday. */
        val days: List<Day>,
        val workoutDays: Int,
        val restDays: Int,
        val missedDays: Int,
        val isCurrent: Boolean
    ) {
        val hasEntries: Boolean
            get() = days.any { it.state != DayState.EMPTY && it.state != DayState.FUTURE }
    }

    data class Month(val year: Int, val month: Int, val weeks: List<Week>)

    data class Model(
        /** Filtered browser list, newest month first, weeks newest first within a month. */
        val months: List<Month>,
        /** Every built week (unfiltered), for the week view, keyed by Monday epoch-day. */
        val weeksByStart: Map<Long, Week>,
        val hasAnyHistory: Boolean,
        val filterActive: Boolean
    )

    // ── Build ──────────────────────────────────────────────────────────────────────────────

    fun build(
        sessions: List<SessionRow>,
        sets: List<SetRow>,
        query: String,
        range: DateRangeFilter.Range?,
        todayEpochDay: Long,
        cutoffHour: Int = DayBoundary.cutoffHour,
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault()
    ): Model {
        val filterActive = query.isNotBlank() || range != null
        if (sessions.isEmpty()) return Model(emptyList(), emptyMap(), false, filterActive)

        val dateMsBySession = sessions.associate { it.id to it.dateMs }
        val setsBySession = sets.filter { it.sessionId in dateMsBySession }.groupBy { it.sessionId }

        // Session-level PR flags from weighted working sets, chronological per exercise.
        val prFlags = HistoryPrFlags.flag(
            sets.asSequence()
                .filter { !it.isWarmup && it.weightKg > 0f && it.sessionId in dateMsBySession }
                .groupBy { it.exerciseName to it.sessionId }
                .map { (key, group) ->
                    HistoryPrFlags.ExerciseSessionMax(
                        exerciseName = key.first,
                        sessionId = key.second,
                        dateMs = dateMsBySession.getValue(key.second),
                        maxWeightKg = group.maxOf { it.weightKg }
                    )
                }
        )

        val byDay = sessions.groupBy { DayBoundary.logicalEpochDay(it.dateMs, cutoffHour, zone) }

        // Weeks that contain at least one entry, plus the current week for orientation.
        val weekStarts = sortedSetOf<Long>()
        byDay.keys.forEach { weekStarts += mondayOf(it) }
        weekStarts += mondayOf(todayEpochDay)

        val dateFmt = DateTimeFormatter.ofPattern(HistorySearch.MATCH_PATTERN, locale)

        val weeks = weekStarts.map { ws ->
            val days = (0L..6L).map { offset ->
                buildDay(ws + offset, byDay, setsBySession, prFlags, todayEpochDay, query, range, dateFmt)
            }
            Week(
                weekStartEpochDay = ws,
                days = days,
                workoutDays = days.count { it.state == DayState.WORKOUT },
                restDays = days.count { it.state == DayState.REST },
                missedDays = days.count { it.state == DayState.MISSED },
                isCurrent = ws == mondayOf(todayEpochDay)
            )
        }.sortedByDescending { it.weekStartEpochDay }

        val visible = weeks.filter { w ->
            if (filterActive) w.days.any { it.matches } else w.hasEntries || w.isCurrent
        }
        val months = visible
            .groupBy { LocalDate.ofEpochDay(it.weekStartEpochDay).let { d -> d.year to d.monthValue } }
            .map { (ym, ws) -> Month(ym.first, ym.second, ws) }
            .sortedWith(compareByDescending<Month> { it.year }.thenByDescending { it.month })

        return Model(months, weeks.associateBy { it.weekStartEpochDay }, true, filterActive)
    }

    private fun buildDay(
        epochDay: Long,
        byDay: Map<Long, List<SessionRow>>,
        setsBySession: Map<Long, List<SetRow>>,
        prFlags: Map<Pair<String, Long>, HistoryPrFlags.Flag>,
        todayEpochDay: Long,
        query: String,
        range: DateRangeFilter.Range?,
        dateFmt: DateTimeFormatter
    ): Day {
        val rows = byDay[epochDay].orEmpty().sortedBy { it.dateMs }
        val workouts = rows.filter { it.kind == null || it.kind == "WORKOUT" }
            .map { s -> buildSessionSummary(s, setsBySession[s.id].orEmpty(), prFlags) }
        val state = when {
            workouts.isNotEmpty() -> DayState.WORKOUT
            rows.any { it.kind == "MISSED" } -> DayState.MISSED
            rows.any { it.kind == "REST" } -> DayState.REST
            epochDay > todayEpochDay -> DayState.FUTURE
            else -> DayState.EMPTY
        }
        val type = if (state != DayState.WORKOUT) null else {
            val groups = workouts.flatMap { it.exercises }.map { it.muscleGroup }
            val cardio = groups.any { it == "Cardio" }
            val other = groups.any { it != "Cardio" }
            when {
                cardio && other -> "MIX"
                cardio -> "RUN"
                else -> "STR"
            }
        }
        val matches = rows.isNotEmpty() &&
            (range == null || epochDay in range.startEpochDay..range.endEpochDay) &&
            (query.isBlank() ||
                LocalDate.ofEpochDay(epochDay).format(dateFmt).contains(query, ignoreCase = true) ||
                workouts.any { s -> s.exercises.any { it.name.contains(query, ignoreCase = true) } })
        return Day(epochDay, state, type, workouts, matches)
    }

    private fun buildSessionSummary(
        session: SessionRow,
        sets: List<SetRow>,
        prFlags: Map<Pair<String, Long>, HistoryPrFlags.Flag>
    ): SessionSummary {
        val exercises = sets.groupBy { it.exerciseName }
            .map { (name, group) ->
                val working = group.filter { !it.isWarmup }
                val top = working.maxWithOrNull(compareBy({ it.weightKg }, { it.reps }))
                    ?: group.maxWithOrNull(compareBy({ it.weightKg }, { it.reps }))
                val flag = prFlags[name to session.id]
                ExerciseSummary(
                    name = name,
                    muscleGroup = group.map { it.muscleGroup }.filter { it.isNotBlank() }
                        .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: "",
                    workingSets = working.size,
                    warmupSets = group.size - working.size,
                    topReps = top?.reps ?: 0,
                    topWeightKg = top?.weightKg ?: 0f,
                    isPr = flag?.isPr == true,
                    priorMaxKg = flag?.priorMaxKg
                )
            }
            // Preserve logging order where loggedAtMs exists; legacy (0) rows fall back to
            // name order after the timed ones.
            .sortedBy { it.name }
            .let { alpha ->
                val firstLogged = sets.filter { it.loggedAtMs > 0L }
                    .groupBy { it.exerciseName }
                    .mapValues { (_, g) -> g.minOf { it.loggedAtMs } }
                alpha.sortedBy { firstLogged[it.name] ?: Long.MAX_VALUE }
            }
        return SessionSummary(session.id, session.dateMs, session.durationMinutes, exercises)
    }

    // ── Small pure helpers the UI leans on (kept here so they are testable) ───────────────

    /** Monday of [epochDay]'s week (epoch day 0 = Thu 1970-01-01, hence the +3 phase). */
    fun mondayOf(epochDay: Long): Long = epochDay - Math.floorMod(epochDay + 3L, 7L)

    /** Default selected day when a week opens: latest past workout, else latest past entry. */
    fun defaultDay(week: Week, todayEpochDay: Long): Long =
        week.days.lastOrNull { it.epochDay <= todayEpochDay && it.state == DayState.WORKOUT }?.epochDay
            ?: week.days.lastOrNull {
                it.epochDay <= todayEpochDay && it.state != DayState.EMPTY && it.state != DayState.FUTURE
            }?.epochDay
            ?: week.days.lastOrNull { it.epochDay <= todayEpochDay }?.epochDay
            ?: week.weekStartEpochDay

    /** "This week" / "Week of 21 Jul". */
    fun weekTitle(week: Week, locale: Locale = Locale.getDefault()): String =
        if (week.isCurrent) "This week"
        else "Week of " + LocalDate.ofEpochDay(week.weekStartEpochDay)
            .format(DateTimeFormatter.ofPattern("d MMM", locale))

    /** "3 workouts · 2 rest · 1 missed" (zero parts omitted); "No entries" when nothing. */
    fun weekSummary(week: Week): String {
        val parts = mutableListOf<String>()
        if (week.workoutDays > 0) parts += "${week.workoutDays} workout" + if (week.workoutDays > 1) "s" else ""
        if (week.restDays > 0) parts += "${week.restDays} rest"
        if (week.missedDays > 0) parts += "${week.missedDays} missed"
        return if (parts.isEmpty()) "No entries" else parts.joinToString("  ·  ")
    }

    /** "July 2026" — the month group header. */
    fun monthLabel(month: Month, locale: Locale = Locale.getDefault()): String =
        LocalDate.of(month.year, month.month, 1)
            .format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))
}
