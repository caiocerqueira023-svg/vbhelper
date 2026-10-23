package com.github.nacabaro.vbhelper.source.gameSave

import android.content.Context
import com.google.gson.Gson
import com.github.nacabaro.vbhelper.gamesave.WorldDsRecordDefaults
import com.github.nacabaro.vbhelper.gamesave.WorldDsSaveReader

/**
 * The USA ROM's own species name tables (full names at data_0211f4e0, short names at
 * data_0211a688, indexed by the record's species id), extracted once by
 * tools/extract_world_ds_species.py and shipped as an asset. This is the game's data, not a
 * user review: it identifies a species for display and never creates, materializes or
 * overrides an individual or a mapping.
 */
class WorldDsSpeciesTable internal constructor(
    val gameId: String,
    val formatProfile: String,
    val codecVersion: Int,
    private val names: List<String>,
    private val recordDefaults: List<WorldDsRecordDefaults?>,
    private val previousSpecies: List<Long>,
) {
    fun name(speciesId: Long): String? {
        if (speciesId < 0 || speciesId >= names.size) return null
        return names[speciesId.toInt()].takeIf { it.isNotEmpty() }
    }

    fun defaults(speciesId: Long): WorldDsRecordDefaults? {
        if (speciesId < 0 || speciesId >= recordDefaults.size) return null
        return recordDefaults[speciesId.toInt()]
    }

    /** The game's previous-form chain for [speciesId], nearest first (func_020a137c + sk[+0x54]). */
    fun lineage(speciesId: Long): List<Long> {
        if (speciesId < 0 || speciesId >= previousSpecies.size) return emptyList()
        val seen = mutableSetOf(speciesId)
        val chain = mutableListOf<Long>()
        var current = speciesId
        while (chain.size < HISTORY_SLOTS) {
            val previous = previousSpecies.getOrNull(current.toInt()) ?: break
            if (previous < 0 || !seen.add(previous)) break
            chain += previous
            current = previous
        }
        return chain
    }

    private data class Asset(
        val gameId: String,
        val formatProfile: String,
        val names: List<String>,
        val recordDefaults: List<List<Int>>? = null,
        val previousSpecies: List<Int>? = null,
    )

    companion object {
        /** The reader's one verified World DS codec; a different codec means a different record layout. */
        private const val CODEC_VERSION = 1
        private const val HISTORY_SLOTS = 5
        @Volatile private var cached: WorldDsSpeciesTable? = null

        fun load(context: Context): WorldDsSpeciesTable = cached ?: synchronized(this) {
            cached ?: parse(context.assets.open("world_ds_species.json").bufferedReader().use { it.readText() })
                .also { cached = it }
        }

        internal fun parse(json: String): WorldDsSpeciesTable {
            val asset = Gson().fromJson(json, Asset::class.java)
            require(asset.gameId == WorldDsSaveReader.GAME_ID && asset.formatProfile == WorldDsSaveReader.PROFILE) {
                "Species table does not describe ${WorldDsSaveReader.PROFILE}"
            }
            require(asset.names.size == WorldDsSaveReader.SPECIES_COUNT) { "Species table is not the full species range" }
            val defaults = asset.recordDefaults?.let { rows ->
                require(rows.size == WorldDsSaveReader.SPECIES_COUNT) { "Species records are not the full species range" }
                rows.map { row ->
                    require(row.size == 15) { "A species record template has the wrong field count" }
                    WorldDsRecordDefaults(
                        unk1A = row[0],
                        unk50 = row[1],
                        ability = row[2],
                        skills = row.subList(3, 7),
                        resistances = row.subList(7, 14),
                        historyUnk50 = row[14],
                    )
                }
            } ?: List(WorldDsSaveReader.SPECIES_COUNT) { null }
            val previous = asset.previousSpecies?.let { rows ->
                require(rows.size == WorldDsSaveReader.SPECIES_COUNT) {
                    "Species predecessors are not the full species range"
                }
                rows.map { it.toLong() }
            } ?: List(WorldDsSaveReader.SPECIES_COUNT) { -1L }
            return WorldDsSpeciesTable(asset.gameId, asset.formatProfile, CODEC_VERSION, asset.names, defaults, previous)
        }
    }
}
