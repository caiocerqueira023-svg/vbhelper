package com.tamerslab.navigation

import com.tamerslab.R
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

sealed class NavigationItems(
    val route: AppRoute,
    @DrawableRes val icon: Int,
    @StringRes val label: Int
) {
    object Scan : NavigationItems(
        AppRoute.Scan(),
        R.drawable.baseline_nfc_24,
        R.string.nav_scan
    )

    object Battles : NavigationItems(
        AppRoute.Battles,
        R.drawable.vb_icon_battle,
        R.string.nav_battle
    )

    object Home : NavigationItems(
        AppRoute.Home,
        R.drawable.baseline_cottage_24,
        R.string.nav_home
    )
    object World : NavigationItems(AppRoute.World, R.drawable.baseline_compass_24, R.string.nav_world)

    /**
     * The Radar place: today's World-tab radar surface promoted to a first-class route.
     * `World` stays registered as an alias for one release so existing back stacks,
     * saved-state handoffs and the legacy tab keep resolving.
     */
    object Radar : NavigationItems(AppRoute.Radar, R.drawable.baseline_compass_24, R.string.nav_radar)

    object Dex : NavigationItems(
        AppRoute.Dex,
        R.drawable.baseline_menu_book_24,
        R.string.nav_dex
    )

    object CardAdventure : NavigationItems(
        AppRoute.CardAdventure(0),
        R.drawable.baseline_fort_24,
        R.string.nav_card_adventure
    )

    object Storage : NavigationItems(
        AppRoute.Storage,
        R.drawable.baseline_chest_24,
        R.string.nav_storage
    )

    object Rank : NavigationItems(
        AppRoute.Rank,
        R.drawable.vb_rank_15,
        R.string.nav_rank
    )

    object Settings : NavigationItems(
        AppRoute.Settings,
        R.drawable.baseline_settings_24,
        R.string.nav_settings
    )

    object Lorebook : NavigationItems(
        AppRoute.Lorebook,
        R.drawable.baseline_book_24,
        R.string.ui_lorebook_title
    )

    object Viewer : NavigationItems(
        AppRoute.Viewer,
        R.drawable.baseline_image_24,
        R.string.nav_viewer
    )

    object CardView : NavigationItems(
        AppRoute.CardView(0),
        R.drawable.baseline_image_24,
        R.string.nav_card
    )

    object Items : NavigationItems(
        AppRoute.Items(),
        R.drawable.vb_icon_items,
        R.string.nav_items
    )

    object MyItems : NavigationItems(
        AppRoute.Items("mine"),
        R.drawable.vb_icon_items,
        R.string.nav_my_items
    )

    object ItemsStore : NavigationItems(
        AppRoute.Items("store"),
        R.drawable.vb_icon_items,
        R.string.nav_items_store
    )

    object ApplyItem : NavigationItems(
        AppRoute.ApplyItem(0),
        R.drawable.vb_icon_items,
        R.string.nav_apply_item
    )

    object Adventure : NavigationItems(
        AppRoute.Adventure,
        R.drawable.baseline_fort_24,
        R.string.nav_adventure
    )

    object Credits : NavigationItems(
        AppRoute.Credits,
        R.drawable.baseline_data_24,
        R.string.nav_credits
    )

    object Chat : NavigationItems(
        AppRoute.Chat(0),
        R.drawable.baseline_mood_24,
        R.string.nav_chat
    )

    object TechniqueLoadout : NavigationItems(
        AppRoute.TechniqueLoadout(0),
        R.drawable.vb_icon_battle,
        R.string.nav_battle
    )

    object WorldChat : NavigationItems(
        AppRoute.WorldChat(0),
        R.drawable.baseline_mood_24,
        R.string.nav_chat
    )

    object WorldConversation : NavigationItems(
        AppRoute.WorldConversation(""),
        R.drawable.baseline_mood_24,
        R.string.nav_chat
    )

    object Digiline : NavigationItems(
        AppRoute.Digiline,
        R.drawable.baseline_mood_24,
        R.string.nav_digiline
    )

    object WildContact : NavigationItems(
        AppRoute.WildContact("", 0),
        R.drawable.baseline_mood_24,
        R.string.ui_digiline_wild
    )

    object WorldRecruits : NavigationItems(
        AppRoute.WorldRecruits,
        R.drawable.baseline_capture_24,
        R.string.nav_world
    )

    object Quests : NavigationItems(
        AppRoute.Quests,
        R.drawable.baseline_fort_24,
        R.string.quest_title
    )
}

