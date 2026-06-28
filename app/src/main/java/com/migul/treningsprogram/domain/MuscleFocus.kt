package com.migul.treningsprogram.domain

import com.migul.treningsprogram.data.MuscleClassifier

/**
 * P1: pure helper for a day's PRIMARY MUSCLE FOCUS — the dominant muscle group across the day's
 * exercises ([P1-A3]). Used to decide whether a manual edit or a single-day regeneration genuinely
 * CHANGED the day's focus (legs → chest) and therefore should trigger an auto-rebalance, versus a
 * minor edit (sets/reps, a same-focus swap) that must NOT. No Android/DB dependency so the focus
 * rule is unit-testable on the JVM harness (mirrors [WorkoutTimeEstimator] / [RegeneratePlanner]).
 */
object MuscleFocus {

    /**
     * The dominant muscle group of a day, by [MuscleClassifier.displayName] frequency. A frequency
     * tie is broken by the alphabetically-LAST group, so the result is fully deterministic (the only
     * property P1 needs). Empty list ⇒ "" (no focus, e.g. a rest day).
     */
    fun dominant(exerciseNames: List<String>): String {
        if (exerciseNames.isEmpty()) return ""
        return exerciseNames
            .map { MuscleClassifier.displayName(it) }
            .groupingBy { it }
            .eachCount()
            .entries
            .maxWithOrNull(compareBy({ it.value }, { it.key }))
            ?.key ?: ""
    }

    /**
     * True when the day's primary focus genuinely changed from [before] to [after]. A change to an
     * empty/no-focus day (after has no exercises) does NOT count — there is nothing to rebalance the
     * week around. This is the trigger condition for the P1 auto-rebalance.
     */
    fun changed(before: List<String>, after: List<String>): Boolean {
        val b = dominant(after)
        return b.isNotBlank() && dominant(before) != b
    }
}
