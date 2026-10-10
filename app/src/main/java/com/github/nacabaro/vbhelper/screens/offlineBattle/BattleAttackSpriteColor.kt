package com.github.nacabaro.vbhelper.screens.offlineBattle

import com.github.nacabaro.vbhelper.battle.offline.core.TechniqueKind
import com.github.nacabaro.vbhelper.battle.offline.core.attackSpriteVariantFor
import kotlin.math.roundToInt

/** Dominant visible hue family; transparent padding, dark outlines and white cores do not wash it out. */
internal fun dominantAttackSpriteColor(pixels: IntArray, fallback: Int): Int {
    val weights = DoubleArray(13)
    val red = DoubleArray(13)
    val green = DoubleArray(13)
    val blue = DoubleArray(13)
    for (pixel in pixels) {
        val alpha = pixel ushr 24
        if (alpha < 32) continue
        val r = (pixel ushr 16) and 255
        val g = (pixel ushr 8) and 255
        val b = pixel and 255
        val maximum = maxOf(r, g, b)
        if (maximum < 32) continue
        val chroma = maximum - minOf(r, g, b)
        val saturation = chroma.toDouble() / maximum
        val bucket = if (chroma < 16 || saturation < 0.12) 12 else {
            val hue = when (maximum) {
                r -> 60.0 * (g-b) / chroma
                g -> 60.0 * ((b-r).toDouble()/chroma + 2.0)
                else -> 60.0 * ((r-g).toDouble()/chroma + 4.0)
            }
            (((hue + 360.0) % 360.0 + 15.0) / 30.0).toInt() % 12
        }
        val weight = alpha / 255.0 * maximum / 255.0 * (0.15 + saturation)
        weights[bucket] += weight
        red[bucket] += r*weight
        green[bucket] += g*weight
        blue[bucket] += b*weight
    }
    val chromatic = (0 until 12).maxByOrNull { weights[it] } ?: 0
    val dominant = if (weights[chromatic] > 0.0) chromatic else 12
    val weight = weights[dominant]
    if (weight <= 0.0) return fallback
    fun channel(sum: Double) = (sum/weight).roundToInt().coerceIn(0, 255)
    return 0xFF000000.toInt() or (channel(red[dominant]) shl 16) or
        (channel(green[dominant]) shl 8) or channel(blue[dominant])
}

internal fun startupAttackSpriteColor(kind: TechniqueKind?, palette: Map<String, Int>, fallback: Int): Int =
    kind?.let(::attackSpriteVariantFor)?.let(palette::get) ?: fallback
