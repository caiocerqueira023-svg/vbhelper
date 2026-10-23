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

@Composable
fun ItemsScreen(navController: NavController) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
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
