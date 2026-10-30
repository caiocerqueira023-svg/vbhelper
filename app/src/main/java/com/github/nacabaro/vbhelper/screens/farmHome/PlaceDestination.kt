package com.github.nacabaro.vbhelper.screens.farmHome

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.navigation.NavController
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.navigation.ItemsDestination
import com.github.nacabaro.vbhelper.navigation.NavigationItems

/**
 * The five places the farm's command deck exposes (owner decision Q1).
 *
 * Labels stay literal for discoverability; the flavour name ("Colosseum", "Exchange", "Vault") only
 * appears as a subtitle on the place header, never in the dock (Q2/Q3).
 */
enum class PlaceDestination(
    val route: String,
    @DrawableRes val icon: Int,
    @StringRes val label: Int,
    @StringRes val subtitle: Int,
) {
    Radar(
        "Radar",
        R.drawable.baseline_compass_24,
        R.string.place_radar,
        R.string.place_radar_subtitle,
    ),
    Arena(
        NavigationItems.Battles.route,
        R.drawable.baseline_swords_24,
        R.string.place_arena,
        R.string.place_arena_subtitle,
    ),
    DigiLine(
        NavigationItems.Digiline.route,
        R.drawable.baseline_mood_24,
        R.string.place_digiline,
        R.string.place_digiline_subtitle,
    ),
    Shop(
        "${NavigationItems.Items.route}?${ItemsDestination.ARG_TAB}=${ItemsDestination.TAB_STORE}",
        R.drawable.baseline_data_24,
        R.string.place_shop,
        R.string.place_shop_subtitle,
    ),
    Partners(
        NavigationItems.Storage.route,
        R.drawable.baseline_chest_24,
        R.string.place_partners,
        R.string.place_partners_subtitle,
    ),
}

/**
 * Place navigation behaves like a tab switch anchored on Home: the current place is saved, the
 * target restores its own state, and Home stays the single back destination. Returning to the farm
 * is always one system-back away because places pop back to [NavigationItems.Home].
 */
fun NavController.navigateToPlace(place: PlaceDestination) {
    navigate(place.route) {
        popUpTo(NavigationItems.Home.route) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
