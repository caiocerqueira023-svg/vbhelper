package com.github.nacabaro.vbhelper.chat

import com.github.nacabaro.vbhelper.database.AppDatabase
import com.github.nacabaro.vbhelper.domain.reactions.DigimonReactionEngine
import com.github.nacabaro.vbhelper.domain.reactions.DigimonStateSnapshot
import com.github.nacabaro.vbhelper.domain.reactions.SnapshotMission
import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import timber.log.Timber

class ReactionRepository(
    private val database: AppDatabase,
    private val chatRepository: ChatRepository
) {
    private val gson = Gson()

    suspend fun snapshotBeforeSendingToWatch(characterId: Long) {
        val character = database.userCharacterDao().getCharacter(characterId)
        val characterInfo = database.userCharacterDao().getCharacterInfo(characterId)
        database.digimonStateSnapshotDao().upsert(
            DigimonStateSnapshot(
                individualId = character.individualId,
                stage = characterInfo.stage,
                mood = character.mood,
                vitalPoints = character.vitalPoints,
                trophies = character.trophies,
                totalBattlesWon = character.totalBattlesWon,
                totalBattlesLost = character.totalBattlesLost,
                injuryStatus = character.injuryStatus.name,
                specialMissionsJson = gson.toJson(currentMissionsFor(characterId)),
                capturedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun evaluateAndReact(characterId: Long) {
        runCatching {
            val character = database.userCharacterDao().getCharacter(characterId)
            val snapshot = database.digimonStateSnapshotDao()
                .getByIndividualId(character.individualId) ?: return@runCatching
            val characterInfo = database.userCharacterDao().getCharacterInfo(characterId)
            val individual = database.digimonIndividualDao().getIndividual(character.individualId)
            val result = DigimonReactionEngine.compareStates(
                previous = snapshot,
                current = character,
                currentStage = characterInfo.stage,
                currentSpecialMissionsJson = gson.toJson(currentMissionsFor(characterId)),
                lastCelebratedWinsMilestone = individual?.lastCelebratedWinsMilestone ?: 0,
                lastCelebratedTrophyMilestone = individual?.lastCelebratedTrophyMilestone ?: 0
            )
            result.events.take(3).forEach { event ->
                runCatching { chatRepository.triggerReaction(characterId, event.prompt) }
                    .onFailure { Timber.w(it, "Falha ao gerar reação ${event.type}") }
            }
            database.digimonIndividualDao().updateMilestones(
                character.individualId,
                result.newWinsMilestone,
                result.newTrophyMilestone
            )
            database.digimonStateSnapshotDao().delete(character.individualId)
        }.onFailure {
            Timber.e(it, "Falha ao avaliar reações do Digimon")
        }
    }

    private suspend fun currentMissionsFor(characterId: Long): List<SnapshotMission> =
        database.userCharacterDao().getSpecialMissions(characterId).first().map {
            SnapshotMission(it.watchId, it.missionType.name, it.progress, it.goal)
        }
}
