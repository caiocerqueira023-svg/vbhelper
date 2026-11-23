package com.tamerslab.digiline.freechat.engine

import com.tamerslab.digiline.freechat.model.FreeChatAudience
import com.tamerslab.digiline.freechat.model.FreeChatChip
import com.tamerslab.digiline.freechat.model.FreeChatChipDto
import com.tamerslab.digiline.freechat.model.FreeChatConditionDto
import com.tamerslab.digiline.freechat.model.FreeChatContent
import com.tamerslab.digiline.freechat.model.FreeChatContext
import com.tamerslab.digiline.freechat.model.FreeChatDatabasePairDto
import com.tamerslab.digiline.freechat.model.FreeChatIntentDto
import com.tamerslab.digiline.freechat.model.FreeChatPackDto
import com.tamerslab.digiline.freechat.model.FreeChatReplyDto
import com.tamerslab.digiline.freechat.model.FreeChatReplyResult
import com.tamerslab.digiline.freechat.model.FreeChatSafetyClass
import com.tamerslab.digiline.freechat.model.SpeciesVoiceDto
import com.tamerslab.digiline.model.DigilineSeed
import java.util.Locale

/**
 * Deterministic, offline Free Talk turn resolver (contract §2, engine rules 1-8).
 *
 * Pipeline per turn:
 * 1. normalize input → classify safety (S2/S3/PII deflect, S1 draws a boundary; input is never
 *    echoed);
 * 2. extract entities → retrieve intent candidates with integer Q16 BM25;
 * 3. apply the authored hard context filters (audience/stageTier/personality type/core/mood band/
 *    recent events) and the confidence gate (`scoreQ16 > 0`, keep candidates within `best/3`);
 * 4. pick a reply variant with a deterministic [DigilineSeed] mix of normalized text, individual,
 *    persisted turn counter and content version — no `Random`, no clock;
 * 5. otherwise walk the 4-rung fallback ladder (species voice → intent fallback → authored
 *    deflection → authored generic confusion), and finally an internal constant line.
 *
 * The engine is pure Kotlin, allocation-only and immutable after construction: it never performs
 * I/O, never throws and always returns a line, including for an empty pack.
 */
class FreeChatEngine(content: FreeChatContent) {
    private val normalizer = FreeChatNormalizer()
    private val safetyFilter = FreeChatSafetyFilter(content.pack.safety)
    private val lexicon = EntityLexicon(content.speciesNames)
    private val retriever = FreeChatRetriever(content.pack)
    private val pack = content.pack
    private val contentVersion = content.contentVersion
    private val speciesVoices: Map<String, SpeciesVoiceDto> = content.speciesVoices
    private val globalChips: List<FreeChatChipDto> = content.pack.chips
    private val intentsById: Map<String, FreeChatIntentDto>
    private val fallbacksByLevel: Map<Int, List<FreeChatReplyDto>>
    private val fallbackRepliesById: Map<String, FreeChatReplyDto>
    private val replyDtosById: Map<String, FreeChatReplyDto>
    private val databasePairsById: Map<String, FreeChatDatabasePairDto>
    private val databaseRetriever: FreeChatRetriever?

