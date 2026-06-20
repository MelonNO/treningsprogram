package com.migul.treningsprogram.domain.model

data class DailyChallenge(
    val id: String,
    val name: String,
    val description: String,
    val bonusXp: Int,
    val isCompleted: Boolean = false
)
