package com.migul.treningsprogram.domain

import com.migul.treningsprogram.domain.model.DailyChallenge
import java.util.Random

/**
 * R4 — everything the user's training data can tell a challenge at draw time. Built once per
 * weekly draw by DailyChallengeManager from the DB; pure so the catalog is fully JVM-testable.
 */
data class ChallengeContext(
    /** Any completed workout exists at all (a PR needs a baseline to beat). */
    val hasHistory: Boolean = false,
    /** Total working sets logged last ISO week (Mon–Sun before the current week). */
    val lastWeekWorkingSets: Int = 0,
    /** Total working volume (kg) last ISO week. */
    val lastWeekVolumeKg: Int = 0,
    /** Average working volume per session over the recent (~4-week) window. */
    val avgSessionVolumeKg: Int = 0,
    /** Best working-set count in one session over the recent window. */
    val maxRecentSessionSets: Int = 0,
    /** Best working volume in one session over the recent window. */
    val maxRecentSessionVolumeKg: Int = 0,
    /** Days with ≥1 planned exercise in the current week's active-program plan. */
    val plannedDayCount: Int = 0,
    /** Weighted exercises with recent history (PR-able), plan-relevant first, capped + sorted. */
    val baselineExercises: List<String> = emptyList()
)

/**
 * R4 — the weekly-challenge pool and the deterministic draw.
 *
 * 26 templates: the 12 pre-R4 classics (same ids) + new statics + 9 ADAPTIVE templates whose
 * concrete targets come from the user's own training ([ChallengeContext]) at draw time, per
 * A-C3 sized challenging-but-reachable (last week + 0–10%, recent best + 1, …).
 *
 * Guarantees (brief ACs):
 *  - the draw is a pure function of (weekKey, ctx): stable within a week, different across weeks;
 *  - an unsatisfiable challenge for THIS user's current state is never drawn (eligibility filter);
 *  - computed targets are frozen into the drawn [DailyChallenge] and stated in its description.
 */
object ChallengeCatalog {

    const val CHALLENGES_PER_WEEK = 3

    data class Template(
        val id: String,
        val name: String,
        val bonusXp: Int,
        val metric: String,
        val adaptive: Boolean = false,
        val eligible: (ChallengeContext) -> Boolean = { true },
        val target: (ChallengeContext) -> Int = { 1 },
        val param: (ChallengeContext, Random) -> String = { _, _ -> "" },
        val describe: (target: Int, param: String) -> String
    )

    /** Round to the nearest 10 (volume targets shouldn't read as "1 237 kg"). */
    private fun round10(v: Int): Int = ((v + 5) / 10) * 10

