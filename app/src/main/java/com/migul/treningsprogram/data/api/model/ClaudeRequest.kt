package com.migul.treningsprogram.data.api.model

import com.google.gson.annotations.SerializedName

data class ClaudeRequest(
    val model: String = "claude-sonnet-4-6",
    @SerializedName("max_tokens") val maxTokens: Int = 8192,
    val messages: List<Message>
) {
    data class Message(
        val role: String = "user",
        val content: String
    )
}
