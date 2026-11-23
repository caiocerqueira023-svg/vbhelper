package com.tamerslab.transfer.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerslab.R
import com.tamerslab.battle.ui.arena.ArenaSprite
import com.tamerslab.battle.offline.tamers.ArenaSpecies
import com.tamerslab.battle.domain.BattleAttribute
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import com.tamerslab.utils.createARGBIntArray
import com.tamerslab.utils.BitmapData
import com.tamerslab.species.SpeciesAppearanceRepository
import com.tamerslab.domain.characters.Sprite
import com.tamerslab.components.motionEnabled
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.material.icons.filled.Pets
import androidx.compose.foundation.Image
import android.graphics.Bitmap
import com.tamerslab.components.VitalButton
import com.tamerslab.components.VitalButtonStyle
import com.tamerslab.components.cyberFrame
import com.tamerslab.gamesave.ObservedDigimon
import com.tamerslab.gamesave.SaveContainer
import com.tamerslab.species.CanonicalSpeciesDto

/**
 * Transfer's library vocabulary.
 *
 * One grammar across the flow: a game is a plate with its launcher art and a roster census, an
 * open save is an identity bar, a box is a three-column grid of flat Digimon cells, and an
 * individual is a full inspector sheet. Cyan is the selection/live signal only; idle chrome is a
 * hairline of onSurface so the palette's own surfaces and the sprites stay in charge.
 */

@Composable
private fun idleLine(): BorderStroke =
    BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f))

@Composable
private fun activeLine(): BorderStroke =
    BorderStroke(1.dp, MaterialTheme.colorScheme.primary)

@Composable
private fun ContainerCensus(party: Int, farm: Int, bank: Int, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CensusItem(Icons.Default.Groups, party)
        CensusItem(Icons.Default.Grass, farm)
        CensusItem(Icons.Default.AccountBalance, bank)
    }
}

