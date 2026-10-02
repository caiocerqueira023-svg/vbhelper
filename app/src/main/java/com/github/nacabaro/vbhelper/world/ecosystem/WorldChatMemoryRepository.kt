package com.github.nacabaro.vbhelper.world.ecosystem

import androidx.room.withTransaction
import com.github.nacabaro.vbhelper.battle.offline.core.BattleOutcome
import com.github.nacabaro.vbhelper.chat.PromptLocalization
import com.github.nacabaro.vbhelper.chat.WorldDialogueCodec
import com.github.nacabaro.vbhelper.database.AppDatabase
import com.github.nacabaro.vbhelper.domain.chat.ChatMessageEntity
import com.google.gson.Gson

data class WorldPrivateChatTarget(val individualId: String, val cardCharacterId: Long, val name: String)

class WorldChatMemoryRepository(private val db: AppDatabase, private val now: ()->Long = System::currentTimeMillis) {
    private val gson=Gson()

    suspend fun adoptPlayerConversation(id: String, close: Boolean = true): WorldPrivateChatTarget? = db.withTransaction {
        val dao=db.worldInteractionDao()
        val event=dao.getInteraction(id) ?: return@withTransaction null
        if(event.type!=InteractionType.CHAT || event.publicReason?.startsWith("WILD_CHAT:")!=true) return@withTransaction null
        val participant=dao.getParticipants(id).singleOrNull { it.role==InteractionRole.WILD } ?: return@withTransaction null
        val cardId=participant.cardCharacterId ?: return@withTransaction null
        val messages=dao.getMessages(id)
        for(message in messages) {
            if(db.worldChatMemoryDao().getLink(message.id)!=null) continue
            val role=when {
                message.source==DialogueTextSource.PLAYER && message.speakerId=="trainer" -> "user"
                message.speakerId==participant.individualId && message.source in listOf(DialogueTextSource.MODEL,DialogueTextSource.AUTHORED) -> "assistant"
                message.source==DialogueTextSource.SYSTEM -> "system"
                else -> continue
            }
            val text=if(role=="assistant") WorldDialogueCodec.visibleText(message.body,participant.individualId)
                ?: WorldDialogueCodec.unreadableReply(PromptLocalization.currentLanguageTag()) else message.body
            val privateId=db.chatDao().insertMessage(ChatMessageEntity(individualId=participant.individualId,role=role,content=text,timestamp=message.createdAt))
            db.worldChatMemoryDao().insertLink(WorldPrivateChatLink(message.id,participant.individualId,privateId))
        }
        if(close && !event.state.terminal) {
            dao.updateInteraction(event.copy(state=InteractionState.ENDED,revision=event.revision+1,endedAt=now(),terminalReason="PRIVATE_CHAT_UNIFIED"))
            dao.releaseClaims(id)
        }
        WorldPrivateChatTarget(participant.individualId,cardId,participantName(participant))
    }

    suspend fun adoptAvailablePlayerConversation(individualId: String) = db.withTransaction {
        db.worldInteractionDao().getOpenInteractions().filter { it.type==InteractionType.CHAT && it.publicReason?.startsWith("WILD_CHAT:")==true }
            .forEach { event -> if(db.worldInteractionDao().getParticipants(event.id).singleOrNull()?.individualId==individualId) adoptPlayerConversation(event.id) }
    }

    suspend fun capturePrivateBattle(eventId: String, intent: WorldDialogueIntent) {
        val participant=db.worldInteractionDao().getParticipants(eventId).singleOrNull { it.role==InteractionRole.WILD && it.individualId==intent.initiatorId }
            ?: throw WorldInteractionException(InteractionFailure.UNAVAILABLE)
        val history=db.chatDao().getMessagesSync(intent.initiatorId)
        val evidence=gson.fromJson(intent.evidenceIdsJson,Array<String>::class.java).mapNotNull { it.removePrefix("private:").toLongOrNull() }.toSet()
        val last=evidence.maxOrNull() ?: throw WorldInteractionException(InteractionFailure.UNAVAILABLE)
        if(!history.map { it.id }.containsAll(evidence)) throw WorldInteractionException(InteractionFailure.UNAVAILABLE)
        val context=history.filter { it.id<=last }.takeLast(12).map { message ->
            BattleConversationTurn(message.role,if(message.role=="assistant")participantName(participant) else "Trainer",
                if(message.role=="assistant") WorldDialogueCodec.visibleText(message.content,intent.initiatorId).orEmpty().take(1000) else message.content.take(1000))
        }
        db.worldChatMemoryDao().saveContext(WorldBattleContext(eventId,intent.sparring,intent.initiatorId,intent.reason,gson.toJson(context),now()))
    }

    suspend fun recordBattle(event: WorldInteraction, participants: List<WorldInteractionParticipant>, outcome: BattleOutcome,
        context: WorldBattleContext?) {
        val friendly=context?.friendly ?: event.isFriendlyBattle
        for(participant in participants) {
            val name=participantName(participant)
            val opponents=participants.filter { it.side!=participant.side && it.side!=InteractionSide.NEUTRAL }
            val perspective=when(outcome) {
                BattleOutcome.ALLIED_VICTORY -> if(participant.side==InteractionSide.ALLIED) BattleMemoryPerspective.WON else BattleMemoryPerspective.LOST
                BattleOutcome.OPPOSING_VICTORY -> if(participant.side==InteractionSide.OPPOSING) BattleMemoryPerspective.WON else BattleMemoryPerspective.LOST
                BattleOutcome.DRAW -> BattleMemoryPerspective.DRAW
                BattleOutcome.ABANDONED -> BattleMemoryPerspective.ABANDONED
            }
            val opponentNames=opponents.map { participantName(it) }.joinToString(" + ")
            val memory=WorldBattleMemory(event.id,participant.individualId,participant.cardCharacterId,name,
                opponentNames,perspective,friendly,context?.reason ?: event.publicReason.orEmpty(),
                context?.transcriptJson ?: "[]",now(),needsReaction=participant.individualId==context?.chatIndividualId)
            if(db.worldChatMemoryDao().insertMemory(memory)!=-1L && memory.needsReaction) {
                db.chatDao().insertMessage(ChatMessageEntity(individualId=memory.individualId,role="system",
                    content=WorldBattleMemoryPrompts.record(memory,PromptLocalization.currentLanguageTag()),timestamp=now()))
            }
        }
    }

    suspend fun participantName(participant: WorldInteractionParticipant): String =
        participant.spawnId?.let { db.worldSpawnDao().getSpawnById(it)?.speciesName }
            ?: participant.cardCharacterId?.let { db.speciesProfileDao().getByCardCharacterId(it)?.let { profile -> profile.matchedName ?: profile.speciesName } }
            ?: "Digimon"
}

val WorldInteraction.isFriendlyBattle: Boolean get() = publicReason?.startsWith("SPARRING:")==true || publicReason?.startsWith("WILD_SPARRING:")==true
