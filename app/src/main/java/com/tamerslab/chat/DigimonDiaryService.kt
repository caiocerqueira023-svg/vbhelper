package com.tamerslab.chat

import com.tamerslab.data.database.AppDatabase
import java.util.concurrent.TimeUnit
import java.util.GregorianCalendar
import timber.log.Timber

class DigimonDiaryService(
    private val database: AppDatabase,
    private val chatRepository: ChatRepository,
    private val digilineContent: com.tamerslab.digiline.content.DigilineContentProvider? = null,
) {
    suspend fun checkAndGenerateEntry(characterId: Long) {
        runCatching {
            val character = database.userCharacterDao().getCharacter(characterId)
            val individual = database.digimonIndividualDao().getIndividual(character.individualId)
                ?: return@runCatching
            val now = System.currentTimeMillis()
            val lastEntry = individual.lastDiaryEntryAt
                ?: (now - TimeUnit.DAYS.toMillis(7) - 1)
            if (now - lastEntry < TimeUnit.DAYS.toMillis(7)) return@runCatching

            val vitals = database.userCharacterDao().getVitalsHistory(characterId)
                .filter {
                    if (it.year == 0) {
                        false
                    } else {
                        GregorianCalendar(it.year, it.month - 1, it.day).timeInMillis > lastEntry
                    }
                }
                .sumOf { it.vitalPoints }
            val transformations = database.userCharacterDao()
                .getTransformationHistoryForExport(characterId)
                .count { it.transformationDate > lastEntry }

            // Deterministic authored diary first (plan §8.2); the LLM-free fallback line is only
            // used when no content pack is available at all.
            val composed = digilineContent?.let { provider ->
                val pack = provider.get()
                if (!pack.hasContent) return@let null
                val personality = database.digimonIndividualDao()
                    .getPersonality(character.individualId)?.personalityType?.number ?: 1
                val composer = com.tamerslab.digiline.engine.DiaryComposer(pack.eventLines)
                composer.compose(
                    event = "diary",
                    personalityType = personality,
                    facts = listOf(
                        "Vital points recorded in this period: $vitals",
                        "Transformations in this period: $transformations",
                        "Battles won: ${character.totalBattlesWon}",
                        "Battles lost: ${character.totalBattlesLost}",
                    ),
                    seed = com.tamerslab.digiline.model.DigilineSeed.stableHash(individual.individualId),
                    dayIndex = com.tamerslab.digiline.model.DigilineSeed.dayIndex(now),
                )
            }
            if (composed != null) {
                chatRepository.insertNotice(
                    character.individualId,
                    "${composed.title}\n${composed.body}",
                )
            } else {
                chatRepository.triggerReaction(
                    characterId,
                    PromptLocalization.diaryPrompt(
                        PromptLocalization.currentLanguageTag(),
                        vitals,
                        transformations,
                        character.totalBattlesWon,
                        character.totalBattlesLost
                    )
                )
            }
            database.digimonIndividualDao().updateLastDiaryEntry(character.individualId, now)
        }.onFailure { Timber.e(it, "Falha ao gerar diário do Digimon") }
    }
}
