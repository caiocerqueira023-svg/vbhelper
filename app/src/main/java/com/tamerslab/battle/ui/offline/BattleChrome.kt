@file:OptIn(ExperimentalMaterial3Api::class)

package com.tamerslab.battle.ui.offline

import android.graphics.RectF
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tamerslab.R
import com.tamerslab.battle.domain.TechniqueDefinition
import com.tamerslab.battle.domain.TechniqueKind
import com.tamerslab.battle.domain.TechniqueRangeProfile
import com.tamerslab.battle.offline.core.BattleOutcome
import com.tamerslab.battle.offline.core.BattleSnapshot
import com.tamerslab.battle.offline.core.BattleStatistics
import com.tamerslab.battle.offline.core.CombatantSnapshot
import com.tamerslab.components.CyberPanel
import com.tamerslab.components.VitalButton
import com.tamerslab.components.VitalButtonStyle
import com.tamerslab.home.ui.PartnerSlabAlpha
import com.tamerslab.ui.theme.BattleDestructive
import com.tamerslab.ui.theme.BattleEnemyHealth
import com.tamerslab.ui.theme.BattleErrorOutline
import com.tamerslab.ui.theme.BattleErrorSurface
import com.tamerslab.ui.theme.BattleTrack
import com.tamerslab.ui.theme.OnVitalPrimary
import com.tamerslab.ui.theme.SceneTextShadow
import com.tamerslab.ui.theme.SurfaceDeepPurple
import com.tamerslab.ui.theme.SurfaceElevatedPurple
import com.tamerslab.ui.theme.SurfaceStroke
import com.tamerslab.ui.theme.TextPrimaryOnDark
import com.tamerslab.ui.theme.TextSecondaryOnDark
import com.tamerslab.ui.theme.VitalCyan
import com.tamerslab.ui.theme.VitalPurpleBright
import com.tamerslab.ui.theme.VitalYellow

/** Fallback height the floating top cluster occupies before layout measurement completes. */
internal val BattleTopClusterReserved: Dp = 88.dp

private val BattleKeyCorner: Dp = 8.dp

@Composable
internal fun TextStyle.hud(): TextStyle =
    copy(shadow = Shadow(SceneTextShadow.copy(alpha = 0.45f), Offset(1f, 1f), 3f))

/**
 * Bottom ground: uses the same `SurfaceElevatedPurple` at `PartnerSlabAlpha` as Home's partner
 * slab and Radar's signal band so all world-overlay chrome reads as one material across themes.
 */
@Composable
private fun battleBandGradient(): Brush {
    val ground = SurfaceElevatedPurple
    return Brush.verticalGradient(
        0f to Color.Transparent,
        0.28f to ground.copy(alpha = PartnerSlabAlpha * 0.62f),
        1f to ground.copy(alpha = (PartnerSlabAlpha + 0.10f).coerceAtMost(0.95f))
    )
}

/**
 * Top ground: mirrors `radarTopScrim` and Home's top status scrim so entering a fight from
 * Radar or Arena keeps the exact same HUD surface tone.
 */
@Composable
private fun battleTopScrim(): Brush {
    val ground = SurfaceElevatedPurple
    return Brush.verticalGradient(
        0f to ground.copy(alpha = PartnerSlabAlpha),
        0.58f to ground.copy(alpha = PartnerSlabAlpha * 0.55f),
        1f to Color.Transparent
    )
}

// ---------------------------------------------------------------------------------------------
// Top cluster
// ---------------------------------------------------------------------------------------------

/**
 * Floating battle identity: exit, matchup, context line, recenter, Debug and pause keys.
 * Lives over the live scene on its own top scrim; never a shelf or a page header.
 */
