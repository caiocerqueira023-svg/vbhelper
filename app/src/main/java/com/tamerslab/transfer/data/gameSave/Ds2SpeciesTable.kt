package com.tamerslab.transfer.data.gameSave

import android.content.Context
import com.google.gson.Gson
import com.tamerslab.gamesave.NdsStoryProfiles
import com.tamerslab.species.CanonicalSpeciesCatalog

/**
 * The parts of a game's own species data the transfer code needs to identify and (later)
 * synthesize individuals. Each game answers for its own profile only.
 */
interface NdsStorySpeciesSource {
    val gameId: String
    val formatProfile: String
    val codecVersion: Int
    fun name(speciesId: Long): String?
    fun lineage(speciesId: Long): List<Long>
}

/** Base-data record shape used by Dawn/Dusk record generation. */
data class Ds2SpeciesDefaults(
    val unk1A: Int,
    val unk50: Int,
    val abilities: List<Int>,
    val growth: Int,
    val skills: List<Int>,
    val resistances: List<Int>,
    val expCurve: Int,
    val baseLevel: Int,
    val maxLevel: Int,
    val baseHp: Int,
    val baseMp: Int,
    val baseAttack: Int,
    val baseDefense: Int,
    val baseSpirit: Int,
    val baseSpeed: Int,
    /** Species unk27: the engine picks one of its set bits for the record's unk1D. */
    val unk27: Int = 0,
)

/**
 * Digimon World Dawn/Dusk species data extracted from the user's own USA ROM by
 * analysis/ds2/tools/extract_ds2_species.py: the game's 0xFFFF-terminated name table, the
 * /dat/dm base-data records and the /dat/sk lineage chain. Dawn and Dusk share this data
 * byte-for-byte (verified), so one asset serves both titles.
 */
