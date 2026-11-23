package com.tamerslab.digiline.freechat.engine

import com.tamerslab.digiline.freechat.model.FreeChatIntentDto
import com.tamerslab.digiline.freechat.model.FreeChatPackDto

/** One scored retrieval candidate (contract §2). */
data class ScoredIntent(
    val intent: FreeChatIntentDto,
    val scoreQ16: Int,
    val entityHits: Int,
    /** Number of query content tokens (stopwords removed) this document actually matches. */
    val matchedQueryTokens: Int = 0,
    /** Number of content tokens in the query (stopwords removed). */
    val queryTokenCount: Int = 0,
    /** The whole normalized query equals an authored match phrase (strongest evidence). */
    val exactPhraseMatch: Boolean = false,
)

/**
 * Deterministic integer BM25 retriever over the authored intent match phrases (contract §2;
 * plan §8.2 stage B).
 *
 * Each intent is a document whose text is `matchPhrases + keywords`; every phrase is normalized
 * with the same [FreeChatNormalizer] as the player input. The score is classic BM25
 * (`k1 = 1.2`, `b = 0.75`) evaluated entirely in **Q16 fixed point**:
 *
 * - term idf uses an integer `ln` (no `Math.log`, no floats in ranking);
 * - document lengths, idf and tf weights are all integers;
 * - the only non-BM25 additions are (a) an exact normalized match-phrase bonus and (b) integer
 *   entity boosts, so merely naming a species/world term can produce a positive candidate;
 * - ties are broken by `(scoreQ16 desc, entityHits desc, intent.id asc)`, never by map iteration
 *   order, so the same input always yields the same ordered list.
 *
 * Ranking is bit-exact across runs/devices because every operation is integer arithmetic.
 */
class FreeChatRetriever(private val pack: FreeChatPackDto) {
    private val normalizer = FreeChatNormalizer()
    private val documents: List<IntentDocument>
    private val documentCount: Int
    private val averageLengthQ16: Long
    private val idfQ16ByTerm: Map<String, Int>

    init {
        val built = pack.intents.map { buildDocument(it) }
        documents = built
        documentCount = built.size
        var totalLength = 0L
        val documentFrequencies = LinkedHashMap<String, Int>()
        for (document in built) {
            totalLength += document.tokenCount.toLong()
            for (term in document.termFrequencies.keys) {
                documentFrequencies[term] = (documentFrequencies[term] ?: 0) + 1
            }
        }
        averageLengthQ16 = if (documentCount == 0) 0L else totalLength * FIXED_ONE / documentCount
        val idf = LinkedHashMap<String, Int>(documentFrequencies.size)
        for ((term, documentFrequency) in documentFrequencies) {
            idf[term] = inverseDocumentFrequencyQ16(documentFrequency)
        }
        idfQ16ByTerm = idf
    }

    /**
     * Returns up to [limit] candidates ordered by descending [ScoredIntent.scoreQ16].
     *
     * Intents without any textual or entity evidence are omitted, so an empty result is the
     * engine's signal to use the fallback ladder.
     */
    fun candidates(
        normalizedText: String,
        entities: FreeChatEntityMentions,
        limit: Int = 8,
    ): List<ScoredIntent> {
        if (limit <= 0 || documents.isEmpty()) return emptyList()
        val queryTokens = normalizedText.split(' ').filter { it.isNotEmpty() }.distinct()
        if (queryTokens.isEmpty() && entities.isEmpty) return emptyList()
        // Evidence is measured on content tokens only: function words ("you", "how", "the") must
        // not make an unrelated intent look like a match.
        val queryContentTokens = queryTokens.filter { it !in STOPWORDS && it.length >= 2 }
        val queryContentTokenSet = queryContentTokens.toSet()

        val scored = ArrayList<ScoredIntent>(documents.size)
        for (document in documents) {
            var textScoreQ16 = 0L
            var matchedContentTokens = 0
            for (term in queryTokens) {
                val termFrequency = document.termFrequencies[term] ?: continue
                if (term in queryContentTokenSet) matchedContentTokens++
                textScoreQ16 += termScoreQ16(
                    termFrequency = termFrequency,
                    documentLength = document.tokenCount,
                    idfQ16 = idfQ16ByTerm[term] ?: 0,
                )
            }
            val exactPhraseMatch = normalizedText.isNotEmpty() && normalizedText in document.exactPhrases
            if (exactPhraseMatch) {
                textScoreQ16 += EXACT_PHRASE_BONUS_Q16
            }
            val speciesMatches = entities.speciesKeys.count { document.entityTokens.contains(it) }
            val worldMatches = entities.worldTerms.count { document.entitySurface.contains(" $it ") }
            val levelMatches = entities.levels.count { document.entitySurface.contains(" $it ") }
            val entityHits = speciesMatches + worldMatches + levelMatches
            if (textScoreQ16 <= 0L && entityHits == 0) continue
            val boostedQ16 = boost(
                textScoreQ16 = textScoreQ16,
                speciesMatches = speciesMatches,
                worldMatches = worldMatches,
                levelMatches = levelMatches,
            )
            scored.add(
                ScoredIntent(
                    intent = document.intent,
                    scoreQ16 = boostedQ16.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(),
                    entityHits = entityHits,
                    matchedQueryTokens = matchedContentTokens,
                    queryTokenCount = queryContentTokens.size,
                    exactPhraseMatch = exactPhraseMatch,
                ),
            )
        }
        if (scored.isEmpty()) return emptyList()
        scored.sortWith(CANDIDATE_ORDER)
        return if (scored.size <= limit) scored.toList() else scored.subList(0, limit).toList()
    }

