package com.migul.treningsprogram.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CrashLog @Inject constructor(@ApplicationContext private val context: Context) {

    data class Entry(
        val timestampMs: Long = 0L,
        val thread: String = "",
        val exceptionClass: String = "",
        val message: String = "",
        val stackTrace: String = ""
    )

    private val gson = Gson()
    private val file get() = File(context.filesDir, "crash_log.json")

    @Synchronized
    fun add(thread: String, throwable: Throwable) {
        val entries = getAll().toMutableList()
        entries.add(
            0, Entry(
                timestampMs = System.currentTimeMillis(),
                thread = thread,
                exceptionClass = throwable.javaClass.name,
                message = throwable.message ?: "(no message)",
                stackTrace = throwable.stackTraceToString().take(8000)
            )
        )
        if (entries.size > 20) entries.subList(20, entries.size).clear()
        try { file.writeText(gson.toJson(entries)) } catch (_: Exception) {}
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
