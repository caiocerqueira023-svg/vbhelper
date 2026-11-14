package com.tamerslab.digiline.model

import kotlin.math.abs
import kotlin.random.Random

/**
 * Parsed, read-only DigiLine content pack.
 *
 * Owned by the model package (engines consume it); the content loader in
 * `com.tamerslab.digiline.content` parses the asset files and constructs exactly this class, so
 * there is a single source of truth for lookups. Every lookup is deterministic and never throws on
 * missing/empty content: it returns `null` / an empty list instead.
 *
 * Cards are sorted by id before any selection so the result does not depend on JSON object order.
 */
class DigilineContentPack(
    val manifest: ContentManifest = ContentManifest(
        schemaVersion = 1,
        contentVersion = 0,
        locale = "en",
        files = emptyMap(),
    ),
    val questions: List<QuestionCardDto> = emptyList(),
    val genericQuestions: List<QuestionCardDto> = emptyList(),
    val quizzes: List<QuizCardDto> = emptyList(),
    val negotiation: NegotiationPackDto = NegotiationPackDto(),
    val persuasion: PersuasionPackDto = PersuasionPackDto(),
    val eventLines: List<EventLineDto> = emptyList(),
    val lore: List<LoreFactDto> = emptyList(),
) {
    val contentVersion: Int get() = manifest.contentVersion

    val locale: String get() = manifest.locale

    val hasContent: Boolean
        get() = questions.isNotEmpty() ||
            genericQuestions.isNotEmpty() ||
            quizzes.isNotEmpty() ||
            negotiation.personalities.isNotEmpty() ||
            persuasion.personalities.isNotEmpty() ||
            eventLines.isNotEmpty()

    // ---------------------------------------------------------------- owned-Digimon questions

    /**
     * Deterministic species question lookup. [variant] is the per-individual stable variant
     * (plan R8); it is normalized modulo the available variants, so the same individual always
     * gets the same authored line. Returns `null` when the species has no question.
     */
    fun speciesQuestion(speciesKey: String, variant: Int): QuestionCardDto? {
        val key = normalizeSpeciesKey(speciesKey)
        if (key.isEmpty()) return null
        val matches = questions
            .filter { question ->
                normalizeSpeciesKey(question.speciesKey ?: question.speciesName ?: "") == key
            }
            .sortedBy { it.id }
        if (matches.isEmpty()) return null
        return matches[positiveMod(variant, matches.size)]
    }

    /** All species questions for [speciesKey], sorted by id (validator/debug helper). */
    fun speciesQuestions(speciesKey: String): List<QuestionCardDto> {
        val key = normalizeSpeciesKey(speciesKey)
        if (key.isEmpty()) return emptyList()
        return questions
            .filter { normalizeSpeciesKey(it.speciesKey ?: it.speciesName ?: "") == key }
            .sortedBy { it.id }
    }

    /**
     * Deterministic generic question for a personality type.
     *
     * Gating (plan §6.4): only cards whose `askerTypes` contains [typeNumber] may be asked. Two safe
     * fallbacks are allowed so a bad mapping cannot leave a Digimon silent: cards whose `core`
     * matches the type's core, then unassigned/neutral cards (`askerTypes` empty). A card for
     * another personality is never returned. [excludedIds] are already-asked ids; when the whole
     * pool is recent it is reused (repeat-safe) rather than returning `null`.
     */
    fun genericQuestion(
        typeNumber: Int,
        seed: Long,
        excludedIds: Set<String> = emptySet(),
    ): QuestionCardDto? {
        val type = typeNumber.coerceIn(1, 16)
        val ordered = genericQuestions.sortedBy { it.id }
        if (ordered.isEmpty()) return null
        val byAsker = ordered.filter { it.askerTypes.contains(type) }
        val coreName = CORE_NAMES[coreIndexOf(type)]
        val byCore = ordered.filter { question ->
            val core = question.core?.trim()
            !core.isNullOrEmpty() && core.equals(coreName, ignoreCase = true)
        }
        val neutral = ordered.filter { it.askerTypes.isEmpty() }
        val pool = byAsker.ifEmpty { byCore }.ifEmpty { neutral }
        if (pool.isEmpty()) return null
        val fresh = pool.filterNot { it.id in excludedIds }
        val available = fresh.ifEmpty { pool }
        return available[randomIndex(seed, available.size)]
    }

    // ------------------------------------------------------------------------------- quizzes

    /**
     * Deterministic quiz pick within the individual's stage tier ± 1 (plan §6.5).
     * [excludedIds] are the recently answered ids; when every candidate is excluded the full pool
     * is used instead of leaving the Digimon silent.
     */
    fun quiz(stageTier: Int, seed: Long, excludedIds: Set<String> = emptySet()): QuizCardDto? {
        if (quizzes.isEmpty()) return null
        val tier = stageTier.coerceIn(1, 4)
        val ordered = quizzes.sortedBy { it.id }
        val inTier = ordered.filter { abs(it.stageTier.coerceIn(1, 4) - tier) <= 1 }
        val candidates = inTier.ifEmpty { ordered }
        val fresh = candidates.filterNot { it.id in excludedIds }
        val pool = fresh.ifEmpty { ordered.filterNot { it.id in excludedIds }.ifEmpty { ordered } }
        return pool[randomIndex(seed, pool.size)]
    }

    fun quizById(id: String): QuizCardDto? = quizzes.firstOrNull { it.id == id }

    fun questionById(id: String): QuestionCardDto? =
        questions.firstOrNull { it.id == id } ?: genericQuestions.firstOrNull { it.id == id }

    // --------------------------------------------------------------------- wild dialogue flows

    /** Negotiation flow for a personality type (1..16) and stage tier (1..4). */
    fun negotiationFlow(type: Int, tier: Int): NegotiationFlowDto? {
        if (type !in 1..16 || tier !in 1..4) return null
        return negotiation.personalities[type.toString()]?.get(tier.toString())
    }

    /** Persuasion flow for a personality type (1..16) and stage (0..3). */
    fun persuasionFlow(type: Int, stage: Int): NegotiationFlowDto? {
        if (type !in 1..16 || stage !in 0..3) return null
        return persuasion.personalities[type.toString()]?.get(stage.toString())
    }

    // ------------------------------------------------------------------------------ event lines

    /**
     * Authored reaction lines for an event: personality-specific lines when they exist, otherwise
     * the shared lines (`types` empty). Sorted by id for determinism.
     */
    fun eventLines(event: String, type: Int): List<EventLineDto> {
        val key = event.trim()
        if (key.isEmpty()) return emptyList()
        val ordered = eventLines.filter { it.event == key }.sortedBy { it.id }
        if (ordered.isEmpty()) return emptyList()
        val typed = ordered.filter { it.types.isNotEmpty() && it.types.contains(type) }
        return typed.ifEmpty { ordered.filter { it.types.isEmpty() } }
    }

    /** All lines of one event regardless of personality (diary/debug helper). */
    fun eventLines(event: String): List<EventLineDto> {
        val key = event.trim()
        if (key.isEmpty()) return emptyList()
        return eventLines.filter { it.event == key }.sortedBy { it.id }
    }

    // ------------------------------------------------------------------------------------ lore

    /** Canonical lore fact for a species key (normalized); `null` when unknown. */
    fun loreFact(speciesKey: String): LoreFactDto? {
        val key = normalizeSpeciesKey(speciesKey)
        if (key.isEmpty()) return null
        return lore
            .filter { normalizeSpeciesKey(it.speciesKey) == key }
            .sortedBy { it.speciesKey }
            .firstOrNull()
    }

    fun factsFor(speciesKey: String): List<LoreFactDto> {
        val key = normalizeSpeciesKey(speciesKey)
        if (key.isEmpty()) return emptyList()
        return lore.filter { normalizeSpeciesKey(it.speciesKey) == key }.sortedBy { it.speciesKey }
    }

    companion object {
        /** Core names in personality-number order 1..4 (PHILANTHROPY, VALOR, WISDOM, AMICABILITY). */
        val CORE_NAMES: List<String> = listOf("PHILANTHROPY", "VALOR", "WISDOM", "AMICABILITY")

        /** Empty pack used by tests/fallbacks; every lookup is safe on it. */
        fun empty(): DigilineContentPack = DigilineContentPack()

        /** 1..4 core index shared with `PersonalityShiftEngine.coreOf`. */
        fun coreIndexOf(typeNumber: Int): Int = (typeNumber.coerceIn(1, 16) - 1) / 4

        /**
         * Normalizes a species key/name per contract §1.1: lowercase, letters/digits only.
         * `"Aegiochusmon (Holy)"` -> `"aegiochusmonholy"`.
         */
        fun normalizeSpeciesKey(value: String): String =
            value.trim().lowercase().filter { it.isLetterOrDigit() }
    }
}

private fun positiveMod(value: Int, modulus: Int): Int {
    if (modulus <= 0) return 0
    val remainder = value % modulus
    return if (remainder < 0) remainder + modulus else remainder
}

private fun randomIndex(seed: Long, size: Int): Int {
    if (size <= 1) return 0
    return Random(seed).nextInt(size)
}
