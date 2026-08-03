package com.migul.treningsprogram.domain

import com.migul.treningsprogram.data.MuscleClassifier
import com.migul.treningsprogram.data.db.entity.PlannedExercise

/**
 * Shared, pure time-estimate helper for a planned workout day. The single source of truth for the
 * "~Xm" labels (Program screen) AND the deterministic ±10 min duration enforcement in AiRepository —
 * both surfaces always show/enforce the same number.
 *
 * ## 2026-08-03 correction ("duration truth")
 * The old formula (`sets × reps × 4 s + (sets−1) × rest + 60 s admin`) was measured against the
 * user's real training history and found ~1.6–1.7× OPTIMISTIC (planned-day estimate vs the real
 * wall-clock `WorkoutSession.durationMinutes`; median ratio 1.62, p25 1.44, p75 1.84 over 16
 * plan-matched days from 18 real sessions). The ±10-min generation gate was therefore measuring
 * the wrong number: plans it rejected as "UNDER by 11–15" at a 60-min target were actually
 * right-sized in real minutes.
 *
 * ### What is MEASURED (from the user's backup export; set-level `loggedAtMs` timestamps)
 *  - Same-exercise set→set cycle (rest + next set's work + logging): median 225 s (p25 185, p75 272,
 *    n=142 gaps).
 *  - Exercise-change gap (last rest + transition + first set): median 305 s (p25 234, p75 471, n=44).
 *  - Per-set surplus over the PLANNED rest (work + rest-overrun + logging): median ~95 s.
 *  - Real minutes per logged set across whole sessions: median 4.4.
 *
 * ### What is REASONED (and why)
 * n=18 sessions with near-constant session shape cannot fit the individual constants (a shape
 * regression is underpowered, R² ≤ 0.10) — so the STRUCTURE below is chosen because it matches the
 * measured gap anatomy, and the constants are calibrated so the aggregate reproduces reality:
 *  - [SETUP_SECONDS_PER_SET] = 35 — rack/unrack, plate changes, logging in the app, rest overrun.
 *    Sits inside the measured per-set surplus once ~40–60 s of rep work is subtracted.
 *  - Rest is now counted after EVERY set ([sets] × rest, was sets−1): the recovery taken after an
 *    exercise's final set is real time — it is where part of the measured 305 s exercise-change gap
 *    lives. With it, predicted cycle/change gaps land on the measured medians.
 *  - [TRANSITION_SECONDS_PER_EXERCISE] = 90 (was a 60 s "admin" term) — walking, claiming and
 *    adjusting the next station, beyond the final-set rest above.
 *  - [WARMUP_RAMP_SECONDS] = 120 per LOADED, long-rest lift — warm-up ramp sets are logged in real
 *    sessions (0–5 per session here, median 1) but never appear in planned rows, so they are a real
 *    unmodeled term. 120 s ≈ 1–2 quick ramp sets; the trigger (weight > 0 AND rest ≥
 *    [RAMP_REST_THRESHOLD_SECONDS]) marks the heavy-compound slots people actually ramp on.
 *
 * With these constants the planned-day estimate reproduces the same user's real durations at
 * median ratio 1.02 (p25 0.89, p75 1.14) — calibrated, not merely asserted. CAVEAT: the
 * calibration data is ONE user's hypertrophy-style training (60–180 s rests). Applying the same
 * overheads to short-rest endurance / weight-loss circuit plans is an EXTRAPOLATION — plausible
 * (setup/logging/transition don't shrink with rest) but unmeasured.
 *
 * The generation prompt's stated TIME BUDGET formula (AiRepository.buildPrompt /
 * buildSingleDayPrompt) MUST quote the SAME structure and constants, so the minutes the model
 * sizes toward equal what this deterministic gate computes. Changing anything here shifts every
 * computed day length AND the Program-screen "~Xm" display — a deliberate, coordinated change,
 * never a silent one.
 */
object WorkoutTimeEstimator {

    /**
     * Per-rep work time. P2 2026-07 raised 3 → 4 s for a realistic controlled tempo; the 2026-08
     * calibration kept it — the measured misses live in the overhead terms, not the rep tempo.
     */
    const val WORK_SECONDS_PER_REP = 4

    /**
     * 2026-08-03 (measured-band, split reasoned — see class KDoc): per-set overhead beyond the rep
     * work — racking, plate changes, logging the set in the app, rest overrun.
     */
    const val SETUP_SECONDS_PER_SET = 35

