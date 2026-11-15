package com.tamerslab.transfer.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.tamerslab.R
import com.tamerslab.components.CyberEmptyState
import com.tamerslab.components.CyberPanel
import com.tamerslab.components.TopBanner
import com.tamerslab.components.VitalButton
import com.tamerslab.components.VitalSectionHeader
import com.tamerslab.components.showAppFeedback
import com.tamerslab.di.TamersLabApplication
import com.tamerslab.gamesave.ObservationNotice
import com.tamerslab.gamesave.RecordMembership
import com.tamerslab.gamesave.SaveContainer
import com.tamerslab.navigation.NavigationItems
import com.tamerslab.navigation.TransferGame
import com.tamerslab.navigation.TransferGameCatalog
import com.tamerslab.transfer.data.gameSave.AndroidSaveDocuments
import com.tamerslab.transfer.data.gameSave.LinkedSaveView
import com.tamerslab.transfer.data.gameSave.NdsLauncherIconRepository
import com.tamerslab.transfer.data.gameSave.OpenGameSaveDocument
import com.tamerslab.transfer.data.gameSave.TransferGameCapabilities
import com.tamerslab.gamesave.TimeStrangerSaveReader
import com.tamerslab.gamesave.WorldDsSaveReader
import java.util.Date
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Read-only save list: linked saves show their latest persisted rosters and capabilities. */
@Composable
fun TransferScreen(showHeader: Boolean = true) {
    val context = LocalContext.current
    val application = context.applicationContext as TamersLabApplication
    val repository = remember { application.container.linkedGameSaves }
    val documents = remember(application) { AndroidSaveDocuments(application) }
    val launcherIcons = remember(application) { NdsLauncherIconRepository(application) }
    val previewRepository = remember(application) { application.container.saveImportPreviews }
    val savedPreviews by previewRepository.savedPreviews.collectAsStateWithLifecycle(initialValue = emptyList())
    var importPreviewRequest by remember { mutableStateOf<ImportPreviewRequest?>(null) }
    var selectedByObservation by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    var sourceSnapshot by remember { mutableStateOf<List<LinkedSaveView>?>(null) }
    val sources = sourceSnapshot.orEmpty()
    val busySources by repository.refreshing.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var refreshingAll by remember { mutableStateOf(false) }
    var linking by remember { mutableStateOf(false) }
    var loadFailed by remember { mutableStateOf(false) }
    var loadAttempt by remember { mutableStateOf(0) }
    var pendingRom by rememberSaveable { mutableStateOf<TransferGame?>(null) }
    var relinkId by rememberSaveable { mutableStateOf<String?>(null) }
    var expandedSources by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var comparisonId by rememberSaveable { mutableStateOf<String?>(null) }
    var copyReviewId by rememberSaveable { mutableStateOf<String?>(null) }
    var repairPc by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<LinkedSaveView?>(null) }
    var unlinkTarget by remember { mutableStateOf<LinkedSaveView?>(null) }
    var folderTruncated by remember { mutableStateOf(false) }

    suspend fun perform(action: suspend () -> Unit) {
        try {
            action()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            context.showAppFeedback(R.string.transfer_action_failed)
        }
    }

    suspend fun refreshAll() {
        if (refreshingAll) return
        refreshingAll = true
        try { perform { repository.refreshAll() } } finally { refreshingAll = false }
    }

    LaunchedEffect(repository, loadAttempt) {
        try {
            repository.sources.collect { sourceSnapshot = it; loadFailed = false }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) { loadFailed = true }
    }

    val filePicker = rememberLauncherForActivityResult(OpenGameSaveDocument()) { uri: Uri? ->
        val repairId = relinkId
        relinkId = null
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            linking = true
            try {
                documents.persistTransferGrant(uri)
                val document = documents.describe(uri)
                val id = if (repairId == null) repository.link(document) else {
                    repository.relink(repairId, document)
                    repairId
                }
                repository.refresh(id)
                expandedSources = (expandedSources + id).distinct()
                if (repairId == null) {
                    val linkedGame = try {
                        TransferGameCatalog.identify(repository.readFresh(id).gameId, document.name)
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { null }
                    if (linkedGame != null && launcherIcons.supports(linkedGame) && !launcherIcons.hasIcon(linkedGame))
                        pendingRom = linkedGame
                }
            } catch (_: TimeoutCancellationException) {
                context.showAppFeedback(R.string.transfer_error_timeout)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: SecurityException) {
                context.showAppFeedback(R.string.transfer_grant_failed)
            } catch (_: IllegalArgumentException) {
                context.showAppFeedback(R.string.transfer_relink_different)
            } catch (_: Exception) {
                context.showAppFeedback(R.string.transfer_action_failed)
            } finally { linking = false }
        }
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree: Uri? ->
        if (tree == null) return@rememberLauncherForActivityResult
        scope.launch {
            linking = true
            try {
                application.container.saveLocations.linkTree(tree)
                repository.refreshAll()
            } catch (_: TimeoutCancellationException) {
                context.showAppFeedback(R.string.transfer_error_timeout)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: SecurityException) {
                context.showAppFeedback(R.string.transfer_grant_failed)
            } catch (_: Exception) {
                context.showAppFeedback(R.string.transfer_action_failed)
            } finally { linking = false }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner, repository) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            refreshAll()
            awaitCancellation()
        }
    }

    Scaffold(topBar = {
        if (showHeader) TopBanner(
            text = stringResource(R.string.transfer_title),
            subtitle = stringResource(R.string.transfer_subtitle),
        )
    }) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    VitalSectionHeader(
                        title = stringResource(R.string.transfer_linked_saves),
                        detail = pluralStringResource(R.plurals.transfer_workspace_save_count, sources.size, sources.size),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        VitalButton(
                            onClick = { relinkId = null; filePicker.launch(arrayOf("*/*")) },
                            enabled = !linking,
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.transfer_link_file)) }
                        VitalButton(
                            onClick = { folderPicker.launch(null) },
                            enabled = !linking,
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.transfer_link_folder)) }
                    }
                    VitalButton(
                        onClick = { scope.launch { refreshAll() } },
                        enabled = !refreshingAll && sources.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (refreshingAll) CircularProgressIndicator(
                            Modifier.padding(end = 8.dp).size(18.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.transfer_refresh_all))
                    }
                    if (linking) CircularProgressIndicator(Modifier.size(24.dp))
                    if (folderTruncated) {
                        Text(
                            stringResource(R.string.transfer_folder_truncated),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item(key = "locations") { TransferLocationsPanel() }
            if (loadFailed) {
                item {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Text(stringResource(R.string.transfer_list_failed), color = MaterialTheme.colorScheme.error)
                        VitalButton(onClick = { loadAttempt++ }) { Text(stringResource(R.string.transfer_refresh)) }
                    }
                }
            } else if (sourceSnapshot == null) {
                item { CircularProgressIndicator(Modifier.padding(16.dp)) }
            } else if (sources.isEmpty()) {
                item {
                    CyberEmptyState(
                        stringResource(R.string.transfer_empty),
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    )
                }
            } else {
                sources.forEach { view ->
                    val id = view.source.sourceId
                    val expanded = id in expandedSources
                    val observationKey = "$id:${view.observation?.contentRevision}"
                    val selected = selectedByObservation[observationKey].orEmpty()
                    item(key = "source:$id") {
                        TransferSourceCard(
                            view = view,
                            busy = view.source.sourceId in busySources,
                            onRefresh = { scope.launch { perform { repository.refresh(id) } } },
                            onRelink = { if (view.source.documentUri.startsWith("companion://")) repairPc = true
                                else { relinkId = id; filePicker.launch(arrayOf("*/*")) } },
                            onRename = { renameTarget = view },
                            onUnlink = { unlinkTarget = view },
                            expanded = expanded,
                            onToggleRoster = { expandedSources = if (expanded) expandedSources - id else expandedSources + id },
                             onCompare = { comparisonId = id },
                             onReviewCopy = { copyReviewId = id },
                            canCompare = sources.any { other -> other.source.sourceId != id && compatibleObservations(view, other) },
                            selectedCount = selected.size,
                            onPreviewImport = {
                                view.observation?.let { observation ->
                                    val shown = shownRecordKeys(observation)
                                    importPreviewRequest = ImportPreviewRequest.Fresh(id, observation.contentRevision,
                                        selected.filter { it in shown })
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        )
                    }
                    if (expanded) {
                        SaveContainer.entries.forEach { container ->
                            val records = view.observation?.let { shownRecords(it, container) }.orEmpty()
                            if (records.isNotEmpty()) {
                                item(key = "container:$id:$container") {
                                    VitalSectionHeader(title = stringResource(containerLabel(container)), modifier = Modifier.padding(horizontal = 16.dp))
                                }
                                items(records, key = { "record:$id:${it.recordKey}" }) { record ->
                                    ObservedRecordRow(record, Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                                        selected = record.recordKey in selected, onSelected = if (view.observation?.capabilities?.canImportSnapshot != true) null else { checked ->
                                            if (checked && selected.size >= 128) context.showAppFeedback(R.string.transfer_import_selection_limit)
                                            else selectedByObservation = selectedByObservation + (observationKey to
                                                if (checked) selected + record.recordKey else selected - record.recordKey)
                                        })
                                }
                            }
                        }
                    }
                }
            }
            if (savedPreviews.isNotEmpty()) {
                item { VitalSectionHeader(title = stringResource(R.string.transfer_saved_previews), modifier = Modifier.padding(horizontal = 16.dp)) }
                items(savedPreviews, key = { "draft:${it.draftId}" }) { draft ->
                    TextButton(onClick = { importPreviewRequest = ImportPreviewRequest.Saved(draft.draftId) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        Text(draft.sourceLabel)
                    }
                }
            }
        }
    }

    renameTarget?.let { target ->
        var label by remember(target.source.sourceId) { mutableStateOf(target.source.label) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(stringResource(R.string.transfer_rename_title)) },
            text = {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it.take(160) },
                    label = { Text(stringResource(R.string.transfer_rename_title)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(enabled = label.isNotBlank(), onClick = {
                    scope.launch { perform { repository.rename(target.source.sourceId, label) } }
                    renameTarget = null
                }) { Text(stringResource(R.string.transfer_save_button)) }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text(stringResource(R.string.ui_cancel)) } },
        )
    }
    unlinkTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { unlinkTarget = null },
            title = { Text(stringResource(R.string.transfer_unlink)) },
            text = { Text(stringResource(R.string.transfer_unlink_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { perform { repository.unlink(target.source.sourceId) } }
                    unlinkTarget = null
                }) { Text(stringResource(R.string.transfer_unlink)) }
            },
            dismissButton = { TextButton(onClick = { unlinkTarget = null }) { Text(stringResource(R.string.ui_cancel)) } },
        )
    }
    sources.firstOrNull { it.source.sourceId == comparisonId }?.let { target ->
        SourceComparisonDialog(target, sources.filter { it.source.sourceId != comparisonId && compatibleObservations(target, it) },
            onDismiss = { comparisonId = null })
    }
    sources.firstOrNull { it.source.sourceId == copyReviewId }?.let { target ->
        WorldDsCopyDialog(target, repository, onDismiss = { copyReviewId = null })
    }
    importPreviewRequest?.let { request ->
        SaveImportPreviewDialog(request, previewRepository, application.container.gameImportAcquisition,
            onDismiss = { importPreviewRequest = null })
    }
    if (repairPc) PcPairingDialog(onDismiss = { repairPc = false })
    NdsLauncherImportPrompt(pendingRom) { pendingRom = null }
}

