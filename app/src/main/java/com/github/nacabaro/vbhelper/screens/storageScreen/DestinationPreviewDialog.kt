package com.github.nacabaro.vbhelper.screens.storageScreen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.gamesave.*
import com.github.nacabaro.vbhelper.source.gameSave.*
import kotlinx.coroutines.CancellationException

/** Plan 4.3/4.4 extension of Game archives: every selected partner, one fresh destination read, no execution. */
@Composable
internal fun DestinationPreviewDialog(
    archive: NativeArchiveView,
    archives: List<NativeArchiveView>,
    sources: LinkedGameSaveRepository,
    repository: DestinationPreviewRepository,
    onDismiss: () -> Unit,
    fixedSourceId: String? = null,
    initialContainer: SaveContainer = SaveContainer.BANK,
) {
    val linked by sources.sources.collectAsStateWithLifecycle(initialValue = null)
    var selectedId by remember(archive.snapshot.id, fixedSourceId) { mutableStateOf(fixedSourceId) }
    var container by remember(archive.snapshot.id, fixedSourceId) { mutableStateOf(initialContainer) }
    var selectedIds by remember(archive.snapshot.id) { mutableStateOf(setOf(archive.snapshot.id)) }
    var attempt by remember { mutableIntStateOf(0) }
    var prepared by remember { mutableStateOf<PreparedBatchDestinationPreview?>(null) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var readStatus by remember { mutableStateOf<Int?>(null) }
    val selected = linked?.firstOrNull { it.source.sourceId == selectedId }
    LaunchedEffect(archive.snapshot.id, selectedIds, selectedId, container, attempt) {
        val id = selectedId ?: return@LaunchedEffect
        val previous = prepared?.takeIf { it.snapshotIds.toSet() == selectedIds &&
            it.preview.destinationKey == id && it.preview.destinationContainer == container }
        prepared = null
        loading = true
        failed = false
        readStatus = null
        try {
            val next = repository.prepareBatch(selectedIds.sorted(), id, container)
            prepared = next
            if (previous != null) readStatus = if (previous.preview.fingerprint == next.preview.fingerprint)
                R.string.destination_unchanged else R.string.destination_rebuilt
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failed = true
        } finally {
            loading = false
        }
    }
    val stale = prepared?.let { selected == null || selected.source.lastError != null ||
        selected.observation?.contentRevision != it.preview.destinationRevision } ?: false
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.destination_title)) },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Text(archive.nickname ?: archive.record?.storedName?.takeIf(String::isNotBlank)
                        ?: stringResource(R.string.transfer_unnamed_record), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.destination_explanation), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (fixedSourceId != null) item {
                    Text(selected?.source?.label ?: stringResource(R.string.destination_loading), style = MaterialTheme.typography.titleSmall)
                }
                else item { Text(stringResource(R.string.destination_choose_save), style = MaterialTheme.typography.titleSmall) }
                if (linked == null) item { Text(stringResource(R.string.destination_loading)) }
                else if (linked!!.isEmpty()) item { Text(stringResource(R.string.destination_no_sources)) }
                items(if (fixedSourceId == null) linked.orEmpty() else emptyList(), key = { it.source.sourceId }) { view ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(
                        selected = selectedId == view.source.sourceId,
                        role = Role.RadioButton,
                        onClick = { selectedId = view.source.sourceId },
                    ), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RadioButton(selectedId == view.source.sourceId, onClick = null)
                        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                            Text(view.source.label)
                            if (view.source.lastError != null || view.observation == null)
                                Text(stringResource(R.string.destination_fresh_read_needed), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (selectedId != null) {
                    item { Text(stringResource(R.string.destination_choose_container), style = MaterialTheme.typography.titleSmall) }
                    items(listOf(SaveContainer.BANK, SaveContainer.PARTY)) { choice ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(container == choice,
                            role = Role.RadioButton, onClick = { container = choice }), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            RadioButton(container == choice, onClick = null)
                            Text(stringResource(if (choice == SaveContainer.BANK) R.string.destination_bank else R.string.destination_party),
                                modifier = Modifier.padding(vertical = 12.dp))
                        }
                    }
                }
                val others = archives.filter { it.snapshot.id != archive.snapshot.id }
                if (others.isNotEmpty()) {
                    item { Text(stringResource(R.string.destination_batch_partners), style = MaterialTheme.typography.titleSmall) }
                    item {
                        Text(stringResource(R.string.destination_batch_partners_explained), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    items(others, key = { it.snapshot.id }) { other ->
                        val checked = other.snapshot.id in selectedIds
                        val atLimit = selectedIds.size >= DestinationPreviewPlanner.MAX_BATCH_ITEMS
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
                            value = checked, enabled = checked || !atLimit, role = Role.Checkbox,
                            onValueChange = { selectedIds = if (it) selectedIds + other.snapshot.id else selectedIds - other.snapshot.id },
                        ), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Checkbox(checked, onCheckedChange = null, enabled = checked || !atLimit)
                            Column(Modifier.weight(1f).padding(vertical = 12.dp)) {
                                Text(other.nickname ?: other.record?.storedName?.takeIf(String::isNotBlank)
                                    ?: stringResource(R.string.transfer_unnamed_record))
                            }
                        }
                    }
                }
                if (loading) item { Text(stringResource(R.string.destination_loading)) }
                if (failed) item { Text(stringResource(R.string.destination_failed), color = MaterialTheme.colorScheme.error) }
                if (stale) item { Text(stringResource(R.string.destination_stale), color = MaterialTheme.colorScheme.error) }
                readStatus?.let { message -> item { Text(stringResource(message)) } }
                prepared?.preview?.let { batch ->
                    val single = batch.items.singleOrNull()
                    if (single != null) {
                        item {
                            Text(stringResource(formatVerdict(single)), style = MaterialTheme.typography.titleSmall)
                        }
                        item { Text(stringResource(R.string.destination_progress_title), style = MaterialTheme.typography.titleSmall) }
                        item {
                            Text(stringResource(single.progress.status.label()), style = MaterialTheme.typography.bodySmall,
                                color = if (single.progress.status == DestinationProgressStatus.NATIVE_CHANGES_UNRESOLVED)
                                    MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (single.progress.pendingNativeCount > 0) item {
                            Text(stringResource(R.string.destination_progress_native_count, single.progress.pendingNativeCount),
                                style = MaterialTheme.typography.bodySmall)
                        }
                        if (single.progress.differingDestinationRecords > 0) item {
                            Text(stringResource(R.string.destination_progress_destination_diff, single.progress.differingDestinationRecords),
                                style = MaterialTheme.typography.bodySmall)
                        }
                        item {
                            Text(stringResource(if (single.progress.targetAnchor != null)
                                R.string.destination_progress_anchor_retained else R.string.destination_progress_anchor_missing),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        items(single.blockers.filterNot { it in PROGRESS_BLOCKERS }.toList()) { blocker ->
                            Text(stringResource(blocker.label()), style = MaterialTheme.typography.bodySmall)
                        }
                        if (single.possibleNativeMatches > 0) item {
                            Text(stringResource(R.string.destination_possible_matches, single.possibleNativeMatches))
                        }
                        item { Text(stringResource(R.string.destination_fields_title), style = MaterialTheme.typography.titleSmall) }
                        items(single.fields.entries.toList()) { (field, disposition) ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(stringResource(field.label()), style = MaterialTheme.typography.labelLarge)
                                Text(stringResource(disposition.label()), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        item { Text(stringResource(R.string.destination_batch_summary, batch.items.size), style = MaterialTheme.typography.titleSmall) }
                        val shared = batch.commonBlockers.filterNot { it in PROGRESS_BLOCKERS }
                        if (shared.isNotEmpty()) {
                            item { Text(stringResource(R.string.destination_batch_shared), style = MaterialTheme.typography.labelLarge) }
                            items(shared.toList()) { blocker -> Text(stringResource(blocker.label()), style = MaterialTheme.typography.bodySmall) }
                        }
                        items(batch.items, key = { it.snapshotKey }) { item ->
                            val view = archives.firstOrNull { it.snapshot.id.toString() == item.snapshotKey }
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(view?.nickname ?: view?.record?.storedName?.takeIf(String::isNotBlank)
                                    ?: stringResource(R.string.transfer_unnamed_record), style = MaterialTheme.typography.titleSmall)
                                Text(stringResource(formatVerdict(item)), style = MaterialTheme.typography.bodySmall)
                                Text(stringResource(item.progress.status.label()), style = MaterialTheme.typography.bodySmall,
                                    color = if (item.progress.status == DestinationProgressStatus.NATIVE_CHANGES_UNRESOLVED)
                                        MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                                if (item.progress.pendingNativeCount > 0) Text(
                                    stringResource(R.string.destination_progress_native_count, item.progress.pendingNativeCount),
                                    style = MaterialTheme.typography.bodySmall)
                                if (item.progress.differingDestinationRecords > 0) Text(
                                    stringResource(R.string.destination_progress_destination_diff, item.progress.differingDestinationRecords),
                                    style = MaterialTheme.typography.bodySmall)
                                if (item.possibleNativeMatches > 0) Text(
                                    stringResource(R.string.destination_possible_matches, item.possibleNativeMatches),
                                    style = MaterialTheme.typography.bodySmall)
                                item.blockers.filterNot { it in PROGRESS_BLOCKERS || it in shared }.forEach { blocker ->
                                    Text(stringResource(blocker.label()), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text(stringResource(R.string.transfer_close)) } },
        dismissButton = {
            if (selectedId != null) TextButton(onClick = { attempt++ }, enabled = !loading) {
                Text(stringResource(R.string.destination_recheck))
            }
        },
    )
}

private fun formatVerdict(preview: DestinationPreview): Int =
    if (preview.sameNativeFormat) R.string.destination_same_format else R.string.destination_other_format

/** Rendered by the dedicated progress section instead of the generic blocker list. */
private val PROGRESS_BLOCKERS = setOf(DestinationBlocker.PROGRESS_CONTEXT_UNAVAILABLE,
    DestinationBlocker.NATIVE_ANCHOR_UNAVAILABLE, DestinationBlocker.NATIVE_PROGRESS_UNRESOLVED,
    DestinationBlocker.DESTINATION_RECORD_CHANGED, DestinationBlocker.PROGRESS_MAPPING_UNAVAILABLE)

private fun DestinationProgressStatus.label(): Int = when (this) {
    DestinationProgressStatus.CONTEXT_UNAVAILABLE -> R.string.destination_progress_unavailable
    DestinationProgressStatus.NO_RECORDED_CHANGES -> R.string.destination_progress_none
    DestinationProgressStatus.RECORDED_CHANGES_UNMAPPED -> R.string.destination_progress_recorded
    DestinationProgressStatus.NATIVE_CHANGES_UNRESOLVED -> R.string.destination_progress_unresolved
}

private fun DestinationBlocker.label(): Int = when (this) {
    DestinationBlocker.PROGRESS_CONTEXT_UNAVAILABLE -> R.string.destination_progress_unavailable
    DestinationBlocker.NATIVE_ANCHOR_UNAVAILABLE -> R.string.destination_progress_anchor_missing
    DestinationBlocker.NATIVE_PROGRESS_UNRESOLVED -> R.string.destination_progress_unresolved
    DestinationBlocker.DESTINATION_RECORD_CHANGED -> R.string.destination_progress_unresolved
    DestinationBlocker.PROGRESS_MAPPING_UNAVAILABLE -> R.string.destination_progress_recorded
    DestinationBlocker.BATCH_IDENTITY_CONFLICT -> R.string.destination_batch_conflict
    DestinationBlocker.WRITER_UNAVAILABLE -> R.string.destination_no_writer
    DestinationBlocker.INSERT_UNSUPPORTED -> R.string.destination_no_insert
    DestinationBlocker.CONVERSION_UNAVAILABLE -> R.string.destination_no_conversion
    DestinationBlocker.IDENTITY_REVIEW_REQUIRED -> R.string.destination_identity_review
    DestinationBlocker.POSSIBLE_EXISTING_INDIVIDUAL -> R.string.destination_existing
    DestinationBlocker.SOURCE_RECORD_UNCERTAIN -> R.string.destination_uncertain
    DestinationBlocker.NATIVE_DATA_MISSING -> R.string.destination_missing_native
    DestinationBlocker.FARM_OUT_OF_SCOPE -> R.string.destination_farm_excluded
}

private fun TransferField.label(): Int = when (this) {
    TransferField.NATIVE_RECORD -> R.string.destination_field_native
    TransferField.SPECIES -> R.string.destination_field_species
    TransferField.NAME -> R.string.destination_field_name
    TransferField.PERSONALITY -> R.string.destination_field_personality
    TransferField.PROGRESSION -> R.string.destination_field_progress
    TransferField.SKILLS_EQUIPMENT -> R.string.destination_field_skills
}

private fun TransferFieldDisposition.label(): Int = when (this) {
    TransferFieldDisposition.RETAINED_IN_STORAGE -> R.string.destination_retained
    TransferFieldDisposition.UNVALIDATED -> R.string.destination_unvalidated
    TransferFieldDisposition.UNMAPPED -> R.string.destination_unmapped
}
