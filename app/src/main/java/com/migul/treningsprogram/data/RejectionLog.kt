package com.migul.treningsprogram.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RejectionLog @Inject constructor(
    @ApplicationContext private val context: Context
) {
    data class Attempt(val attempt: Int, val reason: String, val finalFailure: Boolean)
    data class Session(val timestampMs: Long, val attempts: List<Attempt>, val succeeded: Boolean)

    private val gson = Gson()
    private val file get() = File(context.filesDir, "rejection_log.json")
    private val maxSessions = 30

    @Synchronized
    fun addSession(attempts: List<Attempt>, succeeded: Boolean) {
        if (attempts.isEmpty()) return
        val sessions = getAll().toMutableList()
        sessions.add(0, Session(System.currentTimeMillis(), attempts, succeeded))
        val trimmed = if (sessions.size > maxSessions) sessions.take(maxSessions) else sessions
        file.writeText(gson.toJson(trimmed))
    }

    @Synchronized
    fun getAll(): List<Session> {
        if (!file.exists()) return emptyList()
        return try {
            val type = object : TypeToken<List<Session>>() {}.type
            gson.fromJson<List<Session>>(file.readText(), type) ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    @Synchronized
    fun clear() { file.delete() }
}
