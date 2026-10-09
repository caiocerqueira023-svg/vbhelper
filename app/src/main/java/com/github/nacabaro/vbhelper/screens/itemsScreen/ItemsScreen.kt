package com.github.nacabaro.vbhelper.screens.itemsScreen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.github.nacabaro.vbhelper.components.TopBanner
import com.github.nacabaro.vbhelper.navigation.NavigationItems
import androidx.compose.ui.res.stringResource
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.ui.theme.VitalCyan


@Composable
fun ItemsScreen(
    navController: NavController,
) {
    var selectedTabItem by remember { mutableStateOf(0) }
    val indicatorColor = VitalCyan
    val items = listOf(
        NavigationItems.MyItems,
        NavigationItems.ItemsStore
    )
    Scaffold(
        topBar = {
            Column {
                TopBanner(text = stringResource(R.string.items_title))
                PrimaryTabRow(
                    selectedTabIndex = selectedTabItem,
                    modifier = Modifier,
                    containerColor = MaterialTheme.colorScheme.background,
                    indicator = {},
                    divider = {}
                ) {
                    items.forEachIndexed { index, item ->
                        Tab(
                            text = { Text(text = stringResource(item.label)) },
                            selected = selectedTabItem == index,
                            onClick = { selectedTabItem = index },
                            modifier = Modifier.drawBehind {
                                if (selectedTabItem == index) {
                                    drawLine(
                                        color = indicatorColor,
                                        start = Offset(12.dp.toPx(), size.height - 2.dp.toPx()),
                                        end = Offset(size.width - 12.dp.toPx(), size.height - 2.dp.toPx()),
                                        strokeWidth = 3.dp.toPx()
                                    )
                                }
                            }
                        )
                    }
                    Tab(selected = selectedTabItem == 2, onClick = { selectedTabItem = 2 },
                        text = { Text(stringResource(R.string.quest_battle_items)) })
                }
            }
        }
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = contentPadding.calculateTopPadding())
        ) {
            when (selectedTabItem) {
                0 -> MyItems(navController)
                1 -> ItemsStore(navController)
                2 -> QuestBattleItems()
            }
        }
    }
}
