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
import com.github.nacabaro.vbhelper.world.ecosystem.WorldInteractionException
import com.github.nacabaro.vbhelper.world.ecosystem.InteractionFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

sealed class WildChatEvent {
    object None : WildChatEvent()
    data class Recruited(val message: String) : WildChatEvent()
    data class Pending(val message: String) : WildChatEvent()
    data class Vanished(val message: String) : WildChatEvent()
    data class Challenge(val id:String,val message:String,val sparring:Boolean=true,val accepted:Boolean=false) : WildChatEvent()
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

    fun markInteracted(individualId: String) {
        componentActivity.lifecycleScope.launch(Dispatchers.IO) {
            worldRepository.markInteractedByIndividual(individualId)
        }
    }

    fun sendMessage(
        individualId: String,
        cardCharacterId: Long,
        text: String,
        conversationId: String? = null,
        onResult: (Result<WildChatEvent>) -> Unit
    ) {
        componentActivity.lifecycleScope.launch(Dispatchers.IO) {
            val requestId = conversationId ?: "private-chat:${java.util.UUID.randomUUID()}"
            val result = runCatching {
                try {
                    sendClaimedMessage(requestId, individualId, cardCharacterId, text,conversationId!=null)
                } finally {
                    if(conversationId==null) withContext(NonCancellable) { worldRepository.interactions.finishPrivateChat(requestId, individualId) }
                }
            }
            result.exceptionOrNull()?.let { failure -> if (failure is CancellationException) throw failure }
            val localized = result.fold(
                onSuccess = { Result.success(it) },
                onFailure = { failure -> Result.failure<WildChatEvent>(
                    if (failure is WorldInteractionException) IllegalStateException(componentActivity.getString(failure.messageResource()), failure)
                    else failure
                ) }
            )
            componentActivity.runOnUiThread { onResult(localized) }
        }
    }

    private suspend fun sendClaimedMessage(requestId: String, individualId: String, cardCharacterId: Long, text: String,existingConversation:Boolean): WildChatEvent {
        val chat = if(existingConversation) {
            worldRepository.interactions.renewPrivateChat(requestId,individualId)
            application.container.db.worldInteractionDao().getInteraction(requestId)
        } else worldRepository.interactions.beginPrivateChat(requestId, individualId)
        val chatResult = chatRepository.sendMessageForWildEncounter(individualId, cardCharacterId, text)
        val delta = WildMoodAnalyzer.resolveDelta(text, chatResult.reply, chatResult.moodDelta)
        val newMood = worldRepository.applyWildMoodDelta(individualId, delta, chat?.id) ?: return WildChatEvent.None
        val spawn = worldRepository.getSpawnEntityByIndividualId(individualId)
        if (spawn != null && spawn.recruitmentState != RecruitmentState.WILD) return WildChatEvent.None
        val languageTag = PromptLocalization.currentLanguageTag()
        val proposal=chatResult.intent
        val challengeId=proposal?.let { application.container.worldInteractionOrchestrator.recordPrivateProposal(chat?.id,
            individualId,cardCharacterId,it,chatResult.sourceMessageIds,chatResult.userMessageId) }
        if(challengeId!=null) {
            val intent=application.container.db.worldInteractionDao().getIntent(challengeId)!!
            return WildChatEvent.Challenge(challengeId,proposal.reason,intent.sparring,intent.type==com.github.nacabaro.vbhelper.world.ecosystem.DialogueIntentType.ACCEPT_CHALLENGE)
        }

        return when {
            newMood <= 0 && spawn != null -> {
                val farewell = chatRepository.triggerReactionForWildEncounter(
                    individualId, cardCharacterId, PromptLocalization.wildMoodZeroInstruction(languageTag)
                )
                if (!worldRepository.removeSpawn(spawn.id, chat?.id)) throw WorldInteractionException(InteractionFailure.UNAVAILABLE)
                WildChatEvent.Vanished(farewell)
            }
            newMood >= 100 -> {
                val requirementsMet = worldRepository.meetsRecruitmentRequirements()
                val joinMessage = chatRepository.triggerReactionForWildEncounter(
                    individualId, cardCharacterId, PromptLocalization.wildMoodMaxedInstruction(languageTag, requirementsMet)
                )
                if (requirementsMet) {
                    if (spawn != null) worldRepository.recruitSpawn(spawn.id, chat?.id).getOrThrow()
                    else worldRepository.recruitIndividual(individualId).getOrThrow()
                    WildChatEvent.Recruited(joinMessage)
                } else {
                    worldRepository.markPendingRecruitment(individualId, chat?.id)
                    WildChatEvent.Pending(joinMessage)
                }
            }
            else -> WildChatEvent.None
        }
    }

    fun deleteFromMessage(individualId: String, messageId: Long) {
        componentActivity.lifecycleScope.launch(Dispatchers.IO) {
            chatRepository.deleteFromMessageForIndividual(individualId, messageId)
        }
    }

    fun declineChallenge(id:String) { componentActivity.lifecycleScope.launch(Dispatchers.IO) { application.container.worldInteractionOrchestrator.declineIntent(id) } }

    fun resendMessage(
        individualId: String,
        cardCharacterId: Long,
        messageId: Long,
        text: String,
        conversationId: String? = null,
        onResult: (Result<WildChatEvent>) -> Unit
    ) {
        componentActivity.lifecycleScope.launch(Dispatchers.IO) {
            val deletion = runCatching {
                chatRepository.deleteFromMessageForIndividual(individualId, messageId)
            }
            if (deletion.isFailure) {
                componentActivity.runOnUiThread { onResult(Result.failure(deletion.exceptionOrNull()!!)) }
                return@launch
            }
            sendMessage(individualId, cardCharacterId, text,conversationId,onResult)
        }
    }
}
