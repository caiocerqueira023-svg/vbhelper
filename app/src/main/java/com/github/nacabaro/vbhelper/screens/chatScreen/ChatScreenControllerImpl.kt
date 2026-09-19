package com.github.nacabaro.vbhelper.screens.chatScreen

import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.github.nacabaro.vbhelper.chat.ChatRepository
import com.github.nacabaro.vbhelper.di.VBHelper
import com.github.nacabaro.vbhelper.domain.chat.ChatMessageEntity
import com.github.nacabaro.vbhelper.species.SpeciesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class ChatScreenControllerImpl(
    private val componentActivity: ComponentActivity
) : ChatScreenController {
    private val application = componentActivity.applicationContext as VBHelper
    private val chatRepository = ChatRepository(
        database = application.container.db,
        llmSettingsRepository = application.container.llmSettingsRepository,
        lorebookRepository = application.container.lorebookRepository,
        speciesRepository = application.container.speciesRepository
    )
    private val database = application.container.db
    private val speciesRepository = SpeciesRepository(database, application.container.speciesSettingsRepository)

    override fun getHistory(characterId: Long): Flow<List<ChatMessageEntity>> =
        chatRepository.getHistory(characterId)

    override fun getMood(characterId: Long): Flow<Int> =
        database.userCharacterDao().observeMood(characterId)

    override fun sendMessage(characterId: Long, text: String, onResult: (Result<String>) -> Unit) {
        componentActivity.lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching { chatRepository.sendMessage(characterId, text) }
            componentActivity.runOnUiThread { onResult(result) }
        }
    }

    override fun deleteFromMessage(characterId: Long, messageId: Long) {
        componentActivity.lifecycleScope.launch(Dispatchers.IO) {
            val individualId = database.userCharacterDao().getCharacter(characterId).individualId
            database.chatDao().deleteFromMessage(individualId, messageId)
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
                val individualId = database.userCharacterDao().getCharacter(characterId).individualId
                database.chatDao().deleteFromMessage(individualId, messageId)
                chatRepository.sendMessage(characterId, text)
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
            val userCharacter = database.userCharacterDao().getCharacter(characterId)
            val card = database.cardDao().getCardByCharacterIdSync(characterId)
            val profile = speciesRepository.getProfileForCharacter(userCharacter.charId)
            componentActivity.runOnUiThread {
                onResult(SpeciesContext(userCharacter.charId, card?.name.orEmpty(), profile))
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
        componentActivity.lifecycleScope.launch(Dispatchers.IO) {
            speciesRepository.saveManualProfile(cardCharacterId, name, level, type, profile, specialMoves)
            componentActivity.runOnUiThread(onSaved)
        }
    }
}