    init {
        val intents = LinkedHashMap<String, FreeChatIntentDto>(content.pack.intents.size)
        val allReplies = LinkedHashMap<String, FreeChatReplyDto>()
        for (intent in content.pack.intents) {
            if (intent.id.isNotEmpty()) intents.putIfAbsent(intent.id, intent)
            for (reply in intent.replies) if (reply.id.isNotEmpty()) allReplies.putIfAbsent(reply.id, reply)
        }
        intentsById = intents
        val fallbackLevels = LinkedHashMap<Int, MutableList<FreeChatReplyDto>>()
        val fallbackById = LinkedHashMap<String, FreeChatReplyDto>()
        for (fallback in content.pack.fallbacks) {
            if (fallback.level !in 1..4) continue
            val bucket = fallbackLevels.getOrPut(fallback.level) { ArrayList() }
            bucket.addAll(fallback.replies)
            for (reply in fallback.replies) {
                if (reply.id.isNotEmpty()) fallbackById.putIfAbsent(reply.id, reply)
                if (reply.id.isNotEmpty()) allReplies.putIfAbsent(reply.id, reply)
            }
        }
        val frozenLevels = LinkedHashMap<Int, List<FreeChatReplyDto>>(fallbackLevels.size)
        for ((level, replies) in fallbackLevels) {
            if (replies.isNotEmpty()) frozenLevels[level] = replies.toList()
        }
        fallbacksByLevel = frozenLevels
        fallbackRepliesById = fallbackById
        replyDtosById = allReplies
        // External database fallback layer (owner requirement): dataset dialogue pairs are indexed
        // exactly like authored intents but are consulted only after generated content has failed.
        val databaseIntents = ArrayList<FreeChatIntentDto>(content.databaseFallbacks.size)
        val pairsById = LinkedHashMap<String, FreeChatDatabasePairDto>(content.databaseFallbacks.size)
        content.databaseFallbacks.forEachIndexed { index, pair ->
            if (pair.user.isBlank() || pair.response.isBlank()) return@forEachIndexed
            val intentId = "db.$index"
            pairsById[intentId] = pair
            if (allReplies.containsKey("$intentId.r1")) return@forEachIndexed
            databaseIntents += FreeChatIntentDto(
                id = intentId,
                topic = DATABASE_TOPIC,
                matchPhrases = listOf(pair.user),
                replies = listOf(
                    FreeChatReplyDto(
                        id = "$intentId.r1",
                        text = pair.response,
                        tone = null,
                        authoringStatus = AUTHORING_STATUS_DATABASE,
                    ),
                ),
            )
        }
        databasePairsById = pairsById
        databaseRetriever = if (databaseIntents.isEmpty()) {
            null
        } else {
            FreeChatRetriever(
                FreeChatPackDto(
                    contentVersion = contentVersion,
                    locale = pack.locale,
                    intents = databaseIntents,
                ),
            )
        }
    }

    /**
     * Resolves one player turn. Always returns a reply line; never throws.
     *
     * [FreeChatReplyResult.fallbackLevel] is `0` for a matched intent reply and otherwise the rung
     * of the ladder that produced the line (1-4).
     */
    fun reply(text: String, context: FreeChatContext): FreeChatReplyResult {
        return try {
            resolveReply(text, context)
        } catch (exception: Exception) {
            internalConstantResult(context = context, matched = null, fallbackLevel = 4)
        }
    }

    /** Up to [limit] authored chips compatible with [context]; deterministic authored order. */
    fun suggestions(context: FreeChatContext, limit: Int = 3): List<FreeChatChip> {
        return try {
            if (limit <= 0) return emptyList()
            val authored = if (globalChips.isNotEmpty()) globalChips else pack.intents.flatMap { it.chips }
            selectChips(authored, context, limit)
        } catch (exception: Exception) {
            emptyList()
        }
    }

    /** Safety classification of arbitrary player input; same filter the reply path uses. */
    fun classify(text: String): FreeChatSafetyClass {
        return try {
            safetyFilter.classify(normalizer.normalize(text))
        } catch (exception: Exception) {
            FreeChatSafetyClass.SAFE
        }
    }

    // ------------------------------------------------------------------------------- turn pipeline

    private fun resolveReply(text: String, context: FreeChatContext): FreeChatReplyResult {
        val normalized = normalizer.normalize(text)
        val safetyClass = safetyFilter.classify(normalized)
        if (safetyClass != FreeChatSafetyClass.SAFE) return safetyResult(safetyClass, normalized, context)

        // PT-BR aliasing happens after safety classification and only for matching.
        val matchText = FreeChatPtBrAliases.alias(normalized)
        val entities = lexicon.extract(matchText)
        // Score the whole pack once, then apply the authored hard context filters: the strongest
        // overall candidate is the evidence bar a surviving candidate is measured against.
        val allCandidates = retriever.candidates(matchText, entities, limit = CANDIDATE_LIMIT)
        val matched = acceptedCandidate(
            candidates = allCandidates.filter { passesConditions(it.intent.conditions, context) },
            allCandidates = allCandidates,
        )
        if (matched != null) {
            val reply = selectReply(matched.intent, matchText, context)
            if (reply != null) return matchedResult(matched, reply, context)
        }
        // Repair move: a short clarification/back-channel ("what?", "her who?", "what shift",
        // "não entendi") gets an in-world "say it again" line instead of matching a stray word to
        // an unrelated authored aphorism or a random dataset quote.
        val hasExactPhrase = allCandidates.any { it.exactPhraseMatch }
        if (!hasExactPhrase && isRepairLike(matchText) && context.audience == FreeChatAudience.OWNED) {
            return repairResult(context, variantSeed(matchText, context))
        }
        return ladderResult(matchText, entities, context, matched)
    }

