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
    val messages: List<Message>,
    // H4 (v1.10.4): when true the request is sent as an SSE stream. Full-program generation is routed
    // through streaming so the OkHttp readTimeout becomes an INTER-EVENT stall guard (continuous
    // content_block_delta + periodic ping events keep the connection live) rather than a single
    // time-to-first-byte deadline that a slow-but-healthy long generation would cross. Default false
    // keeps the non-streaming path byte-for-byte for any caller that does not opt in.
    @SerializedName("stream") val stream: Boolean = false
) {
    data class Message(
        val role: String = "user",
        val content: String
    )
}
