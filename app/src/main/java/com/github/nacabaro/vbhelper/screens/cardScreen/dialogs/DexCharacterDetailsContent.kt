package com.github.nacabaro.vbhelper.screens.cardScreen.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.unit.dp
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.components.DimLogo
import com.github.nacabaro.vbhelper.components.motionEnabled
import com.github.nacabaro.vbhelper.components.cyberFrame
import com.github.nacabaro.vbhelper.domain.species.SpeciesProfile
import com.github.nacabaro.vbhelper.dtos.CharacterDtos
import com.github.nacabaro.vbhelper.screens.cardScreen.stageLabel
import com.github.nacabaro.vbhelper.ui.theme.DeepPurpleBgAlt
import com.github.nacabaro.vbhelper.ui.theme.SurfaceStroke
import com.github.nacabaro.vbhelper.ui.theme.TextSecondaryOnDark
import com.github.nacabaro.vbhelper.ui.theme.VitalCyan
import com.github.nacabaro.vbhelper.utils.BitmapData
import com.github.nacabaro.vbhelper.utils.getBitmap
import com.github.nacabaro.vbhelper.utils.getObscuredBitmap
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun DexCharacterDetailsContent(
    character: CharacterDtos.CardCharaProgress,
    obscure: Boolean,
    profile: SpeciesProfile?,
    evolutions: List<CharacterDtos.EvolutionRequirementsWithSpritesAndObtained>,
    hasJogress: Boolean,
    isCustomCard: Boolean,
    speciesPickerEnabled: Boolean,
    onClose: () -> Unit,
    onJogress: () -> Unit,
    onSelectCharacter: (Long) -> Unit,
    onPickSpecies: () -> Unit,
    modifier: Modifier = Modifier,
    scanContent: @Composable () -> Unit = {},
    primaryAction: @Composable () -> Unit = {},
) {
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f, fill = false).testTag("dex-profile-body")
            .verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DexNameImage(character, obscure)
                        if (!obscure) profile?.speciesName?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.semantics { heading() })
                        }
                    }
                    if (!obscure && isCustomCard) IconButton(onClick = onPickSpecies, enabled = speciesPickerEnabled) {
                        Icon(Icons.Default.Settings, stringResource(R.string.ui_choose_species_from_dim))
                    }
                }
                Text(stringResource(when {
                    character.isCurrentlyAvailable -> R.string.dex_status_currently_available
                    character.discoveredOn != null -> R.string.dex_status_previously_obtained
                    else -> R.string.dex_status_never_obtained
                }), style = MaterialTheme.typography.labelMedium,
                    color = if (character.isCurrentlyAvailable) VitalCyan else TextSecondaryOnDark)
                HorizontalDivider(color = SurfaceStroke)
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                DexSpritePortrait(
                    BitmapData(character.spriteIdle, character.spriteWidth, character.spriteHeight),
                    Modifier.size(176.dp).testTag("dex-species-portrait"),
                    grayscale = character.discoveredOn == null, obscure = obscure,
                    idleFrame2 = character.takeIf { it.isCurrentlyAvailable }?.let {
                        BitmapData(it.spriteIdle2, it.spriteWidth, it.spriteHeight)
                    }, animationKey = character.id, framed = true, frameActive = character.isCurrentlyAvailable,
                )
            }

            if (!obscure) {
                Column(Modifier.fillMaxWidth()) {
                    profile?.level?.takeIf { it.isNotBlank() }?.let {
                        DexSpeciesFact(stringResource(R.string.ui_level), it)
                    }
                    profile?.type?.takeIf { it.isNotBlank() }?.let {
                        DexSpeciesFact(stringResource(R.string.ui_type), it)
                    }
                    DexSpeciesFact(stringResource(R.string.dex_profile_attribute), character.attribute.toString())
                    profile?.specialMoves?.let(com.github.nacabaro.vbhelper.species.ResearchText::moves)?.takeIf { it.isNotEmpty() }?.let {
                        DexSpeciesFact(stringResource(R.string.dex_profile_special_moves), it.joinToString("\n"))
                    }
                    DexSpeciesFact(stringResource(R.string.dex_profile_stage), stageLabel(character.stage))
                }
                profile?.profileDescription?.let {
                    if (profile.source == com.github.nacabaro.vbhelper.domain.species.SpeciesSource.MANUAL) it.trim()
                    else com.github.nacabaro.vbhelper.species.ResearchText.description(it)
                }?.takeIf(String::isNotBlank)?.let { description ->
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        DexProfileSectionHeader(stringResource(R.string.ui_profile))
                        Text(description, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                profile?.let { record ->
                    Text(stringResource(if (record.source == com.github.nacabaro.vbhelper.domain.species.SpeciesSource.MANUAL)
                        R.string.immersion_tamer_reference else R.string.immersion_catalog_reference),
                        style = MaterialTheme.typography.bodySmall, color = TextSecondaryOnDark)
                    com.github.nacabaro.vbhelper.components.DebugDetails(
                        "Source: ${record.source}\nCatalog match: ${record.matchedName.orEmpty()}\n" +
                            com.github.nacabaro.vbhelper.species.SpeciesRepository.SPECIES_DB_URL + "\n" +
                            record.profileDescription?.let(com.github.nacabaro.vbhelper.species.ResearchText::productionNotes).orEmpty().joinToString("\n"))
                }
                com.github.nacabaro.vbhelper.components.ResearchLibrary()
                (profile?.speciesName?.takeIf { it.isNotBlank() }
                    ?: profile?.matchedName?.takeIf { it.isNotBlank() })?.let { DexSpeciesAppearanceSection(it) }
            }

            if (obscure) Text(stringResource(R.string.dex_chara_stats_unknown))
            else if (character.baseHp != 65535) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DexProfileSectionHeader(stringResource(R.string.dex_profile_stats))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        listOf("HP" to character.baseHp, "BP" to character.baseBp, "AP" to character.baseAp).forEach { (label, value) ->
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(label, style = MaterialTheme.typography.labelMedium, color = TextSecondaryOnDark)
                                Text(value.toString(), style = MaterialTheme.typography.titleLarge)
                            }
                        }
                    }
                }
            }
            scanContent()

            if (evolutions.isNotEmpty()) {
                DexProfileSectionHeader(stringResource(R.string.dex_detail_evolutions))
                evolutions.forEach { evolution ->
                    DexRouteCard(BitmapData(evolution.spriteIdle, evolution.spriteWidth, evolution.spriteHeight),
                        evolution.discoveredOn, onClick = { onSelectCharacter(evolution.charaId) }) {
                        val requirements = buildList {
                            add(stringResource(R.string.dex_detail_time, evolution.changeTimerHours))
                            if (evolution.requiredVitals > 0) add(stringResource(R.string.dex_detail_vitals, evolution.requiredVitals))
                            if (evolution.requiredTrophies > 0) add(stringResource(R.string.dex_detail_trophies, evolution.requiredTrophies))
                            if (evolution.requiredBattles > 0) add(stringResource(R.string.dex_detail_battles, evolution.requiredBattles))
                            if (evolution.requiredWinRate > 0) add(stringResource(R.string.dex_detail_win_rate, evolution.requiredWinRate))
                            if (evolution.requiredAdventureLevelCompleted >= 0) add(stringResource(
                                R.string.dex_detail_adventure, evolution.requiredAdventureLevelCompleted + 1))
                        }
                        requirements.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
        primaryAction()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (hasJogress) TextButton(onClick = onJogress, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.dex_chara_fusions_button))
            }
            TextButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.dex_chara_close_button))
            }
        }
    }
}

