package com.github.nacabaro.vbhelper.screens.worldScreen

import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.github.nacabaro.vbhelper.chat.ChatRepository
import com.github.nacabaro.vbhelper.chat.PromptLocalization
import com.github.nacabaro.vbhelper.di.VBHelper
import com.github.nacabaro.vbhelper.domain.chat.ChatMessageEntity
import com.github.nacabaro.vbhelper.domain.world.RecruitmentState
import com.github.nacabaro.vbhelper.domain.world.WorldSpawn
import com.github.nacabaro.vbhelper.world.WildMoodAnalyzer
import com.github.nacabaro.vbhelper.world.WorldRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed class WildChatEvent {
    object None : WildChatEvent()
    data class Recruited(val message: String) : WildChatEvent()
    data class Pending(val message: String) : WildChatEvent()
    data class Vanished(val message: String) : WildChatEvent()
    /** Digimon started following after the first message raised mood. */
    data class StartedFollowing(val message: String) : WildChatEvent()
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

    fun observeIsFollowing(individualId: String): Flow<Boolean?> =
        worldRepository.observeIsFollowing(individualId)

    fun sendMessage(
        individualId: String,
        cardCharacterId: Long,
        text: String,
        onResult: (Result<WildChatEvent>) -> Unit
    ) {
        componentActivity.lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching {
                // Detect first exchange before inserting the new messages.
                val priorMessages = chatRepository.getHistoryForIndividual(individualId).first()
                val isFirstMessage = priorMessages.isEmpty()

                val chatResult = chatRepository.sendMessageForWildEncounter(individualId, cardCharacterId, text)
                val delta = WildMoodAnalyzer.resolveDelta(text, chatResult.reply, chatResult.moodDelta)
                val scaledDelta = WildMoodAnalyzer.scaleDelta(delta)
                val newMood = worldRepository.applyWildMoodDelta(individualId, delta)
                    ?: return@runCatching WildChatEvent.None
                val spawn = worldRepository.getSpawnEntityByIndividualId(individualId)
                    ?: return@runCatching WildChatEvent.None

                if (spawn.recruitmentState != RecruitmentState.WILD) {
                    return@runCatching WildChatEvent.None
                }

                val languageTag = PromptLocalization.currentLanguageTag()

                // Activate temporary following when the first message raised mood.
                var startedFollowing = false
                if (isFirstMessage && scaledDelta > 0 && !spawn.isFollowing &&
                    newMood >= WorldSpawn.FOLLOW_STOP_MOOD
                ) {
                    val lat = worldRepository.lastKnownLatitude
                    val lon = worldRepository.lastKnownLongitude
                    if (lat != null && lon != null) {
                        startedFollowing = worldRepository.startFollowing(individualId, lat, lon)
                    }
                }

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
                    startedFollowing -> WildChatEvent.StartedFollowing(chatResult.reply)
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