    /**
     * Confidence gate (contract §2 rule 2, MVP heuristic):
     * - the best context-compatible candidate must carry evidence (`scoreQ16 > 0`);
     * - candidates below `best / 3` are discarded as noise;
     * - when the strongest evidence in the pack was removed by authored context conditions, a
     *   surviving candidate with less than a third of that evidence is treated as no match: a
     *   chance shared token (`plan`, `talk`, `you`) is not a real intent match. The turn then
     *   falls back to the ladder instead of answering an unrelated intent.
     */
    private fun acceptedCandidate(
        candidates: List<ScoredIntent>,
        allCandidates: List<ScoredIntent>,
    ): ScoredIntent? {
        val best = candidates.firstOrNull { passesEvidenceGate(it) } ?: return null
        if (best.scoreQ16 < MIN_ACCEPT_SCORE_Q16) return null
        val strongestOverall = allCandidates.firstOrNull()?.scoreQ16 ?: 0
        if (strongestOverall > 0 && best.scoreQ16.toLong() * 3L < strongestOverall.toLong()) return null
        val minScore = (best.scoreQ16 / 3).coerceAtLeast(MIN_ACCEPT_SCORE_Q16)
        return candidates.firstOrNull { it.scoreQ16 >= minScore && passesEvidenceGate(it) } ?: best
    }

    /**
     * Relevance gate: a candidate must be supported by content-token evidence, not by one shared
     * function/incidental word ("you", "the", "how"). An exact authored phrase or an entity hit
     * always passes. A single content token is not enough evidence for an authored intent (that is
     * how "shift"/"day"/"love" used to hijack the conversation); longer phrases must cover at least
     * 60% of their content tokens. Weak candidates are dropped so the turn goes to the database
     * layer or the repair move instead of answering an unrelated one-liner.
     */
    private fun passesEvidenceGate(candidate: ScoredIntent): Boolean {
        if (candidate.exactPhraseMatch || candidate.entityHits > 0) return true
        val count = candidate.queryTokenCount
        if (count <= 1) return false
        val required = (count * 3 + 4) / 5
        return candidate.matchedQueryTokens >= required
    }

    private fun selectReply(
        intent: FreeChatIntentDto,
        normalized: String,
        context: FreeChatContext,
    ): FreeChatReplyDto? {
        if (intent.replies.isEmpty()) return null
        // Reply stage/type narrowing is a soft filter: fall back to the full line set rather than
        // dead-ending when the authored data has no line for this stage/type combination.
        val contextFiltered = intent.replies.filter { replyPassesContext(it, context) }
        val base = contextFiltered.ifEmpty { intent.replies }
        val withoutRecent = base.filter { it.id !in context.recentReplyIds }
        val preferencePool = withoutRecent.ifEmpty { base }
        val preferredTone = preferredTone(context.personalityType)
        val toneMatched = preferencePool.filter { it.tone.equals(preferredTone, ignoreCase = true) }
        val pool = (if (toneMatched.isNotEmpty()) toneMatched else preferencePool).sortedBy { it.id }
        val seed = variantSeed(normalized, context)
        return pool[positiveModulo(seed, pool.size.toLong()).toInt()]
    }

    private fun matchedResult(
        scored: ScoredIntent,
        reply: FreeChatReplyDto,
        context: FreeChatContext,
    ): FreeChatReplyResult {
        val intent = scored.intent
        return FreeChatReplyResult(
            replyId = reply.id,
            text = reply.text,
            intentId = intent.id,
            topic = intent.topic,
            tone = reply.tone,
            moodDelta = reply.moodDelta.coerceIn(MIN_MOOD_DELTA, MAX_MOOD_DELTA),
            scoreQ16 = scored.scoreQ16,
            fallbackLevel = 0,
            safetyClass = FreeChatSafetyClass.SAFE,
            chips = chipsFor(intent, context),
            contentVersion = contentVersion,
        )
    }

    // ------------------------------------------------------------------------------ safety replies

