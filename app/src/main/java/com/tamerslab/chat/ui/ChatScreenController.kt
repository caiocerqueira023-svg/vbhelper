package com.tamerslab.chat.ui

import com.tamerslab.digiline.gateway.DigilineScene
import com.tamerslab.domain.chat.ChatMessageEntity
import com.tamerslab.domain.species.SpeciesProfile
import kotlinx.coroutines.flow.Flow

/** Native personality sync state shown next to the owned DigiLine (D4/R3). */
enum class PersonalitySyncUiState { NONE, PENDING, WRITTEN, UNAVAILABLE }

interface ChatScreenController {
    fun getHistory(characterId: Long): Flow<List<ChatMessageEntity>>
    fun getMood(characterId: Long): Flow<Int>
    fun getScene(characterId: Long): Flow<DigilineScene?>
    fun ensureScene(characterId: Long, onResult: (Result<Unit>) -> Unit)
    fun chooseSceneOption(characterId: Long, optionId: String, onResult: (Result<Unit>) -> Unit)
    fun dismissScene(characterId: Long, onResult: (Result<Unit>) -> Unit)
    fun getPersonalitySyncState(characterId: Long): Flow<PersonalitySyncUiState>
    fun isShiftNoticePending(characterId: Long, onResult: (Boolean) -> Unit)
    fun markShiftNoticeShown(characterId: Long)
    fun isLegacyLlmEnabled(): Boolean
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
