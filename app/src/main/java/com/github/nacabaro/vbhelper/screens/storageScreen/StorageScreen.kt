package com.github.nacabaro.vbhelper.screens.storageScreen

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.components.CharacterEntry
import com.github.nacabaro.vbhelper.components.CyberEmptyState
import com.github.nacabaro.vbhelper.components.TopBanner
import com.github.nacabaro.vbhelper.components.motionEnabled
import com.github.nacabaro.vbhelper.components.VitalButton
import com.github.nacabaro.vbhelper.di.VBHelper
import com.github.nacabaro.vbhelper.dtos.CharacterDtos
import com.github.nacabaro.vbhelper.navigation.NavigationItems
import com.github.nacabaro.vbhelper.screens.adventureScreen.AdventureScreenControllerImpl
import com.github.nacabaro.vbhelper.source.StorageRepository
import com.github.nacabaro.vbhelper.ui.theme.TextPrimaryOnDark
import com.github.nacabaro.vbhelper.ui.theme.VitalCyan
import com.github.nacabaro.vbhelper.utils.BitmapData
import com.github.nacabaro.vbhelper.utils.DeviceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

@Composable
fun StorageScreen(
    navController: NavController,
    storageScreenController: StorageScreenControllerImpl,
    adventureScreenController: AdventureScreenControllerImpl
) {
    val application = LocalContext.current.applicationContext as VBHelper
    val storageRepository = remember { StorageRepository(application.container.db) }
    val characterList by storageRepository.getAllCharacters().collectAsState(initial = emptyList())
    val farmAssignments by application.container.digifarmRepository.observeAssignments()
        .collectAsState(initial = emptyList())
    val farmByCharacter = remember(farmAssignments) { farmAssignments.associate { it.characterId to it.farmName } }
    val fallbackName = stringResource(R.string.widget_digimon_label)

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            storageRepository.ensureAllPersonalities()
        }
    }

    var selectedCharacter by remember { mutableStateOf<Long?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(StorageFilter.ALL) }
    var sort by rememberSaveable { mutableStateOf(StorageSort.RECENT) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    val allowMotion = motionEnabled()
    var toolbarVisible by rememberSaveable { mutableStateOf(true) }
    var lastScrollPosition by remember { mutableIntStateOf(0) }
    var downwardScrollDistance by remember { mutableIntStateOf(0) }

    LaunchedEffect(gridState) {
        snapshotFlow {
            gridState.firstVisibleItemIndex * 10_000 + gridState.firstVisibleItemScrollOffset
        }.collect { position ->
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

    val visibleCharacters = remember(characterList, query, filter, sort, fallbackName) {
        val normalizedQuery = query.trim().lowercase()
        characterList.asSequence()
            .filter { character ->
                when (filter) {
                    StorageFilter.ALL -> true
                    StorageFilter.FAVORITES -> character.isFavorite
                    StorageFilter.ACTIVE -> character.active
                    StorageFilter.VB -> character.characterType == DeviceType.VBDevice
                    StorageFilter.BE -> character.characterType == DeviceType.BEDevice
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
                onAdventureClick = { navController.navigate(NavigationItems.Adventure.route) }
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
                            text = stringResource(R.string.storage_result_count, visibleCharacters.size),
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
                }
            }

            when {
                characterList.isEmpty() -> {
                    CyberEmptyState(message = stringResource(R.string.storage_nothing_to_see_here))
                }
                visibleCharacters.isEmpty() -> {
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
                        items(
                            items = visibleCharacters,
                            key = { it.id },
                            contentType = { "storage-character" }
                        ) { character ->
                            val openCharacter = {
                                if (!character.isInAdventure) {
                                    selectedCharacter = character.id
                                } else {
                                    Toast.makeText(
                                        application,
                                        application.getString(R.string.storage_in_adventure_toast),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    navController.navigate(NavigationItems.Adventure.route)
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
                                        shape = RectangleShape,
                                        onClick = openCharacter,
                                        cardColors = if (character.active) {
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
                                            tint = VitalCyan.copy(alpha = 0.58f),
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
                                farmByCharacter[character.id]?.let { farmName ->
                                    Text(
                                        text = stringResource(R.string.storage_in_digifarm, farmName),
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
                            navController.navigate(NavigationItems.Home.route)
                        }
                    )
                },
                onSendToBracelet = {
                    navController.navigate(
                        NavigationItems.Scan.route.replace("{characterId}", characterId.toString())
                    )
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
                        NavigationItems.Chat.route.replace("{characterId}", characterId.toString())
                    )
                }
            )
        }
    }
}
