package com.tamerslab.transfer.ui

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.tamerslab.R
import com.tamerslab.components.showAppFeedback
import com.tamerslab.di.TamersLabApplication
import com.tamerslab.gamesave.ObservedDigimon
import com.tamerslab.navigation.TransferGame
import com.tamerslab.navigation.TransferGameCatalog
import com.tamerslab.storage.data.StorageRepository
import com.tamerslab.transfer.data.gameSave.AndroidSaveDocuments
import com.tamerslab.transfer.data.gameSave.ImportSourceChangedException
import com.tamerslab.transfer.data.gameSave.LinkedSaveView
import com.tamerslab.transfer.data.gameSave.NdsLauncherIconRepository
import com.tamerslab.transfer.data.gameSave.TransferGameCapabilities
import com.tamerslab.transfer.data.gameSave.OpenGameSaveDocument
import com.tamerslab.transfer.data.gameSave.WorldDsSendBlocker
import com.tamerslab.transfer.data.gameSave.WorldDsSendRecoveryRequired
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One origin list: games, physical devices and local Storage share the same navigation model. */
@Composable
internal fun TransferGameLibraryScreen(onOpenGame: (TransferGame) -> Unit, onStorage: () -> Unit,
    onManageSaves: () -> Unit, onDevices: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as TamersLabApplication
    val repository = app.container.linkedGameSaves
    val documents = remember(app) { AndroidSaveDocuments(app) }
    val launcherIcons = remember(app) { NdsLauncherIconRepository(app) }
    val scope = rememberCoroutineScope()
    var sources by remember { mutableStateOf<List<LinkedSaveView>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    var opening by remember { mutableStateOf(false) }
    var pendingRom by rememberSaveable { mutableStateOf<TransferGame?>(null) }
    var iconRevision by remember { mutableIntStateOf(0) }
    LaunchedEffect(repository, attempt) {
        try { repository.sources.collect { sources = it; failed = false } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
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
    val picker = rememberLauncherForActivityResult(OpenGameSaveDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            opening = true
            try {
                documents.persistTransferGrant(uri)
                val document = documents.describe(uri)
                val id = repository.link(document)
                val game = try { TransferGameCatalog.identify(repository.readFresh(id).gameId, document.name) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { TransferGameCatalog.identify(null, document.name) }
                if (game == null) {
                    context.showAppFeedback(R.string.transfer_error_format)
                    onManageSaves()
                } else if (launcherIcons.supports(game) && !launcherIcons.hasIcon(game)) {
                    pendingRom = game
                } else onOpenGame(game)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: SecurityException) { context.showAppFeedback(R.string.transfer_grant_failed) }
            catch (_: Exception) { context.showAppFeedback(R.string.transfer_action_failed) }
            finally { opening = false }
        }
    }
    NdsLauncherImportPrompt(pendingRom) { game ->
        pendingRom = null
        iconRevision++
        onOpenGame(game)
    }
    val groups = sources.orEmpty().groupBy { TransferGameCatalog.identify(it.observation?.gameId, it.source.displayName) }
    val games = TransferGame.entries.filter { TransferGameCapabilities.readsNdsStory(it) || groups[it].orEmpty().isNotEmpty() }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.transfer_origins_title), style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = { picker.launch(arrayOf("*/*")) }, enabled = !opening) {
                Text(stringResource(R.string.transfer_workspace_open))
            }
        }
        if (opening) LinearProgressIndicator(Modifier.fillMaxWidth())
        when {
            failed -> Column(Modifier.weight(1f).padding(16.dp)) {
                Text(stringResource(R.string.transfer_list_failed))
                TextButton(onClick = { attempt++ }) { Text(stringResource(R.string.transfer_refresh)) }
            }
            sources == null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else -> LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 8.dp)) {
                items(games, key = { it.name }) { game ->
                    val saves = groups[game].orEmpty()
                    OriginRow(stringResource(gameTitle(game)),
                        if (saves.isNotEmpty() && saves.none { it.observation != null }) stringResource(R.string.transfer_library_unreadable)
                        else if (saves.isEmpty()) stringResource(R.string.transfer_workspace_open)
                        else pluralStringResource(R.plurals.transfer_workspace_save_count, saves.size, saves.size),
                        onClick = { onOpenGame(game) }, leading = { TransferOriginIcon(game, Modifier.size(40.dp), iconRevision) })
                }
                item(key = "bracelet") {
                    OriginRow(stringResource(R.string.transfer_watch_section), stringResource(R.string.transfer_origin_devices),
                        onClick = onDevices, leading = { Icon(Icons.Default.Nfc, null, Modifier.size(32.dp)) })
                }
                item(key = "storage") {
                    OriginRow(stringResource(R.string.transfer_workspace_vault), stringResource(R.string.transfer_origin_storage),
                        onClick = onStorage, leading = { Icon(Icons.Default.Inventory2, null, Modifier.size(32.dp)) })
                }
            }
        }
    }
}

