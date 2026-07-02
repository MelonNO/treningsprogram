package com.migul.treningsprogram.domain.model

import com.migul.treningsprogram.data.db.entity.Achievement

/**
 * R6: a personal record with its numbers, captured at detection time so the celebration surface
 * can show "new vs previous" ("Bench Press — 62.5 kg, up from 60 kg"). Forward-only: history is
 * never backfilled; older results simply had no details.
 */
data class PrDetail(
    val exerciseName: String,
    val newWeightKg: Float,
    val previousWeightKg: Float
)

data class WorkoutResult(
    val xpEarned: Int,
    val totalXp: Int,
    val level: Int,
    val levelProgress: Float,
    val xpToNextLevel: Int,
    val didLevelUp: Boolean,
    val previousLevel: Int,
    val currentStreak: Int,
    val personalRecords: List<String>,
    val newAchievements: List<Achievement>,
    val totalWorkouts: Int,
    val completedChallenges: List<DailyChallenge> = emptyList(),
    val bonusChallengeXp: Int = 0,
    val setsLogged: Int = 0,
    val totalVolumeKg: Float = 0f,
    val exerciseCount: Int = 0,
    /**
     * R6 contract: same exercises as [personalRecords], with old→new weights. Populated by
     * [com.migul.treningsprogram.data.repository.GamificationRepository.processWorkoutCompletion];
     * consumed by the workout-complete celebration surface.
     */
    val prDetails: List<PrDetail> = emptyList(),
    /**
     * R4 contract: the Perfect Week bonus XP granted by THIS completion (0 = none). When > 0,
     * [xpEarned] already includes it and an itemized "Perfect Week" XP-log event exists.
     */
    val perfectWeekXp: Int = 0
)
