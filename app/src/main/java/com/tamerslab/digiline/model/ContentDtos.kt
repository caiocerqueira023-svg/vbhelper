package com.tamerslab.digiline.model

import kotlinx.serialization.Serializable

/**
 * Wire DTOs for the versioned DigiLine content packs.
 *
 * These shapes are frozen by `DIGILINE_IMPLEMENTATION_CONTRACT.md` §3: field names must match the
 * JSON produced by `tools/digiline/build_packs.py`, so changing one here is a content-pack breaking
 * change (bump `schemaVersion`). Parsing is expected to happen with
 * `Json { ignoreUnknownKeys = true; isLenient = true }`.
 *
 * Notes:
 * - Every nullable/defaulted field is optional in the JSON.
 * - [NegotiationNodeDto.id] defaults to `""` because authored packs key nodes by the `nodes` map
 *   and do not repeat the id inside the node object; the loader may fill it from the map key.
 * - Helpers are read-only conveniences; they are not serialized.
 */

/** `manifest.json` of a content pack. */
@Serializable
data class ContentManifest(
    val schemaVersion: Int,
    val contentVersion: Int,
    val locale: String,
    val files: Map<String, String> = emptyMap(),
) {
    companion object {
        const val FILE_SPECIES = "species"
        const val FILE_GENERIC = "generic"
        const val FILE_QUIZ = "quiz"
        const val FILE_NEGOTIATION = "negotiation"
        const val FILE_PERSUASION = "persuasion"
        const val FILE_EVENTS = "events"
        const val FILE_LORE = "lore"
    }
}

/** One selectable answer of an owned-Digimon question card. */
@Serializable
data class AnswerOptionDto(
    val id: String,
    val text: String,
    val reaction: String = "",
    val moodDelta: Int = 0,
    val shift: Map<String, Int> = emptyMap(),
    val tone: String? = null,
) {
    /** [shift] converted to personality-type numbers (`1..16`), ignoring unparsable keys. */
    val shiftByType: Map<Int, Int>
        get() = buildMap {
            for ((key, value) in shift) {
                val type = key.trim().toIntOrNull() ?: continue
                if (type in 1..16 && value != 0) put(type, value)
            }
        }

    val hasShift: Boolean get() = shiftByType.isNotEmpty()
}

/** A question card for an owned Digimon (species-specific or generic by personality). */
@Serializable
data class QuestionCardDto(
    val id: String,
    val prompt: String,
    val options: List<AnswerOptionDto> = emptyList(),
    val stageTier: Int = 1,
    val speciesKey: String? = null,
    val speciesName: String? = null,
    val askerTypes: List<Int> = emptyList(),
    val core: String? = null,
    val tags: List<String> = emptyList(),
    val authoringStatus: String? = null,
) {
    val isSpeciesQuestion: Boolean
        get() = !speciesKey.isNullOrBlank() || tags.contains("species")

    fun option(id: String): AnswerOptionDto? = options.firstOrNull { it.id == id }
}

/** `species_questions.json` / `generic_questions.json`. */
@Serializable
data class QuestionPackDto(
    val schemaVersion: Int = 1,
    val questions: List<QuestionCardDto> = emptyList(),
)

/** One lore-grounded quiz question. */
@Serializable
data class QuizCardDto(
    val id: String,
    val stageTier: Int,
    val prompt: String,
    val options: List<String> = emptyList(),
    val correctIndex: Int,
    val explanation: String,
    val loreRefs: List<String> = emptyList(),
) {
    val hasValidAnswer: Boolean
        get() = correctIndex in options.indices

    val correctAnswer: String? get() = options.getOrNull(correctIndex)
}

/** `quiz.json`. */
@Serializable
data class QuizPackDto(
    val schemaVersion: Int = 1,
    val questions: List<QuizCardDto> = emptyList(),
)