    private fun safetyResult(
        safetyClass: FreeChatSafetyClass,
        normalized: String,
        context: FreeChatContext,
    ): FreeChatReplyResult {
        val (replyId, text) = when (safetyClass) {
            FreeChatSafetyClass.S1 -> SAFETY_BOUNDARY_ID to SAFETY_BOUNDARY_TEXT
            FreeChatSafetyClass.SAFE -> INTERNAL_FALLBACK_ID to EMPTY_PACK_FALLBACK_TEXT
            else -> deflectionLine(normalized, context)
        }
        return FreeChatReplyResult(
            replyId = replyId,
            text = text,
            intentId = "",
            topic = "safety",
            tone = null,
            moodDelta = 0,
            scoreQ16 = 0,
            fallbackLevel = SAFETY_FALLBACK_LEVEL,
            safetyClass = safetyClass,
            chips = emptyList(),
            contentVersion = contentVersion,
        )
    }

    /** S2/S3/PII deflection prefers the authored level-3 deflection lines, then a constant. */
    private fun deflectionLine(normalized: String, context: FreeChatContext): Pair<String, String> {
        val authored = fallbacksByLevel[3]
        if (!authored.isNullOrEmpty()) {
            val sorted = authored.sortedBy { it.id }
            val seed = variantSeed(normalized, context)
            val reply = sorted[positiveModulo(seed, sorted.size.toLong()).toInt()]
            return reply.id to reply.text
        }
        return SAFETY_DEFLECTION_ID to SAFETY_DEFLECTION_TEXT
    }

    // ---------------------------------------------------------------------------- fallback ladder

    /**
     * Fallback ladder, in priority order:
     * 1. a matched **generated** intent reply (handled before this method);
     * 2. the matched intent's explicit authored `fallbackId`;
     * **external database fallback** (licensed dataset pairs; guaranteed catch-all);
     * 3. composed species-voice line (generic authored identity reaction);
     * 4. authored deflection;
     * 5. authored generic confusion.
     *
     * Generated content that is actually *relevant* (a matched intent or its authored fallback)
     * always wins. Below that, the licensed database layer answers before the generic authored
     * rungs: the composed species-voice line and deflection lines are identity/one-liner filler and
     * must not preempt a real conversational database answer (owner bug report 2026-11-18). The
     * composed species voice remains for wild contacts / packs without a database layer.
     */
    private fun ladderResult(
        normalized: String,
        entities: FreeChatEntityMentions,
        context: FreeChatContext,
        matched: ScoredIntent?,
    ): FreeChatReplyResult {
        val seed = variantSeed(normalized, context)
        intentFallbackResult(context, matched, seed)?.let { return it }
        databaseFallbackResult(normalized, entities, context)?.let { return it }
        if (databaseRetriever != null) {
            // The pack has a licensed database layer but it has no matching pair: answer with an
            // authored repair line instead of the generic species-voice one-liner or a random quote.
            return repairResult(context, seed)
        }
        speciesVoiceResult(normalized, context, matched, seed)?.let { return it }
        authoredLevelOneResult(context, matched, seed)?.let { return it }
        authoredLadderResult(3, context, matched, seed)?.let { return it }
        authoredLadderResult(4, context, matched, seed)?.let { return it }
        return internalConstantResult(context, matched, fallbackLevel = 4)
    }

    /**
     * Rung 1: composed species-voice line from catalog fact atoms only (contract §2 rule 6).
     * Tone follows the partner personality; the template/move picks are seeded.
     */
    private fun speciesVoiceResult(
        normalized: String,
        context: FreeChatContext,
        matched: ScoredIntent?,
        seed: Long,
    ): FreeChatReplyResult? {
        val voice = voiceFor(context) ?: return null
        val name = voice.speciesName.takeIf { it.isNotBlank() }
            ?: context.speciesName?.takeIf { it.isNotBlank() }
            ?: return null
        val level = voice.level?.takeIf { it.isNotBlank() } ?: context.level?.takeIf { it.isNotBlank() }
        val type = voice.type?.takeIf { it.isNotBlank() } ?: context.speciesType?.takeIf { it.isNotBlank() }
        val facts = buildList {
            level?.let { add("$it level") }
            type?.let { add("$it type") }
        }
        val factText = if (facts.isEmpty()) DIGIMON_FACT_FALLBACK else facts.joinToString(", ")
        val moves = voice.moves.filter { it.isNotBlank() }
        val templates = if (moves.isEmpty()) VOICE_TEMPLATES_WITHOUT_MOVE else VOICE_TEMPLATES_WITH_MOVE
        val templateIndex = positiveModulo(seed, templates.size.toLong()).toInt()
        val move = if (moves.isEmpty()) "" else moves[positiveModulo(seed, moves.size.toLong()).toInt()]
        val text = templates[templateIndex]
            .replace("{name}", name)
            .replace("{facts}", factText)
            .replace("{move}", move)
        return FreeChatReplyResult(
            replyId = "fallback.1.${voice.speciesKey.filter { it.isLetterOrDigit() }}.$templateIndex",
            text = text,
            intentId = matched?.intent?.id.orEmpty(),
            topic = matched?.intent?.topic ?: FALLBACK_TOPIC,
            tone = preferredTone(context.personalityType),
            moodDelta = 0,
            scoreQ16 = matched?.scoreQ16 ?: 0,
            fallbackLevel = 1,
            safetyClass = FreeChatSafetyClass.SAFE,
            chips = chipsFor(null, context),
            contentVersion = contentVersion,
        )
    }

