package com.github.nacabaro.vbhelper.chat

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatCompletionRequestTest {
    @Test
    fun `request serializes configured roleplay temperature`() {
        val request = ChatCompletionRequest(
            model = "google/gemma-4-26b-a4b-it:free",
            messages = listOf(ChatMessageDto("user", "Olá")),
            temperature = 0.95,
            max_tokens = 400
        )

        val json = Gson().toJson(request)

        assertTrue(json.contains("\"temperature\":0.95"))
        assertTrue(json.contains("\"max_tokens\":400"))
        assertTrue(json.contains("\"model\":\"google/gemma-4-26b-a4b-it:free\""))
    }
}
