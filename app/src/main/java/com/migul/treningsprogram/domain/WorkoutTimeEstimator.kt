package com.migul.treningsprogram.domain

import com.migul.treningsprogram.data.MuscleClassifier
import com.migul.treningsprogram.data.db.entity.PlannedExercise

/**
 * Shared, pure time-estimate helper for a planned workout day. Extracted from
 * ProgramFragment so the same arithmetic that drives the "~Xm" labels also backs the
 * deterministic ±10 min duration enforcement in AiRepository. The formula here is
 * byte-for-byte equivalent to the original ProgramFragment math.
 */
object WorkoutTimeEstimator {

    private const val ADMIN_TIME_PER_EXERCISE_SECONDS = 60

    /**
     * P2 (generation-quality overhaul 2026-07): per-rep work time. Raised 3 → 4 s to reflect a
     * realistic controlled tempo (the old 3 s undercounted and biased every estimate low). This is the
     * single source of truth for the "seconds of work per rep" constant — the generation prompt's stated
     * TIME BUDGET formula (AiRepository.buildPrompt / buildSingleDayPrompt) MUST quote the SAME number,
     * so the minute the model sizes toward equals what this deterministic gate computes. Changing it here
     * shifts every computed day length AND the Program-screen "~Xm" display — a deliberate, coordinated
     * change, never a silent one.
     */
    const val WORK_SECONDS_PER_REP = 4

    /** Estimated seconds for a single planned exercise (work + inter-set rest + setup). */
    fun estimateExerciseSeconds(ex: PlannedExercise): Int {
        return if (isCardio(ex.exerciseName)) {
            parseCardioSeconds(ex.targetReps) + ADMIN_TIME_PER_EXERCISE_SECONDS
        } else {
            val maxReps = Regex("\\d+").findAll(ex.targetReps).lastOrNull()?.value?.toIntOrNull() ?: 10
            ex.sets * (maxReps * WORK_SECONDS_PER_REP) + (ex.sets - 1) * ex.recommendedRestSeconds + ADMIN_TIME_PER_EXERCISE_SECONDS
        }
    }

    /** Estimated whole-minute duration for a day, rounded to nearest minute. */
    fun estimateDayMinutes(exercises: List<PlannedExercise>): Int =
        (exercises.sumOf { estimateExerciseSeconds(it) } + 30) / 60

    private fun isCardio(name: String): Boolean =
        MuscleClassifier.displayName(name) == "Cardio"

    private fun parseCardioSeconds(targetReps: String): Int {
        // "30 min" → 1800, "5km" → 1500 (@ 5min/km), "6×400m" → fallback 30 min
        val minMatch = Regex("(\\d+)\\s*min", RegexOption.IGNORE_CASE).find(targetReps)
        if (minMatch != null) return minMatch.groupValues[1].toInt() * 60
        val kmMatch = Regex("(\\d+(?:\\.\\d+)?)\\s*km", RegexOption.IGNORE_CASE).find(targetReps)
        if (kmMatch != null) return (kmMatch.groupValues[1].toDouble() * 5 * 60).toInt()
        return 1800
    }
}