    val TEMPLATES: List<Template> = listOf(
        // ── statics: the 12 pre-R4 classics (ids preserved) ─────────────────────────────────────
        Template("complete_workout", "Commitment", 100, ChallengeProgress.METRIC_COMPLETE,
            describe = { _, _ -> "Complete a workout this week" }),
        Template("sets_10", "Volume Day", 75, ChallengeProgress.METRIC_SESSION_SETS,
            target = { 10 }, describe = { t, _ -> "Log $t+ working sets in one session" }),
        Template("sets_15", "High Volume", 100, ChallengeProgress.METRIC_SESSION_SETS,
            target = { 15 }, describe = { t, _ -> "Log $t+ working sets in one session" }),
        Template("sets_20", "Max Effort", 150, ChallengeProgress.METRIC_SESSION_SETS,
            target = { 20 }, describe = { t, _ -> "Log $t+ working sets in one session" }),
        Template("set_pr", "Record Day", 150, ChallengeProgress.METRIC_PR,
            eligible = { it.hasHistory },
            describe = { _, _ -> "Set a personal record this week" }),
        Template("chest_day", "Push Day", 50, ChallengeProgress.METRIC_MUSCLE,
            param = { _, _ -> "Chest" }, describe = { _, _ -> "Log a chest exercise" }),
        Template("back_day", "Pull Day", 50, ChallengeProgress.METRIC_MUSCLE,
            param = { _, _ -> "Back" }, describe = { _, _ -> "Log a back exercise" }),
        Template("leg_day", "Leg Day", 75, ChallengeProgress.METRIC_MUSCLE,
            param = { _, _ -> "Legs" }, describe = { _, _ -> "Never skip leg day" }),
        Template("arms_day", "Pump Day", 50, ChallengeProgress.METRIC_MUSCLE,
            param = { _, _ -> "Arms" }, describe = { _, _ -> "Log an arms exercise" }),
        Template("core_day", "Core Day", 50, ChallengeProgress.METRIC_MUSCLE,
            param = { _, _ -> "Core" }, describe = { _, _ -> "Log a core exercise" }),
        Template("exercises_3", "Variety Pack", 75, ChallengeProgress.METRIC_SESSION_EXERCISES,
            target = { 3 }, describe = { t, _ -> "Log $t+ different exercises in one session" }),
        Template("exercises_5", "Full Body", 100, ChallengeProgress.METRIC_SESSION_EXERCISES,
            target = { 5 }, describe = { t, _ -> "Log $t+ different exercises in one session" }),
        // ── statics: new ─────────────────────────────────────────────────────────────────────────
        Template("sets_12", "Working Dozen", 85, ChallengeProgress.METRIC_SESSION_SETS,
            target = { 12 }, describe = { t, _ -> "Log $t+ working sets in one session" }),
        Template("shoulder_day", "Boulder Shoulders", 50, ChallengeProgress.METRIC_MUSCLE,
            param = { _, _ -> "Shoulders" }, describe = { _, _ -> "Log a shoulder exercise" }),
        Template("exercises_7", "The Explorer", 125, ChallengeProgress.METRIC_SESSION_EXERCISES,
            target = { 7 }, describe = { t, _ -> "Log $t+ different exercises in one session" }),
        Template("session_vol_2000", "Tonnage", 125, ChallengeProgress.METRIC_SESSION_VOLUME,
            target = { 2000 }, describe = { t, _ -> "Move $t+ kg of volume in one session" }),
        Template("week_workouts_2", "Back for More", 100, ChallengeProgress.METRIC_WEEK_WORKOUTS,
            target = { 2 }, describe = { t, _ -> "Complete $t workouts this week" }),
        // ── adaptive: computed from YOUR training (A-C3: challenging but reachable) ──────────────
        Template("beat_week_sets", "Outdo Yourself", 150, ChallengeProgress.METRIC_WEEK_SETS,
            adaptive = true,
            eligible = { it.lastWeekWorkingSets >= 6 },
            target = { it.lastWeekWorkingSets + maxOf(1, it.lastWeekWorkingSets / 20) }, // +5%
            describe = { t, _ -> "Beat last week — log $t+ working sets this week" }),
        Template("beat_week_volume", "Raise the Bar", 150, ChallengeProgress.METRIC_WEEK_VOLUME,
            adaptive = true,
            eligible = { it.lastWeekVolumeKg >= 1000 },
            target = { round10(it.lastWeekVolumeKg * 105 / 100) },
            describe = { t, _ -> "Move $t+ kg total this week (last week + 5%)" }),
        Template("week_volume_push", "Heaviest Week", 200, ChallengeProgress.METRIC_WEEK_VOLUME,
            adaptive = true,
            eligible = { it.lastWeekVolumeKg >= 2000 },
            target = { round10(it.lastWeekVolumeKg * 110 / 100) },
            describe = { t, _ -> "Move $t+ kg total this week (last week + 10%)" }),
        Template("above_avg_session", "Above Average", 100, ChallengeProgress.METRIC_SESSION_VOLUME,
            adaptive = true,
            eligible = { it.avgSessionVolumeKg >= 500 },
            target = { round10(it.avgSessionVolumeKg) },
            describe = { t, _ -> "Log one session with $t+ kg volume — your recent average" }),
        Template("pr_exercise", "Chase the Number", 150, ChallengeProgress.METRIC_PR_EXERCISE,
            adaptive = true,
            eligible = { it.baselineExercises.isNotEmpty() },
            param = { ctx, rng -> ctx.baselineExercises[rng.nextInt(ctx.baselineExercises.size)] },
            describe = { _, p -> "Set a PR on $p" }),
        Template("all_planned", "Perfect Attendance", 125, ChallengeProgress.METRIC_WEEK_WORKOUTS,
            adaptive = true,
            eligible = { it.plannedDayCount >= 2 },
            target = { it.plannedDayCount },
            describe = { t, _ -> "Complete all $t planned sessions this week" }),
        Template("per_day_sets", "One More Set", 100, ChallengeProgress.METRIC_SESSION_SETS,
            adaptive = true,
            eligible = { it.lastWeekWorkingSets >= 6 && it.plannedDayCount > 0 },
            target = { it.lastWeekWorkingSets / it.plannedDayCount + 1 },
            describe = { t, _ -> "Log $t+ working sets in one session — one over your daily average" }),
        Template("beat_best_sets", "Top Set Day", 125, ChallengeProgress.METRIC_SESSION_SETS,
            adaptive = true,
            eligible = { it.maxRecentSessionSets >= 5 },
            target = { it.maxRecentSessionSets + 1 },
            describe = { t, _ -> "Log $t+ working sets in one session — beat your recent best" }),
        Template("beat_best_volume", "Volume Best", 150, ChallengeProgress.METRIC_SESSION_VOLUME,
            adaptive = true,
            eligible = { it.maxRecentSessionVolumeKg >= 800 },
            target = { round10(it.maxRecentSessionVolumeKg * 105 / 100) },
            describe = { t, _ -> "Move $t+ kg in one session — top your recent best" }),
    )

    /**
     * The week's deterministic draw: seeded by [weekKey] exactly like the pre-R4 rotation, over
     * the eligible subset only, with adaptive targets/params resolved against [ctx] and frozen
     * into the returned challenges.
     */
    fun draw(weekKey: String, ctx: ChallengeContext, count: Int = CHALLENGES_PER_WEEK): List<DailyChallenge> {
        val rng = Random(weekKey.hashCode().toLong())
        return TEMPLATES.filter { it.eligible(ctx) }
            .shuffled(rng)
            .take(count)
            .map { t ->
                val target = t.target(ctx)
                val param = t.param(ctx, rng)
                DailyChallenge(
                    id = t.id,
                    name = t.name,
                    description = t.describe(target, param),
                    bonusXp = t.bonusXp,
                    metric = t.metric,
                    target = target,
                    param = param
                )
            }
    }
}
