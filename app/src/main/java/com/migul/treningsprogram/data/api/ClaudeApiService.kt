package com.migul.treningsprogram.data.api

import com.migul.treningsprogram.data.api.model.ClaudeRequest
import com.migul.treningsprogram.data.api.model.ClaudeResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface ClaudeApiService {
    @POST("v1/messages")
    suspend fun sendMessage(@Body request: ClaudeRequest): ClaudeResponse
}
