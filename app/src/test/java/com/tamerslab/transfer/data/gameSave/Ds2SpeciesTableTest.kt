package com.tamerslab.transfer.data.gameSave

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class Ds2SpeciesTableTest {
    private fun shipped(): Ds2SpeciesTable {
        val file = File("src/main/assets/ds2_species.json").takeIf { it.isFile }
            ?: File("app/src/main/assets/ds2_species.json")
        assertTrue("ds2_species.json asset is missing", file.isFile)
        return Ds2SpeciesTable.parse(file.readText())
    }

    @Test fun shippedTableCoversTheFullDawnDuskSpeciesRange() {
        val table = shipped()
        assertEquals("digimon_world_ds2_nds", table.gameId)
        assertEquals("ds2-usa-raw-v1", table.formatProfile)
        assertEquals(1, table.codecVersion)
        assertEquals("Chicchimon", table.name(0x41))
        assertEquals("Coronamon", table.name(156))
        assertEquals("Lunamon", table.name(157))
        assertEquals("Reppamon", table.name(0xE2))
        assertEquals("Karatenmon", table.name(0x14B))
        assertNull(table.name(0x40))
        assertNull(table.name(0x1F5))
    }

    @Test fun baseDataMatchesTheValuesFoundInRetailRecords() {
        val defaults = requireNotNull(shipped().defaults(156))
        assertEquals(128, defaults.baseHp)
        assertEquals(124, defaults.baseMp)
        assertEquals(82, defaults.baseAttack)
        assertEquals(68, defaults.baseDefense)
        assertEquals(52, defaults.baseSpirit)
        assertEquals(68, defaults.baseSpeed)
        assertEquals(32, defaults.maxLevel)
        assertEquals(5, defaults.unk50)
        assertEquals(listOf(32, 52, 111, 59), defaults.skills)
        assertEquals(listOf(1, 36, 255, 255), defaults.abilities)
        assertEquals(listOf(1000, 200, 900, 700, 700, 500, 400, 500), defaults.resistances)
        assertEquals(0, defaults.expCurve)
        assertEquals(0, defaults.growth)
    }

    @Test fun expCurvesMatchTheObservedLevelThresholds() {
        val table = shipped()
        // Coronamon at level 20 in the supplied save stores 646 as its next-level threshold;
        // Reppamon at level 35 stores 2344.
        assertEquals(646, table.expToNext(0, 20, 32))
        assertEquals(2344, table.expToNext(0, 35, 45))
        assertEquals(0, table.expToNext(0, 40, 40))
    }

    @Test fun fullRomNamesResolveSpeciesTheTruncatedDisplayTableCannot() {
        val file = File("src/main/assets/species_catalog.json").takeIf { it.isFile }
            ?: File("app/src/main/assets/species_catalog.json")
        val catalog = com.tamerslab.species.CanonicalSpeciesCatalog(
            com.google.gson.Gson().fromJson(file.reader(), com.tamerslab.species.CanonicalSpeciesDatabaseDto::class.java))
        val table = shipped()
        // The record/status table truncates these to MtlMamemon / WGarurumonB / PrMamemon.
        assertEquals("MetalMamemon", table.fullName(0x124))
        assertEquals("WarGarurumon(Black)", table.fullName(0x133))
        assertEquals("PrinceMamemon", table.fullName(0x1B7))
        assertEquals("Metal Mamemon", catalog.speciesById[requireNotNull(table.canonicalId(0x124, catalog))]?.name)
        assertEquals("digimon.metalmamemon", table.canonicalId(0x124, catalog))
        assertEquals("digimon.weregarurumonblack", table.canonicalId(0x133, catalog))
        assertEquals("digimon.weregarurumon", table.canonicalId(0x134, catalog))
        assertEquals("digimon.princemamemon", table.canonicalId(0x1B7, catalog))
        assertEquals("digimon.agumonblack", table.canonicalId(0x85, catalog))
        assertEquals("digimon.coronamon", table.canonicalId(156, catalog))
        // Localization aliases decided from the ROM evolution chains.
        assertEquals("digimon.volcamon", table.canonicalId(0x120, catalog))
        assertEquals("digimon.darktyranomon", table.canonicalId(0xD7, catalog))
        assertEquals("digimon.rapidmonperfect", table.canonicalId(0x144, catalog))
        assertEquals("digimon.bishopchessmonwhite", table.canonicalId(0x17B, catalog))
        assertEquals("digimon.rookchessmonblack", table.canonicalId(0x17C, catalog))
        assertEquals("digimon.slashangemon", table.canonicalId(0x1C0, catalog))
        assertEquals("digimon.dotfalcomon", table.canonicalId(0x90, catalog))
        assertEquals("digimon.justimonaccelarm", table.canonicalId(0x1D0, catalog))
        // Genuinely unidentified entries stay unidentified instead of guessing.
        assertNull(table.canonicalId(0x50, catalog)) // Kokomon is not in the bundled catalog
        assertNull(table.canonicalId(0x58, catalog)) // unused/undecided slot
        assertEquals(398, (0x41..0x1F4).count { table.fullName(it.toLong()) != null })
    }

    @Test fun lineageWalksTheGamesPreviousFormChain() {
        val table = shipped()
        val coronamon = table.lineage(156)
        assertTrue("Coronamon should have at least one previous form", coronamon.isNotEmpty())
        assertTrue(coronamon.all { it in 0x41L..0x1F4L })
        assertFalse(coronamon.contains(156L))
    }
}
