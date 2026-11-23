package com.tamerslab.digiline.freechat.engine

/**
 * Entity mentions found in a normalized turn (contract §2).
 *
 * [speciesKeys] are canonical species keys: lowercase letters/digits only, generated with the same
 * rules as `DigilineContentPack.normalizeSpeciesKey` (so `"Agumon (Black)"` → `"agumonblack"`),
 * which lets intents compare them with authored `loreRefs` and the partner `speciesKey`.
 */
data class FreeChatEntityMentions(
    val speciesKeys: Set<String>,
    val worldTerms: Set<String>,
    val levels: Set<String>,
) {
    val isEmpty: Boolean
        get() = speciesKeys.isEmpty() && worldTerms.isEmpty() && levels.isEmpty()

    companion object {
        val EMPTY = FreeChatEntityMentions(emptySet(), emptySet(), emptySet())
    }
}

/**
 * Longest-match entity extractor over generated species names + a small authored world lexicon
 * (contract §2; plan §8.2).
 *
 * Rules:
 * - multi-token surfaces win over their substrings (`"metal greymon"` is consumed whole, so the
 *   shorter `"greymon"` is not emitted twice);
 * - species keys are matched on the compact (letters/digits) form, so a name typed without spaces
 *   still matches the authored name and vice versa for compact authored names;
 * - conservative by design: a surface that resolves to more than one species key is ignored, and
 *   single-token names shorter than [MIN_SPECIES_KEY_LENGTH] or in [ORDINARY_WORD_KEYS] are never
 *   hard-extracted (they are ordinary words, not reliable identity signals).
 *
 * The constructor precomputes immutable maps, so [extract] is a pure read and thread-safe.
 */
class EntityLexicon(speciesNames: List<String>) {
    private val normalizer = FreeChatNormalizer()

    /** Normalized multi-token authored species surface -> compact species key. */
    private val spacedSpecies: Map<String, String>

    /** Compact (letters/digits) surface -> compact species key, for single-token input. */
    private val compactSpecies: Map<String, String>

    /** Normalized world/game term surface -> canonical world term. */
    private val worldTerms: Map<String, String>

    /** Normalized level surface (e.g. `"baby 1"`) -> canonical level token (e.g. `"baby i"`). */
    private val levelTerms: Map<String, String>

    private val maxSurfaceTokens: Int

    init {
        val spaced = LinkedHashMap<String, MutableSet<String>>()
        val compacts = LinkedHashMap<String, MutableSet<String>>()
        for (name in speciesNames) {
            val tokens = normalizer.normalize(name).split(' ').filter { it.isNotEmpty() }
            if (tokens.isEmpty()) continue
            val key = tokens.joinToString(separator = "") { token -> token.filter { it.isLetterOrDigit() } }
            if (key.length < MIN_SPECIES_KEY_LENGTH) continue
            if (key in ORDINARY_WORD_KEYS) continue
            compacts.getOrPut(key) { LinkedHashSet() }.add(key)
            if (tokens.size > 1) {
                spaced.getOrPut(tokens.joinToString(" ")) { LinkedHashSet() }.add(key)
            }
        }
        spacedSpecies = unambiguous(spaced)
        compactSpecies = unambiguous(compacts)

        worldTerms = buildTerms(WORLD_TERMS.associateWith { it })
        levelTerms = buildTerms(LEVEL_TERMS.toMap())

        maxSurfaceTokens = sequenceOf(
            spacedSpecies.keys.asSequence().map { tokenCount(it) },
            worldTerms.keys.asSequence().map { tokenCount(it) },
            levelTerms.keys.asSequence().map { tokenCount(it) },
        ).flatten().maxOrNull() ?: 1
    }

    /**
     * Extracts entity mentions from an already normalized string.
     *
     * Consumes the longest match at each position (never overlapping), so the result does not
     * depend on map or hash iteration order.
     */
    fun extract(normalizedText: String): FreeChatEntityMentions {
        val tokens = normalizedText.split(' ').filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return FreeChatEntityMentions.EMPTY
        val speciesKeys = LinkedHashSet<String>()
        val foundWorldTerms = LinkedHashSet<String>()
        val foundLevels = LinkedHashSet<String>()
        var index = 0
        while (index < tokens.size) {
            val match = matchAt(tokens, index)
            if (match == null) {
                index += 1
                continue
            }
            when (match.kind) {
                MentionKind.SPECIES -> speciesKeys.add(match.value)
                MentionKind.WORLD -> foundWorldTerms.add(match.value)
                MentionKind.LEVEL -> foundLevels.add(match.value)
            }
            index += match.tokenCount
        }
        return FreeChatEntityMentions(speciesKeys, foundWorldTerms, foundLevels)
    }

