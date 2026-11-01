package com.tamerslab.items.ui

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
import com.tamerslab.di.TamersLabApplication
import com.tamerslab.navigation.AppRoute
import com.tamerslab.navigation.NavigationItems
import com.tamerslab.items.data.ItemsRepository
import com.tamerslab.R
import com.tamerslab.components.CyberEmptyState
import com.tamerslab.components.rememberCollectionLoad
import com.tamerslab.components.CollectionStatePanel
import com.tamerslab.components.CollectionRefreshNotice
import androidx.compose.foundation.layout.Column
import com.tamerslab.components.InstrumentSpacing


@Composable
fun MyItems(
    navController: NavController,
) {
    val application = LocalContext.current.applicationContext as TamersLabApplication
    val itemsRepository = remember { ItemsRepository(application.container.db) }
    val collection = rememberCollectionLoad(itemsRepository) { itemsRepository.getUserItems() }
    val myItems = collection.data.orEmpty()

    var selectedItemId by remember { mutableStateOf<Long?>(null) }
    val selectedItem = myItems.firstOrNull { it.id == selectedItemId }

    if (collection.blocking) { CollectionStatePanel(collection); return }
    if (myItems.isEmpty()) {
        CyberEmptyState(stringResource(R.string.items_no_items))
    } else {
        Column(Modifier.fillMaxSize()) {
        CollectionRefreshNotice(collection)
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 104.dp),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(InstrumentSpacing.Screen),
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
                        selectedItemId = index.id
                    }
                )
            }
        }
        }

        if (selectedItem != null) {
            ItemDialog(
                item = selectedItem,
                onClickUse = {
                    navController.navigate(AppRoute.ApplyItem(selectedItem.id))
                    selectedItemId = null
                },
                onClickCancel = { selectedItemId = null }
            )
        }
    }
}

