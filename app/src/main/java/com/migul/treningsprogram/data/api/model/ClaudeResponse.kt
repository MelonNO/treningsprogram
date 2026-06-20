package com.migul.treningsprogram.data.api.model

data class ClaudeResponse(
    val id: String = "",
    val type: String = "",
    val content: List<ContentBlock> = emptyList()
) {
    data class ContentBlock(
        val type: String = "",
        val text: String = ""
    )

    fun text(): String = content.firstOrNull { it.type == "text" }?.text ?: ""
}
