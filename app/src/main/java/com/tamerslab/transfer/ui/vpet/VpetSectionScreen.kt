package com.tamerslab.transfer.ui.vpet

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tamerslab.BuildConfig
import com.tamerslab.R
import com.tamerslab.components.CyberPanel
import com.tamerslab.components.TopBanner
import com.tamerslab.components.VitalButton
import com.tamerslab.components.VitalButtonStyle
import com.tamerslab.components.VitalSectionHeader
import com.tamerslab.components.showAppFeedback
import com.tamerslab.di.TamersLabApplication
import com.tamerslab.domain.vpet.VpetImportOutcome
import com.tamerslab.domain.vpet.VpetPendingRead
import com.tamerslab.hardware.vpet.VpetModel
import com.tamerslab.ui.theme.SurfaceStroke
import com.tamerslab.ui.theme.TextPrimaryOnDark
import com.tamerslab.ui.theme.TextSecondaryOnDark
import com.tamerslab.ui.theme.VitalCyan
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val LOG_TAG = "VpetSection"
private const val VISIBLE_LOG_LINES = 80

/**
 * VPet reader section (PLAN D2 / D6 / D8): pick the device model, connect the A-Com USB adapter
 * (Bluetooth stays available), read the device, then import the decoded partner into Storage.
 *
 * Reads whose species cannot be resolved stay in the repository's pending list at the bottom of
 * the screen, where they can be mapped to a catalog species or discarded. Debug builds add a
 * scripted read so the whole flow can be exercised without hardware.
 *
 * [initialCharacterId] is part of the transfer destination contract (deep links may carry a
 * partner). The reader does not need it, so it is deliberately not consumed here.
 */
