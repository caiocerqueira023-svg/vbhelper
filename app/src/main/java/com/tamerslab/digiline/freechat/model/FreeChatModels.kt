package com.tamerslab.digiline.freechat.model

/**
 * Pure value types consumed by the Free Talk ("Papo") engine (contract §2).
 *
 * These types are engine-facing, not wire types: the serialization DTOs live in
 * `FreeChatDtos.kt`. Nothing in `freechat/model` or `freechat/engine` may import `android.*`,
 * `androidx.*` or Room, so the whole pipeline is JVM-unit-testable.
 */

/** Where the partner comes from: an owned Digimon or a wild contact. */
enum class FreeChatAudience { OWNED, WILD }

/** Coarse bond band used by authored `moodBands` conditions. */
enum class FreeChatMoodBand {
    LOW,
    SETTLED,
    POSITIVE;

    companion object {
        fun of(mood: Int): FreeChatMoodBand = when {
            mood < 35 -> LOW
            mood < 70 -> SETTLED
            else -> POSITIVE
        }
    }
}

/** Input safety classification; `SAFE` means no authored safety term matched. */
enum class FreeChatSafetyClass { SAFE, S1, S2, S3, PII }

/**
 * Immutable snapshot of everything the engine may use for one turn.
 *
 * [turnCounter] is the persisted per-conversation counter (it must not be derived from message
 * ids, so deleting history cannot reroll a variant). [contentVersion] is mixed into the variant
 * seed so a content update may change a pick but two runs on the same content never do.
 */
data class FreeChatContext(
    val individualId: String,
    val locale: String = "en",
    val speciesKey: String? = null,
    val speciesName: String? = null,
    val level: String? = null,
    val speciesType: String? = null,
    val stageTier: Int = 2,
    val personalityType: Int = 1,
    val core: String = "PHILANTHROPY",
    val mood: Int = 50,
    val audience: FreeChatAudience = FreeChatAudience.OWNED,
    val recentEventKeys: Set<String> = emptySet(),
    val recentReplyIds: Set<String> = emptySet(),
    val turnCounter: Long = 0,
    val contentVersion: Int = 0,
) {
    val moodBand: FreeChatMoodBand get() = FreeChatMoodBand.of(mood)
}

/** A follow-up suggestion rendered by the UI. */
data class FreeChatChip(val id: String, val text: String)

/** One resolved engine turn; `fallbackLevel == 0` means a real intent reply was matched. */
data class FreeChatReplyResult(
    val replyId: String,
    val text: String,
    val intentId: String,
    val topic: String,
    val tone: String?,
    val moodDelta: Int,
    val scoreQ16: Int,
    val fallbackLevel: Int,
    val safetyClass: FreeChatSafetyClass,
    val chips: List<FreeChatChip>,
    val contentVersion: Int,
    /** True when the line came from the licensed database fallback layer, not generated content. */
    val fromDatabase: Boolean = false,
)

/** Fully loaded content: manifest + intents pack + generated species voice atoms. */
data class FreeChatContent(
    val manifest: FreeChatManifestDto,
    val pack: FreeChatPackDto,
    val speciesVoices: Map<String, SpeciesVoiceDto>,
    val speciesNames: List<String>,
    val contentVersion: Int,
    val locale: String,
    /**
     * Optional licensed dataset fallback pairs (`database_fallback.json`). Empty when the pack has
     * no database layer. Used only after generated intents/species voice/intent fallbacks, never
     * before them (owner requirement: generated content is the priority answer source).
     */
    val databaseFallbacks: List<FreeChatDatabasePairDto> = emptyList(),
)
