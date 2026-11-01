package com.tamerslab.chat.ui

import com.tamerslab.domain.chat.ChatMessageEntity
import com.tamerslab.domain.species.SpeciesProfile
import kotlinx.coroutines.flow.Flow

interface ChatScreenController {
    fun getHistory(characterId: Long): Flow<List<ChatMessageEntity>>
    fun getMood(characterId: Long): Flow<Int>
    fun sendMessage(characterId: Long, text: String, onResult: (Result<String>) -> Unit)
    fun deleteFromMessage(characterId: Long, messageId: Long)
    fun resendMessage(characterId: Long, messageId: Long, text: String, onResult: (Result<String>) -> Unit)
    fun markAssistantMessagesRead(characterId: Long)
    fun getSpeciesContext(characterId: Long, onResult: (SpeciesContext) -> Unit)
    fun saveManualSpeciesProfile(
        cardCharacterId: Long,
        name: String,
        level: String?,
        type: String?,
        profile: String?,
        specialMoves: List<String>,
        onSaved: () -> Unit
    )
}

data class SpeciesContext(
    val cardCharacterId: Long,
    val cardName: String,
    val existingProfile: SpeciesProfile?,
    val nickname: String? = null
)
