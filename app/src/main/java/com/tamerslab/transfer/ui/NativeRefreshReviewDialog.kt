package com.tamerslab.transfer.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.tamerslab.R
import com.tamerslab.domain.personality.DigimonPersonalityType
import com.tamerslab.gamesave.RefreshChoice
import com.tamerslab.transfer.data.gameSave.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch

@Composable
internal fun NativeRefreshReviewDialog(review: NativeRefreshReview, repository: GameImportAcquisitionRepository,
    onDismiss: () -> Unit, onCompleted: () -> Unit) {
    var choices by remember(review.fingerprint) { mutableStateOf(review.items.associate { it.incoming.recordKey to it.fields.recommendedChoices() }) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }
    var identityConfirmed by remember(review.fingerprint) { mutableStateOf(!review.requiresIdentityConfirmation) }
    val scope = rememberCoroutineScope()
    val language = LocalConfiguration.current.locales[0].toLanguageTag()
    fun personality(value: String) = DigimonPersonalityType.entries.singleOrNull { it.name == value }?.displayName(language) ?: value
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.transfer_refresh_review_title)) },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item { Text(stringResource(R.string.transfer_refresh_review_explanation)) }
                if (review.requiresIdentityConfirmation) item {
                    Column {
                        Text(stringResource(R.string.transfer_provisional_identity), style = MaterialTheme.typography.bodySmall)
                        Row {
                            Checkbox(identityConfirmed, { identityConfirmed = it }, enabled = !busy)
                            Text(stringResource(R.string.transfer_confirm_same_individuals), Modifier.weight(1f))
                        }
                    }
                }
                error?.let { message -> item { Text(stringResource(message), color = MaterialTheme.colorScheme.error) } }
                items(review.items, key = { it.incoming.recordKey }) { item ->
                    val selected = choices.getValue(item.incoming.recordKey)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.transfer_refresh_baseline), style = MaterialTheme.typography.titleSmall)
                        ObservedRecordRow(item.previousRecord)
                        Text(stringResource(R.string.transfer_refresh_incoming), style = MaterialTheme.typography.titleSmall)
                        ObservedRecordRow(item.incoming)
                        Text(stringResource(R.string.transfer_field_name), style = MaterialTheme.typography.titleSmall)
                        if (item.fields.nameConflict) Text(stringResource(R.string.transfer_refresh_conflict), color = MaterialTheme.colorScheme.error)
                        RefreshOption(stringResource(R.string.transfer_refresh_keep_app,
                            item.appValues.nickname ?: stringResource(R.string.transfer_refresh_no_nickname)),
                            selected.name == RefreshChoice.KEEP_APP, !busy) {
                            choices = choices + (item.incoming.recordKey to selected.copy(name = RefreshChoice.KEEP_APP))
                        }
                        RefreshOption(stringResource(R.string.transfer_refresh_use_source,
                            item.incoming.storedName.ifEmpty { stringResource(R.string.transfer_unnamed_record) }),
                            selected.name == RefreshChoice.USE_SOURCE, !busy) {
                            choices = choices + (item.incoming.recordKey to selected.copy(name = RefreshChoice.USE_SOURCE))
                        }
                        Text(stringResource(R.string.transfer_field_personality), style = MaterialTheme.typography.titleSmall)
                        if (item.fields.personalityConflict) Text(stringResource(R.string.transfer_refresh_conflict), color = MaterialTheme.colorScheme.error)
                        RefreshOption(stringResource(R.string.transfer_refresh_keep_app, personality(item.appValues.personality)),
                            selected.personality == RefreshChoice.KEEP_APP, !busy) {
                            choices = choices + (item.incoming.recordKey to selected.copy(personality = RefreshChoice.KEEP_APP))
                        }
                        if (item.incoming.personalityId == null) Text(stringResource(R.string.world_ds_personality_retained),
                            style = MaterialTheme.typography.bodySmall)
                        else RefreshOption(stringResource(R.string.transfer_refresh_use_source, personality(item.incomingPersonality)),
                            selected.personality == RefreshChoice.USE_SOURCE, !busy) {
                            choices = choices + (item.incoming.recordKey to selected.copy(personality = RefreshChoice.USE_SOURCE))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && error == null && identityConfirmed && choices.values.all { it.name != null && it.personality != null }, onClick = {
                busy = true
                scope.launch {
                    try { repository.commitRefresh(review, choices, identityConfirmed); onCompleted() }
                    catch (_: ImportSourceChangedException) { error = R.string.transfer_import_source_changed }
                    catch (_: ImportPreviewInvalidException) { error = R.string.transfer_refresh_review_stale }
                    catch (_: TimeoutCancellationException) { error = R.string.transfer_error_timeout }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { error = R.string.transfer_import_commit_failed }
                    finally { busy = false }
                }
            }) { Text(stringResource(R.string.transfer_refresh_apply)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.transfer_close)) } },
    )
}

@Composable
private fun RefreshOption(label: String, selected: Boolean, enabled: Boolean, onSelect: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)) {
        RadioButton(selected, onClick = null, enabled = enabled)
        Text(label, Modifier.weight(1f).padding(vertical = 8.dp))
    }
}
