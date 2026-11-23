package com.tamerslab.settings.data

import android.content.Context
import android.content.SharedPreferences
import com.tamerslab.species.data.DigimonScanPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DebugSettings(
    val enabled: Boolean = false,
    val instantScan: Boolean = false,
    val legacyLlmChat: Boolean = false,
    val freeChatKilled: Boolean = false,
    private val distanceOverride: Boolean = false
) {
    val allowAnyDistance: Boolean get() = enabled && distanceOverride
    val percentagePerDefeat: Int get() = DigimonScanPolicy.perDefeat(enabled, instantScan)

    /** True only when the master debug switch and the legacy chat switch are both on. */
    val legacyLlmEnabled: Boolean get() = enabled && legacyLlmChat

    /**
     * Authored DigiLine Free Talk ships on by default; this Debug switch can only kill it.
     * The build switch and content availability are combined by the container, not here.
     */
    val freeChatAvailable: Boolean get() = !freeChatKilled
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
        instantScan = readBoolean("debug_instant_digimon_scan", false),
        legacyLlmChat = readBoolean("debug_legacy_llm_chat", false),
        freeChatKilled = readBoolean("debug_free_chat_killed", false)
    ))
    val state = mutableState.asStateFlow()
    val enabled: Boolean get() = state.value.enabled
    val allowAnyDistance: Boolean get() = state.value.allowAnyDistance
    val legacyLlmEnabled: Boolean get() = state.value.legacyLlmEnabled
    val freeChatAvailable: Boolean get() = state.value.freeChatAvailable

    @Synchronized fun setEnabled(enabled: Boolean) {
        mutableState.value = mutableState.value.copy(
            enabled = enabled,
            distanceOverride = false,
            legacyLlmChat = if (enabled) mutableState.value.legacyLlmChat else false,
            freeChatKilled = if (enabled) mutableState.value.freeChatKilled else false
        )
        writeBoolean("debug_enabled", enabled)
        if (!enabled) {
            writeBoolean("debug_legacy_llm_chat", false)
            writeBoolean("debug_free_chat_killed", false)
        }
    }

    @Synchronized fun setInstantScan(enabled: Boolean) {
        if (!this.enabled) return
        mutableState.value = mutableState.value.copy(instantScan = enabled)
        writeBoolean("debug_instant_digimon_scan", enabled)
    }

    /** Legacy LLM chat is debug-only and sandboxed; it can never run while the master switch is off. */
    @Synchronized fun setLegacyLlmChat(enabled: Boolean) {
        if (!this.enabled) return
        mutableState.value = mutableState.value.copy(legacyLlmChat = enabled)
        writeBoolean("debug_legacy_llm_chat", enabled)
    }

    /**
     * Debug-only kill switch for authored Free Talk. Off by default: free talk stays available
     * until a developer turns this on, and the master debug switch must be on to persist it.
     */
    @Synchronized fun setFreeChatKilled(killed: Boolean) {
        if (!this.enabled) return
        mutableState.value = mutableState.value.copy(freeChatKilled = killed)
        writeBoolean("debug_free_chat_killed", killed)
    }

    /** Range overrides are session-only and never survive restart or master-switch revocation. */
    @Synchronized fun setAnyDistance(enabled: Boolean) {
        mutableState.value = mutableState.value.copy(distanceOverride = this.enabled && enabled)
    }
}