@Composable
private fun TransferSourceCard(
    view: LinkedSaveView,
    busy: Boolean,
    onRefresh: () -> Unit,
    onRelink: () -> Unit,
    onRename: () -> Unit,
    onUnlink: () -> Unit,
    expanded: Boolean,
    onToggleRoster: () -> Unit,
    onCompare: () -> Unit,
    onReviewCopy: () -> Unit,
    canCompare: Boolean,
    selectedCount: Int,
    onPreviewImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val dateFormatter = remember(context) { android.text.format.DateFormat.getDateFormat(context) }
    val timeFormatter = remember(context) { android.text.format.DateFormat.getTimeFormat(context) }
    fun formatTime(time: Long): String {
        val date = Date(time)
        return "${dateFormatter.format(date)} ${timeFormatter.format(date)}"
    }
    CyberPanel(modifier = modifier, active = view.observation != null) {
        VitalSectionHeader(
            title = view.source.label,
            detail = view.observation?.let {
                val shown = shownRecords(it)
                stringResource(
                    R.string.transfer_roster_counts,
                    shown.count { record -> record.membership == RecordMembership.ACTIVE_RECORD },
                    shown.count { record -> record.membership != RecordMembership.ACTIVE_RECORD },
                )
            },
        )
        view.observation?.let { observation ->
            Text(
                stringResource(when (observation.gameId) {
                    TimeStrangerSaveReader.GAME_ID -> R.string.transfer_time_stranger
                    WorldDsSaveReader.GAME_ID -> R.string.transfer_world_ds
                    else -> R.string.transfer_unknown_game
                }),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val successAt = view.source.lastSuccessAt
        Text(
            if (successAt != null) stringResource(R.string.transfer_last_read, formatTime(successAt))
            else stringResource(R.string.transfer_never_read),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        view.source.lastError?.let { error ->
            Text(
                stringResource(if (view.source.documentUri.startsWith("companion://") && error == "PERMISSION") R.string.pc_permission else saveErrorResource(error)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        val interruptedRead = (view.source.lastAttemptAt ?: Long.MIN_VALUE) > (successAt ?: Long.MIN_VALUE)
        if (view.observation != null && (view.source.lastError != null || busy || interruptedRead)) {
            Text(stringResource(R.string.transfer_cached), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VitalButton(onClick = onRefresh, enabled = !busy, modifier = Modifier.weight(1f)) {
                if (busy) CircularProgressIndicator(
                    Modifier.padding(end = 8.dp).size(18.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.transfer_refresh))
            }
            VitalButton(onClick = onRelink, enabled = !busy, modifier = Modifier.weight(1f)) {
                Text(stringResource(if (view.source.documentUri.startsWith("companion://")) R.string.pc_repair else R.string.transfer_relink))
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onRename) { Text(stringResource(R.string.transfer_rename)) }
            TextButton(onClick = onUnlink) { Text(stringResource(R.string.transfer_unlink)) }
            if (canCompare) TextButton(onClick = onCompare) { Text(stringResource(R.string.transfer_compare)) }
        }
        view.observation?.let { observation ->
            observation.notices.map { notice -> when (notice) {
                ObservationNotice.SPECIES_MAPPING_PENDING -> R.string.transfer_species_mapping_pending
                ObservationNotice.OWNERSHIP_RESEARCH_PENDING, ObservationNotice.FARM_RESEARCH_PENDING -> R.string.transfer_ownership_research_pending
                ObservationNotice.IDENTITY_RESEARCH_PENDING -> R.string.transfer_identity_research_pending
                ObservationNotice.SAVE_INTEGRITY_UNVERIFIED -> R.string.transfer_integrity_unverified
                ObservationNotice.SAVE_BLOCK_SELECTION_REQUIRED -> R.string.transfer_save_copy_review
                ObservationNotice.SAVE_COPY_AUTO_SELECTED -> R.string.transfer_save_copy_auto
                ObservationNotice.SAVE_COPY_USER_CONFIRMED -> R.string.world_ds_copy_confirmed
            } }.distinct().forEach { notice ->
                Text(
                    stringResource(notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                stringResource(if (observation.capabilities.provisionalSnapshotCopy) R.string.transfer_snapshot_copy_only else R.string.transfer_view_only),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            VitalButton(
                onClick = onToggleRoster,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(
                    if (expanded) R.string.transfer_hide_roster else R.string.transfer_roster))
            }
            if (TransferGameCapabilities.isNdsStory(observation) &&
                observation.records.mapNotNull { it.nativeParameters?.saveBlock }.distinct().size > 1) {
                TextButton(onClick = onReviewCopy, enabled = !busy) { Text(stringResource(R.string.world_ds_choose_copy)) }
            }
            if (observation.capabilities.canImportSnapshot) {
                Text(stringResource(R.string.transfer_import_select_hint), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                VitalButton(onClick = onPreviewImport, enabled = selectedCount > 0 && !busy, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.transfer_import_preview, selectedCount))
                }
            }
        }
    }
}

