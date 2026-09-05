// app/src/main/java/com/github/nacabaro/vbhelper/chat/ChatRepository.kt
package com.github.nacabaro.vbhelper.chat

import com.github.nacabaro.vbhelper.daos.ChatDao
import com.github.nacabaro.vbhelper.database.AppDatabase
import com.github.nacabaro.vbhelper.domain.chat.ChatMessageEntity
import com.github.nacabaro.vbhelper.source.LlmSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class ChatRepository(
    private val database: AppDatabase,
    private val llmSettingsRepository: LlmSettingsRepository,
    private val chatDao: ChatDao = database.chatDao(),
    private val openRouterService: OpenRouterService = OpenRouterClient.create()
) {
    class MissingApiKeyException : Exception("Chave de API do OpenRouter não configurada.")

    fun getHistory(characterId: Long): Flow<List<ChatMessageEntity>> =
        chatDao.getMessages(characterId)

    suspend fun sendMessage(characterId: Long, userText: String): String {
        val apiKey = llmSettingsRepository.apiKey.first()
            ?: throw MissingApiKeyException()
        val model = llmSettingsRepository.model.first()

        val character = database.userCharacterDao().getCharacterWithSprites(characterId)
        val card = database.cardDao().getCardByCharacterIdSync(characterId)
        val systemPrompt = DigimonPersonaBuilder.buildSystemPrompt(character, card?.name ?: "desconhecido")

        // salva a mensagem do usuário antes de chamar a API
        chatDao.insertMessage(
            ChatMessageEntity(
                characterId = characterId,
                role = "user",
                content = userText,
                timestamp = System.currentTimeMillis()
            )
        )

        val history = chatDao.getMessagesSync(characterId).takeLast(20) // limite de contexto
        val messages = mutableListOf(ChatMessageDto(role = "system", content = systemPrompt))
        messages += history.map { ChatMessageDto(role = it.role, content = it.content) }

        val response = openRouterService.getChatCompletion(
            authorization = "Bearer $apiKey",
            request = ChatCompletionRequest(model = model, messages = messages)
        )

        val reply = response.choices.firstOrNull()?.message?.content?.trim()
            ?: "..."

        chatDao.insertMessage(
            ChatMessageEntity(
                characterId = characterId,
                role = "assistant",
                content = reply,
                timestamp = System.currentTimeMillis()
            )
        )

        return reply
    }

    suspend fun clearHistory(characterId: Long) = chatDao.clearHistory(characterId)
}