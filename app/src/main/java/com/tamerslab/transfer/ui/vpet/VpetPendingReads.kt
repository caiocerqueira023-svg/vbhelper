package com.tamerslab.transfer.ui.vpet

import android.content.Context
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tamerslab.R
import com.tamerslab.components.CyberPanel
import com.tamerslab.components.VitalButton
import com.tamerslab.components.VitalButtonStyle
import com.tamerslab.components.VitalSectionHeader
import com.tamerslab.di.TamersLabApplication
import com.tamerslab.domain.vpet.VpetImportOutcome
import com.tamerslab.domain.vpet.VpetPendingRead
import com.tamerslab.domain.vpet.VpetPendingReason
import com.tamerslab.domain.vpet.VpetSpeciesChoice
import com.tamerslab.ui.theme.StatusYellow
import com.tamerslab.ui.theme.TextPrimaryOnDark
import com.tamerslab.ui.theme.TextSecondaryOnDark
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

@Composable
internal fun VpetPendingReadsHeader(count: Int, modifier: Modifier = Modifier) {
    VitalSectionHeader(
        title = stringResource(R.string.vpet_pending_title),
        detail = count.takeIf { it > 0 }?.let { stringResource(R.string.vpet_pending_count, it) },
        modifier = modifier,
    )
}

@Composable
internal fun VpetPendingReadsHint(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.vpet_pending_hint),
        style = MaterialTheme.typography.bodySmall,
        color = TextSecondaryOnDark,
        modifier = modifier,
    )
}

@Composable
internal fun VpetPendingReadsEmpty(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.vpet_pending_empty),
        style = MaterialTheme.typography.bodySmall,
        color = TextSecondaryOnDark,
        modifier = modifier,
    )
}

/** One retained read: the protocol data, why it is pending and the two recovery actions. */
@Composable
internal fun VpetPendingReadRow(
    pending: VpetPendingRead,
    onMap: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    CyberPanel(modifier = modifier.fillMaxWidth()) {
        Text(
            text = pending.snapshot.name ?: stringResource(R.string.vpet_result_unknown),
            style = MaterialTheme.typography.titleSmall,
            color = TextPrimaryOnDark,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringResource(
                R.string.vpet_pending_details,
                pending.snapshot.modelLabel,
                pending.snapshot.version,
                pending.snapshot.index,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondaryOnDark,
        )
        Text(
            text = vpetPendingReasonText(pending),
            style = MaterialTheme.typography.bodySmall,
            color = StatusYellow,
        )
        Text(
            text = stringResource(
                R.string.vpet_pending_read_at,
                formatVpetReadDate(context, pending.snapshot.readAtMillis),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondaryOnDark,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            VitalButton(onClick = onMap, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.vpet_map_species))
            }
            VitalButton(onClick = onDiscard, style = VitalButtonStyle.DESTRUCTIVE) {
                Text(stringResource(R.string.vpet_discard))
            }
        }
    }
}

@Composable
private fun vpetPendingReasonText(pending: VpetPendingRead): String = when (pending.reason) {
    VpetPendingReason.UNKNOWN_SPECIES ->
        stringResource(R.string.vpet_pending_reason_unknown)
    VpetPendingReason.PARTICIPATION_BLOCKED ->
        pending.blockedSpeciesName.takeIf { it.isNotBlank() }?.let {
            stringResource(R.string.vpet_pending_reason_blocked, it)
        } ?: stringResource(R.string.vpet_pending_reason_blocked_generic)
}

