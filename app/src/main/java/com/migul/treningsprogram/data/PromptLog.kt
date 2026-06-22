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
}