/** Effects attached to a negotiation/persuasion option. */
@Serializable
data class NegotiationEffectsDto(
    val bits: Int = 0,
    val itemId: String? = null,
    val itemQuantity: Int = 0,
    val persuasionFlag: String? = null,
    val depart: Boolean = false,
) {
    /** Bits the player pays (positive cost; 0 when the option pays nothing or grants bits). */
    val bitsCost: Long get() = if (bits < 0) -bits.toLong() else 0L

    val hasPayment: Boolean get() = bitsCost > 0L || itemQuantity > 0
}

/** One selectable option inside a negotiation or persuasion node. */
@Serializable
data class NegotiationOptionDto(
    val id: String,
    val text: String,
    val next: String? = null,
    val requires: String? = null,
    /**
     * Authored demon reaction to *this specific answer*. Shown immediately above the next node so
     * every choice is acknowledged in-fiction (SMT-style dialogue trees) instead of jumping to an
     * unrelated follow-up.
     */
    val reaction: String? = null,
    /** Authored dialogue thread; the follow-up node must declare the same topic. */
    val topic: String? = null,
    val effects: NegotiationEffectsDto = NegotiationEffectsDto(),
)

/** A demand shown by a negotiation node (informational: payment is an option effect). */
@Serializable
data class DemandDto(
    val kind: String,
    val amount: Int = 0,
    val itemId: String? = null,
) {
    companion object {
        const val KIND_BITS = "bits"
        const val KIND_ITEM = "item"
    }
}

/** A single dialogue node of a negotiation or persuasion flow. */
@Serializable
data class NegotiationNodeDto(
    val id: String = "",
    val text: String,
    val speaker: String = "wild",
    val tag: String? = null,
    /** Authored dialogue thread; options leading here carry the same topic. */
    val topic: String? = null,
    val demand: DemandDto? = null,
    val outcome: String? = null,
    val options: List<NegotiationOptionDto> = emptyList(),
) {
    val isTerminal: Boolean get() = !outcome.isNullOrBlank()

    /** Authored tell marker: trick/insight/depart nodes carry extra UI weight. */
    val isTell: Boolean
        get() = tag == "trick" || tag == "insight" || tag == "depart"
}

/** A node graph with a start node id. */
@Serializable
data class NegotiationFlowDto(
    val start: String = "",
    val nodes: Map<String, NegotiationNodeDto> = emptyMap(),
) {
    val startNode: NegotiationNodeDto? get() = nodes[start]

    val isEmpty: Boolean get() = start.isBlank() || nodes.isEmpty()
}

/** `negotiation.json` (also the wire shape of `persuasion.json`). */
@Serializable
data class NegotiationPackDto(
    val schemaVersion: Int = 1,
    val personalities: Map<String, Map<String, NegotiationFlowDto>> = emptyMap(),
)

/** `persuasion.json`: personalities -> stage (0..3) -> flow. */
@Serializable
data class PersuasionPackDto(
    val schemaVersion: Int = 1,
    val personalities: Map<String, Map<String, NegotiationFlowDto>> = emptyMap(),
)

/** One authored event reaction line. */
@Serializable
data class EventLineDto(
    val id: String,
    val event: String,
    val types: List<Int> = emptyList(),
    val text: String,
    val moodDelta: Int = 0,
) {
    fun appliesTo(type: Int): Boolean = types.isEmpty() || types.contains(type)
}

/** `event_lines.json`. */
@Serializable
data class EventLinePackDto(
    val schemaVersion: Int = 1,
    val lines: List<EventLineDto> = emptyList(),
)

/** One canonical lore fact generated from the app species catalog. */
@Serializable
data class LoreFactDto(
    val speciesKey: String,
    val speciesName: String = "",
    val level: String? = null,
    val type: String? = null,
    val attribute: String? = null,
    val source: String = "app_species_catalog",
    val sourceUrl: String? = null,
)

/** `lore_species.json`. */
@Serializable
data class LorePackDto(
    val schemaVersion: Int = 1,
    val facts: List<LoreFactDto> = emptyList(),
)
