package com.github.nacabaro.vbhelper.screens.transferScreen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.gamesave.NdsLauncherBanner
import com.github.nacabaro.vbhelper.navigation.TransferGame
import com.github.nacabaro.vbhelper.source.readCardDocument
import com.github.nacabaro.vbhelper.source.gameSave.NdsLauncherIconRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun NdsLauncherImportPrompt(game: TransferGame?, onFinished: (TransferGame) -> Unit) {
    val context = LocalContext.current
    val repository = remember(context) { NdsLauncherIconRepository(context) }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val target = game ?: return@rememberLauncherForActivityResult
        if (uri == null) onFinished(target) else scope.launch {
            busy = true
            error = false
            try {
                val banner = withContext(Dispatchers.IO) {
                    readCardDocument({ context.contentResolver.openInputStream(uri) }, NdsLauncherBanner::read)
                }
                repository.import(target, banner)
                onFinished(target)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = true }
            finally { busy = false }
        }
    }
    if (game == null) return
    AlertDialog(onDismissRequest = { if (!busy) onFinished(game) },
        title = { Text(stringResource(R.string.transfer_launcher_first_link_title)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.transfer_launcher_first_link_body, stringResource(gameTitle(game))))
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            if (error) Text(stringResource(R.string.transfer_launcher_rom_error), color = MaterialTheme.colorScheme.error)
        } },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                error = false
                picker.launch(arrayOf("*/*"))
            }) { Text(stringResource(R.string.transfer_launcher_choose_rom)) }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = { onFinished(game) }) {
                Text(stringResource(R.string.transfer_launcher_skip))
            }
        })
}
