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

internal data class BattleEffectAnchor(val x: Float, val y: Float, val z: Float)

/** Support plane of the rendered model's transformed bounds, including mirrored/turned poses. */
internal fun battleFrontEffectAnchor(
    transform: FloatArray,
    localCenter: FloatArray,
    halfExtent: FloatArray,
    fighterScale: Float,
    cameraYaw: Double,
    cameraPitch: Double,
): BattleEffectAnchor {
    val nx = (sin(cameraYaw) * cos(cameraPitch)).toFloat()
    val ny = sin(cameraPitch).toFloat()
    val nz = (cos(cameraYaw) * cos(cameraPitch)).toFloat()
    val cx = transform[0]*localCenter[0] + transform[4]*localCenter[1] + transform[8]*localCenter[2] + transform[12]
    val cy = transform[1]*localCenter[0] + transform[5]*localCenter[1] + transform[9]*localCenter[2] + transform[13]
    val cz = transform[2]*localCenter[0] + transform[6]*localCenter[1] + transform[10]*localCenter[2] + transform[14]
    val support = kotlin.math.abs(transform[0]*nx + transform[1]*ny + transform[2]*nz)*halfExtent[0] +
        kotlin.math.abs(transform[4]*nx + transform[5]*ny + transform[6]*nz)*halfExtent[1] +
        kotlin.math.abs(transform[8]*nx + transform[9]*ny + transform[10]*nz)*halfExtent[2]
    val distance = support + fighterScale * 0.035f + 0.015f
    return BattleEffectAnchor(cx + nx*distance, cy + ny*distance, cz + nz*distance)
}

/** Lift oversized hit artwork within its camera plane so the floor cannot cut off the burst. */
internal fun battleImpactPlacementFromAnchor(
    anchor: BattleEffectAnchor,
    fighterScale: Float,
    cameraYaw: Double,
    cameraPitch: Double,
    sizeMultiplier: Float = 1.1f,
): BattleImpactPlacement {
    val height = fighterScale * sizeMultiplier
    val lift = (0.045f - (anchor.y - height * 0.5f * cos(cameraPitch).toFloat())).coerceAtLeast(0f)
    val horizontal = kotlin.math.tan(cameraPitch).toFloat() * lift
    return BattleImpactPlacement(anchor.x - sin(cameraYaw).toFloat()*horizontal, anchor.y + lift,
        anchor.z - cos(cameraYaw).toFloat()*horizontal, height)
}

/** Fast expansion on contact, then a short decay; stronger hits carry a larger physical footprint. */
internal fun battleImpactSizeMultiplier(remainingMillis: Long, visibleElapsedMillis: Long, special: Boolean, critical: Boolean): Float {
    val age = maxOf(BATTLE_DAMAGE_INDICATOR_MILLIS - remainingMillis, visibleElapsedMillis).coerceAtLeast(0L)
    if (age >= BATTLE_IMPACT_FLASH_MILLIS) return 0f
    val base = (if (special) 1.55f else 1.1f) + if (critical) 0.2f else 0f
    val expansion = kotlin.math.sin((age / BATTLE_IMPACT_FLASH_MILLIS.toDouble()) * Math.PI).toFloat()
    return base * (1f + expansion * 0.22f)
}

/** A small camera-facing offset clears the victim's extrusion without moving the effect into the foreground. */
internal fun battleImpactPlacement(
    x: Float, z: Float, fighterScale: Float, cameraYaw: Double, cameraPitch: Double,
    sizeMultiplier: Float = 1.1f,
): BattleImpactPlacement {
    val offset = fighterScale * 0.10f
    return BattleImpactPlacement(
        x + (sin(cameraYaw) * cos(cameraPitch) * offset).toFloat(),
        0.025f + fighterScale * 0.5f + (sin(cameraPitch) * offset).toFloat(),
        z + (cos(cameraYaw) * cos(cameraPitch) * offset).toFloat(),
        fighterScale * sizeMultiplier
    )
}
