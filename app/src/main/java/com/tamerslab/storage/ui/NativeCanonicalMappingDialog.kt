package com.tamerslab.storage.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tamerslab.R
import com.tamerslab.components.rememberPartnerParticipation
import com.tamerslab.domain.transfer.NativeCanonicalMapping
import com.tamerslab.transfer.data.gameSave.*
import com.tamerslab.species.CanonicalSpeciesDto
import com.tamerslab.species.PartnerUnavailableException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun NativeCanonicalMappingDialog(archive: NativeArchiveView, repository: NativeMaterializationRepository,
    identified: CanonicalSpeciesDto?, onDismiss: () -> Unit) {
    var candidates by remember { mutableStateOf<List<CanonicalSpeciesDto>?>(null) }
    var mapping by remember { mutableStateOf<NativeCanonicalMapping?>(null) }
    var selected by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }
    var useSourceName by remember { mutableStateOf(false) }
    var readiness by remember { mutableStateOf<NativeMaterializationReadiness?>(null) }
    var loadAttempt by remember { mutableStateOf(0) }
    val participation = rememberPartnerParticipation()
    val scope = rememberCoroutineScope()
    LaunchedEffect(archive.snapshot.id, loadAttempt) {
        error = null
        try {
            candidates = repository.candidates()
            mapping = repository.mapping(archive.snapshot.id)
            mapping?.let { selected = it.canonicalSpeciesId }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = R.string.native_mapping_failed }
    }
    LaunchedEffect(identified, mapping) {
        if (selected == null) selected = identified?.id
    }
    LaunchedEffect(mapping, participation) {
        readiness = null
        val reviewed = mapping ?: return@LaunchedEffect
        try { readiness = repository.readiness(reviewed) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = R.string.native_mapping_failed }
    }
    val filtered = remember(candidates, query) {
        val term = query.trim()
        candidates.orEmpty().filter { species -> term.isEmpty() || (species.aliases + species.name).any { it.contains(term, true) } }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.native_mapping_review)) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(archive.nickname ?: archive.record?.storedName.orEmpty(), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.native_mapping_explanation), style = MaterialTheme.typography.bodySmall)
                archive.record?.let { Text(stringResource(R.string.native_mapping_native_id, it.nativeSpeciesId)) }
                if (mapping == null && identified != null) Text(stringResource(R.string.native_mapping_identified_from_game,
                    identified.name), style = MaterialTheme.typography.bodySmall)
                error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
                if (candidates == null && error == null) CircularProgressIndicator()
                if (candidates == null && error != null) TextButton(onClick = { loadAttempt++ }) { Text(stringResource(R.string.transfer_refresh)) }
                if (readiness != NativeMaterializationReadiness.ALREADY_MATERIALIZED) {
                    OutlinedTextField(query, { query = it }, label = { Text(stringResource(R.string.ui_search_species)) },
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 220.dp)) {
                        items(filtered, key = { it.id }) { species ->
                            TextButton(enabled = !busy, onClick = { selected = species.id }, modifier = Modifier.fillMaxWidth()) {
                                RadioButton(selected == species.id, onClick = null)
                                Text(species.name, Modifier.weight(1f))
                            }
                        }
                    }
                }
                mapping?.let { reviewed ->
                    Text(stringResource(R.string.native_mapping_confirmed,
                        candidates?.firstOrNull { it.id == reviewed.canonicalSpeciesId }?.name ?: stringResource(R.string.native_mapping_reference_unavailable)))
                    readiness?.let { state ->
                        Text(stringResource(when (state) {
                            NativeMaterializationReadiness.READY -> R.string.native_mapping_ready
                            NativeMaterializationReadiness.PROFILE_UNVERIFIED -> R.string.native_mapping_profile_unverified
                            NativeMaterializationReadiness.ALREADY_MATERIALIZED -> R.string.native_mapping_already_materialized
                            NativeMaterializationReadiness.ORIGIN_STATS_UNMAPPED -> R.string.native_mapping_origin_unmapped
                        }), style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (archive.nickname == null && !archive.record?.storedName.isNullOrEmpty() && readiness == NativeMaterializationReadiness.READY) {
                    Row {
                        Checkbox(useSourceName, { useSourceName = it }, enabled = !busy)
                        Text(stringResource(R.string.native_mapping_use_source_name), Modifier.weight(1f))
                    }
                }
                if (busy) CircularProgressIndicator()
            }
        },
        confirmButton = {
            if (mapping != null && selected == mapping?.canonicalSpeciesId) {
                TextButton(enabled = !busy && readiness == NativeMaterializationReadiness.READY, onClick = {
                    busy = true; error = null
                    scope.launch {
                        try { repository.materialize(requireNotNull(mapping), useSourceName); onDismiss() }
                        catch (_: NativeMaterializationStaleException) { error = R.string.native_mapping_stale }
                        catch (_: PartnerUnavailableException) { error = R.string.native_mapping_mode_unavailable }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { error = R.string.native_mapping_failed }
                        finally { busy = false }
                    }
                }) { Text(stringResource(R.string.native_mapping_materialize)) }
            } else TextButton(enabled = selected != null && !busy, onClick = {
                busy = true; error = null
                scope.launch {
                    try { mapping = repository.saveMapping(archive.snapshot.id, requireNotNull(selected)) }
                    catch (_: NativeMaterializationStaleException) { error = R.string.native_mapping_stale }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { error = R.string.native_mapping_failed }
                    finally { busy = false }
                }
            }) { Text(stringResource(R.string.native_mapping_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.transfer_close)) } },
    )
}
