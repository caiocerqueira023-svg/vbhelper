package com.github.nacabaro.vbhelper.screens.offlineBattle

import com.github.nacabaro.vbhelper.battle.offline.core.BattleImpactSnapshot
import com.github.nacabaro.vbhelper.battle.offline.core.BATTLE_IMPACT_LIFETIME_MILLIS
import kotlin.math.cos
import kotlin.math.sin

internal const val BATTLE_DAMAGE_INDICATOR_MILLIS = BATTLE_IMPACT_LIFETIME_MILLIS
internal const val BATTLE_IMPACT_FLASH_MILLIS = 180L

/** Multiple simultaneous damage hits share a single short flash on each victim. */
internal fun visibleBattleImpactSprites(impacts: List<BattleImpactSnapshot>): List<BattleImpactSnapshot> =
    impacts.filter { it.remainingMillis in (BATTLE_DAMAGE_INDICATOR_MILLIS - BATTLE_IMPACT_FLASH_MILLIS + 1)..BATTLE_DAMAGE_INDICATOR_MILLIS }
        .groupBy { it.targetId }.values.map { hits -> hits.maxBy { it.impactId } }

/** A paused or delayed simulation must not leave a hit sprite stuck over the victim. */
internal fun battleImpactFlashOpacity(remainingMillis: Long, visibleElapsedMillis: Long): Float {
    val age = maxOf(BATTLE_DAMAGE_INDICATOR_MILLIS - remainingMillis, visibleElapsedMillis)
    return if (age < 0) 0f else ((BATTLE_IMPACT_FLASH_MILLIS - age) / 90f).coerceIn(0f, 1f)
}

internal data class BattleImpactPlacement(val x: Float, val y: Float, val z: Float, val height: Float)

/** A small camera-facing offset clears the victim's extrusion without moving the effect into the foreground. */
internal fun battleImpactPlacement(
    x: Float, z: Float, fighterScale: Float, cameraYaw: Double, cameraPitch: Double
): BattleImpactPlacement {
    val offset = fighterScale * 0.10f
    return BattleImpactPlacement(
        x + (sin(cameraYaw) * cos(cameraPitch) * offset).toFloat(),
        0.025f + fighterScale * 0.5f + (sin(cameraPitch) * offset).toFloat(),
        z + (cos(cameraYaw) * cos(cameraPitch) * offset).toFloat(),
        fighterScale * 0.5f
    )
}
