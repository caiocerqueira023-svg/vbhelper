package com.tamerslab.items.ui

import android.widget.Toast
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.withTransaction
import com.tamerslab.R
import com.tamerslab.components.CollectionRefreshNotice
import com.tamerslab.components.CollectionStatePanel
import com.tamerslab.components.CyberEmptyState
import com.tamerslab.components.InstrumentSpacing
import com.tamerslab.components.rememberCollectionLoad
import com.tamerslab.data.database.AppDatabase
import com.tamerslab.di.TamersLabApplication
import com.tamerslab.dtos.ItemDtos
import com.tamerslab.items.data.CurrencyRepository
import com.tamerslab.items.data.ItemsRepository
import kotlinx.coroutines.launch

/**
 * The store catalogue: one wallet strip, the same family sections as the owned list, and a price on
 * every entry. Buying stays a single explicit action in the item dialog.
 */
@Composable
fun ItemsStore() {
    val application = LocalContext.current.applicationContext as TamersLabApplication
    val itemsRepository = remember { ItemsRepository(application.container.db) }
    val collection = rememberCollectionLoad(itemsRepository) { itemsRepository.getAllItems() }
    val items = collection.data.orEmpty()

    val currencyRepository = application.container.currencyRepository
    val currentCurrency = currencyRepository.currencyValue.collectAsStateWithLifecycle(0)
    val scope = rememberCoroutineScope()

    var selectedItemId by remember { mutableStateOf<Long?>(null) }
    val selectedItem = items.firstOrNull { it.id == selectedItemId }

    if (CollectionStatePanel(collection)) return
    if (items.isEmpty()) {
        CyberEmptyState(stringResource(R.string.items_store_empty))
        return
    }

    val bits = currentCurrency.value
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = InstrumentSpacing.Screen, vertical = 8.dp),
    ) {
        item(key = "refresh") {
            CollectionRefreshNotice(collection)
        }
        item(key = "wallet") {
            StoreWalletStrip(bits = bits)
        }
        ItemFamily.entries.forEach { family ->
            val entries = items.filter { itemFamily(it.itemIcon) == family }
            if (entries.isEmpty()) return@forEach
            item(key = "family:${family.name}") {
                CatalogueSectionHeader(family.label)
            }
            items(entries, key = { "store:${it.id}" }) { item ->
                CatalogueCard(
                    tile = CatalogueTile(
                        key = "store:${item.id}",
                        title = catalogueTitle(item),
                        iconRes = getIconResource(item.itemIcon),
                        lengthRes = getLengthIcon(item.itemLength),
                        trailing = stringResource(R.string.items_store_credits, item.price),
                        trailingIsWarning = item.price > bits,
                        onClick = { selectedItemId = item.id },
                    ),
                    wide = family == ItemFamily.MISSIONS,
                )
            }
        }
    }

    if (selectedItem != null) {
        ItemDialog(
            item = selectedItem,
            balance = bits,
            onClickPurchase = {
                scope.launch {
                    Toast.makeText(
                        application.applicationContext,
                        application.getString(
                            purchaseItem(application.container.db, selectedItem, currencyRepository)
                        ),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            },
            onClickCancel = { selectedItemId = null },
        )
    }
}

suspend fun purchaseItem(
    db: AppDatabase,
    item: ItemDtos.ItemsWithQuantities,
    currencyRepository: CurrencyRepository,
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
