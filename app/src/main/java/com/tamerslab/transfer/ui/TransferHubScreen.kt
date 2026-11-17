package com.tamerslab.transfer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.tamerslab.R
import com.tamerslab.components.*
import com.tamerslab.di.TamersLabApplication
import com.tamerslab.domain.device_data.CharacterCapabilities
import com.tamerslab.domain.device_data.UserCharacter
import com.tamerslab.navigation.AppRoute
import com.tamerslab.navigation.TransferSection
import com.tamerslab.navigation.TransferGame
import com.tamerslab.transfer.ui.scan.ScanScreen
import com.tamerslab.transfer.ui.scan.ScanScreenController
import com.tamerslab.transfer.ui.vpet.VpetSectionScreen
import com.tamerslab.storage.ui.BraceletConversionDialog
import com.tamerslab.storage.ui.NativeGameArchivePanel
import com.tamerslab.storage.data.StorageRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Game library first; each game's roster transfers through Tamers Lab Storage. */
@Composable
fun TransferHubScreen(navController: NavController, scanController: ScanScreenController,
    initialSection: TransferSection = TransferSection.SAVES, initialCharacterId: Long? = null,
    initialIndividualId: String? = null) {
    val application = LocalContext.current.applicationContext as TamersLabApplication
    var page by rememberSaveable(initialSection) {
        mutableStateOf(when (initialSection) {
            TransferSection.DEVICES -> "devices"
            TransferSection.STORAGE -> "storage"
            TransferSection.VPET -> "vpet"
            TransferSection.SEND -> "send"
            else -> "library"
        })
    }
    var sendIndividualId by rememberSaveable { mutableStateOf(initialIndividualId) }
    var gameKey by rememberSaveable { mutableStateOf<String?>(null) }
    val game = TransferGame.entries.firstOrNull { it.name == gameKey }
    var menuOpen by remember { mutableStateOf(false) }
    var operationBusy by remember { mutableStateOf(false) }
    var pageHistory by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val openPage: (String) -> Unit = { target ->
        if (target != page) {
            pageHistory = pageHistory + page
            page = target
        }
    }
    val openGame: (TransferGame) -> Unit = { gameKey = it.name; openPage("game") }
    val leave: () -> Unit = {
        when {
            pageHistory.isNotEmpty() -> {
                page = pageHistory.last()
                pageHistory = pageHistory.dropLast(1)
            }
            page != "library" -> page = "library"
            else -> navController.popBackStack()
        }
    }
    BackHandler(enabled = page != "library") { if (!operationBusy) leave() }
    Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0), topBar = {
        TopBanner(stringResource(when (page) {
            "manage" -> R.string.transfer_workspace_manage
            "archives" -> R.string.transfer_workspace_vault_tools
            "devices" -> R.string.transfer_watch_section
            "vpet" -> R.string.vpet_title
            "storage" -> R.string.transfer_workspace_vault
            "game" -> gameTitle(game ?: TransferGame.WORLD_DS)
            "icons" -> R.string.transfer_launcher_icons
            "backups" -> R.string.transfer_move_backups
            "send" -> R.string.transfer_send_title
            else -> R.string.transfer_title
        }), onBackClick = if (operationBusy) null else leave, trailingAction = {
            Box {
                IconButton(onClick = { menuOpen = true }, enabled = !operationBusy) {
                    Icon(Icons.Default.MoreVert, stringResource(R.string.transfer_open_options))
                }
                DropdownMenu(menuOpen, onDismissRequest = { menuOpen = false }) {
                    listOf(
                        Triple("library", R.string.transfer_origins_title, Icons.AutoMirrored.Filled.List),
                        Triple("devices", R.string.transfer_watch_section, Icons.Default.Nfc),
                        Triple("vpet", R.string.vpet_title, Icons.Default.Build),
                        Triple("manage", R.string.transfer_workspace_manage, Icons.Default.Edit),
                        Triple("archives", R.string.transfer_workspace_vault_tools, Icons.Default.Inventory2),
                        Triple("backups", R.string.transfer_move_backups, Icons.Default.Backup),
                        Triple("icons", R.string.transfer_launcher_icons, Icons.Default.Image),
                    ).forEach { (target, label, icon) ->
                        DropdownMenuItem(
                            text = { Text(stringResource(label)) },
                            leadingIcon = { Icon(icon, contentDescription = null) },
                            trailingIcon = if (target == page) {
                                { Icon(Icons.Default.Check, contentDescription = null) }
                            } else null,
                            onClick = {
                                menuOpen = false
                                openPage(target)
                            },
                        )
                    }
                }
            }
        })
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (page) {
                "game", "storage" -> key(page, gameKey) {
                    TransferWorkspaceScreen(game = game.takeIf { page == "game" }, startInStorage = page == "storage",
                        initialCharacterId = initialCharacterId, onManageSaves = { openPage("manage") }, onOpenGame = openGame,
                        onSendToGame = { individualId -> sendIndividualId = individualId; openPage("send") })
                }
                "manage" -> TransferScreen(showHeader = false)
                "archives" -> {
                    val archives by application.container.gameImportAcquisition.archives.collectAsStateWithLifecycle(initialValue = null)
                    Column(Modifier.fillMaxSize().padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.transfer_storage_hint), Modifier.padding(horizontal = 16.dp),
                            style = MaterialTheme.typography.bodyMedium)
                        when {
                            archives == null -> CircularProgressIndicator(Modifier.padding(16.dp))
                            archives!!.isEmpty() -> {
                                Text(stringResource(R.string.transfer_archives_empty), Modifier.padding(horizontal = 16.dp))
                                TextButton(onClick = { page = "library" }) { Text(stringResource(R.string.transfer_library_games)) }
                            }
                            else -> NativeGameArchivePanel(archives!!, application.container.nativeMaterialization)
                        }
                    }
                }
                "devices" -> TransferDevicesPane(navController, scanController, initialCharacterId) { operationBusy = it }
                "vpet" -> VpetSectionScreen(initialCharacterId = initialCharacterId)
                "send" -> TransferSendScreen(initialCharacterId, sendIndividualId) { openPage("devices") }
                "icons" -> TransferLauncherIconsScreen()
                "backups" -> GameMoveBackupsScreen()
                else -> TransferGameLibraryScreen(onOpenGame = openGame, onStorage = {
                    navController.navigate(com.tamerslab.navigation.AppRoute.Storage) { launchSingleTop = true }
                },
                    onManageSaves = { openPage("manage") }, onDevices = { openPage("devices") })
            }
        }
    }
}

