package com.github.nacabaro.vbhelper.chat

import com.github.nacabaro.vbhelper.database.AppDatabase
import java.util.concurrent.TimeUnit
import java.util.GregorianCalendar
import timber.log.Timber

class DigimonDiaryService(
    private val database: AppDatabase,
    private val chatRepository: ChatRepository
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
            chatRepository.triggerReaction(
                characterId,
                "Escreva um diário curto de 1 ou 2 frases sobre os últimos dias. " +
                    "Fatos reais: foram registrados cerca de $vitals vitais, " +
                    "$transformations evolução(ões), ${character.totalBattlesWon} vitórias " +
                    "e ${character.totalBattlesLost} derrotas."
            )
            database.digimonIndividualDao().updateLastDiaryEntry(character.individualId, now)
        }.onFailure { Timber.e(it, "Falha ao gerar diário do Digimon") }
    }
}
