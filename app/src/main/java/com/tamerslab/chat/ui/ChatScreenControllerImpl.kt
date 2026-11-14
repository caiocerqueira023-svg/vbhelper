package com.tamerslab.chat.ui

import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.tamerslab.domain.chat.ChatMessageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class ChatScreenControllerImpl(
    private val componentActivity: ComponentActivity
) : ChatScreenController {
    private val chatRepository = com.tamerslab.di.AppServices.chatRepository
    private val speciesRepository = com.tamerslab.di.AppServices.speciesRepository
    private val ownedRepository = com.tamerslab.di.AppServices.ownedDigilineRepository
    private val digilineGateway = com.tamerslab.di.AppServices.digilineGateway

    override fun getHistory(characterId: Long): Flow<List<ChatMessageEntity>> =
        chatRepository.getHistory(characterId)

    override fun getMood(characterId: Long): Flow<Int> = chatRepository.observeMood(characterId)

    override fun getScene(characterId: Long): Flow<com.tamerslab.digiline.gateway.DigilineScene?> =
        kotlinx.coroutines.flow.flow {
            val individualId = ownedRepository.individualIdForCharacter(characterId)
            if (individualId == null) emit(null) else emitAll(digilineGateway.observeScene(individualId))
        }

    override fun ensureScene(characterId: Long, onResult: (Result<Unit>) -> Unit) =
        launchSceneAction(characterId, onResult) { individualId ->
            digilineGateway.ensureScene(individualId, wild = false)
        }

    override fun chooseSceneOption(characterId: Long, optionId: String, onResult: (Result<Unit>) -> Unit) =
        launchSceneAction(characterId, onResult) { individualId ->
            digilineGateway.choose(individualId, optionId)
        }

    override fun dismissScene(characterId: Long, onResult: (Result<Unit>) -> Unit) =
        launchSceneAction(characterId, onResult) { individualId ->
            digilineGateway.dismiss(individualId)
        }

    override fun isLegacyLlmEnabled(): Boolean =
        com.tamerslab.di.AppServices.debugSettingsRepository.legacyLlmEnabled

    override fun getPersonalitySyncState(characterId: Long): Flow<PersonalitySyncUiState> =
        kotlinx.coroutines.flow.flow {
            val individualId = ownedRepository.individualIdForCharacter(characterId)
            if (individualId == null) {
                emit(PersonalitySyncUiState.NONE)
                return@flow
            }
            emitAll(
                ownedRepository.observeNativeSync(individualId).map { entity ->
                    when (entity?.state) {
                        "PENDING" -> PersonalitySyncUiState.PENDING
                        "WRITTEN" -> PersonalitySyncUiState.WRITTEN
                        null -> PersonalitySyncUiState.NONE
                        else -> PersonalitySyncUiState.UNAVAILABLE
                    }
                }
            )
        }

    override fun isShiftNoticePending(characterId: Long, onResult: (Boolean) -> Unit) {
        componentActivity.lifecycleScope.launch(Dispatchers.IO) {
            val individualId = ownedRepository.individualIdForCharacter(characterId)
            val pending = individualId != null && ownedRepository.shiftNoticePending(individualId)
            componentActivity.runOnUiThread { onResult(pending) }
        }
    }

    override fun markShiftNoticeShown(characterId: Long) {
        componentActivity.lifecycleScope.launch(Dispatchers.IO) {
            val individualId = ownedRepository.individualIdForCharacter(characterId) ?: return@launch
            ownedRepository.markShiftNoticeShown(individualId)
        }
    }

    private fun launchSceneAction(
        characterId: Long,
        onResult: (Result<Unit>) -> Unit,
        action: suspend (individualId: String) -> com.tamerslab.digiline.gateway.DigilineResult,
    ) {
        componentActivity.lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching {
                val individualId = ownedRepository.individualIdForCharacter(characterId)
                    ?: error("This partner has no active character record.")
                when (val outcome = action(individualId)) {
                    is com.tamerslab.digiline.gateway.DigilineResult.Error ->
                        error(outcome.message)
                    else -> Unit
                }
            }
            componentActivity.runOnUiThread { onResult(result) }
        }
    }

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
