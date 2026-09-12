package com.github.nacabaro.vbhelper.chat

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object OpenRouterClient {
    fun create(baseUrl: String): OpenRouterService {
        require(baseUrl.startsWith("https://")) { "The chat endpoint must use HTTPS." }
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val clientBuilder = OkHttpClient.Builder()
            .addInterceptor(logging)
        if (baseUrl.contains("openrouter.ai", ignoreCase = true)) {
            clientBuilder.addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("HTTP-Referer", "https://github.com/nacabaro/vbhelper")
                    .addHeader("X-Title", "VBHelper")
                    .build()
                chain.proceed(request)
            }
        }

        return Retrofit.Builder()
            .baseUrl(if (baseUrl.endsWith('/')) baseUrl else "$baseUrl/")
            .client(clientBuilder.build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OpenRouterService::class.java)
    }
}
