package com.migul.treningsprogram.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExerciseResolutionLog @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("exercise_resolution_log", Context.MODE_PRIVATE)
    }
    private val gson = Gson()

    private fun normKey(name: String): String =
        ExerciseCatalog.normalizeName(name).replace(" ", "_")

    // --- Miss tracking ---

    fun recordMiss(name: String) {
        val nk = normKey(name)
        val countKey = "miss_count_$nk"
        val nameKey  = "miss_name_$nk"
        val count = prefs.getInt(countKey, 0)
        prefs.edit()
            .putInt(countKey, count + 1)
            .putString(nameKey, name)   // preserve original casing/spelling
            .apply()
    }

    fun getMissReport(): List<Pair<String, Int>> {
        return prefs.all
            .filter { (k, _) -> k.startsWith("miss_count_") }
            .map { (k, v) ->
                val nk = k.removePrefix("miss_count_")
                val displayName = prefs.getString("miss_name_$nk", null)
                    ?: nk.replace("_", " ")
                displayName to (v as? Int ?: 0)
            }
            .sortedByDescending { it.second }
    }

    fun clearMisses() {
        val keysToRemove = prefs.all.keys.filter { it.startsWith("miss_count_") || it.startsWith("miss_name_") }
        prefs.edit().also { ed -> keysToRemove.forEach { ed.remove(it) } }.apply()
    }

    // --- Low-confidence tracking ---

    fun recordLowConf(name: String, dbId: String, confidence: Float) {
        val key = "lowconf_${normKey(name)}"
        val count = prefs.getInt(key, 0)
        prefs.edit()
            .putInt(key, count + 1)
            .putString("lowconf_id_${normKey(name)}", dbId)
            .putFloat("lowconf_score_${normKey(name)}", confidence)
            .apply()
    }

    fun getLowConfReport(): List<Triple<String, String, Float>> {
        return prefs.all
            .filter { (k, _) -> k.startsWith("lowconf_count_") }
            .map { (k, _) ->
                val normName = k.removePrefix("lowconf_count_")
                val id = prefs.getString("lowconf_id_$normName", "") ?: ""
                val score = prefs.getFloat("lowconf_score_$normName", 0f)
                Triple(normName.replace("_", " "), id, score)
            }
    }

    // --- LLM match audit ---

    fun recordLlmMatch(name: String, dbId: String) {
        prefs.edit().putString("llm_${normKey(name)}", dbId).apply()
    }

    fun getLlmMatches(): List<Pair<String, String>> {
        return prefs.all
            .filter { (k, _) -> k.startsWith("llm_") }
            .map { (k, v) -> k.removePrefix("llm_").replace("_", " ") to (v as? String ?: "") }
    }

    // --- Alias table ---

    fun addAlias(normalizedName: String, dbId: String) {
        prefs.edit().putString("alias_$normalizedName", dbId).apply()
    }

    fun getAliasId(normalizedName: String): String? =
        prefs.getString("alias_$normalizedName", null)

    fun removeAlias(normalizedName: String) {
        prefs.edit().remove("alias_$normalizedName").apply()
    }

    fun getAllAliases(): Map<String, String> {
        return prefs.all
            .filter { (k, _) -> k.startsWith("alias_") }
            .mapNotNull { (k, v) -> (v as? String)?.let { k.removePrefix("alias_") to it } }
            .toMap()
    }

    // --- Pending LLM retry queue ---

    fun enqueuePendingLlm(name: String) {
        val current = getPendingLlmRaw().toMutableSet()
        current.add(name)
        prefs.edit().putString("pending_llm", gson.toJson(current)).apply()
    }

    fun getPendingLlm(): Set<String> = getPendingLlmRaw()

    fun removePendingLlm(name: String) {
        val current = getPendingLlmRaw().toMutableSet()
        current.remove(name)
        prefs.edit().putString("pending_llm", gson.toJson(current)).apply()
    }

    private fun getPendingLlmRaw(): Set<String> {
        val json = prefs.getString("pending_llm", null) ?: return emptySet()
        return try {
            val type = object : TypeToken<Set<String>>() {}.type
            gson.fromJson(json, type) ?: emptySet()
        } catch (_: Exception) { emptySet() }
    }

    // --- Match rate ---

    fun getMatchRate(totalExercises: Int, matchedExercises: Int): Float {
        if (totalExercises == 0) return 0f
        return matchedExercises.toFloat() / totalExercises.toFloat()
    }
}
