package com.github.nacabaro.vbhelper.screens.itemsScreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.di.VBHelper
import com.github.nacabaro.vbhelper.screens.digilineScreen.battleItemLabel

@Composable
fun QuestBattleItems() {
    val app = LocalContext.current.applicationContext as VBHelper
    val stock by app.container.db.questDao().observeBattleStock().collectAsState(emptyList())
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text(stringResource(R.string.quest_battle_items_hint), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (stock.none { it.quantity > 0 }) item { Text(stringResource(R.string.quest_stock_empty)) }
        items(stock.filter { it.quantity > 0 }, key = { it.itemId }) { item ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(battleItemLabel(item.itemId), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.quest_stock_quantity, item.quantity))
            }
        }
    }
}
