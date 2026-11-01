package com.tamerslab.transfer.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.tamerslab.R
import com.tamerslab.gamesave.IdentityEvidenceComparison
import com.tamerslab.gamesave.IdentityEvidenceReport
import com.tamerslab.gamesave.ObservedDigimon
import com.tamerslab.gamesave.ObservedField
import com.tamerslab.gamesave.RecordIssue
import com.tamerslab.gamesave.RecordMembership
import com.tamerslab.gamesave.SaveContainer
import com.tamerslab.transfer.data.gameSave.LinkedSaveView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@StringRes
internal fun saveErrorResource(code: String): Int = when (code) {
    "PERMISSION" -> R.string.transfer_error_permission
    "TIMEOUT" -> R.string.transfer_error_timeout
    "INVALID_CONTAINER" -> R.string.transfer_error_container
    "UNRECOGNIZED_LAYOUT" -> R.string.transfer_error_layout
    "UNSUPPORTED_FORMAT", "INPUT_TOO_LARGE", "AMBIGUOUS_FORMAT" -> R.string.transfer_error_format
    "REFRESH_NEEDED" -> R.string.transfer_error_refresh_needed
    else -> R.string.transfer_error_unavailable
}

@StringRes
internal fun containerLabel(container: SaveContainer): Int = when (container) {
    SaveContainer.PARTY -> R.string.transfer_party
    SaveContainer.BANK -> R.string.transfer_bank
    SaveContainer.FARM -> R.string.transfer_farm
    SaveContainer.UNASSIGNED -> R.string.transfer_unassigned
}

@Composable
internal fun ObservedRecordRow(record: ObservedDigimon, modifier: Modifier = Modifier,
    selected: Boolean = false, onSelected: ((Boolean) -> Unit)? = null) {
    val state = stringResource(if (record.membership == RecordMembership.NEEDS_REVIEW)
        R.string.transfer_record_needs_review else R.string.transfer_candidate_record)
    val selectionModifier = if (onSelected == null) Modifier else Modifier.toggleable(selected, role = Role.Checkbox, onValueChange = onSelected)
    Column(modifier.then(selectionModifier).padding(vertical = 4.dp).semantics { stateDescription = state },
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            if (onSelected != null) Checkbox(selected, onCheckedChange = null)
            Text(record.storedName.ifEmpty { stringResource(R.string.transfer_unnamed_record) }, style = MaterialTheme.typography.titleSmall)
        }
        val personalityId = record.personalityId
        Text(if (personalityId != null) stringResource(R.string.transfer_record_details, record.level,
            record.nativeSpeciesId, personalityId, record.position) else stringResource(
            R.string.transfer_record_details_no_personality, record.level, record.nativeSpeciesId, record.position),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        record.nativeParameters?.let { native ->
            Text(stringResource(R.string.transfer_world_ds_stats, native.hp, native.maxHp, native.mp, native.maxMp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.transfer_world_ds_combat, native.attack, native.defense, native.spirit, native.speed),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.transfer_world_ds_aptitude, native.aptitude), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.transfer_world_ds_exp, native.expCounters.joinToString(" / ")),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.transfer_world_ds_copy, native.saveBlock), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (record.membership == RecordMembership.NEEDS_REVIEW) {
            Text(state, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        record.issues.forEach { issue ->
            Text(stringResource(when (issue) {
                RecordIssue.INACTIVE_BOUNDARY -> R.string.transfer_issue_boundary
                RecordIssue.INACTIVE_TAIL -> R.string.transfer_issue_tail
                RecordIssue.FARM_OWNERSHIP_UNVERIFIED -> R.string.transfer_issue_farm
                RecordIssue.MISSING_IDENTITY -> R.string.transfer_issue_missing
                RecordIssue.DUPLICATE_IDENTITY -> R.string.transfer_issue_duplicate
                RecordIssue.GUEST_RECORD -> R.string.transfer_issue_guest
                RecordIssue.LENT_RECORD -> R.string.transfer_issue_lent
                RecordIssue.EGG_RECORD -> R.string.transfer_issue_egg
                RecordIssue.FOREIGN_RECORD -> R.string.transfer_issue_foreign
                RecordIssue.CONFLICTING_LOCATION -> R.string.transfer_issue_location
                RecordIssue.UNKNOWN_NAME_CODES -> R.string.transfer_issue_name_codes
                RecordIssue.UNASSIGNED_LOCATION -> R.string.transfer_issue_unassigned
                RecordIssue.AMBIGUOUS_SAVE_BLOCK -> R.string.transfer_issue_save_copy
                RecordIssue.IMPLAUSIBLE_VALUES -> R.string.transfer_issue_values
            }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

internal fun compatibleObservations(a: LinkedSaveView, b: LinkedSaveView): Boolean {
    val before = a.observation ?: return false
    val after = b.observation ?: return false
    return before.gameId == after.gameId && before.formatProfile == after.formatProfile && before.codecVersion == after.codecVersion
}

@Composable
internal fun SourceComparisonDialog(target: LinkedSaveView, options: List<LinkedSaveView>, onDismiss: () -> Unit) {
    var selectedId by rememberSaveable(target.source.sourceId) { mutableStateOf<String?>(null) }
    val selected = options.firstOrNull { it.source.sourceId == selectedId }
    var report by remember(target.observation, selected?.observation) { mutableStateOf<IdentityEvidenceReport?>(null) }
    LaunchedEffect(target.observation, selected?.observation) {
        val before = target.observation
        val after = selected?.observation
        if (before != null && after != null) {
            report = withContext(Dispatchers.Default) { IdentityEvidenceComparison.compare(before, after) }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.transfer_compare)) },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Text(target.source.label, style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.transfer_compare_hint), style = MaterialTheme.typography.bodySmall)
                }
                items(options, key = { it.source.sourceId }) { other ->
                    TextButton(onClick = { selectedId = other.source.sourceId }) { Text(other.source.label) }
                }
                if (options.isEmpty()) item { Text(stringResource(R.string.transfer_compare_empty)) }
                report?.let { result ->
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.transfer_compare_pair, target.source.label, selected?.source?.label.orEmpty()),
                                style = MaterialTheme.typography.titleSmall)
                            if (result.sameRevision) Text(stringResource(R.string.transfer_compare_identical))
                            Text(stringResource(R.string.transfer_compare_candidates, result.candidates.size, result.ambiguousCandidates))
                            Text(stringResource(R.string.transfer_compare_unmatched, result.onlyBefore, result.onlyAfter))
                            Text(stringResource(R.string.transfer_compare_review, result.reviewRecordsBefore, result.reviewRecordsAfter))
                            Text(stringResource(R.string.transfer_compare_missing, result.missingCandidatesBefore, result.missingCandidatesAfter))
                            ObservedField.entries.forEach { field ->
                                val label = stringResource(when (field) {
                                    ObservedField.NAME -> R.string.transfer_field_name
                                    ObservedField.SPECIES -> R.string.transfer_field_species
                                    ObservedField.PERSONALITY -> R.string.transfer_field_personality
                                    ObservedField.LOCATION -> R.string.transfer_field_location
                                    ObservedField.PROGRESSION -> R.string.transfer_field_progression
                                })
                                Text(stringResource(R.string.transfer_compare_changes, label, result.candidates.count { field in it.changedFields }))
                            }
                            Text(stringResource(R.string.transfer_compare_unverified), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.transfer_close)) } },
    )
}
