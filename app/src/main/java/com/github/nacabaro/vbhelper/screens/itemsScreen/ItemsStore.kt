package com.github.nacabaro.vbhelper.screens.itemsScreen

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
import com.github.nacabaro.vbhelper.ui.theme.TextSecondaryOnDark
import com.github.nacabaro.vbhelper.ui.theme.VitalCyan

@Composable
fun ItemsStore(
    navController: NavController
) {
    val application = LocalContext.current.applicationContext as VBHelper
    val itemsRepository = remember { ItemsRepository(application.container.db) }
    val myItems by itemsRepository.getAllItems().collectAsState(emptyList())

    var selectedElementIndex by remember { mutableStateOf<Int?>(null) }

    val currencyRepository = application.container.currencyRepository
    val currentCurrency = currencyRepository.currencyValue.collectAsState(0)

    val scope = rememberCoroutineScope()

    if (myItems.isEmpty()) {
        CyberEmptyState(stringResource(R.string.items_no_items))
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
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
                            selectedElementIndex = myItems.indexOf(index)
                        }
                    )
                }
            }
        }
    }


    if (selectedElementIndex != null) {
        ItemDialog(
            item = myItems[selectedElementIndex!!],
            onClickPurchase = {
                scope.launch {
                    Toast.makeText(
                        application.applicationContext,
                        application.getString(
                            purchaseItem(
                                application.container.db,
                                myItems[selectedElementIndex!!],
                                currencyRepository
                            )
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onClickCancel = { selectedElementIndex = null }
        )
    }
}

suspend fun purchaseItem(
    db: AppDatabase,
    item: ItemDtos.ItemsWithQuantities,
    currencyRepository: CurrencyRepository
): Int {
    return if (currencyRepository.currencyValue.first() < item.price) {
        R.string.items_not_enough_credits
    } else {
        db
            .itemDao()
            .purchaseItem(
                item.id,
                1
            )

        currencyRepository
            .setCurrencyValue(
                currencyRepository.currencyValue.first() - item.price
            )

        R.string.items_purchase_success
    }
}
