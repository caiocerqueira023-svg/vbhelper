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

    fun getHistory(characterId: Long): Flow<List<ChatMessageEntity>> =
        database.userCharacterDao().getIndividualId(characterId)
            .flatMapLatest(chatDao::getMessages)

    fun getHistoryForIndividual(individualId: String): Flow<List<ChatMessageEntity>> =
        chatDao.getMessages(individualId)

    /**
     * Sends a message to a wild Digimon found on the World map that is not in storage.
     * Uses species data (CardCharacter) and the personality generated for this individualId at spawn.
     * Returns the reply along with the mood delta requested by the LLM via the hidden [[MOOD:+N/-N]] marker.
     */
    data class WildChatResult(val reply: String, val moodDelta: Int?)

    suspend fun sendMessageForWildEncounter(
        individualId: String,
        cardCharacterId: Long,
        userText: String
    ): WildChatResult {
        val (systemPrompt, speciesName) = buildWildSystemPrompt(cardCharacterId, individualId)
        val languageTag = PromptLocalization.currentLanguageTag()
        val enrichedSystemPrompt = withLorebookContext(systemPrompt, userText, speciesName) +
            "\n\n" + PromptLocalization.moodDirectiveInstruction(languageTag)

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
        // If the LLM omitted the mood marker, make a single lightweight follow-up
        // call asking it to output ONLY the marker. The conversation history is
        // already in the DB (user message + assistant reply), so the LLM has full
        // context. This is cheap because the response is just the marker itself.
        val resolvedDelta = if (moodDelta != null) {
            moodDelta
        } else {
            requestMoodDelta(enrichedSystemPrompt, individualId, languageTag)
        }
        return WildChatResult(cleanReply, resolvedDelta)
    }

    /**
     * Lightweight follow-up: asks the LLM to output ONLY the [[MOOD:+N/-N]] marker
     * based on the conversation already stored in the DB. Returns null if the LLM
     * still refuses to emit a valid marker (the analyzer fallback handles that case).
     */
    private suspend fun requestMoodDelta(
        systemPrompt: String,
        individualId: String,
        languageTag: String
    ): Int? {
        val followUp = PromptLocalization.moodRatingFollowUpInstruction(languageTag)
        val rawReply = runCatching {
            requestCompletion(systemPrompt, individualId, followUp)
        }.getOrNull() ?: return null
        val (_, delta) = MoodDirectiveParser.extract(rawReply)
        return delta
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
        val languageTag = PromptLocalization.currentLanguageTag()
        val enrichedSystemPrompt = withLorebookContext(systemPrompt, userText, speciesName) +
            "\n\n" + PromptLocalization.moodDirectiveInstruction(languageTag)

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
        val enrichedSystemPrompt = withLorebookContext(systemPrompt, eventDescription, speciesName) +
            "\n\n" + PromptLocalization.moodDirectiveInstruction(languageTag)
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
        if (moodDelta != null) {
            runCatching { database.userCharacterDao().adjustMood(characterId, moodDelta) }
        }
        return cleanReply
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
    ): String {
        val apiKey = llmSettingsRepository.apiKey.first() ?: throw MissingApiKeyException()
        val model = llmSettingsRepository.model.first()
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
                    request = ChatCompletionRequest(model = model, messages = messages)
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
