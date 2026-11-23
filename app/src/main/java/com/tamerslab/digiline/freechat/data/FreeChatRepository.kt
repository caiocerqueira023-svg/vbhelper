package com.tamerslab.digiline.freechat.data

import androidx.room.withTransaction
import com.tamerslab.data.database.AppDatabase
import com.tamerslab.digiline.freechat.content.FreeChatContentProvider
import com.tamerslab.digiline.freechat.content.FreeChatJson
import com.tamerslab.digiline.freechat.engine.FreeChatEngine
import com.tamerslab.digiline.freechat.engine.FreeChatNormalizer
import com.tamerslab.digiline.freechat.gateway.FreeChatResult
import com.tamerslab.digiline.freechat.gateway.FreeChatSceneView
import com.tamerslab.digiline.freechat.model.FreeChatChip
import com.tamerslab.digiline.freechat.model.FreeChatContent
import com.tamerslab.digiline.freechat.model.FreeChatContext
import com.tamerslab.digiline.freechat.model.FreeChatIntentDto
import com.tamerslab.digiline.freechat.model.FreeChatReplyDto
import com.tamerslab.digiline.freechat.model.FreeChatSafetyClass
import com.tamerslab.digiline.model.DigilineSeed
import com.tamerslab.domain.chat.ChatMessageEntity
import kotlinx.serialization.Serializable

/**
 * Free Talk ("Papo") orchestration + persistence (contract §3).
 *
 * Locked rules implemented here:
 * - **Persistence before display**: the user row, the authored assistant row, the [FreeChatTurnEntity]
 *   decision and the [FreeChatProfileEntity] counters/credit are written in one Room transaction
 *   before the scene is returned. A crash or restart therefore replays the persisted turn instead of
 *   rerolling it (turn counter + variant seed + reply id are recorded).
 * - **Deterministic greeting**: one greeting per 12 h window, picked from authored `topic ==
 *   "greeting"` intents with [DigilineSeed]; chips come from the authored pack (engine suggestions,
 *   then global chips) capped at 3.
 * - **Input clamp**: messages are trimmed and clamped to 160 chars before safety/reply.
 * - **Mood is only changed by the conservative session credit**: a single +1 once per day when the
 *   current session reaches 4 messages and the daily credit budget is untouched. Authored
 *   `moodDelta` values never move the bond stat, and no personality/shift/trust table is touched.
 * - **Safety**: S1 gets an authored boundary line (no close); S2/S3/PII get an authored deflection
 *   plus `closeReason = "SAFETY"` (session ends; no lockout, no contact deletion).
 * - **Clear**: deletes only this feature's turns/profile/`freechat_*` transcript rows.
 *
 * All content/reply state comes from the process-wide [FreeChatContentProvider]; any missing pack or
 * disabled switch fails open with an error result and the feature stays hidden.
 */
