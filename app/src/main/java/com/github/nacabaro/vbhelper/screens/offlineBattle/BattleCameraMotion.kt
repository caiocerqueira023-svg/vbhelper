package com.github.nacabaro.vbhelper.screens.offlineBattle

import com.github.nacabaro.vbhelper.battle.offline.core.BattleImpactSnapshot
import com.github.nacabaro.vbhelper.battle.offline.core.BattlePosition
import com.github.nacabaro.vbhelper.battle.offline.core.CombatantState
import com.github.nacabaro.vbhelper.rendering.approachSceneAxis
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

internal data class BattleCameraCue(
    val fighterId: String,
    val position: BattlePosition,
    val state: CombatantState,
)

internal data class BattleCameraFocus(val fighterId: String, val weight: Float)

internal data class BattleCameraTarget(val x: Float, val z: Float)

internal data class BattleCameraShake(val x: Double, val z: Double) {
    fun magnitude(): Double = hypot(x, z)
}

internal fun chooseBattleCameraFocus(
    manualFighterId: String?,
    cues: List<BattleCameraCue>,
    impacts: List<BattleImpactSnapshot>,
): BattleCameraFocus? {
    manualFighterId?.takeIf { id -> cues.any { it.fighterId == id } }?.let {
        return BattleCameraFocus(it, 1f)
    }
    impacts.maxByOrNull { it.impactId }?.targetId
        ?.takeIf { id -> cues.any { it.fighterId == id } }
        ?.let { return BattleCameraFocus(it, 0.52f) }
    cues.firstOrNull { it.state == CombatantState.USING_SPECIAL }?.let {
        return BattleCameraFocus(it.fighterId, 0.46f)
    }
    cues.firstOrNull { it.state == CombatantState.ATTACK_ACTIVE }?.let {
        return BattleCameraFocus(it.fighterId, 0.38f)
    }
    cues.firstOrNull { it.state == CombatantState.ATTACK_STARTUP }?.let {
        return BattleCameraFocus(it.fighterId, 0.32f)
    }
    return null
}

internal fun chooseBattleCameraTarget(
    manualFighterId: String?,
    cues: List<BattleCameraCue>,
    impacts: List<BattleImpactSnapshot>,
    initialFocusFighterId: String? = null,
): BattleCameraTarget {
    val manualFocus = manualFighterId?.let { id ->
        cues.firstOrNull { it.fighterId == id && it.state != CombatantState.DEFEATED }
    }
    manualFocus?.let { return BattleCameraTarget(it.position.x, it.position.z) }

    // Keep team battles anchored on the opening Digimon. Action/impact cues
    // should not pull the camera away every time a hit is exchanged.
    val initialFocus = initialFocusFighterId?.let { id ->
        cues.firstOrNull { it.fighterId == id && it.state != CombatantState.DEFEATED }
    }
    initialFocus?.let { return BattleCameraTarget(it.position.x, it.position.z) }

    if (cues.size == 2) {
        return BattleCameraTarget(
            x = (cues[0].position.x + cues[1].position.x) * 0.5f,
            z = (cues[0].position.z + cues[1].position.z) * 0.5f,
        )
    }
    val focus = chooseBattleCameraFocus(null, cues, impacts)
        ?: return BattleCameraTarget(0f, 0f)
    val fighter = cues.firstOrNull { it.fighterId == focus.fighterId }
        ?: return BattleCameraTarget(0f, 0f)
    return BattleCameraTarget(
        x = fighter.position.x * focus.weight,
        z = fighter.position.z * focus.weight,
    )
}

internal fun advanceBattleCameraYaw(
    currentYaw: Double,
    deltaSeconds: Double,
    automatic: Boolean,
): Double = advanceBattleCameraYaw(currentYaw, deltaSeconds, if (automatic) 1.0 else 0.0)

/** Smoothly ramps back to the authored orbit after manual camera input. */
internal fun advanceBattleCameraYaw(
    currentYaw: Double,
    deltaSeconds: Double,
    orbitBlend: Double,
): Double {
    val progress = orbitBlend.coerceIn(0.0, 1.0)
    val easedBlend = progress * progress * (3.0 - 2.0 * progress)
    return currentYaw + deltaSeconds.coerceIn(0.0, 0.1) * AUTO_ORBIT_RADIANS_PER_SECOND * easedBlend
}

internal fun approachCameraAxis(current: Double, target: Double, deltaSeconds: Double): Double =
    approachSceneAxis(current, target, deltaSeconds, responsiveness = 5.0)

internal fun battleImpactShake(
    impactId: Long,
    remainingMillis: Long,
    critical: Boolean,
): BattleCameraShake {
    if (remainingMillis <= 0L) return BattleCameraShake(0.0, 0.0)
    val remaining = (remainingMillis / IMPACT_DURATION_MILLIS.toDouble()).coerceIn(0.0, 1.0)
    val elapsed = 1.0 - remaining
    val amplitude = (if (critical) 0.16 else 0.075) * remaining * remaining
    val phase = elapsed * PI * 7.0 + (impactId % 7L) * 0.53
    return BattleCameraShake(
        x = sin(phase) * amplitude,
        z = cos(phase * 1.17) * amplitude * 0.72,
    )
}

private const val IMPACT_DURATION_MILLIS = 320L
private const val AUTO_ORBIT_RADIANS_PER_SECOND = 0.055