    /** Applies integer entity boosts on a 1000-basis, plus a small entity-only base score. */
    private fun boost(
        textScoreQ16: Long,
        speciesMatches: Int,
        worldMatches: Int,
        levelMatches: Int,
    ): Long {
        if (speciesMatches == 0 && worldMatches == 0 && levelMatches == 0) return textScoreQ16
        val boostPermille = (
            speciesMatches * SPECIES_BOOST_PERMILLE +
                worldMatches * WORLD_BOOST_PERMILLE +
                levelMatches * LEVEL_BOOST_PERMILLE
            ).coerceAtMost(MAX_BOOST_PERMILLE)
        val entityBaseQ16 = (
            speciesMatches.toLong() * SPECIES_ENTITY_BASE_Q16 +
                worldMatches.toLong() * WORLD_ENTITY_BASE_Q16 +
                levelMatches.toLong() * LEVEL_ENTITY_BASE_Q16
            ).coerceAtMost(MAX_ENTITY_BASE_Q16)
        return textScoreQ16 * (1000L + boostPermille) / 1000L + entityBaseQ16
    }

    private fun termScoreQ16(termFrequency: Int, documentLength: Int, idfQ16: Int): Long {
        if (termFrequency <= 0 || idfQ16 <= 0) return 0L
        val tfQ16 = termFrequency.toLong() * FIXED_ONE
        val lengthRatioQ16 = if (averageLengthQ16 > 0L) {
            documentLength.toLong() * FIXED_ONE * FIXED_ONE / averageLengthQ16
        } else {
            FIXED_ONE
        }
        val normQ16 = (FIXED_ONE - B_Q16) + B_Q16 * lengthRatioQ16 / FIXED_ONE
        val denominatorQ16 = tfQ16 + K1_Q16 * normQ16 / FIXED_ONE
        if (denominatorQ16 <= 0L) return 0L
        return idfQ16.toLong() * termFrequency.toLong() * (K1_Q16 + FIXED_ONE) / denominatorQ16
    }

    private fun inverseDocumentFrequencyQ16(documentFrequency: Int): Int {
        val safeDocumentCount = documentCount.coerceAtLeast(1)
        val safeFrequency = documentFrequency.coerceIn(1, safeDocumentCount)
        val numerator = 2L * (safeDocumentCount - safeFrequency) + 1L
        val denominator = 2L * safeFrequency + 1L
        val ratioQ16 = FIXED_ONE + numerator * FIXED_ONE / denominator
        return lnQ16(ratioQ16).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    }

    /**
     * Integer natural log in Q16 (`ln(1) == 0`, `ln(2) ≈ 45426`), via `atanh(atanh)`-style series:
     * `ln(m) = 2 * (z + z³/3 + z⁵/5 + …)` with `z = (m-1)/(m+1) ∈ [0, 1/3]` after scaling the
     * mantissa into `[1, 2)`. Deterministic and float-free.
     */
    private fun lnQ16(valueQ16: Long): Long {
        if (valueQ16 <= 0L) return 0L
        var mantissaQ16 = valueQ16
        var exponent = 0L
        while (mantissaQ16 >= 2L * FIXED_ONE) {
            mantissaQ16 = mantissaQ16 shr 1
            exponent += 1L
        }
        while (mantissaQ16 < FIXED_ONE) {
            mantissaQ16 = mantissaQ16 shl 1
            exponent -= 1L
        }
        val zQ16 = (mantissaQ16 - FIXED_ONE) * FIXED_ONE / (mantissaQ16 + FIXED_ONE)
        val zSquaredQ16 = zQ16 * zQ16 / FIXED_ONE
        var termQ16 = zQ16
        var sumQ16 = termQ16
        var denominator = 3L
        repeat(7) {
            termQ16 = termQ16 * zSquaredQ16 / FIXED_ONE
            sumQ16 += termQ16 / denominator
            denominator += 2L
        }
        return 2L * sumQ16 + exponent * LN2_Q16
    }

