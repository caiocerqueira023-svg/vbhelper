package com.tamerslab.storage.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tamerslab.R
import com.tamerslab.transfer.data.gameSave.IndividualContinuityRepository
import java.text.DateFormat
import java.util.Date

@Composable
internal fun IndividualContinuityDialog(individualId: String, repository: IndividualContinuityRepository, onDismiss: () -> Unit) {
    val events by remember(individualId) { repository.history(individualId) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val deltas by remember(individualId) { repository.deltas(individualId) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val dates = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
    val projection by produceState<com.tamerslab.domain.transfer.NativeAppProjection?>(null, individualId) {
        value = repository.projection(individualId)
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.continuity_title)) }, text = {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text(stringResource(R.string.continuity_scope), style = MaterialTheme.typography.bodySmall) }
            projection?.let { baseline -> item {
                Text(stringResource(R.string.continuity_baseline, baseline.projectionVersion, baseline.stage,
                    baseline.trainingHp, baseline.trainingAp, baseline.trainingBp, baseline.vitalPoints), style = MaterialTheme.typography.bodySmall)
                if (baseline.baselineKind == "ADOPTED_EXISTING_STATE") Text(stringResource(R.string.continuity_adopted), style = MaterialTheme.typography.bodySmall)
            } }
            items(events, key = { "event:${it.eventId}" }) { event ->
                Column {
                    Text(stringResource(when (event.kind) {
                        "NATIVE_IMPORTED" -> R.string.continuity_native_imported
                        "NATIVE_REFRESHED" -> R.string.continuity_native_refreshed
                        "APP_MATERIALIZED" -> R.string.continuity_materialized
                        "APP_FORM_RECORDED" -> R.string.continuity_app_form
                        else -> R.string.continuity_device_form
                    }))
                    Text(dates.format(Date(event.occurredAt)), style = MaterialTheme.typography.bodySmall)
                }
            }
            if (deltas.isNotEmpty()) item { Text(stringResource(R.string.continuity_app_changes), style = MaterialTheme.typography.titleSmall) }
            items(deltas, key = { "delta:${it.id}" }) { delta ->
                Text(stringResource(R.string.continuity_delta, delta.hp, delta.ap, delta.bp, delta.vitals, delta.wins, delta.losses),
                    style = MaterialTheme.typography.bodySmall)
                if (delta.trophies != 0) Text(stringResource(R.string.continuity_trophies, delta.trophies), style = MaterialTheme.typography.bodySmall)
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.transfer_close)) } })
}

