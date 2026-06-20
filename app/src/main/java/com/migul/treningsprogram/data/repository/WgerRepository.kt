package com.migul.treningsprogram.data.repository

import com.migul.treningsprogram.data.api.WgerApi
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WgerRepository @Inject constructor(private val wgerApi: WgerApi) {

    // name → imageUrl ("" = confirmed no image; null = not yet fetched / error, retry allowed)
    private val imageCache = ConcurrentHashMap<String, String>()

    suspend fun getExerciseImageUrl(name: String): String? {
        imageCache[name]?.let { return it.ifEmpty { null } }
        return searchAndFetch(name)
    }

    private suspend fun searchAndFetch(query: String): String? {
        return try {
            val results = wgerApi.searchExercises(query)
            val baseId = results.suggestions.firstOrNull()?.data?.baseId
            if (baseId == null || baseId == 0) {
                imageCache[query] = ""
                return null
            }
            val info = wgerApi.getExerciseInfo(baseId)
            val img = (info.images.firstOrNull { it.isMain } ?: info.images.firstOrNull())?.image
            val url = img?.let { if (it.startsWith("http")) it else "https://wger.de$it" } ?: ""
            imageCache[query] = url
            url.ifEmpty { null }
        } catch (_: Exception) {
            null  // Don't cache — allow retry next time the exercise is shown
        }
    }
}
