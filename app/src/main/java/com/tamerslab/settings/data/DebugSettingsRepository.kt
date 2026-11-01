package com.tamerslab.settings.data

import android.content.Context
import android.content.SharedPreferences
import com.tamerslab.species.data.DigimonScanPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DebugSettings(
    val enabled: Boolean = false,
    val instantScan: Boolean = false,
    private val distanceOverride: Boolean = false
) {
    val allowAnyDistance: Boolean get() = enabled && distanceOverride
    val percentagePerDefeat: Int get() = DigimonScanPolicy.perDefeat(enabled, instantScan)
}

/** One application-owned opt-in. Build debuggability never enables a user-facing tool. */
class DebugSettingsRepository(
    readBoolean: (String, Boolean) -> Boolean,
    private val writeBoolean: (String, Boolean) -> Unit
) {
    constructor(context: Context) : this(context.applicationContext.getSharedPreferences("app_preferences", Context.MODE_PRIVATE))
    private constructor(preferences: SharedPreferences) : this(
        preferences::getBoolean,
        { key, value -> preferences.edit().putBoolean(key, value).apply() }
    )

    private val mutableState = MutableStateFlow(DebugSettings(
        enabled = readBoolean("debug_enabled", false),
        instantScan = readBoolean("debug_instant_digimon_scan", false)
    ))
    val state = mutableState.asStateFlow()
    val enabled: Boolean get() = state.value.enabled
    val allowAnyDistance: Boolean get() = state.value.allowAnyDistance

    @Synchronized fun setEnabled(enabled: Boolean) {
        mutableState.value = mutableState.value.copy(enabled = enabled, distanceOverride = false)
        writeBoolean("debug_enabled", enabled)
    }

    @Synchronized fun setInstantScan(enabled: Boolean) {
        if (!this.enabled) return
        mutableState.value = mutableState.value.copy(instantScan = enabled)
        writeBoolean("debug_instant_digimon_scan", enabled)
    }

    /** Range overrides are session-only and never survive restart or master-switch revocation. */
    @Synchronized fun setAnyDistance(enabled: Boolean) {
        mutableState.value = mutableState.value.copy(distanceOverride = this.enabled && enabled)
    }
}
