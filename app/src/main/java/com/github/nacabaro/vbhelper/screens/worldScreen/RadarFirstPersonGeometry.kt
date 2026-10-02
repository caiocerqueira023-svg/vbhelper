package com.github.nacabaro.vbhelper.screens.worldScreen

import com.github.nacabaro.vbhelper.world.RadarRelativePosition
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.cos
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RadarScenePoint(val x: Float, val z: Float)

fun radarPresentationDistance(distanceMeters: Double): Double {
    require(distanceMeters.isFinite())
    val d = distanceMeters.coerceIn(0.0, 1000.0)
    return if (d <= 40.0) d / 10.0 else 4.0 + 36.0 * ln(1.0 + (d - 40.0) / 160.0) / ln(7.0)
}

fun RadarRelativePosition.scenePoint(): RadarScenePoint {
    val units = radarPresentationDistance(distanceMeters)
    val radians = Math.toRadians(bearingDegrees)
    return RadarScenePoint((units * sin(radians)).toFloat(), (-units * cos(radians)).toFloat())
}

/** Rendering and hit-target projection must share the camera-clipping workaround. */
fun RadarRelativePosition.firstPersonDisplayPoint(): RadarScenePoint {
    val units = radarPresentationDistance(distanceMeters).coerceAtLeast(1.8)
    val radians = Math.toRadians(bearingDegrees)
    return RadarScenePoint((units * sin(radians)).toFloat(), (-units * cos(radians)).toFloat())
}

internal const val RADAR_DEFAULT_PITCH = -12f
internal data class RadarCameraPose(val eyeHeight: Double, val targetX: Double, val targetY: Double, val targetZ: Double)

internal fun radarFirstPersonCamera(headingDegrees: Float, pitchDegrees: Float): RadarCameraPose {
    val yaw = Math.toRadians(headingDegrees.toDouble())
    val pitch = Math.toRadians(pitchDegrees.coerceIn(-60f, 45f).toDouble())
    val eye = 0.85
    return RadarCameraPose(eye, sin(yaw) * cos(pitch), eye + sin(pitch), -cos(yaw) * cos(pitch))
}

internal fun radarPitchAfterDrag(pitch: Float, deltaY: Float, viewportHeight: Float): Float =
    (pitch + deltaY / viewportHeight.coerceAtLeast(1f) * 90f).coerceIn(-60f, 45f)

internal fun radarPitchStep(pitch: Float, lookingUp: Boolean): Float =
    (pitch+if(lookingUp)15f else -15f).coerceIn(-60f,45f)

internal fun radarFirstPersonSpriteScale(stage: Int): Float = when (stage) {
    0 -> 0.85f
    1 -> 1.05f
    else -> 1.4f
}

/** Presentation spacing keeps meeting participants individually visible and tappable. */
internal fun radarInteractionOffset(slot: Int, count: Int, spacing: Float): Float =
    if(count<2 || slot !in 0 until count) 0f else (slot-(count-1)/2f)*spacing

internal fun radarActivityLunge(frameNanos: Long, slot: Int, motion: Boolean): Float {
    if(!motion) return 0f
    val cycle=(frameNanos%3_600_000_000L)/1_800_000_000.0
    if(cycle.toInt()!=slot%2) return 0f
    return sin((cycle%1.0)*Math.PI).toFloat().coerceIn(0f,1f)
}

internal fun RadarRelativePosition.activityDisplayPoint(slot: Int, count: Int, lunge: Float): RadarScenePoint {
    val base=firstPersonDisplayPoint()
    val bearing=Math.toRadians(bearingDegrees)
    val separation=radarInteractionOffset(slot,count,1.5f)
    val towardsPartner=if(separation<0) 1f else -1f
    val offset=separation+if(count==2) towardsPartner*lunge*0.22f else 0f
    return RadarScenePoint(base.x+(cos(bearing)*offset).toFloat(),base.z+(sin(bearing)*offset).toFloat())
}

internal fun radarSpritePose(walking: Boolean, motion: Boolean, alternate: Boolean, available: Set<String>): String? {
    val base = if (walking && motion) "walk" else "idle"
    return listOfNotNull(if (motion && alternate) "${base}2" else null, base, "idle", "idle2")
        .firstOrNull { it in available }
}

enum class RadarRendererOwner { NONE, FIRST_PERSON, BATTLE }

data class RadarRendererState(
    val owner: RadarRendererOwner = RadarRendererOwner.NONE,
    val requested: RadarRendererOwner = RadarRendererOwner.NONE,
    val generation: Long = 0,
    val releasing: Boolean = false
)

/** A generation-stamped release handshake, including rapid toggles and the reverse transition. */
class RadarRendererOwnership {
    private val _state = MutableStateFlow(RadarRendererState())
    val state = _state.asStateFlow()
    val owner get() = state.value.owner
    val requested get() = state.value.requested
    val generation get() = state.value.generation
    val releasing get() = state.value.releasing
    fun request(next: RadarRendererOwner): Long {
        val previous = state.value
        _state.value = previous.copy(
            requested = next,
            owner = if (previous.owner == RadarRendererOwner.NONE) next else previous.owner,
            generation = previous.generation + if (previous.owner == RadarRendererOwner.NONE && next != RadarRendererOwner.NONE) 1 else 0,
            releasing = previous.releasing || previous.owner != RadarRendererOwner.NONE && previous.owner != next
        )
        return generation
    }
    fun released(releasingOwner: RadarRendererOwner, token: Long): Boolean {
        if (owner != releasingOwner || token != generation) return false
        _state.value = state.value.copy(owner = requested, releasing = false,
            generation = generation + if (requested != RadarRendererOwner.NONE) 1 else 0)
        return true
    }
}
