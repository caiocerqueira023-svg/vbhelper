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
import com.github.nacabaro.vbhelper.world.ecosystem.WorldBattleMemoryPrompts
import kotlinx.coroutines.withTimeout
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ChatRepository(
    private val database: AppDatabase,
    private val llmSettingsRepository: LlmSettingsRepository,
    private val lorebookRepository: LorebookRepository,
    private val speciesRepository: SpeciesRepository,
    private val chatDao: ChatDao = database.chatDao()
) {
    class MissingApiKeyException : Exception("Chat API key is not configured.")

    private val storageRepository = StorageRepository(database)
    private val completionMutex = Mutex()
    private val battleReactionMutex = Mutex()

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
        val sourceMessageIds: List<Long> = emptyList(), val userMessageId: Long? = null)

    suspend fun sendMessageForWildEncounter(
        individualId: String,
        cardCharacterId: Long,
        userText: String
    ): WildChatResult {
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
            Return JSON {"lines":[{"speakerId":"$individualId","text":"your reply"}],"intent":null or
            {"type":"CHALLENGE_BATTLE|ACCEPT_CHALLENGE|DECLINE_CHALLENGE|DEESCALATE","speakerId":"$individualId",
            "targetIds":["trainer"],"evidenceIds":["this:$individualId"],"reason":"brief reason","sparring":false}}.
            A battle intent must come from the actual attributed conversation. A joke, quotation, hypothetical,
            mention of fighting, or explicit refusal is not a challenge or consent. The model cannot consent for the human.
            If your reply AGREES to the current player's request/acceptance of a duel, contest or wager, return ACCEPT_CHALLENGE.
            Set sparring:true for an agreed duel, including a wager, even if your personality's voice sounds aggressive.
            Its reason must preserve the actual agreed stakes for BOTH sides (e.g. your win means they reveal their name;
            their win means you obey). Cite private:$userMessageId and this:$individualId as evidence.
            Do not accept jokes, quoted/hypothetical fights, ordinary unrelated agreement or refusals as a battle request.
            CHALLENGE_BATTLE is a new invitation/attack you initiate, not your affirmative answer to their offer.
            A grounded unilateral hostile attack can start without target agreement. Do not promise a battle without its matching intent.
            Allowed supporting IDs: ${source.joinToString { "private:${it.id}" }}. This line may support its own explicit challenge.
        """.trimIndent()
        val rawReply = requestCompletion(enrichedSystemPrompt, individualId, null, finalSystemInstruction=contract)
        val structured=runCatching { WorldDialogueCodec.parseReadableExchange(rawReply,setOf(individualId),source.map { "private:${it.id}" }.toSet(),
            targetsAllowed=setOf(individualId,"trainer")) }.getOrNull()
        val extracted=MoodDirectiveParser.extract(rawReply)
        val cleanReply=structured?.lines?.single()?.text ?: WorldDialogueCodec.visibleText(rawReply,individualId)
            ?: WorldDialogueCodec.unreadableReply(PromptLocalization.currentLanguageTag())
        val moodDelta=if(structured==null && cleanReply==extracted.first.trim()) extracted.second else null
        val replyId=chatDao.insertMessage(
            ChatMessageEntity(
                individualId = individualId,
                role = "assistant",
                content = cleanReply,
                timestamp = System.currentTimeMillis()
            )
        )
        return WildChatResult(cleanReply, moodDelta,structured?.intent,source.map { it.id }+replyId,userMessageId)
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

        // Models do not always return the hidden mood marker. Use the same
        // sentiment fallback as wild encounters so every conversation has a
        // meaningful, non-zero mood outcome.
        val resolvedMoodDelta = WildMoodAnalyzer.resolveDelta(userText, cleanReply, moodDelta)
        runCatching {
            database.userCharacterDao().adjustMood(
                characterId,
                WildMoodAnalyzer.scaleDelta(resolvedMoodDelta)
            )
        }

        return cleanReply
    }

    suspend fun triggerReaction(characterId: Long, eventDescription: String): String {
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
        val resolvedDelta = WildMoodAnalyzer.resolveDelta(eventDescription, cleanReply, moodDelta)
        runCatching { database.userCharacterDao().adjustMood(characterId, resolvedDelta) }
        return cleanReply
    }

    /** Generates one public Digifarm turn without writing to private chat or changing real vitals/mood. */
    suspend fun generateFarmReply(characterId: Long, farmContext: String): String {
        val (systemPrompt, _, speciesName) = buildSystemPromptAndIndividualId(characterId,includeBattleMemories=false)
        val languageTag = PromptLocalization.currentLanguageTag()
        val instruction = when {
            languageTag.startsWith("pt", true) -> """
                Você mora numa Digifarm com outros Digimon: comida, clima, tarefas e fofocas fazem parte do seu dia a dia.
                Escreva só a sua próxima fala, uma ou duas frases na sua voz, respondendo ao momento e levando-o um pouco adiante.
                A sua vez é só sua; os outros falam por si. Use só pessoas e fatos do contexto.

                Contexto atual:
                $farmContext
            """.trimIndent()
            languageTag.startsWith("ja", true) -> """
                あなたは他のデジモンとデジファームで暮らしています。食事、天候、仕事、おしゃべりが日常です。
                自分の次の発言だけを一、二文で自分の声で書き、目の前の出来事に応えて場面を少し進めます。
                自分の番だけを受け持ち、他の者は本人が語ります。文脈にある人物と事実だけを使います。

                現在の状況:
                $farmContext
            """.trimIndent()
            else -> """
                You live in a Digifarm with other Digimon: food, weather, chores, and gossip are your daily life.
                Write only your own next reply, one or two sentences in your voice, answering the moment directly and moving it a little forward.
                Your turn is yours alone; the others speak for themselves. Use only people and facts in context.

                Current context:
                $farmContext
            """.trimIndent()
        }
        val enriched = withLorebookContext(systemPrompt, farmContext, speciesName)
        return requestCompletionWithoutPrivateHistory(enriched, instruction)
    }

    /** Public wild personas resolved by permanent identity; never reads private history. */
    suspend fun generateRadarExchange(speakers: List<Pair<String, Long>>, context: String, contract: String): String {
        val personas = speakers.map { (individualId, cardId) ->
            val (prompt, species) = buildWildSystemPrompt(cardId, individualId,includeBattleMemories=false)
            "Speaker ID: $individualId\n${withLorebookContext(prompt, context, species)}"
        }
        return requestCompletionWithoutPrivateHistory(personas.joinToString("\n\n") + "\n\n" + contract, context)
    }

    suspend fun generateRadarRecap(facts:String):String = requestCompletionWithoutPrivateHistory(
        "Write a short read-only recap in ${PromptLocalization.currentLanguageTag()}. Use only the recorded public facts. " +
            "Do not invent dialogue, quotations, challenges, participants or outcomes. Return plain text; this cannot trigger combat.",facts)

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
        return PromptContext(if(includeBattleMemories) withBattleMemories(prompt,userCharacter.individualId) else prompt, userCharacter.individualId, speciesProfile?.speciesName)
    }

    private suspend fun buildWildSystemPrompt(
        cardCharacterId: Long,
        individualId: String,
        includeBattleMemories: Boolean = true
    ): Pair<String, String?> {
        val info = database.characterDao().getWildCharacterInfo(cardCharacterId)
            ?: error("Species data was not found for this Digimon.")
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

        val fakeCharacter = CharacterDtos.CharacterWithSprites(
            id = 0,
            charId = cardCharacterId,
            stage = info.stage,
            attribute = info.attribute,
            ageInDays = 0,
            mood = 50,
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
        return (if(includeBattleMemories) withBattleMemories(prompt,individualId) else prompt) to speciesProfile?.speciesName
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
        userTurn: String
    ): String = completionMutex.withLock {
        val apiKey = llmSettingsRepository.apiKey.first() ?: throw MissingApiKeyException()
        val request = ChatCompletionRequest(
            model = llmSettingsRepository.model.first(),
            messages = listOf(
                ChatMessageDto("system", systemPrompt),
                ChatMessageDto("user", userTurn),
                ChatMessageDto(
                    "system",
                    PromptLocalization.replyNudge(PromptLocalization.currentLanguageTag())
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
