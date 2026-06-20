package com.migul.treningsprogram.data.api

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface WgerApi {
    @GET("api/v2/exercise/search/")
    suspend fun searchExercises(
        @Query("term") term: String,
        @Query("language") language: String = "english",
        @Query("format") format: String = "json"
    ): WgerSearchResponse

    @GET("api/v2/exerciseinfo/{id}/")
    suspend fun getExerciseInfo(
        @Path("id") id: Int,
        @Query("format") format: String = "json"
    ): WgerExerciseInfoItem
}

data class WgerSearchResponse(
    val suggestions: List<WgerSuggestion> = emptyList()
)

data class WgerSuggestion(
    val value: String = "",
    val data: WgerSuggestionData = WgerSuggestionData()
)

data class WgerSuggestionData(
    @SerializedName("base_id") val baseId: Int = 0
)

data class WgerExerciseInfoItem(
    val id: Int = 0,
    val images: List<WgerImage> = emptyList()
)

data class WgerImage(
    val image: String = "",
    @SerializedName("is_main") val isMain: Boolean = false
)
