package com.tamerslab.transfer.data.gameSave

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LeCreationStatsTest {
    private fun asset(name: String): File =
        File("src/main/assets/$name").takeIf { it.isFile } ?: File("app/src/main/assets/$name")

    private fun table(): LeSpeciesTable = LeSpeciesTable.parse(asset("le_species.json").readText())

    private fun firstSpecies(table: LeSpeciesTable): Long =
        (54L..742L).first { table.defaults(it) != null && table.name(it) != null }

    @Test
    fun `level one stats equal the species parameter block`() {
        val table = table()
        val species = firstSpecies(table)
        val defaults = requireNotNull(table.defaults(species))
        val stats = requireNotNull(LeCreationStats(table).statsFor(species, 1))
        assertEquals(1, stats.level)
        assertEquals(defaults.hp, stats.maxHp)
        assertEquals(defaults.mp, stats.maxMp)
        assertEquals(defaults.attack, stats.attack)
        assertEquals(defaults.defense, stats.defense)
        assertEquals(defaults.spirit, stats.spirit)
        assertEquals(defaults.speed.coerceIn(1, 99), stats.speed)
    }

    @Test
    fun `level cap keeps stats inside the Lost Evolution bands`() {
        val table = table()
        val species = firstSpecies(table)
        val provider = LeCreationStats(table)
        val base = requireNotNull(provider.statsFor(species, 1))
        val high = requireNotNull(provider.statsFor(species, 99))
        assertTrue(high.maxHp >= base.maxHp && high.maxHp <= 9999)
        assertTrue(high.maxMp >= base.maxMp && high.maxMp <= 9999)
        assertTrue(high.attack >= base.attack && high.attack <= 999)
        assertTrue(high.defense >= base.defense && high.defense <= 999)
        assertTrue(high.spirit >= base.spirit && high.spirit <= 999)
        assertEquals(base.speed, high.speed)
        assertEquals(99, requireNotNull(provider.statsFor(species, 500)).level)
    }
}
