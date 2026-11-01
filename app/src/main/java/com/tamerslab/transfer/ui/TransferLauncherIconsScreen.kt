package com.tamerslab.transfer.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tamerslab.R
import com.tamerslab.components.showAppFeedback
import com.tamerslab.gamesave.NdsLauncherBanner
import com.tamerslab.navigation.TransferGame
import com.tamerslab.species.data.readCardDocument
import com.tamerslab.transfer.data.gameSave.NdsLauncherIconRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Optional title metadata setup, separate from every normal save/individual transfer. */
@Composable
internal fun TransferLauncherIconsScreen() {
    val context = LocalContext.current
    val repository = remember(context) { NdsLauncherIconRepository(context) }
    val scope = rememberCoroutineScope()
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val game = TransferGame.entries.firstOrNull { it.name == selected }
        selected = null
        if (uri != null && game != null) scope.launch {
            busy = true
            try {
                val banner = readCardDocument({ context.contentResolver.openInputStream(uri) }, NdsLauncherBanner::read)
                repository.import(game, banner)
                revision++
                context.showAppFeedback(R.string.transfer_launcher_saved)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { context.showAppFeedback(R.string.transfer_error_format) }
            finally { busy = false }
        }
    }
    Column(Modifier.fillMaxSize()) {
        Text(stringResource(R.string.transfer_launcher_hint), Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(Modifier.weight(1f)) {
            listOf(TransferGame.WORLD_DS, TransferGame.DAWN, TransferGame.DUSK).forEach { game -> item(key = game.name) {
                ListItem(headlineContent = { Text(stringResource(gameTitle(game))) },
                    leadingContent = { TransferOriginIcon(game, Modifier.size(40.dp), revision) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).clickable(enabled = !busy) {
                        selected = game.name; picker.launch(arrayOf("*/*"))
                    })
            } }
        }
    }
}
