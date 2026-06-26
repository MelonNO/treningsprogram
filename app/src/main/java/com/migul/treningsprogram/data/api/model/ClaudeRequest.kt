package com.migul.treningsprogram.data.api.model

import com.google.gson.annotations.SerializedName

data class ClaudeRequest(
    val model: String = "claude-sonnet-4-6",
    // B10: raised 8192 → 16384 to reduce truncation. claude-sonnet-4-6 supports up to 64K output
    // tokens; 16384 stays comfortably inside the non-streaming OkHttp timeout budget (read 180s /
    // call 240s) while giving the model enough room to reason AND reach the JSON plan within budget.
    // A program plan + rationale rarely exceeds a few thousand tokens, so the prior 8192 cap was
    // being burned on planning prose before the JSON was emitted; 16384 leaves headroom for both.
    @SerializedName("max_tokens") val maxTokens: Int = 16384,
    val messages: List<Message>
) {
    data class Message(
        val role: String = "user",
        val content: String
    )
}
