package com.github.nacabaro.vbhelper.screens.transferScreen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.components.*
import com.github.nacabaro.vbhelper.di.VBHelper
import com.github.nacabaro.vbhelper.domain.device_data.CharacterCapabilities
import com.github.nacabaro.vbhelper.domain.device_data.UserCharacter
import com.github.nacabaro.vbhelper.navigation.TransferSection
import com.github.nacabaro.vbhelper.navigation.TransferGame
import com.github.nacabaro.vbhelper.screens.scanScreen.ScanScreen
import com.github.nacabaro.vbhelper.screens.scanScreen.ScanScreenController
import com.github.nacabaro.vbhelper.screens.storageScreen.BraceletConversionDialog
import com.github.nacabaro.vbhelper.screens.storageScreen.NativeGameArchivePanel
import com.github.nacabaro.vbhelper.source.StorageRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Game library first; each game's roster transfers through VBHelper Storage. */
@Composable
fun TransferHubScreen(navController: NavController, scanController: ScanScreenController,
    initialSection: TransferSection = TransferSection.SAVES, initialCharacterId: Long? = null,
    initialIndividualId: String? = null) {
    val application = LocalContext.current.applicationContext as VBHelper
    var page by rememberSaveable(initialSection) {
        mutableStateOf(when (initialSection) {
            TransferSection.DEVICES -> "devices"
            TransferSection.STORAGE -> "storage"
            TransferSection.SEND -> "send"
            else -> "library"
        })
    }
    var sendIndividualId by rememberSaveable { mutableStateOf(initialIndividualId) }
    var gameKey by rememberSaveable { mutableStateOf<String?>(null) }
    val game = TransferGame.entries.firstOrNull { it.name == gameKey }
    val openGame: (TransferGame) -> Unit = { gameKey = it.name; page = "game" }
    var menuOpen by remember { mutableStateOf(false) }
    var operationBusy by remember { mutableStateOf(false) }
    val leave: () -> Unit = { if (page == "library" || page == "send") navController.popBackStack() else page = "library" }
    BackHandler(enabled = page != "library") { if (!operationBusy) leave() }
    Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0), topBar = {
        TopBanner(stringResource(when (page) {
            "manage" -> R.string.transfer_workspace_manage
            "archives" -> R.string.transfer_workspace_vault_tools
            "devices" -> R.string.transfer_watch_section
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
                    listOf("library" to R.string.transfer_origins_title,
                        "manage" to R.string.transfer_workspace_manage,
                        "archives" to R.string.transfer_workspace_vault_tools,
                        "icons" to R.string.transfer_launcher_icons,
                        "backups" to R.string.transfer_move_backups,
                        "devices" to R.string.transfer_watch_section).forEach { (target, label) ->
                        DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = {
                            menuOpen = false; page = target
                        })
                    }
                }
            }
        })
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (page) {
                "game", "storage" -> key(page, gameKey) {
                    TransferWorkspaceScreen(game = game.takeIf { page == "game" }, startInStorage = page == "storage",
                        initialCharacterId = initialCharacterId, onManageSaves = { page = "manage" }, onOpenGame = openGame,
                        onSendToGame = { individualId -> sendIndividualId = individualId; page = "send" })
                }
                "manage" -> TransferScreen(navController, showHeader = false)
                "archives" -> {
                    val archives by application.container.gameImportAcquisition.archives.collectAsState(initial = null)
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
                "send" -> TransferSendScreen(initialCharacterId, sendIndividualId) { page = "devices" }
                "icons" -> TransferLauncherIconsScreen()
                "backups" -> GameMoveBackupsScreen()
                else -> TransferGameLibraryScreen(onOpenGame = openGame, onStorage = {
                    navController.navigate(com.github.nacabaro.vbhelper.navigation.NavigationItems.Storage.route) { launchSingleTop = true }
                },
                    onManageSaves = { page = "manage" }, onDevices = { page = "devices" })
            }
        }
    }
}

@Composable
private fun TransferDevicesPane(navController: NavController, scanController: ScanScreenController,
    initialCharacterId: Long?, onBusy: (Boolean) -> Unit) {
    val application = LocalContext.current.applicationContext as VBHelper
    val repository = remember(application) { StorageRepository(application.container.db) }
    val collection = remember(repository) { repository.getAllCharacters() }
    val characters by collection.collectAsState(initial = emptyList())
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
        com.github.nacabaro.vbhelper.source.CharacterCapabilityResolver(application.container.db)
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
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.transfer_device_hint), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { choosing = true }, enabled = !busy) {
                Text(selected?.nickname?.takeIf(String::isNotBlank) ?: selected?.speciesName
                    ?: stringResource(if (selectedId == null) R.string.transfer_choose_outgoing else R.string.transfer_outgoing_unavailable))
            }
            if (selected != null && outgoing == null) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (needsConversion && outgoing != null) VitalButton(onClick = { converting = true }, enabled = !busy,
                modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.conversion_title)) }
        }
        Box(Modifier.weight(1f)) {
            key(selectedId, epoch) {
                ScanScreen(navController, sendId, scanController, launchedFromHomeScreen = true, embedded = true,
                    onOperationActiveChange = { busy = it; onBusy(it) }, onFinished = { busy = false; onBusy(false); epoch++ })
            }
        }
    }
    if (choosing) AlertDialog(onDismissRequest = { choosing = false }, title = { Text(stringResource(R.string.transfer_choose_outgoing)) },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { TextButton(onClick = { selectedId = null; choosing = false }) { Text(stringResource(R.string.transfer_receive_only)) } }
                items(eligible, key = { it.id }) { partner ->
                    TextButton(onClick = { selectedId = partner.id; choosing = false }) {
                        Text(partner.nickname?.takeIf(String::isNotBlank) ?: partner.speciesName ?: stringResource(R.string.transfer_unnamed_record))
                    }
                }
                if (eligible.isEmpty()) item { Text(stringResource(R.string.transfer_no_outgoing)) }
            }
        }, confirmButton = { TextButton(onClick = { choosing = false }) { Text(stringResource(R.string.transfer_close)) } })
    if (converting && selectedId != null) BraceletConversionDialog(requireNotNull(selectedId),
        onDismiss = { converting = false }, onConverted = { converting = false; epoch++ })
}
