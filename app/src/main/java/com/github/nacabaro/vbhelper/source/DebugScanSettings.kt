package com.github.nacabaro.vbhelper.source

import android.content.Context
import android.content.pm.ApplicationInfo

/** Release builds ignore the persisted development switch even after a debug install. */
class DebugScanSettings(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
    val isAvailable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    var instantScan: Boolean
        get() = isAvailable && preferences.getBoolean("debug_instant_digimon_scan", false)
        set(value) {
            if (isAvailable) preferences.edit().putBoolean("debug_instant_digimon_scan", value).apply()
        }

    val percentagePerDefeat: Int
        get() = DigimonScanPolicy.perDefeat(isAvailable, instantScan)
}
