package com.tamerslab.transfer.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tamerslab.R
import com.tamerslab.components.showAppFeedback
import com.tamerslab.di.TamersLabApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

private data class BackupEntry(val operationId: String, val sourceLabel: String?, val createdAt: Long, val sent: Boolean)

@Composable
internal fun GameMoveBackupsScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as TamersLabApplication
    val moves = app.container.experimentalGameMoves
    val sends = app.container.experimentalGameSends
    val scope = rememberCoroutineScope()
    var backups by remember { mutableStateOf<List<BackupEntry>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var exporting by remember { mutableStateOf(false) }
    LaunchedEffect(moves, sends, attempt) {
        try {
            backups = (moves.backups().map { BackupEntry(it.operationId, it.sourceLabel, it.createdAt, false) } +
                sends.backups().map { BackupEntry(it.operationId, it.sourceLabel, it.createdAt, true) })
                .sortedByDescending { it.createdAt }
            failed = false
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
    }
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
        val operation = selected
        selected = null
        if (uri != null && operation != null) scope.launch {
            exporting = true
            try {
                val entry = backups.orEmpty().first { it.operationId == operation }
                val bytes = if (entry.sent) sends.backupBytes(operation) else moves.backupBytes(operation)
                try { withContext(Dispatchers.IO) {
                    requireNotNull(context.contentResolver.openOutputStream(uri, "wt")).use { it.write(bytes); it.flush() }
                } } finally { bytes.fill(0) }
                context.showAppFeedback(R.string.transfer_backup_exported)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { context.showAppFeedback(R.string.transfer_action_failed) }
            finally { exporting = false }
        }
    }
    Column(Modifier.fillMaxSize()) {
        if (exporting) LinearProgressIndicator(Modifier.fillMaxWidth())
        when {
            failed -> TextButton(onClick = { attempt++ }) { Text(stringResource(R.string.transfer_refresh)) }
            backups == null -> CircularProgressIndicator(Modifier.padding(16.dp))
            backups.orEmpty().isEmpty() -> Text(stringResource(R.string.transfer_backups_empty), Modifier.padding(16.dp))
            else -> LazyColumn(Modifier.weight(1f)) {
                items(backups.orEmpty(), key = { it.operationId }) { backup ->
                    ListItem(headlineContent = { Text(backup.sourceLabel ?: stringResource(R.string.transfer_game_world_ds)) },
                        supportingContent = { Text(android.text.format.DateFormat.getMediumDateFormat(context).format(Date(backup.createdAt)) +
                            " " + android.text.format.DateFormat.getTimeFormat(context).format(Date(backup.createdAt))) },
                        trailingContent = { TextButton(enabled = !exporting, onClick = {
                            selected = backup.operationId; create.launch("world-ds-original.sav")
                        }) { Text(stringResource(R.string.transfer_backup_export)) } })
                }
            }
        }
    }
}
