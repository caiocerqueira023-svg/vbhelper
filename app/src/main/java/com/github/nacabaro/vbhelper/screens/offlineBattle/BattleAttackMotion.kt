package com.github.nacabaro.vbhelper.screens.offlineBattle

import com.github.nacabaro.vbhelper.battle.offline.core.BattlePosition
import com.github.nacabaro.vbhelper.rendering.sprite3d.ResidentFrameImage
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

internal data class BattleAttackFacing(val scaleX: Float, val rotationDegrees: Float)

/** Both standard small and large DiM attack sprites are authored facing left. */
internal const val BATTLE_ATTACK_ART_FACES_LEFT = true

/** Facing uses the current travel vector and the explicit art convention, never a previous transform. */
internal fun screenAttackFacing(dx: Float, dy: Float, authoredFacingLeft: Boolean = false): BattleAttackFacing {
    if (abs(dx) + abs(dy) < 0.0001f) return BattleAttackFacing(1f, 0f)
    val sign = if (dx < -0.0001f) -1f else 1f
    return BattleAttackFacing(sign * if (authoredFacingLeft) -1f else 1f,
        Math.toDegrees(atan2((dy * sign).toDouble(), abs(dx).toDouble())).toFloat())
}

internal fun worldAttackFacing(dx: Float, dz: Float, cameraYaw: Double, cameraPitch: Double,
    authoredFacingLeft: Boolean = false): BattleAttackFacing {
    val right = (dx * cos(cameraYaw) - dz * sin(cameraYaw)).toFloat()
    val up = (-(dx * sin(cameraYaw) + dz * cos(cameraYaw)) * sin(cameraPitch)).toFloat()
    return screenAttackFacing(right, up, authoredFacingLeft)
}

/** A short anticipation, then acceleration all the way into impact; no ease-out at contact. */
internal fun cinematicLaunchProgress(phaseProgress: Float, shotIndex: Int = 0): Float {
    val launchAt = 0.15f + shotIndex.coerceIn(0, 2) * 0.12f
    val t = ((phaseProgress - launchAt) / (1f - launchAt)).coerceIn(0f, 1f)
    return t * t
}

internal fun cinematicAttackPoint(from: BattlePosition, to: BattlePosition, progress: Float) = BattlePosition(
    from.x + (to.x - from.x) * progress.coerceIn(0f, 1f),
    from.z + (to.z - from.z) * progress.coerceIn(0f, 1f),
)

/** Remove transparent sprite-sheet padding at preparation time, preserving all visible pixels. */
internal fun cropBattleImpactFrame(frame: ResidentFrameImage): ResidentFrameImage {
    var minX = frame.width
    var minY = frame.height
    var maxX = -1
    var maxY = -1
    for (y in 0 until frame.height) for (x in 0 until frame.width) {
        if (frame.argb[y * frame.width + x] ushr 24 != 0) {
            minX = minOf(minX, x); minY = minOf(minY, y)
            maxX = maxOf(maxX, x); maxY = maxOf(maxY, y)
        }
    }
    if (maxX < 0) return frame
    minX = (minX - 1).coerceAtLeast(0); minY = (minY - 1).coerceAtLeast(0)
    maxX = (maxX + 1).coerceAtMost(frame.width - 1); maxY = (maxY + 1).coerceAtMost(frame.height - 1)
    val width = maxX - minX + 1
    val height = maxY - minY + 1
    if (width == frame.width && height == frame.height) return frame
    return ResidentFrameImage(IntArray(width * height) { index ->
        frame.argb[(minY + index / width) * frame.width + minX + index % width]
    }, width, height)
}
