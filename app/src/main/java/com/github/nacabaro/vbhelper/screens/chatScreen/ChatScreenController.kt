package com.github.nacabaro.vbhelper.screens.chatScreen

import com.github.nacabaro.vbhelper.domain.chat.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

interface ChatScreenController {
    fun getHistory(characterId: Long): Flow<List<ChatMessageEntity>>
    fun sendMessage(characterId: Long, text: String, onResult: (Result<String>) -> Unit)
}