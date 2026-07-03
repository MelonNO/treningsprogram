package com.migul.treningsprogram.domain

import com.migul.treningsprogram.data.db.entity.Achievement
import com.migul.treningsprogram.data.db.entity.WorkoutSession
import com.migul.treningsprogram.data.db.entity.WorkoutSet
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * B7 — the monthly "Wrapped" recap: a pure derivation of one calendar month's story from
 * existing data. PRESENTATION ONLY — nothing here writes anything; XP/streaks/challenges/
 * stats are never touched by generating or viewing a Wrapped.
 *
 * Month attribution uses the app's LOGICAL day ([DayBoundary] — the known UTC-vs-logical
 * trap): a 01:00 session belongs to the previous logical day, and therefore possibly the
 * previous month. Placeholders (REST/MISSED) are never training: they feed only the
 * rest/missed counts. Warm-ups are excluded from every figure. The PR panel honors the
 * ratified baseline rule: an exercise first ever lifted THIS month has no prior best and is
 * NEVER a PR.
 *
 * Thin months degrade to fewer panels; a month with no real training builds to null
 * (caller shows nothing — never a page of zeros).
 */
object MonthlyWrapped {

    data class MonthKey(val year: Int, val month: Int) : Comparable<MonthKey> {
        override fun compareTo(other: MonthKey): Int =
            compareValuesBy(this, other, { it.year }, { it.month })

        val label: String
            get() = java.time.Month.of(month).getDisplayName(TextStyle.FULL, Locale.ENGLISH) +
                " $year"

        /** Stable prefs key, e.g. "2026-6". */
        val key: String get() = "$year-$month"
    }

    data class PrPanel(val exerciseName: String, val newKg: Float, val previousKg: Float)
    data class Improvement(val exerciseName: String, val fromE1rm: Double, val toE1rm: Double)
    data class Favorite(val exerciseName: String, val sessions: Int)
    data class BwPanel(val startKg: Float, val endKg: Float)

    data class Wrapped(
        val month: MonthKey,
        val sessions: Int,
        val missedDays: Int,
        val restDays: Int,
        val activeDays: Int,
        val totalSets: Int,
        val totalVolumeKg: Float,
        val totalMinutes: Int,
        val biggestPr: PrPanel?,
        val mostImproved: Improvement?,
        val favorite: Favorite?,
        val achievementsUnlocked: List<Achievement>,
        val bodyWeight: BwPanel?
    )

    private fun monthOf(ms: Long): MonthKey =
        DayBoundary.logicalDate(ms).let { MonthKey(it.year, it.monthValue) }

    /** The month whose Wrapped is "ready" now = the month before the current logical month. */
    fun readyMonthKey(nowMs: Long = System.currentTimeMillis()): MonthKey {
        val prev = DayBoundary.logicalDate(nowMs).minusMonths(1)
        return MonthKey(prev.year, prev.monthValue)
    }

    /**
     * Months with at least one real completed workout, newest first — the browsable history.
     * The CURRENT (unfinished) month is excluded: a Wrapped tells the story of an ended month.
     */
    fun availableMonths(
        sessions: List<WorkoutSession>,
        nowMs: Long = System.currentTimeMillis()
    ): List<MonthKey> {
        val current = monthOf(nowMs)
        return sessions
            .filter { it.isCompleted && !it.isPlaceholder }
            .map { monthOf(it.dateMs) }
            .distinct()
            .filter { it < current }
            .sortedDescending()
    }

