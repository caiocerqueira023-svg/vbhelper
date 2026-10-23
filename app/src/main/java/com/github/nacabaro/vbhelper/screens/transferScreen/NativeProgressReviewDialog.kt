package com.github.nacabaro.vbhelper.screens.transferScreen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.gamesave.*
import com.github.nacabaro.vbhelper.source.gameSave.*
import kotlinx.coroutines.CancellationException

@Composable
internal fun NativeProgressReviewDialog(individualId: String, repository: NativeProgressReviewRepository, onDismiss: () -> Unit) {
    var history by remember(individualId) { mutableStateOf<NativeProgressHistory?>(null) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(individualId, attempt) {
        failed = false
        try { history = repository.load(individualId) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.native_progress_title)) }, text = {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text(stringResource(R.string.native_progress_explanation)) }
            if (failed) item { Text(stringResource(R.string.native_progress_unavailable), color = MaterialTheme.colorScheme.error) }
            if (history == null && !failed) item { CircularProgressIndicator() }
            history?.let { data ->
                if (data.pendingCount == 0) item { Text(stringResource(R.string.native_progress_empty)) }
                else item { Text(stringResource(R.string.native_progress_recent, data.entries.size, data.pendingCount)) }
                items(data.entries, key = { it.snapshotId }) { entry ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(entry.sourceLabel, style = MaterialTheme.typography.titleSmall)
                        val report = entry.report
                        if (report == null) Text(stringResource(R.string.native_progress_unavailable))
                        else {
                            report.kinds.forEach { kind -> Text(stringResource(kind.label()), style = MaterialTheme.typography.bodySmall) }
                            report.changes.forEach { change ->
                                val label = if (change.measure == NativeMeasure.EXP_CHANNEL)
                                    stringResource(R.string.native_progress_exp_channel, requireNotNull(change.channel))
                                else stringResource(change.measure.label())
                                Text(stringResource(R.string.native_progress_pair, label,
                                    change.before ?: stringResource(R.string.native_progress_absent),
                                    change.after ?: stringResource(R.string.native_progress_absent)), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.transfer_close)) } },
        dismissButton = { TextButton(onClick = { attempt++ }) { Text(stringResource(R.string.transfer_refresh)) } })
}

private fun NativeChangeKind.label(): Int = when (this) {
    NativeChangeKind.NO_CHANGE -> R.string.native_progress_no_change
    NativeChangeKind.PERSONAL_EDIT -> R.string.native_progress_personal
    NativeChangeKind.LOCATION -> R.string.native_progress_location
    NativeChangeKind.FORM -> R.string.native_progress_form
    NativeChangeKind.IDENTITY -> R.string.native_progress_identity
    NativeChangeKind.PERMANENT_PARAMETERS -> R.string.native_progress_parameters
    NativeChangeKind.TEMPORARY_STATE -> R.string.native_progress_temporary
    NativeChangeKind.UNKNOWN_NATIVE_DATA -> R.string.native_progress_unknown
    NativeChangeKind.NATIVE_DATA_UNAVAILABLE, NativeChangeKind.UNSUPPORTED_PROFILE -> R.string.native_progress_unavailable
}
private fun NativeMeasure.label(): Int = when (this) {
    NativeMeasure.LEVEL -> R.string.native_measure_level
    NativeMeasure.TOTAL_EXP -> R.string.native_measure_exp
    NativeMeasure.TALENT -> R.string.native_measure_talent
    NativeMeasure.BOND -> R.string.native_measure_bond
    NativeMeasure.CURRENT_HP -> R.string.native_measure_hp
    NativeMeasure.MAX_HP -> R.string.native_measure_max_hp
    NativeMeasure.CURRENT_MP -> R.string.native_measure_mp
    NativeMeasure.MAX_MP -> R.string.native_measure_max_mp
    NativeMeasure.ATTACK -> R.string.native_measure_attack
    NativeMeasure.DEFENSE -> R.string.native_measure_defense
    NativeMeasure.SPIRIT -> R.string.native_measure_spirit
    NativeMeasure.SPEED -> R.string.native_measure_speed
    NativeMeasure.APTITUDE -> R.string.native_measure_aptitude
    NativeMeasure.EXP_CHANNEL -> R.string.native_measure_exp
    NativeMeasure.FORM_HISTORY -> R.string.native_measure_history
}
