package com.tamerslab.storage.ui

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.tamerslab.components.showAppFeedback
import com.tamerslab.farm.FarmWorldPreferences
import com.tamerslab.farm.farmWorldStore
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.tamerslab.R
import com.tamerslab.components.CharacterEntry
import com.tamerslab.components.CyberEmptyState
import com.tamerslab.components.rememberCollectionLoad
import com.tamerslab.components.CollectionStatePanel
import com.tamerslab.components.CollectionRefreshNotice
import com.tamerslab.components.TopBanner
import com.tamerslab.components.motionEnabled
import com.tamerslab.components.VitalButton
import com.tamerslab.di.TamersLabApplication
import com.tamerslab.dtos.CharacterDtos
import com.tamerslab.navigation.AppRoute
import com.tamerslab.navigation.NavigationItems
import com.tamerslab.adventure.ui.AdventureScreenControllerImpl
import com.tamerslab.storage.data.StorageRepository
import com.tamerslab.transfer.data.gameSave.AwayNativeRecord
import com.tamerslab.ui.theme.TextPrimaryOnDark
import com.tamerslab.ui.theme.VitalCyan
import com.tamerslab.utils.BitmapData
import com.tamerslab.utils.DeviceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.withContext
import kotlin.math.abs

@Composable
fun StorageScreen(
    navController: NavController,
    storageScreenController: StorageScreenControllerImpl,
    adventureScreenController: AdventureScreenControllerImpl
) {
    val application = LocalContext.current.applicationContext as TamersLabApplication
    val storageRepository = remember { StorageRepository(application.container.db) }
    val collection = rememberCollectionLoad(storageRepository) { storageRepository.getAllCharacters() }
    val characterList = collection.data.orEmpty()
    val nativeArchives by application.container.gameImportAcquisition.archives.collectAsStateWithLifecycle(initialValue = emptyList())
    val awayRepository = remember(application) { application.container.storageAway }
    val awayCharacters by remember(awayRepository) { awayRepository.observeAwayCharacters() }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val linkedSources by application.container.linkedGameSaves.sources.collectAsStateWithLifecycle(initialValue = emptyList())
    val awayCharacterIds = remember(awayCharacters) { awayCharacters.map { it.characterId }.toSet() }
    val awaySourceIds = remember(awayCharacters) { awayCharacters.associate { it.characterId to it.sourceId } }
    val sourceLabels = remember(linkedSources) { linkedSources.associate { it.source.sourceId to it.source.label } }
    val farmPreferences = remember(application) { FarmWorldPreferences(application.farmWorldStore) }
    val fallbackName = stringResource(R.string.widget_digimon_label)

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            storageRepository.ensureAllPersonalities()
        }
    }

    // Arriving with a pending farm swap: say it once through the shared feedback line (no card).
    val swapContext = LocalContext.current
    val pendingSwapTarget = FarmSwapRequest.target
    LaunchedEffect(pendingSwapTarget) {
        val target = pendingSwapTarget ?: return@LaunchedEffect
        swapContext.showAppFeedback(
            R.string.farm_swap_pick_body,
            target.name,
        )
    }

    // Leaving Storage abandons a pending farm swap, so a later visit behaves normally again.
    DisposableEffect(Unit) {
        onDispose { FarmSwapRequest.clear() }
    }

    var selectedCharacter by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedNativeSnapshot by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedAwayRecord by remember { mutableStateOf<AwayNativeRecord?>(null) }
    val participation = com.tamerslab.components.rememberPartnerParticipation()
    val scope = rememberCoroutineScope()
    val debugSettings = com.tamerslab.components.rememberDebugSettings()
    var showDebugAdd by rememberSaveable { mutableStateOf(false) }
    var debugCandidates by remember { mutableStateOf<List<com.tamerslab.dtos.DebugSpawnCharacter>?>(null) }
    var debugCandidatesFailed by remember { mutableStateOf(false) }
    var debugPickerAttempt by remember { mutableIntStateOf(0) }
    var debugAddInProgress by remember { mutableStateOf(false) }
    LaunchedEffect(showDebugAdd, debugPickerAttempt, debugSettings.enabled) {
        if (!showDebugAdd || !debugSettings.enabled) return@LaunchedEffect
        debugCandidates = null
        debugCandidatesFailed = false
        try {
            val repository = com.tamerslab.storage.data.DebugStorageRepository(
                application.container.db,
                debugEnabled = { application.container.debugSettingsRepository.enabled })
            debugCandidates = withContext(Dispatchers.IO) { repository.candidates() }
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { debugCandidatesFailed = true }
    }
    var showScanCollection by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(StorageFilter.ALL) }
    var sort by rememberSaveable { mutableStateOf(StorageSort.RECENT) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    val awayNative by produceState<List<AwayNativeRecord>>(emptyList(), filter) {
        if (filter != StorageFilter.AWAY) return@produceState
        awayRepository.nativeRecords.collect { value = it }
    }
    val visibleNativeArchives = nativeArchives.filter { archive ->
        archive.characterId == null && filter == StorageFilter.ALL &&
            (query.isBlank() || listOfNotNull(archive.nickname, archive.record?.storedName, archive.snapshot.sourceLabel)
                .any { it.contains(query.trim(), ignoreCase = true) })
    }
    val visibleAwayNative = remember(awayNative, query, filter, sort) {
        if (filter != StorageFilter.AWAY) emptyList()
        else awayNative
            .filter { record ->
                query.isBlank() || listOf(record.name, record.sourceLabel, record.gameId)
                    .any { it.contains(query.trim(), ignoreCase = true) }
            }
            .let { records ->
                if (sort == StorageSort.NAME) records.sortedBy { it.name.lowercase() } else records
            }
    }
    val gridState = rememberLazyGridState()
    val allowMotion = motionEnabled()
    var toolbarVisible by rememberSaveable { mutableStateOf(true) }
    var lastScrollPosition by remember { mutableIntStateOf(0) }
    var downwardScrollDistance by remember { mutableIntStateOf(0) }

    LaunchedEffect(gridState) {
        snapshotFlow {
            val layoutInfo = gridState.layoutInfo
            val position = gridState.firstVisibleItemIndex * 10_000 + gridState.firstVisibleItemScrollOffset
            position to (layoutInfo.totalItemsCount > layoutInfo.visibleItemsInfo.size)
        }.collect { (position, hasOverflow) ->
            if (!hasOverflow) {
                toolbarVisible = true
                downwardScrollDistance = 0
                lastScrollPosition = position
                return@collect
            }
            val delta = position - lastScrollPosition
            if (delta > 0) {
                downwardScrollDistance += delta
                if (downwardScrollDistance >= 96) {
                    toolbarVisible = false
                }
            } else if (delta < 0) {
                downwardScrollDistance = 0
                if (abs(delta) >= 12) {
                    toolbarVisible = true
                }
            }
            if (position == 0) {
                toolbarVisible = true
                downwardScrollDistance = 0
            }
            lastScrollPosition = position
        }
    }

    LaunchedEffect(query, filter, sort) {
        gridState.scrollToItem(0)
        toolbarVisible = true
        downwardScrollDistance = 0
        lastScrollPosition = 0
    }

    val visibleCharacters = remember(characterList, query, filter, sort, fallbackName, awayCharacterIds) {
        val normalizedQuery = query.trim().lowercase()
        characterList.asSequence()
            .filter { character ->
                val away = character.id in awayCharacterIds
                if (filter == StorageFilter.AWAY) away
                else !away && when (filter) {
                    StorageFilter.ALL -> true
                    StorageFilter.FAVORITES -> character.isFavorite
                    StorageFilter.ACTIVE -> character.active
                    StorageFilter.VB -> character.characterType == DeviceType.VBDevice
                    StorageFilter.BE -> character.characterType == DeviceType.BEDevice
                    StorageFilter.AWAY -> false
                }
            }
            .filter { character ->
                normalizedQuery.isBlank() || listOf(
                    character.displayName(fallbackName),
                    character.attribute.name,
                    character.characterType.name,
                    character.stage.toString()
                ).any { it.lowercase().contains(normalizedQuery) }
            }
            .let { characters ->
                when (sort) {
                    StorageSort.RECENT -> characters.sortedByDescending { it.id }
                    StorageSort.NAME -> characters.sortedBy { it.displayName(fallbackName).lowercase() }
                    StorageSort.VITALS -> characters.sortedByDescending { it.vitalPoints }
                    StorageSort.STAGE -> characters.sortedWith(
                        compareByDescending<CharacterDtos.CharacterWithSprites> { it.stage }
                            .thenBy { it.displayName(fallbackName).lowercase() }
                    )
                }
            }
            .toList()
    }

    Scaffold(
        topBar = {
            TopBanner(
                text = stringResource(R.string.storage_my_characters_title),
                onScanDataClick = { showScanCollection = true },
                onAdventureClick = { navController.navigate(AppRoute.Adventure) }
            )
        }
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = contentPadding.calculateTopPadding())
        ) {
            AnimatedVisibility(
                visible = toolbarVisible,
                enter = if (allowMotion) expandVertically() + fadeIn() else expandVertically(),
                exit = if (allowMotion) shrinkVertically() + fadeOut() else shrinkVertically(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.storage_search_label)) },
                        placeholder = { Text(stringResource(R.string.storage_search_hint)) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = if (query.isNotEmpty()) {
                            {
                                IconButton(onClick = { query = "" }) {
                                    Icon(
                                        Icons.Default.Clear,
                                        contentDescription = stringResource(R.string.storage_clear_search)
                                    )
                                }
                            }
                        } else null,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        shape = CutCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp)
                    ) {
                        StorageFilter.entries.forEach { option ->
                            FilterChip(
                                selected = filter == option,
                                onClick = { filter = option },
                                label = { Text(stringResource(option.label)) },
                                shape = CutCornerShape(6.dp)
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.storage_result_count, visibleCharacters.size + visibleNativeArchives.size + visibleAwayNative.size),
                            style = MaterialTheme.typography.labelLarge,
                            color = VitalCyan
                        )
                        Box {
                            VitalButton(
                                onClick = { sortMenuExpanded = true },
                                shape = CutCornerShape(6.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null)
                                Text(
                                    text = stringResource(sort.label),
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = sortMenuExpanded,
                                onDismissRequest = { sortMenuExpanded = false }
                            ) {
                                StorageSort.entries.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(option.label)) },
                                        onClick = {
                                            sort = option
                                            sortMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    if (debugSettings.enabled) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            VitalButton(onClick = { showDebugAdd = true }, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.storage_debug_add_button))
                            }
                            Text(
                                stringResource(R.string.storage_debug_add_hint_all),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            CollectionRefreshNotice(collection, Modifier.padding(horizontal = 12.dp))
            when {
                collection.blocking -> CollectionStatePanel(collection, Modifier.weight(1f))
                characterList.isEmpty() && nativeArchives.isEmpty() -> {
                    CyberEmptyState(message = stringResource(if (nativeArchives.isNotEmpty())
                        R.string.storage_native_archive_empty_playable else R.string.storage_nothing_to_see_here))
                }
                visibleCharacters.isEmpty() && visibleNativeArchives.isEmpty() && visibleAwayNative.isEmpty() -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.storage_no_results),
                            color = TextPrimaryOnDark
                        )
                        TextButton(onClick = {
                            query = ""
                            filter = StorageFilter.ALL
                        }) {
                            Text(stringResource(R.string.storage_clear_filters))
                        }
                    }
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 112.dp),
                        state = gridState,
                        modifier = Modifier.weight(1f)
                    ) {
                        items(visibleAwayNative, key = { "away:${it.sourceId}:${it.recordKey}" }, contentType = { "away-native-character" }) { record ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 4.dp)
                            ) {
                                Box(Modifier.fillMaxWidth()) {
                                    com.tamerslab.transfer.ui.AwayNativeRecordTile(record) {
                                        selectedAwayRecord = record
                                    }
                                }
                                Text(
                                    text = record.name,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextPrimaryOnDark,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedAwayRecord = record }
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                                Text(
                                    text = stringResource(R.string.storage_away_in_source, record.sourceLabel),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = VitalCyan,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                        items(visibleNativeArchives, key = { "native:${it.snapshot.individualId}" }, contentType = { "native-storage-character" }) { archive ->
                            Box(Modifier.fillMaxWidth().padding(4.dp)) {
                                com.tamerslab.transfer.ui.NativeArchiveStorageTile(archive) {
                                    selectedNativeSnapshot = archive.snapshot.id
                                }
                            }
                        }
                        items(
                            items = visibleCharacters,
                            key = { it.id },
                            contentType = { "storage-character" }
                        ) { character ->
                            val usable = participation?.owned(character.id)?.usable != false
                            val openCharacter = {
                                val pendingSwap = FarmSwapRequest.target
                                if (pendingSwap != null) {
                                    // The farm asked for this place: activation is the swap, and the
                                    // visitor goes straight back to see it.
                                    when {
                                        character.id in awayCharacterIds -> Toast.makeText(
                                            application,
                                            application.getString(R.string.transfer_send_away),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        character.isInAdventure -> Toast.makeText(
                                            application,
                                            application.getString(R.string.storage_in_adventure_toast),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        else -> {
                                            val outgoing = pendingSwap
                                            val finishSwap: () -> Unit = {
                                                FarmSwapRequest.clear()
                                                navController.popBackStack()
                                                Unit
                                            }
                                            scope.launch {
                                                // The place is genuinely exchanged: Digifarm membership is
                                                // explicit, so the outgoing partner leaves the island and the
                                                // chosen one takes its slot; only a leader swap also moves
                                                // the active partner.
                                                withContext(Dispatchers.IO) {
                                                    farmPreferences.updateFarmMembers(
                                                        favouriteIds = characterList.filter { it.isFavorite }
                                                            .map { it.id },
                                                        add = character.id,
                                                        remove = outgoing.characterId,
                                                    )
                                                }
                                                if (outgoing.isActivePartner) {
                                                    storageScreenController.setActive(
                                                        characterId = character.id,
                                                        announce = true,
                                                        onCompletion = finishSwap,
                                                        onRejected = {},
                                                    )
                                                } else {
                                                    finishSwap()
                                                }
                                            }
                                            Unit
                                        }
                                    }
                                } else if (character.id in awayCharacterIds) {
                                    Toast.makeText(
                                        application,
                                        application.getString(R.string.transfer_send_away),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else if (!character.isInAdventure) {
                                    selectedCharacter = character.id
                                } else {
                                    Toast.makeText(
                                        application,
                                        application.getString(R.string.storage_in_adventure_toast),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    navController.navigate(AppRoute.Adventure)
                                }
                            }
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 4.dp)
                            ) {
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    CharacterEntry(
                                        icon = BitmapData(
                                            bitmap = if (character.active) character.spriteRun1 else character.spriteIdle,
                                            width = character.spriteWidth,
                                            height = character.spriteHeight
                                        ),
                                        idleFrame2 = BitmapData(
                                            bitmap = if (character.active) character.spriteRun2 else character.spriteIdle2,
                                            width = character.spriteWidth,
                                            height = character.spriteHeight
                                        ),
                                        animationKey = character.id,
                                        vitalPoints = character.vitalPoints,
                                        iconDescription = character.displayName(fallbackName),
                                        selected = character.active,
                                        unavailable = !usable,
                                        shape = RectangleShape,
                                        onClick = openCharacter,
                                        cardColors = if (character.active && usable) {
                                            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
                                        } else {
                                            CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                            )
                                        }
                                    )

                                    if (character.isFavorite) {
                                        Icon(
                                            imageVector = Icons.Filled.Star,
                                            contentDescription = stringResource(R.string.storage_favorite_indicator),
                                            tint = (if (usable) VitalCyan else com.tamerslab.ui.theme.InactiveOutline).copy(alpha = 0.58f),
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(14.dp)
                                                .size(18.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = character.displayName(fallbackName),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextPrimaryOnDark,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(onClick = openCharacter)
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                                if (character.id in awayCharacterIds) {
                                    val sourceLabel = awaySourceIds[character.id]?.let(sourceLabels::get)
                                    Text(
                                        text = if (sourceLabel != null)
                                            stringResource(R.string.storage_away_in_source, sourceLabel)
                                        else stringResource(R.string.storage_away_unknown_source),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = VitalCyan,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showScanCollection) StorageScanCollectionDialog(application.container.db,
            onClose = { showScanCollection = false })
        nativeArchives.firstOrNull { it.snapshot.id == selectedNativeSnapshot }?.let { archive ->
            com.tamerslab.transfer.ui.NativeArchiveStorageInformation(archive, onMoveToGame = {
                selectedNativeSnapshot = null
                navController.navigate(com.tamerslab.navigation.TransferNavigation.route(
                    com.tamerslab.navigation.TransferSection.SEND,
                    individualId = archive.snapshot.individualId))
            }, onDismiss = { selectedNativeSnapshot = null })
        }

        selectedAwayRecord?.let { record ->
            com.tamerslab.transfer.ui.AwayNativeRecordInformation(
                record, onDismiss = { selectedAwayRecord = null })
        }

        selectedCharacter?.let { characterId ->
            StorageDialog(
                characterId = characterId,
                onDismissRequest = { selectedCharacter = null },
                onToggleFavorite = { isFavorite ->
                    storageScreenController.setFavorite(
                        characterId = characterId,
                        isFavorite = isFavorite
                    )
                },
                onClickSetActive = {
                    storageScreenController.setActive(
                        characterId = characterId,
                        onCompletion = {
                            selectedCharacter = null
                            navController.navigate(AppRoute.Home)
                        }
                    )
                },
                onTransferOptions = {
                    selectedCharacter = null
                    navController.navigate(com.tamerslab.navigation.TransferNavigation.route(
                        com.tamerslab.navigation.TransferSection.SEND, characterId))
                },
                onClickSendToAdventure = { time ->
                    adventureScreenController.sendCharacterToAdventure(
                        characterId = characterId,
                        timeInMinutes = time
                    )
                    selectedCharacter = null
                },
                onClickDelete = {
                    storageScreenController.deleteCharacter(
                        characterId = characterId,
                        onCompletion = { Log.d("StorageScreen", "Character deleted") }
                    )
                    selectedCharacter = null
                },
                onClickChat = {
                    selectedCharacter = null
                    navController.navigate(
                        AppRoute.Chat(characterId)
                    )
                },
                onClickTechniques = {
                    selectedCharacter = null
                    navController.navigate(
                        AppRoute.TechniqueLoadout(characterId)
                    )
                }
            )
        }

        if (showDebugAdd && debugSettings.enabled) {
            com.tamerslab.world.ui.DebugSpawnPickerDialog(
                characters = debugCandidates,
                failed = debugCandidatesFailed,
                enabled = !debugAddInProgress,
                onRetry = { debugPickerAttempt++ },
                onSelect = { id ->
                    if (debugAddInProgress) return@DebugSpawnPickerDialog
                    debugAddInProgress = true
                    scope.launch(Dispatchers.IO) {
                        try {
                            val repository = com.tamerslab.storage.data.DebugStorageRepository(
                                application.container.db,
                                debugEnabled = { application.container.debugSettingsRepository.enabled })
                            repository.addToStorage(id)
                            withContext(Dispatchers.Main) {
                                android.widget.Toast.makeText(
                                    application,
                                    application.getString(R.string.storage_debug_add_success),
                                    android.widget.Toast.LENGTH_SHORT).show()
                                showDebugAdd = false
                            }
                        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                        catch (_: Exception) {
                            withContext(Dispatchers.Main) {
                                android.widget.Toast.makeText(
                                    application,
                                    application.getString(R.string.storage_debug_add_failure),
                                    android.widget.Toast.LENGTH_SHORT).show()
                            }
                        } finally {
                            debugAddInProgress = false
                        }
                    }
                },
                onDismiss = { showDebugAdd = false })
        }
    }
}

