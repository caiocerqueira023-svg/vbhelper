package com.github.nacabaro.vbhelper.screens.offlineBattle

internal data class BattleStartupVisual(
    val orbSize: Float,
    val orbOpacity: Float,
    val floorSize: Float,
    val floorOpacity: Float,
    val particleCount: Int,
)

/** Shared cinematic vocabulary, reduced to a small gathering cue for ordinary attack wind-up. */
internal fun battleStartupVisual(progress: Float, special: Boolean, allowMotion: Boolean): BattleStartupVisual {
    val p = if (allowMotion && progress.isFinite()) progress.coerceIn(0f, 1f) else 0.65f
    val eased = cinematicEase(p)
    return BattleStartupVisual(
        orbSize = (if (special) 0.17f else 0.11f) + (if (special) 0.26f else 0.19f)*eased,
        orbOpacity = 0.28f + 0.50f*eased,
        floorSize = (if (special) 1.1f else 0.85f) - 0.16f*eased,
        floorOpacity = 0.10f + 0.12f*eased,
        particleCount = if (!allowMotion) 0 else if (special) 8 else 5,
    )
}
