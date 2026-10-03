package com.github.nacabaro.vbhelper.screens.cardScreen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.source.CardBatchImportState
import com.github.nacabaro.vbhelper.ui.theme.TextSecondaryOnDark
import com.github.nacabaro.vbhelper.ui.theme.VitalCyan

@Composable
internal fun CardBatchImportPanel(state: CardBatchImportState, onStop: () -> Unit, onDismiss: () -> Unit,
                                  modifier: Modifier = Modifier) {
    if (state.total == 0) return
    var showIssues by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(when {
                state.stopping -> stringResource(R.string.card_import_stopping)
                state.preparing -> stringResource(R.string.card_import_preparing)
                state.isRunning -> stringResource(R.string.card_import_progress, minOf(state.completed + 1, state.total), state.total)
                state.cancelled -> stringResource(R.string.card_import_stopped, state.completed, state.total)
                else -> stringResource(R.string.card_import_complete)
            }, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), color = VitalCyan)
            if (state.isRunning) TextButton(onClick = onStop, enabled = !state.stopping) {
                Text(stringResource(R.string.card_import_stop))
            } else IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, stringResource(R.string.card_import_dismiss))
            }
        }
        if (state.isRunning) {
            if (state.preparing) LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = VitalCyan)
            else LinearProgressIndicator(progress = { state.completed.toFloat() / state.total },
                modifier = Modifier.fillMaxWidth(), color = VitalCyan)
            state.currentName?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = TextSecondaryOnDark,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Text(stringResource(R.string.card_import_summary, state.added, state.updated, state.failed),
            style = MaterialTheme.typography.labelMedium, color = TextSecondaryOnDark)
        if (!state.isRunning && state.issues.isNotEmpty()) TextButton(onClick = { showIssues = true },
            contentPadding = PaddingValues(0.dp)) {
            Text(stringResource(R.string.card_import_show_issues, state.issues.size))
        }
    }
    if (showIssues) AlertDialog(onDismissRequest = { showIssues = false },
        title = { Text(stringResource(R.string.card_import_issues_title)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(state.issues) { issue ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(issue.displayName ?: stringResource(R.string.card_import_file_number, issue.fileNumber),
                            style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(if (issue.originOnly) R.string.card_import_origin_issue else R.string.card_import_file_issue),
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { showIssues = false }) { Text(stringResource(R.string.ui_close)) } })
}