    private fun matchAt(tokens: List<String>, start: Int): SurfaceMatch? {
        val available = minOf(maxSurfaceTokens, tokens.size - start)
        for (count in available downTo 1) {
            val spaced = tokens.subList(start, start + count).joinToString(" ")
            if (count == 1) {
                compactSpecies[spaced]?.let { return SurfaceMatch(MentionKind.SPECIES, it, 1) }
            } else {
                spacedSpecies[spaced]?.let { return SurfaceMatch(MentionKind.SPECIES, it, count) }
            }
            worldTerms[spaced]?.let { return SurfaceMatch(MentionKind.WORLD, it, count) }
            levelTerms[spaced]?.let { return SurfaceMatch(MentionKind.LEVEL, it, count) }
        }
        return null
    }

    private fun unambiguous(source: Map<String, MutableSet<String>>): Map<String, String> {
        val result = LinkedHashMap<String, String>(source.size)
        for ((surface, keys) in source) {
            if (keys.size == 1) result[surface] = keys.first()
        }
        return result
    }

    private fun buildTerms(pairs: Map<String, String>): Map<String, String> {
        val result = LinkedHashMap<String, String>(pairs.size)
        for ((surface, canonical) in pairs) {
            val normalizedSurface = normalizer.normalize(surface)
            val normalizedCanonical = normalizer.normalize(canonical)
            if (normalizedSurface.isEmpty() || normalizedCanonical.isEmpty()) continue
            result[normalizedSurface] = normalizedCanonical
        }
        return result
    }

    private fun tokenCount(surface: String): Int = surface.count { it == ' ' } + 1

    private enum class MentionKind { SPECIES, WORLD, LEVEL }

    private data class SurfaceMatch(val kind: MentionKind, val value: String, val tokenCount: Int)

    companion object {
        /** Single-token names shorter than this are too weak to treat as an identity mention. */
        const val MIN_SPECIES_KEY_LENGTH = 3

        /**
         * Ordinary-word guard: catalog names that are also everyday words must not hard-boost a
         * species topic. The generated catalog only contains `"Bun"` today; the rest keep the rule
         * future-proof if aliases are ever fed in.
         */
        private val ORDINARY_WORD_KEYS: Set<String> = setOf(
            "bun",
            "man",
            "mon",
            "king",
            "queen",
            "angel",
            "devil",
            "ghost",
            "snow",
            "star",
        )

        /** Authored world/game lexicon (normalized at construction). Never lore prose. */
        private val WORLD_TERMS: List<String> = listOf(
            "digimon",
            "digital world",
            "digital monster",
            "digivice",
            "dim card",
            "digitama",
            "digiegg",
            "digi egg",
            "digicore",
            "digi core",
            "digimental",
            "digivolution",
            "digivolve",
            "digivolving",
            "evolution",
            "evolve",
            "tamer",
            "partner",
            "training",
            "quest",
            "farm",
            "battle",
            "fight",
            "arena",
            "network",
            "server",
            "continent",
            "file island",
            "yggdrasil",
            "royal knights",
            "seven great demon lords",
            "olympus twelve",
            "four holy beasts",
            "bits",
            "data",
            "vaccine",
            "virus",
            "special move",
            "special moves",
            "signature move",
        )

        /**
         * Authored Digimon level surface aliases. `"Baby I"`/`"Baby II"` are the catalog spellings;
         * numeric and hyphenated input forms fold to the same canonical token.
         */
        private val LEVEL_TERMS: List<Pair<String, String>> = listOf(
            "baby i" to "baby i",
            "baby 1" to "baby i",
            "baby ii" to "baby ii",
            "baby 2" to "baby ii",
            "child" to "child",
            "rookie" to "rookie",
            "in training" to "in training",
            "adult" to "adult",
            "champion" to "champion",
            "armor" to "armor",
            "hybrid" to "hybrid",
            "perfect" to "perfect",
            "ultimate" to "ultimate",
            "mega" to "mega",
        )
    }
}
