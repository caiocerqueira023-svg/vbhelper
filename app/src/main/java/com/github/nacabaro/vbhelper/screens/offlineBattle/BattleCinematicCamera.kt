package com.github.nacabaro.vbhelper.screens.offlineBattle

import com.github.nacabaro.vbhelper.battle.offline.core.BattleFinisherKind
import com.github.nacabaro.vbhelper.battle.offline.core.BattleFinisherPhase
import com.github.nacabaro.vbhelper.battle.offline.core.BattleFinisherSnapshot
import com.github.nacabaro.vbhelper.battle.offline.core.BattleFinisherTimeline
import com.github.nacabaro.vbhelper.battle.offline.core.BattlePosition
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

internal data class CinematicActorFrame(
    val sourceVisible: Boolean,
    val sourceTransition: Float = 0f,
    val resultVisible: Boolean = false,
    val resultTransition: Float = 0f,
    val mergeProgress: Float = 0f,
    val resultScale: Float = 1f,
)

internal fun cinematicEase(value: Float): Float = value.coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }

/** Localized impact hit-stop affects visuals only; core commit and external-pause ownership stay intact. */
internal fun cinematicVisualSnapshot(sequence: BattleFinisherSnapshot, allowMotion: Boolean = true): BattleFinisherSnapshot {
    if (!allowMotion || sequence.phase != BattleFinisherPhase.IMPACT) return sequence
    val duration = BattleFinisherTimeline.phaseDurationMillis(sequence.kind, BattleFinisherPhase.IMPACT)
    val start = BattleFinisherTimeline.phaseStartMillis(sequence.kind, BattleFinisherPhase.IMPACT)
    val progress = ((sequence.elapsedMillis - start - 150L).toFloat() / (duration - 150L)).coerceIn(0f, 1f)
    return sequence.copy(phaseProgress = progress, elapsedMillis = start + (progress * duration).toLong())
}

/** Visibility is sampled from authoritative phase state, including after recreation or reduced motion. */
internal fun sampleCinematicActors(sequence: BattleFinisherSnapshot, allowMotion: Boolean = true): CinematicActorFrame {
    if (sequence.kind != BattleFinisherKind.FORM && sequence.kind != BattleFinisherKind.JOGRESS) {
        return CinematicActorFrame(sourceVisible = true)
    }
    val p = sequence.phaseProgress
    if (!allowMotion) return when (sequence.phase) {
        BattleFinisherPhase.FOCUS, BattleFinisherPhase.TRANSFORM -> CinematicActorFrame(true)
        BattleFinisherPhase.RESTORE -> CinematicActorFrame(p >= 0.5f, resultVisible = p < 0.5f)
        else -> CinematicActorFrame(false, resultVisible = true, mergeProgress = 1f)
    }
    return when (sequence.phase) {
        BattleFinisherPhase.FOCUS -> CinematicActorFrame(true)
        BattleFinisherPhase.TRANSFORM -> CinematicActorFrame(
            sourceVisible = p < 0.25f,
            sourceTransition = if (p in 0.20f..0.82f) (1f - cinematicEase((p - 0.55f) / 0.27f)) else 0f,
            resultVisible = p >= 0.93f,
            resultTransition = if (p >= 0.72f) cinematicEase((p - 0.72f) / 0.15f) else 0f,
            mergeProgress = cinematicEase((p - 0.25f) / 0.57f),
            resultScale = 0.35f + 0.65f * cinematicEase((p - 0.72f) / 0.28f),
        )
        BattleFinisherPhase.REVEAL -> CinematicActorFrame(false, resultVisible = true,
            resultTransition = 1f - cinematicEase(p / 0.22f), mergeProgress = 1f)
        BattleFinisherPhase.RESTORE -> CinematicActorFrame(
            sourceVisible = p >= 0.72f,
            sourceTransition = if (p in 0.38f..0.85f) sin(((p - 0.38f) / 0.47f) * PI).toFloat() else 0f,
            resultVisible = p < 0.22f,
            resultTransition = if (p < 0.55f) sin((p / 0.55f) * PI).toFloat() else 0f,
            mergeProgress = 1f - cinematicEase((p - 0.38f) / 0.34f),
        )
        else -> CinematicActorFrame(false, resultVisible = true, mergeProgress = 1f)
    }
}

internal data class CinematicCameraPose(
    val targetX: Double,
    val targetZ: Double,
    val targetY: Double,
    val yaw: Double,
    val pitch: Double,
    val distance: Double,
)

internal fun cinematicAnchor(lead: BattlePosition, partner: BattlePosition?, kind: BattleFinisherKind): BattlePosition =
    if (kind == BattleFinisherKind.JOGRESS && partner != null) BattlePosition(
        (lead.x + partner.x) / 2f, (lead.z + partner.z) / 2f) else lead

