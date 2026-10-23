package com.github.nacabaro.vbhelper.screens.transferScreen

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.components.showAppFeedback
import com.github.nacabaro.vbhelper.di.VBHelper
import com.github.nacabaro.vbhelper.gamesave.*
import com.github.nacabaro.vbhelper.navigation.TransferGame
import com.github.nacabaro.vbhelper.navigation.TransferGameCatalog
import com.github.nacabaro.vbhelper.source.gameSave.*
import com.github.nacabaro.vbhelper.species.CanonicalSpeciesDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch

/** Open an origin, then tap an individual for information and its direct transfer action. */
@Composable
internal fun TransferWorkspaceScreen(game: TransferGame?, startInStorage: Boolean, initialCharacterId: Long?,
    onManageSaves: () -> Unit, onOpenGame: (TransferGame) -> Unit, onSendToGame: (String) -> Unit = {}) {
    val context = LocalContext.current
    val app = context.applicationContext as VBHelper
    val repository = app.container.linkedGameSaves
    val documents = remember(app) { AndroidSaveDocuments(app) }
    val launcherIcons = remember(app) { NdsLauncherIconRepository(app) }
    val presentations = remember(app) { NativeSpeciesPresentationRepository(app.container.db, app.container.dotSpriteLibrary,
        WorldDsSpeciesTable.load(context)) }
    val scope = rememberCoroutineScope()
    var sources by remember { mutableStateOf<List<LinkedSaveView>?>(null) }
    var archives by remember { mutableStateOf<List<NativeArchiveView>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var vaultFailed by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    var selectedSourceId by rememberSaveable { mutableStateOf<String?>(null) }
    var choosingSave by remember { mutableStateOf(false) }
    var opening by remember { mutableStateOf(false) }
    var pendingRom by rememberSaveable { mutableStateOf<TransferGame?>(null) }
    var reviewingCopy by remember { mutableStateOf(false) }
    var archiveId by rememberSaveable { mutableStateOf<Long?>(null) }
    var appliedContext by rememberSaveable(initialCharacterId) { mutableStateOf(false) }
    val refreshing by repository.refreshing.collectAsState()
    val artworkVersion = rememberTransferArtworkVersion()

    LaunchedEffect(repository, attempt) {
        try { repository.sources.collect { sources = it; failed = false } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
    }
    LaunchedEffect(app, attempt) {
        try { app.container.gameImportAcquisition.archives.collect { archives = it; vaultFailed = false } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { vaultFailed = true }
    }
    LaunchedEffect(archives, initialCharacterId) {
        if (!appliedContext && initialCharacterId != null) archives?.firstOrNull { it.characterId == initialCharacterId }?.let {
            archiveId = it.snapshot.id; appliedContext = true
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(repository, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            try { repository.refreshAll() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { context.showAppFeedback(R.string.transfer_action_failed) }
            awaitCancellation()
        }
    }
    val gameSources = sources.orEmpty().filter { game == null ||
        TransferGameCatalog.identify(it.observation?.gameId, it.source.displayName) == game }
    val source = gameSources.firstOrNull { it.source.sourceId == selectedSourceId }
        ?: gameSources.firstOrNull { it.source.lastError == null && it.observation != null }
        ?: gameSources.firstOrNull()
    val observation = source?.observation
    val sourceId = source?.source?.sourceId
    var containerKey by rememberSaveable(sourceId) { mutableStateOf(SaveContainer.PARTY.name) }
    var detailKey by remember(sourceId, observation?.contentRevision) { mutableStateOf<String?>(null) }
    var resolved by remember(sourceId, observation?.contentRevision) { mutableStateOf(emptyMap<String, CanonicalSpeciesDto>()) }
    var movingRecord by remember { mutableStateOf<ObservedDigimon?>(null) }
    var movingSpecies by remember { mutableStateOf<CanonicalSpeciesDto?>(null) }
    val container = SaveContainer.valueOf(containerKey)
    val records = observation?.let { shownRecords(it, container) }.orEmpty()
    val picker = rememberLauncherForActivityResult(OpenGameSaveDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            opening = true
            try {
                documents.persistTransferGrant(uri)
                val document = documents.describe(uri)
                val id = repository.link(document)
                selectedSourceId = id
                val linkedGame = TransferGameCatalog.identify(repository.readFresh(id).gameId, document.name)
                if (linkedGame != null && launcherIcons.supports(linkedGame) && !launcherIcons.hasIcon(linkedGame))
                    pendingRom = linkedGame
                else linkedGame?.let(onOpenGame)
            } catch (_: TimeoutCancellationException) { context.showAppFeedback(R.string.transfer_error_timeout) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: SecurityException) { context.showAppFeedback(R.string.transfer_grant_failed) }
            catch (_: Exception) { context.showAppFeedback(R.string.transfer_action_failed) }
            finally { opening = false }
        }
    }
    LaunchedEffect(sourceId, observation?.contentRevision, archives) {
        resolved = emptyMap()
        val view = source ?: return@LaunchedEffect
        try { resolved = presentations.source(view) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* An unresolved species is explicit; never guess a nickname. */ }
    }
    LaunchedEffect(sourceId) {
        val id = sourceId ?: return@LaunchedEffect
        try { app.container.experimentalGameMoves.recover(id) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { context.showAppFeedback(R.string.transfer_move_recovery) }
        try { app.container.experimentalGameSends.recover(id) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { context.showAppFeedback(R.string.transfer_move_recovery) }
    }

    Column(Modifier.fillMaxSize()) {
        if (!startInStorage) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // A single save opens directly. Only multiple saves need a chooser.
                if (gameSources.size > 1) OutlinedButton(onClick = { choosingSave = true }, modifier = Modifier.weight(1f)) {
                    Text(source?.source?.label.orEmpty(), Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Icon(Icons.Default.ArrowDropDown, null)
                } else Text(source?.source?.label ?: stringResource(R.string.transfer_workspace_open),
                    Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = { sourceId?.let { scope.launch { repository.refresh(it) } } },
                    enabled = sourceId != null && sourceId !in refreshing && !opening) {
                    Icon(Icons.Default.Refresh, stringResource(R.string.transfer_refresh))
                }
            }
            if (opening || sourceId in refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        when {
            startInStorage -> when {
                vaultFailed -> OriginMessage(R.string.transfer_list_failed, R.string.transfer_refresh) { attempt++ }
                archives == null -> OriginLoading()
                archives.orEmpty().isEmpty() -> OriginMessage(R.string.transfer_workspace_vault_empty)
                else -> LazyVerticalGrid(GridCells.Adaptive(104.dp), Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(archives.orEmpty(), key = { it.snapshot.individualId }) { archive ->
                        val species by produceState<CanonicalSpeciesDto?>(null, archive.snapshot.id) {
                            try { value = presentations.archive(archive.snapshot.id, archive.record) }
                            catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) { value = null }
                        }
                        TransferIndividualTile(archive.nickname?.takeIf(String::isNotBlank) ?: archive.record?.storedName.orEmpty(),
                            archive.record?.level, species, artworkVersion, onClick = { archiveId = archive.snapshot.id })
                    }
                }
            }
            failed -> OriginMessage(R.string.transfer_list_failed, R.string.transfer_refresh) { attempt++ }
            sources == null -> OriginLoading()
            source == null -> OriginMessage(R.string.transfer_workspace_open_hint, R.string.transfer_workspace_open) { picker.launch(arrayOf("*/*")) }
            observation == null -> OriginMessage(source.source.lastError?.let(::saveErrorResource) ?: R.string.transfer_never_read,
                R.string.transfer_workspace_manage, onManageSaves)
            else -> {
                if (source.source.lastError != null) Text(stringResource(R.string.transfer_cached), Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                if (observation.records.mapNotNull { it.nativeParameters?.saveBlock }.distinct().size > 1) TextButton(onClick = { reviewingCopy = true }) {
                    Text(stringResource(R.string.world_ds_choose_copy))
                }
                FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SaveContainer.entries.filter { candidate -> candidate == SaveContainer.PARTY ||
                        observation.records.any { it.container == candidate } }.forEach { candidate ->
                        FilterChip(selected = container == candidate, onClick = { containerKey = candidate.name },
                            label = { Text(stringResource(containerLabel(candidate))) })
                    }
                }
                if (records.isEmpty()) OriginMessage(R.string.transfer_workspace_no_records)
                else LazyVerticalGrid(GridCells.Adaptive(104.dp), Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(records, key = { it.recordKey }) { record ->
                        TransferIndividualTile(record.storedName, record.level, resolved[record.recordKey], artworkVersion,
                            onClick = { detailKey = record.recordKey })
                    }
                }
            }
        }
    }

    if (choosingSave) AlertDialog(onDismissRequest = { choosingSave = false },
        title = { Text(stringResource(R.string.transfer_workspace_choose_save)) }, text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                gameSources.forEach { view -> item(key = view.source.sourceId) {
                    TextButton(onClick = { selectedSourceId = view.source.sourceId; choosingSave = false }) { Text(view.source.label) }
                } }
            }
        }, confirmButton = { TextButton(onClick = { choosingSave = false; picker.launch(arrayOf("*/*")) }) { Text(stringResource(R.string.transfer_workspace_open)) } },
        dismissButton = { TextButton(onClick = { choosingSave = false }) { Text(stringResource(R.string.transfer_close)) } })
    if (reviewingCopy && source != null) WorldDsCopyDialog(source, repository, onDismiss = { reviewingCopy = false })
    (movingRecord ?: observation?.records?.singleOrNull { it.recordKey == detailKey })?.let { record ->
        val blocker = source?.let { app.container.experimentalGameMoves.blocker(it, record) }
        val admissionBlocker = if (record.container == SaveContainer.UNASSIGNED && movingRecord == null)
            source?.let { app.container.experimentalGameMoves.admissionBlocker(it, record) } else null
        TransferIndividualInformation(record, record.storedName, movingSpecies ?: resolved[record.recordKey], artworkVersion,
            moveIntoStorage = true, moving = movingRecord != null,
            moveBlocker = if (movingRecord != null) null else blocker?.let(::moveBlockerText),
            onMove = if (source == null || observation == null || blocker != null) null else ({
                val expectedRevision = observation.contentRevision
                val id = source.source.sourceId
                movingRecord = record; movingSpecies = resolved[record.recordKey]
                scope.launch {
                    try {
                        app.container.experimentalGameMoves.move(id, expectedRevision, record.recordKey)
                        detailKey = null
                        context.showAppFeedback(R.string.transfer_move_completed)
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: WorldDsMoveRecoveryRequired) { context.showAppFeedback(R.string.transfer_move_recovery) }
                    catch (_: ImportSourceChangedException) { context.showAppFeedback(R.string.transfer_import_source_changed) }
                    catch (failure: Exception) {
                        Log.e("TransferWorkspace", "move failed source=$id record=${record.recordKey}", failure)
                        context.showAppFeedback(R.string.transfer_action_failed)
                    }
                    finally { movingRecord = null; movingSpecies = null }
                }
            }),
            onGrantAccess = if (blocker == WorldDsMoveBlocker.WRITE_ACCESS) ({ picker.launch(arrayOf("*/*")) }) else null,
            onAdmitToParty = if (record.container == SaveContainer.UNASSIGNED && admissionBlocker == null &&
                source != null && observation != null) ({
                val expectedRevision = observation.contentRevision
                val id = source.source.sourceId
                movingRecord = record; movingSpecies = resolved[record.recordKey]
                scope.launch {
                    try {
                        app.container.experimentalGameMoves.admit(id, expectedRevision, record.recordKey)
                        detailKey = null
                        context.showAppFeedback(R.string.transfer_admit_completed)
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: WorldDsMoveRecoveryRequired) { context.showAppFeedback(R.string.transfer_move_recovery) }
                    catch (_: ImportSourceChangedException) { context.showAppFeedback(R.string.transfer_import_source_changed) }
                    catch (failure: Exception) {
                        Log.e("TransferWorkspace", "admit failed source=$id record=${record.recordKey}", failure)
                        context.showAppFeedback(R.string.transfer_action_failed)
                    }
                    finally { movingRecord = null; movingSpecies = null }
                }
            }) else null, admitBlocker = admissionBlocker?.let(::moveBlockerText),
                onDismiss = { if (movingRecord == null) detailKey = null })
    }
    archives.orEmpty().singleOrNull { it.snapshot.id == archiveId }?.let { archive ->
        val species by produceState<CanonicalSpeciesDto?>(null, archive.snapshot.id) {
            try { value = presentations.archive(archive.snapshot.id, archive.record) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { value = null }
        }
        archive.record?.let { record -> TransferIndividualInformation(record,
            archive.nickname?.takeIf(String::isNotBlank) ?: record.storedName, species, artworkVersion,
            moveIntoStorage = false, moveBlocker = null,
            onMove = if (WorldDsSendPolicy.nativeReturnable(record)) ({ onSendToGame(archive.snapshot.individualId) }) else null,
            onDismiss = { archiveId = null }) }
    }
    NdsLauncherImportPrompt(pendingRom) { linkedGame ->
        pendingRom = null
        onOpenGame(linkedGame)
    }
}

private fun moveBlockerText(blocker: WorldDsMoveBlocker): Int = when (blocker) {
    WorldDsMoveBlocker.FORMAT -> R.string.transfer_native_move_unavailable
    WorldDsMoveBlocker.LAST_PARTY_MEMBER -> R.string.transfer_move_last_party
    WorldDsMoveBlocker.WRITE_ACCESS -> R.string.transfer_move_write_access
    WorldDsMoveBlocker.STALE -> R.string.transfer_import_source_changed
    WorldDsMoveBlocker.COPY_DIVERGENCE -> R.string.transfer_move_copy_divergence
    WorldDsMoveBlocker.ONLY_IN_OLDER_COPY -> R.string.transfer_move_copy_older_only
    WorldDsMoveBlocker.DESTINATION_FULL -> R.string.transfer_send_destination_full
    WorldDsMoveBlocker.REVIEW -> R.string.transfer_record_needs_review
}

@Composable
private fun OriginLoading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
private fun OriginMessage(message: Int, action: Int? = null, onAction: () -> Unit = {}) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(message), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            action?.let { Button(onClick = onAction) { Text(stringResource(it)) } }
        }
    }
}
