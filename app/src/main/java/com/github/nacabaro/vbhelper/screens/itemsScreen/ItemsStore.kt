package com.github.nacabaro.vbhelper.screens.itemsScreen
import androidx.room.withTransaction

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.github.nacabaro.vbhelper.database.AppDatabase
import com.github.nacabaro.vbhelper.di.VBHelper
import com.github.nacabaro.vbhelper.dtos.ItemDtos
import com.github.nacabaro.vbhelper.source.CurrencyRepository
import com.github.nacabaro.vbhelper.source.ItemsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.components.CyberEmptyState
import com.github.nacabaro.vbhelper.components.CyberPanel
import com.github.nacabaro.vbhelper.components.rememberCollectionLoad
import com.github.nacabaro.vbhelper.components.CollectionStatePanel
import com.github.nacabaro.vbhelper.components.CollectionRefreshNotice
import com.github.nacabaro.vbhelper.components.InstrumentSpacing
import com.github.nacabaro.vbhelper.ui.theme.TextSecondaryOnDark
import com.github.nacabaro.vbhelper.ui.theme.VitalCyan

@Composable
fun ItemsStore(
    navController: NavController
) {
    val application = LocalContext.current.applicationContext as VBHelper
    val itemsRepository = remember { ItemsRepository(application.container.db) }
    val collection = rememberCollectionLoad(itemsRepository) { itemsRepository.getAllItems() }
    val myItems = collection.data.orEmpty()

    var selectedItemId by remember { mutableStateOf<Long?>(null) }
    val selectedItem = myItems.firstOrNull { it.id == selectedItemId }

    val currencyRepository = application.container.currencyRepository
    val currentCurrency = currencyRepository.currencyValue.collectAsState(0)

    val scope = rememberCoroutineScope()

    if (collection.blocking) { CollectionStatePanel(collection); return }
    if (myItems.isEmpty()) {
        CyberEmptyState(stringResource(R.string.items_no_items))
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = InstrumentSpacing.Screen),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CollectionRefreshNotice(collection)
            CyberPanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                active = true
            ) {
                Text(
                    text = stringResource(R.string.items_available_balance).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondaryOnDark
                )
                Text(
                    text = stringResource(R.string.items_store_credits, currentCurrency.value),
                    style = MaterialTheme.typography.titleLarge,
                    color = VitalCyan
                )
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 104.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp),
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
    }


    if (selectedItem != null) {
        ItemDialog(
            item = selectedItem,
            onClickPurchase = {
                scope.launch {
                    Toast.makeText(
                        application.applicationContext,
                        application.getString(
                            purchaseItem(
                                application.container.db,
                                selectedItem,
                                currencyRepository
                            )
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onClickCancel = { selectedItemId = null }
        )
    }
}

suspend fun purchaseItem(
    db: AppDatabase,
    item: ItemDtos.ItemsWithQuantities,
    currencyRepository: CurrencyRepository
): Int {
    require(item.price >= 0)
    currencyRepository.initializeWallet()
    return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        db.withTransaction {
            if (db.questDao().spendBits(item.price) != 1) R.string.items_not_enough_credits
            else {
                check(db.itemDao().grantQuestItem(item.id, 1) == 1)
                R.string.items_purchase_success
            }
        }
    }
}
