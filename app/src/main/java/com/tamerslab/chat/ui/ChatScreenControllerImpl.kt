package com.tamerslab.chat.ui

import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.tamerslab.domain.chat.ChatMessageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class ChatScreenControllerImpl(
    private val componentActivity: ComponentActivity
) : ChatScreenController {
    private val chatRepository = com.tamerslab.di.AppServices.chatRepository
    private val speciesRepository = com.tamerslab.di.AppServices.speciesRepository

    override fun getHistory(characterId: Long): Flow<List<ChatMessageEntity>> =
        chatRepository.getHistory(characterId)

    override fun getMood(characterId: Long): Flow<Int> = chatRepository.observeMood(characterId)

    override fun sendMessage(characterId: Long, text: String, onResult: (Result<String>) -> Unit) {
        componentActivity.lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching { chatRepository.sendMessage(characterId, text) }
            componentActivity.runOnUiThread { onResult(result) }
        }
    }

    override fun deleteFromMessage(characterId: Long, messageId: Long) {
        componentActivity.lifecycleScope.launch(Dispatchers.IO) {
            chatRepository.deleteFromMessage(characterId, messageId)
        }
    }

    override fun resendMessage(
        characterId: Long,
        messageId: Long,
        text: String,
        onResult: (Result<String>) -> Unit
    ) {
        componentActivity.lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching {
                chatRepository.resendMessage(characterId, messageId, text)
            }
            componentActivity.runOnUiThread { onResult(result) }
        }
    }

    override fun markAssistantMessagesRead(characterId: Long) {
        componentActivity.lifecycleScope.launch(Dispatchers.IO) {
            chatRepository.markAssistantMessagesRead(characterId)
        }
    }

    override fun getSpeciesContext(characterId: Long, onResult: (SpeciesContext) -> Unit) {
        componentActivity.lifecycleScope.launch(Dispatchers.IO) {
            val partner = chatRepository.partnerContext(characterId)
            val profile = speciesRepository.getProfileForCharacter(partner.cardCharacterId)
            componentActivity.runOnUiThread {
                onResult(SpeciesContext(partner.cardCharacterId, partner.cardName, profile, partner.nickname))
            }
        }
    }

    override fun saveManualSpeciesProfile(
        cardCharacterId: Long,
        name: String,
        level: String?,
        type: String?,
        profile: String?,
        specialMoves: List<String>,
        onSaved: () -> Unit
    ) {
        if (!com.tamerslab.di.AppServices.debugSettingsRepository.enabled) return
        componentActivity.lifecycleScope.launch(Dispatchers.IO) {
            speciesRepository.saveManualProfile(cardCharacterId, name, level, type, profile, specialMoves)
            componentActivity.runOnUiThread(onSaved)
        }
    }
}
