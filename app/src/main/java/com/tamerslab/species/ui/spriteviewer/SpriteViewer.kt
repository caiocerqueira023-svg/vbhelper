package com.tamerslab.species.ui.spriteviewer

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.tamerslab.R
import com.tamerslab.components.*
import com.tamerslab.navigation.AppRoute
import com.tamerslab.navigation.NavigationItems
import com.tamerslab.ui.SpritePreviewPolicy
import kotlinx.coroutines.CancellationException

@Composable
fun SpriteViewer(navController: NavController, spriteViewerController: SpriteViewerController) {
    val debugEnabled = rememberDebugSettings().enabled
    LaunchedEffect(debugEnabled) {
        if (!debugEnabled && !navController.popBackStack()) navController.navigate(AppRoute.Settings) {
            popUpTo(AppRoute.Viewer) { inclusive = true }
        }
    }
    if (!debugEnabled) return
    var page by rememberSaveable { mutableIntStateOf(0) }
    var retry by remember { mutableIntStateOf(0) }
    var failed by remember(page, retry) { mutableStateOf(false) }
    val data by produceState<SpritePreviewPage?>(null, spriteViewerController, page, retry) {
        value = null
        try { value = spriteViewerController.getPage(page) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
    }
    val current = data
    val previews = remember(current) { current?.sprites.orEmpty().flatMap { sprite ->
        listOf("idle 1" to sprite.spriteIdle1, "idle 2" to sprite.spriteIdle2,
            "walk 1" to sprite.spriteWalk1, "walk 2" to sprite.spriteWalk2,
            "run 1" to sprite.spriteRun1, "run 2" to sprite.spriteRun2,
            "train 1" to sprite.spriteTrain1, "train 2" to sprite.spriteTrain2,
            "happy" to sprite.spriteHappy, "sleep" to sprite.spriteSleep,
            "attack" to sprite.spriteAttack, "dodge" to sprite.spriteDodge).map { (name, bytes) ->
            SpritePreviewFrame(sprite.id, name, sprite.width, sprite.height, bytes)
        }
    } }
    Scaffold(topBar = { TopBanner(stringResource(R.string.immersion_debug_viewer), onBackClick = { navController.popBackStack() }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when {
                failed -> CyberEmptyState(stringResource(R.string.immersion_debug_viewer_failed),
                    actionLabel = stringResource(R.string.app_retry), onAction = { retry++ })
                current == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                current.eligibleCount == 0 -> CyberEmptyState(if (current.totalCount == 0) stringResource(R.string.sprite_viewer_empty)
                    else stringResource(R.string.audit_sprite_skipped, current.totalCount),
                    actionLabel = stringResource(R.string.app_retry), onAction = { retry++ })
                else -> {
                    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { page-- }, enabled = page > 0) { Text(stringResource(R.string.audit_previous)) }
                        Text(stringResource(R.string.audit_sprite_page, page + 1,
                            (current.eligibleCount + SpritePreviewPolicy.PAGE_SIZE - 1) / SpritePreviewPolicy.PAGE_SIZE), Modifier.weight(1f))
                        TextButton(onClick = { page++ }, enabled = (page + 1) * SpritePreviewPolicy.PAGE_SIZE < current.eligibleCount) {
                            Text(stringResource(R.string.audit_next))
                        }
                    }
                    if (current.totalCount != current.eligibleCount) Text(stringResource(R.string.audit_sprite_skipped,
                        current.totalCount - current.eligibleCount), Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
                    LazyVerticalGrid(GridCells.Adaptive(140.dp), Modifier.weight(1f), contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(previews, key = { "${it.spriteId}:${it.name}" }) { frame ->
                            var decoded by remember(frame) { mutableStateOf(false) }
                            val bitmap by produceState<android.graphics.Bitmap?>(null, frame) {
                                value = spriteViewerController.decodeFrame(frame)
                                decoded = true
                            }
                            val label = stringResource(R.string.audit_sprite_frame, frame.spriteId, frame.name)
                            CyberPanel {
                                Text(label, style = MaterialTheme.typography.labelSmall)
                                Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
                                    if (bitmap != null) Image(requireNotNull(bitmap).asImageBitmap(), label, Modifier.fillMaxSize(), filterQuality = FilterQuality.None)
                                    else if (decoded) Text(stringResource(R.string.audit_frame_unavailable), style = MaterialTheme.typography.bodySmall)
                                    else CircularProgressIndicator(Modifier.size(24.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