    private fun buildDocument(intent: FreeChatIntentDto): IntentDocument {
        val termFrequencies = LinkedHashMap<String, Int>()
        var tokenCount = 0
        val fields = ArrayList<String>(intent.matchPhrases.size + intent.keywords.size)
        fields.addAll(intent.matchPhrases)
        fields.addAll(intent.keywords)
        for (field in fields) {
            val tokens = normalizer.normalize(field).split(' ').filter { it.isNotEmpty() }
            tokenCount += tokens.size
            for (token in tokens) {
                termFrequencies[token] = (termFrequencies[token] ?: 0) + 1
            }
        }
        val exactPhrases = intent.matchPhrases
            .map { normalizer.normalize(it) }
            .filter { it.isNotEmpty() }
            .toSet()
        // Entity evidence is searched in every authored text field of the intent (phrases,
        // keywords, tags and the lore refs carried by its replies), not only in the BM25 field.
        val loreRefs = intent.replies.flatMap { it.loreRefs }
        val entityFields = fields + intent.tags + loreRefs
        val entitySurface = StringBuilder()
        val entityTokens = LinkedHashSet<String>()
        for (field in entityFields) {
            val normalized = normalizer.normalize(field)
            if (normalized.isEmpty()) continue
            entitySurface.append(' ').append(normalized)
            for (token in normalized.split(' ').filter { it.isNotEmpty() }) {
                entityTokens.add(token)
                val compact = token.filter { it.isLetterOrDigit() }
                if (compact.isNotEmpty()) entityTokens.add(compact)
            }
        }
        return IntentDocument(
            intent = intent,
            termFrequencies = termFrequencies,
            tokenCount = tokenCount,
            exactPhrases = exactPhrases,
            entitySurface = entitySurface.toString(),
            entityTokens = entityTokens,
        )
    }

    private class IntentDocument(
        val intent: FreeChatIntentDto,
        val termFrequencies: Map<String, Int>,
        val tokenCount: Int,
        val exactPhrases: Set<String>,
        val entitySurface: String,
        val entityTokens: Set<String>,
    )

    companion object {
        /** Q16 fixed-point denominator. */
        const val FIXED_ONE: Long = 65_536L

        /** BM25 `k1 = 1.2` in Q16. */
        private const val K1_Q16: Long = 78_643L

        /** BM25 `b = 0.75` in Q16. */
        private const val B_Q16: Long = 49_152L

        /** `ln(2)` in Q16. */
        private const val LN2_Q16: Long = 45_426L

        /** Exact normalized match phrase contributes this much (Q16). */
        private const val EXACT_PHRASE_BONUS_Q16: Long = 30L * FIXED_ONE

        private const val SPECIES_BOOST_PERMILLE = 180
        private const val WORLD_BOOST_PERMILLE = 130
        private const val LEVEL_BOOST_PERMILLE = 100
        private const val MAX_BOOST_PERMILLE = 900

        private const val SPECIES_ENTITY_BASE_Q16 = 12_000L
        private const val WORLD_ENTITY_BASE_Q16 = 8_000L
        private const val LEVEL_ENTITY_BASE_Q16 = 6_000L

        /** Entity-only evidence must not drown a strong textual match. */
        private const val MAX_ENTITY_BASE_Q16 = 96_000L

        private val CANDIDATE_ORDER: Comparator<ScoredIntent> =
            compareByDescending<ScoredIntent> { it.scoreQ16 }
                .thenByDescending { it.entityHits }
                .thenBy { it.intent.id }

        /**
         * Function words (EN + PT) dropped before evidence measurement. Content words such as
         * "training", "sad", "trust" or "hello" are what make a phrase a real match.
         */
        private val STOPWORDS: Set<String> = setOf(
            "a", "an", "the", "is", "are", "was", "were", "be", "been", "being", "am",
            "do", "does", "did", "doing", "have", "has", "had", "having",
            "i", "me", "my", "mine", "you", "your", "yours", "we", "our", "ours",
            "it", "its", "this", "that", "these", "those", "there", "here",
            "to", "of", "in", "on", "at", "for", "with", "and", "or", "but", "if", "then", "so",
            "what", "how", "why", "when", "where", "who", "which",
            "can", "could", "will", "would", "should", "may", "might", "must",
            "not", "no", "yes", "please", "just", "very", "really", "about", "like", "some", "any",
            "tell", "say", "said", "let", "get", "got", "go", "going", "want", "wanna",
            // Generic time/quantity nouns: a shared "day"/"time"/"thing" is not intent evidence.
            "day", "days", "today", "tonight", "night", "morning", "time", "times",
            "thing", "things", "stuff", "way", "people", "person", "yourself", "myself",
            "good", "bad", "great", "nice", "fine", "ok", "okay",
            // Portuguese function words (inputs are aliased but leftovers are still stopworded).
            "o", "a", "os", "as", "um", "uma", "de", "do", "da", "dos", "das", "em", "no", "na",
            "e", "ou", "que", "se", "por", "para", "com", "sem", "meu", "minha", "seu", "sua",
            "voce", "voces", "eu", "ele", "ela", "nos", "isso", "este", "esta", "esse", "essa",
            "ser", "sou", "era", "foi", "sao", "esta", "estou", "tem", "ter", "vai", "ir",
            "muito", "mais", "menos", "nao", "sim", "tudo", "bem", "ja", "ai", "la",
        )
    }
}
