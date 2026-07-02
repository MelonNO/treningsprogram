package com.migul.treningsprogram.domain

import com.migul.treningsprogram.data.db.entity.WorkoutSet
import com.migul.treningsprogram.domain.model.DailyChallenge

/**
 * R4 — THE single definition of how a challenge measures progress, shared by:
 *  - the completion award (DailyChallengeManager.completeChallenges),
 *  - the live in-workout progress line (LogWorkoutViewModel.challengeProgress),
 *  - the Home card's progress suffix.
 * One place ⇒ the preview can never disagree with the award (the old hardcoded id-switches could).
 *
 * Metrics ending in a SESSION_ scope look at one session's working sets only; WEEK_ metrics
 * accumulate across the ISO week in [DailyChallenge.progress] (updated at each completion).
 * All counting uses WORKING sets — warm-ups never advance a challenge (established convention).
 */
object ChallengeProgress {

    const val METRIC_COMPLETE = "complete"
    const val METRIC_SESSION_SETS = "session_sets"
    const val METRIC_SESSION_EXERCISES = "session_exercises"
    const val METRIC_SESSION_VOLUME = "session_volume"
    const val METRIC_MUSCLE = "muscle"
    const val METRIC_PR = "pr"
    const val METRIC_PR_EXERCISE = "pr_exercise"
    const val METRIC_WEEK_SETS = "week_sets"
    const val METRIC_WEEK_VOLUME = "week_volume"
    const val METRIC_WEEK_WORKOUTS = "week_workouts"

    fun isWeekMetric(metric: String?): Boolean = metric?.startsWith("week_") == true

    /** One session's contribution to [metric] (working sets only). */
    fun sessionValue(
        metric: String?,
        param: String,
        workingSets: List<WorkoutSet>,
        prExercises: List<String> = emptyList()
    ): Int = when (metric) {
        METRIC_COMPLETE, METRIC_WEEK_WORKOUTS -> if (workingSets.isNotEmpty()) 1 else 0
        METRIC_SESSION_SETS, METRIC_WEEK_SETS -> workingSets.size
        METRIC_SESSION_EXERCISES -> workingSets.map { it.exerciseName }.toSet().size
        METRIC_SESSION_VOLUME, METRIC_WEEK_VOLUME ->
            workingSets.sumOf { it.reps.toDouble() * it.weightKg }.toInt()
        METRIC_MUSCLE -> workingSets.count { it.muscleGroup.equals(param, ignoreCase = true) }
        METRIC_PR -> prExercises.size
        METRIC_PR_EXERCISE -> if (prExercises.any { it.equals(param, ignoreCase = true) }) 1 else 0
        else -> 0
    }

    /** The effective target (boolean goals behave as target 1). */
    fun targetOf(ch: DailyChallenge): Int = maxOf(ch.target, 1)

    /** Total progress counting past accumulation for week-scoped metrics. */
    fun totalProgress(ch: DailyChallenge, sessionValue: Int): Int =
        if (isWeekMetric(ch.metric)) ch.progress + sessionValue else sessionValue

    /**
     * The challenge's state after a completed session: week metrics fold the session into
     * [DailyChallenge.progress]; any metric completes when total progress reaches the target.
     */
    fun afterSession(
        ch: DailyChallenge,
        workingSets: List<WorkoutSet>,
        prExercises: List<String>
    ): DailyChallenge {
        if (ch.isCompleted) return ch
        val v = sessionValue(ch.metric, ch.param, workingSets, prExercises)
        val total = totalProgress(ch, v)
        return ch.copy(
            progress = if (isWeekMetric(ch.metric)) total else ch.progress,
            isCompleted = total >= targetOf(ch)
        )
    }

    /** Countable = shows an "n/target" readout (PR-style and boolean goals don't). */
    fun isCountable(ch: DailyChallenge): Boolean =
        ch.metric != null && ch.target > 1 &&
            ch.metric != METRIC_PR && ch.metric != METRIC_PR_EXERCISE

    /**
     * The live one-line progress readout for the workout screen (replaces the pre-R4 hardcoded
     * id-switch). PR state isn't known mid-session, so PR challenges stay "⬜" until completion —
     * same behavior as before.
     */
    fun liveLine(challenges: List<DailyChallenge>, workingSets: List<WorkoutSet>): String {
        if (challenges.isEmpty()) return ""
        return challenges.joinToString("   ") { ch ->
            val v = sessionValue(ch.metric, ch.param, workingSets)
            val total = totalProgress(ch, v)
            when {
                ch.isCompleted || (ch.metric != METRIC_PR && ch.metric != METRIC_PR_EXERCISE &&
                    ch.metric != null && total >= targetOf(ch)) -> "✅ ${ch.name}"
                isCountable(ch) -> "⬜ ${ch.name} ${total.coerceAtMost(targetOf(ch))}/${targetOf(ch)}"
                else -> "⬜ ${ch.name}"
            }
        }
    }

    /**
     * Home-card progress suffix: only week-scoped countables have meaningful progress OUTSIDE a
     * live session (session metrics reset every workout). Null = nothing to show.
     */
    fun homeSuffix(ch: DailyChallenge): String? =
        if (!ch.isCompleted && isWeekMetric(ch.metric) && isCountable(ch) && ch.progress > 0)
            "${ch.progress.coerceAtMost(targetOf(ch))}/${targetOf(ch)}"
        else null
}
