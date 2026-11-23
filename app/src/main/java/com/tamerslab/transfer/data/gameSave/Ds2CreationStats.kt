package com.tamerslab.transfer.data.gameSave

/**
 * Dawn/Dusk created-at-level stats, computed from the game's own data and engine formula.
 *
 * Recovered from the retail ARM9:
 *  - `FUN_020558e8` adds six per-level gains from the growth row selected by `species.growth`
 *    (12 bytes = six low/high pairs: HP, MP, ATK, DEF, SPI, SPD). A gain is
 *    `floor((rand(1..low) + high) / 10)` — `DAT_02055b10 = 0x66666667` is the fixed-point 1/10,
 *    shifted by 34.
 *  - `DAT_02055b14 = 9999` clamps HP/MP and `DAT_02055b18 = 999` clamps the other four stats.
 *  - Species base stats (`Ds2SpeciesDefaults`) are the values at the record's base level.
 *
 * The game's gain is random per level, so a synthetic record uses the deterministic expectation of
 * that formula (mean of the random range), which is the stats such a record normally has.
 */
internal class Ds2CreationStats(private val table: Ds2SpeciesTable) : NativeCreationStats {
    override fun statsFor(speciesId: Long, level: Int): CreationStats? {
        val defaults = table.defaults(speciesId) ?: return null
        val target = level.coerceIn(1, defaults.maxLevel.coerceAtLeast(1))
        val levels = (target - defaults.baseLevel).coerceAtLeast(0)
        val growth = defaults.growth.coerceIn(0, GROWTH_ROWS.lastIndex)

        fun gained(pair: Int): Int {
            val row = GROWTH_ROWS[growth]
            val low = row[pair * 2].toDouble()
            val high = row[pair * 2 + 1].toDouble()
            // Mean of rand(1..low) plus the row's flat high byte, divided by the engine's 1/10 scale.
            val expectedPerLevel = ((low + 1.0) / 2.0 + high) / 10.0
            return Math.round(expectedPerLevel * levels).toInt()
        }

        val maxHp = (defaults.baseHp + gained(0)).coerceIn(1, 9999)
        val maxMp = (defaults.baseMp + gained(1)).coerceIn(0, 9999)
        val attack = (defaults.baseAttack + gained(2)).coerceIn(1, 999)
        val defense = (defaults.baseDefense + gained(3)).coerceIn(1, 999)
        val spirit = (defaults.baseSpirit + gained(4)).coerceIn(1, 999)
        val speed = (defaults.baseSpeed + gained(5)).coerceIn(1, 999)
        return CreationStats(
            level = target,
            hp = maxHp,
            maxHp = maxHp,
            mp = maxMp,
            maxMp = maxMp,
            attack = attack,
            defense = defense,
            spirit = spirit,
            speed = speed,
            aptitude = null,
        )
    }

    companion object {
        /** The six growth rows extracted from the Dawn/Dusk ARM9 (`DAT_02055b0c = 0x0211E298`). */
        private val GROWTH_ROWS: List<List<Int>> = listOf(
            listOf(60, 90, 45, 75, 12, 14, 12, 14, 12, 14, 11, 16),
            listOf(75, 85, 55, 65, 14, 20, 12, 15, 10, 14, 14, 13),
            listOf(55, 125, 30, 85, 11, 15, 13, 21, 12, 10, 14, 14),
            listOf(45, 100, 40, 100, 11, 13, 11, 13, 12, 21, 11, 17),
            listOf(50, 90, 40, 75, 10, 12, 10, 15, 12, 18, 12, 22),
            listOf(60, 150, 45, 65, 9, 17, 15, 15, 10, 15, 13, 15),
        )
    }
}
