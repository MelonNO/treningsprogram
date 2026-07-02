package com.migul.treningsprogram.domain

/**
 * R1 — schedule-aware streak: the streak measures STICKING TO THE PLAN, not training every
 * calendar day.
 *
 *  - A completed workout on a new logical day extends the chain (+1) as long as no planned
 *    training day was MISSED in between.
 *  - Planned rest days are NEUTRAL: they neither extend nor break the chain. So are days with
 *    no session row at all (pre-feature history, per A-R2) — gaps never break by themselves.
 *  - A MISSED day (an auto-logged, scheduled-but-empty day — [com.migul.treningsprogram.data.db
 *    .entity.WorkoutSession.KIND_MISSED]) BREAKS the chain.
 *  - Two workouts on the same logical day count the day once.
 *
 * Pure day-walk math over logical epoch-days ([DayBoundary.logicalEpochDay]) so it is fully
 * unit-testable off-device and shared verbatim by the live incremental award
 * (GamificationRepository) and the backup-merge replay (StatsRecomputer) — recompute parity.
 */
object StreakPolicy {

    /**
     * The streak value after completing a workout on [todayEpochDay].
     *
     * @param currentStreak the streak before this workout (0 = none/broken).
     * @param lastWorkoutEpochDay logical epoch-day of the previous workout, or null if none.
     * @param todayEpochDay logical epoch-day the completed workout belongs to.
     * @param missedEpochDays logical epoch-days that have a MISSED session (any superset is fine —
     *        only days strictly between the last workout and today are consulted).
     */
    fun nextStreakOnWorkout(
        currentStreak: Int,
        lastWorkoutEpochDay: Long?,
        todayEpochDay: Long,
        missedEpochDays: Set<Long>
    ): Int = when {
        currentStreak <= 0 || lastWorkoutEpochDay == null -> 1
        todayEpochDay == lastWorkoutEpochDay              -> currentStreak
        isChainBroken(lastWorkoutEpochDay, todayEpochDay, missedEpochDays) -> 1
        else                                              -> currentStreak + 1
    }

    /**
     * True when a MISSED day lies strictly between [lastWorkoutEpochDay] and [uptoEpochDay]
     * (both exclusive). Used both for the workout-time walk and for display freshness
     * ("yesterday was missed ⇒ the streak shown today is already 0", with [uptoEpochDay] = today).
     */
    fun isChainBroken(
        lastWorkoutEpochDay: Long,
        uptoEpochDay: Long,
        missedEpochDays: Set<Long>
    ): Boolean = missedEpochDays.any { it > lastWorkoutEpochDay && it < uptoEpochDay }
}
