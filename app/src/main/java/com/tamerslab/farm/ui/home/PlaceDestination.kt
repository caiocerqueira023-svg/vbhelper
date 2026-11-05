package com.tamerslab.farm.ui.home

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.navigation.NavController
import com.tamerslab.R
import com.tamerslab.navigation.AppRoute
import com.tamerslab.navigation.ItemsDestination
import com.tamerslab.navigation.NavigationItems

/**
 * The five places the farm's command deck exposes (owner decision Q1).
 *
 * Labels stay literal for discoverability; the flavour name ("Colosseum", "Exchange", "Vault") only
 * appears as a subtitle on the place header, never in the dock (Q2/Q3).
 */
enum class PlaceDestination(
    val route: AppRoute,
    @DrawableRes val icon: Int,
    @StringRes val label: Int,
    @StringRes val subtitle: Int,
) {
    Radar(
        AppRoute.Radar,
        R.drawable.baseline_compass_24,
        R.string.place_radar,
        R.string.place_radar_subtitle,
    ),
    Arena(
        AppRoute.Battles,
        R.drawable.baseline_swords_24,
        R.string.place_arena,
        R.string.place_arena_subtitle,
    ),
    Dex(
        AppRoute.Dex,
        R.drawable.baseline_menu_book_24,
        R.string.place_dex,
        R.string.place_dex_subtitle,
    ),
    Shop(
        AppRoute.Items(ItemsDestination.TAB_STORE),
        R.drawable.baseline_data_24,
        R.string.place_shop,
        R.string.place_shop_subtitle,
    ),
    Partners(
        AppRoute.Storage,
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
        popUpTo(AppRoute.Home) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}


