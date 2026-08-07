package com.migul.treningsprogram.domain.model

import com.migul.treningsprogram.data.db.entity.Achievement
import com.migul.treningsprogram.data.db.entity.LiftGoal

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
    /**
     * Brief 02 (2026-08-07) — the XP level is retired; "level" now means strength.
     *
     * XP itself is untouched ([xpEarned] / [totalXp] accrue exactly as before and still feed
     * achievements and challenges), but it no longer produces a level, a progress-to-next-level
     * figure, or a Rookie→Apex title. What replaced those five fields is the strength rating:
     * null tier = unrated (no sex, no usable weigh-in, or nothing qualifying logged yet).
     */
    val strengthTier: com.migul.treningsprogram.domain.strength.StrengthTier?,
    /** The tier as it stood BEFORE this session, so the celebration can say what was crossed. */
    val previousStrengthTier: com.migul.treningsprogram.domain.strength.StrengthTier?,
    /** Decision D3: the level-up celebration now fires on reaching a new strength tier. */
    val didTierUp: Boolean,
    /** 0..1 within the current tier — what the Home progress bar fills to. */
    val strengthProgress: Float,
    /** The same fraction BEFORE this session, so the bar animates from where it really was. */
    val previousStrengthProgress: Float,
    /** 0..100. Drives the re-based strength achievements. */
    val strengthScore: Int,
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
    val perfectWeekXp: Int = 0,
    /**
     * N5 contract: lift goals this session newly ACHIEVED (already persisted as achieved by
     * GoalRepository.detectReached — the one-way flip that makes the celebration fire exactly
     * once). NO XP rides on these (A-G1); they only drive the celebration surface.
     */
    val reachedGoals: List<LiftGoal> = emptyList(),
    /**
     * QoL item 03: estimated kcal for THIS session, computed on the fly at completion via
     * [com.migul.treningsprogram.domain.CalorieEstimator] (same estimator as Recap and the Stats
     * weekly total). null = show no figure. Never persisted.
     */
    val estimatedKcal: Int? = null
)
