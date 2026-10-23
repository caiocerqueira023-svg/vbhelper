package com.github.nacabaro.vbhelper.screens.transferScreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.domain.personality.DigimonPersonalityType
import com.github.nacabaro.vbhelper.gamesave.ImportBlocker
import com.github.nacabaro.vbhelper.gamesave.ImportItemAction
import com.github.nacabaro.vbhelper.gamesave.TimeStrangerSaveReader
import com.github.nacabaro.vbhelper.source.gameSave.ImportSourceChangedException
import com.github.nacabaro.vbhelper.source.gameSave.SavedImportPreview
import com.github.nacabaro.vbhelper.source.gameSave.SaveImportPreviewRepository
import com.github.nacabaro.vbhelper.source.gameSave.GameImportAcquisitionRepository
import com.github.nacabaro.vbhelper.source.gameSave.ImportIdentityReviewRequiredException
import com.github.nacabaro.vbhelper.source.gameSave.ImportPreviewInvalidException
import com.github.nacabaro.vbhelper.source.gameSave.NativeRefreshReview
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch

internal sealed interface ImportPreviewRequest {
    data class Fresh(val sourceId: String, val revision: String, val keys: List<String>) : ImportPreviewRequest
    data class Saved(val draftId: String) : ImportPreviewRequest
}

@Composable
internal fun SaveImportPreviewDialog(request: ImportPreviewRequest, repository: SaveImportPreviewRepository,
    acquisition: GameImportAcquisitionRepository, onDismiss: () -> Unit) {
    var result by remember(request) { mutableStateOf<SavedImportPreview?>(null) }
    var error by remember(request) { mutableStateOf<Int?>(null) }
    var retry by remember(request) { mutableStateOf(0) }
    var committing by remember(request) { mutableStateOf(false) }
    var completed by remember(request) { mutableStateOf(false) }
    var refreshed by remember(request) { mutableStateOf(false) }
    var refreshReview by remember(request) { mutableStateOf<NativeRefreshReview?>(null) }
    val scope = rememberCoroutineScope()
    val language = LocalConfiguration.current.locales[0].toLanguageTag()
    LaunchedEffect(request, retry) {
        error = null
        try {
            result = when (request) {
                is ImportPreviewRequest.Fresh -> repository.prepare(request.sourceId, request.revision, request.keys)
                is ImportPreviewRequest.Saved -> requireNotNull(repository.load(request.draftId))
            }
        } catch (_: ImportSourceChangedException) {
            error = R.string.transfer_import_source_changed
        } catch (_: TimeoutCancellationException) {
            error = R.string.transfer_error_timeout
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = R.string.transfer_import_preview_failed }
    }
    refreshReview?.let { review ->
        NativeRefreshReviewDialog(review, acquisition, onDismiss = { refreshReview = null }, onCompleted = {
            refreshReview = null; completed = true; refreshed = true; error = null
        })
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.transfer_import_preview_title)) },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (error != null) item { Text(stringResource(requireNotNull(error)), color = MaterialTheme.colorScheme.error) }
                else if (result == null) item { CircularProgressIndicator() }
                result?.let { saved ->
                    val preview = saved.preview
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(saved.draft.sourceLabel, style = MaterialTheme.typography.titleSmall)
                            Text(stringResource(R.string.transfer_import_copy_explanation))
                            if (preview.requiresIdentityReview) Text(stringResource(R.string.transfer_provisional_identity), style = MaterialTheme.typography.bodySmall)
                            if (request is ImportPreviewRequest.Saved) Text(stringResource(R.string.transfer_import_historical))
                            Text(stringResource(if (refreshed) R.string.transfer_refresh_completed else if (completed) R.string.transfer_import_completed
                                else R.string.transfer_import_saved_review))
                            if (preview.mappingPending) Text(stringResource(R.string.transfer_import_mapping_pending))
                            if (preview.items.any { it.record.personalityId == null })
                                Text(stringResource(R.string.world_ds_personality_initialization), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    items(preview.items, key = { it.record.recordKey }) { item ->
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ObservedRecordRow(item.record)
                            if (preview.gameId == TimeStrangerSaveReader.GAME_ID) {
                                val personality = DigimonPersonalityType.entries.singleOrNull { it.number == item.record.personalityId }
                                if (personality != null) Text(stringResource(R.string.transfer_import_native_personality, personality.displayName(language)))
                            }
                            if (item.action == ImportItemAction.ALREADY_IMPORTED) Text(stringResource(R.string.transfer_import_already_imported))
                            item.blockers.forEach { blocker ->
                                Text(stringResource(when (blocker) {
                                    ImportBlocker.ADAPTER_VIEW_ONLY -> R.string.transfer_import_block_adapter
                                    ImportBlocker.OWNERSHIP_UNVERIFIED -> R.string.transfer_import_block_ownership
                                    ImportBlocker.IDENTITY_UNVERIFIED -> R.string.transfer_import_block_identity
                                    ImportBlocker.RECORD_NEEDS_REVIEW -> R.string.transfer_import_block_record
                                    ImportBlocker.AMBIGUOUS_IDENTITY -> R.string.transfer_import_block_ambiguous
                                    ImportBlocker.NATIVE_DATA_MISSING -> R.string.transfer_import_block_native
                                }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val saved = result
            if (!completed && error == null && request is ImportPreviewRequest.Fresh && saved != null && acquisition.canCommit(saved.preview)) {
                TextButton(enabled = !committing, onClick = {
                    committing = true
                    scope.launch {
                        try { acquisition.commit(saved.draft.draftId); completed = true }
                        catch (_: ImportIdentityReviewRequiredException) { error = R.string.transfer_import_refresh_required }
                        catch (_: ImportSourceChangedException) { error = R.string.transfer_import_source_changed }
                        catch (_: ImportPreviewInvalidException) { error = R.string.transfer_import_source_changed }
                        catch (_: TimeoutCancellationException) { error = R.string.transfer_error_timeout }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { error = R.string.transfer_import_commit_failed }
                        finally { committing = false }
                    }
                }) { Text(stringResource(R.string.transfer_import_commit)) }
            } else TextButton(enabled = !committing, onClick = onDismiss) { Text(stringResource(R.string.transfer_close)) }
        },
        dismissButton = {
            if (!committing && !completed && error == R.string.transfer_import_refresh_required && request is ImportPreviewRequest.Fresh) {
                TextButton(onClick = {
                    committing = true
                    scope.launch {
                        try { refreshReview = acquisition.prepareRefresh(requireNotNull(result).draft.draftId) }
                        catch (_: ImportSourceChangedException) { error = R.string.transfer_import_source_changed }
                        catch (_: ImportPreviewInvalidException) { error = R.string.transfer_refresh_review_stale }
                        catch (_: ImportIdentityReviewRequiredException) { error = R.string.transfer_refresh_selection_required }
                        catch (_: TimeoutCancellationException) { error = R.string.transfer_error_timeout }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { error = R.string.transfer_import_preview_failed }
                        finally { committing = false }
                    }
                }) { Text(stringResource(R.string.transfer_refresh_review_title)) }
            } else if (!committing && !completed && error != null && error != R.string.transfer_import_source_changed &&
                error != R.string.transfer_import_refresh_required && request is ImportPreviewRequest.Fresh) {
                TextButton(onClick = { result = null; retry++ }) { Text(stringResource(R.string.transfer_refresh)) }
            } else if (!committing && !completed && result != null && error == null && request is ImportPreviewRequest.Fresh && acquisition.canCommit(requireNotNull(result).preview)) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.transfer_close)) }
            }
        },
    )
}