class Ds2SpeciesTable internal constructor(
    override val gameId: String,
    override val formatProfile: String,
    override val codecVersion: Int,
    private val names: Map<Int, String>,
    private val baseData: Map<Int, Ds2SpeciesDefaults>,
    private val previousSpecies: Map<Int, Int>,
    private val expCurves: List<List<Int>>,
    /** Full ROM names from ARM9; the record/status table is truncated to about 12 glyphs. */
    private val fullNames: Map<Int, String> = emptyMap(),
    /** Localization aliases the bundled catalog spells differently (see the asset provenance). */
    private val canonicalOverrides: Map<Int, String> = emptyMap(),
) : NdsStorySpeciesSource {
    override fun name(speciesId: Long): String? = names[speciesId.toInt()]?.takeIf { it.isNotEmpty() }

    /** The ROM's full species name (for example "MetalMamemon" where the short table says "MtlMamemon"). */
    fun fullName(speciesId: Long): String? = fullNames[speciesId.toInt()]?.takeIf { it.isNotEmpty() }

    /**
     * The bundled canonical species this game id really is. Prefers the documented localization
     * aliases, then the full ROM name, then the truncated display name; a name the catalog cannot
     * resolve stays unidentified instead of guessing.
     */
    fun canonicalId(speciesId: Long, catalog: CanonicalSpeciesCatalog): String? {
        canonicalOverrides[speciesId.toInt()]?.let { return it }
        val fromFull = fullName(speciesId)?.let(catalog::resolve)?.takeIf { it.kind == "DIGIMON" }?.id
        if (fromFull != null) return fromFull
        return name(speciesId)?.let(catalog::resolve)?.takeIf { it.kind == "DIGIMON" }?.id
    }

    override fun lineage(speciesId: Long): List<Long> {
        val seen = mutableSetOf(speciesId)
        val chain = mutableListOf<Long>()
        var current = speciesId.toInt()
        while (chain.size < HISTORY_SLOTS) {
            val previous = previousSpecies[current] ?: break
            if (previous < 0 || !seen.add(previous.toLong())) break
            chain += previous.toLong()
            current = previous
        }
        return chain
    }

    fun defaults(speciesId: Long): Ds2SpeciesDefaults? = baseData[speciesId.toInt()]

    /** The game's three level-up curves (ARM9 0x0211E440); same values as the World DS curves. */
    fun expToNext(curve: Int, level: Int, maxLevel: Int): Int {
        if (level < 1 || level >= 99 || level >= maxLevel) return 0
        return expCurves.getOrNull(curve.coerceIn(0, expCurves.lastIndex))?.getOrNull(level) ?: 0
    }

    private data class Asset(
        val gameId: String,
        val formatProfile: String,
        val names: Map<String, String>? = null,
        val baseData: Map<String, BaseData>? = null,
        val previousSpecies: Map<String, Int>? = null,
        val expCurves: List<List<Int>>? = null,
        val fullNames: Map<String, String>? = null,
        val canonicalOverrides: Map<String, String>? = null,
    )

    private data class BaseData(
        val level: Int = 1,
        val unk1A: Int = 0,
        val hp: Int = 1,
        val mp: Int = 0,
        val attack: Int = 1,
        val defense: Int = 1,
        val spirit: Int = 1,
        val speed: Int = 1,
        val unk50: Int = 0,
        val maxLevel: Int = 99,
        val resistances: List<Int> = emptyList(),
        val abilities: List<Int> = emptyList(),
        val growth: Int = 0,
        val skills: List<Int> = emptyList(),
        val expCurve: Int = 0,
        val unk27: Int = 0,
    )

    companion object {
        /** Matches the core DS2 codec version; a new codec means a different record layout. */
        internal const val CODEC_VERSION = 1
        private const val HISTORY_SLOTS = 5
        private const val SPECIES_COUNT = 0x1F4 - 0x41 + 1
        @Volatile private var cached: Ds2SpeciesTable? = null

        fun load(context: Context): Ds2SpeciesTable = cached ?: synchronized(this) {
            cached ?: parse(context.assets.open("ds2_species.json").bufferedReader().use { it.readText() })
                .also { cached = it }
        }

        internal fun parse(json: String): Ds2SpeciesTable {
            val asset = Gson().fromJson(json, Asset::class.java)
            val profile = NdsStoryProfiles.DAWN_DUSK
            require(asset.gameId == profile.gameId && asset.formatProfile == profile.formatProfile) {
                "Species table does not describe ${profile.formatProfile}"
            }
            val names = asset.names.orEmpty().mapKeys { it.key.toInt() }
            require(names.size == SPECIES_COUNT) { "Species names are not the full Dawn/Dusk range" }
            val base = asset.baseData.orEmpty().mapValues { (_, row) ->
                Ds2SpeciesDefaults(
                    unk1A = row.unk1A,
                    unk50 = row.unk50,
                    abilities = row.abilities,
                    growth = row.growth,
                    skills = row.skills,
                    resistances = row.resistances,
                    expCurve = row.expCurve,
                    baseLevel = row.level,
                    maxLevel = row.maxLevel,
                    baseHp = row.hp,
                    baseMp = row.mp,
                    baseAttack = row.attack,
                    baseDefense = row.defense,
                    baseSpirit = row.spirit,
                    baseSpeed = row.speed,
                    unk27 = row.unk27,
                )
            }.mapKeys { it.key.toInt() }
            require(base.size == SPECIES_COUNT) { "Species base data is not the full Dawn/Dusk range" }
            val curves = asset.expCurves.orEmpty()
            require(curves.size == 3 && curves.all { it.size == 99 }) { "EXP curves are not the three 99-level rows" }
            return Ds2SpeciesTable(asset.gameId, asset.formatProfile, CODEC_VERSION, names, base,
                asset.previousSpecies.orEmpty().mapKeys { it.key.toInt() }, curves,
                asset.fullNames.orEmpty().mapKeys { it.key.toInt() },
                asset.canonicalOverrides.orEmpty().mapKeys { it.key.toInt() })
        }
    }
}