/** Destination selection for a stored individual; only exact retained World DS records can return. */
@Composable
internal fun TransferSendScreen(characterId: Long?, archiveIndividualId: String? = null, onDevices: () -> Unit) {
    val app = LocalContext.current.applicationContext as TamersLabApplication
    val context = LocalContext.current
    val repository = app.container.linkedGameSaves
    val sends = app.container.experimentalGameSends
    val scope = rememberCoroutineScope()
    var sources by remember { mutableStateOf<List<LinkedSaveView>?>(null) }
    LaunchedEffect(repository) {
        try { repository.sources.collect { sources = it } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { }
    }
    val storage = remember(app) { StorageRepository(app.container.db) }
    val collection = remember(storage) { storage.getAllCharacters() }
    val characters by collection.collectAsStateWithLifecycle(initialValue = emptyList())
    val stored = characters.firstOrNull { it.id == characterId }
    var epoch by remember { mutableIntStateOf(0) }
    var choosing by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    val individualId by produceState<String?>(null, characterId, archiveIndividualId, epoch) {
        value = null
        try {
            value = withContext(Dispatchers.IO) {
                characterId?.let { app.container.db.userCharacterDao().getCharacter(it)?.individualId } ?: archiveIndividualId
            }
        }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { }
    }
    val archiveName by produceState<String?>(null, individualId, stored, epoch) {
        value = null
        val id = individualId ?: return@produceState
        if (stored != null) return@produceState
        try { value = withContext(Dispatchers.IO) { app.container.db.digimonIndividualDao().getIndividual(id)?.nickname } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { }
    }
    val character = stored?.nickname?.takeIf(String::isNotBlank) ?: stored?.speciesName ?: archiveName?.takeIf(String::isNotBlank)
    val retained by produceState<ObservedDigimon?>(null, individualId, epoch) {
        value = null
        val id = individualId ?: return@produceState
        try { value = sends.outbound(id) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (e: Exception) { Log.e("WorldDsSend", "outbound failed for $id", e) }
    }
    val away by produceState<WorldDsSendBlocker?>(null, individualId, epoch) {
        value = null
        val id = individualId ?: return@produceState
        try { value = sends.awayBlocker(id) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { }
    }
    val unavailable by produceState<WorldDsSendBlocker?>(null, individualId, epoch) {
        value = null
        val id = individualId ?: return@produceState
        try { value = sends.outboundBlocker(id) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (e: Exception) { Log.e("WorldDsSend", "outboundBlocker failed for $id", e) }
    }
    val destinations by produceState<List<Pair<LinkedSaveView, WorldDsSendBlocker?>>>(emptyList(), retained, away, sources, epoch) {
        value = emptyList()
        val id = individualId ?: return@produceState
        val record = retained ?: return@produceState
        if (away != null) return@produceState
        value = sources.orEmpty()
            .filter { TransferGameCapabilities.hasNativeWriter(TransferGameCatalog.identify(it.observation?.gameId, it.source.displayName)) }
            .map { view ->
                view to try { sends.blocker(view, id, record) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { WorldDsSendBlocker.FORMAT }
            }
    }
    val readyCount = destinations.count { it.second == null }
    val worldDsSubtitle = when {
        individualId == null -> R.string.transfer_destination_pending
        retained == null -> unavailable?.let(::sendBlockerText) ?: R.string.transfer_send_no_native
        away != null -> R.string.transfer_send_away
        destinations.isEmpty() -> R.string.transfer_destination_pending
        readyCount == 0 -> destinations.firstNotNullOfOrNull { it.second?.let(::sendBlockerText) } ?: R.string.transfer_destination_pending
        else -> R.string.transfer_send_ready
    }
    val groups = sources.orEmpty().groupBy { TransferGameCatalog.identify(it.observation?.gameId, it.source.displayName) }
    val games = TransferGame.entries.filter { TransferGameCapabilities.readsNdsStory(it) || groups[it].orEmpty().isNotEmpty() }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            character?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
            Text(stringResource(R.string.transfer_send_hint), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (sending) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 8.dp)) {
            item(key = "bracelet") {
                OriginRow(stringResource(R.string.transfer_watch_section), stringResource(R.string.transfer_origin_devices),
                    onClick = onDevices, leading = { Icon(Icons.Default.Nfc, null, Modifier.size(32.dp)) })
            }
            items(games, key = { it.name }) { game ->
                if (TransferGameCapabilities.hasNativeWriter(game))
                    OriginRow(stringResource(gameTitle(game)), stringResource(worldDsSubtitle),
                        onClick = { choosing = true }, leading = { TransferOriginIcon(game, Modifier.size(40.dp)) },
                        enabled = readyCount > 0 && !sending)
                else
                    OriginRow(stringResource(gameTitle(game)), stringResource(R.string.transfer_destination_pending),
                        onClick = {}, leading = { TransferOriginIcon(game, Modifier.size(40.dp)) }, enabled = false)
            }
        }
    }
    if (choosing) AlertDialog(onDismissRequest = { if (!sending) choosing = false },
        title = { Text(stringResource(R.string.transfer_send_choose_save)) },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                destinations.forEach { (view, blocker) -> item(key = view.source.sourceId) {
                    TextButton(enabled = blocker == null && !sending, onClick = {
                        choosing = false
                        sending = true
                        val id = individualId
                        val revision = view.observation?.contentRevision
                        scope.launch {
                            try {
                                sends.send(requireNotNull(id), view.source.sourceId, requireNotNull(revision))
                                context.showAppFeedback(R.string.transfer_send_completed)
                            } catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: WorldDsSendRecoveryRequired) { context.showAppFeedback(R.string.transfer_move_recovery) }
                            catch (e: ImportSourceChangedException) { Log.e("WorldDsSend", "send source changed", e); context.showAppFeedback(R.string.transfer_import_source_changed) }
                            catch (e: Exception) { Log.e("WorldDsSend", "send failed", e); context.showAppFeedback(R.string.transfer_action_failed) }
                            finally { sending = false; epoch++ }
                        }
                    }) {
                        Column {
                            Text(view.source.label)
                            Text(stringResource(blocker?.let(::sendBlockerText) ?: R.string.transfer_send_ready),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } }
            }
        },
        confirmButton = { TextButton(onClick = { choosing = false }, enabled = !sending) { Text(stringResource(R.string.transfer_close)) } })
}

private fun sendBlockerText(blocker: WorldDsSendBlocker): Int = when (blocker) {
    WorldDsSendBlocker.SNAPSHOT_UNAVAILABLE -> R.string.transfer_send_no_native
    WorldDsSendBlocker.CONVERSION_UNAVAILABLE -> R.string.transfer_send_no_species
    WorldDsSendBlocker.ALREADY_AWAY -> R.string.transfer_send_away
    WorldDsSendBlocker.FORMAT -> R.string.transfer_native_move_unavailable
    WorldDsSendBlocker.REVIEW -> R.string.transfer_record_needs_review
    WorldDsSendBlocker.STALE -> R.string.transfer_import_source_changed
    WorldDsSendBlocker.WRITE_ACCESS -> R.string.transfer_move_write_access
    WorldDsSendBlocker.IDENTITY_TAKEN -> R.string.transfer_send_identity_taken
    WorldDsSendBlocker.DESTINATION_FULL -> R.string.transfer_send_destination_full
}

@Composable
private fun OriginRow(title: String, subtitle: String, onClick: () -> Unit, leading: @Composable () -> Unit,
    enabled: Boolean = true) {
    val trailing: (@Composable () -> Unit)? = if (enabled) {
        { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) }
    } else null
    ListItem(headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
        supportingContent = { Text(subtitle, style = MaterialTheme.typography.bodySmall) },
        leadingContent = { Box(Modifier.size(48.dp).alpha(if (enabled) 1f else 0.38f), contentAlignment = Alignment.Center) { leading() } },
        trailingContent = trailing,
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0f),
            headlineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f),
            supportingColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.38f)),
        modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp)
            .then(if (enabled) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier))
    HorizontalDivider(Modifier.padding(start = 80.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

internal fun gameTitle(game: TransferGame): Int = when (game) {
    TransferGame.WORLD_DS -> R.string.transfer_game_world_ds
    TransferGame.DAWN -> R.string.transfer_game_dawn
    TransferGame.DUSK -> R.string.transfer_game_dusk
    TransferGame.LOST_EVOLUTION -> R.string.transfer_game_lost_evolution
    TransferGame.SUPER_XROS_WARS_BLUE -> R.string.transfer_game_xros_blue
    TransferGame.SUPER_XROS_WARS_RED -> R.string.transfer_game_xros_red
    TransferGame.TIME_STRANGER -> R.string.transfer_time_stranger
}

