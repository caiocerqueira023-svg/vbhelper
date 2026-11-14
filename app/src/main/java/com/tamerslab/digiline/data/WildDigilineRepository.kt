package com.tamerslab.digiline.data

import androidx.room.withTransaction
import com.tamerslab.data.database.AppDatabase
import com.tamerslab.digiline.content.DigilineContentProvider
import com.tamerslab.digiline.engine.NegotiationEngine
import com.tamerslab.digiline.engine.PersuasionEngine
import com.tamerslab.digiline.gateway.DigilineOptionView
import com.tamerslab.digiline.gateway.DigilineResult
import com.tamerslab.digiline.gateway.DigilineScene
import com.tamerslab.digiline.model.DigilineContentPack
import com.tamerslab.digiline.model.DigilineSeed
import com.tamerslab.digiline.model.NegotiationOutcome
import com.tamerslab.digiline.model.NegotiationState
import com.tamerslab.digiline.model.PersuasionOutcome
import com.tamerslab.digiline.model.PersuasionState
import com.tamerslab.domain.chat.ChatMessageEntity
import com.tamerslab.domain.digifarm.WildRelationship
import com.tamerslab.items.data.CurrencyRepository
import com.tamerslab.quests.QuestRepository
import com.tamerslab.species.SpeciesRepository
import com.tamerslab.world.ecosystem.WorldSocialRepository
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

/**
 * Wild Digimon DigiLine: negotiation (unjoined) and post-join persuasion (plan §7).
 *
 * Trust no longer gates anything (D6): a negotiation `JOIN` outcome unlocks the contact in the
 * DigiLine Wild tab, and the authored persuasion arc replaces the trust meter. `DEPART` outcomes
 * may remove a contact, but only through authored nodes with a preceding tell (D11/R6).
 */
