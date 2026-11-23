package com.tamerslab.transfer.data.gameSave

import android.content.Context
import com.google.gson.Gson
import com.tamerslab.gamesave.LostEvolution
import com.tamerslab.species.CanonicalSpeciesCatalog

/** Base-data record shape used by Lost Evolution record synthesis (ARM9 0x38-byte blocks). */
data class LeSpeciesDefaults(
    val lineId: Int,
    val hp: Int,
    val mp: Int,
    val attack: Int,
    val defense: Int,
    val spirit: Int,
    val speed: Int,
    val growthBits: Int,
    val evolutions: List<Int>,
    val skills: List<Int>,
)

/**
 * Digimon Story: Lost Evolution species data extracted from the user's own ROM
 * (`analysis/dsle/tools/build_le_species_asset.py`): the id-ordered name list from the message
 * pack (index == species id, validated against the real save), the 0x38-byte parameter blocks from
 * ARM9 (base stats, skills) and the stage-ordered id bands.
 */
class LeSpeciesTable internal constructor(
    override val gameId: String,
    override val formatProfile: String,
    override val codecVersion: Int,
    private val names: Map<Int, String>,
    private val baseData: Map<Int, LeSpeciesDefaults>,
    private val canonicalOverrides: Map<Int, String>,
    private val stages: Map<Int, Int>,
) : NdsStorySpeciesSource {
    override fun name(speciesId: Long): String? = names[speciesId.toInt()]?.takeIf { it.isNotEmpty() }

    /**
     * No evidenced ancestor chain exists for Lost Evolution records (the record has no history
     * forms); the engine's evolution targets are not ancestors, so they are not reported here.
     */
    override fun lineage(speciesId: Long): List<Long> = emptyList()

    fun defaults(speciesId: Long): LeSpeciesDefaults? = baseData[speciesId.toInt()]

    /** Stage index 0..4 derived from the ROM's stage-ordered species table. */
    fun stage(speciesId: Long): Int = stages[speciesId.toInt()] ?: 0

    /** The bundled canonical species for one game id (explicit override, then the game's name). */
    fun canonicalId(speciesId: Long, catalog: CanonicalSpeciesCatalog): String? {
        canonicalOverrides[speciesId.toInt()]?.let { return it }
        return name(speciesId)?.let(catalog::resolve)?.takeIf { it.kind == "DIGIMON" }?.id
    }

    companion object {
        internal const val CODEC_VERSION = 1
        @Volatile private var cached: LeSpeciesTable? = null

        fun load(context: Context): LeSpeciesTable = cached ?: synchronized(this) {
            cached ?: parse(context.assets.open("le_species.json").bufferedReader().use { it.readText() })
                .also { cached = it }
        }

        internal fun parse(json: String): LeSpeciesTable {
            val asset = Gson().fromJson(json, Asset::class.java)
            val profile = com.tamerslab.gamesave.NdsStoryProfiles.LOST_EVOLUTION
            require(asset.gameId == profile.gameId && asset.formatProfile == profile.formatProfile) {
                "Species table does not describe ${profile.formatProfile}"
            }
            val names = asset.names.orEmpty().mapKeys { it.key.toInt() }
            require(names.isNotEmpty()) { "Lost Evolution species names are missing" }
            val base = asset.baseData.orEmpty().mapKeys { it.key.toInt() }.mapValues { (_, row) ->
                LeSpeciesDefaults(row.lineId, row.hp, row.mp, row.attack, row.defense, row.spirit,
                    row.speed, row.growthBits, row.evolutions, row.skills)
            }
            val stages = mutableMapOf<Int, Int>()
            asset.stageBands.orEmpty().forEach { (label, range) ->
                val stage = when (label) {
                    "inTraining" -> 0
                    "rookie" -> 1
                    "champion" -> 2
                    "ultimate" -> 3
                    "mega" -> 4
                    else -> return@forEach
                }
                if (range.size == 2) for (species in range[0]..range[1]) stages[species] = stage
            }
            return LeSpeciesTable(asset.gameId, asset.formatProfile, CODEC_VERSION, names, base,
                asset.canonicalOverrides.orEmpty().mapKeys { it.key.toInt() }, stages)
        }
    }

    private data class Asset(
        val gameId: String,
        val formatProfile: String,
        val names: Map<String, String>? = null,
        val baseData: Map<String, BaseData>? = null,
        val canonicalOverrides: Map<String, String>? = null,
        val stageBands: Map<String, List<Int>>? = null,
    )

    private data class BaseData(
        val lineId: Int = 0,
        val hp: Int = 1,
        val mp: Int = 0,
        val attack: Int = 1,
        val defense: Int = 1,
        val spirit: Int = 1,
        val speed: Int = 1,
        val growthBits: Int = 0,
        val evolutions: List<Int> = emptyList(),
        val skills: List<Int> = emptyList(),
    )
}
