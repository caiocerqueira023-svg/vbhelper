package com.github.nacabaro.vbhelper.chat

import com.github.cfogrady.vbnfc.data.NfcCharacter
import com.github.nacabaro.vbhelper.daos.ChatDao
import com.github.nacabaro.vbhelper.chat.lorebook.LorebookRepository
import com.github.nacabaro.vbhelper.database.AppDatabase
import com.github.nacabaro.vbhelper.domain.chat.ChatMessageEntity
import com.github.nacabaro.vbhelper.dtos.CharacterDtos
import com.github.nacabaro.vbhelper.source.LlmSettingsRepository
import com.github.nacabaro.vbhelper.utils.DeviceType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalCoroutinesApi::class)
class ChatRepository(
    private val database: AppDatabase,
    private val llmSettingsRepository: LlmSettingsRepository,
    private val lorebookRepository: LorebookRepository,
    private val chatDao: ChatDao = database.chatDao(),
    private val openRouterService: OpenRouterService = OpenRouterClient.create()
) {
    class MissingApiKeyException : Exception("Chave de API do OpenRouter não configurada.")

    fun getHistory(characterId: Long): Flow<List<ChatMessageEntity>> =
        database.userCharacterDao().getIndividualId(characterId)
            .flatMapLatest(chatDao::getMessages)

    fun getHistoryForIndividual(individualId: String): Flow<List<ChatMessageEntity>> =
        chatDao.getMessages(individualId)

    /**
     * Sends a message to a wild Digimon found on the World map that is not in storage.
     * Uses species data (CardCharacter) and the personality generated for this individualId at spawn.
     */
    suspend fun sendMessageForWildEncounter(
        individualId: String,
        cardCharacterId: Long,
        userText: String
    ): String {
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
        val reply = requestCompletion(enrichedSystemPrompt, individualId, null)
        chatDao.insertMessage(
            ChatMessageEntity(
                individualId = individualId,
                role = "assistant",
                content = reply,
                timestamp = System.currentTimeMillis()
            )
        )
        return reply
    }

    suspend fun deleteFromMessageForIndividual(individualId: String, messageId: Long) {
        chatDao.deleteFromMessage(individualId, messageId)
    }

    fun getLatestAssistantMessage(individualId: String): Flow<ChatMessageEntity?> =
        chatDao.getLatestAssistantMessage(individualId)

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
        val reply = requestCompletion(enrichedSystemPrompt, individualId, null)
        chatDao.insertMessage(
            ChatMessageEntity(
                individualId = individualId,
                role = "assistant",
                content = reply,
                timestamp = System.currentTimeMillis()
            )
        )
        return reply
    }

    suspend fun triggerReaction(characterId: Long, eventDescription: String): String {
        val (systemPrompt, individualId, speciesName) = buildSystemPromptAndIndividualId(characterId)
        val enrichedSystemPrompt = withLorebookContext(systemPrompt, eventDescription, speciesName)
        val instruction = PromptLocalization.reactionInstruction(
            PromptLocalization.currentLanguageTag(),
            eventDescription
        )
        val reply = requestCompletion(enrichedSystemPrompt, individualId, instruction)
        chatDao.insertMessage(
            ChatMessageEntity(
                individualId = individualId,
                role = "assistant",
                content = reply,
                timestamp = System.currentTimeMillis()
            )
        )
        return reply
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
        val personality = database.digimonIndividualDao().getPersonality(userCharacter.individualId)
        val card = database.cardDao().getCardByCharacterIdSync(characterId)
        val speciesProfile = database.speciesProfileDao().getByCardCharacterId(userCharacter.charId)
        val promptTemplate = llmSettingsRepository.systemPromptTemplate.first()
        val tamerName = llmSettingsRepository.tamerName.first()
        val languageTag = PromptLocalization.currentLanguageTag()
        val prompt = DigimonPersonaBuilder.buildSystemPrompt(
            character,
            card?.name ?: "desconhecido",
            speciesProfile,
            promptTemplate,
            personality,
            tamerName,
            languageTag
        )
        return PromptContext(prompt, userCharacter.individualId, speciesProfile?.speciesName)
    }

    private suspend fun buildWildSystemPrompt(
        cardCharacterId: Long,
        individualId: String
    ): Pair<String, String?> {
        val info = database.characterDao().getWildCharacterInfo(cardCharacterId)
            ?: error("Dados da espécie não encontrados para este Digimon.")
        val personality = database.digimonIndividualDao().getPersonality(individualId)
        val speciesProfile = database.speciesProfileDao().getByCardCharacterId(cardCharacterId)
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
            spriteWidth = info.spriteWidth,
            spriteHeight = info.spriteHeight,
            nameSprite = ByteArray(0),
            nameSpriteWidth = 0,
            nameSpriteHeight = 0,
            isBemCard = false,
            nickname = null,
            isInAdventure = false,
            active = false
        )

        val prompt = DigimonPersonaBuilder.buildSystemPrompt(
            fakeCharacter,
            info.cardName,
            speciesProfile,
            promptTemplate,
            personality,
            tamerName,
            languageTag,
            defaultTemplate = PromptLocalization::defaultWildSystemPrompt
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
        val messages = mutableListOf(ChatMessageDto("system", systemPrompt))
        messages += chatDao.getMessagesSync(individualId).takeLast(20)
            .map { ChatMessageDto(it.role, it.content) }
        extraUserTurn?.let { messages += ChatMessageDto("user", it) }
        val response = openRouterService.getChatCompletion(
            authorization = "Bearer $apiKey",
            request = ChatCompletionRequest(model = model, messages = messages)
        )
        return response.choices.firstOrNull()?.message?.content?.trim() ?: "..."
    }
}
