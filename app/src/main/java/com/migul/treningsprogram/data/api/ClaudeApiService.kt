package com.migul.treningsprogram.data.api

import com.migul.treningsprogram.data.api.model.ClaudeRequest
import com.migul.treningsprogram.data.api.model.ClaudeResponse
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Streaming

interface ClaudeApiService {
    @POST("v1/messages")
    suspend fun sendMessage(@Body request: ClaudeRequest): ClaudeResponse

    /**
     * H4 (v1.10.4): streaming variant of [sendMessage]. The request must carry `stream = true`.
     *
     * `@Streaming` keeps Retrofit from buffering the whole body up-front, so OkHttp's readTimeout
     * applies per network read — i.e. it now guards the gap BETWEEN SSE events, not the total
     * time-to-first-byte. A healthy long generation emits continuous `content_block_delta` + periodic
     * `ping` events, so the inter-event read never stalls; a genuinely hung stream still trips the
     * readTimeout. The raw SSE body is parsed by [com.migul.treningsprogram.data.repository.parseClaudeStream]
     * back into a [ClaudeResponse] so every downstream consumer is unchanged.
     */
    @Streaming
    @POST("v1/messages")
    suspend fun sendMessageStreaming(@Body request: ClaudeRequest): ResponseBody
}
