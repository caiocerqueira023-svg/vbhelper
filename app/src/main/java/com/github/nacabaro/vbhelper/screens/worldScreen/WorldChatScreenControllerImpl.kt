package com.github.nacabaro.vbhelper.screens.worldScreen

import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.github.nacabaro.vbhelper.chat.ChatRepository
import com.github.nacabaro.vbhelper.chat.PromptLocalization
import com.github.nacabaro.vbhelper.di.VBHelper
import com.github.nacabaro.vbhelper.domain.chat.ChatMessageEntity
import com.github.nacabaro.vbhelper.domain.world.RecruitmentState
import com.github.nacabaro.vbhelper.world.WildMoodAnalyzer
import com.github.nacabaro.vbhelper.world.WorldRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

sealed class WildChatEvent {
    object None : WildChatEvent()
    data class Recruited(val message: String) : WildChatEvent()
    data class Pending(val message: String) : WildChatEvent()
    data class Vanished(val message: String) : WildChatEvent()
}

class WorldChatScreenControllerImpl(
    private val componentActivity: ComponentActivity
) {
    private val application = componentActivity.applicationContext as VBHelper
    private val chatRepository: ChatRepository = application.container.chatRepository
    private val worldRepository: WorldRepository = application.container.worldRepository

    fun getHistory(individualId: String): Flow<List<ChatMessageEntity>> =
        chatRepository.getHistoryForIndividual(individualId)

    fun observeMood(individualId: String): Flow<Int?> =
        worldRepository.observeMood(individualId)

    fun sendMessage(
        individualId: String,
        cardCharacterId: Long,
        text: String,
        onResult: (Result<WildChatEvent>) -> Unit
    ) {
        componentActivity.lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching {
                val chatResult = chatRepository.sendMessageForWildEncounter(individualId, cardCharacterId, text)
                val delta = WildMoodAnalyzer.resolveDelta(text, chatResult.reply, chatResult.moodDelta)
                val newMood = worldRepository.applyWildMoodDelta(individualId, delta)
                    ?: return@runCatching WildChatEvent.None
                val spawn = worldRepository.getSpawnEntityByIndividualId(individualId)
                    ?: return@runCatching WildChatEvent.None

                if (spawn.recruitmentState != RecruitmentState.WILD) {
                    return@runCatching WildChatEvent.None
                }

                val languageTag = PromptLocalization.currentLanguageTag()
                when {
                    newMood <= 0 -> {
                        val farewell = chatRepository.triggerReactionForWildEncounter(
                            individualId, cardCharacterId,
                            PromptLocalization.wildMoodZeroInstruction(languageTag)
                        )
                        worldRepository.removeSpawn(spawn.id)
                        WildChatEvent.Vanished(farewell)
                    }
                    newMood >= 100 -> {
                        val requirementsMet = worldRepository.meetsRecruitmentRequirements()
                        val joinMessage = chatRepository.triggerReactionForWildEncounter(
                            individualId, cardCharacterId,
                            PromptLocalization.wildMoodMaxedInstruction(languageTag, requirementsMet)
                        )
                        if (requirementsMet) {
                            worldRepository.recruitSpawn(spawn.id)
                            WildChatEvent.Recruited(joinMessage)
                        } else {
                            worldRepository.markPendingRecruitment(spawn.id)
                            WildChatEvent.Pending(joinMessage)
                        }
                    }
                    else -> WildChatEvent.None
                }
            }
            componentActivity.runOnUiThread { onResult(result) }
        }
    }

    fun deleteFromMessage(individualId: String, messageId: Long) {
        componentActivity.lifecycleScope.launch(Dispatchers.IO) {
            chatRepository.deleteFromMessageForIndividual(individualId, messageId)
        }
    }
}