class FreeChatRepository(
    private val database: AppDatabase,
    private val content: FreeChatContentProvider,
    private val assembler: FreeChatContextAssembler,
    private val now: () -> Long = System::currentTimeMillis,
    private val contentEnabled: () -> Boolean = { true },
) {

    private val dao get() = database.freeChatDao()
    private val normalizer = FreeChatNormalizer()

    @Volatile
    private var engineCache: Pair<FreeChatContent, FreeChatEngine>? = null

    /**
     * Synchronous composer gate. Content is loaded lazily on the first [open]; the feature is
     * considered available until a load has actually failed (fail-open, contract §3/§5).
     */
    fun isAvailable(): Boolean = contentEnabled() && content.isPotentiallyAvailable()

    /** Opens the thread: persists the 12 h greeting (when due) and returns the chips to show. */
    suspend fun open(characterId: Long): FreeChatResult {
        if (!contentEnabled()) return unavailable()
        val individualId = resolveIndividualId(characterId)
            ?: return FreeChatResult(error = ERROR_NO_PARTNER)
        val loaded = content.get() ?: return unavailable()
        val context = assembler.assemble(individualId, loaded.contentVersion)
            ?: return FreeChatResult(error = ERROR_NO_PARTNER)
        val engine = engineFor(loaded)
        val current = now()
        val profile = dao.profile(individualId)
            ?: FreeChatProfileEntity(individualId = individualId, contentVersion = loaded.contentVersion)
        val chips = suggestedChips(loaded, engine, context)
        if (current - profile.lastTalkAt < GREETING_WINDOW_MILLIS) {
            return FreeChatResult(scene = FreeChatSceneView(chips = chips))
        }
        val greeting = greetingIntent(loaded, context)
            ?: return FreeChatResult(scene = FreeChatSceneView(chips = chips))
        val reply = greetingReply(greeting, context)
            ?: return FreeChatResult(scene = FreeChatSceneView(chips = chips))
        val text = reply.text.trim()
        if (text.isEmpty()) return FreeChatResult(scene = FreeChatSceneView(chips = chips))
        val greetingChips = greeting.chips
            .map { FreeChatChip(id = it.id, text = it.text) }
            .ifEmpty { chips }
            .take(MAX_CHIPS)
        val payloadJson = encodePayload(
            intentId = greeting.id,
            replyId = reply.id,
            topic = greeting.topic,
            fallbackLevel = 0,
            safetyClass = FreeChatSafetyClass.SAFE.name,
        )
        database.withTransaction {
            database.chatDao().insertMessage(
                ChatMessageEntity(
                    individualId = individualId,
                    role = ROLE_ASSISTANT,
                    content = text,
                    timestamp = current,
                    isRead = true,
                    kind = KIND_ASSISTANT,
                    payloadJson = payloadJson,
                )
            )
            dao.upsertProfile(
                profile.copy(
                    lastTalkAt = current,
                    contentVersion = loaded.contentVersion,
                )
            )
        }
        return FreeChatResult(
            scene = FreeChatSceneView(
                lines = listOf(text),
                chips = greetingChips,
                partnerTone = reply.tone,
            )
        )
    }

    /** One free-text turn: classify, reply, persist (transcript + turn + counters), return scene. */
    suspend fun send(characterId: Long, text: String): FreeChatResult {
        if (!contentEnabled()) return unavailable()
        val individualId = resolveIndividualId(characterId)
            ?: return FreeChatResult(error = ERROR_NO_PARTNER)
        val loaded = content.get() ?: return unavailable()
        val message = text.trim().take(MAX_INPUT_CHARS)
        if (message.isEmpty()) return FreeChatResult(error = ERROR_EMPTY_MESSAGE)
        val baseContext = assembler.assemble(individualId, loaded.contentVersion)
            ?: return FreeChatResult(error = ERROR_NO_PARTNER)
        val profile = dao.profile(individualId)
            ?: FreeChatProfileEntity(individualId = individualId, contentVersion = loaded.contentVersion)
        val turnCounter = profile.turnCounter + 1
        val context = baseContext.copy(turnCounter = turnCounter)
        val normalized = normalizer.normalize(message)
        val variantSeed = DigilineSeed.combine(
            DigilineSeed.stableHash(normalized),
            DigilineSeed.stableHash(individualId),
            turnCounter,
            loaded.contentVersion.toLong(),
        )
        val engine = engineFor(loaded)
        val reply = runCatching { engine.reply(message, context) }.getOrElse { return unavailable() }
        val replyText = reply.text.trim()
        if (replyText.isEmpty()) return unavailable()
        val closing = reply.safetyClass in CLOSING_SAFETY_CLASSES
        // Privacy: S2/S3/PII turns never persist the player's raw text (plan §10.2), only a marker;
        // neither hash field is stored either because short PII (e.g. a phone number) is guessable.
        // `variantSeed` mixes the normalized text into a decision seed, so it is equally
        // brute-forceable and must be sentinel for the same turns.
        val persistedMessage = if (closing) REDACTED_PLAYER_MESSAGE else message
        val persistedHash = if (closing) 0L else DigilineSeed.stableHash(normalized)
        val persistedVariantSeed = if (closing) 0L else variantSeed
        val chips = if (closing) {
            emptyList()
        } else {
            reply.chips
                .take(MAX_CHIPS)
                .ifEmpty { suggestedChips(loaded, engine, context) }
                .take(MAX_CHIPS)
        }
        val scene = FreeChatSceneView(
            lines = listOf(replyText),
            chips = chips,
            closeReason = if (closing) CLOSE_REASON_SAFETY else null,
            partnerTone = reply.tone,
        )
        val payloadJson = encodePayload(
            intentId = reply.intentId,
            replyId = reply.replyId,
            topic = reply.topic,
            fallbackLevel = reply.fallbackLevel,
            safetyClass = reply.safetyClass.name,
            fromDatabase = reply.fromDatabase,
        )
        val current = now()
        database.withTransaction {
            database.chatDao().insertMessage(
                ChatMessageEntity(
                    individualId = individualId,
                    role = ROLE_USER,
                    content = persistedMessage,
                    timestamp = current,
                    isRead = true,
                    kind = KIND_USER,
                )
            )
            database.chatDao().insertMessage(
                ChatMessageEntity(
                    individualId = individualId,
                    role = ROLE_ASSISTANT,
                    content = replyText,
                    timestamp = current,
                    isRead = true,
                    kind = KIND_ASSISTANT,
                    payloadJson = payloadJson,
                )
            )
            dao.insertTurn(
                FreeChatTurnEntity(
                    individualId = individualId,
                    turnCounter = turnCounter,
                    normalizedHash = persistedHash,
                    intentId = reply.intentId,
                    replyId = reply.replyId,
                    variantSeed = persistedVariantSeed,
                    scoreQ16 = reply.scoreQ16,
                    fallbackLevel = reply.fallbackLevel,
                    safetyClass = reply.safetyClass.name,
                    createdAt = current,
                )
            )
            val sessionCounter =
                if (current - profile.lastTalkAt >= SESSION_GAP_MILLIS) 1L else profile.sessionCounter + 1
            val today = DigilineSeed.dayIndex(current)
            val newDay = profile.lastMoodCreditDay != today
            val moodCreditToday = if (newDay) 0 else profile.moodCreditToday
            var creditApplied = false
            if (sessionCounter >= SESSION_MOOD_MIN_MESSAGES &&
                moodCreditToday < MAX_MOOD_CREDITS_PER_DAY &&
                newDay
            ) {
                creditApplied = applySessionMoodCredit(individualId)
            }
            dao.upsertProfile(
                profile.copy(
                    turnCounter = turnCounter,
                    lastTalkAt = current,
                    lastMoodCreditDay = if (creditApplied) today else profile.lastMoodCreditDay,
                    moodCreditToday = if (creditApplied) moodCreditToday + 1 else moodCreditToday,
                    sessionCounter = sessionCounter,
                    contentVersion = loaded.contentVersion,
                )
            )
        }
        return FreeChatResult(scene = scene)
    }

    /** Clears only this feature's conversation state for the character (never the contact). */
    suspend fun clear(characterId: Long) {
        val individualId = resolveIndividualId(characterId) ?: return
        database.withTransaction {
            dao.clearTurns(individualId)
            dao.clearProfile(individualId)
            dao.clearFreeChatMessages(individualId)
        }
    }

    // ------------------------------------------------------------------- content / chips

    private fun engineFor(loaded: FreeChatContent): FreeChatEngine {
        engineCache?.let { (cachedContent, cachedEngine) ->
            if (cachedContent === loaded) return cachedEngine
        }
        val created = FreeChatEngine(loaded)
        engineCache = loaded to created
        return created
    }

    /** Authored suggestions from the engine, then the global chip bank (max 3). */
    private fun suggestedChips(
        loaded: FreeChatContent,
        engine: FreeChatEngine,
        context: FreeChatContext,
    ): List<FreeChatChip> {
        val suggested = runCatching { engine.suggestions(context, MAX_CHIPS) }.getOrDefault(emptyList())
        if (suggested.isNotEmpty()) return suggested.take(MAX_CHIPS)
        return loaded.pack.chips.map { FreeChatChip(id = it.id, text = it.text) }.take(MAX_CHIPS)
    }

    // ----------------------------------------------------------------------- greeting

    private fun greetingIntent(loaded: FreeChatContent, context: FreeChatContext): FreeChatIntentDto? {
        val candidates = loaded.pack.intents.filter { intent ->
            intent.topic.equals(TOPIC_GREETING, ignoreCase = true) &&
                intent.replies.isNotEmpty() &&
                intentMatches(intent, context)
        }
        if (candidates.isEmpty()) return null
        val seed = DigilineSeed.combine(
            DigilineSeed.stableHash(context.individualId),
            context.turnCounter,
            context.contentVersion.toLong(),
        )
        return candidates[floorIndex(seed, candidates.size)]
    }

    private fun greetingReply(
        intent: FreeChatIntentDto,
        context: FreeChatContext,
    ): FreeChatReplyDto? {
        val eligible = intent.replies.filter { reply ->
            reply.text.isNotBlank() && replyMatches(reply, context)
        }
        val pool = eligible.ifEmpty { intent.replies.filter { it.text.isNotBlank() } }
        if (pool.isEmpty()) return null
        val seed = DigilineSeed.combine(
            DigilineSeed.stableHash(intent.id),
            DigilineSeed.stableHash(context.individualId),
            context.turnCounter,
        )
        return pool[floorIndex(seed, pool.size)]
    }

    /** Hard context filters of contract §2 rule 3 (audience / stage tier / type / mood band). */
    private fun intentMatches(intent: FreeChatIntentDto, context: FreeChatContext): Boolean {
        val conditions = intent.conditions
        val audience = conditions.audience
        if (!audience.isNullOrBlank() && !audience.equals(context.audience.name, ignoreCase = true)) {
            return false
        }
        if (conditions.stageTiers.isNotEmpty() && context.stageTier !in conditions.stageTiers) {
            return false
        }
        if (conditions.types.isNotEmpty() && context.personalityType !in conditions.types) {
            return false
        }
        if (conditions.moodBands.isNotEmpty()) {
            val bands = conditions.moodBands.map { it.trim().uppercase() }
            if (context.moodBand.name !in bands) return false
        }
        return true
    }

    /** Soft reply filters: empty = any (contract §2 rule 4). */
    private fun replyMatches(reply: FreeChatReplyDto, context: FreeChatContext): Boolean {
        if (reply.stageTiers.isNotEmpty() && context.stageTier !in reply.stageTiers) return false
        if (reply.types.isNotEmpty() && context.personalityType !in reply.types) return false
        return true
    }

    // -------------------------------------------------------------------- mood credit

    /**
     * Applies the single conservative session credit: +1 mood, floored/capped at 0..100.
     * Returns true only when the mood value actually changed, so a maxed bond never burns the
     * daily credit. Never writes personality/shift/trust tables.
     */
    private suspend fun applySessionMoodCredit(individualId: String): Boolean {
        val user = database.userCharacterDao().getByIndividualId(individualId).firstOrNull()
            ?: return false
        val newMood = (user.mood + SESSION_MOOD_CREDIT).coerceIn(MIN_MOOD, MAX_MOOD)
        if (newMood == user.mood) return false
        database.userCharacterDao().updateCharacter(user.copy(mood = newMood))
        return true
    }

    // -------------------------------------------------------------------------- helpers

    private suspend fun resolveIndividualId(characterId: Long): String? =
        runCatching { database.userCharacterDao().getCharacter(characterId).individualId }.getOrNull()

    private fun encodePayload(
        intentId: String,
        replyId: String,
        topic: String,
        fallbackLevel: Int,
        safetyClass: String,
        fromDatabase: Boolean = false,
    ): String = FreeChatJson.encodeToString(
        FreeChatPayload.serializer(),
        FreeChatPayload(
            intentId = intentId,
            replyId = replyId,
            topic = topic,
            fallbackLevel = fallbackLevel,
            safetyClass = safetyClass,
            fromDatabase = fromDatabase,
        ),
    )

    private fun unavailable(): FreeChatResult = FreeChatResult(error = ERROR_UNAVAILABLE)

    private fun floorIndex(seed: Long, size: Int): Int {
        if (size <= 0) return 0
        return ((seed % size) + size).toInt() % size
    }

    /** Persisted decision metadata for the assistant transcript row (contract §3 shape). */
    @Serializable
    private data class FreeChatPayload(
        val intentId: String,
        val replyId: String,
        val topic: String,
        val fallbackLevel: Int,
        val safetyClass: String,
        val fromDatabase: Boolean = false,
    )

    companion object {
        /** One greeting per 12 h window (contract §3). */
        const val GREETING_WINDOW_MILLIS: Long = 12L * 60L * 60L * 1000L

        /** A gap of this size starts a new conversation session (session mood credit scope). */
        const val SESSION_GAP_MILLIS: Long = 12L * 60L * 60L * 1000L

        /** Contract §3: messages are clamped to 160 characters. */
        const val MAX_INPUT_CHARS: Int = 160

        /** Contract §3: at most three chips are rendered above the composer. */
        const val MAX_CHIPS: Int = 3

        /** Contract §3: a session earns its single +1 after four messages. */
        const val SESSION_MOOD_MIN_MESSAGES: Long = 4

        /** Contract §3: daily credit budget (`moodCreditToday < 2`). */
        const val MAX_MOOD_CREDITS_PER_DAY: Int = 2

        /** Conservative relationship effect: only +1, never negative from free text. */
        const val SESSION_MOOD_CREDIT: Int = 1

        const val MIN_MOOD: Int = 0
        const val MAX_MOOD: Int = 100

        const val ROLE_USER: String = "user"
        const val ROLE_ASSISTANT: String = "assistant"
        const val KIND_USER: String = "freechat_user"
        const val KIND_ASSISTANT: String = "freechat_assistant"

        /** Gateway/UI close reason for a session ended by the safety filter. */
        const val CLOSE_REASON_SAFETY: String = "SAFETY"

        /** Transcript marker stored instead of raw text for S2/S3/PII turns (privacy, plan §10.2). */
        const val REDACTED_PLAYER_MESSAGE: String = "[message hidden by safety filter]"

        const val ERROR_UNAVAILABLE: String = "Free talk is unavailable right now."
        const val ERROR_NO_PARTNER: String = "This partner cannot talk right now."
        const val ERROR_EMPTY_MESSAGE: String = "Type something first."

        private const val TOPIC_GREETING = "greeting"

        private val CLOSING_SAFETY_CLASSES = setOf(
            FreeChatSafetyClass.S2,
            FreeChatSafetyClass.S3,
            FreeChatSafetyClass.PII,
        )
    }
}
