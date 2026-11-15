package com.tamerslab.items.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.tamerslab.R
import com.tamerslab.components.CollectionRefreshNotice
import com.tamerslab.components.CollectionStatePanel
import com.tamerslab.components.CyberEmptyState
import com.tamerslab.components.InstrumentSpacing
import com.tamerslab.components.rememberCollectionLoad
import com.tamerslab.di.TamersLabApplication
import com.tamerslab.farm.ui.digiline.battleItemLabel
import com.tamerslab.items.data.ItemsRepository
import com.tamerslab.navigation.AppRoute
import com.tamerslab.ui.theme.TextSecondaryOnDark

/**
 * Owned items, grouped by what they do. Quest battle stock lives here too: it is the same kind of
 * consumable, so it reads as one more catalogue section instead of a separate bare list tab.
 */
@Composable
fun MyItems(
    navController: NavController,
    onOpenStore: () -> Unit = {},
) {
    val application = LocalContext.current.applicationContext as TamersLabApplication
    val itemsRepository = remember { ItemsRepository(application.container.db) }
    val collection = rememberCollectionLoad(itemsRepository) { itemsRepository.getUserItems() }
    val myItems = collection.data.orEmpty()
    val battleStock by remember(application) {
        application.container.db.questDao().observeBattleStock()
    }.collectAsStateWithLifecycle(emptyList())
    val stock = battleStock.filter { it.quantity > 0 }

    var selectedItemId by remember { mutableStateOf<Long?>(null) }
    val selectedItem = myItems.firstOrNull { it.id == selectedItemId }

    if (CollectionStatePanel(collection)) return
    if (myItems.isEmpty() && stock.isEmpty()) {
        CyberEmptyState(
            message = stringResource(R.string.items_no_items),
            actionLabel = stringResource(R.string.items_open_store),
            onAction = onOpenStore,
        )
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(InstrumentSpacing.Screen),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "refresh") {
            CollectionRefreshNotice(collection)
        }
        ItemFamily.entries.forEach { family ->
            val entries = myItems.filter { itemFamily(it.itemIcon) == family }
            if (entries.isEmpty()) return@forEach
            item(span = { GridItemSpan(maxLineSpan) }, key = "family:${family.name}") {
                CatalogueSectionHeader(family.label, entries.size)
            }
            // Mission names carry a goal plus a window, so that family takes the full row width
            // instead of truncating in a two-up tile; consumables stay in the compact grid.
            if (family == ItemFamily.MISSIONS) {
                items(entries, key = { "item:${it.id}" }, span = { GridItemSpan(maxLineSpan) }) { item ->
                    CatalogueCard(
                        tile = CatalogueTile(
                            key = "item:${item.id}",
                            title = catalogueTitle(item),
                            iconRes = getIconResource(item.itemIcon),
                            lengthRes = getLengthIcon(item.itemLength),
                            trailing = stringResource(R.string.items_quantity_badge, item.quantity),
                            onClick = { selectedItemId = item.id },
                        ),
                        wide = true,
                    )
                }
            } else {
                items(entries, key = { "item:${it.id}" }) { item ->
                    CatalogueCard(
                        tile = CatalogueTile(
                            key = "item:${item.id}",
                            title = catalogueTitle(item),
                            iconRes = getIconResource(item.itemIcon),
                            lengthRes = getLengthIcon(item.itemLength),
                            trailing = stringResource(R.string.items_quantity_badge, item.quantity),
                            onClick = { selectedItemId = item.id },
                        ),
                    )
                }
            }
        }
        if (stock.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "battle") {
                CatalogueSectionHeader(R.string.quest_battle_items, stock.size)
            }
            item(span = { GridItemSpan(maxLineSpan) }, key = "battle-hint") {
                Text(
                    text = stringResource(R.string.quest_battle_items_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondaryOnDark,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                )
            }
            items(stock, key = { "battle:${it.itemId}" }) { entry ->
                CatalogueCard(
                    tile = CatalogueTile(
                        key = "battle:${entry.itemId}",
                        title = battleItemLabel(entry.itemId),
                        iconRes = battleStockIcon(entry.itemId),
                        trailing = stringResource(R.string.items_quantity_badge, entry.quantity),
                    ),
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
            onClickCancel = { selectedItemId = null },
        )
    }
}

/** Battle consumables are not authored in the shop catalogue, so they get stable family glyphs. */
private fun battleStockIcon(itemId: String): Int = when (itemId) {
    "quest_energy" -> R.drawable.baseline_attack_24
    "quest_cleanse" -> R.drawable.baseline_reset_24
    else -> R.drawable.baseline_health_24
}