@Composable
private fun CensusItem(icon: ImageVector, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Icon(icon, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(count.toString(), style = MaterialTheme.typography.labelMedium,
            color = if (count > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AutoSizeText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    maxLines: Int = 1,
    maxFontSize: TextUnit = 14.sp,
    minFontSize: TextUnit = 9.sp,
    fontWeight: FontWeight? = null,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val baseStyle = MaterialTheme.typography.bodySmall.copy(fontWeight = fontWeight)
    BoxWithConstraints(modifier) {
        val widthPx = with(density) { maxWidth.toPx() }.toInt().coerceAtLeast(1)
        val heightPx = with(density) { maxHeight.toPx() }
        val chosen = remember(text, widthPx, heightPx, maxLines, maxFontSize, minFontSize) {
            var candidate = maxFontSize.value
            var selected = minFontSize
            while (candidate >= minFontSize.value) {
                val style = baseStyle.copy(fontSize = candidate.sp,
                    lineHeight = (candidate * 1.18f).sp)
                val layout = measurer.measure(
                    AnnotatedString(text), style, maxLines = maxLines,
                    constraints = Constraints(maxWidth = widthPx),
                )
                if (layout.size.height <= heightPx && layout.lineCount <= maxLines) {
                    selected = candidate.sp
                    break
                }
                candidate -= 0.5f
            }
            selected
        }
        Text(
            text,
            style = baseStyle.copy(fontSize = chosen, lineHeight = (chosen.value * 1.18f).sp),
            color = color,
            textAlign = textAlign,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxSize(),
        )
    }
}


/**
 * The partner's own artwork with the Arena idle animation: VB sprite idle frames when the resolved
 * appearance carries them, bundled idle artwork otherwise. [size] is the drawn sprite box.
 */
@Composable
internal fun TransferIdleSprite(
    species: CanonicalSpeciesDto?,
    artworkVersion: Any,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val app = LocalContext.current.applicationContext as com.tamerslab.di.TamersLabApplication
    var arenaSpecies by remember(species?.id) { mutableStateOf<ArenaSpecies?>(null) }
    LaunchedEffect(species?.id, artworkVersion) {
        arenaSpecies = species?.let { resolved ->
            val assetId = runCatching {
                app.container.dotSpriteLibrary.catalogBlocking().bundledVbAssetIds(resolved).firstOrNull()
            }.getOrNull()
            ArenaSpecies(
                name = resolved.name,
                stage = 0,
                assetId = assetId,
                cardCharacterId = null,
                attribute = BattleAttribute.NONE,
                specialMoves = emptyList(),
                vitalStats = null,
                isCatalogDefinition = true,
            )
        }
    }
    ArenaSprite(arenaSpecies, size, modifier)
}

@Composable
internal fun TransferGamePlate(
    title: String,
    status: String,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    census: Triple<Int, Int, Int>? = null,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = idleLine(),
        modifier = modifier.fillMaxWidth().height(214.dp).alpha(if (enabled) 1f else 0.42f),
    ) {
        Column(Modifier.padding(10.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(112.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(76.dp), contentAlignment = Alignment.Center) { leading() }
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(38.dp)) {
                AutoSizeText(
                    title,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    maxFontSize = 15.sp,
                    minFontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(2.dp))
            if (census != null) {
                ContainerCensus(census.first, census.second, census.third)
                Spacer(Modifier.height(2.dp))
            }
            Box(Modifier.fillMaxWidth().heightIn(min = 16.dp)) {
                AutoSizeText(
                    status,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    maxFontSize = 12.sp,
                    minFontSize = 9.sp,
                )
            }
        }
    }
}

@Composable
internal fun TransferWidePlate(
    title: String,
    status: String,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    trailingText: String? = null,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = idleLine(),
        modifier = modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.42f),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center) { leading() }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(status, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            trailingText?.let {
                Text(it, style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary, maxLines = 1)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The opened save, as an identity bar: game art, document, an at-a-glance roster census. */
@Composable
internal fun TransferSaveIdentityBar(
    saveLabel: String,
    statusLine: String,
    leading: @Composable () -> Unit,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    onSwitchSaves: (() -> Unit)? = null,
    onSaveCopy: (() -> Unit)? = null,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = idleLine(),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { leading() }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(saveLabel, style = MaterialTheme.typography.titleSmall, maxLines = 2,
                        overflow = TextOverflow.Ellipsis)
                    Text(statusLine, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = onRefresh, enabled = !refreshing) {
                    if (refreshing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.transfer_refresh))
                }
            }
            if (onSwitchSaves != null || onSaveCopy != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    val quiet = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                    onSwitchSaves?.let {
                        TextButton(onClick = it, colors = quiet) {
                            Text(stringResource(R.string.transfer_workspace_choose_save))
                        }
                    }
                    onSaveCopy?.let {
                        TextButton(onClick = it, colors = quiet) {
                            Text(stringResource(R.string.world_ds_choose_copy))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun containerIcon(container: SaveContainer?): ImageVector = when (container) {
    null -> Icons.Default.Apps
    SaveContainer.PARTY -> Icons.Default.Groups
    SaveContainer.FARM -> Icons.Default.Grass
    SaveContainer.BANK -> Icons.Default.AccountBalance
    SaveContainer.UNASSIGNED -> Icons.Default.Inventory2
}

/** Box scope tabs: All, Party, Farm, Bank — one row, counts inline, selected state in the accent. */
@Composable
internal fun TransferScopeTabs(
    counts: Map<SaveContainer, Int>,
    total: Int,
    selected: SaveContainer?,
    onSelect: (SaveContainer?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scopes = buildList {
        add(null to total)
        SaveContainer.entries.forEach { container ->
            val count = counts[container] ?: 0
            if (count > 0) add(container to count)
        }
    }
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        scopes.forEach { (container, count) ->
            val active = container == selected
            Surface(
                onClick = { onSelect(container) },
                shape = MaterialTheme.shapes.small,
                color = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                else MaterialTheme.colorScheme.surfaceContainer,
                border = if (active) activeLine() else idleLine(),
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(containerIcon(container), null, Modifier.size(16.dp),
                        tint = if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        (container?.let { stringResource(containerLabel(it)) }
                            ?: stringResource(R.string.transfer_box_all)) + " · $count",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun recordDisplayName(nickname: String, species: CanonicalSpeciesDto?): String {
    val usable = nickname.takeIf {
        it.isNotBlank() && !it.startsWith("digimon.", ignoreCase = true)
    }
    return usable ?: species?.name ?: stringResource(R.string.transfer_unnamed_record)
}

/** Long single-word names get invisible break points so a card never clips them. */
private fun wrapNameForCard(name: String): String =
    if (name.any { it == ' ' }) name else name.chunked(4).joinToString("\u200B")

/** One Digimon in the box: a flat cell with the sprite as the hero, name and level underneath. */
@Composable
internal fun TransferDigimonCard(
    nickname: String,
    level: Int?,
    species: CanonicalSpeciesDto?,
    container: SaveContainer,
    position: Int,
    artworkVersion: Any,
    onClick: () -> Unit,
    selected: Boolean = false,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        else MaterialTheme.colorScheme.surfaceContainer,
        border = if (selected) activeLine() else idleLine(),
        modifier = Modifier.fillMaxWidth().height(158.dp),
    ) {
        Column(Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(104.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center,
            ) {
                TransferIdleSprite(species, artworkVersion, 92.dp)
            }
            Box(Modifier.fillMaxWidth().height(28.dp).padding(top = 3.dp)) {
                AutoSizeText(
                    recordDisplayName(nickname, species),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    maxFontSize = 11.sp,
                    minFontSize = 8.sp,
                )
            }
            Box(Modifier.fillMaxWidth().height(16.dp)) {
                AutoSizeText(
                    level?.let { stringResource(R.string.transfer_workspace_level, it) }.orEmpty(),
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    maxFontSize = 11.sp,
                    minFontSize = 9.sp,
                )
            }
        }
    }
}

@Composable
private fun LevelBadge(level: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
    ) {
        Text(
            stringResource(R.string.transfer_workspace_level, level),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
        )
    }
}

/** Outgoing partner plate for the send screen. */
@Composable
internal fun TransferOutgoingPlate(
    nickname: String,
    species: CanonicalSpeciesDto?,
    level: Int?,
    artworkVersion: Any,
    status: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = idleLine(),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(72.dp).clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center) {
                TransferIdleSprite(species, artworkVersion, 58.dp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(nickname, style = MaterialTheme.typography.titleMedium, maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
                species?.let {
                    Text(it.name, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    level?.let { LevelBadge(it) }
                    Text(status, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                }
            }
        }
    }
}

/** Full inspector for one native individual: portrait, facts, native detail, one primary action. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TransferInspectorSheet(
    record: ObservedDigimon,
    nickname: String,
    species: CanonicalSpeciesDto?,
    artworkVersion: Any,
    moveIntoStorage: Boolean,
    moving: Boolean = false,
    moveBlocker: Int? = R.string.transfer_native_move_unavailable,
    onMove: (() -> Unit)? = null,
    onGrantAccess: (() -> Unit)? = null,
    onAdmitToParty: (() -> Unit)? = null,
    admitBlocker: Int? = null,
    individualId: String? = null,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val app = LocalContext.current.applicationContext as com.tamerslab.di.TamersLabApplication
    val storyDomain by produceState<com.tamerslab.domain.transfer.StoryProgressDomain?>(null, individualId) {
        value = null
        val id = individualId ?: return@produceState
        value = try {
            app.container.progressDomains.story(id)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }
    var showNativeDetails by rememberSaveable(record.recordKey) { mutableStateOf(false) }
    val native = record.nativeParameters
    ModalBottomSheet(
        onDismissRequest = { if (!moving) onDismiss() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    Modifier.size(96.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .cyberFrame(active = true),
                    contentAlignment = Alignment.Center,
                ) {
                    TransferIdleSprite(species, artworkVersion, 78.dp)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(recordDisplayName(nickname, species),
                        style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(species?.name ?: stringResource(R.string.transfer_species_unresolved),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        record.level.takeIf { it > 0 }?.let { LevelBadge(it) }
                        Surface(shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            border = idleLine()) {
                            Text(
                                stringResource(containerLabel(record.container)) + " · ${record.position}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }

            moveBlocker?.let {
                Text(stringResource(it), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
            }

            storyDomain?.let { story ->
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    DomainHeader(stringResource(R.string.transfer_progress_story),
                        domainSourceLabel(story.seedSource))
                    InspectorFactRow(stringResource(R.string.transfer_workspace_level), story.level.toString())
                    InspectorFactRow(stringResource(R.string.native_measure_hp), "${story.hp} / ${story.maxHp}")
                    InspectorFactRow(stringResource(R.string.native_measure_mp), "${story.mp} / ${story.maxMp}")
                    InspectorFactRow(stringResource(R.string.native_measure_attack), story.attack.toString())
                    InspectorFactRow(stringResource(R.string.native_measure_defense), story.defense.toString())
                    InspectorFactRow(stringResource(R.string.native_measure_spirit), story.spirit.toString())
                    InspectorFactRow(stringResource(R.string.native_measure_speed), story.speed.toString())
                    InspectorFactRow(stringResource(R.string.native_measure_exp), story.expTotal.toString())
                    story.aptitude?.let {
                        InspectorFactRow(stringResource(R.string.native_measure_aptitude), it.toString())
                    }
                }
            }

            Column(
                Modifier.fillMaxWidth().heightIn(max = 300.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                val facts = buildList {
                    if (native != null) {
                        add(stringResource(R.string.native_measure_hp) to "${native.hp} / ${native.maxHp}")
                        add(stringResource(R.string.native_measure_mp) to "${native.mp} / ${native.maxMp}")
                        add(stringResource(R.string.native_measure_attack) to native.attack.toString())
                        add(stringResource(R.string.native_measure_defense) to native.defense.toString())
                        add(stringResource(R.string.native_measure_spirit) to native.spirit.toString())
                        add(stringResource(R.string.native_measure_speed) to native.speed.toString())
                        add(stringResource(R.string.native_measure_aptitude) to native.aptitude.toString())
                    }
                    record.totalExp?.let { add(stringResource(R.string.native_measure_exp) to it.toString()) }
                }
                facts.forEach { (label, value) -> InspectorFactRow(label, value) }
                TextButton(onClick = { showNativeDetails = !showNativeDetails }) {
                    Text(stringResource(if (showNativeDetails) R.string.transfer_hide_native_details
                    else R.string.transfer_native_details))
                }
                if (showNativeDetails) {
                    InspectorFactRow(stringResource(R.string.transfer_box_slot_label), record.position.toString())
                    native?.let { parameters ->
                        InspectorFactRow(stringResource(R.string.transfer_world_ds_copy), parameters.saveBlock.toString())
                        if (parameters.expCounters.isNotEmpty()) {
                            InspectorFactRow(
                                stringResource(R.string.transfer_native_exp_channels),
                                parameters.expCounters.joinToString(" · "),
                            )
                        }
                    }
                }
            }

            if (moveIntoStorage && (admitBlocker != null || onAdmitToParty != null)) {
                TextButton(onClick = { onAdmitToParty?.invoke() },
                    enabled = onAdmitToParty != null && !moving) {
                    Text(stringResource(R.string.transfer_move_to_party))
                }
                admitBlocker?.let {
                    Text(stringResource(it), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (moveBlocker == R.string.transfer_move_write_access && onGrantAccess != null) {
                TextButton(onClick = onGrantAccess, enabled = !moving) {
                    Text(stringResource(R.string.transfer_move_grant_write))
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss, enabled = !moving,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant)) {
                    Text(stringResource(R.string.transfer_close))
                }
                VitalButton(
                    onClick = { onMove?.invoke() },
                    enabled = onMove != null && !moving,
                    style = VitalButtonStyle.PRIMARY,
                    modifier = Modifier.weight(1f),
                ) {
                    if (moving) CircularProgressIndicator(Modifier.size(18.dp).padding(end = 4.dp), strokeWidth = 2.dp)
                    Text(stringResource(if (moveIntoStorage) R.string.transfer_move_to_storage
                    else R.string.transfer_move_to_game))
                }
            }
        }
    }
}

@Composable
private fun DomainHeader(title: String, source: Int) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
        Text(stringResource(source), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

private fun domainSourceLabel(source: com.tamerslab.domain.transfer.ProgressSeedSource): Int =
    when (source) {
        com.tamerslab.domain.transfer.ProgressSeedSource.VB -> R.string.transfer_progress_seed_vb
        com.tamerslab.domain.transfer.ProgressSeedSource.STORY -> R.string.transfer_progress_seed_story
        com.tamerslab.domain.transfer.ProgressSeedSource.IMPORT -> R.string.transfer_progress_seed_import
    }

@Composable
private fun InspectorFactRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Text(value, style = MaterialTheme.typography.bodyMedium, maxLines = 2, textAlign = TextAlign.End)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
}

/** A blank empty slot: same footprint as a cell, nothing drawn inside it. */
@Composable
internal fun TransferEmptySlot(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(158.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.28f)),
    )
}

/** Shared identity bar for the non-game origins (bracelet, V-Pet): same frame as a save bar. */
@Composable
internal fun TransferOriginIdentityBar(
    title: String,
    subtitle: String,
    leading: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = idleLine(),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { leading() }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Maintenance surfaces: grouped by what they protect, each row says what it does. */
@Composable
internal fun TransferToolsScreen(
    onManageSaves: () -> Unit,
    onArchives: () -> Unit,
    onBackups: () -> Unit,
    onIcons: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val app = LocalContext.current.applicationContext as com.tamerslab.di.TamersLabApplication
    var saveCount by remember { mutableStateOf<Int?>(null) }
    var archiveCount by remember { mutableStateOf<Int?>(null) }
    var backupCount by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(app) {
        runCatching { saveCount = app.container.linkedGameSaves.sources.first().size }
        runCatching { archiveCount = app.container.gameImportAcquisition.archives.first().size }
        runCatching {
            backupCount = app.container.experimentalGameMoves.backups().size +
                app.container.experimentalGameSends.backups().size
        }
    }
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.transfer_tools_hint), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        ToolSection(stringResource(R.string.transfer_tools_group_saves))
        TransferWidePlate(
            stringResource(R.string.transfer_tools_saves),
            stringResource(R.string.transfer_tools_saves_desc),
            onClick = onManageSaves,
            leading = { Icon(Icons.Default.Edit, null, Modifier.size(24.dp)) },
            trailingText = saveCount?.toString(),
        )
        ToolSection(stringResource(R.string.transfer_tools_group_protection))
        TransferWidePlate(
            stringResource(R.string.transfer_move_backups),
            stringResource(R.string.transfer_tools_backups_desc),
            onClick = onBackups,
            leading = { Icon(Icons.Default.Backup, null, Modifier.size(24.dp)) },
            trailingText = backupCount?.toString(),
        )
        TransferWidePlate(
            stringResource(R.string.transfer_tools_archive),
            stringResource(R.string.transfer_tools_archive_desc),
            onClick = onArchives,
            leading = { Icon(Icons.Default.Inventory2, null, Modifier.size(24.dp)) },
            trailingText = archiveCount?.toString(),
        )
        ToolSection(stringResource(R.string.transfer_tools_group_appearance))
        TransferWidePlate(
            stringResource(R.string.transfer_launcher_icons),
            stringResource(R.string.transfer_tools_icons_desc),
            onClick = onIcons,
            leading = { Icon(Icons.Default.Image, null, Modifier.size(24.dp)) },
        )
    }
}

@Composable
private fun ToolSection(title: String) {
    Text(title, style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp))
}