/** Catalog search that resolves the retained read's species and imports it right away. */
@Composable
internal fun VpetSpeciesMappingDialog(
    pending: VpetPendingRead,
    onDismiss: () -> Unit,
    onMapped: (VpetImportOutcome) -> Unit,
) {
    val context = LocalContext.current
    val repository = remember(context) {
        (context.applicationContext as TamersLabApplication).container.vpetRepository
    }
    val scope = rememberCoroutineScope()
    var query by rememberSaveable(pending.id) { mutableStateOf(pending.snapshot.name.orEmpty()) }
    var choices by remember(pending.id) { mutableStateOf<List<VpetSpeciesChoice>>(emptyList()) }
    var searching by remember { mutableStateOf(true) }
    var mapping by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val failureFallback = stringResource(R.string.vpet_map_failed_generic)

    LaunchedEffect(query) {
        searching = true
        delay(SEARCH_DEBOUNCE_MILLIS)
        try {
            choices = withContext(Dispatchers.IO) { repository.searchSpecies(query.trim(), SEARCH_LIMIT) }
            errorMessage = null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Log.w(LOG_TAG, "V-Pet species search failed", failure)
            choices = emptyList()
            errorMessage = failureFallback
        } finally {
            searching = false
        }
    }

    val mapTo: (VpetSpeciesChoice) -> Unit = { choice ->
        if (!mapping) {
            mapping = true
            scope.launch {
                try {
                    val outcome = withContext(Dispatchers.IO) {
                        repository.mapPending(pending.id, choice.canonicalId)
                    }
                    onMapped(outcome)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    Log.w(LOG_TAG, "V-Pet species mapping failed", failure)
                    errorMessage = failureFallback
                } finally {
                    mapping = false
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!mapping) onDismiss() },
        title = { Text(stringResource(R.string.vpet_map_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.vpet_map_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondaryOnDark,
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.take(MAX_QUERY_LENGTH) },
                    label = { Text(stringResource(R.string.vpet_map_search)) },
                    singleLine = true,
                    enabled = !mapping,
                    modifier = Modifier.fillMaxWidth(),
                )
                errorMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                when {
                    searching -> Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 96.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                    choices.isEmpty() -> Text(
                        text = stringResource(R.string.vpet_map_no_results),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    else -> LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                        items(choices, key = { it.canonicalId }) { choice ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !mapping) { mapTo(choice) }
                                    .padding(horizontal = 4.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = choice.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TextPrimaryOnDark,
                                )
                                choice.level?.takeIf { it.isNotBlank() }?.let { level ->
                                    Text(
                                        text = level,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondaryOnDark,
                                    )
                                }
                            }
                        }
                    }
                }
                if (mapping) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !mapping) {
                Text(stringResource(R.string.ui_cancel))
            }
        },
    )
}

/** Confirmation before a retained read (and its provenance) is destroyed. */
@Composable
internal fun VpetDiscardPendingDialog(
    pending: VpetPendingRead,
    onDismiss: () -> Unit,
    onDiscarded: () -> Unit,
) {
    val context = LocalContext.current
    val repository = remember(context) {
        (context.applicationContext as TamersLabApplication).container.vpetRepository
    }
    val scope = rememberCoroutineScope()
    var discarding by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!discarding) onDismiss() },
        title = { Text(stringResource(R.string.vpet_discard_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(
                        R.string.vpet_discard_body,
                        pending.snapshot.name ?: pending.snapshot.modelLabel,
                    )
                )
                if (failed) {
                    Text(
                        text = stringResource(R.string.vpet_discard_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !discarding,
                onClick = {
                    discarding = true
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { repository.discardPending(pending.id) }
                            onDiscarded()
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            Log.w(LOG_TAG, "V-Pet pending read discard failed", failure)
                            failed = true
                        } finally {
                            discarding = false
                        }
                    }
                },
            ) {
                Text(stringResource(R.string.vpet_discard))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !discarding) {
                Text(stringResource(R.string.ui_cancel))
            }
        },
    )
}

private fun formatVpetReadDate(context: Context, readAtMillis: Long): String {
    val date = Date(readAtMillis)
    val datePart = android.text.format.DateFormat.getMediumDateFormat(context).format(date)
    val timePart = android.text.format.DateFormat.getTimeFormat(context).format(date)
    return "$datePart $timePart"
}

private const val LOG_TAG = "VpetPendingReads"
private const val SEARCH_DEBOUNCE_MILLIS = 220L
private const val SEARCH_LIMIT = 20
private const val MAX_QUERY_LENGTH = 80
