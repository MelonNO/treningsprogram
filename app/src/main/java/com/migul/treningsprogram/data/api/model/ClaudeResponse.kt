package com.migul.treningsprogram.data.api.model

import com.google.gson.annotations.SerializedName

data class ClaudeResponse(
    val id: String = "",
    val type: String = "",
    val content: List<ContentBlock> = emptyList(),
    // B10: the Anthropic /v1/messages "stop_reason". "max_tokens" means the model ran out of
    // output budget before finishing — a TRUNCATED response (often before it ever reached the JSON).
    // Absent on error/older payloads ⇒ null (treated as "not truncated").
    @SerializedName("stop_reason") val stopReason: String? = null
) {
    data class ContentBlock(
        val type: String = "",
        val text: String = ""
    )

    fun text(): String = content.firstOrNull { it.type == "text" }?.text ?: ""

    /** True when the API reports it stopped because it hit the output-token cap (a cut-off response). */
    fun hitTokenLimit(): Boolean = stopReason == "max_tokens"
}
