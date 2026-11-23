package com.tamerslab.transfer.data.gameSave

/**
 * World DS created-at-level stats from the retail engine.
 *
 * Recovered from `analysis/ds1/ghidra/out-arm9` (Ghidra export of the USA ARM9):
 *  - `FUN_020a1c70` creates the record with the species base block (`func_020a29c4`), reads the
 *    level, and scales each stat by `(level * growth + 100000) / 100000`, where the growth row is
 *    selected by the record's growth index (species dm +0x2A) from the 14-byte table at
 *    RAM 0x0210AEC8 (`DAT_020a1f1c`).
 *  - Row order: attack, defense, spirit, speed, HP, MP, max-level/aptitude.
 *  - `DAT_020a1f20 = 100000` and `DAT_020a1f24 = 0x14F8B589` implement the fixed-point 1/100000
 *    (`>> 45`); clamps are `999` for combat stats and `9999` for HP/MP.
 *
 * The engine applies the factor with integer truncation; this provider does the same arithmetic.
 */
internal class WorldDsCreationStats(private val table: WorldDsSpeciesTable) : NativeCreationStats {
    override fun statsFor(speciesId: Long, level: Int): CreationStats? {
        val species = table.creation(speciesId) ?: return null
        val target = level.coerceIn(1, 99)
        val row = GROWTH_ROWS[species.growth.coerceIn(0, 6)]

        fun scaled(base: Int, growth: Int, max: Int): Int {
            val factor = target.toLong() * growth + 100_000L
            val scaled = (base.toLong() * factor / 100_000L).toInt()
            return scaled.coerceIn(1, max)
        }

        val maxHp = scaled(species.baseHp, row[4], 9999)
        val maxMp = scaled(species.baseMp, row[5], 9999)
        return CreationStats(
            level = target,
            hp = maxHp,
            maxHp = maxHp,
            mp = maxMp,
            maxMp = maxMp,
            attack = scaled(species.baseAttack, row[0], 999),
            defense = scaled(species.baseDefense, row[1], 999),
            spirit = scaled(species.baseSpirit, row[2], 999),
            speed = scaled(species.baseSpeed, row[3], 999),
            aptitude = null,
        )
    }

    companion object {
        /** Growth rows dumped from `DAT_020a1f1c = 0x0210AEC8`, stride 14 (seven s16). */
        private val GROWTH_ROWS: List<List<Int>> = listOf(
            listOf(25, 25, 30, 25, 25, 25, 15),
            listOf(35, 25, 25, 15, 30, 25, 15),
            listOf(25, 35, 15, 25, 30, 25, 15),
            listOf(25, 15, 35, 25, 25, 30, 15),
            listOf(15, 25, 25, 35, 25, 25, 15),
            listOf(30, 25, 25, 25, 35, 15, 15),
            listOf(25, 15, 30, 25, 25, 35, 15),
            listOf(0, 28, 0, 38, 0, 48, 0),
        )
    }
}
