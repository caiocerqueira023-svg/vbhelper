package com.tamerslab.transfer.ui

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import com.tamerslab.components.VitalSectionHeader
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Folder
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
import androidx.compose.ui.text.style.TextOverflow
import com.tamerslab.gamesave.SaveContainer
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
    onManageSaves: () -> Unit, onDevices: () -> Unit, onVpet: () -> Unit) {
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
        if (opening) LinearProgressIndicator(Modifier.fillMaxWidth())
        when {
            failed -> Column(Modifier.weight(1f).padding(16.dp)) {
                Text(stringResource(R.string.transfer_list_failed))
                TextButton(onClick = { attempt++ }) { Text(stringResource(R.string.transfer_refresh)) }
            }
            sources == null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else -> {
                val totalSaves = groups.values.sumOf { it.size }
                LazyVerticalGrid(GridCells.Fixed(2), Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item(key = "library-header", span = { GridItemSpan(2) }) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            com.tamerslab.components.VitalSectionBar(
                                stringResource(R.string.transfer_menu_library))
                            TransferWidePlate(
                                title = stringResource(R.string.transfer_workspace_open),
                                status = pluralStringResource(R.plurals.transfer_workspace_save_count,
                                    totalSaves, totalSaves),
                                onClick = { picker.launch(arrayOf("*/*")) },
                                leading = {
                                    Icon(Icons.Default.Folder, null, Modifier.size(24.dp))
                                },
                                enabled = !opening,
                            )
                        }
                    }
                    items(games, key = { it.name }) { game ->
                        val saves = groups[game].orEmpty()
                        TransferGamePlate(
                            title = stringResource(gameTitle(game)),
                            status = gameShelfStatus(saves),
                            census = gameShelfCensus(saves),
                            onClick = { onOpenGame(game) },
                            leading = { TransferOriginIcon(game, Modifier.size(72.dp), iconRevision) },
                        )
                    }
                    item(key = "collection-header", span = { GridItemSpan(2) }) {
                        com.tamerslab.components.VitalSectionBar(
                            stringResource(R.string.transfer_collection_section))
                    }
                    item(key = "storage", span = { GridItemSpan(2) }) {
                        TransferWidePlate(
                            stringResource(R.string.transfer_workspace_vault),
                            stringResource(R.string.transfer_origin_storage),
                            onClick = onStorage,
                            leading = { Icon(Icons.Default.Inventory2, null, Modifier.size(26.dp)) },
                        )
                    }
                    item(key = "devices-header", span = { GridItemSpan(2) }) {
                        com.tamerslab.components.VitalSectionBar(
                            stringResource(R.string.transfer_devices_section))
                    }
                    item(key = "bracelet", span = { GridItemSpan(2) }) {
                        TransferWidePlate(
                            stringResource(R.string.transfer_watch_section),
                            stringResource(R.string.transfer_origin_devices),
                            onClick = onDevices,
                            leading = { Icon(Icons.Default.Nfc, null, Modifier.size(26.dp)) },
                        )
                    }
                    item(key = "vpet", span = { GridItemSpan(2) }) {
                        TransferWidePlate(
                            stringResource(R.string.vpet_title),
                            stringResource(R.string.transfer_origin_vpet),
                            onClick = onVpet,
                            leading = { Icon(Icons.Default.Build, null, Modifier.size(26.dp)) },
                        )
                    }

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
    val artworkVersion = rememberTransferArtworkVersion()
    var sources by remember { mutableStateOf<List<LinkedSaveView>?>(null) }
    LaunchedEffect(repository) {
        try { repository.sources.collect { sources = it } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { }
    }
    val storage = remember(app) { app.container.storageRepository }
    val collection = remember(storage) { storage.getAllCharacters() }
    val characters by collection.collectAsStateWithLifecycle(initialValue = emptyList())
    val stored = characters.firstOrNull { it.id == characterId }
    var epoch by remember { mutableIntStateOf(0) }
    // The receiving-save chooser only exists for games that actually have several saves.
    var choosingGame by remember { mutableStateOf<TransferGame?>(null) }
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
    val sendSpecies by produceState<com.tamerslab.species.CanonicalSpeciesDto?>(null, individualId, stored) {
        value = null
        val id = individualId ?: return@produceState
        try {
            val speciesName = withContext(Dispatchers.IO) {
                stored?.speciesName ?: app.container.db.digimonIndividualDao().getIndividual(id)?.speciesName
            } ?: return@produceState
            value = withContext(Dispatchers.IO) {
                app.container.dotSpriteLibrary.catalogBlocking().resolve(speciesName)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            value = null
        }
    }

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
            .filter { TransferGameCapabilities.hasWiredWriter(TransferGameCatalog.identify(it.observation?.gameId, it.source.displayName)) }
            .map { view ->
                view to try { sends.blocker(view, id, record) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { WorldDsSendBlocker.FORMAT }
            }
    }
    val readyCount = destinations.count { it.second == null }
    val worldDsSubtitle = when {
        individualId == null -> R.string.transfer_destination_pending
        retained == null -> unavailable?.let(::sendBlockerText) ?: R.string.transfer_send_pick_supported_game
        away != null -> R.string.transfer_send_away
        destinations.isEmpty() -> R.string.transfer_destination_pending
        readyCount == 0 -> destinations.firstNotNullOfOrNull { it.second?.let(::sendBlockerText) } ?: R.string.transfer_destination_pending
        else -> R.string.transfer_send_ready
    }
    val groups = sources.orEmpty().groupBy { TransferGameCatalog.identify(it.observation?.gameId, it.source.displayName) }
    val games = TransferGame.entries.filter { TransferGameCapabilities.readsNdsStory(it) || groups[it].orEmpty().isNotEmpty() }
    // Two plain-language steps: the game must be closed for the write, then reopened to read it.
    var readyToSend by remember { mutableStateOf<Pair<LinkedSaveView, String?>?>(null) }
    var sentToGame by remember { mutableStateOf(false) }
    // Native container the player wants the Digimon to land in; filtered per destination game.
    var sendDestination by remember { mutableStateOf<com.tamerslab.gamesave.SaveContainer?>(null) }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TransferOutgoingPlate(
                nickname = character ?: stringResource(R.string.transfer_unnamed_record),
                species = sendSpecies,
                level = retained?.level,
                artworkVersion = artworkVersion,
                status = stringResource(worldDsSubtitle),
            )
            Text(stringResource(R.string.transfer_send_hint), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (sending) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyVerticalGrid(GridCells.Fixed(2), Modifier.weight(1f), contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(games, key = { it.name }) { game ->
                val wired = TransferGameCapabilities.hasWiredWriter(game)
                val gameSaves = destinations.filter { (view, _) ->
                    TransferGameCatalog.identify(view.observation?.gameId, view.source.displayName) == game
                }
                TransferGamePlate(
                    title = stringResource(gameTitle(game)),
                    status = stringResource(if (wired) worldDsSubtitle else R.string.transfer_destination_pending),
                    onClick = {
                        val single = gameSaves.singleOrNull()
                        if (gameSaves.size == 1 && single != null && single.second == null) {
                            // One receiving save: go straight to the placement confirmation.
                            sendDestination = TransferGameCapabilities.defaultSendDestination(game)
                            readyToSend = single.first to single.first.observation?.contentRevision
                        } else {
                            choosingGame = game
                        }
                    },
                    leading = { TransferOriginIcon(game, Modifier.size(52.dp)) },
                    enabled = wired && gameSaves.isNotEmpty() && !sending,
                )
            }
            item(key = "bracelet", span = { GridItemSpan(2) }) {
                TransferWidePlate(
                    stringResource(R.string.transfer_watch_section),
                    stringResource(R.string.transfer_origin_devices),
                    onClick = onDevices,
                    leading = { Icon(Icons.Default.Nfc, null, Modifier.size(26.dp)) },
                )
            }
        }
    }
    choosingGame?.let { chosenGame ->
        val gameSaves = destinations.filter { (view, _) ->
            TransferGameCatalog.identify(view.observation?.gameId, view.source.displayName) == chosenGame
        }
        AlertDialog(onDismissRequest = { if (!sending) choosingGame = null },
            title = { Text(stringResource(gameTitle(chosenGame))) },
            text = {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                    item(key = "caption") {
                        Text(stringResource(R.string.transfer_send_choose_save),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp))
                    }
                    gameSaves.forEach { (view, blocker) -> item(key = view.source.sourceId) {
                        TextButton(enabled = blocker == null && !sending, onClick = {
                            choosingGame = null
                            sendDestination = TransferGameCapabilities.defaultSendDestination(chosenGame)
                            readyToSend = view to view.observation?.contentRevision
                        }) {
                            Column {
                                Text(view.source.label)
                                Text(stringResource(blocker?.let(::sendBlockerText) ?: R.string.transfer_send_ready),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } }
                }
            },
            confirmButton = { TextButton(onClick = { choosingGame = null }, enabled = !sending) {
                Text(stringResource(R.string.transfer_close))
            } })
    }
    readyToSend?.let { (view, revision) ->
        val id = individualId
        val sendGame = TransferGameCatalog.identify(view.observation?.gameId, view.source.displayName)
        val sendOptions = TransferGameCapabilities.sendDestinations(sendGame)
        AlertDialog(onDismissRequest = { if (!sending) readyToSend = null },
            title = { Text(stringResource(R.string.transfer_send_close_game_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (sendOptions.size > 1) {
                        Text(stringResource(R.string.transfer_send_destination_choose),
                            style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            sendOptions.forEach { option ->
                                FilterChip(selected = sendDestination == option,
                                    onClick = { sendDestination = option },
                                    enabled = !sending,
                                    label = { Text(stringResource(destinationLabel(option))) })
                            }
                        }
                    }
                    Text(stringResource(R.string.transfer_send_close_game_body))
                }
            },
            confirmButton = { TextButton(enabled = id != null && revision != null && !sending, onClick = {
                readyToSend = null
                sending = true
                scope.launch {
                    try {
                        sends.send(requireNotNull(id), view.source.sourceId, requireNotNull(revision),
                            sendDestination)
                        sentToGame = true
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: WorldDsSendRecoveryRequired) { context.showAppFeedback(R.string.transfer_move_recovery) }
                    catch (e: ImportSourceChangedException) { Log.e("WorldDsSend", "send source changed", e); context.showAppFeedback(R.string.transfer_import_source_changed) }
                    catch (e: Exception) { Log.e("WorldDsSend", "send failed", e); context.showAppFeedback(R.string.transfer_action_failed) }
                    finally { sending = false; epoch++ }
                }
            }) { Text(stringResource(R.string.transfer_send_confirm)) } },
            dismissButton = { TextButton(enabled = !sending, onClick = { readyToSend = null }) { Text(stringResource(R.string.transfer_close)) } })
    }
    if (sentToGame) {
        AlertDialog(onDismissRequest = { sentToGame = false },
            title = { Text(stringResource(R.string.transfer_send_open_game_title)) },
            text = { Text(stringResource(R.string.transfer_send_open_game_body)) },
            confirmButton = { TextButton(onClick = { sentToGame = false }) { Text(stringResource(R.string.transfer_send_ok)) } })
    }
}

private fun destinationLabel(container: com.tamerslab.gamesave.SaveContainer): Int = when (container) {
    com.tamerslab.gamesave.SaveContainer.PARTY -> R.string.transfer_send_destination_party
    com.tamerslab.gamesave.SaveContainer.BANK -> R.string.transfer_send_destination_bank
    com.tamerslab.gamesave.SaveContainer.FARM -> R.string.transfer_send_destination_farm
    else -> R.string.transfer_send_destination_party
}

private fun sendBlockerText(blocker: WorldDsSendBlocker): Int = when (blocker) {
    WorldDsSendBlocker.SNAPSHOT_UNAVAILABLE -> R.string.transfer_send_no_native
    WorldDsSendBlocker.SPECIES_UNKNOWN -> R.string.transfer_send_species_unknown
    WorldDsSendBlocker.SPECIES_NOT_IN_GAME -> R.string.transfer_send_species_not_in_game
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

@Composable
private fun gameShelfStatus(saves: List<LinkedSaveView>): String = when {
    saves.isEmpty() -> stringResource(R.string.transfer_workspace_open)
    saves.none { it.observation != null } -> stringResource(R.string.transfer_library_unreadable)
    else -> pluralStringResource(R.plurals.transfer_workspace_save_count, saves.size, saves.size)
}

/** Roster census for a single readable save; multi-save shelves only show the save count. */
@Composable
private fun gameShelfCensus(saves: List<LinkedSaveView>): Triple<Int, Int, Int>? {
    val observation = saves.singleOrNull()?.observation ?: return null
    val counts = shownRecords(observation).groupingBy { it.container }.eachCount()
    return Triple(counts[SaveContainer.PARTY] ?: 0, counts[SaveContainer.FARM] ?: 0,
        counts[SaveContainer.BANK] ?: 0)
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

