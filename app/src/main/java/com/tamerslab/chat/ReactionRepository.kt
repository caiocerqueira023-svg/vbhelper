package com.tamerslab.chat

import com.tamerslab.data.database.AppDatabase
import com.tamerslab.domain.reactions.DigimonReactionEngine
import com.tamerslab.domain.reactions.DigimonStateSnapshot
import com.tamerslab.domain.reactions.SnapshotMission
import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import timber.log.Timber

class ReactionRepository(
    private val database: AppDatabase,
    private val chatRepository: ChatRepository
) {
    private val gson = Gson()

    /**
     * Best-effort baseline captured before an export so a returning partner can be compared.
     * A missing baseline only skips that comparison ([evaluateAndReact] returns early), therefore
     * this must never block, fail or crash the transfer itself.
     */
    suspend fun snapshotBeforeSendingToWatch(characterId: Long) {
        runCatching {
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
        }.onFailure {
            Timber.w(it, "Could not snapshot the state before sending character $characterId to the watch")
        }
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
