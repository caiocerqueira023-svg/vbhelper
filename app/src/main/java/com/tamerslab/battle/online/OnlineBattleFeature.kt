package com.tamerslab.battle.online

import com.tamerslab.BuildConfig

/** Rollback switch for the legacy online PVP/auth surface. Offline battle is independent. */
object OnlineBattleFeature {
    val ENABLED: Boolean = BuildConfig.ONLINE_PVP_ENABLED
}
