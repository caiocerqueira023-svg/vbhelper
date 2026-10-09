package com.github.nacabaro.vbhelper.screens.digilineScreen

import androidx.compose.foundation.Image
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
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.components.TopBanner
import com.github.nacabaro.vbhelper.di.VBHelper
import com.github.nacabaro.vbhelper.dtos.DigilineFarmThread
import com.github.nacabaro.vbhelper.dtos.DigilineStorageThread
import com.github.nacabaro.vbhelper.dtos.DigilineWildThread
import com.github.nacabaro.vbhelper.navigation.NavigationItems
import com.github.nacabaro.vbhelper.ui.theme.OnVitalAccent
import com.github.nacabaro.vbhelper.ui.theme.SurfaceDeepPurple
import com.github.nacabaro.vbhelper.ui.theme.SurfaceStroke
import com.github.nacabaro.vbhelper.ui.theme.TextPrimaryOnDark
import com.github.nacabaro.vbhelper.ui.theme.TextSecondaryOnDark
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

    Scaffold(
        topBar = {
            Column {
                TopBanner(text = stringResource(R.string.nav_digiline))
                PrimaryTabRow(
                    selectedTabIndex = tab,
                    containerColor = MaterialTheme.colorScheme.background,
                    indicator = {},
                    divider = {}
                ) {
                    DigilineTab(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        label = stringResource(R.string.ui_digiline_storage)
                    )
                    DigilineTab(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        label = stringResource(R.string.ui_digiline_wild)
                    )
                    DigilineTab(
                        selected = tab == 2,
                        onClick = { tab = 2 },
                        label = stringResource(R.string.ui_digiline_farms)
                    )
                }
            }
        },
        contentWindowInsets = WindowInsets.statusBars
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
        ) {
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

fun trackQuestTarget(navController: NavController, individualId: String) {
    if (runCatching { navController.getBackStackEntry(NavigationItems.World.route) }.getOrNull() == null) {
        navController.navigate(NavigationItems.World.route)
    }
    navController.getBackStackEntry(NavigationItems.World.route).savedStateHandle["radar-quest-target"] = individualId
    navController.popBackStack(NavigationItems.World.route, false)
}

@Composable
private fun DigilineTab(
    selected: Boolean,
    onClick: () -> Unit,
    label: String
) {
    val indicatorColor = VitalCyan
    Tab(
        selected = selected,
        onClick = onClick,
        selectedContentColor = VitalCyan,
        unselectedContentColor = TextSecondaryOnDark,
        modifier = Modifier.drawBehind {
            if (selected) {
                drawLine(
                    color = indicatorColor,
                    start = Offset(12.dp.toPx(), size.height - 2.dp.toPx()),
                    end = Offset(size.width - 12.dp.toPx(), size.height - 2.dp.toPx()),
                    strokeWidth = 3.dp.toPx()
                )
            }
        },
        text = { Text(label, style = MaterialTheme.typography.labelLarge) }
    )
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
                item.lastMessage ?: stringResource(R.string.ui_digiline_trust, item.trust),
                item.unreadCount, null, 0, 0) { onClick(item) }
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
    onClick: () -> Unit
) {
    val image = remember(sprite, width, height) {
        sprite?.let { runCatching { BitmapData(it, width, height).getBitmap().asImageBitmap() }.getOrNull() }
    }
    Surface(
        color = SurfaceDeepPurple.copy(alpha = 0.36f),
        shape = CutCornerShape(8.dp),
        border = BorderStroke(1.dp, SurfaceStroke.copy(alpha = 0.72f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
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
