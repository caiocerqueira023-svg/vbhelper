package com.tamerslab.transfer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.tamerslab.R
import com.tamerslab.components.*
import com.tamerslab.di.TamersLabApplication
import com.tamerslab.domain.device_data.CharacterCapabilities
import com.tamerslab.domain.device_data.UserCharacter
import com.tamerslab.dtos.CharacterDtos
import com.tamerslab.navigation.AppRoute
import com.tamerslab.navigation.TransferSection
import com.tamerslab.navigation.TransferGame
import com.tamerslab.transfer.ui.scan.ScanScreen
import com.tamerslab.transfer.ui.scan.ScanScreenController
import com.tamerslab.transfer.ui.vpet.VpetSectionScreen
import com.tamerslab.storage.ui.BraceletConversionDialog
import com.tamerslab.storage.ui.StorageCharacterPickerDialog
import com.tamerslab.storage.ui.StorageCharacterPickerOption
import com.tamerslab.storage.ui.NativeGameArchivePanel
import com.tamerslab.storage.data.StorageRepository
import com.tamerslab.ui.theme.SurfaceElevatedPurple
import com.tamerslab.ui.theme.SurfaceStroke
import com.tamerslab.ui.theme.TextMutedOnDark
import com.tamerslab.ui.theme.TextPrimaryOnDark
import com.tamerslab.ui.theme.TextSecondaryOnDark
import com.tamerslab.ui.theme.VitalCyan
import com.tamerslab.utils.BitmapData
import com.tamerslab.utils.getBitmap
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
            "tools" -> R.string.transfer_menu_section_tools
            else -> R.string.transfer_title
        }), onBackClick = if (operationBusy) null else leave, trailingAction = {
            if (page != "tools") {
                IconButton(onClick = { openPage("tools") }, enabled = !operationBusy) {
                    Icon(Icons.Default.Build, stringResource(R.string.transfer_menu_section_tools))
                }
            }
        })
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (page) {
                "library" -> TransferGameLibraryScreen(onOpenGame = openGame,
                    onStorage = {
                        navController.navigate(com.tamerslab.navigation.AppRoute.Storage) { launchSingleTop = true }
                    },
                    onManageSaves = { openPage("manage") }, onDevices = { openPage("devices") },
                    onVpet = { openPage("vpet") })
                "storage" -> key("storage") {
                    TransferWorkspaceScreen(game = null, startInStorage = true,
                        initialCharacterId = initialCharacterId,
                        onManageSaves = { openPage("manage") }, onOpenGame = openGame,
                        onSendToGame = { individualId -> sendIndividualId = individualId; openPage("send") })
                }
                "tools" -> TransferToolsScreen(
                    onManageSaves = { openPage("manage") },
                    onArchives = { openPage("archives") },
                    onBackups = { openPage("backups") },
                    onIcons = { openPage("icons") },
                )
                "game" -> key("game", gameKey) {
                    TransferWorkspaceScreen(game = game, startInStorage = false,
                        initialCharacterId = initialCharacterId,
                        onManageSaves = { openPage("manage") }, onOpenGame = openGame,
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
                "vpet" -> Column(Modifier.fillMaxSize()) {
                    TransferOriginIdentityBar(
                        title = stringResource(R.string.vpet_title),
                        subtitle = stringResource(R.string.transfer_menu_vpet_desc),
                        leading = { Icon(Icons.Default.Build, null, Modifier.size(24.dp)) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    Box(Modifier.weight(1f)) {
                        VpetSectionScreen(initialCharacterId = initialCharacterId)
                    }
                }
                "send" -> TransferSendScreen(initialCharacterId, sendIndividualId) { openPage("devices") }
                "icons" -> TransferLauncherIconsScreen()
                "backups" -> GameMoveBackupsScreen()
                else -> Unit
            }
        }
    }
}

@Composable
private fun TransferDevicesPane(navController: NavController, scanController: ScanScreenController,
    initialCharacterId: Long?, onBusy: (Boolean) -> Unit) {
    val application = LocalContext.current.applicationContext as TamersLabApplication
    val repository = remember(application) { application.container.storageRepository }
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
    LaunchedEffect(selectedId, eligible, busy) {
        if (!busy && selectedId != null && selected == null) selectedId = null
    }
    Column(Modifier.fillMaxSize()) {
        CyberPanel(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            active = selected != null,
            onClick = if (busy) null else {
                { choosing = true }
            },
            contentPadding = PaddingValues(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PartnerAvatar(selected, Modifier.size(58.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        text = stringResource(
                            if (selected != null) R.string.transfer_outgoing_partner
                            else R.string.transfer_receive_only_short,
                        ).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected != null) VitalCyan else TextMutedOnDark,
                        letterSpacing = 0.8.sp,
                    )
                    Text(
                        text = selected?.nickname?.takeIf(String::isNotBlank)
                            ?: selected?.speciesName
                            ?: stringResource(R.string.transfer_no_partner_short),
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimaryOnDark,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = when {
                            busy -> stringResource(R.string.transfer_busy)
                            selected != null -> selected.speciesName
                                ?: stringResource(R.string.transfer_choose_partner_hint)
                            else -> stringResource(R.string.transfer_choose_partner_hint)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondaryOnDark,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TextButton(
                    onClick = { choosing = true },
                    enabled = !busy,
                ) {
                    Text(stringResource(R.string.transfer_change_partner))
                }
            }
            if (needsConversion && outgoing != null) {
                Text(
                    text = stringResource(R.string.transfer_conversion_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondaryOnDark,
                )
                TextButton(
                    onClick = { converting = true },
                    enabled = !busy,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(stringResource(R.string.conversion_title))
                }
            }
        }
        Box(Modifier.weight(1f)) {
            key(selectedId, epoch) {
                ScanScreen(
                    navController,
                    sendId,
                    scanController,
                    launchedFromHomeScreen = true,
                    embedded = true,
                    onOperationActiveChange = { busy = it; onBusy(it) },
                    onFinished = { busy = false; onBusy(false); epoch++ },
                )
            }
        }
    }
    if (choosing) {
        val receiveOnlyOption = StorageCharacterPickerOption(
            id = "receive-only",
            displayName = stringResource(R.string.transfer_receive_only_short),
            isActive = selectedId == null,
            content = { onPick ->
                ReceiveOnlyPickerTile(
                    selected = selectedId == null,
                    onClick = {
                        selectedId = null
                        choosing = false
                    },
                )
            },
        )
        StorageCharacterPickerDialog(
            characters = eligible,
            title = stringResource(R.string.transfer_choose_outgoing),
            emptyMessage = stringResource(R.string.transfer_no_outgoing),
            supplementalOptions = listOf(receiveOnlyOption),
            onDismiss = { choosing = false },
            onCharacterSelected = { id ->
                selectedId = id
                choosing = false
            },
            onSupplementalSelected = {
                selectedId = null
                choosing = false
            },
        )
    }
    if (converting && selectedId != null) BraceletConversionDialog(requireNotNull(selectedId),
        onDismiss = { converting = false }, onConverted = { converting = false; epoch++ })
}

@Composable
private fun ReceiveOnlyPickerTile(selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        Surface(
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
            shape = RectangleShape,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clickable(onClick = onClick),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(R.drawable.vb_lab_scan_icon),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().padding(8.dp).rotate(90f),
                    contentScale = ContentScale.Fit,
                    colorFilter = ColorFilter.tint(VitalCyan, BlendMode.SrcIn),
                )
            }
        }
        Text(
            text = stringResource(R.string.transfer_receive_only_short),
            style = MaterialTheme.typography.labelMedium,
            color = TextPrimaryOnDark,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun PartnerAvatar(character: CharacterDtos.CharacterWithSprites?, modifier: Modifier = Modifier) {
    val bitmap = remember(character?.id, character?.spriteIdle) {
        character?.let {
            runCatching {
                BitmapData(it.spriteIdle, it.spriteWidth, it.spriteHeight).getBitmap().asImageBitmap()
            }.getOrNull()
        }
    }
    Box(
        modifier = modifier.background(SurfaceElevatedPurple, CutCornerShape(7.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = character?.nickname ?: character?.speciesName,
                modifier = Modifier.fillMaxSize().padding(4.dp),
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.None,
            )
        } else {
            Image(
                painter = painterResource(R.drawable.vb_lab_scan_icon),
                contentDescription = null,
                modifier = Modifier.size(34.dp).rotate(90f),
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(VitalCyan, BlendMode.SrcIn),
            )
        }
    }
}

