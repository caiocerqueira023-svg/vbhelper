package com.github.nacabaro.vbhelper.screens.worldScreen

import kotlin.math.roundToInt

internal data class RadarOverheadPosition(val x: Int, val y: Int)

/** Text's measured height determines its baseline; a sprite hit box never constrains it. */
internal fun radarOverheadPosition(centerX: Float, spriteTop: Float, width: Int, height: Int,
    viewportWidth: Int, margin: Int, gap: Int): RadarOverheadPosition? {
    if(!centerX.isFinite() || !spriteTop.isFinite() || width<=0 || height<=0 ||
        width>viewportWidth-2*margin || spriteTop-height-gap<margin) return null
    return RadarOverheadPosition((centerX-width/2f).roundToInt().coerceIn(margin,viewportWidth-margin-width),
        (spriteTop-height-gap).roundToInt())
}