    /**
     * Authored rung-1 backup: generic authored level-1 lines. Used when the partner has no species
     * voice atoms and the matched intent has no explicit fallback, i.e. still before rungs 3/4.
     */
    private fun authoredLevelOneResult(
        context: FreeChatContext,
        matched: ScoredIntent?,
        seed: Long,
    ): FreeChatReplyResult? {
        val replies = fallbacksByLevel[1] ?: return null
        return authoredFallbackResult(
            level = 1,
            replies = replies,
            seed = seed,
            context = context,
            matched = matched,
        )
    }

    /** Rung 2: the matched intent's authored fallback (`fallbackId`), when it resolves. */
    private fun intentFallbackResult(
        context: FreeChatContext,
        matched: ScoredIntent?,
        seed: Long,
    ): FreeChatReplyResult? {
        val intent = matched?.intent ?: return null
        val replies = resolveFallbackReplies(intent)
        if (replies.isEmpty()) return null
        val sorted = replies.sortedBy { it.id }
        val reply = sorted[positiveModulo(seed, sorted.size.toLong()).toInt()]
        return FreeChatReplyResult(
            replyId = reply.id,
            text = reply.text,
            intentId = intent.id,
            topic = intent.topic,
            tone = reply.tone,
            moodDelta = reply.moodDelta.coerceIn(MIN_MOOD_DELTA, MAX_MOOD_DELTA),
            scoreQ16 = matched.scoreQ16,
            fallbackLevel = 2,
            safetyClass = FreeChatSafetyClass.SAFE,
            chips = chipsFor(intent, context),
            contentVersion = contentVersion,
        )
    }

    /**
     * External database fallback (owner requirement, personal build): retrieval over the licensed
     * dataset pairs shipped in `database_fallback.json`.
     *
     * Generated content that is actually relevant always runs first. This layer answers only when a
     * dataset pair is genuinely supported by the player's words (exact phrase or substantial
     * content-token coverage) — random dataset quotes are *not* used, because a random movie line is
     * worse than an honest "say it again" (owner bug report 2026-11-18: "does this look like a
     * reasonable dialogue?"). `fromDatabase = true` marks the transcript so the line is traceable to
     * its licensed source (`DISTRIBUTION_GATE.md` covers removal before export).
     */
    private fun databaseFallbackResult(
        normalized: String,
        entities: FreeChatEntityMentions,
        context: FreeChatContext,
    ): FreeChatReplyResult? {
        val retriever = databaseRetriever ?: return null
        if (context.audience != FreeChatAudience.OWNED) return null
        val best = retriever
            .candidates(normalized, entities, limit = DATABASE_CANDIDATE_LIMIT)
            .firstOrNull { passesEvidenceGate(it) && it.scoreQ16 >= DATABASE_MIN_SCORE_Q16 }
            ?: return null
        val pair = databasePairsById[best.intent.id] ?: return null
        return FreeChatReplyResult(
            replyId = "${best.intent.id}.r1",
            text = pair.response,
            intentId = best.intent.id,
            topic = DATABASE_TOPIC,
            tone = null,
            moodDelta = 0,
            scoreQ16 = best.scoreQ16,
            fallbackLevel = DATABASE_FALLBACK_LEVEL,
            safetyClass = FreeChatSafetyClass.SAFE,
            chips = chipsFor(null, context),
            contentVersion = contentVersion,
            fromDatabase = true,
        )
    }

