package com.tamerslab.items.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.tamerslab.R
import com.tamerslab.components.TopBanner
import com.tamerslab.components.VitalTabs
import com.tamerslab.navigation.ItemsDestination

/**
 * Item catalogue. [initialTab] mirrors the optional `Items?tab={tab}` navigation argument
 * (`ItemsDestination.TAB_MINE` / `TAB_STORE`) so a dock "Shop" entry can land directly on the store;
 * the bare `Items` route keeps opening owned items. Battle stock is no longer a separate tab - it is
 * a section of the owned catalogue.
 */
@Composable
fun ItemsScreen(
    navController: NavController,
    initialTab: String = ItemsDestination.TAB_MINE
) {
    val requestedTab = remember(initialTab) { ItemsDestination.tabIndex(initialTab) }
    var selectedTab by rememberSaveable(requestedTab) { mutableIntStateOf(requestedTab) }
    Scaffold(topBar = {
        Column {
            TopBanner(stringResource(R.string.items_title))
            VitalTabs(
                selectedTab,
                listOf(stringResource(R.string.nav_my_items), stringResource(R.string.nav_items_store)),
                { selectedTab = it },
            )
        }
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (selectedTab) {
                0 -> MyItems(navController, onOpenStore = { selectedTab = 1 })
                else -> ItemsStore()
            }
        }
    }
}
