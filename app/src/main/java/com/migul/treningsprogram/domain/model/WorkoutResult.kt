package com.migul.treningsprogram.domain.model

import com.migul.treningsprogram.data.db.entity.Achievement

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
    val exerciseCount: Int = 0
)
