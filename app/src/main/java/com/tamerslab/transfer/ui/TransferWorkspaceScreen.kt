package com.tamerslab.transfer.ui

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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.tamerslab.R
import com.tamerslab.components.showAppFeedback
import com.tamerslab.di.TamersLabApplication
import com.tamerslab.gamesave.*
import com.tamerslab.navigation.TransferGame
import com.tamerslab.navigation.TransferGameCatalog
import com.tamerslab.transfer.data.gameSave.*
import com.tamerslab.species.CanonicalSpeciesDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch

/** Open an origin, then tap an individual for information and its direct transfer action. */
@Composable
internal fun TransferWorkspaceScreen(game: TransferGame?, startInStorage: Boolean, initialCharacterId: Long?,
    onManageSaves: () -> Unit, onOpenGame: (TransferGame) -> Unit, onSendToGame: (String) -> Unit = {}) {
    val context = LocalContext.current
    val app = context.applicationContext as TamersLabApplication
    val repository = app.container.linkedGameSaves
    val documents = remember(app) { AndroidSaveDocuments(app) }
    val launcherIcons = remember(app) { NdsLauncherIconRepository(app) }
    val presentations = remember(app) { NativeSpeciesPresentationRepository(app.container.db, app.container.dotSpriteLibrary,
        NdsStorySpeciesTables.load(context)) }
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
    val refreshing by repository.refreshing.collectAsStateWithLifecycle()
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
    var containerKey by rememberSaveable(sourceId) { mutableStateOf("ALL") }
    var detailKey by remember(sourceId, observation?.contentRevision) { mutableStateOf<String?>(null) }
    var resolved by remember(sourceId, observation?.contentRevision) { mutableStateOf(emptyMap<String, CanonicalSpeciesDto>()) }
    var movingRecord by remember { mutableStateOf<ObservedDigimon?>(null) }
    var movingSpecies by remember { mutableStateOf<CanonicalSpeciesDto?>(null) }
    val container = containerKey.takeIf { it != "ALL" }?.let(SaveContainer::valueOf)
    val allRecords = observation?.let { shownRecords(it) }.orEmpty()
    val containerCounts = allRecords.groupingBy { it.container }.eachCount()
    val records = allRecords.filter { container == null || it.container == container }
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
            val multipleCopies = observation?.records?.mapNotNull { it.nativeParameters?.saveBlock }
                ?.distinct()?.size ?: 0 > 1
            TransferSaveIdentityBar(
                saveLabel = (source?.source?.label ?: stringResource(R.string.transfer_workspace_open))
                    .removeSuffix(".sav"),
                statusLine = source?.let { view ->
                    when {
                        view.source.lastError != null -> stringResource(R.string.transfer_cached)
                        view.observation == null -> stringResource(R.string.transfer_never_read)
                        else -> stringResource(R.string.transfer_save_box_summary,
                            containerCounts[SaveContainer.PARTY] ?: 0,
                            containerCounts[SaveContainer.FARM] ?: 0,
                            containerCounts[SaveContainer.BANK] ?: 0)
                    }
                } ?: stringResource(R.string.transfer_workspace_open_hint),
                leading = { game?.let { TransferOriginIcon(it, Modifier.size(40.dp)) } },
                refreshing = opening || sourceId in refreshing,
                onRefresh = { sourceId?.let { scope.launch { repository.refresh(it) } } },
                onSwitchSaves = if (gameSources.size > 1) ({ choosingSave = true }) else null,
                onSaveCopy = if (multipleCopies) ({ reviewingCopy = true }) else null,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        when {
            startInStorage -> when {
                vaultFailed -> OriginMessage(R.string.transfer_list_failed, R.string.transfer_refresh) { attempt++ }
                archives == null -> OriginLoading()
                archives.orEmpty().isEmpty() -> OriginMessage(R.string.transfer_workspace_vault_empty)
                else -> LazyVerticalGrid(GridCells.Fixed(3), Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(archives.orEmpty(), key = { it.snapshot.individualId }) { archive ->
                        val species by produceState<CanonicalSpeciesDto?>(null, archive.snapshot.id) {
                            try { value = presentations.archive(archive.snapshot.id, archive.record) }
                            catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) { value = null }
                        }
                        TransferDigimonCard(
                            archive.nickname?.takeIf(String::isNotBlank) ?: archive.record?.storedName.orEmpty(),
                            archive.record?.level, species,
                            archive.record?.container ?: SaveContainer.UNASSIGNED,
                            archive.record?.position ?: 0, artworkVersion,
                            onClick = { archiveId = archive.snapshot.id })
                    }
                    val archiveRecords = archives.orEmpty().size
                    val placeholders = if (archiveRecords < 12) 12 - archiveRecords
                    else (3 - archiveRecords % 3) % 3
                    items(placeholders, key = { "empty-$it" }) { TransferEmptySlot() }
                }
            }
            failed -> OriginMessage(R.string.transfer_list_failed, R.string.transfer_refresh) { attempt++ }
            sources == null -> OriginLoading()
            source == null -> OriginMessage(R.string.transfer_workspace_open_hint, R.string.transfer_workspace_open) { picker.launch(arrayOf("*/*")) }
            observation == null -> OriginMessage(source.source.lastError?.let(::saveErrorResource) ?: R.string.transfer_never_read,
                R.string.transfer_workspace_manage, onManageSaves)
            else -> {
                TransferScopeTabs(containerCounts, allRecords.size, container,
                    onSelect = { containerKey = it?.name ?: "ALL" },
                    modifier = Modifier.padding(horizontal = 16.dp))
                if (records.isEmpty()) OriginMessage(R.string.transfer_box_empty)
                else LazyVerticalGrid(GridCells.Fixed(3), Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(records, key = { it.recordKey }) { record ->
                        TransferDigimonCard(record.storedName, record.level, resolved[record.recordKey],
                            record.container, record.position, artworkVersion,
                            onClick = { detailKey = record.recordKey })
                    }
                    val placeholders = if (records.size < 12) 12 - records.size
                    else (3 - records.size % 3) % 3
                    items(placeholders, key = { "empty-$it" }) { TransferEmptySlot() }
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
        TransferInspectorSheet(record, record.storedName, movingSpecies ?: resolved[record.recordKey], artworkVersion,
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
        archive.record?.let { record -> TransferInspectorSheet(record,
            archive.nickname?.takeIf(String::isNotBlank) ?: record.storedName, species, artworkVersion,
            individualId = archive.snapshot.individualId,
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

