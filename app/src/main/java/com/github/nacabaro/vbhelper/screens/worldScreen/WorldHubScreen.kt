package com.github.nacabaro.vbhelper.screens.worldScreen

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.screens.digifarmScreen.DigifarmScreen
import com.github.nacabaro.vbhelper.ui.theme.TextSecondaryOnDark
import com.github.nacabaro.vbhelper.ui.theme.VitalCyan

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
    }.collectAsState()
    val incomingInteraction by remember(worldEntry) { worldEntry.savedStateHandle.getStateFlow<String?>("radar-interaction",null) }.collectAsState()
    val incomingQuestTarget by remember(worldEntry) { worldEntry.savedStateHandle.getStateFlow<String?>("radar-quest-target",null) }.collectAsState()
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
    val indicatorColor = VitalCyan
    PrimaryTabRow(
        selectedTabIndex = selectedTab,
        containerColor = MaterialTheme.colorScheme.background,
        indicator = {},
        divider = {}
    ) {
        Tab(
            selected = selectedTab == 0,
            onClick = { onTabSelected(0) },
            selectedContentColor = VitalCyan,
            unselectedContentColor = TextSecondaryOnDark,
            modifier = Modifier.drawBehind {
                if (selectedTab == 0) {
                    drawLine(
                        color = indicatorColor,
                        start = Offset(12.dp.toPx(), size.height - 2.dp.toPx()),
                        end = Offset(size.width - 12.dp.toPx(), size.height - 2.dp.toPx()),
                        strokeWidth = 3.dp.toPx()
                    )
                }
            },
            text = {
                Text(
                    text = stringResource(R.string.ui_world_radar_tab),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        )
        Tab(
            selected = selectedTab == 1,
            onClick = { onTabSelected(1) },
            selectedContentColor = VitalCyan,
            unselectedContentColor = TextSecondaryOnDark,
            modifier = Modifier.drawBehind {
                if (selectedTab == 1) {
                    drawLine(
                        color = indicatorColor,
                        start = Offset(12.dp.toPx(), size.height - 2.dp.toPx()),
                        end = Offset(size.width - 12.dp.toPx(), size.height - 2.dp.toPx()),
                        strokeWidth = 3.dp.toPx()
                    )
                }
            },
            text = {
                Text(
                    text = stringResource(R.string.ui_digifarm_tab),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        )
    }
}
