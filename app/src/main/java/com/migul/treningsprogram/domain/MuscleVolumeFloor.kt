package com.migul.treningsprogram.domain

import com.migul.treningsprogram.data.MuscleClassifier
import com.migul.treningsprogram.data.db.entity.PlannedExercise

/**
 * Item 01 (training-data improvements 2026-08-03): per-muscle weekly FLOOR check on generated plans.
 *
 * Evidence (the user's own 3-week realized sample, 5-day Hypertrophy profile): generated weeks
 * allocated Legs to exactly ONE planned day every week (one missed day ⇒ a whole week with zero leg
 * sets) and gave Shoulders ~3 direct sets/week — while chest/back/arms got 8–16. Nothing in the
 * accept path checked per-muscle frequency or minimum volume, so those plans sailed through.
 *
 * This object is the SINGLE ADJUSTABLE PLACE for the floor thresholds (flagged assumption A1 — the
 * numbers are a product tuning knob; the outcome "no 1-day/3-set muscle groups on a ≥4-day
 * hypertrophy plan" is the requirement):
 *  - each major muscle group must appear on at least [FLOOR_DAYS_PER_MUSCLE] training days, and
 *  - receive at least [FLOOR_SETS_PER_MUSCLE] direct planned sets across the week.
 *
 * Scope guards (deliberate):
 *  - Active ONLY for the goals in [APPLICABLE_GOALS] (of the app's four goals — Strength /
 *    Hypertrophy / Endurance / Weight Loss — only Hypertrophy is a per-muscle-volume-driven goal;
 *    Strength legitimately concentrates volume, Endurance/Weight-Loss are density-driven).
 *  - Active ONLY at ≥ [MIN_PROFILE_DAYS_PER_WEEK] days/week — a 2–3-day profile cannot give every
 *    muscle two dedicated days, so the floor must not make those profiles ungeneratable.
 *  - Skipped on DELOAD weeks (volume is deliberately reduced) and on partial regenerations with
 *    locked (already-logged) days (the model cannot restructure days it must echo verbatim) — the
 *    caller handles both via [isActive]'s parameters / its own locked-days check.
 *
 * "Direct" sets = the planned sets of exercises whose PRIMARY broad classification
 * ([MuscleClassifier.fromName] — the same classifier Stats uses, keeping this check consistent with
 * what the Stats screens show) is that muscle group. Core / Cardio / unclassifiable names are never
 * floored ([MAJOR_GROUPS] only).
 */
object MuscleVolumeFloor {

    /** The major muscle groups the floor protects (broad labels from [MuscleClassifier.fromName]). */
    val MAJOR_GROUPS: List<String> = listOf("Chest", "Back", "Legs", "Shoulders", "Arms")

    // ── A1 tuning knobs — the one adjustable place ────────────────────────────────────────────────
    /** Goals (lowercase) the floor applies to. */
    val APPLICABLE_GOALS: Set<String> = setOf("hypertrophy")
    /** Minimum profile days/week for the floor to be active at all. */
    const val MIN_PROFILE_DAYS_PER_WEEK: Int = 4
    /** Every major muscle must appear on at least this many distinct training days. */
    const val FLOOR_DAYS_PER_MUSCLE: Int = 2
    /** Every major muscle must receive at least this many direct planned sets across the week. */
    const val FLOOR_SETS_PER_MUSCLE: Int = 6

    /** Whether the floor applies to this profile at all (goal + frequency + not a deload week). */
    fun isActive(goal: String, daysPerWeek: Int, isDeload: Boolean = false): Boolean =
        !isDeload &&
            goal.trim().lowercase() in APPLICABLE_GOALS &&
            daysPerWeek >= MIN_PROFILE_DAYS_PER_WEEK

    /** Per-major-group coverage of a planned week: group → (distinct training days, direct sets). */
    fun coverage(exercises: List<PlannedExercise>): Map<String, Pair<Set<Int>, Int>> {
        val byGroup = exercises.groupBy { MuscleClassifier.fromName(it.exerciseName) }
        return MAJOR_GROUPS.associateWith { group ->
            val rows = byGroup[group].orEmpty()
            Pair(rows.map { it.dayOfWeek }.toSet(), rows.sumOf { it.sets })
        }
    }

    /**
     * Deterministic accept-path check. Returns a targeted, retryable rejection reason when any major
     * muscle group sits below the frequency or set floor, or null when the plan is compliant (or the
     * floor is inactive for this profile). Mirrors the duration/cardio gate contract: pure, name-based,
     * feedback names every violating muscle so the next attempt can fix all of them at once.
     */
    fun violation(
        exercises: List<PlannedExercise>,
        goal: String,
        daysPerWeek: Int,
        isDeload: Boolean = false
    ): String? {
        if (!isActive(goal, daysPerWeek, isDeload)) return null
        val problems = coverage(exercises).mapNotNull { (group, cov) ->
            val (days, sets) = cov
            val parts = buildList {
                if (days.size < FLOOR_DAYS_PER_MUSCLE)
                    add("${days.size} training day${if (days.size == 1) "" else "s"} (floor $FLOOR_DAYS_PER_MUSCLE)")
                if (sets < FLOOR_SETS_PER_MUSCLE)
                    add("$sets direct sets (floor $FLOOR_SETS_PER_MUSCLE)")
            }
            if (parts.isEmpty()) null else "$group: ${parts.joinToString(", ")}"
        }
        if (problems.isEmpty()) return null
        return "Per-muscle weekly floor violated for a $daysPerWeek-day $goal plan — " +
            problems.joinToString("; ") + ". " +
            "EVERY major muscle group (Chest, Back, Legs, Shoulders, Arms) must be trained on at " +
            "least $FLOOR_DAYS_PER_MUSCLE different days this week with at least " +
            "$FLOOR_SETS_PER_MUSCLE direct sets total — a muscle group must NEVER depend on a " +
            "single training day. Redistribute so each listed muscle gets a second day and enough " +
            "direct sets (an upper/lower or full-body-leaning split reaches this naturally)."
    }

    /**
     * The generation-prompt guidance block (belt to the [violation] gate's braces). "" when the floor
     * is inactive so the emitted prompt stays byte-identical for out-of-scope profiles.
     */
    fun promptBlock(goal: String, daysPerWeek: Int, isDeload: Boolean = false): String {
        if (!isActive(goal, daysPerWeek, isDeload)) return ""
        return """
══════════════════════════════════════════
PER-MUSCLE WEEKLY FLOORS (HARD — deterministically enforced for this $daysPerWeek-day $goal plan)
══════════════════════════════════════════
Across the whole week, EVERY major muscle group — Chest, Back, Legs, Shoulders, Arms — must:
- appear on at least $FLOOR_DAYS_PER_MUSCLE DIFFERENT training days (never concentrated on a single day — one missed day must not be able to zero a muscle group for the week), and
- receive at least $FLOOR_SETS_PER_MUSCLE direct hard sets in total (direct = exercises whose PRIMARY muscle is that group; pressing does not count as direct shoulder work).
This overrides the split suggestion when they conflict: choose a split that trains each major muscle ~2×/week (upper/lower, full-body lean, or a push/pull/legs rotation that repeats each muscle). The app RE-COUNTS both floors deterministically and REJECTS the plan if any major muscle is under either floor.
"""
    }
}
