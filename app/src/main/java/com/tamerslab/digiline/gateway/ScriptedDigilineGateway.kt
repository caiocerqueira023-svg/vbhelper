package com.tamerslab.digiline.gateway

import com.tamerslab.data.database.AppDatabase
import com.tamerslab.digiline.data.OwnedDigilineRepository
import com.tamerslab.digiline.data.WildDigilineRepository
import com.tamerslab.domain.chat.ChatMessageEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Default, fully offline DigiLine gateway (plan D1/D6/D10):
 * owned individuals route to [OwnedDigilineRepository] (questions/quiz/personality),
 * wild contacts route to [WildDigilineRepository] (negotiation/persuasion).
 *
 * Scenes are process-memory UI state; the underlying progress is persisted by the repositories
 * before a scene is returned, so a process restart resumes from the database (never from a reroll).
 */
class ScriptedDigilineGateway(
    private val database: AppDatabase,
    private val owned: OwnedDigilineRepository,
    private val wild: WildDigilineRepository,
) : DigilineGateway {

    private val scenes = MutableStateFlow<Map<String, DigilineScene?>>(emptyMap())

    override fun observeHistory(individualId: String): Flow<List<ChatMessageEntity>> =
        database.chatDao().getMessages(individualId)

    override fun observeScene(individualId: String): Flow<DigilineScene?> =
        scenes.map { it[individualId] }

    override suspend fun ensureScene(individualId: String, wild: Boolean): DigilineResult {
        val result = if (wild) {
            this.wild.ensureScene(individualId)
        } else {
            owned.ensureScene(individualId)
        }
        return publish(individualId, result)
    }

    override suspend fun choose(individualId: String, optionId: String): DigilineResult {
        val result = if (isWild(individualId)) {
            wild.choose(individualId, optionId)
        } else {
            owned.choose(individualId, optionId)
        }
        return publish(individualId, result)
    }

    override suspend fun dismiss(individualId: String): DigilineResult {
        val result = if (isWild(individualId)) {
            wild.dismiss(individualId)
        } else {
            owned.dismiss(individualId)
        }
        return publish(individualId, result)
    }

    private fun publish(individualId: String, result: DigilineResult): DigilineResult {
        val scene = (result as? DigilineResult.Scene)?.scene
        scenes.value = scenes.value + (individualId to scene)
        return result
    }

    private suspend fun isWild(individualId: String): Boolean =
        database.userCharacterDao().getByIndividualId(individualId).isEmpty()
}