@Composable
fun VpetSectionScreen(
    initialCharacterId: Long? = null,
    showHeader: Boolean = false,
) {
    val context = LocalContext.current
    val application = context.applicationContext as TamersLabApplication
    val preferences = remember(context) { VpetPreferences(context) }
    val scope = rememberCoroutineScope()
    val coordinator = remember(preferences) {
        VpetReadCoordinator(context.applicationContext, scope, preferences)
    }
    DisposableEffect(coordinator) {
        onDispose { coordinator.close() }
    }
    val repository = remember(application) { application.container.vpetRepository }
    val pendingReadsFlow = remember(repository) { repository.pendingReads }
    val pendingReads by pendingReadsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var importing by remember { mutableStateOf(false) }
    var pendingFromResult by remember { mutableStateOf<VpetPendingRead?>(null) }
    var mappingPending by remember { mutableStateOf<VpetPendingRead?>(null) }
    var discardingPending by remember { mutableStateOf<VpetPendingRead?>(null) }
    var logExpanded by rememberSaveable { mutableStateOf(false) }

    val importCurrentRead: () -> Unit = {
        val result = coordinator.result
        if (result != null && !importing) {
            importing = true
            pendingFromResult = null
            val snapshot = coordinator.snapshotFor(result)
            scope.launch {
                try {
                    when (val outcome = withContext(Dispatchers.IO) { repository.import(snapshot) }) {
                        is VpetImportOutcome.Imported -> {
                            coordinator.clearResult()
                            context.showAppFeedback(
                                R.string.vpet_import_success,
                                outcome.speciesName ?: snapshot.name ?: snapshot.modelLabel,
                            )
                        }
                        is VpetImportOutcome.Pending -> {
                            pendingFromResult = outcome.pending
                            context.showAppFeedback(R.string.vpet_import_pending)
                        }
                        is VpetImportOutcome.Failed ->
                            context.showAppFeedback(R.string.vpet_import_failed, outcome.message)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    Log.e(LOG_TAG, "V-Pet read import failed", failure)
                    context.showAppFeedback(R.string.vpet_import_failed_generic)
                } finally {
                    importing = false
                }
            }
        }
    }

    val handleMapped: (VpetPendingRead, VpetImportOutcome) -> Unit = { pending, outcome ->
        when (outcome) {
            is VpetImportOutcome.Imported -> {
                if (pendingFromResult?.id == pending.id) {
                    pendingFromResult = null
                    coordinator.clearResult()
                }
                context.showAppFeedback(
                    R.string.vpet_map_success,
                    outcome.speciesName ?: pending.snapshot.name ?: pending.snapshot.modelLabel,
                )
            }
            is VpetImportOutcome.Pending -> {
                if (pendingFromResult?.id == outcome.pending.id) {
                    pendingFromResult = outcome.pending
                }
                context.showAppFeedback(R.string.vpet_import_pending)
            }
            is VpetImportOutcome.Failed ->
                context.showAppFeedback(R.string.vpet_map_failed, outcome.message)
        }
    }

    Column(Modifier.fillMaxSize()) {
        if (showHeader) {
            TopBanner(text = stringResource(R.string.vpet_title))
        }
        VpetSectionBody(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            coordinator = coordinator,
            pendingReads = pendingReads,
            importing = importing,
            pendingFromResult = pendingFromResult,
            logExpanded = logExpanded,
            onToggleLog = { logExpanded = !logExpanded },
            onImport = importCurrentRead,
            onMapResultPending = { mappingPending = it },
            onDiscardResultPending = { discardingPending = it },
            onMapPending = { mappingPending = it },
            onDiscardPending = { discardingPending = it },
            onDismissResult = {
                pendingFromResult = null
                coordinator.clearResult()
            },
        )
    }

    mappingPending?.let { pending ->
        VpetSpeciesMappingDialog(
            pending = pending,
            onDismiss = { mappingPending = null },
            onMapped = { outcome ->
                mappingPending = null
                handleMapped(pending, outcome)
            },
        )
    }
    discardingPending?.let { pending ->
        VpetDiscardPendingDialog(
            pending = pending,
            onDismiss = { discardingPending = null },
            onDiscarded = {
                discardingPending = null
                if (pendingFromResult?.id == pending.id) {
                    pendingFromResult = null
                    coordinator.clearResult()
                }
                context.showAppFeedback(R.string.vpet_discard_success)
            },
        )
    }
}

@Composable
private fun VpetSectionBody(
    modifier: Modifier,
    coordinator: VpetReadCoordinator,
    pendingReads: List<VpetPendingRead>,
    importing: Boolean,
    pendingFromResult: VpetPendingRead?,
    logExpanded: Boolean,
    onToggleLog: () -> Unit,
    onImport: () -> Unit,
    onMapResultPending: (VpetPendingRead) -> Unit,
    onDiscardResultPending: (VpetPendingRead) -> Unit,
    onMapPending: (VpetPendingRead) -> Unit,
    onDiscardPending: (VpetPendingRead) -> Unit,
    onDismissResult: () -> Unit,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "vpet-intro") { VpetIntroPanel() }
        item(key = "vpet-model") { VpetModelSelector(coordinator) }
        item(key = "vpet-connection") { VpetConnectionPanel(coordinator) }
        item(key = "vpet-read") { VpetReadPanel(coordinator) }
        item(key = "vpet-log") {
            VpetProtocolLogPanel(coordinator, expanded = logExpanded, onToggle = onToggleLog)
        }
        coordinator.result?.let { result ->
            item(key = "vpet-result") {
                VpetResultCard(
                    result = result,
                    simulated = coordinator.simulatedRead,
                    importing = importing,
                    pending = pendingFromResult,
                    onImport = onImport,
                    onMapSpecies = { pendingFromResult?.let(onMapResultPending) },
                    onDiscardPending = { pendingFromResult?.let(onDiscardResultPending) },
                    onDismiss = onDismissResult,
                )
            }
        }
        coordinator.failure?.let { failure ->
            item(key = "vpet-failure") { VpetFailurePanel(failure) }
        }
        if (BuildConfig.DEBUG) {
            item(key = "vpet-debug") { VpetDebugPanel(coordinator) }
        }
        item(key = "vpet-pending-header") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                VpetPendingReadsHeader(pendingReads.size)
                VpetPendingReadsHint()
            }
        }
        if (pendingReads.isEmpty()) {
            item(key = "vpet-pending-empty") { VpetPendingReadsEmpty() }
        } else {
            items(pendingReads, key = { it.id }) { pending ->
                VpetPendingReadRow(
                    pending = pending,
                    onMap = { onMapPending(pending) },
                    onDiscard = { onDiscardPending(pending) },
                )
            }
        }
    }
}

