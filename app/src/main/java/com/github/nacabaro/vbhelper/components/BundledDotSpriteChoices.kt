package com.github.nacabaro.vbhelper.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.species.BundledDotSpriteLibrary
import com.github.nacabaro.vbhelper.species.CanonicalSpeciesCatalog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** A source tab inside the existing appearance chooser; all alternatives are shipped offline. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun BundledDotSpriteChoices(library: BundledDotSpriteLibrary?, speciesKey: String,
    query: String, selectedSprite: Int?, onSelected: (Int) -> Unit, modifier: Modifier = Modifier) {
    var catalog by remember(library) { mutableStateOf<CanonicalSpeciesCatalog?>(null) }
    var loading by remember(library) { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    var matchingOnly by rememberSaveable(speciesKey) { mutableStateOf(true) }
    var limit by remember(speciesKey, query, matchingOnly) { mutableIntStateOf(24) }
    var installing by remember { mutableStateOf<String?>(null) }
    var installFailed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(library, attempt) {
        loading = true
        failed = false
        try { catalog = requireNotNull(library).catalog() }
        catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            failed = true
        } finally { loading = false }
    }
    val rows = remember(catalog, speciesKey, query, matchingOnly) {
        catalog?.searchSets(speciesKey, query, matchingOnly).orEmpty()
    }
    val hasMatchingSets = remember(catalog, speciesKey) { catalog?.setsForSpecies(speciesKey).orEmpty().isNotEmpty() }
    Column(modifier.fillMaxWidth()) {
        FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = matchingOnly, onClick = { matchingOnly = true }, label = { Text(stringResource(R.string.dot_matching)) })
            FilterChip(selected = !matchingOnly, onClick = { matchingOnly = false }, label = { Text(stringResource(R.string.dot_all)) })
        }
        Text(stringResource(R.string.dot_offline_hint), Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (loading && catalog == null) CollectionStatePanel(CollectionLoad<String>(null, false, {}), Modifier.weight(1f))
        else if (failed && catalog == null) CollectionStatePanel(CollectionLoad<String>(null, true, { attempt++ }), Modifier.weight(1f))
        else LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
            if (rows.isEmpty()) item {
                Text(stringResource(if (matchingOnly && !hasMatchingSets) R.string.dot_no_matching else R.string.dot_no_results),
                    style = MaterialTheme.typography.bodyMedium)
            }
            items(rows.take(limit), key = { it.id }) { row ->
                Row(Modifier.fillMaxWidth().selectable(selected = row.spriteId == selectedSprite,
                    enabled = installing == null, role = Role.RadioButton, onClick = {
                        installing = row.id
                        installFailed = false
                        scope.launch {
                            try { onSelected(requireNotNull(library).install(row.id)) }
                            catch (failure: Exception) {
                                if (failure is CancellationException) throw failure
                                installFailed = true
                            } finally { installing = null }
                        }
                    }).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DotSpritePortrait(library, row.id, Modifier.size(72.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(row.name, style = MaterialTheme.typography.titleSmall)
                        Text(listOf(row.author, row.source).filter(String::isNotBlank).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (installing == row.id) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    else RadioButton(row.spriteId == selectedSprite, onClick = null, enabled = installing == null)
                }
                HorizontalDivider()
            }
            if (rows.size > limit) item {
                VitalButton(onClick = { limit += 24 }, enabled = installing == null,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text(stringResource(R.string.species_load_more)) }
            }
        }
        if (installFailed) Text(stringResource(R.string.dot_selection_failed), Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun DotSpritePortrait(library: BundledDotSpriteLibrary?, setId: String, modifier: Modifier) {
    val image by produceState<ImageBitmap?>(null, library, setId) {
        try { value = library?.portrait(setId)?.asImageBitmap() }
        catch (failure: Exception) { if (failure is CancellationException) throw failure }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        image?.let { Image(it, null, Modifier.fillMaxSize(), filterQuality = FilterQuality.None) }
            ?: Icon(Icons.Outlined.Pets, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