@Composable
private fun TransferDevicesPane(navController: NavController, scanController: ScanScreenController,
    initialCharacterId: Long?, onBusy: (Boolean) -> Unit) {
    val application = LocalContext.current.applicationContext as TamersLabApplication
    val repository = remember(application) { StorageRepository(application.container.db) }
    val collection = remember(repository) { repository.getAllCharacters() }
    val characters by collection.collectAsStateWithLifecycle(initialValue = emptyList())
    val participation = rememberPartnerParticipation()
    val eligible = characters.filter { !it.isInAdventure && participation?.owned(it.id)?.usable == true }
    var selectedId by rememberSaveable(initialCharacterId) { mutableStateOf(initialCharacterId) }
    var choosing by rememberSaveable { mutableStateOf(false) }
    var converting by rememberSaveable { mutableStateOf(false) }
    var epoch by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    val selected = eligible.firstOrNull { it.id == selectedId }
    val outgoing by produceState<UserCharacter?>(null, selected?.id, epoch) {
        value = null
        val id = selected?.id ?: return@produceState
        try { value = withContext(Dispatchers.IO) { application.container.db.userCharacterDao().getCharacter(id) } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { value = null }
    }
    // The physical slot backing the partner: a catalog-only partner with no export target needs a
    // DIM/BEM association before it can be written to a watch, whatever its stored device type is.
    val capabilityResolver = remember(application) {
        com.tamerslab.storage.data.CharacterCapabilityResolver(application.container.db)
    }
    val capabilities by produceState<CharacterCapabilities?>(null, outgoing?.id) {
        value = null
        val character = outgoing ?: return@produceState
        try { value = withContext(Dispatchers.IO) { capabilityResolver.resolve(character) } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { value = null }
    }
    val needsConversion = capabilities?.needsAssociation == true
    val sendId = if (busy) selectedId else selectedId.takeIf {
        selected != null && capabilities != null && !needsConversion
    }
    Column(Modifier.fillMaxSize()) {
        CyberPanel(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            active = selected != null && !busy,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(R.string.transfer_outgoing_partner),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        when {
                            selected != null -> selected.nickname?.takeIf(String::isNotBlank)
                                ?: selected.speciesName ?: stringResource(R.string.transfer_unnamed_record)
                            selectedId != null -> stringResource(R.string.transfer_outgoing_unavailable)
                            else -> stringResource(R.string.transfer_receive_only_short)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TextButton(onClick = { choosing = true }, enabled = !busy) {
                    Text(stringResource(R.string.transfer_change_partner))
                }
            }
            if (selected != null && outgoing == null) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (needsConversion && outgoing != null) {
                VitalButton(
                    onClick = { converting = true },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.conversion_title)) }
            }
            Text(
                stringResource(R.string.transfer_device_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(Modifier.weight(1f)) {
            key(selectedId, epoch) {
                ScanScreen(navController, sendId, scanController, launchedFromHomeScreen = true, embedded = true,
                    onOperationActiveChange = { busy = it; onBusy(it) }, onFinished = { busy = false; onBusy(false); epoch++ })
            }
        }
    }
    if (choosing) {
        AlertDialog(
            onDismissRequest = { choosing = false },
            title = { Text(stringResource(R.string.transfer_choose_outgoing)) },
            text = {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                    item(key = "receive-only") {
                        PartnerOption(
                            label = stringResource(R.string.transfer_receive_only_short),
                            supporting = stringResource(R.string.transfer_receive_only),
                            selected = selectedId == null,
                            onClick = { selectedId = null; choosing = false },
                        )
                    }
                    items(eligible, key = { it.id }) { partner ->
                        PartnerOption(
                            label = partner.nickname?.takeIf(String::isNotBlank)
                                ?: partner.speciesName ?: stringResource(R.string.transfer_unnamed_record),
                            supporting = partner.speciesName?.takeIf { partner.nickname?.isNotBlank() == true },
                            selected = partner.id == selectedId,
                            onClick = { selectedId = partner.id; choosing = false },
                        )
                    }
                    if (eligible.isEmpty()) {
                        item(key = "no-outgoing") {
                            Text(
                                stringResource(R.string.transfer_no_outgoing),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { choosing = false }) { Text(stringResource(R.string.transfer_close)) }
            },
        )
    }
    if (converting && selectedId != null) BraceletConversionDialog(requireNotNull(selectedId),
        onDismiss = { converting = false }, onConverted = { converting = false; epoch++ })
}

@Composable
private fun PartnerOption(
    label: String,
    supporting: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = supporting?.let { { Text(it) } },
        leadingContent = { RadioButton(selected = selected, onClick = onClick) },
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}
