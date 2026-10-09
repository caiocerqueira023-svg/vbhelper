package com.github.nacabaro.vbhelper.screens.worldScreen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.dtos.DebugSpawnCharacter
import com.github.nacabaro.vbhelper.ui.theme.SurfaceStroke
import com.github.nacabaro.vbhelper.ui.theme.TextMutedOnDark
import com.github.nacabaro.vbhelper.ui.theme.TextPrimaryOnDark
import com.github.nacabaro.vbhelper.utils.getBitmap
import com.github.nacabaro.vbhelper.utils.BitmapData

/** One gesture handler prevents a long press from also triggering the random spawn. */
@Composable
internal fun DebugSpawnButton(enabled: Boolean, loading: Boolean, onRandomSpawn: () -> Unit,
    onChooseSpawn: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.testTag("radar-debug-spawn").combinedClickable(
            enabled = enabled, role = Role.Button,
            onClick = onRandomSpawn,
            onLongClickLabel = stringResource(R.string.ui_world_debug_picker_title),
            onLongClick = onChooseSpawn),
        shape = CutCornerShape(8.dp),
        border = BorderStroke(1.dp, SurfaceStroke),
        color = androidx.compose.ui.graphics.Color.Transparent,
        contentColor = if (enabled) TextPrimaryOnDark else TextMutedOnDark
    ) {
        Row(Modifier.heightIn(min = 48.dp).padding(ButtonDefaults.ContentPadding),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(if (loading) R.string.ui_world_debug_spawn_loading else R.string.ui_world_debug_spawn_button))
        }
    }
}

@Composable
internal fun DebugSpawnPickerDialog(characters: List<DebugSpawnCharacter>?, failed: Boolean,
    enabled: Boolean, onRetry: () -> Unit, onSelect: (Long) -> Unit, onDismiss: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(characters, query) {
        val term = query.trim()
        characters?.filter {
            term.isEmpty() || it.speciesName.orEmpty().contains(term, ignoreCase = true) ||
                it.cardName.contains(term, ignoreCase = true) || it.charaIndex.toString().contains(term)
        }.orEmpty()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ui_world_debug_picker_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true,
                    label = { Text(stringResource(R.string.ui_world_debug_picker_search)) },
                    modifier = Modifier.fillMaxWidth())
                when {
                    failed -> {
                        Text(stringResource(R.string.ui_world_debug_picker_failure), color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = onRetry) { Text(stringResource(R.string.ui_world_debug_picker_retry)) }
                    }
                    characters == null -> Text(stringResource(R.string.ui_world_debug_picker_loading))
                    characters.isEmpty() -> Text(stringResource(R.string.ui_world_debug_picker_empty))
                    filtered.isEmpty() -> Text(stringResource(R.string.ui_world_debug_picker_no_results))
                    else -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                        items(filtered, key = { it.id }) { character ->
                            val name = character.speciesName?.takeIf { it.isNotBlank() }
                                ?: stringResource(R.string.ui_world_debug_picker_fallback_name, character.charaIndex)
                            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp)
                                .testTag("debug-spawn-character-${character.id}")
                                .combinedClickable(enabled = enabled, role = Role.Button, onClick = { onSelect(character.id) })
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                val bitmap = remember(character.id, character.spriteIdle) {
                                    if (character.spriteWidth > 0 && character.spriteHeight > 0)
                                        runCatching { BitmapData(character.spriteIdle, character.spriteWidth, character.spriteHeight).getBitmap().asImageBitmap() }.getOrNull()
                                    else null
                                }
                                if (bitmap != null) Image(bitmap, contentDescription = null,
                                    modifier = Modifier.size(48.dp), filterQuality = FilterQuality.None)
                                Column(Modifier.weight(1f)) {
                                    Text(name, style = MaterialTheme.typography.bodyLarge)
                                    Text(character.cardName, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_cancel)) } }
    )
}