/** Shared appearance lives beside the species facts so it is reachable without opening an editor. */
@Composable
private fun DexSpeciesAppearanceSection(speciesName: String) {
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as? com.github.nacabaro.vbhelper.di.VBHelper
        ?: return
    val key = remember(speciesName) { com.github.nacabaro.vbhelper.species.SpeciesCustomizationRepository.key(speciesName) }
    val dao = remember(app) { app.container.db.speciesCustomizationDao() }
    val chosen by dao.observeSpriteId(key).collectAsState(null)
    var showPicker by remember(key) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun refreshWidget() { com.github.nacabaro.vbhelper.widget.DigimonWidgetProvider.notifyActiveCharacterChanged(app) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DexProfileSectionHeader(stringResource(R.string.species_sprite_title))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            com.github.nacabaro.vbhelper.components.LoadedSpritePortrait(chosen, Modifier.size(64.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.species_sprite_hint), style = MaterialTheme.typography.bodySmall,
                    color = TextSecondaryOnDark)
                TextButton(onClick = { showPicker = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.species_choose_sprite))
                }
            }
        }
        if (chosen != null) TextButton(onClick = {
            scope.launch {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { dao.clearSpriteChoice(key) }
                refreshWidget()
            }
        }) { Text(stringResource(R.string.species_original_sprites)) }
    }
    if (showPicker) com.github.nacabaro.vbhelper.components.SpeciesSpritePickerDialog(key, chosen,
        onDismiss = { showPicker = false }, onSelected = { spriteId ->
            showPicker = false
            scope.launch {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    if (spriteId == null) dao.clearSpriteChoice(key)
                    else dao.upsertSpriteChoice(com.github.nacabaro.vbhelper.domain.species.SpeciesSpriteChoice(key, spriteId))
                }
                refreshWidget()
            }
        })
}