/** Stage two originals side-by-side in the shot, rather than hiding one behind the other in depth. */
internal fun cinematicFusionSources(lead: BattlePosition, partner: BattlePosition, target: BattlePosition?, height: Float): Pair<BattlePosition, BattlePosition> {
    val anchor = cinematicAnchor(lead, partner, BattleFinisherKind.JOGRESS)
    val dx = (target?.x ?: anchor.x + 2f) - anchor.x
    val dz = (target?.z ?: anchor.z) - anchor.z
    val length = hypot(dx, dz)
    val rightX = if (length > 0.01f) dx / length else 1f
    val rightZ = if (length > 0.01f) dz / length else 0f
    val spacing = height * 0.7f
    return BattlePosition(anchor.x - rightX * spacing, anchor.z - rightZ * spacing) to
        BattlePosition(anchor.x + rightX * spacing, anchor.z + rightZ * spacing)
}

private fun blendCamera(a: CinematicCameraPose, b: CinematicCameraPose, t: Float): CinematicCameraPose {
    val p = cinematicEase(t).toDouble()
    fun mix(x: Double, y: Double) = x + (y - x) * p
    var yawDelta = (b.yaw - a.yaw) % (2 * PI)
    if (yawDelta > PI) yawDelta -= 2 * PI
    if (yawDelta < -PI) yawDelta += 2 * PI
    return CinematicCameraPose(mix(a.targetX, b.targetX), mix(a.targetZ, b.targetZ), mix(a.targetY, b.targetY),
        a.yaw + yawDelta * p, mix(a.pitch, b.pitch), mix(a.distance, b.distance))
}

/** Four authored shots. All samples depend on cinematic time, never Choreographer elapsed time. */
internal fun cinematicCameraPose(
    sequence: BattleFinisherSnapshot,
    lead: BattlePosition,
    partner: BattlePosition?,
    target: BattlePosition?,
    actorHeight: Double,
    aspectRatio: Double,
    saved: CinematicCameraPose,
    allowMotion: Boolean = true,
): CinematicCameraPose {
    if (sequence.phase == BattleFinisherPhase.RESTORE && sequence.phaseProgress >= 1f) return saved
    val anchor = cinematicAnchor(lead, partner, sequence.kind)
    val opponent = target ?: BattlePosition(anchor.x + 2f, anchor.z)
    val dx = (opponent.x - anchor.x).toDouble()
    val dz = (opponent.z - anchor.z).toDouble()
    val nominalYaw = if (hypot(dx, dz) > 0.01) atan2(-dz, dx) else saved.yaw
    fun angularDistance(value: Double): Double = kotlin.math.abs(atan2(sin(value - saved.yaw), cos(value - saved.yaw)))
    // Preserve the current side of the action axis; don't spin around a thin sprite to force a side.
    val yaw = listOf(nominalYaw, nominalYaw + PI).minBy(::angularDistance)
    val height = actorHeight.coerceAtLeast(0.5)
    val aspect = aspectRatio.coerceIn(0.15, 4.0)
    // ModelViewer uses 28mm focal length and a 24mm-high sensor. Fit against usable frame extents.
    val tanHalfFov = 24.0 / 56.0
    fun fit(points: List<BattlePosition>, fraction: Double): CinematicCameraPose {
        val centerX = points.map { it.x.toDouble() }.average()
        val centerZ = points.map { it.z.toDouble() }.average()
        val halfWidth = points.maxOf { kotlin.math.abs((it.x - centerX) * cos(yaw) - (it.z - centerZ) * sin(yaw)) } +
            height * if (fraction > 0.5) 0.55 else 0.8
        val distance = max(height / (2 * tanHalfFov * fraction), halfWidth / (tanHalfFov * aspect * 0.82))
        return CinematicCameraPose(centerX, centerZ, height * 0.53, yaw, 0.25, distance.coerceIn(2.8, 35.0))
    }
    val sourcePoints = if (sequence.kind == BattleFinisherKind.JOGRESS && partner != null) {
        cinematicFusionSources(lead, partner, target, height.toFloat()).let { listOf(it.first, it.second) }
    } else if (sequence.kind == BattleFinisherKind.DUO && partner != null) listOf(lead, partner) else listOf(lead)
    val establish = fit(sourcePoints, 0.42)
    val hero = fit(listOf(anchor), 0.53).copy(yaw = yaw + 0.12, pitch = 0.22)
    val action = fit(listOf(anchor, opponent) + if (sequence.kind == BattleFinisherKind.DUO && partner != null) listOf(partner) else emptyList(), 0.42)
    val reaction = action.copy(targetX = anchor.x * 0.38 + opponent.x * 0.62,
        targetZ = anchor.z * 0.38 + opponent.z * 0.62)
    if (!allowMotion) return if (sequence.phase == BattleFinisherPhase.RESTORE) saved else action
    return when (sequence.phase) {
        BattleFinisherPhase.FOCUS -> blendCamera(saved, establish, sequence.phaseProgress)
        BattleFinisherPhase.TRANSFORM -> blendCamera(establish, hero, (sequence.phaseProgress - 0.6f) / 0.4f)
        BattleFinisherPhase.REVEAL, BattleFinisherPhase.CHARGE -> hero
        BattleFinisherPhase.RELEASE -> blendCamera(hero, action, sequence.phaseProgress / 0.18f)
        BattleFinisherPhase.IMPACT -> blendCamera(action, reaction, (sequence.phaseProgress - 0.20f) / 0.35f)
        BattleFinisherPhase.AFTERMATH -> reaction
        BattleFinisherPhase.RESTORE -> blendCamera(reaction, saved, sequence.phaseProgress)
    }
}