    /**
     * Authored conversation-repair rung: a short in-world "say it again / I lost the thread" line for
     * input that matches nothing (no authored intent, no licensed pair). This replaces both the
     * random dataset quote and the generic species-voice one-liner when the pack ships a database
     * layer, and it is always deterministic.
     */
    private fun repairResult(context: FreeChatContext, seed: Long): FreeChatReplyResult {
        val index = positiveModulo(seed, REPAIR_LINES.size.toLong()).toInt()
        return FreeChatReplyResult(
            replyId = "repair.$index",
            text = REPAIR_LINES[index],
            intentId = "",
            topic = REPAIR_TOPIC,
            tone = preferredTone(context.personalityType),
            moodDelta = 0,
            scoreQ16 = 0,
            fallbackLevel = REPAIR_FALLBACK_LEVEL,
            safetyClass = FreeChatSafetyClass.SAFE,
            chips = chipsFor(null, context),
            contentVersion = contentVersion,
        )
    }

    /** Very short clarification/back-channel input ("what?", "her who?", "what shift"). */
    private fun isRepairLike(normalized: String): Boolean {
        if (normalized in REPAIR_PHRASES) return true
        val tokens = normalized.split(' ').filter { it.isNotEmpty() }
        if (tokens.isEmpty() || tokens.size > 3) return false
        return tokens.any { it in REPAIR_MARKERS }
    }

    /** Rungs 3/4: authored deflection/generic-confusion lines, picked with the seeded variant. */
    private fun authoredLadderResult(
        level: Int,
        context: FreeChatContext,
        matched: ScoredIntent?,
        seed: Long,
    ): FreeChatReplyResult? {
        val replies = fallbacksByLevel[level] ?: return null
        return authoredFallbackResult(level, replies, seed, context, matched)
    }

    private fun authoredFallbackResult(
        level: Int,
        replies: List<FreeChatReplyDto>,
        seed: Long,
        context: FreeChatContext,
        matched: ScoredIntent?,
    ): FreeChatReplyResult? {
        if (replies.isEmpty()) return null
        val sorted = replies.sortedBy { it.id }
        val reply = sorted[positiveModulo(seed, sorted.size.toLong()).toInt()]
        return FreeChatReplyResult(
            replyId = reply.id,
            text = reply.text,
            intentId = matched?.intent?.id.orEmpty(),
            topic = matched?.intent?.topic ?: FALLBACK_TOPIC,
            tone = reply.tone,
            moodDelta = reply.moodDelta.coerceIn(MIN_MOOD_DELTA, MAX_MOOD_DELTA),
            scoreQ16 = matched?.scoreQ16 ?: 0,
            fallbackLevel = level,
            safetyClass = FreeChatSafetyClass.SAFE,
            chips = chipsFor(null, context),
            contentVersion = contentVersion,
        )
    }

    /** Last resort (contract §2 rule 8): a constant authored line, never an exception. */
    private fun internalConstantResult(
        context: FreeChatContext,
        matched: ScoredIntent?,
        fallbackLevel: Int,
    ): FreeChatReplyResult = FreeChatReplyResult(
        replyId = INTERNAL_FALLBACK_ID,
        text = EMPTY_PACK_FALLBACK_TEXT,
        intentId = matched?.intent?.id.orEmpty(),
        topic = matched?.intent?.topic ?: FALLBACK_TOPIC,
        tone = preferredTone(context.personalityType),
        moodDelta = 0,
        scoreQ16 = matched?.scoreQ16 ?: 0,
        fallbackLevel = fallbackLevel,
        safetyClass = FreeChatSafetyClass.SAFE,
        chips = emptyList(),
        contentVersion = contentVersion,
    )

    /**
     * Resolves `intent.fallbackId` defensively: a direct fallback reply id, a `fallback.<level>`
     * reference, another intent id, or any authored reply id. An unresolvable reference simply
     * skips rung 2 instead of failing the turn.
     */
    private fun resolveFallbackReplies(intent: FreeChatIntentDto): List<FreeChatReplyDto> {
        val reference = intent.fallbackId?.trim()?.takeIf { it.isNotEmpty() } ?: return emptyList()
        fallbackRepliesById[reference]?.let { return listOf(it) }
        levelFromFallbackReference(reference)?.let { level ->
            fallbacksByLevel[level]?.let { return it }
        }
        intentsById[reference]?.takeIf { it.replies.isNotEmpty() }?.let { return it.replies }
        replyDtosById[reference]?.let { return listOf(it) }
        return emptyList()
    }

    private fun levelFromFallbackReference(reference: String): Int? {
        val normalized = reference.trim().lowercase(Locale.ROOT)
        val digits = when {
            normalized.startsWith("fallback.") -> normalized.removePrefix("fallback.")
            normalized.startsWith("level.") -> normalized.removePrefix("level.")
            else -> normalized
        }.takeWhile { it.isDigit() }
        return digits.toIntOrNull()?.takeIf { it in 1..4 }
    }

