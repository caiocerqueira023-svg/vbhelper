package com.github.nacabaro.vbhelper.screens.digilineScreen

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Badge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.components.TopBanner
import com.github.nacabaro.vbhelper.di.VBHelper
import com.github.nacabaro.vbhelper.dtos.DigilineFarmThread
import com.github.nacabaro.vbhelper.dtos.DigilineStorageThread
import com.github.nacabaro.vbhelper.dtos.DigilineWildThread
import com.github.nacabaro.vbhelper.navigation.NavigationItems
import com.github.nacabaro.vbhelper.ui.theme.VitalCyan
import com.github.nacabaro.vbhelper.utils.BitmapData
import com.github.nacabaro.vbhelper.utils.getBitmap

@Composable
fun DigilineScreen(navController: NavController) {
    val app = LocalContext.current.applicationContext as VBHelper
    val repository = app.container.digifarmRepository
    val storage by repository.observeStorageThreads().collectAsState(emptyList())
    val wild by repository.observeWildThreads().collectAsState(emptyList())
    val farms by repository.observeFarmThreads().collectAsState(emptyList())
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(topBar = { TopBanner(text = stringResource(R.string.nav_digiline)) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(tab, containerColor = MaterialTheme.colorScheme.surface, contentColor = VitalCyan) {
                Tab(tab == 0, { tab = 0 }, text = { Text(stringResource(R.string.ui_digiline_storage)) })
                Tab(tab == 1, { tab = 1 }, text = { Text(stringResource(R.string.ui_digiline_wild)) })
                Tab(tab == 2, { tab = 2 }, text = { Text(stringResource(R.string.ui_digiline_farms)) })
            }
            when (tab) {
                0 -> ThreadList(storage, stringResource(R.string.ui_digiline_empty_storage)) { thread ->
                    navController.navigate(NavigationItems.Chat.route.replace("{characterId}", thread.characterId.toString()))
                }
                1 -> WildThreadList(wild, stringResource(R.string.ui_digiline_empty_wild)) { thread ->
                    navController.navigate(
                        NavigationItems.WildContact.route
                            .replace("{individualId}", thread.individualId)
                            .replace("{cardCharacterId}", thread.cardCharacterId.toString())
                    )
                }
                else -> FarmThreadList(farms, stringResource(R.string.ui_digiline_empty_farms)) { thread ->
                    navController.navigate(NavigationItems.FarmGroup.route.replace("{farmId}", thread.farmId))
                }
            }
        }
    }
}

@Composable
private fun ThreadList(items: List<DigilineStorageThread>, empty: String, onClick: (DigilineStorageThread) -> Unit) {
    if (items.isEmpty()) return EmptyList(empty)
    LazyColumn(Modifier.fillMaxSize()) {
        items(items, key = { it.individualId }) { item ->
            ThreadRow(item.nickname ?: item.speciesName ?: "Digimon", item.lastMessage, item.unreadCount, item.spriteIdle, item.spriteWidth, item.spriteHeight) { onClick(item) }
        }
    }
}

@Composable
private fun WildThreadList(items: List<DigilineWildThread>, empty: String, onClick: (DigilineWildThread) -> Unit) {
    if (items.isEmpty()) return EmptyList(empty)
    LazyColumn(Modifier.fillMaxSize()) {
        items(items, key = { it.individualId }) { item ->
            ThreadRow(item.speciesName ?: "Digimon", item.lastMessage ?: stringResource(R.string.ui_digiline_trust, item.trust), item.unreadCount, null, 0, 0) { onClick(item) }
        }
    }
}

@Composable
private fun FarmThreadList(items: List<DigilineFarmThread>, empty: String, onClick: (DigilineFarmThread) -> Unit) {
    if (items.isEmpty()) return EmptyList(empty)
    LazyColumn(Modifier.fillMaxSize()) {
        items(items, key = { it.farmId }) { item ->
            ThreadRow(item.farmName, item.lastMessage ?: stringResource(R.string.ui_digiline_resident_count, item.residentCount), item.unreadCount, null, 0, 0) { onClick(item) }
        }
    }
}

@Composable
private fun EmptyList(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ThreadRow(
    title: String,
    preview: String?,
    unread: Int,
    sprite: ByteArray?,
    width: Int,
    height: Int,
    onClick: () -> Unit
) {
    val image = remember(sprite, width, height) {
        sprite?.let { runCatching { BitmapData(it, width, height).getBitmap().asImageBitmap() }.getOrNull() }
    }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        image?.let { Image(it, title, Modifier.size(48.dp), filterQuality = FilterQuality.None) }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            preview?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2) }
        }
        if (unread > 0) Badge { Text(unread.toString()) }
    }
}
