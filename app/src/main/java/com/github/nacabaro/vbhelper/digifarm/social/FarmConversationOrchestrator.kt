package com.github.nacabaro.vbhelper.digifarm.social

import com.github.nacabaro.vbhelper.chat.ChatRepository
import com.github.nacabaro.vbhelper.digifarm.DigifarmRepository
import com.github.nacabaro.vbhelper.digifarm.map.BirdFarmMap
import com.github.nacabaro.vbhelper.digifarm.map.MapPoint
import com.github.nacabaro.vbhelper.domain.digifarm.FarmMessage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * Validated public utterance envelope (§8). The app binds the author; the model
 * never chooses it. Intents are limited to social actions; no SQL or commands.
 */
data class FarmUtterance(
    val speech: String,
    val audience: String,
    val recipientIds: List<String>,
    val intent: String? = null,
    val targetId: String? = null,
    val replyToMessageId: String? = null
)

object FarmUtteranceValidator {
    val ALLOWED_INTENTS = setOf("speak", "approach", "invite_activity", "accept_invite", "decline_invite", "end_conversation")
    const val MAX_SPEECH_CHARS = 600

    fun validate(
        speech: String,
        recipientIds: List<String>,
        farmResidentIds: Set<String>,
        intent: String? = null,
        targetId: String? = null,
        replyToMessageId: String? = null
    ): FarmUtterance? {
        val clean = speech.trim().take(MAX_SPEECH_CHARS)
        if (clean.isEmpty()) return null
        if (recipientIds.any { it !in farmResidentIds }) return null
        if (intent != null && intent !in ALLOWED_INTENTS) return null
        if (targetId != null && targetId !in farmResidentIds) return null
        val audience = when {
            recipientIds.isEmpty() -> "ALL"
            recipientIds.size == 1 -> "INDIVIDUAL"
            else -> "SUBSET"
        }
        return FarmUtterance(clean, audience, recipientIds.distinct(), intent, targetId, replyToMessageId)
    }

    /** Plain-text fallback: valid speech with pre-authorized recipients, actions ignored. */
    fun fallback(speech: String, recipientIds: List<String>, farmResidentIds: Set<String>): FarmUtterance? =
        validate(speech, recipientIds.filter { it in farmResidentIds }, farmResidentIds)
}

/** Short-lived conversation session (§8): max 6 turns, then residents are freed. */
data class ConversationSession(
    val sessionId: String,
    val participantIds: List<String>,
    val turnCount: Int,
    val startedAt: Long,
    val expiresAt: Long
)

