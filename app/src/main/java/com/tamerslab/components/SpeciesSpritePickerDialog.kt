package com.tamerslab.components

import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tamerslab.R
import com.tamerslab.di.TamersLabApplication
import com.tamerslab.domain.species.LoadedSpriteChoice
import com.tamerslab.species.CanonicalSpeciesCatalog
import com.tamerslab.ui.theme.DeepPurpleBg
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun SpeciesSpritePickerDialog(speciesKey: String, selectedSprite: Int?, onDismiss: () -> Unit, onSelected: (Int?) -> Unit,
    canonicalSpeciesId: String? = null) {
    val app = LocalContext.current.applicationContext as TamersLabApplication
    val dao = app.container.db.speciesCustomizationDao()
    var query by rememberSaveable { mutableStateOf("") }
    var matchedOnly by rememberSaveable { mutableStateOf(true) }
    var sourceTab by rememberSaveable { mutableIntStateOf(0) }
    var rows by remember(speciesKey, query, matchedOnly) { mutableStateOf(emptyList<LoadedSpriteChoice>()) }
    var loading by remember(speciesKey, query, matchedOnly) { mutableStateOf(true) }
    var failed by remember(speciesKey, query, matchedOnly) { mutableStateOf(false) }
    var more by remember(speciesKey, query, matchedOnly) { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    var generation by remember { mutableLongStateOf(0L) }
    var paginationJob by remember { mutableStateOf<Job?>(null) }
    var dotCatalog by remember { mutableStateOf<CanonicalSpeciesCatalog?>(null) }
    val scope = rememberCoroutineScope()
    val pageSize = 24
    LaunchedEffect(app.container.dotSpriteLibrary) {
        try { dotCatalog = app.container.dotSpriteLibrary.catalog() }
        catch (failure: Exception) { if (failure is CancellationException) throw failure }
    }
    LaunchedEffect(speciesKey, query, matchedOnly, sourceTab, attempt) {
        paginationJob?.cancel()
        paginationJob = null
        val request = ++generation
        if (sourceTab == 2) { loading = false; return@LaunchedEffect }
        loading = true
        failed = false
        try {
            delay(200)
            val page = withContext(Dispatchers.IO) { dao.getSpriteChoices(speciesKey, matchedOnly, query.trim(), 0, pageSize + 1) }
            if (generation == request) { rows = page.take(pageSize); more = page.size > pageSize }
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            if (generation == request) failed = true
        } finally { if (generation == request) loading = false }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        // Opaque palette page: a transparent dialog surface let the screen below and the window dim
        // show through, which read as the wrong background in the two light palettes.
        Surface(Modifier.fillMaxSize(), color = DeepPurpleBg) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.exclude(WindowInsets.ime)).imePadding()) {
                TopBanner(stringResource(R.string.species_choose_sprite), onBackClick = onDismiss)
                VitalSearchField(query, { query = it }, stringResource(R.string.species_search_sprites), Modifier.padding(horizontal = 16.dp))
                VitalTabs(sourceTab,
                    listOf(stringResource(R.string.dot_assigned_tab), stringResource(R.string.dot_loaded_tab), stringResource(R.string.dot_bundled_tab)),
                    { sourceTab = it; if (it != 2) matchedOnly = it == 0 })
                TextButton(onClick = { onSelected(null) }, modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(stringResource(R.string.species_original_sprites))
                }
                if (sourceTab == 2) BundledDotSpriteChoices(app.container.dotSpriteLibrary, canonicalSpeciesId ?: speciesKey, query,
                    selectedSprite, { onSelected(it) }, Modifier.weight(1f))
                else if (rows.isEmpty() && loading) CollectionStatePanel(CollectionLoad<String>(null, false, {}), Modifier.weight(1f))
                else if (failed && rows.isEmpty()) CollectionStatePanel(CollectionLoad<String>(null, true, { attempt++ }), Modifier.weight(1f))
                else LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(16.dp)) {
                    if (rows.isEmpty()) item { Text(stringResource(R.string.species_no_sprites), style = MaterialTheme.typography.bodyMedium) }
                    items(rows, key = { "${it.spriteId}:${it.cardCharacterId}" }) { row ->
                        val bundled = dotCatalog?.setsBySpriteId?.get(row.spriteId)
                        Row(Modifier.fillMaxWidth().selectable(selected = row.spriteId == selectedSprite,
                            role = Role.RadioButton, onClick = { onSelected(row.spriteId) }).padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            LoadedSpritePortrait(row.spriteId, Modifier.size(72.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(bundled?.name ?: row.speciesName ?: stringResource(R.string.immersion_unidentified_partner), style = MaterialTheme.typography.titleSmall)
                                Text(if (bundled != null) listOf(bundled.author, bundled.source).filter(String::isNotBlank).joinToString(" · ")
                                    else if (row.charaIndex >= 0) stringResource(R.string.species_sprite_details, row.cardName, row.charaIndex + 1, row.spriteId)
                                    else stringResource(R.string.species_sprite_identifier, row.spriteId),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            RadioButton(row.spriteId == selectedSprite, onClick = null)
                        }
                        HorizontalDivider()
                    }
                    if (more) item {
                        VitalButton(enabled = !loading, modifier = Modifier.fillMaxWidth().padding(top = 12.dp), onClick = loadMore@ {
                            if (loading) return@loadMore
                            loading = true
                            val capturedQuery = query
                            val capturedMode = matchedOnly
                            val offset = rows.size
                            val capturedTab = sourceTab
                            val capturedGeneration = generation
                            fun currentRequest() = generation == capturedGeneration && sourceTab == capturedTab &&
                                query == capturedQuery && matchedOnly == capturedMode
                            paginationJob = scope.launch {
                                try {
                                    val page = withContext(Dispatchers.IO) { dao.getSpriteChoices(speciesKey, capturedMode, capturedQuery.trim(), offset, pageSize + 1) }
                                    if (currentRequest()) {
                                        rows = rows + page.take(pageSize)
                                        more = page.size > pageSize
                                    }
                                } catch (failure: Exception) {
                                    if (failure is CancellationException) throw failure
                                    if (currentRequest()) failed = true
                                } finally { if (currentRequest()) loading = false }
                            }
                        }) { Text(stringResource(R.string.species_load_more)) }
                    }
                    if (failed) item { Text(stringResource(R.string.app_load_failed), color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}
