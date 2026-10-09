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
    data class QuestUpdated(val message: String) : WildChatEvent()
    data class Vanished(val message: String) : WildChatEvent()
    data class Challenge(val id:String,val message:String,val sparring:Boolean=true,val accepted:Boolean=false) : WildChatEvent()
}

class WorldChatScreenControllerImpl(
    private val componentActivity: ComponentActivity
) {
    private val application = componentActivity.applicationContext as VBHelper
    private val chatRepository: ChatRepository = application.container.chatRepository
    private val worldRepository: WorldRepository = application.container.worldRepository
    private val quests = application.container.questRepository

    fun observeQuests(individualId: String) = quests.observeContact(individualId)
    fun observeQuestPartners() = quests.observePartners()

    fun prepareQuests(individualId: String) {
        componentActivity.lifecycleScope.launch(Dispatchers.IO) { quests.refreshGiver(individualId) }
    }

    private suspend fun applyQuestAction(individualId: String, action: com.github.nacabaro.vbhelper.quests.QuestDialogueAction,
                                         conversationId: String?): com.github.nacabaro.vbhelper.quests.QuestActionResult {
        val quest = requireNotNull(application.container.db.questDao().getQuest(action.questId))
        require(quest.giverId == individualId && quest.revision == action.revision) { "Quest changed. Open its current details." }
        if (action.type == com.github.nacabaro.vbhelper.quests.QuestActionType.TURN_IN &&
            quest.category == com.github.nacabaro.vbhelper.quests.QuestCategory.RECRUITMENT) {
            worldRepository.recruitIndividual(individualId, conversationId).getOrThrow()
            return com.github.nacabaro.vbhelper.quests.QuestActionResult(quest.id, action.type,
                "Recruitment quest completed. ${quest.giverName} has joined Storage.", recruited = true)
        }
        return quests.act(individualId, action)
    }

    fun questAction(individualId: String, questId: String, revision: Long,
                    type: com.github.nacabaro.vbhelper.quests.QuestActionType, conversationId: String?,
                    partnerId: String? = null,
                    onResult: (Result<WildChatEvent>) -> Unit) {
        componentActivity.lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching {
                val applied = applyQuestAction(individualId,
                    com.github.nacabaro.vbhelper.quests.QuestDialogueAction(type, questId, revision, "button", partnerId), conversationId)
                val feedback = componentActivity.getString(when (type) {
                    com.github.nacabaro.vbhelper.quests.QuestActionType.ACCEPT -> com.github.nacabaro.vbhelper.R.string.quest_feedback_accepted
                    com.github.nacabaro.vbhelper.quests.QuestActionType.DECLINE -> com.github.nacabaro.vbhelper.R.string.quest_feedback_declined
                    com.github.nacabaro.vbhelper.quests.QuestActionType.DELIVER -> com.github.nacabaro.vbhelper.R.string.quest_feedback_delivered
                    com.github.nacabaro.vbhelper.quests.QuestActionType.COLLECT -> com.github.nacabaro.vbhelper.R.string.quest_feedback_collected
                    com.github.nacabaro.vbhelper.quests.QuestActionType.ABANDON -> com.github.nacabaro.vbhelper.R.string.quest_feedback_abandoned
                    com.github.nacabaro.vbhelper.quests.QuestActionType.STATUS -> com.github.nacabaro.vbhelper.R.string.quest_feedback_updated
                    com.github.nacabaro.vbhelper.quests.QuestActionType.SKIP_FOLLOW_UP -> com.github.nacabaro.vbhelper.R.string.quest_feedback_follow_up_skipped
                    com.github.nacabaro.vbhelper.quests.QuestActionType.TURN_IN -> if (applied.recruited)
                        com.github.nacabaro.vbhelper.R.string.ui_world_recruited_toast else com.github.nacabaro.vbhelper.R.string.quest_feedback_completed
                })
                application.container.db.chatDao().insertMessage(ChatMessageEntity(individualId = individualId,
                    role = "assistant", content = feedback, timestamp = System.currentTimeMillis()))
                // Presentation is optional and happens after the gameplay mutation has committed.
                componentActivity.lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        if (chatRepository.publicDialogueAvailable()) kotlinx.coroutines.withTimeout(20_000) {
                            val card = application.container.db.questDao().getQuest(questId)?.giverCardCharacterId ?: return@withTimeout
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
        val chatResult = chatRepository.sendMessageForWildEncounter(individualId, cardCharacterId, text) { action ->
            applyQuestAction(individualId, action, chat?.id)
        }
        if (chatResult.questResult?.recruited == true) return WildChatEvent.Recruited(chatResult.reply)
        val delta = if (chatResult.questResult != null) 0 else WildMoodAnalyzer.resolveDelta(text, chatResult.reply, chatResult.moodDelta)
        val newMood = worldRepository.applyWildMoodDelta(individualId, delta, chat?.id) ?: return WildChatEvent.None
        val spawn = worldRepository.getSpawnEntityByIndividualId(individualId)
        val hadRecruitment = application.container.db.questDao().forGiver(individualId).any {
            it.category == com.github.nacabaro.vbhelper.quests.QuestCategory.RECRUITMENT
        }
        quests.ensureOffers(individualId)
        val languageTag = PromptLocalization.currentLanguageTag()
        val proposal=chatResult.intent
        val challengeId=proposal?.let { application.container.worldInteractionOrchestrator.recordPrivateProposal(chat?.id,
            individualId,cardCharacterId,it,chatResult.sourceMessageIds,chatResult.userMessageId) }
        if(challengeId!=null) {
            val intent=application.container.db.worldInteractionDao().getIntent(challengeId)!!
            return WildChatEvent.Challenge(challengeId,proposal.reason,intent.sparring,intent.type==com.github.nacabaro.vbhelper.world.ecosystem.DialogueIntentType.ACCEPT_CHALLENGE)
        }

        return when {
            newMood <= 0 && spawn != null && application.container.db.questDao().unfinishedTargetCount(individualId) == 0 -> {
                val farewell = chatRepository.triggerReactionForWildEncounter(
                    individualId, cardCharacterId, PromptLocalization.wildMoodZeroInstruction(languageTag)
                )
                if (!worldRepository.removeSpawn(spawn.id, chat?.id)) throw WorldInteractionException(InteractionFailure.UNAVAILABLE)
                WildChatEvent.Vanished(farewell)
            }
            newMood >= 100 -> {
                if (hadRecruitment) return WildChatEvent.None
                val invitation = try { chatRepository.reactToPendingQuestOffers(individualId, cardCharacterId).lastOrNull()
                    ?: componentActivity.getString(com.github.nacabaro.vbhelper.R.string.quest_recruitment_unlocked) }
                catch (failure: Exception) {
                    if (failure is CancellationException) throw failure
                    componentActivity.getString(com.github.nacabaro.vbhelper.R.string.quest_recruitment_unlocked)
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
