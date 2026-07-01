package com.migul.treningsprogram.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PromptLog @Inject constructor(
    @ApplicationContext private val context: Context
) {
    data class Entry(
        val timestampMs: Long = 0L,
        val type: String = "",
        val prompt: String = "",
        val response: String = ""
    )

    private val gson = Gson()
    private val file get() = File(context.filesDir, "prompt_log.json")
    private val maxEntries = 40

    @Synchronized
    fun add(type: String, prompt: String, response: String) {
        val entries = getAll().toMutableList()
        entries.add(0, Entry(System.currentTimeMillis(), type, prompt, response))
        val trimmed = if (entries.size > maxEntries) entries.take(maxEntries) else entries
        file.writeText(gson.toJson(trimmed))
    }

    @Synchronized
    fun getAll(): List<Entry> {
        if (!file.exists()) return emptyList()
        return try {
            val type = object : TypeToken<List<Entry>>() {}.type
            gson.fromJson<List<Entry>>(file.readText(), type) ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    @Synchronized
    fun clear() { file.delete() }

    companion object {
        /**
         * Item 1: renders the WHOLE set of prompt-log entries — each entry's prompt AND its AI
         * response — into one readable, paste-ready text block for the clipboard. Entries are clearly
         * separated and labelled so they stay distinguishable when pasted elsewhere.
         *
         * No API key ever appears here: the key is an HTTP header, never part of the prompt/response
         * text, so nothing needs stripping. Pure (timestamp formatting injected) so it is unit-testable.
         */
        fun formatAll(entries: List<Entry>, formatTimestamp: (Long) -> String): String {
            if (entries.isEmpty()) return ""
            val total = entries.size
            return entries.mapIndexed { i, e ->
                buildString {
                    append("===== Entry ${i + 1} of $total")
                    if (e.type.isNotBlank()) append(" · ${e.type.replace('_', ' ').uppercase()}")
                    append(" · ${formatTimestamp(e.timestampMs)} =====\n\n")
                    append("--- PROMPT ---\n")
                    append(e.prompt)
                    append("\n\n--- AI RESPONSE ---\n")
                    append(e.response)
                }
            }.joinToString("\n\n\n")
        }
    }
}
