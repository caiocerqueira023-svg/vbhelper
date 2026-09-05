package com.github.nacabaro.vbhelper.chat

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface OpenRouterService {
    @POST("api/v1/chat/completions")
    suspend fun getChatCompletion(
        @Header("Authorization") authorization: String, // "Bearer sk-or-..."
        @Body request: ChatCompletionRequest
    ): ChatCompletionResponse
}