class FarmConversationOrchestrator(
    private val farmRepository: DigifarmRepository,
    private val chatRepository: ChatRepository
) {
    private val generationMutex = Mutex()
    private val lastAutonomousByFarm = mutableMapOf<String, Long>()
    private val sessions = mutableMapOf<String, ConversationSession>()

    companion object {
        const val MAX_SESSION_TURNS = 6
        const val SESSION_EXPIRY_MILLIS = 5 * 60_000L
        /** Feet distance under which spontaneous talk is allowed. */
        const val TALK_PROXIMITY_PX = 220f
        private val rateLock = Any()
        private val globalCalls = ArrayDeque<Long>()
    }

    suspend fun sendTamerMessage(
        farmId: String,
        text: String,
        recipientIds: List<String>
    ): List<FarmMessage> = generationMutex.withLock {
        val residents = farmRepository.residentsSnapshot(farmId)
        val ids = residents.map { it.individualId }.toSet()
        // Tamer messages are delivered digitally even at distance (§8); only membership is validated.
        val utterance = FarmUtteranceValidator.fallback(text, recipientIds, ids) ?: return emptyList()
        val userMessage = farmRepository.postMessage(
            farmId = farmId,
            authorIndividualId = null,
            authorName = "Tamer",
            body = utterance.speech,
            recipientIds = utterance.recipientIds
        )
        val responders = if (utterance.recipientIds.isEmpty()) residents.take(2) else {
            residents.filter { it.individualId in utterance.recipientIds }.take(3)
        }
        val replies = mutableListOf(userMessage)
        val sessionId = UUID.randomUUID().toString()
        responders.forEach { resident ->
            if (!acquireCallSlot()) return@forEach
            val context = buildContext(farmId, resident.individualId, resident.displayName, "Tamer said: ${utterance.speech}")
            val rawReply = runCatching { chatRepository.generateFarmReply(resident.characterId, context) }.getOrNull()
                ?: return@forEach
            val validated = FarmUtteranceValidator.fallback(rawReply, emptyList(), ids) ?: return@forEach
            replies += farmRepository.postMessage(
                farmId = farmId,
                authorIndividualId = resident.individualId,
                authorName = resident.displayName,
                body = validated.speech,
                recipientIds = emptyList(),
                sessionId = sessionId
            )
        }
        replies
    }

    suspend fun maybeGenerateAutonomous(farmId: String): FarmMessage? = generationMutex.withLock {
        val now = System.currentTimeMillis()
        if (now - (lastAutonomousByFarm[farmId] ?: 0L) < 20_000L || !acquireCallSlot()) return null
        val residents = farmRepository.residentsSnapshot(farmId)
        if (residents.size < 2) return null
        val active = sessions[farmId]
        if (active != null && (now > active.expiresAt || active.turnCount >= MAX_SESSION_TURNS)) {
            sessions.remove(farmId)
        }
        val author = residents[((now / 20_000L) % residents.size).toInt()]
        // Prefer a nearby listener; otherwise approach instead of talking across the map.
        val candidates = residents.filter { it.individualId != author.individualId }
        val listener = candidates.minByOrNull {
            BirdFarmMap.distance(
                MapPoint(it.positionX, it.positionY),
                MapPoint(author.positionX, author.positionY)
            )
        } ?: return null
        val distance = BirdFarmMap.distance(
            MapPoint(listener.positionX, listener.positionY),
            MapPoint(author.positionX, author.positionY)
        )
        val connected = BirdFarmMap.areConnected(
            MapPoint(author.positionX, author.positionY),
            MapPoint(listener.positionX, listener.positionY)
        )
        if (!connected || distance > TALK_PROXIMITY_PX) {
            farmRepository.steerToward(farmId, author.individualId, listener.positionX, listener.positionY)
            return null
        }
        val session = sessions[farmId]?.takeIf {
            author.individualId in it.participantIds && listener.individualId in it.participantIds
        } ?: ConversationSession(
            sessionId = UUID.randomUUID().toString(),
            participantIds = listOf(author.individualId, listener.individualId),
            turnCount = 0,
            startedAt = now,
            expiresAt = now + SESSION_EXPIRY_MILLIS
        )
        val context = buildContext(
            farmId,
            author.individualId,
            author.displayName,
            "${author.displayName} is near ${listener.displayName}. Start or continue a brief everyday conversation addressed to ${listener.displayName}."
        )
        val rawReply = runCatching { chatRepository.generateFarmReply(author.characterId, context) }.getOrNull()
            ?: return null
        lastAutonomousByFarm[farmId] = now
        val validated = FarmUtteranceValidator.fallback(rawReply, listOf(listener.individualId), residents.map { it.individualId }.toSet())
            ?: return null
        val firstMessage = farmRepository.postMessage(
            farmId = farmId,
            authorIndividualId = author.individualId,
            authorName = author.displayName,
            body = validated.speech,
            recipientIds = validated.recipientIds,
            sessionId = session.sessionId
        )
        farmRepository.recordInteraction(author.individualId, listener.individualId)
        farmRepository.rememberConversation(
            author.individualId,
            listener.individualId,
            firstMessage.id,
            "Spoke with ${listener.displayName}: ${validated.speech}"
        )
        sessions[farmId] = session.copy(turnCount = session.turnCount + 1)
        if (acquireCallSlot()) {
            val responseContext = buildContext(
                farmId,
                listener.individualId,
                listener.displayName,
                "${author.displayName} just said to you: ${validated.speech}. Reply briefly if it fits your personality."
            )
            val rawResponse = runCatching { chatRepository.generateFarmReply(listener.characterId, responseContext) }.getOrNull()
            if (rawResponse != null) {
                val validatedResponse = FarmUtteranceValidator.fallback(rawResponse, listOf(author.individualId), residents.map { it.individualId }.toSet())
                if (validatedResponse != null) {
                    val responseMessage = farmRepository.postMessage(
                        farmId = farmId,
                        authorIndividualId = listener.individualId,
                        authorName = listener.displayName,
                        body = validatedResponse.speech,
                        recipientIds = validatedResponse.recipientIds,
                        sessionId = session.sessionId
                    )
                    farmRepository.rememberConversation(
                        listener.individualId,
                        author.individualId,
                        responseMessage.id,
                        "Replied to ${author.displayName}: ${validatedResponse.speech}"
                    )
                    sessions[farmId] = session.copy(turnCount = session.turnCount + 2)
                    return responseMessage
                }
            }
        }
        firstMessage
    }

    private suspend fun buildContext(
        farmId: String,
        authorId: String,
        authorName: String,
        event: String
    ): String {
        val recent = farmRepository.messagesSnapshot(farmId, 8)
            .joinToString("\n") { "${it.authorNameSnapshot}: ${it.body}" }
        val memories = farmRepository.memories(authorId).joinToString("\n") { "- ${it.summary}" }
        return "You are $authorName. $event\nRelevant memories:\n$memories\nRecent public Digifarm conversation:\n$recent"
    }

    private fun acquireCallSlot(): Boolean {
        synchronized(rateLock) {
            val now = System.currentTimeMillis()
            while (globalCalls.firstOrNull()?.let { now - it > 3_600_000L } == true) globalCalls.removeFirst()
            if (globalCalls.size >= 60 || globalCalls.count { now - it <= 60_000L } >= 3) return false
            globalCalls += now
            return true
        }
    }
}
