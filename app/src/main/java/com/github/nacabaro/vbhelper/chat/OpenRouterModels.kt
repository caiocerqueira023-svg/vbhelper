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
    val message: ChatCompletionResponseMessage,
    val finish_reason: String?
)

/**
 * Gateways disagree on where they expose chain-of-thought.  Keep these fields
 * separate so only the model's final `content` is ever shown in chat.
 */
data class ChatCompletionResponseMessage(
    val role: String? = null,
    val content: String? = null,
    val reasoning: String? = null,
    val reasoning_content: String? = null,
    val analysis: String? = null
)

data class ChatCompletionResponse(
    val id: String?,
    val model: String?,
    val choices: List<ChatCompletionChoice>
)
