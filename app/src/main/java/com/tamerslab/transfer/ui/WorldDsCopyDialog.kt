package com.tamerslab.transfer.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.tamerslab.R
import com.tamerslab.gamesave.WorldDsCopySelection
import com.tamerslab.transfer.data.gameSave.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun WorldDsCopyDialog(view: LinkedSaveView, repository: LinkedGameSaveRepository, onDismiss: () -> Unit) {
    val observation = view.observation ?: return
    val copies = observation.records.groupBy { it.nativeParameters?.saveBlock }.filterKeys { it != null }.toSortedMap(compareBy { it })
    var selected by remember(observation.contentRevision) { mutableStateOf(
        view.source.reviewedSaveCopy.takeIf { view.source.reviewedCopyRevision == observation.contentRevision }
            ?: WorldDsCopySelection.authoritativeCopy(observation)) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.world_ds_choose_copy)) },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { Text(stringResource(R.string.world_ds_copy_explanation)) }
                items(copies.keys.toList()) { copy ->
                    val records = copies.getValue(copy)
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected == copy, enabled = !busy,
                        role = Role.RadioButton, onClick = { selected = copy }), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RadioButton(selected == copy, onClick = null, enabled = !busy)
                        Column(Modifier.weight(1f).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.world_ds_copy_roster, requireNotNull(copy), records.size), style = MaterialTheme.typography.titleSmall)
                            records.take(5).forEach { record ->
                                Text(stringResource(R.string.world_ds_copy_member, record.storedName.ifBlank {
                                    stringResource(R.string.transfer_unnamed_record) }, record.level), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                if (error) item { Text(stringResource(R.string.world_ds_copy_failed), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = selected != null && !busy, onClick = {
                busy = true
                scope.launch {
                    try { repository.selectWorldDsCopy(view.source.sourceId, observation.contentRevision, requireNotNull(selected)); onDismiss() }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { error = true }
                    finally { busy = false }
                }
            }) { Text(stringResource(R.string.world_ds_use_copy)) }
        }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.transfer_close)) } })
}
