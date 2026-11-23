package com.tamerslab.storage.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.tamerslab.R
import com.tamerslab.components.VitalButton
import com.tamerslab.transfer.data.gameSave.NativeArchiveView
import com.tamerslab.transfer.data.gameSave.NativeMaterializationRepository
import com.tamerslab.transfer.data.gameSave.NativeSpeciesPresentationRepository
import com.tamerslab.transfer.data.gameSave.NdsStorySpeciesTables
import com.tamerslab.species.CanonicalSpeciesDto
import androidx.compose.ui.platform.LocalContext
import com.tamerslab.di.TamersLabApplication
import kotlinx.coroutines.CancellationException

/** An unmapped source-native representation remains visible without acquiring gameplay eligibility. */
@Composable
internal fun NativeGameArchivePanel(archives: List<NativeArchiveView>, materialization: NativeMaterializationRepository) {
    var open by remember { mutableStateOf(false) }
    var mappingTarget by remember { mutableStateOf<Long?>(null) }
    var historyTarget by remember { mutableStateOf<String?>(null) }
    var destinationTarget by remember { mutableStateOf<Long?>(null) }
    var progressTarget by remember { mutableStateOf<String?>(null) }
    val application = LocalContext.current.applicationContext as TamersLabApplication
    val presentations = remember(application) { NativeSpeciesPresentationRepository(application.container.db,
        application.container.dotSpriteLibrary, NdsStorySpeciesTables.load(application)) }
    val language = LocalConfiguration.current.locales[0].toLanguageTag()
    if (archives.isEmpty()) return
    VitalButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Text(stringResource(R.string.storage_native_archives, archives.size))
    }
    val target = archives.firstOrNull { it.snapshot.id == mappingTarget }
    val destination = archives.firstOrNull { it.snapshot.id == destinationTarget }
    if (progressTarget != null) {
        com.tamerslab.transfer.ui.NativeProgressReviewDialog(requireNotNull(progressTarget),
            remember(application) { com.tamerslab.transfer.data.gameSave.NativeProgressReviewRepository(application.container.db) },
            onDismiss = { progressTarget = null })
    } else if (destination != null) {
        DestinationPreviewDialog(destination, archives, application.container.linkedGameSaves,
            application.container.destinationPreviews, onDismiss = { destinationTarget = null })
    } else if (historyTarget != null) {
        IndividualContinuityDialog(requireNotNull(historyTarget), application.container.individualContinuity, onDismiss = { historyTarget = null })
    } else if (target != null) {
        var identified by remember(target.snapshot.id) { mutableStateOf<CanonicalSpeciesDto?>(null) }
        LaunchedEffect(target.snapshot.id) {
            try { identified = presentations.archive(target.snapshot.id, target.record) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { identified = null }
        }
        NativeCanonicalMappingDialog(target, materialization, identified, onDismiss = { mappingTarget = null })
    } else if (open) AlertDialog(
        onDismissRequest = { open = false },
        title = { Text(stringResource(R.string.storage_native_archives, archives.size)) },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(archives, key = { it.snapshot.individualId }) { archive ->
                    val inactive = stringResource(R.string.storage_native_archive_accessibility)
                    Column(Modifier.fillMaxWidth().semantics { stateDescription = inactive },
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Memory, contentDescription = null, tint = Color.Gray)
                            Text(archive.nickname ?: archive.record?.storedName?.takeIf { it.isNotEmpty() }
                                ?: stringResource(R.string.transfer_unnamed_record), style = MaterialTheme.typography.titleSmall)
                        }
                        Text(archive.snapshot.sourceLabel, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (archive.identityConfidence != "VERIFIED") Text(stringResource(R.string.transfer_provisional_short),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        archive.record?.let { record ->
                            Text(stringResource(R.string.storage_native_source_name, record.storedName), style = MaterialTheme.typography.bodySmall)
                            val personalityId = record.personalityId
                            Text(if (personalityId != null) stringResource(R.string.transfer_record_details, record.level,
                                record.nativeSpeciesId, personalityId, record.position) else stringResource(
                                R.string.transfer_record_details_no_personality, record.level, record.nativeSpeciesId,
                                record.position), style = MaterialTheme.typography.bodySmall)
                        }
                        archive.personality?.let { personality ->
                            Text(stringResource(R.string.storage_native_app_personality, personality.displayName(language)), style = MaterialTheme.typography.bodySmall)
                        }
                        if (archive.snapshot.nativePersonalityId == null) Text(stringResource(R.string.world_ds_personality_retained),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.storage_native_archive_details), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { mappingTarget = archive.snapshot.id }) {
                            Text(stringResource(R.string.native_mapping_review))
                        }
                        TextButton(onClick = { historyTarget = archive.snapshot.individualId }) { Text(stringResource(R.string.continuity_title)) }
                        TextButton(onClick = { progressTarget = archive.snapshot.individualId }) { Text(stringResource(R.string.native_progress_title)) }
                        TextButton(onClick = { destinationTarget = archive.snapshot.id }) {
                            Text(stringResource(R.string.destination_title))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.transfer_close)) } },
    )
}
