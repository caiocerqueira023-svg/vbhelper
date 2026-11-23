package com.tamerslab.transfer.data.gameSave

/**
 * Lost Evolution created-at-level stats from the game's own data and level-up code.
 *
 * Recovered from the retail ARM9 (`analysis/dsle/ghidra/out-arm9`):
 *  - `FUN_020b0b20` adds five per-level gains (HP, MP, ATK, DEF, SPI; speed is not grown) using the
 *    growth row selected by `species.growthBits & 7` (`FUN_020cf848`, table `DAT_020cf870`).
 *  - A pair is (base byte, random range byte): gain = `floor((base + rand(1..range)) / 10)`, with
 *    `DAT_020b0d48 = 0x66666667` as the fixed-point 1/10 (`>> 34`).
 *  - HP/MP clamp to `DAT_020b0d4c = 9999`, combat stats to `DAT_020b0d50 = 999`; speed stays at the
 *    species value and is capped at 99.
 *
 * The game's gain is random, so the provider uses the deterministic expectation of the same formula.
 */
internal class LeCreationStats(private val table: LeSpeciesTable) : NativeCreationStats {
    override fun statsFor(speciesId: Long, level: Int): CreationStats? {
        val defaults = table.defaults(speciesId) ?: return null
        val target = level.coerceIn(1, 99)
        val levels = (target - 1).coerceAtLeast(0)
        val growth = (defaults.growthBits and 7).coerceIn(0, GROWTH_ROWS.lastIndex)

        fun gained(pair: Int): Int {
            val row = GROWTH_ROWS[growth]
            val base = row[pair * 2].toDouble()
            val range = row[pair * 2 + 1].toDouble()
            val expectedPerLevel = (base + (range + 1.0) / 2.0) / 10.0
            return Math.round(expectedPerLevel * levels).toInt()
        }

        val maxHp = (defaults.hp + gained(0)).coerceIn(1, 9999)
        val maxMp = (defaults.mp + gained(1)).coerceIn(0, 9999)
        return CreationStats(
            level = target,
            hp = maxHp,
            maxHp = maxHp,
            mp = maxMp,
            maxMp = maxMp,
            attack = (defaults.attack + gained(2)).coerceIn(1, 999),
            defense = (defaults.defense + gained(3)).coerceIn(1, 999),
            spirit = (defaults.spirit + gained(4)).coerceIn(1, 999),
            speed = defaults.speed.coerceIn(1, 99),
            aptitude = null,
        )
    }

    companion object {
        /** Dumped from the retail ARM9 growth pairs (`DAT_020cf870 = 0x02142258`). */
        private val GROWTH_ROWS: List<List<Int>> = listOf(
            listOf(90, 60, 75, 45, 14, 12, 14, 12, 16, 11),
            listOf(150, 60, 65, 45, 17, 9, 15, 15, 15, 13),
            listOf(95, 50, 120, 55, 14, 10, 14, 9, 18, 8),
            listOf(85, 75, 65, 55, 20, 14, 15, 12, 13, 14),
            listOf(125, 55, 85, 30, 15, 11, 21, 13, 14, 14),
            listOf(90, 50, 75, 40, 12, 10, 15, 10, 22, 12),
        )
    }
}