    /**
     * Builds [month]'s Wrapped, or null when the month has no real completed workout
     * (never a page of zeros). [allSets] must span ALL history (the PR baseline needs
     * pre-month bests); month membership of a set follows its SESSION's logical date.
     */
    fun build(
        month: MonthKey,
        sessions: List<WorkoutSession>,
        allSets: List<WorkoutSet>,
        achievements: List<Achievement>,
        weighIns: List<RelativeStrength.WeighIn>
    ): Wrapped? {
        val monthStart = LocalDate.of(month.year, month.month, 1)
        val nextMonth = monthStart.plusMonths(1)

        fun inMonth(ms: Long): Boolean {
            val d = DayBoundary.logicalDate(ms)
            return !d.isBefore(monthStart) && d.isBefore(nextMonth)
        }
        fun beforeMonth(ms: Long): Boolean = DayBoundary.logicalDate(ms).isBefore(monthStart)

        val monthSessions = sessions.filter {
            it.isCompleted && !it.isPlaceholder && inMonth(it.dateMs)
        }
        if (monthSessions.isEmpty()) return null

        val sessionDateById = sessions.associate { it.id to it.dateMs }
        val monthSessionIds = monthSessions.map { it.id }.toSet()
        val working = allSets.filter { !it.isWarmup }
        val monthSets = working.filter { it.sessionId in monthSessionIds }

        // Headline figures.
        val totalVolume = monthSets.sumOf { it.reps.toDouble() * it.weightKg }.toFloat()
        val activeDays = monthSessions.map { DayBoundary.logicalEpochDay(it.dateMs) }.distinct().size
        val totalMinutes = monthSessions.sumOf { it.durationMinutes }
        val missed = sessions.count { it.isMissedDay && inMonth(it.dateMs) }
        val rest = sessions.count { it.isRestDay && inMonth(it.dateMs) }

        // Biggest PR (baseline rule: a prior best MUST exist and be strictly beaten).
        val biggestPr = monthSets
            .filter { it.weightKg > 0f }
            .groupBy { it.exerciseName }
            .mapNotNull { (name, sets) ->
                val monthMax = sets.maxOf { it.weightKg }
                val prevBest = working
                    .filter {
                        it.exerciseName == name && it.weightKg > 0f &&
                            beforeMonth(sessionDateById[it.sessionId] ?: return@filter false)
                    }
                    .maxOfOrNull { it.weightKg }
                    ?: return@mapNotNull null   // first-ever this month = baseline, never a PR
                if (monthMax > prevBest) PrPanel(name, monthMax, prevBest) else null
            }
            .maxByOrNull { it.newKg - it.previousKg }

        // Most-improved lift WITHIN the month: best-set e1RM of the first vs last in-month
        // session of that exercise (needs >= 2 in-month sessions and a positive delta).
        val mostImproved = monthSets
            .filter { it.weightKg > 0f }
            .groupBy { it.exerciseName }
            .mapNotNull { (name, sets) ->
                val bySession = sets.groupBy { it.sessionId }
                    .mapNotNull { (sid, s) ->
                        val date = sessionDateById[sid] ?: return@mapNotNull null
                        date to s.maxOf { Epley.estimate(it.weightKg, it.reps) }
                    }
                    .sortedBy { it.first }
                if (bySession.size < 2) return@mapNotNull null
                val from = bySession.first().second
                val to = bySession.last().second
                if (to > from) Improvement(name, from, to) else null
            }
            .maxByOrNull { it.toE1rm - it.fromE1rm }

        // Favorite = most in-month sessions (alphabetical tie-break for determinism).
        val favorite = monthSets
            .groupBy { it.exerciseName }
            .map { (name, sets) -> Favorite(name, sets.map { it.sessionId }.distinct().size) }
            .sortedWith(compareByDescending<Favorite> { it.sessions }.thenBy { it.exerciseName })
            .firstOrNull()

        val unlocked = achievements.filter {
            it.isUnlocked && it.unlockedAtMs > 0L && inMonth(it.unlockedAtMs)
        }

        val monthWeighIns = weighIns.filter { it.weightKg > 0f && inMonth(it.dateMs) }
            .sortedBy { it.dateMs }
        val bw = if (monthWeighIns.size >= 2) {
            BwPanel(monthWeighIns.first().weightKg, monthWeighIns.last().weightKg)
        } else null

        return Wrapped(
            month = month,
            sessions = monthSessions.size,
            missedDays = missed,
            restDays = rest,
            activeDays = activeDays,
            totalSets = monthSets.size,
            totalVolumeKg = totalVolume,
            totalMinutes = totalMinutes,
            biggestPr = biggestPr,
            mostImproved = mostImproved,
            favorite = favorite,
            achievementsUnlocked = unlocked,
            bodyWeight = bw
        )
    }
}
