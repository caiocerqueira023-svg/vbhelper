package com.tamerslab.species

import java.text.Normalizer
import java.util.Locale

/** Immutable reference identities and bundled artwork. No individual or device state lives here. */
data class CanonicalSpeciesDatabaseDto(
    val schemaVersion: Int = 1,
    val speciesRevision: String = "",
    val spriteRevision: String = "",
    val speciesSource: String = "",
    val spriteSource: String = "",
    val packSha256: String = "",
    val packBytes: Long = 0,
    val species: List<CanonicalSpeciesDto> = emptyList(),
    val spriteSets: List<DotSpriteSetDto> = emptyList(),
    val spriteSources: List<SpriteSourceDto> = emptyList(),
)

data class SpriteSourceDto(val id: String, val url: String, val revision: String, val credits: String = "")

data class DotSpriteAssociationDto(val defaultSetId: String? = null, val setIds: List<String> = emptyList())

data class CanonicalSpeciesDto(
    val id: String,
    val name: String,
    val aliases: List<String> = emptyList(),
    val kind: String = "DIGIMON",
    val level: String? = null,
    val type: String? = null,
    val profile: String? = null,
    val specialMoves: List<String> = emptyList(),
    val cardSlots: List<String> = emptyList(),
    val dotSprites: DotSpriteAssociationDto = DotSpriteAssociationDto(),
) {
    fun asEntry() = SpeciesEntryDto(name, level, type, profile?.let(ResearchText::description),
        ResearchText.moves(specialMoves), speciesId = id, dotSprites = dotSprites)
}

data class DotSpriteSetDto(
    val id: String,
    val spriteId: Int,
    val name: String,
    val author: String,
    val source: String = "",
    val donationUrl: String? = null,
    val format: Int = 1,
    val width: Int,
    val height: Int,
    val stage: String = "",
    val attribute: String = "",
    val frames: Map<String, String> = emptyMap(),
    val sourceImages: Map<String, String> = emptyMap(),
    val sourceHashes: Map<String, String> = emptyMap(),
    val provider: String = "dmc-sprites",
)

class CanonicalSpeciesCatalog(val data: CanonicalSpeciesDatabaseDto) {
    val speciesById = data.species.associateBy { it.id }
    val setsById = data.spriteSets.associateBy { it.id }
    val setsBySpriteId = data.spriteSets.associateBy { it.spriteId }
    private val directNames = data.species.groupBy { compactKey(it.name) }
    private val searchNames = data.species.flatMap { species ->
        species.dotSprites.setIds.map { it to (species.aliases + species.name) }
    }.groupBy({ it.first }, { it.second }).mapValues { it.value.flatten().distinct() }
    private val names = data.species.flatMap { species ->
        (species.aliases + species.name).distinct().map { key(it) to species.id }
    }.groupBy({ it.first }, { it.second }).mapValues { it.value.distinct() }

    init {
        require(data.schemaVersion == 1) { "Unsupported catalog schema" }
        require(speciesById.size == data.species.size && setsById.size == data.spriteSets.size)
        require(data.spriteSets.map { it.spriteId }.distinct().size == data.spriteSets.size)
        data.spriteSets.forEach { set ->
            require(set.id.isNotBlank() && set.spriteId < -1)
            require(set.width in 1..256 && set.height in 1..256 && "idle_1" in set.frames)
            require(set.frames.values.all { FRAME_PATH.matches(it) })
        }
        data.species.forEach { species ->
            require(species.id.isNotBlank() && species.name.isNotBlank())
            require(species.dotSprites.setIds.all { it in setsById })
            require(species.dotSprites.setIds.isEmpty() && species.dotSprites.defaultSetId == null ||
                species.dotSprites.defaultSetId in species.dotSprites.setIds)
        }
    }

    /** Explicit names/aliases only. Ambiguous identities never resolve to an arbitrary form. */
    fun resolve(nameOrId: String): CanonicalSpeciesDto? = speciesById[nameOrId]
        ?: directNames[compactKey(nameOrId)]?.singleOrNull()
        ?: names[key(nameOrId)]?.singleOrNull()?.let(speciesById::get)

    fun isExpandedReady(speciesId: String): Boolean = speciesById[speciesId]?.let {
        it.kind == "DIGIMON" && it.dotSprites.setIds.isNotEmpty()
    } ?: false

    fun setsForSpecies(nameOrId: String): List<DotSpriteSetDto> {
        val species = resolve(nameOrId) ?: return emptyList()
        return species.dotSprites.setIds.mapNotNull(setsById::get).sortedWith(
            compareBy<DotSpriteSetDto> { it.id != species.dotSprites.defaultSetId }
                .thenBy { it.author.lowercase(Locale.ROOT) }.thenBy { it.source }.thenBy { it.id })
    }

    fun searchSets(nameOrId: String, query: String, matchingOnly: Boolean): List<DotSpriteSetDto> {
        val source = if (matchingOnly) setsForSpecies(nameOrId) else data.spriteSets.sortedWith(
            compareBy<DotSpriteSetDto> { it.name.lowercase(Locale.ROOT) }
                .thenBy { !it.author.equals("Bandai", ignoreCase = true) }.thenBy { it.author }.thenBy { it.id })
        val term = query.trim()
        val normalized = key(term)
        return source.filter { set -> term.isEmpty() ||
            (listOf(set.name, set.author, set.source) + searchNames[set.id].orEmpty())
                .any { it.contains(term, ignoreCase = true) || normalized.isNotEmpty() && key(it).contains(normalized) } }
    }

    companion object {
        internal val FRAME_PATH = Regex("frames/[0-9a-f]{64}\\.bin")
        private val colors = listOf("black", "white", "red", "blue", "green", "yellow", "gold", "silver", "orange", "violet", "purple", "clear")
        private fun compactKey(name: String) = Normalizer.normalize(name, Normalizer.Form.NFKD)
            .lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }
        fun key(name: String): String {
            val compact = compactKey(name)
            val suffix = when {
                compact.endsWith("xantibody") -> "xantibody"
                compact.endsWith("x") -> "x"
                else -> ""
            }
            val base = compact.dropLast(suffix.length)
            val color = colors.firstOrNull { base.endsWith(it) && base.dropLast(it.length).endsWith("mon") }
            return (if (color == null) base else color + base.dropLast(color.length)) + if (suffix.isEmpty()) "" else "xantibody"
        }
    }
}

/** Native twelve-pose compatibility; DMC/PENC offsets are never interpreted as VB offsets. */
object DotFrameRoles {
    fun path(set: DotSpriteSetDto, pose: Int): String {
        val roles = when (pose) {
            2, 4, 6, 12 -> listOf("idle_2", "idle_1")
            7 -> listOf("train_1", "idle_1")
            8 -> listOf("train_2", "atk_2", "idle_2", "idle_1")
            9 -> listOf("happy", "idle_1")
            10 -> listOf("sleep_1", "idle_1")
            11 -> listOf("atk_2", "atk_1", "idle_1")
            else -> listOf("idle_1")
        }
        return requireNotNull(roles.firstNotNullOfOrNull(set.frames::get)) { "Missing validated idle frame" }
    }
}