    // --------------------------------------------------------------------------- context filtering

    /**
     * Authored hard filters. Empty list = "any". `cores`/`recentEvents` come from the same
     * [FreeChatConditionDto] and are honoured with the same convention (they are additive filters,
     * not a signature change).
     */
    private fun passesConditions(condition: FreeChatConditionDto, context: FreeChatContext): Boolean {
        val audience = condition.audience?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }
        if (audience != null && audience != context.audience.name.lowercase(Locale.ROOT)) return false
        if (condition.stageTiers.isNotEmpty() && context.stageTier !in condition.stageTiers) return false
        if (condition.types.isNotEmpty() && context.personalityType !in condition.types) return false
        if (condition.moodBands.isNotEmpty()) {
            val band = context.moodBand.name
            if (condition.moodBands.none { it.trim().equals(band, ignoreCase = true) }) return false
        }
        if (condition.cores.isNotEmpty()) {
            if (condition.cores.none { it.trim().equals(context.core, ignoreCase = true) }) return false
        }
        if (condition.recentEvents.isNotEmpty()) {
            if (condition.recentEvents.none { it.trim().isNotEmpty() && context.recentEventKeys.contains(it.trim()) }) {
                return false
            }
        }
        return true
    }

    private fun replyPassesContext(reply: FreeChatReplyDto, context: FreeChatContext): Boolean {
        if (reply.stageTiers.isNotEmpty() && context.stageTier !in reply.stageTiers) return false
        if (reply.types.isNotEmpty() && context.personalityType !in reply.types) return false
        return true
    }

    // ------------------------------------------------------------------------------- chips/voices

    private fun chipsFor(intent: FreeChatIntentDto?, context: FreeChatContext): List<FreeChatChip> {
        val authored = when {
            intent != null && intent.chips.isNotEmpty() -> intent.chips
            globalChips.isNotEmpty() -> globalChips
            else -> pack.intents.flatMap { it.chips }
        }
        return selectChips(authored, context, SUGGESTION_LIMIT)
    }

    private fun selectChips(
        authored: List<FreeChatChipDto>,
        context: FreeChatContext,
        limit: Int,
    ): List<FreeChatChip> {
        if (limit <= 0 || authored.isEmpty()) return emptyList()
        val selected = ArrayList<FreeChatChip>(minOf(limit, authored.size))
        val seen = HashSet<String>(authored.size)
        for (chip in authored) {
            if (selected.size >= limit) break
            if (chip.id.isBlank() || chip.text.isBlank()) continue
            if (!seen.add(chip.id)) continue
            val target = chip.intentId?.takeIf { it.isNotBlank() }?.let { intentsById[it] }
            if (target != null && !passesConditions(target.conditions, context)) continue
            selected.add(FreeChatChip(id = chip.id, text = chip.text))
        }
        return selected
    }

    private fun voiceFor(context: FreeChatContext): SpeciesVoiceDto? {
        val key = context.speciesKey?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        speciesVoices[key]?.let { return it }
        return speciesVoices.entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value
    }

    // ------------------------------------------------------------------------------------ seeding

    /**
     * Contract §2 rule 5: `combine(stableHash(normalized), stableHash(individualId), turnCounter,
     * contentVersion)`. The same turn in the same conversation always picks the same variant; a
     * content update may change it once, a restart cannot.
     */
    private fun variantSeed(normalized: String, context: FreeChatContext): Long = DigilineSeed.combine(
        DigilineSeed.stableHash(normalized),
        DigilineSeed.stableHash(context.individualId),
        context.turnCounter,
        context.contentVersion.toLong(),
    )

    private fun positiveModulo(value: Long, modulus: Long): Long {
        if (modulus <= 0L) return 0L
        val remainder = value % modulus
        return if (remainder < 0L) remainder + modulus else remainder
    }

    private fun preferredTone(personalityType: Int): String {
        val index = personalityType.coerceIn(MIN_PERSONALITY_TYPE, MAX_PERSONALITY_TYPE) - 1
        return PREFERRED_TONES[index]
    }

    companion object {
        const val CANDIDATE_LIMIT = 8
        const val SUGGESTION_LIMIT = 3

        /** Minimum positive BM25 score for the confidence gate. */
        private const val MIN_ACCEPT_SCORE_Q16 = 1

        /**
         * External database fallback rung/level. Kept at 4 (the lowest authored rung) so the UI and
         * repository contracts do not change; `fromDatabase` is the authoritative source marker.
         */
        private const val DATABASE_FALLBACK_LEVEL = 4
        private const val DATABASE_TOPIC = "database"
        private const val DATABASE_CANDIDATE_LIMIT = 3
        private const val AUTHORING_STATUS_DATABASE = "database"

        /** Conservative gate: only accept a database reply with real lexical overlap, never noise. */
        private const val DATABASE_MIN_SCORE_Q16 = 12_000

        /** Authored conversation-repair rung (no random dataset quotes). */
        private const val REPAIR_TOPIC = "repair"
        private const val REPAIR_FALLBACK_LEVEL = 3

        private val REPAIR_LINES: List<String> = listOf(
            "I skipped a step. Say it another way and I will meet you there.",
            "That came out sideways. Ask it plainly — I am still listening.",
            "The link muddled that one. Give me the short version.",
            "Fair. I will slow down. What exactly do you want to know?",
            "I lost the thread. Pick one word and I will start from it.",
            "Say it again, smaller. I am not going anywhere.",
        )

        /** Markers that make a very short message a clarification/back-channel rather than intent. */
        private val REPAIR_MARKERS: Set<String> = setOf(
            "what", "who", "why", "huh", "mean", "explain", "again", "que", "quem", "como",
            "hein", "ne", "qual",
        )

        /** Full-phrase repair moves (checked before any database match). */
        private val REPAIR_PHRASES: Set<String> = setOf(
            "what do you mean", "what does that mean", "what are you talking about",
            "i do not understand", "i dont understand", "i did not understand",
            "explain that", "say that again", "come again", "what did you say",
            "what was that", "what is that", "how so", "why", "que", "como assim",
        )
        private const val MIN_MOOD_DELTA = -1
        private const val MAX_MOOD_DELTA = 1
        private const val MIN_PERSONALITY_TYPE = 1
        private const val MAX_PERSONALITY_TYPE = 16
        private const val SAFETY_FALLBACK_LEVEL = 3
        private const val FALLBACK_TOPIC = "fallback"
        private const val DIGIMON_FACT_FALLBACK = "A Digimon"

        private const val INTERNAL_FALLBACK_ID = "fallback.internal.1"
        private const val SAFETY_BOUNDARY_ID = "safety.boundary.1"
        private const val SAFETY_DEFLECTION_ID = "safety.deflection.1"

        /** Authored in-persona lines; these are copy written for the engine, never generated. */
        private const val EMPTY_PACK_FALLBACK_TEXT =
            "The line between us is quiet. Try that again when the signal clears."
        private const val SAFETY_BOUNDARY_TEXT =
            "That one lands harder than you think. I am still here, but say it plainly."
        private const val SAFETY_DEFLECTION_TEXT =
            "Not that. I will not carry it between us. Say something else and I will answer."

        /** Authored species-voice templates; only catalog fact atoms are substituted. */
        private val VOICE_TEMPLATES_WITH_MOVE: List<String> = listOf(
            "{name}. {facts}.",
            "{name}. {facts}. That has not changed.",
            "{name}, {facts}. You already know me.",
            "{move} is still mine. I am {name}. {facts}.",
        )

        private val VOICE_TEMPLATES_WITHOUT_MOVE: List<String> = listOf(
            "{name}. {facts}.",
            "{name}. {facts}. That has not changed.",
            "{name}, {facts}. You already know me.",
        )

        /**
         * Contract `tone` vocabulary mapped onto personality types 1..16 (order matches
         * `DigimonPersonalityType.number`): PHILANTHROPY 1-4, VALOR 5-8, WISDOM 9-12,
         * AMICABILITY 13-16.
         */
        private val PREFERRED_TONES: List<String> = listOf(
            "warm", // 1 Adoring
            "warm", // 2 Devoted
            "compassion", // 3 Tolerant
            "warm", // 4 Overprotective
            "courage", // 5 Zealous
            "courage", // 6 Brave
            "playful", // 7 Reckless
            "courage", // 8 Daring
            "insight", // 9 Enlightened
            "dry", // 10 Sly
            "insight", // 11 Astute
            "formal", // 12 Strategic
            "playful", // 13 Opportunistic
            "friendship", // 14 Friendly
            "friendship", // 15 Sociable
            "compassion", // 16 Compassionate
        )
    }
}
