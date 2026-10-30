package com.github.nacabaro.vbhelper.utils

/**
 * The primary stat family of a stored partner, persisted as `UserCharacter.characterType`.
 *
 * This names **where the partner's data lives** (which sidecar is authoritative) — it is not a
 * capability. Whether a partner can reach a watch, needs a DIM/BEM association, or exports as VB/BE
 * is derived from its definition and export target; see
 * [com.github.nacabaro.vbhelper.domain.device_data.CharacterCapabilities].
 *
 * A partner may change how it is projected (conversion, World DS return, DIM fallback) without its
 * family changing, so prefer capability checks over `characterType == APP` style comparisons.
 */
enum class DeviceType {
    VBDevice, // Bandai Vital Bracelet
    BEDevice, // Bandai BE Bracelet
    /** Import origin only; never persisted as a stored character type (folds into VBDevice). */
    VitalWear,
    APP // Source-independent app partner; no invented bracelet measurements.
}

fun detectDeviceType(deviceName: String): DeviceType = when {
    deviceName.startsWith("VitalWear", ignoreCase = true) || deviceName.startsWith("VW-", ignoreCase = true) -> DeviceType.VitalWear
    deviceName.startsWith("BE-", ignoreCase = true) -> DeviceType.BEDevice
    deviceName.startsWith("VB-", ignoreCase = true) -> DeviceType.VBDevice
    else -> DeviceType.VBDevice // Default/fallback, can be refined
}
