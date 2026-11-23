package com.tamerslab.digiline.freechat.model

import kotlinx.serialization.Serializable

/**
 * Wire DTOs for the Free Talk ("Papo") content packs (contract `FREE_CHAT_IMPLEMENTATION_CONTRACT.md`
 * §1/§2).
 *
 * Field names are frozen by the contract and must match the JSON produced by
 * `tools/freechat/build_freechat.py`, so renaming one is a content-pack breaking change (bump
 * `schemaVersion`). Parsing is expected to happen with
 * `Json { ignoreUnknownKeys = true; isLenient = true }`.
 *
 * Every nullable/defaulted field is optional in the JSON. These types are pure data: no Android,
 * Room or Compose imports are allowed in `freechat/model` or `freechat/engine`.
 */

/** `manifest.json` of a Free Talk content pack. */
@Serializable
data class FreeChatManifestDto(
    val schemaVersion: Int = 1,
    val contentVersion: Int = 1,
    val locale: String = "en",
    val files: Map<String, String> = emptyMap(),
)

/** Authored applicability conditions of an intent (hard filters, contract §2 rule 3). */
@Serializable
data class FreeChatConditionDto(
    val audience: String? = null,
    val stageTiers: List<Int> = emptyList(),
    val types: List<Int> = emptyList(),
    val cores: List<String> = emptyList(),
    val moodBands: List<String> = emptyList(),
    val recentEvents: List<String> = emptyList(),
)

/** One follow-up chip (rendered as a small button above the composer). */
@Serializable
data class FreeChatChipDto(
    val id: String,
    val text: String,
    val intentId: String? = null,
)

/** One authored reply line of an intent. */
@Serializable
data class FreeChatReplyDto(
    val id: String,
    val text: String,
    val tone: String? = null,
    val stageTiers: List<Int> = emptyList(),
    val types: List<Int> = emptyList(),
    val moodDelta: Int = 0,
    val energy: Int = 0,
    val loreRefs: List<String> = emptyList(),
    val authoringStatus: String = "draft",
)

/** One authored intent: match phrases for retrieval, replies, chips and a fallback reference. */
@Serializable
data class FreeChatIntentDto(
    val id: String,
    val topic: String = "general",
    val tags: List<String> = emptyList(),
    val matchPhrases: List<String> = emptyList(),
    val keywords: List<String> = emptyList(),
    val conditions: FreeChatConditionDto = FreeChatConditionDto(),
    val replies: List<FreeChatReplyDto> = emptyList(),
    val chips: List<FreeChatChipDto> = emptyList(),
    val fallbackId: String? = null,
)

/** One rung of the authored fallback ladder (levels 1..4, contract §2 rule 6). */
@Serializable
data class FreeChatFallbackDto(
    val level: Int,
    val replies: List<FreeChatReplyDto> = emptyList(),
)

/** `free_chat.json` — intents, global chips, fallback ladder and input safety terms. */
@Serializable
data class FreeChatPackDto(
    val schemaVersion: Int = 1,
    val contentVersion: Int = 1,
    val locale: String = "en",
    val intents: List<FreeChatIntentDto> = emptyList(),
    val chips: List<FreeChatChipDto> = emptyList(),
    val fallbacks: List<FreeChatFallbackDto> = emptyList(),
    val safety: Map<String, List<String>> = emptyMap(),
)

/** One generated identity atom set of a catalog species (`species_voice.json`, no prose). */
@Serializable
data class SpeciesVoiceDto(
    val speciesKey: String,
    val speciesName: String = "",
    val level: String? = null,
    val type: String? = null,
    val moves: List<String> = emptyList(),
    val tier: Int = 2,
)

/** `species_voice.json`. */
@Serializable
data class SpeciesVoicePackDto(
    val schemaVersion: Int = 1,
    val voices: List<SpeciesVoiceDto> = emptyList(),
)

/**
 * One raw public-dialogue pair used ONLY as the external fallback layer (owner requirement:
 * generated Digimon content is the priority source; real dataset dialogue answers only when the
 * generated corpus has no answer). Personal build only — `DISTRIBUTION_GATE.md` covers removal.
 */
@Serializable
data class FreeChatDatabasePairDto(
    val user: String,
    val response: String,
    val source: String = "",
    val license: String = "",
)

/** `database_fallback.json` — the licensed dataset fallback layer, optional in a pack. */
@Serializable
data class FreeChatDatabasePackDto(
    val schemaVersion: Int = 1,
    val contentVersion: Int = 1,
    val locale: String = "en",
    val source: String = "",
    val pairs: List<FreeChatDatabasePairDto> = emptyList(),
)