class WildDigilineRepository(
    private val database: AppDatabase,
    private val content: DigilineContentProvider,
    private val speciesRepository: SpeciesRepository,
    private val currencyRepository: CurrencyRepository,
    private val questRepository: QuestRepository,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val dao get() = database.digilineDao()

    /**
     * The line currently displayed in the scene card but not yet written to the transcript.
     * Committing it when the player advances keeps the transcript and the card from duplicating
     * each other, so the chat reads like a real back-and-forth instead of jump cuts.
     */
    private val pendingLine = mutableMapOf<String, String>()

    /** Terminal outcome line waiting for the player to press Continue. */
    private val pendingTerminal = mutableMapOf<String, String>()

    suspend fun ensureScene(wildIndividualId: String): DigilineResult {
        // A scene shown before the player left is re-displayed below; do not duplicate it in
        // history. A terminal acknowledgement that was never continued is committed now.
        pendingLine.remove(wildIndividualId)
        commitPendingTerminal(wildIndividualId)
        val current = now()
        val relationship = database.wildRelationshipDao().get(wildIndividualId)
            ?: return DigilineResult.Error("This Digimon is no longer available.")
        val pack = content.get()
        if (!pack.hasContent) return DigilineResult.Closed(null)

        if (relationship.contactUnlockedAt != null) {
            return persuasionScene(wildIndividualId, relationship, pack, current)
        }
        return negotiationScene(wildIndividualId, relationship, pack, current)
    }

    suspend fun choose(wildIndividualId: String, optionId: String): DigilineResult {
        val current = now()
        val relationship = database.wildRelationshipDao().get(wildIndividualId)
            ?: return DigilineResult.Error("This Digimon is no longer available.")
        val pack = content.get()
        return if (relationship.contactUnlockedAt != null) {
            choosePersuasion(wildIndividualId, relationship, pack, optionId, current)
        } else {
            chooseNegotiation(wildIndividualId, relationship, pack, optionId, current)
        }
    }

    /** Closing keeps a negotiation resumable and only discards the current persuasion line. */
    suspend fun dismiss(wildIndividualId: String): DigilineResult {
        pendingLine.remove(wildIndividualId)
        commitPendingTerminal(wildIndividualId)
        return DigilineResult.Closed(null)
    }

    // ---------------------------------------------------------------------------- negotiation

    private suspend fun negotiationScene(
        wildIndividualId: String,
        relationship: WildRelationship,
        pack: DigilineContentPack,
        current: Long,
    ): DigilineResult {
        val type = personalityType(wildIndividualId)
        val tier = tierFor(relationship)
        val flow = pack.negotiationFlow(type, tier) ?: return DigilineResult.Closed(null)
        val session = dao.negotiation(wildIndividualId)
        val contextKey = if (session != null) "wild_resume" else "wild_new"
        val state = if (session != null) {
            resumeNegotiation(session, flow, type)
        } else {
            val seed = DigilineSeed.combine(
                DigilineSeed.stableHash(wildIndividualId),
                current,
            )
            val firstConversation = dao.negotiationLogs(wildIndividualId).isEmpty()
            val started = NegotiationEngine.start(
                flow = flow,
                seed = seed,
                tier = tier,
                personalityType = type,
                firstConversation = firstConversation,
            )
            if (started.finished && started.outcome != null) {
                return finishNegotiation(wildIndividualId, started.outcome, null, null, current)
            }
            dao.upsertNegotiation(
                DigilineNegotiationEntity(
                    wildIndividualId = wildIndividualId,
                    tier = tier,
                    nodeId = started.nodeId,
                    phase = started.phase.name,
                    seed = seed,
                    demandsJson = encodeLedger(started),
                    startedAt = current,
                    updatedAt = current,
                    state = SESSION_ACTIVE,
                    departUsed = false,
                )
            )
            started
        }
        return DigilineResult.Scene(negotiationSceneOf(state, contextKey))
    }

    private suspend fun chooseNegotiation(
        wildIndividualId: String,
        relationship: WildRelationship,
        pack: DigilineContentPack,
        optionId: String,
        current: Long,
    ): DigilineResult {
        val type = personalityType(wildIndividualId)
        val tier = tierFor(relationship)
        val flow = pack.negotiationFlow(type, tier) ?: return DigilineResult.Closed(null)
        val session = dao.negotiation(wildIndividualId)
            ?: return negotiationScene(wildIndividualId, relationship, pack, current)
        commitPendingLine(wildIndividualId)
        val state = resumeNegotiation(session, flow, type)
        val playerBits = runCatching { currencyRepository.currencyValue.first().toLong() }.getOrDefault(0L)
        val step = NegotiationEngine.choose(
            state = state,
            optionId = optionId,
            playerBits = playerBits,
            playerItems = emptyMap(),
        )
        if (!step.accepted) {
            return DigilineResult.Error(step.message ?: "That choice is not available.")
        }

        val newBalance = (playerBits + step.bitsDelta).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        if (step.bitsDelta != 0L) {
            runCatching { currencyRepository.setCurrencyValue(newBalance) }
        }

        database.withTransaction {
            dao.upsertNegotiationLog(
                DigilineNegotiationLogEntity(
                    wildIndividualId = wildIndividualId,
                    nodeId = step.previousNode?.id ?: state.nodeId,
                    optionId = optionId,
                    effectsJson = encodeMove(step),
                    createdAt = current,
                )
            )
            dao.upsertNegotiation(
                session.copy(
                    nodeId = step.state.nodeId,
                    phase = step.state.phase.name,
                    demandsJson = encodeLedger(step.state),
                    updatedAt = current,
                    departUsed = step.state.departUsed,
                )
            )
        }
        append(wildIndividualId, ROLE_USER, step.option?.text)
        val reaction = step.option?.reaction?.trim()?.takeIf { it.isNotEmpty() }

        val outcome = step.outcome
        if (outcome != null) {
            return finishNegotiation(wildIndividualId, outcome, step.node?.text, reaction, current)
        }
        val nextLines = listOfNotNull(reaction, step.node?.text?.trim()?.takeIf { it.isNotEmpty() })
        pendingLine[wildIndividualId] = nextLines.joinToString("\n")
        return DigilineResult.Scene(negotiationSceneOf(step.state, null, reaction))
    }

    private suspend fun finishNegotiation(
        wildIndividualId: String,
        outcome: NegotiationOutcome,
        terminalLine: String?,
        reaction: String?,
        current: Long,
    ): DigilineResult {
        when (outcome) {
            NegotiationOutcome.JOIN -> {
                database.withTransaction {
                    database.wildRelationshipDao().unlockQuestContact(wildIndividualId, current)
                    dao.upsertWildPersuasion(
                        WildPersuasionEntity(
                            wildIndividualId = wildIndividualId,
                            stageIndex = 0,
                            questOfferState = QUEST_NONE,
                            joinedAt = current,
                            lastTalkAt = current,
                        )
                    )
                    dao.deleteNegotiation(wildIndividualId)
                }
            }

            NegotiationOutcome.DEPART -> {
                database.withTransaction {
                    database.wildRelationshipDao().clearQuestContact(wildIndividualId, current)
                    dao.deleteWildPersuasion(wildIndividualId)
                    dao.deleteNegotiation(wildIndividualId)
                }
            }

            else -> dao.deleteNegotiation(wildIndividualId)
        }
        pendingLine.remove(wildIndividualId)
        val committed = listOfNotNull(reaction, terminalLine?.takeIf { it.isNotBlank() })
            .joinToString("\n")
        committed.takeIf { it.isNotBlank() }?.let { pendingTerminal[wildIndividualId] = it }
        return DigilineResult.Scene(reactionScene(terminalLine, outcome.name, reaction))
    }

    private fun reactionScene(
        line: String?,
        outcomeKey: String?,
        reaction: String? = null,
    ): DigilineScene = DigilineScene(
        kind = DigilineScene.Kind.REACTION,
        reactionLine = reaction?.takeIf { it.isNotBlank() },
        lines = line?.takeIf { it.isNotBlank() }?.let { listOf(it) }.orEmpty(),
        outcomeKey = outcomeKey,
        canDismiss = true,
        contextKey = "wild_result",
    )

    private fun negotiationSceneOf(
        state: NegotiationState,
        contextKey: String?,
        reaction: String? = null,
    ): DigilineScene {
        val demand = state.node?.demand
        return DigilineScene(
            kind = DigilineScene.Kind.NEGOTIATION,
            reactionLine = reaction?.takeIf { it.isNotBlank() },
            lines = listOfNotNull(state.node?.text),
            options = NegotiationEngine.availableOptions(state, null, emptyMap())
                .map { DigilineOptionView(it.id, it.text) },
            canDismiss = true,
            demandBits = demand?.takeIf { it.kind.equals("bits", ignoreCase = true) }
                ?.amount?.toLong()?.takeIf { it > 0 },
            contextKey = contextKey,
        )
    }

    private fun resumeNegotiation(
        session: DigilineNegotiationEntity,
        flow: com.tamerslab.digiline.model.NegotiationFlowDto,
        type: Int,
    ): NegotiationState {
        val ledger = decodeLedger(session.demandsJson)
        return NegotiationState(
            flow = flow,
            nodeId = session.nodeId,
            tier = session.tier,
            seed = session.seed,
            personalityType = type,
            bitsPaid = ledger.bitsPaid,
            itemsPaid = ledger.itemsPaid,
            flags = ledger.flags,
            departUsed = session.departUsed,
            firstConversation = false,
            finished = false,
            outcome = null,
            history = emptyList(),
        )
    }

    // ------------------------------------------------------------------------------ persuasion

    private suspend fun persuasionScene(
        wildIndividualId: String,
        relationship: WildRelationship,
        pack: DigilineContentPack,
        current: Long,
    ): DigilineResult {
        val existing = dao.wildPersuasion(wildIndividualId)
        val persuasion = existing
            ?: WildPersuasionEntity(
                wildIndividualId = wildIndividualId,
                stageIndex = 0,
                questOfferState = QUEST_NONE,
                joinedAt = relationship.contactUnlockedAt ?: current,
                lastTalkAt = current,
            ).also { dao.upsertWildPersuasion(it) }
        val contextKey = if (existing != null) "persuasion_resume" else "persuasion_new"

        if (persuasion.departureState == DEPARTURE_DONE) return DigilineResult.Closed(null)
        if (persuasion.questOfferState == QUEST_PENDING) {
            runCatching { questRepository.ensureOffers(wildIndividualId) }
            val refreshed = dao.wildPersuasion(wildIndividualId) ?: persuasion
            if (refreshed.questOfferState != QUEST_PENDING) return DigilineResult.Closed(null)
        }
        if (persuasion.questOfferState == QUEST_OFFERED) return DigilineResult.Closed(null)

        val stage = persuasion.stageIndex.coerceIn(0, MAX_PERSUASION_STAGE)
        val type = personalityType(wildIndividualId)
        val flow = pack.persuasionFlow(type, stage) ?: return DigilineResult.Closed(null)
        val state = PersuasionEngine.start(
            flow = flow,
            stage = stage,
            seed = DigilineSeed.combine(DigilineSeed.stableHash(wildIndividualId), stage.toLong()),
            personalityType = type,
            flags = decodeFlags(persuasion.topicsDoneJson),
        ) ?: return DigilineResult.Closed(null)
        pendingLine[wildIndividualId] = state.node?.text.orEmpty()
        return DigilineResult.Scene(persuasionSceneOf(state, contextKey))
    }

    private suspend fun choosePersuasion(
        wildIndividualId: String,
        relationship: WildRelationship,
        pack: DigilineContentPack,
        optionId: String,
        current: Long,
    ): DigilineResult {
        val persuasion = dao.wildPersuasion(wildIndividualId)
            ?: return DigilineResult.Error("This Digimon has not joined your DigiLine contacts yet.")
        if (persuasion.questOfferState == QUEST_OFFERED) return DigilineResult.Closed(null)
        val stage = persuasion.stageIndex.coerceIn(0, MAX_PERSUASION_STAGE)
        val type = personalityType(wildIndividualId)
        val flow = pack.persuasionFlow(type, stage) ?: return DigilineResult.Closed(null)
        val state = PersuasionEngine.start(
            flow = flow,
            stage = stage,
            seed = DigilineSeed.combine(DigilineSeed.stableHash(wildIndividualId), stage.toLong()),
            personalityType = type,
            flags = decodeFlags(persuasion.topicsDoneJson),
        ) ?: return DigilineResult.Closed(null)

        val step = PersuasionEngine.choose(state, optionId)
        if (!step.accepted) return DigilineResult.Error(step.message ?: "That choice is not available.")

        commitPendingLine(wildIndividualId)
        append(wildIndividualId, ROLE_USER, step.option?.text)

        val newFlags = step.state.flags
        val updatedStage = step.nextStage?.coerceIn(0, MAX_PERSUASION_STAGE)
            ?: if (step.outcome == PersuasionOutcome.NEXT_STAGE) PersuasionEngine.nextStage(stage) else stage
        val updated = persuasion.copy(
            stageIndex = updatedStage,
            topicsDoneJson = encodeFlags(newFlags),
            lastTalkAt = current,
            questOfferState = when (step.outcome) {
                PersuasionOutcome.QUEST_OFFER -> QUEST_PENDING
                else -> persuasion.questOfferState
            },
        )

        if (step.outcome == PersuasionOutcome.DEPART) {
            database.withTransaction {
                dao.upsertWildPersuasion(updated.copy(departureState = DEPARTURE_DONE))
                database.wildRelationshipDao().clearQuestContact(wildIndividualId, current)
            }
            return acknowledgement(wildIndividualId, step.node?.text, "DEPART")
        }
        if (step.outcome == PersuasionOutcome.QUEST_OFFER) {
            dao.upsertWildPersuasion(updated)
            runCatching { questRepository.ensureOffers(wildIndividualId) }
            return acknowledgement(wildIndividualId, step.node?.text, "QUEST_OFFER")
        }

        dao.upsertWildPersuasion(updated)
        if (step.state.finished || step.state.node?.options.isNullOrEmpty()) {
            val outcome = step.outcome?.name ?: "REFUSE"
            return acknowledgement(wildIndividualId, step.node?.text, outcome)
        }
        pendingLine[wildIndividualId] = step.node?.text.orEmpty()
        return DigilineResult.Scene(persuasionSceneOf(step.state, null))
    }

    private fun acknowledgement(wildIndividualId: String, line: String?, outcomeKey: String?): DigilineResult {
        pendingLine.remove(wildIndividualId)
        line?.takeIf { it.isNotBlank() }?.let { pendingTerminal[wildIndividualId] = it }
        return DigilineResult.Scene(reactionScene(line, outcomeKey))
    }

    private fun persuasionSceneOf(
        state: PersuasionState,
        contextKey: String?,
    ): DigilineScene = DigilineScene(
        kind = DigilineScene.Kind.PERSUASION,
        lines = listOfNotNull(state.node?.text),
        options = PersuasionEngine.availableOptions(state)
            .map { DigilineOptionView(it.id, it.text) },
        canDismiss = true,
        contextKey = contextKey,
    )

    // --------------------------------------------------------------------------------- helpers

    private suspend fun personalityType(wildIndividualId: String): Int =
        runCatching {
            WorldSocialRepository(database).profile(wildIndividualId).personality.number
        }.getOrDefault(DEFAULT_PERSONALITY)

    private suspend fun tierFor(relationship: WildRelationship): Int {
        val species = runCatching {
            speciesRepository.getProfileForCharacter(relationship.cardCharacterId)
        }.getOrNull()
        val cardStage = runCatching {
            database.characterDao().getById(relationship.cardCharacterId)?.stage
        }.getOrNull()
        return DigilineStages.tier(cardStage, species?.level)
    }

    private suspend fun commitPendingLine(individualId: String) {
        val line = pendingLine.remove(individualId)?.takeIf { it.isNotBlank() } ?: return
        append(individualId, ROLE_ASSISTANT, line)
    }

    private suspend fun commitPendingTerminal(individualId: String) {
        val line = pendingTerminal.remove(individualId)?.takeIf { it.isNotBlank() } ?: return
        append(individualId, ROLE_ASSISTANT, line)
    }

    private suspend fun append(individualId: String, role: String, text: String?) {
        val value = text?.trim().orEmpty()
        if (value.isEmpty()) return
        database.chatDao().insertMessage(
            ChatMessageEntity(
                individualId = individualId,
                role = role,
                content = value,
                timestamp = now(),
                kind = if (role == ROLE_USER) KIND_USER_MESSAGE else KIND_ASSISTANT_MESSAGE,
            )
        )
    }

    private data class Ledger(
        val bitsPaid: Long = 0,
        val itemsPaid: Map<String, Int> = emptyMap(),
        val flags: Set<String> = emptySet(),
    )

    private fun encodeLedger(state: NegotiationState): String {
        val json = JSONObject()
        json.put("bits", state.bitsPaid)
        val items = JSONObject()
        state.itemsPaid.forEach { (id, count) -> items.put(id, count) }
        json.put("items", items)
        val flags = JSONArray()
        state.flags.sorted().forEach { flags.put(it) }
        json.put("flags", flags)
        return json.toString()
    }

    private fun decodeLedger(raw: String?): Ledger {
        if (raw.isNullOrBlank()) return Ledger()
        return runCatching {
            val json = JSONObject(raw)
            val itemsObject = json.optJSONObject("items")
            val items = buildMap {
                if (itemsObject != null) {
                    itemsObject.keys().forEach { key -> put(key, itemsObject.optInt(key, 0)) }
                }
            }
            val flagsArray = json.optJSONArray("flags")
            val flags = buildSet {
                if (flagsArray != null) {
                    for (index in 0 until flagsArray.length()) {
                        flagsArray.optString(index).takeIf { it.isNotBlank() }?.let(::add)
                    }
                }
            }
            Ledger(bitsPaid = json.optLong("bits", 0L), itemsPaid = items, flags = flags)
        }.getOrDefault(Ledger())
    }

    private fun encodeMove(step: com.tamerslab.digiline.model.NegotiationStep): String {
        val json = JSONObject()
        json.put("from", step.previousNode?.id.orEmpty())
        json.put("to", step.state.nodeId)
        json.put("bits", step.bitsDelta)
        json.put("outcome", step.outcome?.name.orEmpty())
        return json.toString()
    }

    private fun encodeFlags(flags: Set<String>): String {
        val array = JSONArray()
        flags.sorted().forEach { array.put(it) }
        return array.toString()
    }

    private fun decodeFlags(raw: String): Set<String> =
        runCatching {
            val array = JSONArray(raw)
            buildSet {
                for (index in 0 until array.length()) {
                    array.optString(index).takeIf { it.isNotBlank() }?.let(::add)
                }
            }
        }.getOrDefault(emptySet())

    companion object {
        const val SESSION_ACTIVE = "ACTIVE"
        const val QUEST_NONE = "NONE"
        const val QUEST_PENDING = "PENDING"
        const val QUEST_OFFERED = "OFFERED"
        const val DEPARTURE_DONE = "DEPARTED"
        const val MAX_PERSUASION_STAGE = 3
        const val DEFAULT_PERSONALITY = 14
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
        const val KIND_USER_MESSAGE = "digiline_user"
        const val KIND_ASSISTANT_MESSAGE = "digiline_assistant"
    }
}
