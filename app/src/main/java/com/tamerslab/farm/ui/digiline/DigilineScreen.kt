package com.tamerslab.farm.ui.digiline

import androidx.compose.foundation.Image
import com.tamerslab.navigation.AppRoute
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.tamerslab.R
import com.tamerslab.components.TopBanner
import com.tamerslab.components.VitalTabs
import com.tamerslab.components.cyberFrame
import androidx.compose.ui.graphics.RectangleShape
import com.tamerslab.components.rememberCollectionLoad
import com.tamerslab.components.CollectionStatePanel
import com.tamerslab.components.CollectionRefreshNotice
import com.tamerslab.di.TamersLabApplication
import com.tamerslab.dtos.DigilineFarmThread
import com.tamerslab.dtos.DigilineStorageThread
import com.tamerslab.dtos.DigilineWildThread
import com.tamerslab.navigation.NavigationItems
import com.tamerslab.navigation.deliverToRadar
import com.tamerslab.ui.theme.OnVitalAccent
import com.tamerslab.ui.theme.SurfaceDeepPurple
import com.tamerslab.ui.theme.SurfaceStroke
import com.tamerslab.ui.theme.TextPrimaryOnDark
import com.tamerslab.ui.theme.TextSecondaryOnDark
import com.tamerslab.ui.theme.VitalCyan
import com.tamerslab.utils.BitmapData
import com.tamerslab.utils.getBitmap

@Composable
fun DigilineScreen(navController: NavController) {
    val app = LocalContext.current.applicationContext as TamersLabApplication
    val repository = app.container.digifarmRepository
    val storageLoad = rememberCollectionLoad(repository to "stored") { repository.observeStorageThreads() }
    val wildLoad = rememberCollectionLoad(repository to "wild") { repository.observeWildThreads() }
    val farmsLoad = rememberCollectionLoad(repository to "farms") { repository.observeFarmThreads() }
    val storage = storageLoad.data.orEmpty()
    val wild = wildLoad.data.orEmpty()
    val farms = farmsLoad.data.orEmpty()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            Column {
                TopBanner(text = stringResource(R.string.nav_digiline))
                VitalTabs(tab, listOf(stringResource(R.string.ui_digiline_storage), stringResource(R.string.ui_digiline_wild),
                    stringResource(R.string.ui_digiline_farms)), { tab = it })
            }
        },
        contentWindowInsets = WindowInsets.statusBars
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
        ) {
            val activeLoad = when (tab) { 0 -> storageLoad; 1 -> wildLoad; else -> farmsLoad }
            if (!CollectionStatePanel(activeLoad)) Column(Modifier.fillMaxSize()) {
            CollectionRefreshNotice(activeLoad)
            when (tab) {
                0 -> ThreadList(storage, stringResource(R.string.ui_digiline_empty_storage)) { thread ->
                    navController.navigate(AppRoute.Chat(thread.characterId))
                }
                1 -> WildThreadList(wild, stringResource(R.string.ui_digiline_empty_wild)) { thread ->
                    navController.navigate(AppRoute.WildContact(thread.individualId, thread.cardCharacterId))
                }
                else -> FarmThreadList(farms, stringResource(R.string.ui_digiline_empty_farms)) { thread ->
                    navController.navigate(AppRoute.FarmGroup(thread.farmId))
                }
            }
            }
        }
    }
}

fun trackQuestTarget(navController: NavController, individualId: String) {
    // Radar place first, legacy World route second, and a push when neither is on the stack.
    navController.deliverToRadar("radar-quest-target", individualId)
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
            ThreadRow(item.speciesName ?: "Digimon",
                 item.lastMessage,
                item.unreadCount, null, 0, 0,
                reading = stringResource(R.string.audit_trust_reading, item.trust,
                    com.tamerslab.components.rapportSummary(item.trust))) { onClick(item) }
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
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondaryOnDark
        )
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
    reading: String? = null,
    onClick: () -> Unit
) {
    val image = remember(sprite, width, height) {
        sprite?.let { runCatching { BitmapData(it, width, height).getBitmap().asImageBitmap() }.getOrNull() }
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RectangleShape,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .cyberFrame(active = unread > 0)
            .clickable(onClick = onClick)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CutCornerShape(6.dp))
                    .background(VitalCyan.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                if (image != null) {
                    Image(image, title, Modifier.size(48.dp), filterQuality = FilterQuality.None)
                } else {
                    Icon(
                        painter = painterResource(R.drawable.baseline_mood_24),
                        contentDescription = null,
                        tint = VitalCyan,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimaryOnDark,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                reading?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = VitalCyan) }
                preview?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondaryOnDark,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (unread > 0) {
                Badge(
                    containerColor = VitalCyan,
                    contentColor = OnVitalAccent
                ) { Text(unread.toString()) }
            }
        }
    }
}



