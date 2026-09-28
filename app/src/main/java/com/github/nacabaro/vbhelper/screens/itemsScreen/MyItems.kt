package com.github.nacabaro.vbhelper.screens.itemsScreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.github.nacabaro.vbhelper.di.VBHelper
import com.github.nacabaro.vbhelper.navigation.NavigationItems
import com.github.nacabaro.vbhelper.source.ItemsRepository
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.components.CyberEmptyState


@Composable
fun MyItems(
    navController: NavController,
) {
    val application = LocalContext.current.applicationContext as VBHelper
    val itemsRepository = remember { ItemsRepository(application.container.db) }
    val myItems by itemsRepository.getUserItems().collectAsState(emptyList())

    var selectedElementIndex by remember { mutableStateOf<Int?>(null) }

    if (myItems.isEmpty()) {
        CyberEmptyState(stringResource(R.string.items_no_items))
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 104.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(
                items = myItems,
                key = { it.id },
                contentType = { "item-entry" }
            ) { index ->
                ItemElement(
                    item = index,
                    modifier = Modifier,
                    onClick = {
                        selectedElementIndex = myItems.indexOf(index)
                    }
                )
            }
        }

        if (selectedElementIndex != null) {
            ItemDialog(
                item = myItems[selectedElementIndex!!],
                onClickUse = {
                    navController
                        .navigate(
                            NavigationItems
                                .ApplyItem.route
                                .replace(
                                    "{itemId}",
                                    myItems[selectedElementIndex!!].id.toString()
                                )
                        )
                    selectedElementIndex = null
                },
                onClickCancel = { selectedElementIndex = null }
            )
        }
    }
}