@Composable
fun BattleTopCluster(
    title: String,
    subtitle: String?,
    paused: Boolean,
    modifier: Modifier = Modifier,
    debugToolsEnabled: Boolean = false,
    recenterEnabled: Boolean = false,
    onExit: () -> Unit,
    onRecenter: (() -> Unit)? = null,
    onDebug: (() -> Unit)? = null,
    onTogglePause: () -> Unit,
    onHeightChanged: (Dp) -> Unit = {},
) {
    val density = LocalDensity.current
    Column(
        modifier
            .fillMaxWidth()
            .background(battleTopScrim())
            // Observe the outer size (including the status-bar inset) so projected plates and
            // finisher captions start below the entire floating cluster.
            .onSizeChanged { onHeightChanged(with(density) { it.height.toDp() }) }
            .statusBarsPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .heightIn(min = 56.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BattleChromeKey(
                onClick = onExit,
                contentDescription = stringResource(R.string.ui_battle_training_exit)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = TextPrimaryOnDark,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column(
                Modifier.weight(1f).padding(start = 10.dp, end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    title,
                    color = TextPrimaryOnDark,
                    style = MaterialTheme.typography.titleMedium.hud(),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() }
                )
                subtitle?.let {
                    Text(
                        it,
                        color = if (paused) VitalCyan else TextSecondaryOnDark,
                        style = MaterialTheme.typography.labelMedium.hud(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (onRecenter != null) {
                BattleChromeKey(
                    onClick = onRecenter,
                    enabled = recenterEnabled,
                    contentDescription = stringResource(R.string.ui_battle_training_recenter_camera),
                    testTag = "offline-battle-recenter-camera"
                ) {
                    Icon(
                        imageVector = Icons.Filled.CenterFocusStrong,
                        contentDescription = null,
                        tint = if (recenterEnabled) TextPrimaryOnDark else TextSecondaryOnDark,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            if (debugToolsEnabled && onDebug != null) {
                BattleChromeKey(
                    onClick = onDebug,
                    contentDescription = stringResource(R.string.ui_battle_training_ai_debug),
                    accent = true
                ) {
                    Icon(
                        imageVector = Icons.Outlined.BugReport,
                        contentDescription = null,
                        tint = VitalCyan,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            BattleChromeKey(
                onClick = onTogglePause,
                accent = paused,
                contentDescription = if (paused) stringResource(R.string.ui_battle_training_resume)
                else stringResource(R.string.ui_battle_pause)
            ) {
                Icon(
                    imageVector = if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    contentDescription = null,
                    tint = if (paused) VitalCyan else TextPrimaryOnDark,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(1.dp)
                .background(TextPrimaryOnDark.copy(alpha = 0.12f))
        )
    }
}

@Composable
private fun BattleChromeKey(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Boolean = false,
    testTag: String? = null,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val shape = CutCornerShape(BattleKeyCorner)
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(shape)
            // Bare key, like Home's HUD: the icon and its 48dp target, no fill and no hairline frame.
            // The cut-corner shape only bounds the press ripple; cyan marks the active state.
            .clickable(
                interactionSource = interaction,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) { content() }
}

// ---------------------------------------------------------------------------------------------
// Combatant HUD
// ---------------------------------------------------------------------------------------------

private val enemyPlateWidth = 116.dp

/**
 * Projected enemy name + health plates. The selected partner's vitals live in the command band's
 * header, so nothing floats over the arena besides the opposing plates and their target hairline.
 * Everything floats in the scene, anchored to the projected fighter bounds.
 */
@Composable
fun BoxScope.BattleCombatantHud(
    snapshot: BattleSnapshot,
    sceneView: OfflineBattleSceneView?,
    fighterScale: Float,
    fighters: Map<String, BattleFighterPresentation>,
    selectedOpponent: CombatantSnapshot?,
    topInset: Dp = BattleTopClusterReserved,
    bottomInset: Dp = 0.dp,
    projectionVersion: () -> Int = { 0 },
) {
    // Reading the projection state here (not in the screen root) scopes camera-update
    // invalidation to the projected HUD instead of recomposing the whole battle surface.
    projectionVersion()
    val density = LocalDensity.current
    val sceneW = sceneView?.width ?: 0
    val sceneH = sceneView?.height ?: 0
    val plateWidthPx = with(density) { enemyPlateWidth.toPx() }
    // Grow with the system font scale instead of clipping the name/HP row at 1.3x.
    val plateHeight = 16.dp * density.fontScale + 13.dp
    val plateHeightPx = with(density) { plateHeight.toPx() }
    val topLimit = with(density) { topInset.toPx() } + plateHeightPx * 0.5f
    val bottomLimit = sceneH - with(density) { bottomInset.toPx() } - plateHeightPx
    val sideGap = with(density) { 6.dp.toPx() }

    if (sceneView != null && sceneW > 0 && sceneH > 0) {
        val placed = mutableListOf<RectF>()
        val bodyBounds = (snapshot.alliedMembers + snapshot.opposingMembers).mapNotNull { member ->
            val visualScale = fighters[member.combatantId]?.visualScaleMultiplier ?: 1f
            val foot = sceneView.projectBattlePosition(member.position.x, 0f, member.position.z)
            val head = sceneView.projectBattlePosition(
                member.position.x,
                fighterScale * visualScale,
                member.position.z,
            )
            if (foot == null || head == null) null else {
                val halfWidth = kotlin.math.abs(foot.second - head.second) * 0.6f
                RectF(head.first - halfWidth, head.second, head.first + halfWidth, foot.second)
            }
        }
        snapshot.opposingMembers.forEach { fighter ->
            if (fighter.health <= 0) return@forEach
            val visualScale = fighters[fighter.combatantId]?.visualScaleMultiplier ?: 1f
            val point = sceneView.projectBattlePosition(
                fighter.position.x,
                1.9f * visualScale,
                fighter.position.z,
            ) ?: return@forEach
            val anchorX = point.first
            val anchorY = point.second - plateHeightPx
            val candidates = listOf(
                (anchorX - plateWidthPx / 2f) to anchorY,
                (anchorX + sideGap) to (point.second - plateHeightPx / 2f),
                (anchorX - plateWidthPx - sideGap) to (point.second - plateHeightPx / 2f),
                (anchorX - plateWidthPx / 2f) to (point.second + sideGap),
            )
            val placement = candidates.firstOrNull { (x, y) ->
                val rect = RectF(x, y, x + plateWidthPx, y + plateHeightPx)
                x >= 0f && y >= topLimit && rect.right <= sceneW &&
                    rect.bottom <= bottomLimit &&
                    bodyBounds.none { RectF.intersects(rect, it) } &&
                    placed.none { RectF.intersects(rect, it) }
            } ?: return@forEach
            placed += RectF(placement.first, placement.second, placement.first + plateWidthPx, placement.second + plateHeightPx)
            EnemyPlate(
                fighter = fighter,
                selected = fighter.combatantId == selectedOpponent?.combatantId,
                x = with(density) { placement.first.toDp() },
                y = with(density) { placement.second.toDp() },
                height = plateHeight,
            )
        }
    }
}

@Composable
private fun BoxScope.EnemyPlate(
    fighter: CombatantSnapshot,
    selected: Boolean,
    x: Dp,
    y: Dp,
    height: Dp,
) {
    Surface(
        modifier = Modifier
            .offset(x = x, y = y)
            .width(enemyPlateWidth)
            .heightIn(min = height)
            .semantics {
                contentDescription = fighter.displayName
                stateDescription = "${fighter.health}/${fighter.maxHealth} HP"
                progressBarRangeInfo =
                    androidx.compose.ui.semantics.ProgressBarRangeInfo(
                        fighter.health.toFloat(),
                        0f..fighter.maxHealth.coerceAtLeast(1).toFloat()
                    )
                this.selected = selected
            }
            .testTag("offline-battle-enemy-health-bar"),
        color = SurfaceDeepPurple.copy(alpha = 0.92f),
        shape = CutCornerShape(6.dp),
        border = BorderStroke(
            1.dp,
            if (selected) VitalCyan else BattleEnemyHealth.copy(alpha = 0.82f)
        )
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = height)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                fighter.displayName,
                color = TextPrimaryOnDark,
                style = MaterialTheme.typography.labelSmall.hud(),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .background(BattleTrack.copy(alpha = 0.85f), CutCornerShape(1.dp))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth((fighter.health.toFloat() / fighter.maxHealth.coerceAtLeast(1)).coerceIn(0f, 1f))
                        .height(5.dp)
                        .background(BattleEnemyHealth, CutCornerShape(1.dp))
                )
            }
        }
    }
}

/**
 * The partner's instruments, in the header slot the dead Command Points meter used to occupy:
 * three compact readings over their own hairline tracks. The scene keeps its full height and the
 * arena is no longer covered by a floating strip.
 */
@Composable
private fun PartnerVitalsBand(fighter: CombatantSnapshot) {
    val description = buildString {
        append(fighter.displayName)
        append(" · HP ").append(fighter.health).append('/').append(fighter.maxHealth)
        append(" · MP ").append(fighter.energy).append('/').append(fighter.maxEnergy)
        append(" · ESP ").append(fighter.specialCharge).append('/').append(fighter.maxSpecialCharge)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("offline-battle-partner-vitals")
            .semantics(mergeDescendants = true) { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        BattleVitalInstrument("HP", fighter.health, fighter.maxHealth, VitalCyan, Modifier.weight(1f))
        BattleVitalInstrument("MP", fighter.energy, fighter.maxEnergy, VitalPurpleBright, Modifier.weight(1f))
        if (fighter.specialTechniqueId != null) {
            BattleVitalInstrument(
                "ESP",
                fighter.specialCharge,
                fighter.maxSpecialCharge,
                VitalYellow,
                Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun BattleVitalInstrument(
    label: String,
    value: Int,
    maximum: Int,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                label,
                color = color,
                style = MaterialTheme.typography.labelSmall.hud().copy(
                    fontWeight = FontWeight.Black,
                    fontFeatureSettings = "tnum"
                ),
                maxLines = 1,
                softWrap = false
            )
            Text(
                "$value/${maximum.coerceAtLeast(1)}",
                color = TextPrimaryOnDark,
                style = MaterialTheme.typography.labelSmall.hud().copy(
                    fontWeight = FontWeight.Bold,
                    fontFeatureSettings = "tnum"
                ),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip
            )
        }
        Box(Modifier.fillMaxWidth().height(3.dp), contentAlignment = Alignment.BottomStart) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(BattleTrack.copy(alpha = 0.8f), CutCornerShape(1.dp))
            )
            Box(
                Modifier
                    .fillMaxWidth((value.toFloat() / maximum.coerceAtLeast(1)).coerceIn(0f, 1f))
                    .height(2.dp)
                    .background(color, CutCornerShape(1.dp))
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Floating command band
// ---------------------------------------------------------------------------------------------

private val battleCommandOrder = listOf(
    BattleCommandSlot.TECHNIQUE_1,
    BattleCommandSlot.TECHNIQUE_2,
    BattleCommandSlot.TECHNIQUE_3,
    BattleCommandSlot.ITEMS,
    BattleCommandSlot.SPECIAL,
    BattleCommandSlot.STRATEGY,
    BattleCommandSlot.FOCUS,
    BattleCommandSlot.DEFEND,
    BattleCommandSlot.MOVE,
)

/**
 * One floating command instrument: the selected partner's name and HP/MP/ESP instruments where the
 * dead Command Points meter used to sit, one quiet event line, and a fixed 3×3 deck. Row one is the
 * partner's three equipped techniques; row two Items / Special / Strategy; row three Focus / Defend /
 * Move. Move arms a one-tap arena destination instead of opening a menu, and unavailable keys stay
 * visible so muscle memory survives every combat state.
 */
@Composable
fun BattleCommandBand(
    selectedAlly: CombatantSnapshot?,
    selectedOpponent: CombatantSnapshot?,
    techniques: List<TechniqueDefinition>,
    feedback: String,
    moveArmed: Boolean,
    commandsEnabled: Boolean,
    onTechnique: (Int) -> Unit,
    onOpenItems: () -> Unit,
    onSpecial: () -> Unit,
    onOpenStrategies: () -> Unit,
    onFocus: () -> Unit,
    onDefend: () -> Unit,
    onArmMove: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "offline-battle-touch-deck",
) {
    val alive = commandsEnabled && selectedAlly != null && selectedAlly.health > 0
    val targetAlive = commandsEnabled && selectedOpponent != null && selectedOpponent.health > 0
    val equippedTechniques = selectedAlly?.techniqueIds.orEmpty().mapNotNull { id ->
        techniques.firstOrNull { it.techniqueId == id }
    }
    val techniqueReady = (0 until 3).map { index ->
        val technique = equippedTechniques.getOrNull(index) ?: return@map false
        val selfOrSupport = technique.rangeProfile == TechniqueRangeProfile.SELF ||
            technique.kind == TechniqueKind.HEAL || technique.kind == TechniqueKind.SUPPORT
        val targetOk = if (selfOrSupport) alive else targetAlive
        alive && targetOk &&
            (selectedAlly?.cooldownsMillis?.get(technique.techniqueId) ?: 0L) == 0L &&
            ((selectedAlly?.energy ?: 0) - (selectedAlly?.reservedEnergy ?: 0)) >= technique.energyCost
    }
    val special = selectedAlly?.specialTechniqueId?.let { id ->
        techniques.firstOrNull { it.techniqueId == id }
    }
    val availableSpecialCharge = selectedAlly?.let {
        (it.specialCharge - it.reservedSpecialCharge).coerceAtLeast(0)
    } ?: 0
    val specialReady = alive && special != null &&
        availableSpecialCharge >= selectedAlly.maxSpecialCharge &&
        selectedAlly.energy - selectedAlly.reservedEnergy >= special.energyCost &&
        (selectedAlly.cooldownsMillis[special.techniqueId] ?: 0L) == 0L &&
        (special.healPower > 0 || targetAlive)
    val deck = battleCommandDeck(
        BattleCommandAvailability(
            partnerReady = alive,
            targetReady = targetAlive,
            techniqueReady = techniqueReady,
            specialReady = specialReady,
            moveArmed = moveArmed,
        )
    ).associateBy { it.slot }
    val configuration = LocalConfiguration.current
    val compactHeight = configuration.screenHeightDp < 520

    Box(
        modifier
            .fillMaxWidth()
            .background(battleBandGradient())
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(TextPrimaryOnDark.copy(alpha = 0.12f))
        )
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(
                    start = 12.dp,
                    end = 12.dp,
                    top = if (compactHeight) 6.dp else 8.dp,
                    bottom = if (compactHeight) 6.dp else 8.dp
                )
                .testTag(testTag),
            verticalArrangement = Arrangement.spacedBy(if (compactHeight) 4.dp else 6.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().testTag("offline-battle-command-header"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    stringResource(R.string.ui_battle_deck_commands),
                    color = TextPrimaryOnDark,
                    style = MaterialTheme.typography.labelMedium.hud(),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                if (selectedAlly != null) {
                    Text(
                        selectedAlly.displayName,
                        color = TextSecondaryOnDark,
                        style = MaterialTheme.typography.labelSmall.hud(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
            }
            selectedAlly?.let { PartnerVitalsBand(it) }
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // The same quiet signal tick used by the Radar status band marks the event line.
                Box(
                    Modifier
                        .width(2.dp)
                        .height(14.dp)
                        .background(if (commandsEnabled) VitalCyan else SurfaceStroke.copy(alpha = 0.6f))
                )
                Text(
                    feedback,
                    color = TextSecondaryOnDark,
                    style = MaterialTheme.typography.labelSmall.hud(),
                    // One line keeps the band height stable through finishers and reward changes,
                    // so the nine command stations never shift under the player's thumb.
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 16.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite }
                )
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(
                    Modifier.widthIn(max = 520.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(if (compactHeight) 4.dp else 6.dp)
                ) {
                    battleCommandOrder.chunked(3).forEach { rowSlots ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(if (compactHeight) 4.dp else 6.dp)
                        ) {
                            rowSlots.forEach slotLoop@{ slot ->
                                val item = deck[slot] ?: return@slotLoop
                                val keyHeight = 48.dp
                                when (slot) {
                                    BattleCommandSlot.TECHNIQUE_1,
                                    BattleCommandSlot.TECHNIQUE_2,
                                    BattleCommandSlot.TECHNIQUE_3 -> {
                                        val index = slot.ordinal
                                        val technique = equippedTechniques.getOrNull(index)
                                        val displayName = technique?.let {
                                            battleTechniqueName(it.techniqueId, it.displayName)
                                        }
                                        BattleCommandKey(
                                            label = displayName ?: "—",
                                            subLabel = technique?.let {
                                                stringResource(R.string.ui_battle_cost_energy, it.energyCost)
                                            },
                                            enabled = item.enabled && technique != null,
                                            accent = false,
                                            height = keyHeight,
                                            contentDescription = if (technique != null && displayName != null) {
                                                stringResource(
                                                    R.string.ui_battle_cmd_technique_description,
                                                    displayName,
                                                    technique.energyCost
                                                )
                                            } else {
                                                null
                                            },
                                            onClick = { onTechnique(index) }
                                        )
                                    }

                                    BattleCommandSlot.ITEMS -> BattleCommandKey(
                                        label = "${stringResource(R.string.ui_battle_cmd_items)} ›",
                                        enabled = item.enabled,
                                        accent = false,
                                        height = keyHeight,
                                        onClick = onOpenItems
                                    )

                                    BattleCommandSlot.SPECIAL -> BattleCommandKey(
                                        label = if (specialReady) {
                                            stringResource(R.string.ui_battle_cmd_special_ready)
                                        } else {
                                            val maximum = selectedAlly?.maxSpecialCharge?.coerceAtLeast(1) ?: 100
                                            val visibleCharge = selectedAlly?.specialCharge ?: 0
                                            stringResource(
                                                R.string.ui_battle_cmd_special_charging,
                                                visibleCharge * 100 / maximum
                                            )
                                        },
                                        enabled = item.enabled,
                                        accent = item.enabled && specialReady,
                                        accentColor = VitalYellow,
                                        height = keyHeight,
                                        onClick = onSpecial
                                    )

                                    BattleCommandSlot.STRATEGY -> BattleCommandKey(
                                        label = "${stringResource(R.string.ui_battle_cmd_strategy)} ›",
                                        enabled = item.enabled,
                                        accent = false,
                                        height = keyHeight,
                                        onClick = onOpenStrategies
                                    )

                                    BattleCommandSlot.FOCUS -> BattleCommandKey(
                                        label = stringResource(R.string.ui_battle_cmd_focus),
                                        enabled = item.enabled,
                                        accent = false,
                                        height = keyHeight,
                                        onClick = onFocus
                                    )

                                    BattleCommandSlot.DEFEND -> BattleCommandKey(
                                        label = stringResource(R.string.ui_battle_cmd_defend),
                                        enabled = item.enabled,
                                        accent = false,
                                        height = keyHeight,
                                        onClick = onDefend
                                    )

                                    BattleCommandSlot.MOVE -> BattleCommandKey(
                                        label = stringResource(R.string.ui_battle_cmd_move),
                                        enabled = item.enabled,
                                        accent = moveArmed,
                                        height = keyHeight,
                                        contentDescription = if (moveArmed) {
                                            stringResource(R.string.ui_battle_move_armed)
                                        } else {
                                            null
                                        },
                                        onClick = onArmMove
                                    )
                                }
                            }
                            repeat(3 - rowSlots.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Bare command key, per the cross-screen instrument vocabulary: no fill, no angular frame and no
 * border at rest. The key is a 48dp target carrying only its label; a 1dp hairline under the target
 * separates it from the row below, and an accent tint marks the live Special/Move states.
 */
@Composable
private fun RowScope.BattleCommandKey(
    label: String,
    enabled: Boolean,
    height: Dp,
    accent: Boolean = false,
    subLabel: String? = null,
    contentDescription: String? = null,
    accentColor: Color = VitalCyan,
    onClick: () -> Unit,
) {
    val description = contentDescription ?: label
    val hairline = when {
        accent -> accentColor.copy(alpha = 0.85f)
        enabled -> SurfaceStroke.copy(alpha = 0.55f)
        else -> SurfaceStroke.copy(alpha = 0.28f)
    }
    val labelColor = when {
        accent -> accentColor
        enabled -> TextPrimaryOnDark
        else -> TextSecondaryOnDark.copy(alpha = 0.68f)
    }
    Column(
        Modifier
            .weight(1f)
            .testTag("offline-battle-command-slot")
            .semantics { this.contentDescription = description }
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = height)
                .clip(CutCornerShape(BattleKeyCorner))
                .clickable(
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick
                )
                .padding(horizontal = 4.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    label,
                    color = labelColor,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (accent) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 2,
                    textAlign = TextAlign.Center,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subLabel.isNullOrBlank()) {
                    Text(
                        subLabel,
                        color = TextSecondaryOnDark.copy(alpha = if (enabled) 0.9f else 0.55f),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(hairline)
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Floating state surfaces
// ---------------------------------------------------------------------------------------------

@Composable
fun BattleCountdownBanner(count: Int, modifier: Modifier = Modifier) {
    CyberPanel(
        modifier = modifier,
        active = true,
        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                stringResource(R.string.ui_battle_training_get_ready),
                color = VitalCyan,
                style = MaterialTheme.typography.labelLarge.hud(),
                fontWeight = FontWeight.Bold
            )
            Text(
                "$count",
                color = TextPrimaryOnDark,
                style = MaterialTheme.typography.displayLarge.hud(),
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
fun BattleLoadingBanner(text: String, modifier: Modifier = Modifier) {
    CyberPanel(
        modifier = modifier,
        active = true,
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text,
                color = TextPrimaryOnDark,
                style = MaterialTheme.typography.labelMedium.hud(),
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            LinearProgressIndicator(
                modifier = Modifier.width(180.dp).height(3.dp),
                color = VitalCyan,
                trackColor = SurfaceStroke.copy(alpha = 0.45f)
            )
        }
    }
}

@Composable
fun BattleInfoBanner(
    text: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CyberPanel(
        modifier = modifier,
        active = true,
        contentPadding = PaddingValues(16.dp)
    ) {
        Text(
            text,
            color = TextPrimaryOnDark,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        VitalButton(
            onClick = onAction,
            style = VitalButtonStyle.PRIMARY,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(actionLabel, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun BattleErrorBanner(
    error: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = BattleErrorSurface.copy(alpha = 0.96f),
        shape = RectangleShape,
        border = BorderStroke(1.dp, BattleErrorOutline)
    ) {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                stringResource(R.string.immersion_battle_unavailable),
                color = TextPrimaryOnDark,
                style = MaterialTheme.typography.bodySmall
            )
            com.tamerslab.components.DebugDetails(error)
            VitalButton(
                onClick = onRetry,
                style = VitalButtonStyle.SECONDARY,
                contentColor = TextPrimaryOnDark,
                modifier = Modifier.heightIn(min = 48.dp)
            ) { Text(stringResource(R.string.ui_battle_training_retry)) }
        }
    }
}

@Composable
fun BattlePausePanel(
    pauseReason: String?,
    tacticalPauseEnabled: Boolean,
    onResume: () -> Unit,
    onToggleTacticalPause: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val paneTitle = stringResource(R.string.ui_battle_training_paused_badge)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {},
        contentAlignment = Alignment.Center
    ) {
        CyberPanel(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .widthIn(max = 380.dp)
                .fillMaxWidth()
                .semantics { this.paneTitle = paneTitle },
            active = true,
            contentPadding = PaddingValues(18.dp)
        ) {
            Text(
                paneTitle,
                color = VitalCyan,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            pauseReason?.let {
                Text(
                    it,
                    color = TextSecondaryOnDark,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .toggleable(
                        value = tacticalPauseEnabled,
                        role = Role.Switch,
                        onValueChange = { onToggleTacticalPause() }
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    if (tacticalPauseEnabled) stringResource(R.string.ui_battle_tactical_pause_short_on)
                    else stringResource(R.string.ui_battle_tactical_pause_short_off),
                    modifier = Modifier.weight(1f),
                    color = TextPrimaryOnDark,
                    style = MaterialTheme.typography.bodyMedium
                )
                Switch(
                    checked = tacticalPauseEnabled,
                    onCheckedChange = null
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VitalButton(
                    onClick = onResume,
                    style = VitalButtonStyle.PRIMARY,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        stringResource(R.string.ui_battle_training_resume),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                VitalButton(
                    onClick = onExit,
                    style = VitalButtonStyle.SECONDARY,
                    contentColor = BattleDestructive,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        stringResource(R.string.ui_battle_training_exit),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Result
// ---------------------------------------------------------------------------------------------

private fun battleDurationLabel(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1_000L).coerceAtLeast(0L)
    return "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

@Composable
fun BattleResultPanel(
    @Suppress("UNUSED_PARAMETER") outcome: BattleOutcome,
    statistics: BattleStatistics,
    elapsedMillis: Long,
    outcomeText: String,
    onRematch: (() -> Unit)?,
    onReturn: () -> Unit,
    modifier: Modifier = Modifier,
    rewardContent: @Composable () -> Unit = {},
) {
    CyberPanel(
        modifier = modifier
            .widthIn(max = 460.dp)
            .fillMaxWidth()
            .heightIn(max = 540.dp),
        active = true,
        contentPadding = PaddingValues(16.dp)
    ) {
        Column(
            Modifier.weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    outcomeText,
                    color = TextPrimaryOnDark,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    stringResource(R.string.ui_battle_result_duration, battleDurationLabel(elapsedMillis)),
                    color = VitalCyan,
                    style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum")
                )
                Text(
                    stringResource(
                        R.string.ui_battle_result_damage,
                        statistics.damageDealt,
                        statistics.damageReceived,
                        statistics.damagePrevented
                    ),
                    color = TextSecondaryOnDark,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    stringResource(
                        R.string.ui_battle_result_stats,
                        statistics.healingDone,
                        statistics.ordersCompleted,
                        statistics.itemsUsed,
                        statistics.projectilesHit,
                        statistics.projectilesHit + statistics.projectilesMissed
                    ),
                    color = TextSecondaryOnDark,
                    style = MaterialTheme.typography.bodySmall
                )
                rewardContent()
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onRematch != null) {
                    VitalButton(
                        onClick = onRematch,
                        style = VitalButtonStyle.PRIMARY,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.ui_battle_rematch), fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
                VitalButton(
                    onClick = onReturn,
                    style = VitalButtonStyle.SECONDARY,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.ui_battle_back), maxLines = 1)
                }
            }
        }
    }
}
