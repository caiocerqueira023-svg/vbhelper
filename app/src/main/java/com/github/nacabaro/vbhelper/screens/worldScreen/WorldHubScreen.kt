package com.github.nacabaro.vbhelper.screens.worldScreen

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.screens.digifarmScreen.DigifarmScreen
import com.github.nacabaro.vbhelper.ui.theme.TextSecondaryOnDark
import com.github.nacabaro.vbhelper.ui.theme.VitalCyan

@Composable
fun WorldScreen(navController: NavController) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    when (selectedTab) {
        0 -> RadarScreen(
            navController = navController,
            selectedWorldTab = selectedTab,
            onWorldTabSelected = { selectedTab = it }
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
                        color = VitalCyan,
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
                        color = VitalCyan,
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
