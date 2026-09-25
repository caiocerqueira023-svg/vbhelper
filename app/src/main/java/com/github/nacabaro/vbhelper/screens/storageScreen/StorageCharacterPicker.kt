package com.github.nacabaro.vbhelper.screens.storageScreen

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.components.CharacterEntry
import com.github.nacabaro.vbhelper.components.CyberEmptyState
import com.github.nacabaro.vbhelper.components.TopBanner
import com.github.nacabaro.vbhelper.components.VitalButton
import com.github.nacabaro.vbhelper.components.motionEnabled
import com.github.nacabaro.vbhelper.dtos.CharacterDtos
import com.github.nacabaro.vbhelper.ui.theme.TextPrimaryOnDark
import com.github.nacabaro.vbhelper.ui.theme.VitalCyan
import com.github.nacabaro.vbhelper.utils.BitmapData
import com.github.nacabaro.vbhelper.utils.DeviceType
import kotlin.math.abs

internal enum class StorageFilter(@StringRes val label: Int) {
    ALL(R.string.storage_filter_all),
    FAVORITES(R.string.storage_filter_favorites),
    ACTIVE(R.string.storage_filter_active),
    VB(R.string.storage_filter_vb),
    BE(R.string.storage_filter_be)
}

internal enum class StorageSort(@StringRes val label: Int) {
    RECENT(R.string.storage_sort_recent),
    NAME(R.string.storage_sort_name),
    VITALS(R.string.storage_sort_vitals),
    STAGE(R.string.storage_sort_stage)
}

object StorageCharacterPickerTags {
    const val Screen = "storage-character-picker-screen"
}

/** A non-Storage participant rendered inside the same Storage selection grid. */
data class StorageCharacterPickerOption(
    val id: String,
    val displayName: String,
    val searchableTerms: List<String> = emptyList(),
    val isFavorite: Boolean = false,
    val isActive: Boolean = false,
    val deviceType: DeviceType? = null,
    val content: @Composable (onClick: () -> Unit) -> Unit
)

internal fun CharacterDtos.CharacterWithSprites.displayName(fallback: String): String =
    nickname?.takeIf { it.isNotBlank() }
        ?: speciesName?.takeIf { it.isNotBlank() }
        ?: "$fallback #$id"

/**
 * Full-screen Storage-style selector used whenever an action needs a Digimon
 * chosen from storage. The source list may already be scoped by the action,
 * while the complete Storage search, filter, and sort controls remain intact.
 */
@Composable
fun StorageCharacterPickerScreen(
    characters: List<CharacterDtos.CharacterWithSprites>,
    title: String,
    emptyMessage: String,
    isLoading: Boolean = false,
    supplementalOptions: List<StorageCharacterPickerOption> = emptyList(),
    onBack: () -> Unit,
    onCharacterSelected: (Long) -> Unit,
    onSupplementalSelected: (String) -> Unit = {}
) {
    val fallbackName = stringResource(R.string.widget_digimon_label)
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
                if (downwardScrollDistance >= 96) toolbarVisible = false
            } else if (delta < 0) {
                downwardScrollDistance = 0
                if (abs(delta) >= 12) toolbarVisible = true
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

    val visibleCharacters = remember(characters, query, filter, sort, fallbackName) {
        val normalizedQuery = query.trim().lowercase()
        characters.asSequence()
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
            .let { filtered ->
                when (sort) {
                    StorageSort.RECENT -> filtered.sortedByDescending { it.id }
                    StorageSort.NAME -> filtered.sortedBy { it.displayName(fallbackName).lowercase() }
                    StorageSort.VITALS -> filtered.sortedByDescending { it.vitalPoints }
                    StorageSort.STAGE -> filtered.sortedWith(
                        compareByDescending<CharacterDtos.CharacterWithSprites> { it.stage }
                            .thenBy { it.displayName(fallbackName).lowercase() }
                    )
                }
            }
            .toList()
    }
    val visibleSupplementalOptions = remember(supplementalOptions, query, filter, sort) {
        val normalizedQuery = query.trim().lowercase()
        supplementalOptions.asSequence()
            .filter { option ->
                when (filter) {
                    StorageFilter.ALL -> true
                    StorageFilter.FAVORITES -> option.isFavorite
                    StorageFilter.ACTIVE -> option.isActive
                    StorageFilter.VB -> option.deviceType == DeviceType.VBDevice
                    StorageFilter.BE -> option.deviceType == DeviceType.BEDevice
                }
            }
            .filter { option ->
                normalizedQuery.isBlank() ||
                    (listOf(option.displayName) + option.searchableTerms)
                        .any { it.lowercase().contains(normalizedQuery) }
            }
            .let { filtered ->
                if (sort == StorageSort.NAME) filtered.sortedBy { it.displayName.lowercase() }
                else filtered
            }
            .toList()
    }
    val visibleOptionCount = visibleCharacters.size + visibleSupplementalOptions.size

    Scaffold(
        topBar = { TopBanner(text = title, onBackClick = onBack) }
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = contentPadding.calculateTopPadding())
                .testTag(StorageCharacterPickerTags.Screen)
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
                            text = stringResource(R.string.storage_result_count, visibleOptionCount),
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
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = VitalCyan)
                    }
                }
                characters.isEmpty() && supplementalOptions.isEmpty() -> {
                    CyberEmptyState(message = emptyMessage)
                }
                visibleOptionCount == 0 -> {
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
                            contentType = { "storage-picker-character" }
                        ) { character ->
                            val selectCharacter = { onCharacterSelected(character.id) }
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
                                        onClick = selectCharacter,
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
                                        .clickable(onClick = selectCharacter)
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                        items(
                            items = visibleSupplementalOptions,
                            key = { "supplemental-${it.id}" },
                            contentType = { "storage-picker-supplemental" }
                        ) { option ->
                            option.content { onSupplementalSelected(option.id) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StorageCharacterPickerDialog(
    characters: List<CharacterDtos.CharacterWithSprites>,
    title: String,
    emptyMessage: String,
    isLoading: Boolean = false,
    supplementalOptions: List<StorageCharacterPickerOption> = emptyList(),
    onDismiss: () -> Unit,
    onCharacterSelected: (Long) -> Unit,
    onSupplementalSelected: (String) -> Unit = {}
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            StorageCharacterPickerScreen(
                characters = characters,
                title = title,
                emptyMessage = emptyMessage,
                isLoading = isLoading,
                supplementalOptions = supplementalOptions,
                onBack = onDismiss,
                onCharacterSelected = onCharacterSelected,
                onSupplementalSelected = onSupplementalSelected
            )
        }
    }
}
