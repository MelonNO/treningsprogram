package com.migul.treningsprogram.domain.model

import com.migul.treningsprogram.data.db.entity.WorkoutSession

/**
 * One exercise as it appeared in a single session, with the data needed for the
 * "vs last time" delta and PR framing on the Recap screen.
 */
data class ExerciseRecap(
    val exerciseName: String,
    val muscleGroup: String,
    val isCardio: Boolean,
    val sets: Int,
    val topWeightKg: Float,
    val topReps: Int,
    val totalReps: Int,              // sum of reps across working sets (cardio proxy)
    val volumeKg: Float,
    val prevTopWeightKg: Float?,     // null = first time performing this exercise
    val prevTopReps: Int?,
    val isPrThisSession: Boolean,
    val existingPrWeightKg: Float?,  // best ever (may be this session)
    val existingPrDateMs: Long?
)

/**
 * Pacing derived from per-set log timestamps. Only the gaps *between* logged sets
 * are measurable from a single timestamp per set — a gap contains both the rest
 * and the next set's work, so this models rest/idle, not a true work split.
 */
data class SessionPacing(
    val gapCount: Int,            // number of between-set intervals measured
    val avgRestSeconds: Int,      // mean of the normal-rest gaps
    val targetRestSeconds: Int?,  // prescribed rest from the plan, or null if unknown
    val restSeconds: Int,         // total between-set time within the idle ceiling
    val idleSeconds: Int,         // total time spent in long pauses (over the ceiling)
    val longPauseCount: Int       // how many gaps exceeded the idle ceiling
)

/** Everything the session-scoped Recap screen needs for one session. */
data class SessionRecap(
    val session: WorkoutSession,
    val focusMuscle: String,
    val durationMinutes: Int,
    val totalVolumeKg: Float,
    val totalSets: Int,
    val exercises: List<ExerciseRecap>,
    val muscleVolume: List<Pair<String, Int>>,   // muscle -> working set count, desc
    val effort: List<Pair<String, Int>>,         // Easy/Moderate/Hard (ordered) -> count
    val plannedSets: Int?,                        // null = no plan for this day
    val estimatedMinutes: Int?,                   // null = no plan for this day
    val skippedExercises: List<String>,          // planned but not performed
    val pacing: SessionPacing?                    // null = too few timestamped sets to measure
)
