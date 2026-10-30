package com.github.nacabaro.vbhelper.screens.itemsScreen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.components.TopBanner
import com.github.nacabaro.vbhelper.components.VitalTabs
import com.github.nacabaro.vbhelper.navigation.ItemsDestination

/**
 * Item hub. [initialTab] mirrors the optional `Items?tab={tab}` navigation argument
 * (`ItemsDestination.TAB_MINE` / `TAB_STORE` / `TAB_QUEST`) so a dock "Shop" entry can land
 * directly on the store; the bare `Items` route keeps opening My items.
 */
@Composable
fun ItemsScreen(
    navController: NavController,
    initialTab: String = ItemsDestination.TAB_MINE
) {
    val requestedTab = remember(initialTab) { ItemsDestination.tabIndex(initialTab) }
    var selectedTab by rememberSaveable(requestedTab) { mutableIntStateOf(requestedTab) }
    Scaffold(topBar = { Column {
        TopBanner(stringResource(R.string.items_title))
        VitalTabs(selectedTab, listOf(stringResource(R.string.nav_my_items), stringResource(R.string.nav_items_store),
            stringResource(R.string.quest_battle_items)), { selectedTab = it })
    } }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (selectedTab) { 0 -> MyItems(navController); 1 -> ItemsStore(navController); else -> QuestBattleItems() }
        }
    }
}