@Composable
private fun DexSpeciesFact(label: String, value: String) {    Column {
        Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}
            .padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top) {
            Text(label, modifier = Modifier.weight(.36f),
                style = MaterialTheme.typography.labelLarge, color = VitalCyan)
            Text(value, modifier = Modifier.weight(.64f), style = MaterialTheme.typography.bodyMedium)
        }
        HorizontalDivider(color = SurfaceStroke.copy(alpha = .45f))
    }
}

@Composable
private fun DexProfileSectionHeader(title: String) {
    Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHighest, RectangleShape)
        .cyberFrame().padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = VitalCyan,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.semantics { heading() })
    }
}

@Composable
internal fun DexNameImage(character: CharacterDtos.CardCharaProgress, obscure: Boolean) {
    if (obscure) Text(stringResource(R.string.dex_chara_unknown_name), style = MaterialTheme.typography.titleMedium)
    else {
        val bitmap = remember(character.nameSprite, character.nameSpriteWidth, character.nameSpriteHeight) {
            BitmapData(character.nameSprite, character.nameSpriteWidth, character.nameSpriteHeight).getBitmap().asImageBitmap()
        }
        DimLogo(bitmap, stringResource(R.string.dex_chara_name_icon_description), filterQuality = FilterQuality.None,
            contentScale = ContentScale.Fit, alignment = Alignment.CenterStart,
            modifier = Modifier.fillMaxWidth().height(32.dp))
    }
}

@Composable
internal fun DexSpritePortrait(
    icon: BitmapData,
    modifier: Modifier = Modifier,
    grayscale: Boolean = false,
    obscure: Boolean = false,
    idleFrame2: BitmapData? = null,
    animationKey: Any = icon.bitmap,
    framed: Boolean = false,
    frameActive: Boolean = false,
) {
    val motion = motionEnabled()
    var frame by remember(animationKey) { mutableIntStateOf(0) }
    LaunchedEffect(animationKey, idleFrame2?.bitmap, motion) {
        frame = 0
        if (motion && idleFrame2 != null) while (true) { delay(750); frame = 1 - frame }
    }
    val displayed = if (frame == 1 && idleFrame2 != null) idleFrame2 else icon
    val bitmap = remember(displayed.bitmap, displayed.width, displayed.height, obscure) {
        (if (obscure) displayed.getObscuredBitmap() else displayed.getBitmap()).asImageBitmap()
    }
    val grayscaleFilter = remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) }
    val portraitModifier = modifier.background(DeepPurpleBgAlt)
        .then(if (framed) Modifier.cyberFrame(active = frameActive) else Modifier)
    Box(portraitModifier, contentAlignment = Alignment.Center) {
        Image(bitmap, stringResource(R.string.dex_chara_icon_description), filterQuality = FilterQuality.None,
            contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(.84f),
            colorFilter = when { obscure -> ColorFilter.tint(MaterialTheme.colorScheme.secondary); grayscale -> grayscaleFilter; else -> null })
    }
}

@Composable
internal fun DexRouteCard(icon: BitmapData, discoveredOn: Long?, onClick: (() -> Unit)? = null,
                          content: @Composable ColumnScope.() -> Unit) {
    val description = stringResource(R.string.dex_detail_open_evolution)
    val interactive = if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick)
        .semantics { contentDescription = description } else Modifier
    Surface(modifier = Modifier.fillMaxWidth().then(interactive), color = DeepPurpleBgAlt,
        border = BorderStroke(1.dp, SurfaceStroke)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DexSpritePortrait(icon, Modifier.size(64.dp), grayscale = discoveredOn == null)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
        }
    }
}
