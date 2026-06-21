package com.migul.treningsprogram.domain.model

data class OnboardingQuestion(
    val id: String,
    val question: String,
    val type: String,           // "text" or "choice"
    val options: List<String> = emptyList()
)
