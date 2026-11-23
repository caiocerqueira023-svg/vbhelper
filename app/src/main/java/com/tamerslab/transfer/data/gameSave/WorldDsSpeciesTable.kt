package com.tamerslab.transfer.data.gameSave

import android.content.Context
import com.google.gson.Gson
import com.tamerslab.gamesave.WorldDsRecordDefaults
import com.tamerslab.gamesave.WorldDsSaveReader

/**
 * The USA ROM's own species name tables (full names at data_0211f4e0, short names at
 * data_0211a688, indexed by the record's species id), extracted once by
 * tools/extract_world_ds_species.py and shipped as an asset. This is the game's data, not a
 * user review: it identifies a species for display and never creates, materializes or
 * overrides an individual or a mapping.
 */
class WorldDsSpeciesTable internal constructor(
    override val gameId: String,
    override val formatProfile: String,
    override val codecVersion: Int,
    private val names: List<String>,
    private val recordDefaults: List<WorldDsRecordDefaults?>,
    private val creationData: List<CreationRow?>,
    private val previousSpecies: List<Long>,
) : NdsStorySpeciesSource {
    override fun name(speciesId: Long): String? {
        if (speciesId < 0 || speciesId >= names.size) return null
        return names[speciesId.toInt()].takeIf { it.isNotEmpty() }
    }

    fun defaults(speciesId: Long): WorldDsRecordDefaults? {
        if (speciesId < 0 || speciesId >= recordDefaults.size) return null
        return recordDefaults[speciesId.toInt()]
    }

    /** Creation data copied by the engine addDigimon routine (func_020a29c4). */
    internal fun creation(speciesId: Long): CreationRow? {
        if (speciesId < 0 || speciesId >= creationData.size) return null
        return creationData[speciesId.toInt()]
    }

    internal data class CreationRow(
        val baseHp: Int,
        val baseMp: Int,
        val baseAttack: Int,
        val baseDefense: Int,
        val baseSpirit: Int,
        val baseSpeed: Int,
        val extra: Int,
        val maxLevel: Int,
        val baseLevel: Int,
        val growth: Int,
    )

    /** The game's previous-form chain for [speciesId], nearest first (func_020a137c + sk[+0x54]). */
    override fun lineage(speciesId: Long): List<Long> {
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
            val creationRows = ArrayList<CreationRow?>()
            val defaults = asset.recordDefaults?.let { rows ->
                require(rows.size == WorldDsSaveReader.SPECIES_COUNT) { "Species records are not the full species range" }
                rows.map { row ->
                    require(row.size == 15 || row.size == 25) { "A species record template has the wrong field count" }
                    creationRows += if (row.size == 25) {
                        CreationRow(
                            baseHp = row[15], baseMp = row[16], baseAttack = row[17], baseDefense = row[18],
                            baseSpirit = row[19], baseSpeed = row[20], extra = row[21],
                            maxLevel = row[22], baseLevel = row[23], growth = row[24],
                        )
                    } else null
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
            while (creationRows.size < WorldDsSaveReader.SPECIES_COUNT) creationRows += null
            val previous = asset.previousSpecies?.let { rows ->
                require(rows.size == WorldDsSaveReader.SPECIES_COUNT) {
                    "Species predecessors are not the full species range"
                }
                rows.map { it.toLong() }
            } ?: List(WorldDsSaveReader.SPECIES_COUNT) { -1L }
            return WorldDsSpeciesTable(asset.gameId, asset.formatProfile, CODEC_VERSION, asset.names,
                defaults, creationRows, previous)
        }
    }
}
