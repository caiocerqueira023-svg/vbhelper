package com.github.nacabaro.vbhelper.battle.offline.data

/** Frozen source references; changing them requires a ruleset version bump and balance review. */
object OfflineBattleRuleset {
    const val VERSION = 2

    /**
     * Stage sets non-overlapping tempo bands. HP/BP/AP select a position inside
     * the band, so a tank can be slow for its stage without becoming slower
     * than every combatant in the preceding stage.
     */
    const val STAT_COMPRESSION = 0.45f
    const val MOVEMENT_STAGE_ZERO_MIN = 2.34f
    const val MOVEMENT_STAGE_STEP = 0.14f
    const val MOVEMENT_BAND_WIDTH = 0.12f
    const val COOLDOWN_STAGE_ZERO_CENTER = 1.10f
    const val COOLDOWN_STAGE_STEP = 0.05f
    const val COOLDOWN_BAND_HALF_WIDTH = 0.02f
    const val ENERGY_REGEN_STAGE_ZERO = 2.25f
    const val ENERGY_REGEN_STAGE_STEP = 0.25f

    /** Median HP/AP/BP values by extracted source phase, from the bundled 1,410-record catalog. */
    val arenaExtractedMediansByPhase: List<BattleStatReference?> = listOf(
        null,
        null,
        BattleStatReference(bp = 2_400f, hp = 1_800f, ap = 700f),
        BattleStatReference(bp = 3_000f, hp = 2_000f, ap = 900f),
        BattleStatReference(bp = 2_450f, hp = 2_200f, ap = 1_000f),
        BattleStatReference(bp = 4_250f, hp = 3_080f, ap = 1_120f)
    )
}

data class BattleStatReference(val bp: Float, val hp: Float, val ap: Float)
