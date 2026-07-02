package com.migul.treningsprogram.domain.model

data class DailyChallenge(
    val id: String,
    val name: String,
    val description: String,
    val bonusXp: Int,
    val isCompleted: Boolean = false,
    // R4 — progress metadata. Older persisted JSON has none of these fields; Gson then leaves
    // metric = null, which DailyChallengeManager treats as pre-R4 state and re-rolls once
    // (explicitly allowed by the brief; never double-awards).
    /** One of [com.migul.treningsprogram.domain.ChallengeProgress]'s METRIC_* ids. */
    val metric: String? = null,
    /** Concrete numeric target, computed at draw time and frozen for the week (0 = boolean goal). */
    val target: Int = 0,
    /** Accumulated progress from COMPLETED sessions this week (week-scoped metrics only). */
    val progress: Int = 0,
    /** Metric parameter — a muscle group or exercise name ("" = none). */
    val param: String = ""
)
