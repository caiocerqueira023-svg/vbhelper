package com.tamerslab.world.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavBackStackEntry
import com.tamerslab.R
import com.tamerslab.farm.ui.digifarm.DigifarmScreen
import com.tamerslab.components.VitalTabs
import com.tamerslab.ui.theme.TextSecondaryOnDark
import com.tamerslab.ui.theme.VitalCyan

@Composable
fun WorldScreen(
    navController: NavController,
    worldEntry: NavBackStackEntry,
    onFullScreenBattleChanged: (Boolean) -> Unit = {}
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var firstPersonPreferred by rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    var labelsPreferred by rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val incomingChallenge by remember(worldEntry) {
        worldEntry.savedStateHandle.getStateFlow<String?>("radar-challenge",null)
    }.collectAsStateWithLifecycle()
    val incomingInteraction by remember(worldEntry) { worldEntry.savedStateHandle.getStateFlow<String?>("radar-interaction",null) }.collectAsStateWithLifecycle()
    val incomingQuestTarget by remember(worldEntry) { worldEntry.savedStateHandle.getStateFlow<String?>("radar-quest-target",null) }.collectAsStateWithLifecycle()
    LaunchedEffect(incomingChallenge,incomingInteraction,incomingQuestTarget) {
        if(incomingChallenge!=null || incomingInteraction!=null || incomingQuestTarget!=null) selectedTab=0
    }
    when (selectedTab) {
        0 -> RadarScreen(
            navController = navController,
            worldEntry = worldEntry,
            selectedWorldTab = selectedTab,
            onWorldTabSelected = { selectedTab = it },
            onFullScreenBattleChanged = onFullScreenBattleChanged,
            firstPersonPreferred = firstPersonPreferred,
            onViewPreferenceChanged = { firstPersonPreferred = it },
            labelsPreferred = labelsPreferred,
            onLabelsPreferenceChanged = { labelsPreferred = it }
        )
        else -> DigifarmScreen(
            navController = navController,
            selectedWorldTab = selectedTab,
            onWorldTabSelected = { selectedTab = it }
        )
    }
}

@Composable
internal fun WorldSectionTabs(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    VitalTabs(selectedTab, listOf(stringResource(R.string.ui_world_radar_tab), stringResource(R.string.ui_digifarm_tab)), onTabSelected)
}

