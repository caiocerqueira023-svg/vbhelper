package com.tamerslab.transfer.data.gameSave

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Ds2CreationStatsTest {
    private fun asset(name: String): File =
        File("src/main/assets/$name").takeIf { it.isFile } ?: File("app/src/main/assets/$name")

    private fun table(): Ds2SpeciesTable = Ds2SpeciesTable.parse(asset("ds2_species.json").readText())

    private fun firstSpecies(table: Ds2SpeciesTable): Long =
        (0x41L..0x1F4L).first { table.defaults(it) != null && table.name(it) != null }

    @Test
    fun `base level stats equal the species record base stats`() {
        val table = table()
        val species = firstSpecies(table)
        val defaults = requireNotNull(table.defaults(species))
        val stats = requireNotNull(Ds2CreationStats(table).statsFor(species, defaults.baseLevel))
        assertEquals(defaults.baseLevel, stats.level)
        assertEquals(defaults.baseHp, stats.maxHp)
        assertEquals(defaults.baseMp, stats.maxMp)
        assertEquals(defaults.baseAttack, stats.attack)
        assertEquals(defaults.baseDefense, stats.defense)
        assertEquals(defaults.baseSpirit, stats.spirit)
        assertEquals(defaults.baseSpeed, stats.speed)
    }

    @Test
    fun `created-at-level stats grow from the base record inside the engine clamps`() {
        val table = table()
        val species = firstSpecies(table)
        val defaults = requireNotNull(table.defaults(species))
        val provider = Ds2CreationStats(table)
        val base = requireNotNull(provider.statsFor(species, defaults.baseLevel))
        val high = requireNotNull(provider.statsFor(species, defaults.maxLevel))
        assertTrue(high.maxHp >= base.maxHp)
        assertTrue(high.maxMp >= base.maxMp)
        assertTrue(high.attack >= base.attack)
        assertTrue(high.defense >= base.defense)
        assertTrue(high.spirit >= base.spirit)
        assertTrue(high.speed >= base.speed)
        assertTrue(high.maxHp <= 9999)
        assertTrue(high.maxMp <= 9999)
        assertTrue(high.attack <= 999 && high.defense <= 999 && high.spirit <= 999 && high.speed <= 999)
    }

    @Test
    fun `levels outside the species range clamp to its own cap`() {
        val table = table()
        val species = firstSpecies(table)
        val defaults = requireNotNull(table.defaults(species))
        val stats = requireNotNull(Ds2CreationStats(table).statsFor(species, 500))
        assertEquals(defaults.maxLevel, stats.level)
    }
}
