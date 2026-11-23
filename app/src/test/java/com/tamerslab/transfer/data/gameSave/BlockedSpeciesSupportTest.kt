package com.tamerslab.transfer.data.gameSave

import com.google.gson.Gson
import com.tamerslab.species.CanonicalSpeciesCatalog
import com.tamerslab.species.CanonicalSpeciesDatabaseDto
import java.io.File
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The partners the owner reported as unsendable must map into at least one story title. */
class BlockedSpeciesSupportTest {
    private fun asset(name: String): File =
        File("src/main/assets/$name").takeIf { it.isFile } ?: File("app/src/main/assets/$name")

    private fun catalog(): CanonicalSpeciesCatalog = CanonicalSpeciesCatalog(
        Gson().fromJson(asset("species_catalog.json").reader(), CanonicalSpeciesDatabaseDto::class.java))

    private fun names(): List<String> = listOf(
        "Otamamon", "Gottsumon", "Machinedramon", "Falcomon", "SkullGreymon",
        "digimon.weregarurumonblack", "digimon.karatenmon",
    )

    @Test
    fun `reported partners resolve to a canonical species`() {
        val catalog = catalog()
        for (name in names()) {
            assertNotNull("$name does not resolve", catalog.resolve(name))
        }
    }

    @Test
    fun `reported partners are mapped into at least one story title`() {
        val catalog = catalog()
        val ds1 = WorldDsSpeciesTable.parse(asset("world_ds_species.json").readText())
        val ds2 = Ds2SpeciesTable.parse(asset("ds2_species.json").readText())
        val le = LeSpeciesTable.parse(asset("le_species.json").readText())
        for (name in names()) {
            val canonical = requireNotNull(catalog.resolve(name))
            val inDs1 = WorldDsSpeciesCrosswalk.dsIdsByCanonical(ds1, catalog)[canonical.id] != null
            val inDs2 = WorldDsSpeciesCrosswalk.ds2IdsByCanonical(ds2, catalog)[canonical.id] != null
            val inLe = WorldDsSpeciesCrosswalk.leIdsByCanonical(le, catalog)[canonical.id] != null
            assertTrue("$name is mapped into no story title", inDs1 || inDs2 || inLe)
        }
    }

    @Test
    fun `bootstrap providers have creation data for the reported partners`() {
        val catalog = catalog()
        val ds1 = WorldDsSpeciesTable.parse(asset("world_ds_species.json").readText())
        val ds2 = Ds2SpeciesTable.parse(asset("ds2_species.json").readText())
        val le = LeSpeciesTable.parse(asset("le_species.json").readText())
        val names = listOf("Otamamon", "Gottsumon", "Devidramon", "Falcomon", "SkullGreymon",
            "digimon.weregarurumonblack", "digimon.karatenmon")
        for (name in names) {
            val canonical = requireNotNull(catalog.resolve(name))
            val ds1Id = WorldDsSpeciesCrosswalk.dsIdsByCanonical(ds1, catalog)[canonical.id]
            val ds2Id = WorldDsSpeciesCrosswalk.ds2IdsByCanonical(ds2, catalog)[canonical.id]
            val leId = WorldDsSpeciesCrosswalk.leIdsByCanonical(le, catalog)[canonical.id]
            if (ds1Id != null) {
                assertNotNull("$name has no World DS creation stats", WorldDsCreationStats(ds1).statsFor(ds1Id, 50))
            }
            if (ds2Id != null) {
                assertNotNull("$name has no Dawn/Dusk creation stats", Ds2CreationStats(ds2).statsFor(ds2Id, 50))
            }
            if (leId != null) {
                assertNotNull("$name has no LE creation stats", LeCreationStats(le).statsFor(leId, 50))
            }
        }
    }
}
