package com.migul.treningsprogram

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

object UpdateChecker {

    private const val LATEST_URL =
        "https://api.github.com/repos/MelonNO/treningsprogram/releases/latest"

    data class ReleaseInfo(val tag: String, val apkUrl: String, val notes: String)

    suspend fun fetchLatest(): ReleaseInfo? = withContext(Dispatchers.IO) {
        try {
            val conn = URL(LATEST_URL).openConnection()
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.connectTimeout = 8_000
            conn.readTimeout = 8_000
            val json = JSONObject(conn.getInputStream().bufferedReader().readText())
            val tag = json.getString("tag_name")
            val assets = json.getJSONArray("assets")
            if (assets.length() == 0) return@withContext null
            val apkUrl = assets.getJSONObject(0).getString("browser_download_url")
            val notes = json.optString("body", "")
            ReleaseInfo(tag, apkUrl, notes)
        } catch (_: Exception) {
            null
        }
    }

    fun isNewer(latestTag: String, currentVersion: String): Boolean {
        val latest  = latestTag.trimStart('v').split(".").map { it.toIntOrNull() ?: 0 }
        val current = currentVersion.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(latest.size, current.size)) {
            val l = latest.getOrElse(i) { 0 }
            val c = current.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }
}
