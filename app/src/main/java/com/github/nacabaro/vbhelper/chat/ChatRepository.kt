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
    data class WildChatResult(val reply: String, val moodDelta: Int?)

    suspend fun sendMessageForWildEncounter(
        individualId: String,
        cardCharacterId: Long,
        userText: String
    ): WildChatResult {
        val (systemPrompt, speciesName) = buildWildSystemPrompt(cardCharacterId, individualId)
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
        return WildChatResult(cleanReply, moodDelta)
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
        val (systemPrompt, _, speciesName) = buildSystemPromptAndIndividualId(characterId)
        val languageTag = PromptLocalization.currentLanguageTag()
        val instruction = when {
            languageTag.startsWith("pt", true) -> """
                Você está vivendo numa Digifarm com outros Digimon. Responda apenas por você.
                Produza uma fala natural de uma ou duas frases; ações curtas entre asteriscos são raras.
                Não narre pensamentos nem controle outros personagens. Use somente pessoas e fatos do contexto.

                Contexto atual:
                $farmContext
            """.trimIndent()
            languageTag.startsWith("ja", true) -> """
                あなたは他のデジモンとデジファームで暮らしています。自分の発言だけを書いてください。
                自然な一、二文で返答し、他のキャラクターを操作したり心情を語ったりしないでください。

                現在の状況:
                $farmContext
            """.trimIndent()
            else -> """
                You live in a Digifarm with other Digimon. Speak only for yourself.
                Reply naturally in one or two sentences. Brief actions in asterisks should be rare.
                Do not narrate thoughts or control other characters. Use only people and facts in context.

                Current context:
                $farmContext
            """.trimIndent()
        }
        val enriched = withLorebookContext(systemPrompt, farmContext, speciesName)
        return requestCompletionWithoutPrivateHistory(enriched, instruction)
    }

    suspend fun clearHistory(characterId: Long) {
        val individualId = database.userCharacterDao().getCharacter(characterId).individualId
        chatDao.clearHistory(individualId)
    }

    private data class PromptContext(
        val systemPrompt: String,
        val individualId: String,
        val speciesName: String?
    )

    private suspend fun buildSystemPromptAndIndividualId(characterId: Long): PromptContext {
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
        return PromptContext(prompt, userCharacter.individualId, speciesProfile?.speciesName)
    }

    private suspend fun buildWildSystemPrompt(
        cardCharacterId: Long,
        individualId: String
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
        return prompt to speciesProfile?.speciesName
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
        extraUserTurn: String?
    ): String = completionMutex.withLock {
        val apiKey = llmSettingsRepository.apiKey.first() ?: throw MissingApiKeyException()
        val model = llmSettingsRepository.model.first()
        val temperature = llmSettingsRepository.temperature.first()
        val baseUrl = llmSettingsRepository.chatCompletionsBaseUrl.first()
        val messages = mutableListOf(ChatMessageDto("system", systemPrompt))
        messages += chatDao.getMessagesSync(individualId).takeLast(20)
            .map { ChatMessageDto(it.role, it.content) }
        extraUserTurn?.let { messages += ChatMessageDto("user", it) }
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
            messages = listOf(ChatMessageDto("system", systemPrompt), ChatMessageDto("user", userTurn)),
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
