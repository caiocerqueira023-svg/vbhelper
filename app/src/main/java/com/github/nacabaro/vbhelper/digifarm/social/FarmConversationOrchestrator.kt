package com.github.nacabaro.vbhelper.digifarm.social

import com.github.nacabaro.vbhelper.chat.ChatRepository
import com.github.nacabaro.vbhelper.digifarm.DigifarmRepository
import com.github.nacabaro.vbhelper.domain.digifarm.FarmMessage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class FarmConversationOrchestrator(
    private val farmRepository: DigifarmRepository,
    private val chatRepository: ChatRepository
) {
    private val generationMutex = Mutex()
    private val lastAutonomousByFarm = mutableMapOf<String, Long>()

    companion object {
        private val rateLock = Any()
        private val globalCalls = ArrayDeque<Long>()
    }

    suspend fun sendTamerMessage(
        farmId: String,
        text: String,
        recipientIds: List<String>
    ): List<FarmMessage> = generationMutex.withLock {
        val userMessage = farmRepository.postMessage(
            farmId = farmId,
            authorIndividualId = null,
            authorName = "Tamer",
            body = text,
            recipientIds = recipientIds
        )
        val residents = farmRepository.residentsSnapshot(farmId)
        val responders = if (recipientIds.isEmpty()) residents.take(2) else {
            residents.filter { it.individualId in recipientIds }.take(3)
        }
        val replies = mutableListOf(userMessage)
        val sessionId = UUID.randomUUID().toString()
        responders.forEach { resident ->
            if (!acquireCallSlot()) return@forEach
            val context = buildContext(farmId, resident.individualId, resident.displayName, "Tamer said: $text")
            val reply = chatRepository.generateFarmReply(resident.characterId, context)
            replies += farmRepository.postMessage(
                farmId = farmId,
                authorIndividualId = resident.individualId,
                authorName = resident.displayName,
                body = reply,
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
        val author = residents[((now / 20_000L) % residents.size).toInt()]
        val listener = residents.first { it.individualId != author.individualId }
        val context = buildContext(
            farmId,
            author.individualId,
            author.displayName,
            "${author.displayName} is near ${listener.displayName}. Start or continue a brief everyday conversation addressed to ${listener.displayName}."
        )
        val reply = chatRepository.generateFarmReply(author.characterId, context)
        lastAutonomousByFarm[farmId] = now
        val firstMessage = farmRepository.postMessage(
            farmId = farmId,
            authorIndividualId = author.individualId,
            authorName = author.displayName,
            body = reply,
            recipientIds = listOf(listener.individualId),
            sessionId = UUID.randomUUID().toString()
        )
        farmRepository.recordInteraction(author.individualId, listener.individualId)
        farmRepository.rememberConversation(
            author.individualId,
            listener.individualId,
            firstMessage.id,
            "Spoke with ${listener.displayName}: $reply"
        )
        if (acquireCallSlot()) {
            val responseContext = buildContext(
                farmId,
                listener.individualId,
                listener.displayName,
                "${author.displayName} just said to you: $reply. Reply briefly if it fits your personality."
            )
            val response = chatRepository.generateFarmReply(listener.characterId, responseContext)
            val responseMessage = farmRepository.postMessage(
                farmId = farmId,
                authorIndividualId = listener.individualId,
                authorName = listener.displayName,
                body = response,
                recipientIds = listOf(author.individualId),
                sessionId = firstMessage.sessionId
            )
            farmRepository.rememberConversation(
                listener.individualId,
                author.individualId,
                responseMessage.id,
                "Replied to ${author.displayName}: $response"
            )
            return responseMessage
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
