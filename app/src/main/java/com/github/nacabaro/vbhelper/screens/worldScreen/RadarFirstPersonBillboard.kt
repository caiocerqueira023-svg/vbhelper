package com.github.nacabaro.vbhelper.screens.worldScreen

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** FP-only point-facing billboard. The sprite front (+Z) points at the player's eye. */
internal data class RadarFirstPersonBillboard(
    val position: RadarScenePoint,
    val centerY: Float,
    val yawRadians: Float,
    val pitchRadians: Float
) {
    /** Same rotated right/up basis as the mesh, for labels and body hit targets. */
    fun worldPoint(horizontal: Float, vertical: Float): FloatArray {
        val yaw=yawRadians.toDouble()
        val pitch=pitchRadians.toDouble()
        return floatArrayOf(
            position.x+(horizontal*cos(yaw)+vertical*sin(yaw)*sin(pitch)).toFloat(),
            centerY+(vertical*cos(pitch)).toFloat(),
            position.z+(-horizontal*sin(yaw)+vertical*cos(yaw)*sin(pitch)).toFloat()
        )
    }
}

internal fun radarFirstPersonBillboard(position: RadarScenePoint, spriteHeight: Float, eyeHeight: Double): RadarFirstPersonBillboard {
    require(position.x.isFinite() && position.z.isFinite() && spriteHeight.isFinite() && spriteHeight>0f && eyeHeight.isFinite())
    val centerY=0.02f+spriteHeight/2f
    val distance=sqrt(position.x.toDouble()*position.x+position.z.toDouble()*position.z)
    val yaw=if(distance<0.00001) 0f else atan2(-position.x.toDouble(),-position.z.toDouble()).toFloat()
    val pitch=atan2(centerY-eyeHeight,distance).toFloat()
    return RadarFirstPersonBillboard(position,centerY,yaw,pitch)
}
