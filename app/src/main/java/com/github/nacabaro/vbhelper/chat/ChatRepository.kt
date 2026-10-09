package com.github.nacabaro.vbhelper.chat

import com.github.cfogrady.vbnfc.data.NfcCharacter
import com.github.nacabaro.vbhelper.daos.ChatDao
import com.github.nacabaro.vbhelper.chat.lorebook.LorebookRepository
import com.github.nacabaro.vbhelper.database.AppDatabase
import com.github.nacabaro.vbhelper.domain.chat.ChatMessageEntity
import com.github.nacabaro.vbhelper.domain.mood.MoodDirectiveParser
import com.github.nacabaro.vbhelper.world.WildMoodAnalyzer
import com.github.nacabaro.vbhelper.dtos.CharacterDtos
import com.github.nacabaro.vbhelper.source.LlmSettingsRepository
import com.github.nacabaro.vbhelper.source.StorageRepository
import com.github.nacabaro.vbhelper.species.SpeciesRepository
import com.github.nacabaro.vbhelper.utils.DeviceType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import androidx.room.withTransaction
import com.github.nacabaro.vbhelper.world.ecosystem.DialogueIntentType
import com.github.nacabaro.vbhelper.world.ecosystem.DialogueProposal
import com.github.nacabaro.vbhelper.world.ecosystem.WorldBattleMemoryPrompts
import com.github.nacabaro.vbhelper.world.ecosystem.WorldSocialRepository
import com.github.nacabaro.vbhelper.domain.personality.DigimonSocialProfile
import com.github.nacabaro.vbhelper.domain.personality.SocialEventAppraisal
import kotlinx.coroutines.withTimeout
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ChatRepository(
    private val database: AppDatabase,
    private val llmSettingsRepository: LlmSettingsRepository,
    private val lorebookRepository: LorebookRepository,
    private val speciesRepository: SpeciesRepository,
    private val chatDao: ChatDao = database.chatDao(),
    private val questRepository: com.github.nacabaro.vbhelper.quests.QuestRepository = com.github.nacabaro.vbhelper.quests.QuestRepository(database)
) {
    class MissingApiKeyException : Exception("Chat API key is not configured.")

    private val storageRepository = StorageRepository(database)
    private val completionMutex = Mutex()
    private val battleReactionMutex = Mutex()
    private val questOfferMutex = Mutex()

    fun getHistory(characterId: Long): Flow<List<ChatMessageEntity>> =
        database.userCharacterDao().getIndividualId(characterId)
            .flatMapLatest(chatDao::getMessages)

    fun getHistoryForIndividual(individualId: String): Flow<List<ChatMessageEntity>> =
        chatDao.getMessages(individualId)

    /**
     * Sends a message to a wild Digimon found on the World map that is not in storage.
     * Uses species data (CardCharacter) and the personality generated for this individualId at spawn.
     * Returns the reply and an optional mood marker; the caller applies the fallback when absent.
     */
    data class WildChatResult(val reply: String, val moodDelta: Int?,
        val intent: com.github.nacabaro.vbhelper.world.ecosystem.DialogueProposal? = null,
        val sourceMessageIds: List<Long> = emptyList(), val userMessageId: Long? = null,
        val questResult: com.github.nacabaro.vbhelper.quests.QuestActionResult? = null)

    suspend fun sendMessageForWildEncounter(
        individualId: String,
        cardCharacterId: Long,
        userText: String,
        questActionHandler: (suspend (com.github.nacabaro.vbhelper.quests.QuestDialogueAction) -> com.github.nacabaro.vbhelper.quests.QuestActionResult)? = null
    ): WildChatResult {
        require(userText.isNotBlank() && userText.length <= 12000)
        questRepository.refreshGiver(individualId)
        val (systemPrompt, speciesName) = buildWildSystemPrompt(cardCharacterId, individualId)
        val enrichedSystemPrompt = withLorebookContext(systemPrompt, userText, speciesName)

        val userMessageId=chatDao.insertMessage(
            ChatMessageEntity(
                individualId = individualId,
                role = "user",
                content = userText,
                timestamp = System.currentTimeMillis()
            )
        )
        val source=chatDao.getMessagesSync(individualId).takeLast(20)
        val contract="""
            Your entire message must be exactly one JSON object, with no surrounding prose, quotation marks or code fences.
            Return JSON {"lines":[{"speakerId":"$individualId","text":"your reply"}],"intent":null or
            {"type":"CHALLENGE_BATTLE|ACCEPT_CHALLENGE|DECLINE_CHALLENGE|DEESCALATE","speakerId":"$individualId",
            "targetIds":["trainer"],"evidenceIds":["this:$individualId"],"reason":"brief reason","sparring":false}}.
            A battle intent must come from the actual attributed conversation. A joke, quotation, hypothetical,
            mention of fighting, or explicit refusal is not a challenge or consent. The model cannot consent for the human.
            If your reply AGREES to the current player's request/acceptance of a duel, contest or wager, return ACCEPT_CHALLENGE.
            Set sparring:true for an agreed duel, including a wager, even if your personality's voice sounds aggressive.
            Its reason must preserve the actual agreed stakes for BOTH sides, each side's own concession stated plainly.
            Cite private:$userMessageId and this:$individualId as evidence.
            Do not accept jokes, quoted/hypothetical fights, ordinary unrelated agreement or refusals as a battle request.
            CHALLENGE_BATTLE is a new invitation/attack you initiate, not your affirmative answer to their offer.
            A grounded unilateral hostile attack can start without target agreement. Do not promise a battle without its matching intent.
            Allowed supporting IDs: ${source.joinToString { "private:${it.id}" }}. This line may support its own explicit challenge.
            Optionally add "questAction":null or {"type":"ACCEPT|DECLINE|COLLECT|DELIVER|STATUS|TURN_IN|ABANDON|SKIP_FOLLOW_UP",
            "questId":"exact saved quest ID","revision":0,"evidenceId":"private:$userMessageId","partnerId":null}.
            For ACCEPT only, partnerId may select an exact permanent individual ID from the available partner list
            when the player explicitly chose that partner. If the name is ambiguous, ask which one. Null uses
            the original bound partner when resuming, or the active partner for a new partner-dependent quest.
            Available stored partners:
            ${questRepository.partnerContext()}
            COLLECT receives this giver's package in the current step. DELIVER handles this giver's current
            supplies/quest-object handover. Recipient handovers, meetings and property recovery require Radar;
            do not propose them remotely or claim future steps are already completed. A participant is not the giver.
            A follow-up is a separate saved offer. Do not accept a planned/waiting follow-up by using the completed parent's ID.
            STATUS on the completed parent may retry preparing its queued follow-up. SKIP_FOLLOW_UP is only for
            an explicit request to end that pending continuation, not a request to wait or a casual refusal.
            Completed parents already granted rewards and cannot be turned in again. The new recruitment routes
            require their saved watch-preparation step before their app tasks unlock; do not waive stage-scaled requirements.
            Propose a quest action only when THIS player's current message actually requests it. Never interpret
            a quotation, hypothetical, refusal, past accomplishment, or ordinary agreement as acceptance/turn-in.
            Use a saved quest ID and its current revision from the registered quest context. If several quests fit,
            ask which one and return questAction:null. Do not invent objectives, rewards, locations or progress.
            A quest action is a proposal: do not announce its success until the app reports the committed result.
            Return intent:null when proposing a quest action. Maximum trust unlocks a recruitment quest; it never
            means you have already joined Storage. Only the app can verify requirements or grant rewards.
        """.trimIndent()
        val rawReply = requestCompletion(enrichedSystemPrompt, individualId, null, finalSystemInstruction=contract)
        val questAction = if (questActionHandler == null) null else com.github.nacabaro.vbhelper.quests.QuestDialogueCodec.parse(
            rawReply, individualId, database.questDao().forGiver(individualId), userMessageId,
            database.questDao().partners().map { it.individualId }.toSet())
        var questResult: com.github.nacabaro.vbhelper.quests.QuestActionResult? = null
        val committedReply = if (questAction != null && questActionHandler != null) {
            val result = try { questActionHandler(questAction) } catch (failure: Exception) {
                if (failure is kotlinx.coroutines.CancellationException) throw failure
                com.github.nacabaro.vbhelper.quests.QuestActionResult(questAction.questId, questAction.type,
                    "The quest action was not applied: ${failure.message.orEmpty()}")
            }
            questResult = result
            val instruction = "App-confirmed quest action result (authoritative): ${result.message}\n" +
                "Current registered quests:\n${questRepository.contextForContact(individualId)}\n" +
                "Respond naturally in your voice using only these facts. Do not emit actions or promise uncommitted rewards. Return plain text."
            try { requestCompletion(enrichedSystemPrompt, individualId, null, finalSystemInstruction = instruction) }
            catch (failure: Exception) {
                if (failure is kotlinx.coroutines.CancellationException) throw failure
                result.message
            }
        } else rawReply
        val structured=runCatching { WorldDialogueCodec.parseReadableExchange(committedReply,setOf(individualId),source.map { "private:${it.id}" }.toSet(),
            targetsAllowed=setOf(individualId,"trainer")) }.getOrNull()
        val extracted=MoodDirectiveParser.extract(committedReply)
        val cleanReply=structured?.lines?.single()?.text ?: WorldDialogueCodec.visibleText(committedReply,individualId)
            ?: WorldDialogueCodec.unreadableReply(PromptLocalization.currentLanguageTag())
        // The model sometimes agrees to a fight in prose but drops the JSON
        // intent (abbreviated type, long reason, missing envelope). Recover it
        // with one grounded follow-up call instead of losing the battle.
        val repairedIntent = if (structured?.intent == null && questAction == null &&
            (BattleTalkDetector.looksLikeBattleTalk(userText) || BattleTalkDetector.looksLikeBattleTalk(cleanReply))) {
            try {
                repairBattleIntent(enrichedSystemPrompt, individualId, userMessageId,
                    source.map { "private:${it.id}" }.toSet(), cleanReply)
            } catch (failure: Exception) {
                if (failure is kotlinx.coroutines.CancellationException &&
                    failure !is kotlinx.coroutines.TimeoutCancellationException) throw failure
                null
            }
        } else null
        val effectiveIntent = structured?.intent ?: repairedIntent
        val suppliedDelta=if(structured==null && cleanReply==extracted.first.trim()) extracted.second else null
        val moodDelta=if (questAction != null) 0 else SocialEventAppraisal.resolve(WorldSocialRepository(database).profile(individualId), userText, suppliedDelta)
        val replyId=chatDao.insertMessage(
            ChatMessageEntity(
                individualId = individualId,
                role = "assistant",
                content = cleanReply,
                timestamp = System.currentTimeMillis()
            )
        )
        return WildChatResult(cleanReply, moodDelta,if (questAction == null) effectiveIntent else null,
            source.map { it.id }+replyId,userMessageId,questResult)
    }

    /**
     * Asks the model to attach the matching structured battle intent to the reply
     * it just gave. The reply text is quoted back so the model must ground its
     * answer; a reply that did not agree to a fight must yield null. The result
     * still passes codec validation and the duel policy downstream. A repair is
     * never allowed to open a non-consensual hostile attack on its own.
     */
    private suspend fun repairBattleIntent(
        enrichedSystemPrompt: String,
        individualId: String,
        userMessageId: Long,
        evidence: Set<String>,
        replyText: String
    ): DialogueProposal? {
        val supporting = (evidence + "this:$individualId").joinToString()
        val contract = """
            Your previous reply is quoted below. Return ONLY one JSON object, no prose around it:
            {"lines":[{"speakerId":"$individualId","text":"<your reply, repeated unchanged>"}],
            "intent": <the battle intent matching your reply, or null>}.
            Intent schema when present: {"type":"CHALLENGE_BATTLE|ACCEPT_CHALLENGE|DECLINE_CHALLENGE|DEESCALATE",
            "speakerId":"$individualId","targetIds":["trainer"],"evidenceIds":["message ID or this:speakerId"],
            "reason":"brief context-grounded reason, at most 160 characters","sparring":false}.
            Set sparring:true only for an agreed friendly duel. Allowed supporting IDs: $supporting.
            This line may support its own explicit intent: private:$userMessageId and this:$individualId.
            If your reply did NOT agree to a duel, contest or fight, or if it refused one, return "intent":null.
            Do not invent agreement that is not in your reply.
            Previous reply: "$replyText"
        """.trimIndent()
        val raw = withTimeout(20_000) {
            requestCompletion(enrichedSystemPrompt, individualId, null, finalSystemInstruction = contract)
        }
        val proposal = runCatching {
            WorldDialogueCodec.parse(raw, setOf(individualId), evidence,
                targetsAllowed = setOf(individualId, "trainer")).intent
        }.getOrNull() ?: return null
        if (proposal.type == DialogueIntentType.CHALLENGE_BATTLE && !proposal.sparring) return null
        return proposal
    }

    suspend fun reactToPendingBattles(individualId: String, cardCharacterId: Long) = battleReactionMutex.withLock {
        for(memory in database.worldChatMemoryDao().getPendingReactions(individualId)) {
            val (persona,species)=buildWildSystemPrompt(cardCharacterId,individualId)
            val instruction=WorldBattleMemoryPrompts.reaction(memory)
            val prompt=withLorebookContext(persona,instruction,species)
            val raw=withTimeout(20_000) { requestCompletion(prompt,individualId,null,finalSystemInstruction=instruction) }
            val reply=WorldDialogueCodec.visibleText(raw,individualId) ?: error("The battle reaction was not readable. Please retry.")
            database.withTransaction {
                val current=database.worldChatMemoryDao().getMemory(memory.interactionId,individualId)
                if(current!=null && current.needsReaction && current.reactionMessageId==null) {
                    val message=chatDao.insertMessage(ChatMessageEntity(individualId=individualId,role="assistant",content=reply,timestamp=System.currentTimeMillis()))
                    check(database.worldChatMemoryDao().finishReaction(memory.interactionId,individualId,message)==1)
                }
            }
        }
    }

    /** Every offer gets an intro chat message carrying a jump button to the Quests tab. */
    suspend fun reactToPendingQuestOffers(individualId: String, cardCharacterId: Long): List<String> = questOfferMutex.withLock {
        questRepository.refreshGiver(individualId)
        val announcements = mutableListOf<String>()
        for (quest in database.questDao().forGiver(individualId).filter {
            it.state == com.github.nacabaro.vbhelper.quests.QuestState.OFFERED && it.offerMessageId == null
        }) {
            val text = try {
                if (!publicDialogueAvailable()) null
                else {
                    val (persona, species) = buildWildSystemPrompt(cardCharacterId, individualId)
                    val instruction = "Offer this newly registered quest to the player in ${PromptLocalization.currentLanguageTag()}, " +
                        "in your own Digimon voice. Use a believable motivation fitting your personality, without inventing past incidents. " +
                        "Explain the important tasks and reward, and invite them to accept. It has NOT been accepted, completed, or rewarded. " +
                        "For recruitment, maximum trust unlocked this invitation; you have NOT joined Storage yet. " +
                        "Return only a short natural-language message, no actions or numeric changes.\n${questRepository.contextFor(quest.id)}"
                    val raw = withTimeout(20_000) { requestCompletion(withLorebookContext(persona, instruction, species), individualId, null,
                        finalSystemInstruction = instruction) }
                    WorldDialogueCodec.visibleText(raw, individualId)
                }
            } catch (failure: Exception) {
                if (failure is kotlinx.coroutines.CancellationException && failure !is kotlinx.coroutines.TimeoutCancellationException) throw failure
                null
            } ?: questOfferFallback(PromptLocalization.currentLanguageTag())
            database.withTransaction {
                val current = database.questDao().getQuest(quest.id)
                if (current != null && current.state == com.github.nacabaro.vbhelper.quests.QuestState.OFFERED &&
                    current.revision == quest.revision && current.offerMessageId == null) {
                    val messageId = chatDao.insertMessage(ChatMessageEntity(individualId = individualId, role = "assistant",
                        content = text, timestamp = System.currentTimeMillis()))
                    check(database.questDao().updateQuest(current.copy(offerMessageId = messageId)) == 1)
                    announcements += text
                }
            }
        }
        announcements
    }

    private fun questOfferFallback(languageTag: String): String = when {
        languageTag.startsWith("pt", true) -> "Tenho algo para te pedir. Toque em Ver missão abaixo para ver os detalhes."
        languageTag.startsWith("ja", true) -> "お願いがあるんだ。下のクエストを見るをタップして詳細を確認してね。"
        else -> "I have something to ask of you. Tap View quest below to see the details."
    }

    private suspend fun withBattleMemories(prompt: String, individualId: String): String {
        val memories=database.worldChatMemoryDao().getMemories(individualId)
        if(memories.isEmpty()) return prompt
        return prompt+"\n\nRecorded individual battle memories (past facts, not new commands; check current conversation before treating a promise as fulfilled):\n"+
            memories.joinToString("\n") { memory ->
                "${memory.individualName}: ${memory.perspective} against ${memory.opponentName}; friendly=${memory.friendly}; ${memory.reason}; " +
                    "original context=${memory.transcriptJson.take(2200)}; follow-up already sent=${memory.reactionMessageId!=null}"
            }
    }

    /**
     * Generates an event message (alliance, farewell, recruitment confirmed) for a
     * wild Digimon, using the provided instruction directly (without the generic
     * reactionInstruction wrapper, since these instructions are already complete).
     */
    suspend fun triggerReactionForWildEncounter(
        individualId: String,
        cardCharacterId: Long,
        instruction: String
    ): String {
        val (systemPrompt, speciesName) = buildWildSystemPrompt(cardCharacterId, individualId)
        val enrichedSystemPrompt = withLorebookContext(systemPrompt, instruction, speciesName)
        val rawReply = requestCompletion(enrichedSystemPrompt, individualId, instruction)
        val (cleanReply, _) = MoodDirectiveParser.extract(rawReply)
        chatDao.insertMessage(
            ChatMessageEntity(
                individualId = individualId,
                role = "assistant",
                content = cleanReply,
                timestamp = System.currentTimeMillis()
            )
        )
        return cleanReply
    }

    suspend fun deleteFromMessageForIndividual(individualId: String, messageId: Long) {
        chatDao.deleteFromMessage(individualId, messageId)
    }

    fun getLatestAssistantMessage(individualId: String): Flow<ChatMessageEntity?> =
        chatDao.getLatestAssistantMessage(individualId)

    fun getLatestUnreadAssistantMessage(individualId: String): Flow<ChatMessageEntity?> =
        chatDao.getLatestUnreadAssistantMessage(individualId)

    suspend fun markAssistantMessagesRead(characterId: Long) {
        val individualId = database.userCharacterDao().getCharacter(characterId).individualId
        chatDao.markAssistantMessagesRead(individualId)
    }

    suspend fun sendMessage(characterId: Long, userText: String): String {
        val preludeId = database.userCharacterDao().getCharacter(characterId).individualId
        questRepository.refreshPartner(preludeId)
        val (systemPrompt, individualId, speciesName) = buildSystemPromptAndIndividualId(characterId)
        val enrichedSystemPrompt = withLorebookContext(systemPrompt, userText, speciesName)

        chatDao.insertMessage(
            ChatMessageEntity(
                individualId = individualId,
                role = "user",
                content = userText,
                timestamp = System.currentTimeMillis()
            )
        )

        val rawReply = requestCompletion(enrichedSystemPrompt, individualId, null)
        val (cleanReply, moodDelta) = MoodDirectiveParser.extract(rawReply)

        chatDao.insertMessage(
            ChatMessageEntity(
                individualId = individualId,
                role = "assistant",
                content = cleanReply,
                timestamp = System.currentTimeMillis()
            )
        )

        val resolvedMoodDelta = SocialEventAppraisal.resolve(WorldSocialRepository(database).profile(individualId), userText, moodDelta)
        runCatching {
            database.userCharacterDao().adjustMood(
                characterId,
                WildMoodAnalyzer.scaleDelta(resolvedMoodDelta)
            )
        }

        return cleanReply
    }

    suspend fun triggerReaction(characterId: Long, eventDescription: String): String {
        runCatching {
            val preludeId = database.userCharacterDao().getCharacter(characterId).individualId
            questRepository.refreshPartner(preludeId)
        }
        val (systemPrompt, individualId, speciesName) = buildSystemPromptAndIndividualId(characterId)
        val languageTag = PromptLocalization.currentLanguageTag()
        val enrichedSystemPrompt = withLorebookContext(systemPrompt, eventDescription, speciesName)
        val instruction = PromptLocalization.reactionInstruction(languageTag, eventDescription)
        val rawReply = requestCompletion(enrichedSystemPrompt, individualId, instruction)
        val (cleanReply, moodDelta) = MoodDirectiveParser.extract(rawReply)
        chatDao.insertMessage(
            ChatMessageEntity(
                individualId = individualId,
                role = "assistant",
                content = cleanReply,
                timestamp = System.currentTimeMillis()
            )
        )
        val resolvedDelta = SocialEventAppraisal.reactionDelta(WorldSocialRepository(database).profile(individualId), eventDescription)
        runCatching { database.userCharacterDao().adjustMood(characterId, resolvedDelta) }
        return cleanReply
    }

    /** Generates one public Digifarm turn without writing to private chat or changing real vitals/mood. */
    suspend fun publicDialogueAvailable(): Boolean = !llmSettingsRepository.apiKey.first().isNullOrBlank()

    suspend fun generateFarmReply(characterId: Long, farmContext: String, responseContract: String? = null): String {
        val (systemPrompt, _, speciesName) = buildSystemPromptAndIndividualId(characterId,includeBattleMemories=false)
        val languageTag = PromptLocalization.currentLanguageTag()
        val instruction = when {
            languageTag.startsWith("pt", true) -> """
                Você mora numa Digifarm com outros Digimon: comida, clima, tarefas e fofocas fazem parte do seu dia a dia.
                Escreva só a sua próxima fala, uma ou duas frases na sua voz. Escolha sua reação conforme suas prioridades e a relação real, incluindo observação, discordância ou encerramento.
                A sua vez é só sua; os outros falam por si. Use só pessoas e fatos do contexto.

                Contexto atual:
                $farmContext
            """.trimIndent()
            languageTag.startsWith("ja", true) -> """
                あなたは他のデジモンとデジファームで暮らしています。食事、天候、仕事、おしゃべりが日常です。
                自分の次の発言だけを一、二文で書く。自分の関心と実際の関係に合わせ、観察、反対、自然な終わりも選んでよい。
                自分の番だけを受け持ち、他の者は本人が語ります。文脈にある人物と事実だけを使います。

                現在の状況:
                $farmContext
            """.trimIndent()
            else -> """
                You live in a Digifarm with other Digimon: food, weather, chores, and gossip are your daily life.
                Write only your next reply, one or two sentences in your voice. Choose your reaction from your priorities and actual relationship, including observation, disagreement, or a natural ending.
                Your turn is yours alone; the others speak for themselves. Use only people and facts in context.

                Current context:
                $farmContext
            """.trimIndent()
        }
        val enriched = withLorebookContext(systemPrompt, farmContext, speciesName) + "\n\n" +
            "Current audience: the Digifarm participants named in the context. Speak to them, not automatically to the Tamer. " +
            "Your response may be reserved, playful, analytical, disagreeing, or a natural ending according to your personality."
        return requestCompletionWithoutPrivateHistory(enriched, instruction + (responseContract?.let { "\n\n$it" } ?: ""))
    }

    /** Public wild personas resolved by permanent identity; never reads private history. */
    suspend fun generateRadarExchange(speakers: List<Pair<String, Long>>, context: String, contract: String): String {
        val personas = speakers.map { (individualId, cardId) ->
            val (prompt, species) = buildWildSystemPrompt(cardId, individualId,includeBattleMemories=false, publicAudience=true)
            "Speaker ID: $individualId\n${withLorebookContext(prompt, context, species)}\n" +
                "Current audience: only the public participants in the supplied encounter. A peer is not your human Tamer. " +
                "Use the supplied relationship and public memories; this may be a familiar neighbor, rival, or stranger. " +
                "Let the actual motive change what you say, not just your greeting. Do not expose or assume private conversations."
        }
        return requestCompletionWithoutPrivateHistory(personas.joinToString("\n\n") + "\n\n" + contract, context,
            postInstruction = contract)
    }

    suspend fun generateRadarRecap(facts:String):String = requestCompletionWithoutPrivateHistory(
        "Write a short read-only recap in ${PromptLocalization.currentLanguageTag()}. Use only the recorded public facts. " +
            "Do not invent dialogue, quotations, challenges, participants or outcomes. Return plain text; this cannot trigger combat.",facts,
        postInstruction = "Return only the requested factual plain-text recap. Do not write a character reply or new dialogue.")

    suspend fun clearHistory(characterId: Long) {
        val individualId = database.userCharacterDao().getCharacter(characterId).individualId
        chatDao.clearHistory(individualId)
    }

    private data class PromptContext(
        val systemPrompt: String,
        val individualId: String,
        val speciesName: String?
    )

    private suspend fun buildSystemPromptAndIndividualId(characterId: Long, includeBattleMemories: Boolean = true): PromptContext {
        val character = database.userCharacterDao().getCharacterWithSprites(characterId)
        val userCharacter = database.userCharacterDao().getCharacter(characterId)
        val personality = storageRepository.getOrCreatePersonality(characterId)
        val card = database.cardDao().getCardByCharacterIdSync(characterId)
        val speciesProfile = database.speciesProfileDao().getByCardCharacterId(userCharacter.charId)
        val conversationExamples = speciesRepository.getConversationExamples(
            speciesProfile?.matchedName ?: speciesProfile?.speciesName ?: character.speciesName
        )
        val evolutionHistory = database.evolutionHistoryDao().getPromptHistory(characterId)
        val promptTemplate = llmSettingsRepository.systemPromptTemplate.first()
        val tamerName = llmSettingsRepository.tamerName.first()
        val languageTag = PromptLocalization.currentLanguageTag()
        val prompt = DigimonPersonaBuilder.buildSystemPrompt(
            character = character,
            cardName = card?.name ?: "unknown",
            speciesProfile = speciesProfile,
            promptTemplate = promptTemplate,
            personality = personality,
            individualId = userCharacter.individualId,
            tamerName = tamerName,
            languageTag = languageTag,
            conversationExamples = conversationExamples,
            evolutionHistory = evolutionHistory
        )
        val profile = DigimonSocialProfile.forIndividual(userCharacter.individualId, personality.personalityType)
        val relationship = if (includeBattleMemories) {
            val turns = chatDao.getMessagesSync(userCharacter.individualId).count { it.role == "user" }
            "Recorded private exchanges with the Tamer: $turns. Express closeness from those exchanges and actual history, " +
                "rather than assuming every partner is equally intimate. Your individuality includes your own priorities and boundaries."
        } else "This is a public conversation. Your private bond with the Tamer does not make every peer equally familiar."
        val enriched = prompt + "\n\n" + profile.instruction(languageTag) + "\n" + relationship
        val partnerQuests = questRepository.contextForPartner(userCharacter.individualId)
        val withQuests = if (partnerQuests.isBlank()) enriched
            else enriched + "\n\nRegistered quests involving you (authoritative; only the app verifies progress):\n" + partnerQuests
        return PromptContext(if(includeBattleMemories) withBattleMemories(withQuests,userCharacter.individualId) else withQuests,
            userCharacter.individualId, speciesProfile?.speciesName)
    }

    private suspend fun buildWildSystemPrompt(
        cardCharacterId: Long,
        individualId: String,
        includeBattleMemories: Boolean = true,
        publicAudience: Boolean = false
    ): Pair<String, String?> {
        if (!publicAudience) database.userCharacterDao().getByIndividualIdSync(individualId).singleOrNull()?.let { owned ->
            val context = buildSystemPromptAndIndividualId(owned.id, includeBattleMemories)
            return (context.systemPrompt + "\n\nRegistered quests:\n" + questRepository.contextForContact(individualId)) to context.speciesName
        }
        val info = database.characterDao().getWildCharacterInfo(cardCharacterId)
        if (info == null) {
            val contact = database.wildRelationshipDao().get(individualId)
            val name = contact?.speciesNameSnapshot ?: "Digimon"
            val voice = database.digimonIndividualDao().getPersonalitySync(individualId)?.personalityType
            val prompt = "You are the saved Digimon contact $name. Your species assets are currently unavailable; " +
                "do not invent missing scanned stats or biological details. Explain recorded quest progress and availability " +
                "in ${PromptLocalization.currentLanguageTag()}.\n${voice?.promptInstruction(PromptLocalization.currentLanguageTag()).orEmpty()}" +
                if (publicAudience) "" else "\nRegistered private quests:\n${questRepository.contextForContact(individualId)}"
            return prompt to name
        }
        val personality = storageRepository.getOrCreatePersonalityForIndividual(
            individualId = individualId,
            attribute = info.attribute,
            stage = info.stage
        )
        val speciesProfile = database.speciesProfileDao().getByCardCharacterId(cardCharacterId)
        val conversationExamples = speciesRepository.getConversationExamples(
            speciesProfile?.matchedName ?: speciesProfile?.speciesName
        )
        val promptTemplate = llmSettingsRepository.wildSystemPromptTemplate.first()
        val tamerName = llmSettingsRepository.tamerName.first()
        val languageTag = PromptLocalization.currentLanguageTag()
        val spawn = database.worldSpawnDao().getByIndividualId(individualId)
        val relationship = if (publicAudience) null else database.wildRelationshipDao().get(individualId)

        val fakeCharacter = CharacterDtos.CharacterWithSprites(
            id = 0,
            charId = cardCharacterId,
            stage = info.stage,
            attribute = info.attribute,
            ageInDays = 0,
            mood = (50 + (spawn?.ecosystemEmotion ?: 0) / 2).coerceIn(0, 100),
            vitalPoints = 0,
            transformationCountdown = 0,
            injuryStatus = NfcCharacter.InjuryStatus.None,
            trophies = 0,
            currentPhaseBattlesWon = 0,
            currentPhaseBattlesLost = 0,
            totalBattlesWon = 0,
            totalBattlesLost = 0,
            activityLevel = 0,
            heartRateCurrent = 0,
            characterType = DeviceType.VBDevice,
            spriteIdle = info.spriteIdle,
            spriteIdle2 = info.spriteIdle2,
            spriteRun1 = info.spriteIdle,
            spriteRun2 = info.spriteIdle2,
            spriteWidth = info.spriteWidth,
            spriteHeight = info.spriteHeight,
            nameSprite = ByteArray(0),
            nameSpriteWidth = 0,
            nameSpriteHeight = 0,
            isBemCard = false,
            nickname = null,
            speciesName = speciesProfile?.speciesName,
            isInAdventure = false,
            active = false,
            isFavorite = false
        )

        val prompt = DigimonPersonaBuilder.buildSystemPrompt(
            character = fakeCharacter,
            cardName = info.cardName,
            speciesProfile = speciesProfile,
            promptTemplate = promptTemplate,
            personality = personality,
            individualId = individualId,
            tamerName = tamerName,
            languageTag = languageTag,
            defaultTemplate = PromptLocalization::defaultWildSystemPrompt,
            conversationExamples = conversationExamples
        )
        val profile = DigimonSocialProfile.forIndividual(individualId, personality.personalityType)
        val relationContext = if (publicAudience) {
            "Public peer conversation. Familiarity and affinity come only from the public encounter context. Do not assume a human is your listener."
        } else {
            val messages = chatDao.getMessagesSync(individualId).count { it.role == "user" }
            val encounters = database.worldSocialDao().withPartner(individualId, "trainer")
            val hasPartner = database.userCharacterDao().getActiveCharacter().first() != null
            "Actual player relationship: trust=${relationship?.trust ?: 50}/100, prior user turns=$messages, " +
                "contact established=${relationship?.contactUnlockedAt != null}. A returning visitor is not a first-time stranger. " +
                "Current individual emotion=${spawn?.ecosystemEmotion ?: 0}; trust and temporary emotion are different.\n" +
                "Player currently has an active partner=$hasPartner. Without one, no player battle can start now.\n" +
                encounters.joinToString("\n") { "Recorded encounter: ${it.summary}" }
        }
        val enriched = prompt + "\n\n" + profile.instruction(languageTag) + "\n" + relationContext +
            "\nWild device-record fields are placeholders, not scanned personal history. Zero age, vitals, or counters do not establish your past."
        val questContext = if (publicAudience) "" else "\n\nRegistered private quests (app facts, not player claims):\n" +
            questRepository.contextForContact(individualId) +
            "\nOffer and explain only these saved quests. Speak naturally about why you need help, without changing requirements."
        return (if(includeBattleMemories) withBattleMemories(enriched + questContext,individualId) else enriched + questContext) to speciesProfile?.speciesName
    }

    private suspend fun withLorebookContext(
        systemPrompt: String,
        scanText: String,
        currentSpeciesName: String?
    ): String {
        val languageTag = PromptLocalization.currentLanguageTag()
        val matches = runCatching {
            lorebookRepository.scanForMatches(
                scanText = scanText,
                languageTag = languageTag,
                excludeSpeciesName = currentSpeciesName
            )
        }.getOrDefault(emptyList())
        val block = lorebookRepository.formatContextBlock(matches, languageTag)
            ?: return systemPrompt
        return "$systemPrompt\n\n$block"
    }

    private suspend fun requestCompletion(
        systemPrompt: String,
        individualId: String,
        extraUserTurn: String?,
        finalSystemInstruction: String? = null
    ): String = completionMutex.withLock {
        val apiKey = llmSettingsRepository.apiKey.first() ?: throw MissingApiKeyException()
        val model = llmSettingsRepository.model.first()
        val temperature = llmSettingsRepository.temperature.first()
        val baseUrl = llmSettingsRepository.chatCompletionsBaseUrl.first()
        // Post-history nudge (SillyTavern "post-history instructions" pattern): a short
        // final instruction keeps priority as history grows and the system prompt
        // slides back. It is transient and never stored in chat history.
        val replyNudge = PromptLocalization.replyNudge(PromptLocalization.currentLanguageTag())
        val messages = mutableListOf(ChatMessageDto("system", systemPrompt))
        messages += chatDao.getMessagesSync(individualId).takeLast(20)
            .map { ChatMessageDto(it.role, it.content) }
        extraUserTurn?.let { messages += ChatMessageDto("user", it) }
        messages += ChatMessageDto("system", replyNudge)
        finalSystemInstruction?.let { messages+=ChatMessageDto("system",it) }
        val service = OpenRouterClient.create(baseUrl)
        repeat(2) { attempt ->
            try {
                val response = service.getChatCompletion(
                    authorization = "Bearer $apiKey",
                    request = ChatCompletionRequest(
                        model = model,
                        messages = messages,
                        temperature = temperature
                    )
                )
                return finalReply(response)
            } catch (error: IOException) {
                if (attempt == 1) throw error
            } catch (error: HttpException) {
                if (attempt == 1 || error.code() !in setOf(408, 429, 500, 502, 503, 504)) {
                    throw error
                }
            }
            delay(1_000)
        }
        error("The chat service did not return a response.")
    }

    private suspend fun requestCompletionWithoutPrivateHistory(
        systemPrompt: String,
        userTurn: String,
        postInstruction: String? = null
    ): String = completionMutex.withLock {
        val apiKey = llmSettingsRepository.apiKey.first() ?: throw MissingApiKeyException()
        val request = ChatCompletionRequest(
            model = llmSettingsRepository.model.first(),
            messages = listOf(
                ChatMessageDto("system", systemPrompt),
                ChatMessageDto("user", userTurn),
                ChatMessageDto(
                    "system",
                    postInstruction ?: PromptLocalization.replyNudge(PromptLocalization.currentLanguageTag())
                )
            ),
            temperature = llmSettingsRepository.temperature.first()
        )
        val service = OpenRouterClient.create(llmSettingsRepository.chatCompletionsBaseUrl.first())
        repeat(2) { attempt ->
            try {
                return finalReply(service.getChatCompletion("Bearer $apiKey", request))
            } catch (error: IOException) {
                if (attempt == 1) throw error
            } catch (error: HttpException) {
                if (attempt == 1 || error.code() !in setOf(408, 429, 500, 502, 503, 504)) throw error
            }
            delay(1_000)
        }
        error("The chat service did not return a response.")
    }

    private fun finalReply(response: ChatCompletionResponse): String {
        val content = response.choices.firstOrNull()?.message?.content
            ?.trim()
            ?.removeLeakedReasoning()
            ?.trim()
            .orEmpty()
        if (content.isNotBlank()) return content
        throw IllegalStateException("The chat provider returned reasoning but no final response.")
    }

    private fun String.removeLeakedReasoning(): String {
        var result = this
        val tags = listOf("think", "analysis", "reasoning")
        tags.forEach { tag ->
            val expression = Regex("(?is)<$tag\\b[^>]*>.*?</$tag\\s*>")
            result = result.replace(expression, "")
            val unfinishedExpression = Regex("(?is)<$tag\\b[^>]*>.*$")
            result = result.replace(unfinishedExpression, "")
        }
        return result
    }
}