    /**
     * 2026-08-03 (measured-band, split reasoned): time to move between exercises beyond the final
     * set's rest — walking, claiming/adjusting the next station. Replaces the old 60 s "admin" term.
     */
    const val TRANSITION_SECONDS_PER_EXERCISE = 90

    /**
     * 2026-08-03 (reasoned; see class KDoc): warm-up ramp allowance for a loaded, long-rest lift.
     * Ramp sets are logged in real sessions but absent from planned rows.
     */
    const val WARMUP_RAMP_SECONDS = 120

    /** Effective rest at/above this (with a real load) marks a lift as ramp-worthy. */
    const val RAMP_REST_THRESHOLD_SECONDS = 120

    /**
     * Estimated seconds for a single planned exercise (work + per-set overhead + rest + transition
     * + ramp allowance).
     *
     * Item 4 (rest-UX batch 2026-07): when [manualRest] is non-null the user takes THEIR OWN
     * per-category rest times instead of the AI's per-exercise suggestion, so the estimate counts
     * the category time for every strength exercise (cardio entries carry no inter-set rest and
     * are unaffected). `null` = AI mode = the exercise's own recommended rest.
     */
    fun estimateExerciseSeconds(ex: PlannedExercise, manualRest: ManualRestTimes? = null): Int {
        // 2026-08 fix: a Cardio-classified entry whose target is a PURE rep count or rep range
        // ("20", "15-20" — e.g. Mountain Climbers programmed as 3×20, or a mis-classified
        // "Cable Crunch") must be timed with the strength formula, NOT parseCardioSeconds —
        // the latter's 1800 s fallback counted such an entry as ~31 minutes, corrupting the
        // duration gate in both directions. Duration/distance targets ("30 min", "5 km") and
        // genuinely unparseable interval strings ("6×400m", which keeps the 1800 s fallback)
        // still take the cardio branch.
        return if (isCardio(ex.exerciseName) && !isPureRepScheme(ex.targetReps)) {
            parseCardioSeconds(ex.targetReps) + TRANSITION_SECONDS_PER_EXERCISE
        } else {
            val maxReps = Regex("\\d+").findAll(ex.targetReps).lastOrNull()?.value?.toIntOrNull() ?: 10
            val rest = manualRest?.restSecondsFor(ex.exerciseName) ?: ex.recommendedRestSeconds
            val ramp = if (ex.targetWeightKg > 0f && rest >= RAMP_REST_THRESHOLD_SECONDS) {
                WARMUP_RAMP_SECONDS
            } else 0
            ex.sets * (maxReps * WORK_SECONDS_PER_REP + SETUP_SECONDS_PER_SET) +
                ex.sets * rest +
                TRANSITION_SECONDS_PER_EXERCISE +
                ramp
        }
    }

    /** Estimated whole-minute duration for a day, rounded to nearest minute. */
    fun estimateDayMinutes(exercises: List<PlannedExercise>, manualRest: ManualRestTimes? = null): Int =
        (exercises.sumOf { estimateExerciseSeconds(it, manualRest) } + 30) / 60

    private fun isCardio(name: String): Boolean =
        MuscleClassifier.displayName(name) == "Cardio"

    /**
     * True when [targetReps] is a bare rep count ("20") or rep range ("15-20") with no units —
     * such a target describes SETS×REPS work regardless of the exercise's cardio classification,
     * so it is timed with the strength formula (see [estimateExerciseSeconds]).
     */
    private val PURE_REP_SCHEME = Regex("^\\d+(\\s*-\\s*\\d+)?$")
    private fun isPureRepScheme(targetReps: String): Boolean =
        PURE_REP_SCHEME.matches(targetReps.trim())

    private fun parseCardioSeconds(targetReps: String): Int {
        // "30 min" → 1800, "5km" → 1500 (@ 5min/km), "6×400m" → fallback 30 min
        val minMatch = Regex("(\\d+)\\s*min", RegexOption.IGNORE_CASE).find(targetReps)
        if (minMatch != null) return minMatch.groupValues[1].toInt() * 60
        val kmMatch = Regex("(\\d+(?:\\.\\d+)?)\\s*km", RegexOption.IGNORE_CASE).find(targetReps)
        if (kmMatch != null) return (kmMatch.groupValues[1].toDouble() * 5 * 60).toInt()
        return 1800
    }
}