@Composable
private fun VpetIntroPanel() {
    CyberPanel(Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.vpet_intro_title),
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimaryOnDark,
        )
        Text(
            text = stringResource(R.string.vpet_intro_body),
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondaryOnDark,
        )
        Text(
            text = stringResource(R.string.vpet_intro_battle),
            style = MaterialTheme.typography.bodySmall,
            color = VitalCyan,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VpetModelSelector(coordinator: VpetReadCoordinator) {
    CyberPanel(Modifier.fillMaxWidth()) {
        VitalSectionHeader(
            title = stringResource(R.string.vpet_model_title),
            detail = coordinator.learnedVersion(coordinator.selectedModel)?.let { version ->
                stringResource(R.string.vpet_model_learned_version, version)
            },
        )
        Text(
            text = stringResource(R.string.vpet_model_hint),
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondaryOnDark,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            VpetModel.entries.forEach { model ->
                FilterChip(
                    selected = model == coordinator.selectedModel,
                    onClick = { coordinator.selectModel(model) },
                    enabled = !coordinator.isReading,
                    label = { Text(model.displayName) },
                )
            }
        }
    }
}

@Composable
private fun VpetReadPanel(coordinator: VpetReadCoordinator) {
    CyberPanel(Modifier.fillMaxWidth(), active = coordinator.canRead) {
        VitalSectionHeader(
            title = stringResource(R.string.vpet_read_title),
            detail = if (coordinator.isReading) {
                stringResource(R.string.vpet_reading, coordinator.progress)
            } else null,
        )
        coordinator.lastStatusLine?.takeIf { it.isNotBlank() }?.let { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondaryOnDark,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (coordinator.isReading) {
            LinearProgressIndicator(
                progress = { coordinator.progress / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = VitalCyan,
                trackColor = SurfaceStroke,
            )
            VitalButton(
                onClick = coordinator::stopRead,
                style = VitalButtonStyle.DESTRUCTIVE,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.vpet_stop))
            }
        } else {
            VitalButton(
                onClick = { coordinator.startRead() },
                enabled = coordinator.canRead,
                style = VitalButtonStyle.PRIMARY,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(
                        if (coordinator.result != null) R.string.vpet_read_again
                        else R.string.vpet_read
                    )
                )
            }
            Text(
                text = stringResource(
                    if (coordinator.isConnected) R.string.vpet_read_hint
                    else R.string.vpet_read_connect_first
                ),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondaryOnDark,
            )
        }
    }
}

@Composable
private fun VpetProtocolLogPanel(
    coordinator: VpetReadCoordinator,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    CyberPanel(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VitalSectionHeader(
                title = stringResource(R.string.vpet_log_title),
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onToggle) {
                Text(
                    stringResource(
                        if (expanded) R.string.vpet_log_hide else R.string.vpet_log_show
                    )
                )
            }
        }
        if (expanded) {
            if (coordinator.protocolLog.isEmpty()) {
                Text(
                    text = stringResource(R.string.vpet_log_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondaryOnDark,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    coordinator.protocolLog.takeLast(VISIBLE_LOG_LINES).forEach { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = TextSecondaryOnDark,
                        )
                    }
                }
            }
        } else {
            Text(
                text = stringResource(R.string.vpet_log_lines, coordinator.protocolLog.size),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondaryOnDark,
            )
        }
    }
}

@Composable
private fun VpetDebugPanel(coordinator: VpetReadCoordinator) {
    CyberPanel(Modifier.fillMaxWidth()) {
        VitalSectionHeader(title = stringResource(R.string.vpet_debug_title))
        Text(
            text = stringResource(R.string.vpet_debug_hint),
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondaryOnDark,
        )
        VitalButton(
            onClick = { coordinator.simulateRead() },
            enabled = !coordinator.isReading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.vpet_simulate_read))
        }
    }
}
