package com.github.nacabaro.vbhelper.digifarm.social

import com.github.nacabaro.vbhelper.chat.ChatRepository
import com.github.nacabaro.vbhelper.digifarm.DigifarmRepository
import com.github.nacabaro.vbhelper.domain.digifarm.FarmMessage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import kotlin.math.hypot
import com.github.nacabaro.vbhelper.digifarm.FarmBehaviorPolicy
import com.github.nacabaro.vbhelper.domain.personality.SocialRandom
import com.github.nacabaro.vbhelper.domain.personality.SocialEventAppraisal
import com.github.nacabaro.vbhelper.domain.personality.SocialStimulus
import com.github.nacabaro.vbhelper.world.ecosystem.*
import com.github.nacabaro.vbhelper.chat.PromptLocalization
import com.github.nacabaro.vbhelper.chat.WorldDialogueCodec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException

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
    private val lastInitiationByIndividual = mutableMapOf<String, Long>()
    private val lifetime = FarmConversationLifetime()

    fun acquire(farmId: String) = lifetime.acquire(farmId)
    fun release(farmId: String) = lifetime.release(farmId)
    fun suspendFarm(farmId: String) = lifetime.suspendIfUnobserved(farmId)

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
        val token = lifetime.token(farmId)
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
        val farm = farmRepository.getFarm(farmId) ?: return emptyList()
        val profiles = residents.associate { it.individualId to farmRepository.socialProfile(it.individualId) }
        val responders = if (utterance.recipientIds.isEmpty()) residents.sortedBy { resident ->
            val profile = profiles.getValue(resident.individualId)
            val interest = if (SocialEventAppraisal.classify(text) == SocialStimulus.CHALLENGE) profile.challenge else profile.initiative
            -kotlin.math.ln(SocialRandom.unit(farm.randomSeed,resident.individualId,"farm-user-response",userMessage.sequence).coerceAtLeast(1e-12)) / (.1 + interest)
        }.take(2) else {
            residents.filter { it.individualId in utterance.recipientIds }.take(3)
        }
        val replies = mutableListOf(userMessage)
        val sessionId = UUID.randomUUID().toString()
        responders.forEach { resident ->
            val context = buildContext(farmId, resident.individualId, resident.displayName, "Tamer said: ${utterance.speech}")
            val motive = when (SocialEventAppraisal.classify(text)) {
                SocialStimulus.REFUSAL -> SocialMotive.AVOIDANCE
                SocialStimulus.CHALLENGE -> SocialMotive.SHARED_ACTIVITY
                SocialStimulus.TEASING -> SocialMotive.PLAYFUL_TEASING
                SocialStimulus.CARE -> SocialMotive.CHECK_IN
                else -> SocialMotive.COMPANY
            }
            val rawReply = generate(farmId,resident.characterId,resident.individualId,context,
                SocialEncounterDecision(resident.individualId,"trainer",motive,(userMessage.sequence % 64).toInt()), token)
                ?: return@forEach
            if (!membersStillPresent(farmId, token, setOf(resident.individualId))) return@forEach
            val validated = FarmUtteranceValidator.fallback(rawReply, emptyList(), ids) ?: return@forEach
            replies += farmRepository.postMessage(
                farmId = farmId,
                authorIndividualId = resident.individualId,
                authorName = resident.displayName,
                body = validated.speech,
                recipientIds = emptyList(),
                sessionId = sessionId,
                publicationAllowed = { lifetime.isCurrent(farmId,token) }
            )
        }
        replies
    }

    suspend fun maybeGenerateAutonomous(farmId: String): FarmMessage? = generationMutex.withLock {
        val now = System.currentTimeMillis()
        val token = lifetime.begin(farmId) ?: return null
        if (now - (lastAutonomousByFarm[farmId] ?: 0L) < 20_000L) return null
        val farm = farmRepository.getFarm(farmId)?.takeIf { it.autonomousDialogueEnabled && it.archivedAt == null } ?: return null
        val residents = farmRepository.residentsSnapshot(farmId)
        if (residents.size < 2) return null
        val states = farmRepository.residentStates(farmId).associateBy { it.individualId }
        val profiles = residents.associate { it.individualId to farmRepository.socialProfile(it.individualId) }
        val available = residents.filter { resident -> states[resident.individualId]?.let {
            FarmBehaviorPolicy.available(it, profiles.getValue(resident.individualId), now)
        } == true }
        if (available.size < 2) return null
        val active = sessions[farmId]
        if (active != null && (now > active.expiresAt || active.turnCount >= MAX_SESSION_TURNS)) {
            sessions.remove(farmId)
        }
        val continuing = sessions[farmId]?.takeIf { it.turnCount < MAX_SESSION_TURNS && it.expiresAt > now &&
            it.participantIds.all { id -> available.any { resident -> resident.individualId == id } } }
        val author = continuing?.participantIds?.firstOrNull()?.let { id -> available.firstOrNull { it.individualId == id } }
            ?: available.filter { resident -> now - (lastInitiationByIndividual[resident.individualId] ?:
                farmRepository.memories(resident.individualId).maxOfOrNull { it.createdAt } ?: 0) >=
                (25_000 + (1 - profiles.getValue(resident.individualId).initiative) * 50_000).toLong() }
                .minByOrNull { resident -> -kotlin.math.ln(SocialRandom.unit(farm.randomSeed,resident.individualId,"farm-initiation",now/20_000).coerceAtLeast(1e-12)) /
                    (.1 + profiles.getValue(resident.individualId).initiative) } ?: return null
        // Prefer a nearby listener; otherwise approach instead of talking across the map.
        val relationships = farmRepository.relationships(author.individualId).associateBy { it.otherId }
        val listenerId = continuing?.participantIds?.getOrNull(1) ?:
            FarmBehaviorPolicy.partner(states.getValue(author.individualId),available.map { states.getValue(it.individualId) },
                profiles.getValue(author.individualId),relationships,farm.randomSeed,now)?.individualId ?: return null
        val listener = available.firstOrNull { it.individualId == listenerId } ?: return null
        val distance = hypot((listener.positionX - author.positionX).toDouble(),
            (listener.positionY - author.positionY).toDouble())
        if (distance > TALK_PROXIMITY_PX) {
            farmRepository.steerToward(farmId, author.individualId, listener.positionX, listener.positionY)
            return null
        }
        val relation = relationships[listener.individualId]
        val planned = WorldSocialPlanner.choosePlayer(farm.randomSeed, now / 1_500, listOf(
            SocialCandidate(author.individualId, profiles.getValue(author.individualId),
                trust = relation?.affinity ?: 50, familiarity = relation?.familiarity ?: 0)), false)
            ?: SocialEncounterDecision(author.individualId,listener.individualId,SocialMotive.OBSERVATION,0)
        val listenerProfile = profiles.getValue(listener.individualId)
        val declines = planned.motive == SocialMotive.SHARED_ACTIVITY &&
            SocialRandom.unit(farm.randomSeed,listener.individualId,"farm-receptivity",now / 20_000) > listenerProfile.curiosity
        val choice = planned.copy(initiatorId = author.individualId, targetId = listener.individualId,
            motive = if (planned.hostile || planned.motive == SocialMotive.CHALLENGE_SPARRING) SocialMotive.SHARED_ACTIVITY else planned.motive,
            listenerDeclines = declines, listenerSpeaks = declines ||
                SocialRandom.unit(farm.randomSeed,listener.individualId,"farm-response",now / 20_000) < .4 + listenerProfile.initiative * .6)
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
            "${author.displayName} is near ${listener.displayName}. Actual motive: ${choice.description()}. " +
                "Address ${listener.displayName}; current invitation receptivity=${!choice.listenerDeclines}.",
            listener.individualId
        )
        val rawReply = generate(farmId,author.characterId,author.individualId,context,choice,token) ?: return null
        if (!membersStillPresent(farmId,token,setOf(author.individualId,listener.individualId))) return null
        lastAutonomousByFarm[farmId] = now
        lastInitiationByIndividual[author.individualId] = now
        val validated = FarmUtteranceValidator.fallback(rawReply, listOf(listener.individualId), residents.map { it.individualId }.toSet())
            ?: return null
        val firstMessage = farmRepository.postMessage(
            farmId = farmId,
            authorIndividualId = author.individualId,
            authorName = author.displayName,
            body = validated.speech,
            recipientIds = validated.recipientIds,
            sessionId = session.sessionId,
            publicationAllowed = { lifetime.isCurrent(farmId,token) }
        )
        farmRepository.rememberConversation(
            author.individualId,
            listener.individualId,
            firstMessage.id,
            "Spoke with ${listener.displayName}: ${validated.speech}"
        )
        sessions[farmId] = session.copy(turnCount = session.turnCount + 1)
        if (choice.listenerSpeaks) {
            val responseContext = buildContext(
                farmId,
                listener.individualId,
                listener.displayName,
                "${author.displayName} just said to you: ${validated.speech}. " +
                    if (choice.listenerDeclines) "You chose to decline. Express that in your own way, without agreeing to participate."
                    else if (choice.motive == SocialMotive.SHARED_ACTIVITY) "You independently chose to accept the invitation. Express that in your voice; a nearby activity will start only if you are both still available."
                    else "Choose your own response, including disagreement or a concise natural ending.", author.individualId
            )
            val rawResponse = generate(farmId,listener.characterId,listener.individualId,responseContext,choice,token,responding = true)
            if (rawResponse != null) {
                if (!membersStillPresent(farmId,token,setOf(author.individualId,listener.individualId))) return firstMessage
                val validatedResponse = FarmUtteranceValidator.fallback(rawResponse, listOf(author.individualId), residents.map { it.individualId }.toSet())
                if (validatedResponse != null) {
                    val responseMessage = farmRepository.postMessage(
                        farmId = farmId,
                        authorIndividualId = listener.individualId,
                        authorName = listener.displayName,
                        body = validatedResponse.speech,
                        recipientIds = validatedResponse.recipientIds,
                        sessionId = session.sessionId,
                        publicationAllowed = { lifetime.isCurrent(farmId,token) }
                    )
                    farmRepository.rememberConversation(
                        listener.individualId,
                        author.individualId,
                        responseMessage.id,
                        "Replied to ${author.displayName}: ${validatedResponse.speech}"
                    )
                    sessions[farmId] = session.copy(turnCount = session.turnCount + 2)
                    val eventStimulus = if (choice.listenerDeclines) SocialStimulus.REFUSAL else if (choice.motive == SocialMotive.PLAYFUL_TEASING)
                        SocialStimulus.TEASING else if (choice.motive == SocialMotive.CHECK_IN) SocialStimulus.CARE else SocialStimulus.NEUTRAL
                    fun appraisal(id: String, heard: String): Int {
                        val heardStimulus = SocialEventAppraisal.classify(heard)
                        val stimulus = if (heardStimulus == SocialStimulus.NEUTRAL) eventStimulus else heardStimulus
                        return if (stimulus == SocialStimulus.NEUTRAL) 1 else SocialEventAppraisal.delta(profiles.getValue(id),stimulus)
                    }
                    farmRepository.recordInteraction(author.individualId,listener.individualId,
                        appraisal(author.individualId,validatedResponse.speech),appraisal(listener.individualId,validated.speech))
                    if (choice.motive == SocialMotive.SHARED_ACTIVITY && !choice.listenerDeclines &&
                        SocialEventAppraisal.classify(validatedResponse.speech) != SocialStimulus.REFUSAL) {
                        val authorProfile = profiles.getValue(author.individualId)
                        val activity = if (authorProfile.training + listenerProfile.training > authorProfile.playfulness + listenerProfile.playfulness) "TRAIN" else "PLAY"
                        if (farmRepository.beginSharedActivity(farmId,author.individualId,listener.individualId,activity)) {
                            farmRepository.rememberConversation(author.individualId,listener.individualId,"activity:${responseMessage.id}",
                                "Accepted an invitation and started $activity together with ${listener.displayName}.")
                            farmRepository.rememberConversation(listener.individualId,author.individualId,"activity:${responseMessage.id}",
                                "Accepted an invitation and started $activity together with ${author.displayName}.")
                            sessions.remove(farmId)
                        }
                    }
                    if (choice.listenerDeclines) sessions.remove(farmId)
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
        event: String,
        otherId: String? = null
    ): String {
        val recent = farmRepository.messagesSnapshot(farmId, 8)
            .filter { message -> message.audience == "ALL" || message.authorIndividualId == authorId || authorId in farmRepository.recipientIds(message.id) }
            .joinToString("\n") { "${it.authorNameSnapshot}: ${it.body}" }
        val memories = farmRepository.memories(authorId,otherId).joinToString("\n") { "- ${it.summary}" }
        val resident = farmRepository.residentStates(farmId).firstOrNull { it.individualId == authorId }
        val relation = otherId?.let { id -> farmRepository.relationships(authorId).firstOrNull { it.otherId == id } }
        return "You are $authorName. $event\n${farmRepository.socialProfile(authorId).instruction(PromptLocalization.currentLanguageTag())}\n" +
            "Current activity=${resident?.activity}; energy=${resident?.energy}; hunger satisfaction=${resident?.satiety}; " +
            "social satisfaction=${resident?.social}; fun=${resident?.funLevel}.\n" +
            "Relationship to recipient: affinity=${relation?.affinity ?: 50}, familiarity=${relation?.familiarity ?: 0}.\n" +
            "Relevant memories:\n$memories\nRecent authorized Digifarm conversation:\n$recent"
    }

    private suspend fun membersStillPresent(farmId: String, token: Long, ids: Set<String>): Boolean =
        lifetime.isCurrent(farmId,token) && farmRepository.getFarm(farmId)?.archivedAt == null &&
            farmRepository.residentStates(farmId).map { it.individualId }.toSet().containsAll(ids)

    private suspend fun generate(farmId: String, characterId: Long, individualId: String, context: String,
        choice: SocialEncounterDecision, token: Long, responding: Boolean = false): String? {
        val job = currentCoroutineContext()[Job]
        if (!lifetime.register(farmId,token,job)) return null
        return try {
            val available = chatRepository.publicDialogueAvailable()
            val raw = if (available && acquireCallSlot()) try { withTimeout(20_000) { chatRepository.generateFarmReply(characterId,context) } }
                catch (timeout: TimeoutCancellationException) { null }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { null } else null
            raw?.let { WorldDialogueCodec.visibleText(it, individualId) } ?:
                SocialOpenings.create(choice, farmRepository.socialProfile(individualId), PromptLocalization.currentLanguageTag(),
                    responding = responding).text
        } finally {
            lifetime.unregister(farmId,job)
        }
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
