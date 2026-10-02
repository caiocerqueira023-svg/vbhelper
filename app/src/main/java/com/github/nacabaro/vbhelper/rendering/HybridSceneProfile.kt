package com.github.nacabaro.vbhelper.rendering

import com.google.android.filament.View

internal enum class HybridSceneKind { BATTLE, DIGIFARM, RADAR_FP }

internal data class HybridSceneProfile(
    val bloomStrength: Float,
    val ambientOcclusionEnabled: Boolean,
    val ambientOcclusionRadius: Float,
    val ambientOcclusionPower: Float,
    val ambientOcclusionIntensity: Float,
    val temporalAntiAliasingEnabled: Boolean = false,
)

internal fun hybridSceneProfile(kind: HybridSceneKind): HybridSceneProfile = when (kind) {
    HybridSceneKind.BATTLE -> HybridSceneProfile(
        bloomStrength = 0.34f,
        ambientOcclusionEnabled = false,
        ambientOcclusionRadius = 0.72f,
        ambientOcclusionPower = 1.15f,
        ambientOcclusionIntensity = 0.68f,
    )
    HybridSceneKind.DIGIFARM -> HybridSceneProfile(
        bloomStrength = 0.52f,
        ambientOcclusionEnabled = false,
        ambientOcclusionRadius = 0.18f,
        ambientOcclusionPower = 1.08f,
        ambientOcclusionIntensity = 0.58f,
    )
    HybridSceneKind.RADAR_FP -> HybridSceneProfile(0.22f, false, 0.18f, 1f, 0.5f)
}

/**
 * Keeps sprite textures crisp (no temporal accumulation) while adding restrained
 * depth cues to the 3D geometry around them.
 */
internal fun View.applyHybridSceneProfile(kind: HybridSceneKind) {
    val profile = hybridSceneProfile(kind)
    setPostProcessingEnabled(true)
    setAntiAliasing(View.AntiAliasing.FXAA)
    setDithering(View.Dithering.TEMPORAL)
    temporalAntiAliasingOptions = temporalAntiAliasingOptions.apply {
        enabled = profile.temporalAntiAliasingEnabled
    }
    ambientOcclusionOptions = ambientOcclusionOptions.apply {
        enabled = profile.ambientOcclusionEnabled
        radius = profile.ambientOcclusionRadius
        power = profile.ambientOcclusionPower
        intensity = profile.ambientOcclusionIntensity
        resolution = 0.5f
        quality = View.QualityLevel.MEDIUM
        lowPassFilter = View.QualityLevel.MEDIUM
        upsampling = View.QualityLevel.MEDIUM
    }
    bloomOptions = bloomOptions.apply {
        enabled = true
        strength = profile.bloomStrength
        resolution = 256
        threshold = true
    }
}
