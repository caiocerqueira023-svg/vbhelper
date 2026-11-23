package com.tamerslab.transfer.data.gameSave

import com.tamerslab.domain.transfer.ProgressDomainRules

/**
 * First-send bootstrap policy: a Digimon that already has app/VB progress but no story block gets one
 * story block the first time it enters a story game. The level half is settled (the owner's reference:
 * ultimate with 7500 vital points → level 75); the stat half must be the values the game itself gives
 * that species at that level, so it is supplied per title by [NativeCreationStats].
 *
 * The Dawn/Dusk engine formula has been recovered from the decompiled ARM9 and is recorded here for
 * the provider implementation:
 *  - `FUN_02055bd0(record, expChannels)` applies level-ups: it sums the eight EXP channels, asks the
 *    EXP curve (`FUN_02055860`) how many levels were reached, then calls `FUN_020558e8` once per level.
 *  - `FUN_020558e8(record, out)` computes the six stat gains per level from the species growth row
 *    (`growthIndex` at record +0x1c selects a 12-byte row: six low/high byte pairs for HP, MP, ATK,
 *    DEF, SPI, SPD). Each pair is `random(1..lowByte) + highByte`, scaled by the fixed-point constant
 *    `DAT_02055b10` (`>> 34`), added to the current stat, and clamped (HP/MP max `DAT_02055b14`, other
 *    stats max `DAT_02055b18`). `FUN_02082090` is the RNG (`seed = seed * 7 + 5`, returns 16-bit).
 *
 * Remaining extraction work for the provider: the growth-row table address/value (`DAT_02055b0c`), the
 * fixed-point constant (`DAT_02055b10`), the clamp constants (`DAT_02055b14`, `DAT_02055b18`), and the
 * species base records. Until then the bootstrap deliberately refuses to invent stats.
 */
internal object StoryBootstrapPolicy {
    /** Level a story block starts at when it is seeded from the app/VB vital points. */
    fun levelFor(vitalPoints: Int): Int = ProgressDomainRules.levelForVitalPoints(vitalPoints)

    /** True when the provider can supply real created-at-level stats for this title/species. */
    fun canBootstrap(stats: NativeCreationStats?): Boolean = stats != null
}

/** The stats the target game itself gives a species when created at [level]. */
internal interface NativeCreationStats {
    fun statsFor(speciesId: Long, level: Int): CreationStats?
}

internal data class CreationStats(
    val level: Int,
    val hp: Int,
    val maxHp: Int,
    val mp: Int,
    val maxMp: Int,
    val attack: Int,
    val defense: Int,
    val spirit: Int,
    val speed: Int,
    val aptitude: Int? = null,
)
