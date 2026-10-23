package com.github.nacabaro.vbhelper.source.gameSave

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class WorldDsSpeciesTableTest {
    @Test fun shippedTableNamesTheGamesSpeciesRange() {
        val table = shipped()
        assertEquals("Koromon", table.name(0))
        assertEquals("Poyomon", table.name(2))
        assertEquals("Tanemon", table.name(4))
        assertEquals("Calumon", table.name(11))
        assertEquals("Pile Egg", table.name(420))
        assertEquals("Saber Egg", table.name(425))
    }

    @Test fun shippedTableCarriesSpeciesRecordDefaults() {
        val table = shipped()
        assertEquals(2, table.defaults(2)?.unk1A)
        assertEquals(1, table.defaults(2)?.unk50)
        assertEquals(45, table.defaults(2)?.ability)
        assertEquals(listOf(5, 112, 65535, 65535), table.defaults(2)?.skills)
        assertEquals(3, table.defaults(166)?.unk1A)
        assertEquals(listOf(1, 19, 186, 149), table.defaults(166)?.skills)
        assertNull(table.defaults(426))
    }

    @Test fun shippedTableCarriesTheGamesPreviousFormChain() {
        val table = shipped()
        assertEquals(listOf(4L), table.lineage(35))
        assertEquals(listOf(8L), table.lineage(29))
        assertEquals(listOf(91L, 19L), table.lineage(166))
        assertEquals(listOf(207L, 128L, 38L, 3L), table.lineage(313))
        assertEquals(emptyList<Long>(), table.lineage(3))
        assertEquals(emptyList<Long>(), table.lineage(426))
    }

    @Test fun unusedSlotsAndOutOfRangeIdsStayUnnamed() {
        val table = shipped()
        assertNull(table.name(101))
        assertNull(table.name(426))
        assertNull(table.name(-1))
    }

    @Test fun aTableThatDoesNotDescribeThisProfileOrRangeIsRejected() {
        val valid = file.reader().use { it.readText() }
        assertThrows(IllegalArgumentException::class.java) { WorldDsSpeciesTable.parse(valid.replace("digimon_world_ds_nds", "digimon_world_ds_3ds")) }
        assertThrows(IllegalArgumentException::class.java) { WorldDsSpeciesTable.parse(valid.replace("world-ds-usa-drastic-raw-v1", "world-ds-eur")) }
        assertThrows(IllegalArgumentException::class.java) { WorldDsSpeciesTable.parse(valid.replace("\"Koromon\",", "")) }
    }

    private fun shipped(): WorldDsSpeciesTable =
        WorldDsSpeciesTable.parse(file.reader().use { it.readText() })

    private val file: File =
        File("src/main/assets/world_ds_species.json").takeIf { it.isFile } ?: File("app/src/main/assets/world_ds_species.json")
}
