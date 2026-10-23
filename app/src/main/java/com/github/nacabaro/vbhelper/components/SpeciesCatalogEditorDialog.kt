package com.github.nacabaro.vbhelper.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.di.VBHelper
import com.github.nacabaro.vbhelper.species.SpeciesCustomizationRepository
import com.github.nacabaro.vbhelper.species.SpeciesEntryDto
import com.github.nacabaro.vbhelper.ui.SpritePreviewPolicy
import com.github.nacabaro.vbhelper.utils.BitmapData
import com.github.nacabaro.vbhelper.utils.createARGBIntArray
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun SpeciesCatalogEditorDialog(originalName: String?, onDismiss: () -> Unit, onSaved: (SpeciesEntryDto) -> Unit) {
    val app = LocalContext.current.applicationContext as VBHelper
    val repository = app.container.speciesRepository
    val scope = rememberCoroutineScope()
    var initialized by rememberSaveable(originalName) { mutableStateOf(originalName == null) }
    var isCopy by rememberSaveable(originalName) { mutableStateOf(false) }
    var name by rememberSaveable(originalName) { mutableStateOf(originalName.orEmpty()) }
    var level by rememberSaveable(originalName) { mutableStateOf("") }
    var type by rememberSaveable(originalName) { mutableStateOf("") }
    var description by rememberSaveable(originalName) { mutableStateOf("") }
    var moves by rememberSaveable(originalName) { mutableStateOf("") }
    var chosenSprite by rememberSaveable(originalName) { mutableStateOf<Int?>(null) }
    var canonicalSpeciesId by rememberSaveable(originalName) { mutableStateOf<String?>(null) }
    var showSprites by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var loadFailed by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    val previewKey = SpeciesCustomizationRepository.key(originalName ?: name)
    val previewSprite by produceState(chosenSprite, chosenSprite, previewKey) {
        if (chosenSprite == null && previewKey.isNotEmpty()) {
            delay(200)
            value = withContext(Dispatchers.IO) { app.container.db.speciesCustomizationDao()
                .getSpriteChoices(previewKey, true, "", 0, 1).firstOrNull()?.spriteId }
        } else value = chosenSprite
    }
    LaunchedEffect(originalName, attempt) {
        if (initialized || originalName == null) return@LaunchedEffect
        try {
            val loaded = withContext(Dispatchers.IO) {
                val key = SpeciesCustomizationRepository.key(originalName)
                val entry = repository.getAllSpeciesEntries().firstOrNull { SpeciesCustomizationRepository.key(it.name) == key }
                    ?: SpeciesEntryDto(originalName)
                Triple(entry, app.container.db.speciesCustomizationDao().getSpriteId(key),
                    app.container.db.speciesCustomizationDao().getEntry(key) == null)
            }
            name = loaded.first.name
            level = loaded.first.level.orEmpty()
            type = loaded.first.type.orEmpty()
            description = loaded.first.profile.orEmpty()
            moves = loaded.first.specialMoves.joinToString("\n")
            chosenSprite = loaded.second
            canonicalSpeciesId = loaded.first.speciesId
            // A catalog species without a user-owned record is duplicated, never edited in place.
            isCopy = loaded.third
            initialized = true
            loadFailed = false
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            loadFailed = true
        }
    }
    Dialog(onDismissRequest = { if (!saving) onDismiss() }, properties = DialogProperties(
        usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnClickOutside = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.exclude(WindowInsets.ime)).imePadding()) {
                TopBanner(stringResource(if (originalName == null || isCopy) R.string.species_add else R.string.species_edit),
                    onBackClick = { if (!saving) onDismiss() })
                val copyNameClash = isCopy && originalName != null &&
                    SpeciesCustomizationRepository.key(name) == SpeciesCustomizationRepository.key(originalName)
                if (!initialized) {
                    CollectionStatePanel(CollectionLoad<String>(null, loadFailed, { attempt++ }), Modifier.weight(1f))
                } else {
                    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(if (isCopy) R.string.species_copy_note else R.string.species_template_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(name, { name = it.take(160) }, label = { Text(stringResource(R.string.ui_species_name_required)) },
                            singleLine = true, enabled = !saving, modifier = Modifier.fillMaxWidth())
                        if (copyNameClash) Text(stringResource(R.string.species_copy_name_hint),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        OutlinedTextField(level, { level = it.take(100) }, label = { Text(stringResource(R.string.ui_optional_level)) },
                            singleLine = true, enabled = !saving, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(type, { type = it.take(100) }, label = { Text(stringResource(R.string.ui_optional_type)) },
                            singleLine = true, enabled = !saving, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(moves, { moves = it }, label = { Text(stringResource(R.string.ui_special_moves)) },
                            supportingText = { Text(stringResource(R.string.species_moves_hint)) },
                            enabled = !saving, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 5)
                        OutlinedTextField(description, { description = it.take(20_000) }, label = { Text(stringResource(R.string.ui_optional_profile)) },
                            enabled = !saving, modifier = Modifier.fillMaxWidth(), minLines = 4, maxLines = 10)
                        VitalSectionHeader(stringResource(R.string.species_sprite_title))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            LoadedSpritePortrait(previewSprite, Modifier.size(88.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(stringResource(R.string.species_sprite_hint), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                VitalButton(onClick = { showSprites = true }, enabled = !saving && name.isNotBlank()) {
                                    Text(stringResource(R.string.species_choose_sprite))
                                }
                            }
                        }
                        if (chosenSprite != null) TextButton(onClick = { chosenSprite = null }, enabled = !saving) {
                            Text(stringResource(R.string.species_original_sprites))
                        }
                        if (failed) Text(stringResource(R.string.species_save_failed), color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium)
                    }
                    VitalButton(style = VitalButtonStyle.PRIMARY, modifier = Modifier.fillMaxWidth().padding(16.dp),
                        enabled = !saving && SpeciesCustomizationRepository.key(name).isNotEmpty() && !copyNameClash, onClick = {
                            saving = true
                            failed = false
                            val entry = SpeciesEntryDto(name, level, type, description,
                                moves.split('\n', ',').map(String::trim).filter(String::isNotEmpty))
                            scope.launch {
                                try {
                                    val saved = repository.saveCatalogEntry(originalName, entry, chosenSprite)
                                    com.github.nacabaro.vbhelper.widget.DigimonWidgetProvider.notifyActiveCharacterChanged(app)
                                    onSaved(saved)
                                }
                                catch (failure: Exception) {
                                    if (failure is CancellationException) throw failure
                                    failed = true
                                } finally { saving = false }
                            }
                        }) {
                        if (saving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.ui_save))
                    }
                }
            }
        }
    }
    if (showSprites) SpeciesSpritePickerDialog(
        SpeciesCustomizationRepository.key(originalName ?: name), chosenSprite,
        onDismiss = { showSprites = false }, onSelected = { chosenSprite = it; showSprites = false },
        canonicalSpeciesId = canonicalSpeciesId)
}

/** Raw loaded artwork preview: the chooser must show each alternative rather than normalize its own candidates. */
@Composable
internal fun LoadedSpritePortrait(spriteId: Int?, modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as? VBHelper
    val image by key(spriteId) {
        produceState<androidx.compose.ui.graphics.ImageBitmap?>(null) {
            value = withContext(Dispatchers.IO) {
                if (spriteId == null || app == null) return@withContext null
                app.container.db.spriteDao().getById(spriteId)?.takeIf {
                    SpritePreviewPolicy.validFrame(it.width, it.height, it.spriteIdle1.size)
                }?.let {
                    android.graphics.Bitmap.createBitmap(BitmapData(it.spriteIdle1, it.width, it.height).createARGBIntArray(),
                        it.width, it.height, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
                }
            }
        }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        image?.let { Image(it, null, Modifier.fillMaxSize(), filterQuality = FilterQuality.None) }
            ?: Icon(Icons.Outlined.Pets, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
