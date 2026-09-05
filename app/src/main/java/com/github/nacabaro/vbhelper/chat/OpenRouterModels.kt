package com.github.nacabaro.vbhelper.chat

data class ChatMessageDto(
    val role: String, // "system" | "user" | "assistant"
    val content: String
)

data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessageDto>,
    val temperature: Double = 0.9,
    val max_tokens: Int = 400
)

data class ChatCompletionChoice(
    val index: Int,
    val message: ChatMessageDto,
    val finish_reason: String?
)

data class ChatCompletionResponse(
    val id: String?,
    val model: String?,
    val choices: List<ChatCompletionChoice>
)