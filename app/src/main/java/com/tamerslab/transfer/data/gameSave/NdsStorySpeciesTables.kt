package com.tamerslab.transfer.data.gameSave

import android.content.Context
import com.tamerslab.species.CanonicalSpeciesCatalog

/**
 * Every registered game's own species tables, resolved by the observation's
 * (gameId, formatProfile, codecVersion) triple. One registry instead of a World DS-only table, so
 * Dawn/Dusk identification and materialization reuse the same code path.
 */
class NdsStorySpeciesTables(
    val ds1: WorldDsSpeciesTable,
    val ds2: Ds2SpeciesTable,
    val le: LeSpeciesTable,
) {
    fun resolve(gameId: String, formatProfile: String, codecVersion: Int): NdsStorySpeciesSource? =
        listOf(ds1, ds2, le).firstOrNull {
            it.gameId == gameId && it.formatProfile == formatProfile && it.codecVersion == codecVersion
        }

    fun name(gameId: String, formatProfile: String, codecVersion: Int, speciesId: Long): String? =
        resolve(gameId, formatProfile, codecVersion)?.name(speciesId)

    /**
     * The bundled canonical species for one game id. Dawn/Dusk answers from its full ROM name table
     * (the record/status table is truncated); World DS answers from its own name table.
     */
    fun canonicalId(gameId: String, formatProfile: String, codecVersion: Int, speciesId: Long,
        catalog: CanonicalSpeciesCatalog): String? {
        val table = resolve(gameId, formatProfile, codecVersion) ?: return null
        if (table is Ds2SpeciesTable) return table.canonicalId(speciesId, catalog)
        if (table is LeSpeciesTable) return table.canonicalId(speciesId, catalog)
        val species = table.name(speciesId)?.let(catalog::resolve) ?: return null
        return species.id.takeIf { species.kind == "DIGIMON" }
    }

    fun lineage(gameId: String, formatProfile: String, codecVersion: Int, speciesId: Long): List<Long> =
        resolve(gameId, formatProfile, codecVersion)?.lineage(speciesId).orEmpty()

    companion object {
        @Volatile private var cached: NdsStorySpeciesTables? = null

        fun load(context: Context): NdsStorySpeciesTables = cached ?: synchronized(this) {
            cached ?: NdsStorySpeciesTables(WorldDsSpeciesTable.load(context), Ds2SpeciesTable.load(context),
                LeSpeciesTable.load(context))
                .also { cached = it }
        }
    }
}
