package com.tamerslab.world.ui

import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.tamerslab.chat.ChatRepository
import com.tamerslab.chat.PromptLocalization
import com.tamerslab.di.TamersLabApplication
import com.tamerslab.domain.chat.ChatMessageEntity
import com.tamerslab.domain.world.RecruitmentState
import com.tamerslab.world.WildMoodAnalyzer
import com.tamerslab.world.WorldRepository
import com.tamerslab.world.ecosystem.WorldInteractionException
import com.tamerslab.world.ecosystem.InteractionFailure
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
    data class QuestUpdated(val message: String) : WildChatEvent()
    data class Vanished(val message: String) : WildChatEvent()
    data class Challenge(val id:String,val message:String,val sparring:Boolean=true,val accepted:Boolean=false) : WildChatEvent()
}

class WorldChatScreenControllerImpl(
    private val componentActivity: ComponentActivity
) {
    private val application = componentActivity.applicationContext as TamersLabApplication
    private val chatRepository: ChatRepository = com.tamerslab.di.AppServices.chatRepository
    private val worldRepository: WorldRepository = com.tamerslab.di.AppServices.worldRepository
    private val quests = com.tamerslab.di.AppServices.questRepository
    private val digilineGateway = com.tamerslab.di.AppServices.digilineGateway

    fun getScene(individualId: String): Flow<com.tamerslab.digiline.gateway.DigilineScene?> =
        digilineGateway.observeScene(individualId)

    fun ensureScene(individualId: String, onResult: (Result<Unit>) -> Unit) =
        launchSceneAction(individualId, onResult) { digilineGateway.ensureScene(individualId, wild = true) }

    fun chooseSceneOption(individualId: String, optionId: String, onResult: (Result<Unit>) -> Unit) =
        launchSceneAction(individualId, onResult) { digilineGateway.choose(individualId, optionId) }

    fun dismissScene(individualId: String, onResult: (Result<Unit>) -> Unit) =
        launchSceneAction(individualId, onResult) { digilineGateway.dismiss(individualId) }

    fun isLegacyLlmEnabled(): Boolean = com.tamerslab.di.AppServices.debugSettingsRepository.legacyLlmEnabled

    private fun launchSceneAction(
        individualId: String,
        onResult: (Result<Unit>) -> Unit,
        action: suspend () -> com.tamerslab.digiline.gateway.DigilineResult,
    ) {
        componentActivity.lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching {
                when (val outcome = action()) {
                    is com.tamerslab.digiline.gateway.DigilineResult.Error -> error(outcome.message)
                    else -> Unit
                }
            }
            componentActivity.runOnUiThread { onResult(result) }
        }
    }

    fun observeQuests(individualId: String) = quests.observeContact(individualId)
    fun observeQuestPartners() = quests.observePartners()

    fun prepareQuests(individualId: String) {
        componentActivity.lifecycleScope.launch(Dispatchers.IO) { quests.refreshGiver(individualId) }
    }

    private suspend fun applyQuestAction(individualId: String, action: com.tamerslab.quests.QuestDialogueAction,
                                         conversationId: String?): com.tamerslab.quests.QuestActionResult {
        val quest = requireNotNull(quests.quest(action.questId))
        require(quest.giverId == individualId && quest.revision == action.revision) { "Quest changed. Open its current details." }
        if (action.type == com.tamerslab.quests.QuestActionType.TURN_IN &&
            quest.category == com.tamerslab.quests.QuestCategory.RECRUITMENT) {
            worldRepository.recruitIndividual(individualId, conversationId).getOrThrow()
            return com.tamerslab.quests.QuestActionResult(quest.id, action.type,
                "Recruitment quest completed. ${quest.giverName} has joined Storage.", recruited = true)
        }
        return quests.act(individualId, action)
    }

    fun questAction(individualId: String, questId: String, revision: Long,
                    type: com.tamerslab.quests.QuestActionType, conversationId: String?,
                    partnerId: String? = null,
                    onResult: (Result<WildChatEvent>) -> Unit) {
        componentActivity.lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching {
                val applied = applyQuestAction(individualId,
                    com.tamerslab.quests.QuestDialogueAction(type, questId, revision, "button", partnerId), conversationId)
                val feedback = componentActivity.getString(when (type) {
                    com.tamerslab.quests.QuestActionType.ACCEPT -> com.tamerslab.R.string.quest_feedback_accepted
                    com.tamerslab.quests.QuestActionType.DECLINE -> com.tamerslab.R.string.quest_feedback_declined
                    com.tamerslab.quests.QuestActionType.DELIVER -> com.tamerslab.R.string.quest_feedback_delivered
                    com.tamerslab.quests.QuestActionType.COLLECT -> com.tamerslab.R.string.quest_feedback_collected
                    com.tamerslab.quests.QuestActionType.ABANDON -> com.tamerslab.R.string.quest_feedback_abandoned
                    com.tamerslab.quests.QuestActionType.STATUS -> com.tamerslab.R.string.quest_feedback_updated
                    com.tamerslab.quests.QuestActionType.SKIP_FOLLOW_UP -> com.tamerslab.R.string.quest_feedback_follow_up_skipped
                    com.tamerslab.quests.QuestActionType.TURN_IN -> if (applied.recruited)
                        com.tamerslab.R.string.ui_world_recruited_toast else com.tamerslab.R.string.quest_feedback_completed
                })
                chatRepository.insertNotice(individualId, feedback)
                // Presentation is optional and happens after the gameplay mutation has committed.
                componentActivity.lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        if (chatRepository.publicDialogueAvailable()) kotlinx.coroutines.withTimeout(20_000) {
                            val card = quests.quest(questId)?.giverCardCharacterId ?: return@withTimeout
                            chatRepository.triggerReactionForWildEncounter(individualId, card,
                                "App-confirmed quest action: ${applied.message}\n" +
                                    "React naturally in ${PromptLocalization.currentLanguageTag()} using only this committed result. " +
                                    "Do not invent progress, rewards, new quests or actions.\n${quests.contextForGiver(individualId)}")
                        }
                    } catch (failure: Exception) {
                        if (failure is CancellationException && failure !is kotlinx.coroutines.TimeoutCancellationException) throw failure
                    }
                }
                if (applied.recruited) WildChatEvent.Recruited(feedback) else WildChatEvent.None
            }
            result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
            componentActivity.runOnUiThread { onResult(result) }
        }
    }

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
        replacingMessageId: Long? = null,
        onResult: (Result<WildChatEvent>) -> Unit
    ) {
        componentActivity.lifecycleScope.launch(Dispatchers.IO) {
            val requestId = conversationId ?: "private-chat:${java.util.UUID.randomUUID()}"
            val result = runCatching {
                try {
                    sendClaimedMessage(requestId, individualId, cardCharacterId, text,conversationId!=null, replacingMessageId)
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

    private suspend fun sendClaimedMessage(requestId: String, individualId: String, cardCharacterId: Long, text: String,existingConversation:Boolean, replacingMessageId: Long? = null): WildChatEvent {
        val chat = if(existingConversation) {
            worldRepository.interactions.renewPrivateChat(requestId,individualId)
            worldRepository.interactions.interaction(requestId)
        } else worldRepository.interactions.beginPrivateChat(requestId, individualId)
        val chatResult = chatRepository.sendMessageForWildEncounter(individualId, cardCharacterId, text, replacingMessageId) { action ->
            applyQuestAction(individualId, action, chat?.id)
        }
        if (replacingMessageId != null) return WildChatEvent.None
        if (chatResult.questResult?.recruited == true) return WildChatEvent.Recruited(chatResult.reply)
        val delta = if (chatResult.questResult != null) 0 else WildMoodAnalyzer.resolveDelta(text, chatResult.reply, chatResult.moodDelta)
        val newMood = worldRepository.applyWildMoodDelta(individualId, delta, chat?.id) ?: return WildChatEvent.None
        val spawn = worldRepository.getSpawnEntityByIndividualId(individualId)
        val hadRecruitment = quests.giverQuests(individualId).any {
            it.category == com.tamerslab.quests.QuestCategory.RECRUITMENT
        }
        quests.ensureOffers(individualId)
        val languageTag = PromptLocalization.currentLanguageTag()
        val proposal=chatResult.intent
        val challengeId=proposal?.let { com.tamerslab.di.AppServices.worldInteractionOrchestrator.recordPrivateProposal(chat?.id,
            individualId,cardCharacterId,it,chatResult.sourceMessageIds,chatResult.userMessageId) }
        if(challengeId!=null) {
            val intent=worldRepository.interactions.intent(challengeId)!!
            return WildChatEvent.Challenge(challengeId,proposal.reason,intent.sparring,intent.type==com.tamerslab.world.ecosystem.DialogueIntentType.ACCEPT_CHALLENGE)
        }

        return when {
            newMood <= 0 && spawn != null && quests.unfinishedTargets(individualId) == 0 -> {
                val farewell = chatRepository.triggerReactionForWildEncounter(
                    individualId, cardCharacterId, PromptLocalization.wildMoodZeroInstruction(languageTag)
                )
                if (!worldRepository.removeSpawn(spawn.id, chat?.id)) throw WorldInteractionException(InteractionFailure.UNAVAILABLE)
                WildChatEvent.Vanished(farewell)
            }
            newMood >= 100 -> {
                if (hadRecruitment) return WildChatEvent.None
                val invitation = try { chatRepository.reactToPendingQuestOffers(individualId, cardCharacterId).lastOrNull()
                    ?: componentActivity.getString(com.tamerslab.R.string.quest_recruitment_unlocked) }
                catch (failure: Exception) {
                    if (failure is CancellationException) throw failure
                    componentActivity.getString(com.tamerslab.R.string.quest_recruitment_unlocked)
                }
                WildChatEvent.QuestUpdated(invitation)
            }
            else -> WildChatEvent.None
        }
    }

    fun deleteFromMessage(individualId: String, messageId: Long) {
        componentActivity.lifecycleScope.launch(Dispatchers.IO) {
            chatRepository.deleteFromMessageForIndividual(individualId, messageId)
        }
    }

    fun declineChallenge(id:String) { componentActivity.lifecycleScope.launch(Dispatchers.IO) { com.tamerslab.di.AppServices.worldInteractionOrchestrator.declineIntent(id) } }

    fun resendMessage(
        individualId: String,
        cardCharacterId: Long,
        messageId: Long,
        text: String,
        conversationId: String? = null,
        onResult: (Result<WildChatEvent>) -> Unit
    ) {
        sendMessage(individualId, cardCharacterId, text, conversationId, replacingMessageId = messageId, onResult = onResult)
    }
}

