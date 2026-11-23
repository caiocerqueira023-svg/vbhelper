package com.tamerslab.transfer.data.gameSave

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldDsCreationStatsTest {
    private fun asset(name: String): File =
        File("src/main/assets/$name").takeIf { it.isFile } ?: File("app/src/main/assets/$name")

    private fun table(): WorldDsSpeciesTable = WorldDsSpeciesTable.parse(asset("world_ds_species.json").readText())

    private fun firstSpecies(table: WorldDsSpeciesTable): Long =
        (0L..425L).first { table.creation(it) != null && table.name(it) != null }

    @Test
    fun `level one stats start from the species creation block`() {
        val table = table()
        val species = firstSpecies(table)
        val creation = requireNotNull(table.creation(species))
        val stats = requireNotNull(WorldDsCreationStats(table).statsFor(species, 1))
        assertEquals(1, stats.level)
        // The engine factor is (level * growth + 100000) / 100000, so level 1 is the base block
        // within one growth-percent step.
        assertTrue(stats.maxHp >= creation.baseHp - 1)
        assertEquals(creation.baseMp, stats.maxMp)
        assertEquals(creation.baseAttack, stats.attack)
        assertEquals(creation.baseDefense, stats.defense)
        assertEquals(creation.baseSpirit, stats.spirit)
        assertEquals(creation.baseSpeed, stats.speed)
    }

    @Test
    fun `higher levels follow the core growth rows inside the engine clamps`() {
        val table = table()
        val species = firstSpecies(table)
        val provider = WorldDsCreationStats(table)
        val low = requireNotNull(provider.statsFor(species, 1))
        val high = requireNotNull(provider.statsFor(species, 99))
        assertTrue(high.maxHp >= low.maxHp && high.maxHp <= 9999)
        assertTrue(high.maxMp >= low.maxMp && high.maxMp <= 9999)
        assertTrue(high.attack >= low.attack && high.attack <= 999)
        assertTrue(high.defense >= low.defense && high.defense <= 999)
        assertTrue(high.spirit >= low.spirit && high.spirit <= 999)
        assertTrue(high.speed >= low.speed && high.speed <= 999)
    }

    @Test
    fun `levels outside the DS range clamp to 99`() {
        val table = table()
        val species = firstSpecies(table)
        assertEquals(99, requireNotNull(WorldDsCreationStats(table).statsFor(species, 500)).level)
        assertEquals(1, requireNotNull(WorldDsCreationStats(table).statsFor(species, -5)).level)
    }
}
