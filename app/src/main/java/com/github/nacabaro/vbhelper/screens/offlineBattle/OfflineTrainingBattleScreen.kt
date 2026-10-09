package com.github.nacabaro.vbhelper.screens.offlineBattle

import android.graphics.Bitmap
import android.graphics.RectF
import android.content.pm.ApplicationInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import com.github.nacabaro.vbhelper.R
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.github.nacabaro.vbhelper.components.motionEnabled
import com.github.nacabaro.vbhelper.battle.offline.core.BattleEvent
import com.github.nacabaro.vbhelper.battle.offline.core.BattleItemDefinition
import com.github.nacabaro.vbhelper.battle.offline.core.BattleItemKind
import com.github.nacabaro.vbhelper.battle.offline.core.BattleOutcome
import com.github.nacabaro.vbhelper.battle.offline.core.BattleSnapshot
import com.github.nacabaro.vbhelper.battle.offline.core.BattleStrategy
import com.github.nacabaro.vbhelper.battle.offline.core.CombatantSnapshot
import com.github.nacabaro.vbhelper.battle.offline.core.TechniqueDefinition
import com.github.nacabaro.vbhelper.battle.offline.core.TechniqueKind
import com.github.nacabaro.vbhelper.battle.offline.core.attackSpriteVariantFor
import com.github.nacabaro.vbhelper.battle.offline.core.TrainerAction
import com.github.nacabaro.vbhelper.battle.offline.core.OrderStatus
import com.github.nacabaro.vbhelper.battle.offline.core.OrderUpdate
import com.github.nacabaro.vbhelper.battle.offline.core.OrderFailure
import com.github.nacabaro.vbhelper.battle.offline.data.TrainingBattleFactory
import com.github.nacabaro.vbhelper.battle.offline.data.BlastEvolutionRepository
import com.github.nacabaro.vbhelper.domain.device_data.BlastEvolutionSlot
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import com.github.nacabaro.vbhelper.ui.theme.DeepPurpleBgAlt
import com.github.nacabaro.vbhelper.ui.theme.SpaceBlack
import com.github.nacabaro.vbhelper.ui.theme.SurfaceElevatedPurple
import com.github.nacabaro.vbhelper.ui.theme.SurfaceStroke
import com.github.nacabaro.vbhelper.ui.theme.StatusRed
import com.github.nacabaro.vbhelper.ui.theme.TextPrimaryOnDark
import com.github.nacabaro.vbhelper.ui.theme.TextSecondaryOnDark
import com.github.nacabaro.vbhelper.ui.theme.VitalCyan
import com.github.nacabaro.vbhelper.ui.theme.OnVitalPrimary
import com.github.nacabaro.vbhelper.ui.theme.BattlePanel
import com.github.nacabaro.vbhelper.ui.theme.BattleBackdrop
import com.github.nacabaro.vbhelper.ui.theme.BattleErrorSurface
import com.github.nacabaro.vbhelper.ui.theme.BattleErrorOutline
import com.github.nacabaro.vbhelper.ui.theme.BattleErrorHint
import com.github.nacabaro.vbhelper.ui.theme.BattleDestructive
import com.github.nacabaro.vbhelper.ui.theme.BattleEnemyHealth
import com.github.nacabaro.vbhelper.ui.theme.BattleTrack
import com.github.nacabaro.vbhelper.ui.theme.SceneTextShadow
import com.github.nacabaro.vbhelper.ui.theme.VitalPurpleBright
import com.github.nacabaro.vbhelper.ui.theme.VitalYellow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.github.nacabaro.vbhelper.di.VBHelper

private enum class TrainingTacticalMenu { TECHNIQUES, ITEMS }

internal data class BattlePortraitPanels(
    val arenaHeightDp: Float,
    val deckHeightDp: Float,
    val gapHeightDp: Float
)

internal fun calculateBattlePortraitPanels(
    availableHeightDp: Float,
    minimumArenaHeightDp: Float = 180f,
    minimumDeckHeightDp: Float = 248f,
    preferredGapHeightDp: Float = 10f,
    cinematic: Boolean = false
): BattlePortraitPanels {
    val available = availableHeightDp.coerceAtLeast(0f)
    if (available == 0f) return BattlePortraitPanels(0f, 0f, 0f)

    val gap = minOf(available, preferredGapHeightDp.coerceAtLeast(0f))
    val contentHeight = (available - gap).coerceAtLeast(0f)

    if (cinematic) {
        val deck = minOf(contentHeight, 56f)
        return BattlePortraitPanels(contentHeight - deck, deck, gap)
    }

    val minimumTotal = minimumArenaHeightDp + minimumDeckHeightDp
    if (contentHeight < minimumTotal) {
        val deck = minOf(contentHeight, minimumDeckHeightDp)
        return BattlePortraitPanels(
            arenaHeightDp = contentHeight - deck,
            deckHeightDp = deck,
            gapHeightDp = gap
        )
    }

    val deck = maxOf(contentHeight / 2f, minimumDeckHeightDp)
    return BattlePortraitPanels(
        arenaHeightDp = contentHeight - deck,
        deckHeightDp = deck,
        gapHeightDp = gap
    )
}

@Composable
fun OfflineTrainingBattleScreen(
    viewModel: OfflineBattleSessionViewModel,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val debugToolsEnabled = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()
    val state by viewModel.state.collectAsState()
    val snapshot = state.snapshot
    val finisher = snapshot?.finisher
    val finisherActive = finisher != null
    val allowMotion = motionEnabled()
    val allies = snapshot?.alliedMembers.orEmpty()
    val opponents = snapshot?.opposingMembers.orEmpty()
    var selectedAllyId by rememberSaveable(state.sessionId) { mutableStateOf("") }
    var selectedOpponentId by rememberSaveable(state.sessionId) { mutableStateOf("") }
    var tacticalMenuPause by rememberSaveable(state.sessionId) { mutableStateOf(true) }
    var menu by rememberSaveable(state.sessionId) { mutableStateOf<TrainingTacticalMenu?>(null) }
    var showStrategyMenu by rememberSaveable(state.sessionId) { mutableStateOf(false) }
    var manualPause by rememberSaveable(state.sessionId) { mutableStateOf(false) }
    var commandFeedback by remember(state.sessionId) { mutableStateOf<String?>(null) }
    var feedbackRevision by remember { mutableIntStateOf(0) }
    var showBattleDebug by rememberSaveable(state.sessionId) { mutableStateOf(false) }
    var showExitDialog by rememberSaveable(state.sessionId) { mutableStateOf(false) }
    var sceneView by remember { mutableStateOf<OfflineBattleSceneView?>(null) }
    var rendererError by remember { mutableStateOf<String?>(null) }
    var sceneReady by remember(state.sessionId) { mutableStateOf(false) }
    var projectionRevision by remember { mutableIntStateOf(0) }
    var sceneGeneration by rememberSaveable(state.sessionId) { mutableIntStateOf(0) }
    var blastPresence by remember(state.sessionId) { mutableStateOf<Set<String>?>(null) }
    var blastOverlay by remember(state.sessionId) { mutableStateOf<BlastOverlay?>(null) }
    var blastDismissed by remember(state.sessionId) { mutableStateOf(setOf<String>()) }
    LaunchedEffect(state.sessionId) {
        blastPresence = runCatching {
            withContext(Dispatchers.IO) {
                val app = context.applicationContext
                val data = BlastEvolutionRepository.load(app)
                BlastEvolutionRepository.presentSet(
                    data, (app as VBHelper).container.db.speciesProfileDao().getPresentSpeciesNames())
            }
        }.getOrNull()
    }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val wideBattleLayout = isLandscape && configuration.screenWidthDp >= 480
            @Suppress("UNUSED_VARIABLE")
    val cameraFrameVersion = projectionRevision

    BattleFinisherFeedback(
        sessionId = state.sessionId,
        finisher = finisher,
        enabled = sceneReady && rendererError == null && state.error == null && state.countdown == 0 &&
            snapshot?.isPaused == false && !manualPause && !showExitDialog &&
            lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    )

    val selectedAlly = allies.firstOrNull { it.combatantId == selectedAllyId && it.health > 0 }
        ?: allies.firstOrNull { it.health > 0 } ?: allies.firstOrNull()
    val selectedOpponent = opponents.firstOrNull { it.combatantId == selectedOpponentId && it.health > 0 }
        ?: opponents.firstOrNull { it.health > 0 } ?: opponents.firstOrNull()
    LaunchedEffect(allies.map { it.combatantId }) {
        if (allies.none { it.combatantId == selectedAllyId }) selectedAllyId = allies.firstOrNull()?.combatantId.orEmpty()
    }
    LaunchedEffect(opponents.map { it.combatantId }) {
        if (opponents.none { it.combatantId == selectedOpponentId }) selectedOpponentId = opponents.firstOrNull()?.combatantId.orEmpty()
    }

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> viewModel.setPaused("background", true)
                Lifecycle.Event.ON_RESUME -> viewModel.setPaused("background", false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        viewModel.setPaused("background", !lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(state.sessionId) {
        viewModel.setPaused("scene", true)
        onDispose { viewModel.setPaused("scene", true) }
    }
    LaunchedEffect(state.sessionId, menu, tacticalMenuPause, showStrategyMenu, showExitDialog, manualPause, finisherActive) {
        viewModel.setPaused("tech-menu", !finisherActive && menu == TrainingTacticalMenu.TECHNIQUES && tacticalMenuPause)
        viewModel.setPaused("item-menu", !finisherActive && menu == TrainingTacticalMenu.ITEMS && tacticalMenuPause)
        viewModel.setPaused("strategy-menu", !finisherActive && showStrategyMenu && tacticalMenuPause)
        viewModel.setPaused("exit-dialog", showExitDialog)
        viewModel.setPaused("manual", manualPause)
    }
    LaunchedEffect(feedbackRevision) {
        delay(4000)
        commandFeedback = null
    }

    fun issue(actor: String, action: TrainerAction, interrupt: Boolean = false) {
        if (viewModel.state.value.snapshot?.finisher != null) return
        val result = viewModel.issueOrder(actor, action, interrupt) ?: return
        feedbackRevision++
        commandFeedback = if (result.status == OrderStatus.FAILED) orderFailureText(resources, result)
        else if (result.status == OrderStatus.QUEUED) resources.getString(R.string.ui_battle_order_queued)
        else resources.getString(R.string.ui_battle_command_applied)
    }

    fun closeBlastOverlay() {
        blastOverlay = null
        viewModel.setPaused("blast-menu", false)
    }

    fun blastToExecuting() {
        val ov = blastOverlay ?: return
        blastDismissed = blastDismissed + ov.window.key
        if (viewModel.state.value.snapshot?.finisher != null) {
            closeBlastOverlay()
            return
        }
        blastOverlay = ov.copy(executing = true)
        viewModel.setPaused("blast-menu", false)
    }

    fun blastHit() {
        if (viewModel.state.value.snapshot?.finisher != null) return
        val ov = blastOverlay ?: return
        val snap = snapshot
        val lead = snap?.alliedMembers?.find { it.combatantId == ov.window.combatantId }
        val fusion = if (lead != null && snap != null) {
            resolveTapFusion(context, lead, snap.alliedMembers, state.fighters, blastPresence)
        } else null
        issue(ov.window.combatantId, TrainerAction.ConfirmBlastTiming(fusion?.result, fusion?.special))
        blastToExecuting()
    }

    fun blastMiss() {
        if (blastOverlay == null) return
        blastToExecuting()
    }

    LaunchedEffect(state.sessionId, finisher?.sequenceId) {
        if (finisher == null) return@LaunchedEffect
        blastOverlay?.let { blastDismissed = blastDismissed + it.window.key }
        closeBlastOverlay()
        menu = null
        showStrategyMenu = false
        showBattleDebug = false
        commandFeedback = null
        viewModel.setPaused("tech-menu", false)
        viewModel.setPaused("item-menu", false)
        viewModel.setPaused("strategy-menu", false)
    }
    val blastCandidate = pickBlastWindow(snapshot, allies, selectedAlly)
    LaunchedEffect(state.sessionId, blastCandidate?.key, finisher?.sequenceId) {
        if (blastCandidate != null && blastOverlay == null && blastCandidate.key !in blastDismissed) {
            blastOverlay = BlastOverlay(blastCandidate, executing = false)
            viewModel.setPaused("blast-menu", true)
        }
    }
    LaunchedEffect(snapshot, blastOverlay) {
        val ov = blastOverlay ?: return@LaunchedEffect
        if (snapshot?.finisher != null) {
            closeBlastOverlay()
            return@LaunchedEffect
        }
        if (snapshot?.result != null) {
            blastDismissed = emptySet()
            closeBlastOverlay()
            return@LaunchedEffect
        }
        if (ov.executing) {
            val member = snapshot?.alliedMembers?.find { it.combatantId == ov.window.combatantId }
            if (member != null && member.activeTechniqueId == null) {
                closeBlastOverlay()
            }
        }
    }
    LaunchedEffect(blastOverlay?.let { it.window.key to it.executing }) {
        if (blastOverlay?.executing == true) {
            delay(12000)
            closeBlastOverlay()
        }
    }

    fun closeTacticalMenu() {
        when (menu) {
            TrainingTacticalMenu.TECHNIQUES -> if (tacticalMenuPause) viewModel.setPaused("tech-menu", false)
            TrainingTacticalMenu.ITEMS -> if (tacticalMenuPause) viewModel.setPaused("item-menu", false)
            null -> Unit
        }
        menu = null
    }

    fun openTacticalMenu(next: TrainingTacticalMenu) {
        if (viewModel.state.value.snapshot?.finisher != null) return
        closeTacticalMenu()
        menu = next
        if (tacticalMenuPause) {
            viewModel.setPaused(if (next == TrainingTacticalMenu.TECHNIQUES) "tech-menu" else "item-menu", true)
        }
    }

    fun requestExit() {
        closeTacticalMenu()
        showStrategyMenu = false
        viewModel.setPaused("strategy-menu", false)
        showExitDialog = true
        viewModel.setPaused("exit-dialog", true)
    }

    fun cancelExit() {
        showExitDialog = false
        viewModel.setPaused("exit-dialog", false)
    }

    BackHandler(enabled = true) {
        when {
            menu != null -> closeTacticalMenu()
            showStrategyMenu -> showStrategyMenu = false
            showExitDialog -> cancelExit()
            else -> requestExit()
        }
    }

    val fullScreenArena = snapshot != null && snapshot.result == null && sceneReady && state.countdown == 0
    Column(
        modifier = modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(if (fullScreenArena) 0.dp else 5.dp)
    ) {
        AnimatedVisibility(
            visible = !fullScreenArena,
            enter = slideInVertically(
                animationSpec = tween(if (allowMotion) 240 else 0, easing = FastOutSlowInEasing),
                initialOffsetY = { -it }
            ) + expandVertically(
                animationSpec = tween(if (allowMotion) 240 else 0, easing = FastOutSlowInEasing),
                expandFrom = Alignment.Top
            ) + fadeIn(tween(if (allowMotion) 160 else 0)),
            exit = slideOutVertically(
                animationSpec = tween(if (allowMotion) 240 else 0, easing = FastOutSlowInEasing),
                targetOffsetY = { -it }
            ) + shrinkVertically(
                animationSpec = tween(if (allowMotion) 240 else 0, easing = FastOutSlowInEasing),
                shrinkTowards = Alignment.Top
            ) + fadeOut(tween(if (allowMotion) 160 else 0))
        ) {
            Row(
                Modifier.fillMaxWidth().height(48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.ui_battle_training_title), color = TextPrimaryOnDark, fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        text = when {
                            wideBattleLayout && commandFeedback != null -> commandFeedback.orEmpty()
                            snapshot?.isPaused == true -> stringResource(R.string.ui_battle_training_paused, pauseReasonLabel(snapshot.pauseReason))
                            snapshot?.result != null -> stringResource(R.string.ui_battle_training_finished)
                            state.loading -> stringResource(R.string.ui_battle_training_preparing)
                            else -> stringResource(R.string.ui_battle_training_autonomous, formatDuration(snapshot?.elapsedMillis ?: 0))
                        },
                        color = if (snapshot?.isPaused == true) VitalCyan else TextSecondaryOnDark,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(
                        onClick = { sceneView?.resetCamera() },
                        enabled = sceneReady && !finisherActive,
                        modifier = Modifier.size(46.dp)
                            .border(1.dp, SurfaceStroke, CutCornerShape(6.dp))
                            .clip(CutCornerShape(6.dp))
                            .testTag("offline-battle-recenter-camera")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CenterFocusStrong,
                            contentDescription = stringResource(R.string.ui_battle_training_recenter_camera),
                            tint = TextPrimaryOnDark
                        )
                    }
                    if (debugToolsEnabled && snapshot != null) {
                        OutlinedButton(onClick = { showBattleDebug = true }, shape = CutCornerShape(6.dp),
                            border = BorderStroke(1.dp, VitalCyan), modifier = Modifier.size(width = 52.dp, height = 42.dp),
                            contentPadding = PaddingValues(0.dp)) {
                            Text(stringResource(R.string.ui_battle_training_ai_debug), color = VitalCyan)
                        }
                    }
                    OutlinedButton(onClick = ::requestExit, shape = CutCornerShape(6.dp),
                        border = BorderStroke(1.dp, SurfaceStroke), modifier = Modifier.size(width = 66.dp, height = 42.dp),
                        contentPadding = PaddingValues(0.dp)) {
                        Text(stringResource(R.string.ui_battle_training_exit), color = TextPrimaryOnDark)
                    }
                }
            }
        }

        val arenaContent: @Composable (Modifier) -> Unit = { arenaModifier ->
            Box(arenaModifier
                .border(2.dp, SurfaceStroke, MaterialTheme.shapes.medium)
                .clip(MaterialTheme.shapes.medium)
                .background(DeepPurpleBgAlt)
                .padding(2.dp)
                .clip(MaterialTheme.shapes.medium)
                .testTag("offline-battle-arena-viewport")) {
            key(sceneGeneration) {
                OfflineBattleScene(
                    snapshot = snapshot,
                    fighters = state.fighters,
                    manifest = state.arenaManifest,
                    sessionId = state.sessionId,
                    renderingEnabled = lifecycleState.isAtLeast(Lifecycle.State.RESUMED),
                    onReady = { sceneView = it },
                    onSceneReady = {
                        sceneReady = true
                        rendererError = null
                        viewModel.setPaused("scene", false)
                        viewModel.setPaused("renderer-error", false)
                    },
                    onProjectionChanged = { projectionRevision++ },
                    onFighterTapped = { fighterId ->
                        if (viewModel.state.value.snapshot?.finisher != null) return@OfflineBattleScene
                        if (allies.any { it.combatantId == fighterId }) selectedAllyId = fighterId
                        if (opponents.any { it.combatantId == fighterId }) selectedOpponentId = fighterId
                    },
                    onAssetError = {
                        sceneReady = false
                        rendererError = it
                        viewModel.setPaused("renderer-error", true)
                    },
                    modifier = Modifier.fillMaxSize(),
                    preparedForms = state.preparedForms,
                    allowMotion = allowMotion
                )
            }
            if (fullScreenArena && !finisherActive) {
                Row(
                    Modifier.align(Alignment.TopEnd).padding(5.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { sceneView?.resetCamera() },
                        modifier = Modifier.size(48.dp)
                            .border(1.dp, SurfaceStroke, CutCornerShape(5.dp))
                            .clip(CutCornerShape(5.dp))
                            .testTag("offline-battle-recenter-camera")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CenterFocusStrong,
                            contentDescription = stringResource(R.string.ui_battle_training_recenter_camera),
                            tint = TextPrimaryOnDark
                        )
                    }
                    if (debugToolsEnabled) {
                        OutlinedButton(
                            onClick = { showBattleDebug = true },
                            shape = CutCornerShape(5.dp),
                            border = BorderStroke(1.dp, VitalCyan),
                            modifier = Modifier.size(48.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) { Text(stringResource(R.string.ui_battle_training_ai_debug), color = VitalCyan) }
                    }
                    OutlinedButton(
                        onClick = ::requestExit,
                        shape = CutCornerShape(5.dp),
                        border = BorderStroke(1.dp, SurfaceStroke),
                        modifier = Modifier.size(width = 58.dp, height = 48.dp),
                        contentPadding = PaddingValues(horizontal = 3.dp)
                    ) { Text(stringResource(R.string.ui_battle_training_exit), color = TextPrimaryOnDark) }
                }
            }
            snapshot?.takeIf { sceneReady }?.let { current ->
                if (current.finisher == null) {
                    BattleVfxOverlay(current, state.fighters, sceneView)
                    BattleArenaStatusOverlay(
                        snapshot = current,
                        sceneView = sceneView,
                        fighterScale = state.arenaManifest?.fighterScale ?: 1.65f,
                        fighters = state.fighters,
                        selectedAlly = selectedAlly,
                        selectedOpponent = selectedOpponent
                    )
                }
                current.finisher?.let { movie ->
                    BattleFinisherOverlay(
                        finisher = movie,
                        fighters = state.fighters,
                        preparedForms = state.preparedForms,
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp)
                    )
                }
                blastOverlay?.takeIf { current.finisher == null }?.let { ov ->
                    val fighter = state.fighters[ov.window.combatantId]
                    val specialName = fighter?.specialDisplayName
                        ?: battleTechniqueName(ov.window.techniqueId, ov.window.techniqueId)
                    val form = current.alliedMembers.find { it.combatantId == ov.window.combatantId }?.blastFormSpecies
                    BlastTimingOverlay(
                        specialName = specialName,
                        attackerName = form?.let { "→ $it" }
                            ?: (fighter?.displayName ?: ov.window.combatantId.substringAfter(':')),
                        blastInfo = blastEquippedLine(
                            current.alliedMembers.find { it.combatantId == ov.window.combatantId }),
                        windowKey = ov.window.key,
                        executing = ov.executing,
                        inputEnabled = lifecycleState.isAtLeast(Lifecycle.State.RESUMED) && !manualPause && !showExitDialog &&
                            (!current.isPaused || current.pauseReason == "blast-menu"),
                        onHit = ::blastHit,
                        onMiss = ::blastMiss
                    )
                }
                if (current.finisher == null && current.isPaused && current.result == null && state.countdown == 0) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
                        color = BattlePanel.copy(alpha = 0xE5 / 255f), shape = CutCornerShape(6.dp),
                        border = BorderStroke(1.dp, VitalCyan.copy(alpha = 0.7f))
                    ) {
                        Text(stringResource(R.string.ui_battle_training_paused_badge), Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                            color = VitalCyan, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            if (sceneReady && state.countdown > 0 && snapshot?.result == null) {
                Surface(Modifier.align(Alignment.Center), color = BattleBackdrop.copy(alpha = 0xD0 / 255f), shape = CutCornerShape(8.dp)) {
                    Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.ui_battle_training_get_ready), color = TextPrimaryOnDark, style = MaterialTheme.typography.titleMedium)
                        Text("${state.countdown}", color = VitalCyan, style = MaterialTheme.typography.displayMedium)
                    }
                }
            }
            if (state.loading || (snapshot != null && !sceneReady && rendererError == null && state.error == null)) {
                Surface(Modifier.align(Alignment.Center), color = SurfaceElevatedPurple,
                    shape = CutCornerShape(8.dp), border = BorderStroke(1.dp, SurfaceStroke)) {
                    Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.ui_battle_training_loading), color = TextPrimaryOnDark,
                            fontWeight = FontWeight.SemiBold)
                        LinearProgressIndicator(color = VitalCyan, trackColor = DeepPurpleBgAlt)
                    }
                }
            }
            val error = rendererError ?: state.error
            if (snapshot == null && !state.loading && error == null) {
                Surface(Modifier.align(Alignment.Center), color = SurfaceElevatedPurple, shape = CutCornerShape(8.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.ui_battle_training_interrupted), color = TextPrimaryOnDark)
                        Button(onClick = { viewModel.finishSession(); onExit() }) { Text(stringResource(R.string.ui_battle_training_back_to_battles)) }
                    }
                }
            }
            if (error != null) {
                Surface(Modifier.align(Alignment.BottomCenter).padding(8.dp), color = BattleErrorSurface.copy(alpha = 0xF0 / 255f),
                    shape = CutCornerShape(6.dp), border = BorderStroke(1.dp, BattleErrorOutline)) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(error, color = TextPrimaryOnDark, style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(
                            onClick = {
                                if (state.error != null) viewModel.retry(context)
                                else if (sceneView != null) sceneView?.retryScene()
                                else sceneGeneration++
                            }
                        ) { Text(stringResource(R.string.ui_battle_training_retry)) }
                    }
                }
            }
            snapshot?.result?.let { result ->
                BattleResultPanel(
                    outcome = result.outcome,
                    statistics = result.statistics,
                    elapsedMillis = result.elapsedMillis,
                    outcomeText = trainingOutcomeText(result.outcome),
                    onRematch = { rendererError = null; viewModel.retry(context) },
                    onReturn = {
                        viewModel.finishSession()
                        onExit()
                    },
                    modifier = Modifier.align(Alignment.Center).padding(14.dp)
                )
            }
            }
        }

        val pauseEnabled = snapshot != null && snapshot.result == null && sceneReady &&
            rendererError == null && state.countdown == 0
        val commandsEnabled = pauseEnabled && !finisherActive
        val current = snapshot
        val teamContent: @Composable ColumnScope.() -> Unit = {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("offline-battle-touch-deck")
            ) {
                if (finisher != null) {
                    BattleFinisherStatusStrip(
                        kind = finisher.kind,
                        paused = current?.isPaused == true,
                        manuallyPaused = manualPause,
                        pauseEnabled = pauseEnabled,
                        onToggleManualPause = { manualPause = !manualPause },
                        onExit = ::requestExit,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    val fixedHeight = if (wideBattleLayout) 68.dp else 92.dp
                    val commandHeight = ((maxHeight - fixedHeight) / 3).coerceIn(48.dp, 84.dp)
                    Column(
                        Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        BattleCommandDeckHeader(
                            availableCommandPoints = ((current?.commandPoints ?: 0) -
                                (current?.reservedCommandPoints ?: 0)).coerceAtLeast(0),
                            maxCommandPoints = current?.maxCommandPoints ?: 0,
                            commandsEnabled = commandsEnabled,
                            manuallyPaused = manualPause,
                            tacticalMenuPause = tacticalMenuPause,
                            onToggleManualPause = { manualPause = !manualPause },
                            onToggleTacticalPause = { tacticalMenuPause = !tacticalMenuPause }
                        )
                        BattleCommandBar(
                            selectedAlly = selectedAlly,
                            selectedOpponent = selectedOpponent,
                            commandHeight = commandHeight,
                            availableCommandPoints = ((current?.commandPoints ?: 0) -
                                (current?.reservedCommandPoints ?: 0)).coerceAtLeast(0),
                            supportReady = current != null && !current.isPaused &&
                                selectedAlly?.combatantId?.let(current.pendingSupportCombatantIds::contains) == true,
                            commandsEnabled = commandsEnabled && blastOverlay == null,
                            onFocus = { ally, target -> issue(ally, TrainerAction.FocusTarget(target)) },
                            onDefend = { ally -> issue(ally, TrainerAction.Defend(), true) },
                            onSupport = { ally -> issue(ally, TrainerAction.Support) },
                            onMove = { ally, away ->
                                issue(ally, if (away) TrainerAction.KeepDistance else TrainerAction.MoveCloser, true)
                            },
                            onSpecial = { ally, target ->
                                selectedAlly?.specialTechniqueId?.let { techniqueId ->
                                    issue(ally, TrainerAction.UseTechnique(techniqueId, target))
                                }
                            },
                            onOpenTechniques = { openTacticalMenu(TrainingTacticalMenu.TECHNIQUES) },
                            onOpenItems = { openTacticalMenu(TrainingTacticalMenu.ITEMS) },
                            onOpenStrategies = {
                                if (viewModel.state.value.snapshot?.finisher == null) showStrategyMenu = true
                            }
                        )
                        if (!wideBattleLayout) {
                            Text(
                                text = commandFeedback ?: current?.let { eventSummary(it, state.fighters) }
                                    ?: if (state.loading) stringResource(R.string.ui_battle_preparing_arena) else stringResource(R.string.ui_battle_deck_hint),
                                modifier = Modifier.fillMaxWidth().heightIn(min = 18.dp, max = 18.dp)
                                    .padding(horizontal = 3.dp),
                                color = TextSecondaryOnDark,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val availableBattleHeight = maxHeight
            if (wideBattleLayout) {
                val panelWidth by animateDpAsState(
                    targetValue = if (finisherActive) (maxWidth * 0.26f).coerceIn(184.dp, 248.dp)
                        else (maxWidth * 0.44f).coerceIn(268.dp, 390.dp),
                    animationSpec = tween(if (allowMotion) 240 else 0), label = "finisher-command-width")
                val arenaWidth = (maxWidth - panelWidth - 12.dp).coerceAtLeast(150.dp)
                val arenaSize = minOf(arenaWidth, availableBattleHeight)
                Row(Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    arenaContent(Modifier.width(if (finisherActive) arenaWidth else arenaSize).height(arenaSize))
                    Column(
                        Modifier.width(panelWidth).height(if (finisherActive) minOf(56.dp, availableBattleHeight) else availableBattleHeight),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        content = teamContent
                    )
                }
            } else {
                val panels = calculateBattlePortraitPanels(availableBattleHeight.value, cinematic = finisherActive)
                val arenaHeight by animateDpAsState(panels.arenaHeightDp.dp,
                    tween(if (allowMotion) 240 else 0), label = "finisher-arena-height")
                val deckHeight by animateDpAsState(panels.deckHeightDp.dp,
                    tween(if (allowMotion) 240 else 0), label = "finisher-command-height")
                Column(Modifier.fillMaxSize().testTag("offline-battle-two-screen-layout")) {
                    Box(
                        Modifier.fillMaxWidth().height(arenaHeight),
                        contentAlignment = Alignment.Center
                    ) {
                        arenaContent(Modifier.fillMaxSize())
                    }
                    Box(Modifier.fillMaxWidth().height(panels.gapHeightDp.dp))
                    Column(
                        Modifier.fillMaxWidth().height(deckHeight),
                        content = teamContent
                    )
                }
            }
        }
    }

    if (!finisherActive && showBattleDebug && debugToolsEnabled && snapshot != null) {
        val members = snapshot.alliedMembers + snapshot.opposingMembers
        val names = members.associate { it.combatantId to it.displayName }
        val techniqueNames = TrainingBattleFactory.techniques.associate { it.techniqueId to it.displayName }
        AlertDialog(
            onDismissRequest = { showBattleDebug = false },
            title = { Text("Diagnóstico da IA · ${formatDuration(snapshot.elapsedMillis)}") },
            text = {
                Column(Modifier.heightIn(max = 540.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("CP ${snapshot.commandPoints}/${snapshot.maxCommandPoints} · reservado ${snapshot.reservedCommandPoints} · projéteis ${snapshot.projectiles.size} · eventos ${snapshot.eventCount}",
                         style = MaterialTheme.typography.bodySmall)
                    Text("Regras v${snapshot.rulesetVersion}", style = MaterialTheme.typography.bodySmall)
                    members.forEach { member ->
                        Surface(color = DeepPurpleBgAlt, shape = CutCornerShape(5.dp),
                            border = BorderStroke(1.dp, SurfaceStroke)) {
                            Column(Modifier.fillMaxWidth().padding(9.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("${member.displayName} · ${member.state.name} · ${member.strategy.name}",
                                    color = TextPrimaryOnDark, fontWeight = FontWeight.SemiBold)
                                Text("Alvo: ${member.targetId?.let { names[it] ?: it } ?: "nenhum"} · distância ${member.debug.targetDistance?.let { "%.2f".format(it) } ?: "—"}",
                                    color = TextSecondaryOnDark, style = MaterialTheme.typography.bodySmall)
                                Text("Decisão: ${member.debug.decision}", color = TextSecondaryOnDark,
                                    style = MaterialTheme.typography.bodySmall)
                                Text("Personalidade: ${member.debug.personalityType?.name ?: "FRIENDLY"} · núcleo ${member.debug.personalityCore?.name ?: "—"}",
                                    color = TextSecondaryOnDark, style = MaterialTheme.typography.bodySmall)
                                Text("Técnica: ${member.activeTechniqueId?.let { techniqueNames[it] ?: it } ?: "nenhuma"} · energia ${member.energy}/${member.maxEnergy} · reservada ${member.reservedEnergy}",
                                     color = TextSecondaryOnDark, style = MaterialTheme.typography.bodySmall)
                                Text("Prontidão ${member.debug.readiness.toInt()}/100 · necessária ${member.debug.readinessRequired.toInt()} · buffs ${member.debug.buffsRemaining} · contra-ataque ${if (member.debug.counterReady) "pronto" else "indisponível"}",
                                    color = TextSecondaryOnDark, style = MaterialTheme.typography.bodySmall)
                                Text("Perfil ${member.debug.encounterProfileId} · reposicionamentos ${member.debug.positioningReplans} · ${member.debug.targetReason}",
                                    color = TextSecondaryOnDark, style = MaterialTheme.typography.bodySmall)
                                if (member.debug.techniqueScores.isNotEmpty()) {
                                    Text("Pontuações: ${member.debug.techniqueScores.entries.joinToString { (id, score) -> "${techniqueNames[id] ?: id}=${score.toInt()}" }}",
                                        color = TextSecondaryOnDark, style = MaterialTheme.typography.bodySmall)
                                }
                                if (member.debug.techniqueScoreComponents.isNotEmpty()) {
                                    Text("Componentes: ${member.debug.techniqueScoreComponents.entries.joinToString(" · ") { (id, parts) ->
                                        "${techniqueNames[id] ?: id} {${parts.entries.joinToString { (name, value) -> "$name=${value.toInt()}" }}}"
                                    }}", color = TextSecondaryOnDark, style = MaterialTheme.typography.bodySmall)
                                }
                                Text("Ordem atual: ${member.currentOrderId ?: "—"} · fila: ${member.queuedOrderIds.joinToString().ifBlank { "—" }}",
                                    color = TextSecondaryOnDark, style = MaterialTheme.typography.bodySmall)
                                Text("Cooldowns: ${member.cooldownsMillis.entries.joinToString { (id, remaining) -> "${techniqueNames[id] ?: id} ${remaining}ms" }.ifBlank { "nenhum" }}",
                                    color = TextSecondaryOnDark, style = MaterialTheme.typography.bodySmall)
                                Text("Ameaça: ${member.debug.threatByCombatant.entries.joinToString { (id, value) -> "${names[id] ?: id}=$value" }.ifBlank { "nenhuma" }}",
                                    color = TextSecondaryOnDark, style = MaterialTheme.typography.bodySmall)
                                member.debug.lastOrderFailure?.let { reason ->
                                    Text("Última rejeição: $reason", color = BattleErrorHint,
                                        style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { OutlinedButton(onClick = { showBattleDebug = false }) { Text("Fechar") } }
        )
    }

    if (!finisherActive && showStrategyMenu && selectedAlly != null) {
        AlertDialog(
            onDismissRequest = { showStrategyMenu = false },
            title = { Text(stringResource(R.string.ui_battle_training_strategy_title, selectedAlly.displayName)) },
            text = {
                Column {
                    BattleStrategy.entries.forEach { strategy ->
                        DropdownMenuItem(
                            text = { Text(strategyLabel(strategy)) },
                            onClick = {
                                issue(selectedAlly.combatantId, TrainerAction.ChangeStrategy(strategy))
                                showStrategyMenu = false
                            }
                        )
                    }
                }
            },
            confirmButton = { Button(onClick = { showStrategyMenu = false }) { Text(stringResource(R.string.ui_battle_training_close)) } }
        )
    }

    when (if (finisherActive) null else menu) {
        TrainingTacticalMenu.TECHNIQUES -> TechniquesDialog(
            snapshot = snapshot,
            selectedAlly = selectedAlly,
            selectedOpponent = selectedOpponent,
            tacticalPauseEnabled = tacticalMenuPause,
            onTechnique = { technique, actor, target ->
                issue(actor, TrainerAction.UseTechnique(technique.techniqueId, target))
                closeTacticalMenu()
            },
            onDismiss = ::closeTacticalMenu
        )
        TrainingTacticalMenu.ITEMS -> ItemsDialog(
            snapshot = snapshot,
            selectedAlly = selectedAlly,
            tacticalPauseEnabled = tacticalMenuPause,
            onUseItem = { item, target ->
                issue(selectedAlly?.combatantId ?: return@ItemsDialog, TrainerAction.UseItem(item.itemId, target))
                closeTacticalMenu()
            },
            onDismiss = ::closeTacticalMenu
        )
        null -> Unit
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = ::cancelExit,
            title = { Text(stringResource(R.string.ui_battle_training_end_title)) },
            text = { Text(stringResource(R.string.ui_battle_training_end_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.abandon()
                        viewModel.finishSession()
                        onExit()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BattleDestructive)
                ) { Text(stringResource(R.string.ui_battle_training_end_confirm)) }
            },
            dismissButton = { OutlinedButton(onClick = ::cancelExit) { Text(stringResource(R.string.ui_battle_training_continue)) } }
        )
    }
}

/**
 * World variant of the battle HUD. The square Radar viewport is kept in place;
 * only its contents and the controls immediately below it are replaced.
 */
@Composable
fun WorldRadarBattleContent(
    viewModel: OfflineBattleSessionViewModel,
    battleActive: Boolean,
    onOutcome: (BattleOutcome) -> Unit,
    onExit: () -> Unit,
    radarViewport: @Composable () -> Unit,
    radarControls: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    rendererAllowed: Boolean = true,
    onRendererReleased: () -> Unit = {},
    onRendererCreated: () -> Unit = {},
    rewardContent: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()
    val state by viewModel.state.collectAsState()
    val snapshot = state.snapshot
    val finisher = snapshot?.finisher
    val finisherActive = finisher != null
    val allowMotion = motionEnabled()
    val allies = snapshot?.alliedMembers.orEmpty()
    val opponents = snapshot?.opposingMembers.orEmpty()
    var selectedAllyId by rememberSaveable(state.sessionId) { mutableStateOf("") }
    var selectedOpponentId by rememberSaveable(state.sessionId) { mutableStateOf("") }
    var menu by rememberSaveable(state.sessionId) { mutableStateOf<TrainingTacticalMenu?>(null) }
    var tacticalMenuPause by rememberSaveable(state.sessionId) { mutableStateOf(true) }
    var showStrategyMenu by rememberSaveable(state.sessionId) { mutableStateOf(false) }
    var showExitDialog by rememberSaveable(state.sessionId) { mutableStateOf(false) }
    var manualPause by rememberSaveable(state.sessionId) { mutableStateOf(false) }
    var sceneReady by remember(state.sessionId) { mutableStateOf(false) }
    var sceneView by remember(state.sessionId) { mutableStateOf<OfflineBattleSceneView?>(null) }
    var rendererError by remember(state.sessionId) { mutableStateOf<String?>(null) }
    var commandFeedback by remember(state.sessionId) { mutableStateOf<String?>(null) }
    var projectionRevision by remember(state.sessionId) { mutableIntStateOf(0) }
    var blastPresence by remember(state.sessionId) { mutableStateOf<Set<String>?>(null) }
    var blastOverlay by remember(state.sessionId) { mutableStateOf<BlastOverlay?>(null) }
    var blastDismissed by remember(state.sessionId) { mutableStateOf(setOf<String>()) }
    LaunchedEffect(state.sessionId) {
        blastPresence = runCatching {
            withContext(Dispatchers.IO) {
                val app = context.applicationContext
                val data = BlastEvolutionRepository.load(app)
                BlastEvolutionRepository.presentSet(
                    data, (app as VBHelper).container.db.speciesProfileDao().getPresentSpeciesNames())
            }
        }.getOrNull()
    }
    @Suppress("UNUSED_VARIABLE")
    val cameraFrameVersion = projectionRevision

    BattleFinisherFeedback(
        sessionId = state.sessionId,
        finisher = finisher,
        enabled = battleActive && rendererAllowed && sceneReady && rendererError == null && state.error == null &&
            state.countdown == 0 && snapshot?.isPaused == false && !manualPause && !showExitDialog &&
            lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    )

    val selectedAlly = allies.firstOrNull { it.combatantId == selectedAllyId && it.health > 0 }
        ?: allies.firstOrNull { it.health > 0 } ?: allies.firstOrNull()
    val selectedOpponent = opponents.firstOrNull { it.combatantId == selectedOpponentId && it.health > 0 }
        ?: opponents.firstOrNull { it.health > 0 } ?: opponents.firstOrNull()

    LaunchedEffect(allies.map { it.combatantId }) {
        if (allies.none { it.combatantId == selectedAllyId }) {
            selectedAllyId = allies.firstOrNull()?.combatantId.orEmpty()
        }
    }
    LaunchedEffect(opponents.map { it.combatantId }) {
        if (opponents.none { it.combatantId == selectedOpponentId }) {
            selectedOpponentId = opponents.firstOrNull()?.combatantId.orEmpty()
        }
    }
    LaunchedEffect(state.sessionId, snapshot?.result?.outcome) {
        snapshot?.result?.outcome?.let(onOutcome)
    }
    LaunchedEffect(state.sessionId, battleActive, menu, tacticalMenuPause, showStrategyMenu, showExitDialog, manualPause, finisherActive) {
        viewModel.setPaused("tech-menu", battleActive && !finisherActive && menu == TrainingTacticalMenu.TECHNIQUES && tacticalMenuPause)
        viewModel.setPaused("item-menu", battleActive && !finisherActive && menu == TrainingTacticalMenu.ITEMS && tacticalMenuPause)
        viewModel.setPaused("strategy-menu", battleActive && !finisherActive && showStrategyMenu && tacticalMenuPause)
        viewModel.setPaused("exit-dialog", battleActive && showExitDialog)
        viewModel.setPaused("manual", battleActive && manualPause)
    }
    LaunchedEffect(state.sessionId, battleActive, sceneReady) {
        if (!battleActive || !sceneReady) viewModel.setPaused("scene", true)
    }
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> viewModel.setPaused("background", true)
                Lifecycle.Event.ON_RESUME -> viewModel.setPaused("background", false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        viewModel.setPaused("background", !lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(state.sessionId) {
        viewModel.setPaused("scene", true)
        onDispose { viewModel.setPaused("scene", true) }
    }

    fun issue(actor: String, action: TrainerAction, interrupt: Boolean = false) {
        if (viewModel.state.value.snapshot?.finisher != null) return
        if(allies.firstOrNull { it.combatantId==actor }?.sourceCharacterId==null) {
            commandFeedback=resources.getString(com.github.nacabaro.vbhelper.R.string.ui_world_wild_autonomous)
            return
        }
        val update = viewModel.issueOrder(actor, action, interrupt) ?: return
        commandFeedback = if (update.status == OrderStatus.FAILED) orderFailureText(resources, update)
        else if (update.status == OrderStatus.QUEUED) {
            resources.getString(R.string.ui_battle_order_queued)
        } else {
            resources.getString(R.string.ui_battle_command_applied)
        }
    }

    fun closeMenu() {
        menu = null
        viewModel.setPaused("tech-menu", false)
        viewModel.setPaused("item-menu", false)
    }

    fun requestExit() {
        closeMenu()
        showStrategyMenu = false
        viewModel.setPaused("strategy-menu", false)
        showExitDialog = true
        viewModel.setPaused("exit-dialog", true)
    }

    fun cancelExit() {
        showExitDialog = false
        viewModel.setPaused("exit-dialog", false)
    }

    fun closeBlastOverlay() {
        blastOverlay = null
        viewModel.setPaused("blast-menu", false)
    }

    fun blastToExecuting() {
        val ov = blastOverlay ?: return
        blastDismissed = blastDismissed + ov.window.key
        if (viewModel.state.value.snapshot?.finisher != null) {
            closeBlastOverlay()
            return
        }
        blastOverlay = ov.copy(executing = true)
        viewModel.setPaused("blast-menu", false)
    }

    fun blastHit() {
        if (viewModel.state.value.snapshot?.finisher != null) return
        val ov = blastOverlay ?: return
        val snap = snapshot
        val lead = snap?.alliedMembers?.find { it.combatantId == ov.window.combatantId }
        val fusion = if (lead != null && snap != null) {
            resolveTapFusion(context, lead, snap.alliedMembers, state.fighters, blastPresence)
        } else null
        issue(ov.window.combatantId, TrainerAction.ConfirmBlastTiming(fusion?.result, fusion?.special))
        blastToExecuting()
    }

    fun blastMiss() {
        if (blastOverlay == null) return
        blastToExecuting()
    }

    LaunchedEffect(state.sessionId, finisher?.sequenceId) {
        if (finisher == null) return@LaunchedEffect
        blastOverlay?.let { blastDismissed = blastDismissed + it.window.key }
        closeBlastOverlay()
        closeMenu()
        showStrategyMenu = false
        commandFeedback = null
        viewModel.setPaused("strategy-menu", false)
    }
    val blastCandidate = pickBlastWindow(snapshot, allies, selectedAlly)
    LaunchedEffect(state.sessionId, blastCandidate?.key, finisher?.sequenceId) {
        if (blastCandidate != null && blastOverlay == null && blastCandidate.key !in blastDismissed) {
            blastOverlay = BlastOverlay(blastCandidate, executing = false)
            viewModel.setPaused("blast-menu", true)
        }
    }
    LaunchedEffect(snapshot, blastOverlay) {
        val ov = blastOverlay ?: return@LaunchedEffect
        if (snapshot?.finisher != null) {
            closeBlastOverlay()
            return@LaunchedEffect
        }
        if (snapshot?.result != null) {
            blastDismissed = emptySet()
            closeBlastOverlay()
            return@LaunchedEffect
        }
        if (ov.executing) {
            val member = snapshot?.alliedMembers?.find { it.combatantId == ov.window.combatantId }
            if (member != null && member.activeTechniqueId == null) {
                closeBlastOverlay()
            }
        }
    }
    LaunchedEffect(blastOverlay?.let { it.window.key to it.executing }) {
        if (blastOverlay?.executing == true) {
            delay(12000)
            closeBlastOverlay()
        }
    }

    BackHandler(enabled = battleActive) {
        when {
            menu != null -> closeMenu()
            showStrategyMenu -> showStrategyMenu = false
            showExitDialog -> cancelExit()
            snapshot?.result != null -> {
                viewModel.finishSession()
                onExit()
            }
            else -> requestExit()
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (battleActive) 0.dp else 5.dp)
    ) {
        AnimatedContent(
            targetState = battleActive,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .border(2.dp, SurfaceStroke, MaterialTheme.shapes.medium)
                .clip(MaterialTheme.shapes.medium)
                .testTag("world-radar-battle-viewport"),
            transitionSpec = {
                (fadeIn(tween(if (allowMotion) 380 else 0)) + scaleIn(tween(if (allowMotion) 380 else 0), initialScale = 0.98f)) togetherWith
                    (fadeOut(tween(if (allowMotion) 260 else 0)) + scaleOut(tween(if (allowMotion) 260 else 0), targetScale = 1.02f))
            },
            label = "world-radar-viewport-transform"
        ) { showBattle ->
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.medium)
                    .background(if (showBattle) DeepPurpleBgAlt else SpaceBlack)
                    .padding(2.dp)
            ) {
                if (showBattle) {
                    if (rendererAllowed) {
                    OfflineBattleScene(
                        snapshot = snapshot,
                        fighters = state.fighters,
                        manifest = state.arenaManifest,
                        sessionId = state.sessionId,
                        renderingEnabled = lifecycleState.isAtLeast(Lifecycle.State.RESUMED),
                        onReady = { sceneView = it },
                        onSceneReady = {
                            sceneReady = true
                            rendererError = null
                            viewModel.setPaused("scene", false)
                            viewModel.setPaused("renderer-error", false)
                        },
                        onProjectionChanged = { projectionRevision++ },
                        onFighterTapped = { fighterId ->
                            if (viewModel.state.value.snapshot?.finisher != null) return@OfflineBattleScene
                            if (allies.any { it.combatantId == fighterId }) selectedAllyId = fighterId
                            if (opponents.any { it.combatantId == fighterId }) selectedOpponentId = fighterId
                        },
                        onAssetError = {
                            sceneReady = false
                            rendererError = it
                            viewModel.setPaused("renderer-error", true)
                        },
                        modifier = Modifier.fillMaxSize(),
                        onReleased = onRendererReleased,
                        onCreated = onRendererCreated,
                        preparedForms = state.preparedForms,
                        allowMotion = allowMotion
                    )
                    }
                    snapshot?.takeIf { sceneReady }?.let { current ->
                        if (current.finisher == null) {
                            BattleVfxOverlay(
                                snapshot = current,
                                fighters = state.fighters,
                                sceneView = sceneView
                            )
                            BattleArenaStatusOverlay(
                                snapshot = current,
                                sceneView = sceneView,
                                fighterScale = state.arenaManifest?.fighterScale ?: 1.65f,
                                fighters = state.fighters,
                                selectedAlly = selectedAlly,
                                selectedOpponent = selectedOpponent
                            )
                        }
                        current.finisher?.let { movie ->
                            BattleFinisherOverlay(
                                finisher = movie,
                                fighters = state.fighters,
                                preparedForms = state.preparedForms,
                                modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp)
                            )
                        }
                        blastOverlay?.takeIf { current.finisher == null }?.let { ov ->
                            val fighter = state.fighters[ov.window.combatantId]
                            val specialName = fighter?.specialDisplayName
                                ?: battleTechniqueName(ov.window.techniqueId, ov.window.techniqueId)
                            val form = snapshot?.alliedMembers?.find { it.combatantId == ov.window.combatantId }?.blastFormSpecies
                            BlastTimingOverlay(
                                specialName = specialName,
                                attackerName = form?.let { "→ $it" }
                                    ?: (fighter?.displayName ?: ov.window.combatantId.substringAfter(':')),
                                blastInfo = blastEquippedLine(
                                    snapshot?.alliedMembers?.find { it.combatantId == ov.window.combatantId }),
                                windowKey = ov.window.key,
                                executing = ov.executing,
                                inputEnabled = battleActive && lifecycleState.isAtLeast(Lifecycle.State.RESUMED) &&
                                    !manualPause && !showExitDialog && (!current.isPaused || current.pauseReason == "blast-menu"),
                                onHit = ::blastHit,
                                onMiss = ::blastMiss
                            )
                        }
                    }

                    if (state.loading || (snapshot != null && !sceneReady && rendererError == null)) {
                        Surface(
                            modifier = Modifier.align(Alignment.Center),
                            color = SurfaceElevatedPurple,
                            shape = CutCornerShape(8.dp),
                            border = BorderStroke(1.dp, SurfaceStroke)
                        ) {
                            Column(
                                Modifier.padding(18.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(stringResource(R.string.ui_battle_radar_transforming), color = TextPrimaryOnDark)
                                LinearProgressIndicator(color = VitalCyan, trackColor = DeepPurpleBgAlt)
                            }
                        }
                    }
                    if (sceneReady && state.countdown > 0 && snapshot?.result == null) {
                        Surface(
                            Modifier.align(Alignment.Center),
                            color = BattleBackdrop.copy(alpha = 0xD0 / 255f),
                            shape = CutCornerShape(8.dp)
                        ) {
                            Column(
                                Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(stringResource(R.string.ui_battle_training_get_ready), color = TextPrimaryOnDark)
                                Text("${state.countdown}", color = VitalCyan, style = MaterialTheme.typography.displayMedium)
                            }
                        }
                    }
                    (rendererError ?: state.error)?.let { error ->
                        Surface(
                            Modifier.align(Alignment.BottomCenter).padding(8.dp),
                            color = BattleErrorSurface.copy(alpha = 0xF0 / 255f),
                            shape = CutCornerShape(6.dp),
                            border = BorderStroke(1.dp, BattleErrorOutline)
                        ) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(error, color = TextPrimaryOnDark, style = MaterialTheme.typography.bodySmall)
                                OutlinedButton(onClick = {
                                    if (state.error != null) viewModel.retry(context, preserveEncounter = true) else sceneView?.retryScene()
                                }) { Text(stringResource(R.string.ui_battle_training_retry)) }
                            }
                        }
                    }
                    snapshot?.result?.let { result ->
                        BattleResultPanel(
                            outcome = result.outcome,
                            statistics = result.statistics,
                            elapsedMillis = result.elapsedMillis,
                            outcomeText = radarOutcomeText(result.outcome),
                            rewardContent = rewardContent,
                            onRematch = null,
                            onReturn = {
                                viewModel.finishSession()
                                onExit()
                            },
                            modifier = Modifier.align(Alignment.Center).padding(14.dp)
                        )
                    }
                } else {
                    radarViewport()
                }
            }
        }

        if (battleActive) {
            val worldPauseEnabled = snapshot != null && snapshot.result == null && sceneReady &&
                rendererError == null && state.countdown == 0
            val worldCommandsEnabled = worldPauseEnabled && !finisherActive
            Box(Modifier.fillMaxWidth().height(if (finisherActive) 4.dp else 12.dp))
            Column(
                Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState())
                    .testTag("world-radar-command-deck"),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (finisher != null) {
                    BattleFinisherStatusStrip(
                        kind = finisher.kind,
                        paused = snapshot?.isPaused == true,
                        manuallyPaused = manualPause,
                        pauseEnabled = worldPauseEnabled,
                        onToggleManualPause = { manualPause = !manualPause },
                        onExit = ::requestExit
                    )
                } else {
                    BattleCommandDeckHeader(
                        availableCommandPoints = ((snapshot?.commandPoints ?: 0) -
                            (snapshot?.reservedCommandPoints ?: 0)).coerceAtLeast(0),
                        maxCommandPoints = snapshot?.maxCommandPoints ?: 0,
                        commandsEnabled = worldPauseEnabled,
                        manuallyPaused = manualPause,
                        tacticalMenuPause = tacticalMenuPause,
                        onToggleManualPause = { manualPause = !manualPause },
                        onToggleTacticalPause = { tacticalMenuPause = !tacticalMenuPause }
                    )
                    BattleCommandBar(
                        selectedAlly = selectedAlly,
                        selectedOpponent = selectedOpponent,
                        availableCommandPoints = ((snapshot?.commandPoints ?: 0) -
                            (snapshot?.reservedCommandPoints ?: 0)).coerceAtLeast(0),
                        supportReady = snapshot != null && !snapshot.isPaused &&
                            selectedAlly?.combatantId?.let(snapshot.pendingSupportCombatantIds::contains) == true,
                        commandsEnabled = worldCommandsEnabled && blastOverlay == null,
                        onFocus = { ally, target -> issue(ally, TrainerAction.FocusTarget(target)) },
                        onDefend = { ally -> issue(ally, TrainerAction.Defend(), true) },
                        onSupport = { ally -> issue(ally, TrainerAction.Support) },
                        onMove = { ally, away ->
                            issue(ally, if (away) TrainerAction.KeepDistance else TrainerAction.MoveCloser, true)
                        },
                        onSpecial = { ally, target ->
                            selectedAlly?.specialTechniqueId?.let { techniqueId ->
                                issue(ally, TrainerAction.UseTechnique(techniqueId, target))
                            }
                        },
                        onOpenTechniques = {
                            if (viewModel.state.value.snapshot?.finisher == null) {
                                menu = TrainingTacticalMenu.TECHNIQUES
                                if (tacticalMenuPause) viewModel.setPaused("tech-menu", true)
                            }
                        },
                        onOpenItems = {
                            if (viewModel.state.value.snapshot?.finisher == null) {
                                menu = TrainingTacticalMenu.ITEMS
                                if (tacticalMenuPause) viewModel.setPaused("item-menu", true)
                            }
                        },
                        onOpenStrategies = {
                            if (viewModel.state.value.snapshot?.finisher == null) showStrategyMenu = true
                        }
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = commandFeedback ?: snapshot?.let { eventSummary(it, state.fighters) }
                                ?: resources.getString(R.string.ui_battle_preparing),
                            color = TextSecondaryOnDark,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(onClick = ::requestExit, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.ui_battle_training_exit))
                        }
                    }
                }
            }
        } else {
            radarControls()
        }
    }

    if (battleActive && !finisherActive && showStrategyMenu && selectedAlly != null) {
        AlertDialog(
            onDismissRequest = { showStrategyMenu = false },
            title = { Text(stringResource(R.string.ui_battle_training_strategy_title, selectedAlly.displayName)) },
            text = {
                Column {
                    BattleStrategy.entries.forEach { strategy ->
                        DropdownMenuItem(
                            text = { Text(strategyLabel(strategy)) },
                            onClick = {
                                issue(selectedAlly.combatantId, TrainerAction.ChangeStrategy(strategy))
                                showStrategyMenu = false
                            }
                        )
                    }
                }
            },
            confirmButton = { OutlinedButton(onClick = { showStrategyMenu = false }) { Text(stringResource(R.string.ui_battle_training_close)) } }
        )
    }

    when (if (battleActive && !finisherActive) menu else null) {
        TrainingTacticalMenu.TECHNIQUES -> TechniquesDialog(
            snapshot = snapshot,
            selectedAlly = selectedAlly,
            selectedOpponent = selectedOpponent,
            tacticalPauseEnabled = tacticalMenuPause,
            onTechnique = { technique, actor, target ->
                issue(actor, TrainerAction.UseTechnique(technique.techniqueId, target))
                closeMenu()
            },
            onDismiss = ::closeMenu
        )
        TrainingTacticalMenu.ITEMS -> ItemsDialog(
            snapshot = snapshot,
            selectedAlly = selectedAlly,
            tacticalPauseEnabled = tacticalMenuPause,
            onUseItem = { item, target ->
                issue(selectedAlly?.combatantId ?: return@ItemsDialog, TrainerAction.UseItem(item.itemId, target))
                closeMenu()
            },
            onDismiss = ::closeMenu
        )
        null -> Unit
    }

    if (battleActive && showExitDialog) {
        AlertDialog(
            onDismissRequest = ::cancelExit,
            title = { Text(stringResource(R.string.ui_battle_radar_exit_title)) },
            text = { Text(stringResource(R.string.ui_battle_radar_exit_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.abandon()
                        viewModel.finishSession()
                        onExit()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BattleDestructive)
                ) { Text(stringResource(R.string.ui_battle_radar_exit_confirm)) }
            },
            dismissButton = { OutlinedButton(onClick = ::cancelExit) { Text(stringResource(R.string.ui_battle_training_continue)) } }
        )
    }
}

@Composable
private fun BattleVfxOverlay(
    snapshot: BattleSnapshot,
    fighters: Map<String, BattleFighterPresentation>,
    sceneView: OfflineBattleSceneView?
) {
    val density = LocalDensity.current
    val allowMotion = motionEnabled()
    val chargePhase = if (allowMotion) {
        (snapshot.elapsedMillis % STARTUP_EFFECT_CYCLE_MILLIS) / STARTUP_EFFECT_CYCLE_MILLIS.toFloat()
    } else 0.45f
    val newestMiss = latestBattleMissCue(snapshot)
    var visibleMiss by remember { mutableStateOf<BattleMissCue?>(null) }
    val missProgress = remember { Animatable(1f) }
    LaunchedEffect(newestMiss?.eventId) {
        val cue = newestMiss ?: run {
            visibleMiss = null
            return@LaunchedEffect
        }
        visibleMiss = cue
        if (allowMotion) {
            missProgress.snapTo(0f)
            missProgress.animateTo(1f, tween(MISS_EFFECT_DURATION_MILLIS, easing = FastOutSlowInEasing))
        } else {
            missProgress.snapTo(0.55f)
            delay(MISS_EFFECT_REDUCED_MOTION_MILLIS)
        }
        if (visibleMiss?.eventId == cue.eventId) visibleMiss = null
    }
    Box(Modifier.fillMaxSize()) {
        val combatantsById = (snapshot.alliedMembers + snapshot.opposingMembers)
            .associateBy { it.combatantId }

        combatantsById.values.filter {
            it.activeTechniqueId != null &&
                (it.state == com.github.nacabaro.vbhelper.battle.offline.core.CombatantState.ATTACK_STARTUP ||
                    it.state == com.github.nacabaro.vbhelper.battle.offline.core.CombatantState.USING_SPECIAL)
        }.forEach { fighter ->
            val point = sceneView?.projectBattlePosition(
                fighter.position.x,
                STARTUP_EFFECT_HEIGHT,
                fighter.position.z
            )
            if (point != null) {
                AttackStartupEffect(
                    x = with(density) { point.first.toDp() } - 56.dp,
                    y = with(density) { point.second.toDp() } - 56.dp,
                    phase = chargePhase,
                    special = fighter.state ==
                        com.github.nacabaro.vbhelper.battle.offline.core.CombatantState.USING_SPECIAL,
                    fighterId = fighter.combatantId
                )
            }
        }

        (snapshot.alliedMembers + snapshot.opposingMembers)
            .filter { it.state == com.github.nacabaro.vbhelper.battle.offline.core.CombatantState.ATTACK_ACTIVE }
            .forEach { attacker ->
                val kind = attacker.activeTechniqueKind ?: return@forEach
                if (kind == TechniqueKind.PROJECTILE) return@forEach
                val variant = attackSpriteVariantFor(kind) ?: return@forEach
                val target = attacker.targetId?.let(combatantsById::get) ?: return@forEach
                val techniqueId = attacker.activeTechniqueId ?: return@forEach
                val bitmap = fighters[attacker.combatantId]?.attackVisuals?.get(variant) ?: return@forEach
                val startEventIndex = snapshot.recentEvents.indexOfLast { event ->
                    event is BattleEvent.TechniqueStarted && event.combatantId == attacker.combatantId &&
                        event.techniqueId == techniqueId
                }
                if (startEventIndex < 0) return@forEach
                val eventId = (snapshot.eventCount - (snapshot.recentEvents.lastIndex - startEventIndex))
                    .coerceAtLeast(0L)
                val start = sceneView?.projectBattlePosition(
                    attacker.position.x, PROJECTILE_HEIGHT, attacker.position.z
                ) ?: return@forEach
                val end = sceneView.projectBattlePosition(
                    target.position.x, PROJECTILE_HEIGHT, target.position.z
                ) ?: return@forEach
                key(eventId) {
                    AttackSpriteTravelOverlay(
                        bitmap = bitmap,
                        start = Offset(start.first, start.second),
                        end = Offset(end.first, end.second),
                        eventId = eventId,
                        size = if (variant == "large") 36.dp else 24.dp,
                    )
                }
            }

        snapshot.projectiles.forEach { projectile ->
            val point = sceneView?.projectBattlePosition(
                projectile.position.x, PROJECTILE_HEIGHT, projectile.position.z
            )
            val image = fighters[projectile.ownerId]?.attackVisuals?.get(projectile.visual ?: "small")
            if (point != null && image != null) {
                val size = if (projectile.visual == "large") 36.dp else 24.dp
                val next = sceneView.projectBattlePosition(
                    projectile.position.x + projectile.velocityX * 0.05f,
                    PROJECTILE_HEIGHT,
                    projectile.position.z + projectile.velocityZ * 0.05f
                )
                ProjectileImage(
                    bitmap = image,
                    x = with(density) { point.first.toDp() } - size / 2,
                    y = with(density) { point.second.toDp() } - size / 2,
                    size = size,
                    rotation = next?.let {
                        Math.toDegrees(
                            kotlin.math.atan2(
                                (it.second - point.second).toDouble(),
                                (it.first - point.first).toDouble()
                            )
                        ).toFloat()
                    } ?: 0f
                )
            }
        }

        snapshot.impacts.forEach { impact ->
            val target = combatantsById[impact.targetId] ?: return@forEach
            val targetScale = fighters[impact.targetId]?.visualScaleMultiplier ?: 1f
            val point = sceneView?.projectBattlePosition(
                target.position.x, DAMAGE_NUMBER_HEIGHT * targetScale, target.position.z
            )
            if (point != null) {
                val outward = if (point.first < sceneView.width / 2f) -1 else 1
                DamageNumberOverlay(
                    damage = impact.damage,
                    critical = impact.critical,
                    remainingMillis = impact.remainingMillis,
                    x = with(density) { point.first.toDp() } - (outward * DAMAGE_NUMBER_OFFSET_X).dp - 60.dp,
                    y = with(density) { point.second.toDp() } - 26.dp - DAMAGE_NUMBER_OFFSET_Y.dp,
                    targetId = impact.targetId,
                    special = impact.isSpecial
                )
            }
        }

        visibleMiss?.let { cue ->
            val anchor = combatantsById[cue.anchorCombatantId]
            val point = anchor?.let {
                sceneView?.projectBattlePosition(it.position.x, MISS_EFFECT_HEIGHT, it.position.z)
            }
            if (point != null) {
                AttackMissEffect(
                    x = with(density) { point.first.toDp() } - 56.dp,
                    y = with(density) { point.second.toDp() } - 56.dp,
                    progress = missProgress.value,
                    eventId = cue.eventId,
                    special = cue.isSpecial
                )
            }
        }
    }
}

@Composable
private fun AttackStartupEffect(
    x: androidx.compose.ui.unit.Dp,
    y: androidx.compose.ui.unit.Dp,
    phase: Float,
    special: Boolean,
    fighterId: String
) {
    val signal = if (special) VitalYellow else VitalCyan
    val accentColor = VitalCyan
    Canvas(
        Modifier.offset(x, y).size(112.dp)
            .testTag("battle-startup-effect-$fighterId")
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val pulse = 0.5f - 0.5f * cos(phase * 2f * PI.toFloat())
        val radius = size.minDimension * (0.405f + pulse * 0.018f)
        val bounds = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f)
        val topLeft = Offset(center.x - radius, center.y - radius)
        val segmentCount = if (special) 6 else 4
        repeat(segmentCount) { index ->
            val angle = phase * 360f + index * (360f / segmentCount)
            val sweep = if (special) 36f else 48f
            drawArc(
                color = signal.copy(
                    alpha = if (special) 0.72f + pulse * 0.2f else 0.28f + pulse * 0.12f
                ),
                startAngle = angle,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = bounds,
                style = Stroke(width = if (special) 2.4.dp.toPx() else 2.dp.toPx())
            )
            val radians = angle * PI.toFloat() / 180f
            val inner = radius - 3.dp.toPx()
            val outer = radius + 5.dp.toPx()
            drawLine(
                color = Color.White.copy(alpha = 0.68f),
                start = Offset(center.x + cos(radians) * inner, center.y + sin(radians) * inner),
                end = Offset(center.x + cos(radians) * outer, center.y + sin(radians) * outer),
                strokeWidth = 1.5.dp.toPx()
            )
        }
        if (special) {
            val innerRadius = radius * 0.83f
            repeat(4) { index ->
                val angle = phase * -220f + 45f + index * 90f
                drawArc(
                    color = accentColor.copy(alpha = 0.3f),
                    startAngle = angle,
                    sweepAngle = 22f,
                    useCenter = false,
                    topLeft = Offset(center.x - innerRadius, center.y - innerRadius),
                    size = androidx.compose.ui.geometry.Size(innerRadius * 2f, innerRadius * 2f),
                    style = Stroke(width = 1.4.dp.toPx())
                )
            }
        }
    }
}

@Composable
private fun AttackMissEffect(
    x: androidx.compose.ui.unit.Dp,
    y: androidx.compose.ui.unit.Dp,
    progress: Float,
    eventId: Long,
    special: Boolean
) {
    val allowMotion = motionEnabled()
    val errorColor = StatusRed
    val specialColor = VitalYellow
    Canvas(
        Modifier.offset(x, y).size(112.dp)
            .graphicsLayer {
                alpha = if (progress < 0.72f) 1f else ((1f - progress) / 0.28f).coerceIn(0f, 1f)
                rotationZ = if (allowMotion) progress * 7f else 0f
            }
            .testTag("battle-miss-effect-$eventId")
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension * (0.36f + progress * 0.11f)
        val bounds = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f)
        val topLeft = Offset(center.x - radius, center.y - radius)
        repeat(3) { segment ->
            val angle = segment * 120f + progress * 18f
            drawArc(
                color = errorColor.copy(alpha = 0.88f * (1f - progress * 0.25f)),
                startAngle = angle,
                sweepAngle = 38f,
                useCenter = false,
                topLeft = topLeft,
                size = bounds,
                style = Stroke(width = 2.6.dp.toPx())
            )
            val radians = (angle + 38f) * PI.toFloat() / 180f
            val inner = radius - 3.dp.toPx()
            val outer = radius + 4.dp.toPx()
            drawLine(
                color = Color.White.copy(alpha = (1f - progress) * 0.7f),
                start = Offset(center.x + cos(radians) * inner, center.y + sin(radians) * inner),
                end = Offset(center.x + cos(radians) * outer, center.y + sin(radians) * outer),
                strokeWidth = 1.4.dp.toPx()
            )
        }
        repeat(if (special) 6 else 3) { index ->
            val angle = (index * (if (special) 60f else 120f) + 24f) * PI.toFloat() / 180f
            val distance = radius + size.minDimension * (0.03f + progress * 0.11f)
            val centerPoint = Offset(center.x + cos(angle) * distance, center.y + sin(angle) * distance)
            val side = if (index % 2 == 0) 3.dp.toPx() else 2.dp.toPx()
            drawRect(
                color = (if (special && index % 2 == 0) specialColor else errorColor)
                    .copy(alpha = (1f - progress) * 0.85f),
                topLeft = Offset(centerPoint.x - side / 2f, centerPoint.y - side / 2f),
                size = androidx.compose.ui.geometry.Size(side, side)
            )
        }
    }
}

@Composable
private fun DamageNumberOverlay(
    damage: Int,
    critical: Boolean,
    remainingMillis: Long,
    x: androidx.compose.ui.unit.Dp,
    y: androidx.compose.ui.unit.Dp,
    targetId: String,
    special: Boolean
) {
    val progress = (1f - remainingMillis / HIT_EFFECT_DURATION_MILLIS.toFloat()).coerceIn(0f, 1f)
    val fade = if (progress < 0.7f) 1f else ((1f - progress) / 0.3f).coerceIn(0f, 1f)
    Box(
        modifier = Modifier.offset(x, y).size(120.dp, 60.dp)
            .graphicsLayer { alpha = fade }
            .testTag("battle-damage-number-$targetId"),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$damage",
            color = if (special || critical) VitalYellow else Color.White,
            fontWeight = FontWeight.Black,
            fontSize = if (critical) 21.sp else 18.sp,
            style = MaterialTheme.typography.titleMedium.copy(
                shadow = Shadow(Color.Black, Offset(0f, 3f), 6f)
            ),
            modifier = Modifier.graphicsLayer {
                translationY = -22f * progress
                scaleX = 1f + (1f - progress) * 0.25f
                scaleY = scaleX
            }
        )
    }
}

@Composable
private fun ProjectileImage(bitmap: Bitmap, x: androidx.compose.ui.unit.Dp, y: androidx.compose.ui.unit.Dp, size: androidx.compose.ui.unit.Dp, rotation: Float) {
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = stringResource(R.string.ui_battle_projectile_desc),
        modifier = Modifier.offset(x, y).size(size).graphicsLayer {
            rotationZ = rotation
            scaleX = -1f
        }
    )
}

@Composable
private fun AttackSpriteTravelOverlay(
    bitmap: Bitmap,
    start: Offset,
    end: Offset,
    eventId: Long,
    size: androidx.compose.ui.unit.Dp,
) {
    val density = LocalDensity.current
    val travel = remember(eventId) { Animatable(0f) }
    val allowMotion = motionEnabled()
    LaunchedEffect(eventId, allowMotion) {
        travel.snapTo(0f)
        if (allowMotion) {
            travel.animateTo(1f, tween(140, easing = LinearEasing))
        } else {
            travel.snapTo(1f)
        }
    }
    val x = start.x + (end.x - start.x) * travel.value
    val y = start.y + (end.y - start.y) * travel.value
    val rotation = Math.toDegrees(
        kotlin.math.atan2((end.y - start.y).toDouble(), (end.x - start.x).toDouble())
    ).toFloat()
    ProjectileImage(
        bitmap = bitmap,
        x = with(density) { x.toDp() } - size / 2,
        y = with(density) { y.toDp() } - size / 2,
        size = size,
        rotation = rotation,
    )
}

@Composable
private fun BoxScope.BattleArenaStatusOverlay(
    snapshot: BattleSnapshot,
    sceneView: OfflineBattleSceneView?,
    fighterScale: Float,
    fighters: Map<String, BattleFighterPresentation>,
    selectedAlly: CombatantSnapshot?,
    selectedOpponent: CombatantSnapshot?
) {
    val density = LocalDensity.current
    val labels = mutableListOf<Pair<Float, Float>>()
    val labelWidth = with(density) { 66.dp.toPx() }
    val labelHeight = with(density) { 9.dp.toPx() }
    val pauseStatusBottom = with(density) { 44.dp.toPx() }
    val labelGap = with(density) { 4.dp.toPx() }
    val bodyBounds = (snapshot.alliedMembers + snapshot.opposingMembers).mapNotNull { member ->
        val visualScale = fighters[member.combatantId]?.visualScaleMultiplier ?: 1f
        val foot = sceneView?.projectBattlePosition(member.position.x, 0f, member.position.z)
        val head = sceneView?.projectBattlePosition(
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
        val visualScale = fighters[fighter.combatantId]?.visualScaleMultiplier ?: 1f
        val point = sceneView?.projectBattlePosition(
            fighter.position.x,
            LABEL_HEIGHT * visualScale,
            fighter.position.z,
        )
        if (point != null) {
            val centeredX = point.first - labelWidth / 2
            val aboveY = point.second - labelHeight
            val placement = listOf(
                centeredX to aboveY,
                (point.first + labelGap) to aboveY,
                (point.first - labelWidth - labelGap) to aboveY,
                centeredX to (aboveY - labelHeight - labelGap)
            ).firstOrNull { (x, y) ->
                val rectangle = RectF(x, y, x + labelWidth, y + labelHeight)
                x >= 0 && y >= pauseStatusBottom &&
                    rectangle.right <= sceneView.width &&
                    rectangle.bottom <= sceneView.height &&
                    bodyBounds.none { RectF.intersects(rectangle, it) } &&
                    labels.none { (otherX, otherY) ->
                        kotlin.math.abs(otherX - x) < labelWidth + labelGap &&
                            kotlin.math.abs(otherY - y) < labelHeight + labelGap
                    }
            } ?: return@forEach
            val (labelX, labelY) = placement
            labels += labelX to labelY
            EnemySceneHealthBar(
                fighter = fighter,
                selected = fighter.combatantId == selectedOpponent?.combatantId,
                x = with(density) { labelX.toDp() },
                y = with(density) { labelY.toDp() }
            )
        }
    }
    selectedAlly?.let { partner ->
        PartnerArenaHud(
            fighter = partner,
            modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
        )
    }
}

@Composable
private fun EnemySceneHealthBar(
    fighter: CombatantSnapshot,
    selected: Boolean,
    x: androidx.compose.ui.unit.Dp,
    y: androidx.compose.ui.unit.Dp
) {
    Surface(
        modifier = Modifier.offset(x, y).size(width = 66.dp, height = 9.dp)
            .testTag("offline-battle-enemy-health-bar"),
        color = BattleBackdrop.copy(alpha = 0xE8 / 255f),
        shape = CutCornerShape(2.dp),
        border = BorderStroke(1.dp, if (selected) VitalCyan else BattleEnemyHealth)
    ) {
        Box(Modifier.fillMaxSize().padding(1.dp).background(BattleTrack)) {
            Box(
                Modifier.fillMaxHeight()
                    .fillMaxWidth(
                        (fighter.health.toFloat() / fighter.maxHealth.coerceAtLeast(1)).coerceIn(0f, 1f)
                    )
                    .background(BattleEnemyHealth)
            )
        }
    }
}

@Composable
private fun PartnerArenaHud(fighter: CombatantSnapshot, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .width(184.dp)
            .padding(horizontal = 7.dp, vertical = 5.dp)
            .testTag("offline-battle-player-hud"),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        ArenaHudBar("HP", fighter.health, fighter.maxHealth, VitalCyan)
        ArenaHudBar("MP", fighter.energy, fighter.maxEnergy, VitalPurpleBright)
        if (fighter.specialTechniqueId != null) {
            ArenaHudBar("ESP", fighter.specialCharge, fighter.maxSpecialCharge, VitalYellow)
        }
    }
}

@Composable
private fun ArenaHudBar(label: String, value: Int, maximum: Int, color: Color) {
    Row(
        Modifier.fillMaxWidth().height(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            label,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            style = TextStyle(shadow = Shadow(SceneTextShadow, Offset(1f, 1f), 3f))
        )
        Box(Modifier.weight(1f).height(5.dp).background(BattleTrack.copy(alpha = 0xD0 / 255f), CutCornerShape(1.dp))) {
            Box(
                Modifier.fillMaxHeight()
                    .fillMaxWidth((value.toFloat() / maximum.coerceAtLeast(1)).coerceIn(0f, 1f))
                    .background(color, CutCornerShape(1.dp))
            )
        }
        Text(
            "$value/$maximum",
            modifier = Modifier.widthIn(min = 56.dp),
            color = TextPrimaryOnDark,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            textAlign = TextAlign.End,
            style = TextStyle(shadow = Shadow(SceneTextShadow, Offset(1f, 1f), 3f))
        )
    }
}

@Composable
private fun BattleCommandDeckHeader(
    availableCommandPoints: Int,
    maxCommandPoints: Int,
    commandsEnabled: Boolean,
    manuallyPaused: Boolean,
    tacticalMenuPause: Boolean,
    onToggleManualPause: () -> Unit,
    onToggleTacticalPause: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().height(48.dp).testTag("offline-battle-command-header"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    stringResource(R.string.ui_battle_deck_commands),
                    color = TextPrimaryOnDark,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "CP $availableCommandPoints/${maxCommandPoints.coerceAtLeast(0)}",
                    color = VitalCyan,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            LinearProgressIndicator(
                progress = {
                    if (maxCommandPoints <= 0) 0f
                    else availableCommandPoints.toFloat() / maxCommandPoints.toFloat()
                },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = VitalCyan,
                trackColor = DeepPurpleBgAlt
            )
        }
        OutlinedButton(
            onClick = onToggleManualPause,
            enabled = commandsEnabled,
            modifier = Modifier.size(width = 72.dp, height = 48.dp),
            shape = CutCornerShape(6.dp),
            border = BorderStroke(1.dp, if (manuallyPaused) VitalCyan else SurfaceStroke),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            Text(
                if (manuallyPaused) stringResource(R.string.ui_battle_training_continue) else stringResource(R.string.ui_battle_pause),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall
            )
        }
        OutlinedButton(
            onClick = onToggleTacticalPause,
            enabled = commandsEnabled,
            modifier = Modifier.size(width = 86.dp, height = 48.dp),
            shape = CutCornerShape(6.dp),
            border = BorderStroke(1.dp, if (tacticalMenuPause) VitalCyan else SurfaceStroke),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            Text(
                stringResource(R.string.ui_battle_tactics_state, if (tacticalMenuPause) "ON" else "OFF"),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun BattleCommandBar(
    selectedAlly: CombatantSnapshot?,
    selectedOpponent: CombatantSnapshot?,
    commandHeight: androidx.compose.ui.unit.Dp = 48.dp,
    availableCommandPoints: Int,
    supportReady: Boolean,
    commandsEnabled: Boolean,
    onFocus: (String, String) -> Unit,
    onDefend: (String) -> Unit,
    onSupport: (String) -> Unit,
    onMove: (String, Boolean) -> Unit,
    onSpecial: (String, String?) -> Unit,
    onOpenTechniques: () -> Unit,
    onOpenItems: () -> Unit,
    onOpenStrategies: () -> Unit
) {
    val alive = commandsEnabled && selectedAlly != null && selectedAlly.health > 0
    val targetAlive = commandsEnabled && selectedOpponent != null && selectedOpponent.health > 0
    val special = selectedAlly?.specialTechniqueId?.let { specialId ->
        TrainingBattleFactory.techniques.firstOrNull { it.techniqueId == specialId }
    }
    val availableSpecialCharge = selectedAlly?.let {
        (it.specialCharge - it.reservedSpecialCharge).coerceAtLeast(0)
    } ?: 0
    val specialReady = alive && targetAlive && special != null &&
        availableSpecialCharge >= selectedAlly.maxSpecialCharge &&
        selectedAlly.energy - selectedAlly.reservedEnergy >= special.energyCost &&
        (selectedAlly.cooldownsMillis[special.techniqueId] ?: 0) == 0L
    val deck = battleCommandDeck(
        BattleCommandAvailability(
            partnerReady = alive,
            targetReady = targetAlive,
            supportReady = supportReady && commandsEnabled,
            specialReady = specialReady
        )
    )
    val allyId = selectedAlly?.combatantId
    val targetId = selectedOpponent?.combatantId
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        deck.chunked(3).forEach { rowItems ->
            CommandRow {
                rowItems.forEach { item ->
                    val label = when (item.slot) {
                        BattleCommandSlot.TECHNIQUES -> stringResource(R.string.ui_battle_cmd_techniques)
                        BattleCommandSlot.ITEMS -> stringResource(R.string.ui_battle_cmd_items)
                        BattleCommandSlot.DEFEND -> stringResource(R.string.ui_battle_cmd_defend)
                        BattleCommandSlot.SUPPORT -> stringResource(R.string.ui_battle_cmd_support, availableCommandPoints)
                        BattleCommandSlot.SPECIAL -> if (specialReady) stringResource(R.string.ui_battle_cmd_special_ready) else {
                            val maximum = selectedAlly?.maxSpecialCharge?.coerceAtLeast(1) ?: 100
                            val visibleCharge = selectedAlly?.specialCharge ?: 0
                            stringResource(R.string.ui_battle_cmd_special_charging, visibleCharge * 100 / maximum)
                        }
                        BattleCommandSlot.FOCUS -> stringResource(R.string.ui_battle_cmd_focus)
                        BattleCommandSlot.STRATEGY -> stringResource(R.string.ui_battle_cmd_strategy)
                        BattleCommandSlot.MOVE_CLOSER -> stringResource(R.string.ui_battle_cmd_approach)
                        BattleCommandSlot.KEEP_DISTANCE -> stringResource(R.string.ui_battle_cmd_keep_distance)
                    }
                    CommandButton(
                        label = label,
                        enabled = item.enabled,
                        primary = item.slot == BattleCommandSlot.TECHNIQUES,
                        height = commandHeight
                    ) {
                        when (item.slot) {
                            BattleCommandSlot.TECHNIQUES -> onOpenTechniques()
                            BattleCommandSlot.ITEMS -> onOpenItems()
                            BattleCommandSlot.DEFEND -> allyId?.let(onDefend)
                            BattleCommandSlot.SUPPORT -> allyId?.let(onSupport)
                            BattleCommandSlot.SPECIAL -> allyId?.let { onSpecial(it, targetId) }
                            BattleCommandSlot.FOCUS -> if (allyId != null && targetId != null) {
                                onFocus(allyId, targetId)
                            }
                            BattleCommandSlot.STRATEGY -> onOpenStrategies()
                            BattleCommandSlot.MOVE_CLOSER -> allyId?.let { onMove(it, false) }
                            BattleCommandSlot.KEEP_DISTANCE -> allyId?.let { onMove(it, true) }
                        }
                    }
                }
            }
        }
    }
}

/** Needle sweep duration of the Blast timing minigame (wall clock; sim is paused). */
internal const val BLAST_SWEEP_MILLIS = 1500
/** Progress at which the red zone starts (needle sweeps clockwise from the top). */
internal const val BLAST_RED_ZONE_START = 0.82f

/** The timing challenge is functional input: Remove animations must not auto-complete it. */
private object BlastTimingClock : MotionDurationScale {
    override val scaleFactor: Float = 1f
}

/** True when the needle was stopped inside the red zone: stronger special. */
internal fun blastNeedleHit(progress: Float): Boolean = progress >= BLAST_RED_ZONE_START

private data class BlastOverlayWindow(val combatantId: String, val techniqueId: String, val deadline: Long) {
    val key: String get() = "$combatantId|$techniqueId|$deadline"
}

private data class BlastOverlay(val window: BlastOverlayWindow, val executing: Boolean)

/** Live Blast window preferred for the overlay: selected ally first, else any ally. */
private fun pickBlastWindow(
    snapshot: BattleSnapshot?,
    allies: List<CombatantSnapshot>,
    selectedAlly: CombatantSnapshot?
): BlastOverlayWindow? {
    val snap = snapshot ?: return null
    if (snap.result != null || snap.finisher != null) return null
    val live = allies.filter { it.health > 0 && (snap.pendingBlastTiming[it.combatantId] ?: 0L) > snap.elapsedMillis }
    if (live.isEmpty()) return null
    val chosen = live.firstOrNull { it.combatantId == selectedAlly?.combatantId } ?: live.first()
    val tech = chosen.specialTechniqueId ?: return null
    return BlastOverlayWindow(chosen.combatantId, tech, snap.pendingBlastTiming.getValue(chosen.combatantId))
}

/** Equipped Blast summary for the timing overlay; null when nothing is equipped. */
@Composable
private fun blastEquippedLine(member: CombatantSnapshot?): String? {
    if (member == null) return null
    if (!member.jogressResultSpecies.isNullOrBlank()) return member.jogressResultSpecies
    if (member.blastMode == BlastEvolutionSlot.FORM && !member.blastTargetSpecies.isNullOrBlank()) {
        return member.blastTargetSpecies
    }
    if (member.blastMode == BlastEvolutionSlot.POWER) {
        return stringResource(R.string.ui_battle_blast_mode_power)
    }
    return null
}

@Composable
private fun BlastTimingOverlay(
    specialName: String,
    attackerName: String,
    blastInfo: String?,
    windowKey: String,
    executing: Boolean,
    onHit: () -> Unit,
    onMiss: () -> Unit,
    modifier: Modifier = Modifier,
    inputEnabled: Boolean = true
) {
    val needle = remember(windowKey) { Animatable(0f) }
    var done by remember(windowKey) { mutableStateOf(false) }
    fun stop() {
        if (done || executing || !inputEnabled) return
        done = true
        if (blastNeedleHit(needle.value)) onHit() else onMiss()
    }
    LaunchedEffect(windowKey, executing, inputEnabled) {
        if (executing || !inputEnabled || done) return@LaunchedEffect
        val remainingMillis = ((1f - needle.value).coerceIn(0f, 1f) * BLAST_SWEEP_MILLIS).roundToInt()
        withContext(BlastTimingClock) {
            needle.animateTo(1f, tween(remainingMillis, easing = LinearEasing))
        }
        // Sweep completed untouched: regular special, never a hit.
        if (!done) {
            done = true
            onMiss()
        }
    }
    Box(
        modifier.fillMaxSize()
            .clickable(
                enabled = inputEnabled && !executing,
                interactionSource = remember(windowKey) { MutableInteractionSource() },
                indication = null
            ) { stop() },
        contentAlignment = Alignment.Center
    ) {
        if (!executing) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    specialName,
                    color = VitalYellow,
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                blastInfo?.let {
                    Text(
                        it,
                        color = VitalCyan,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
                BlastGaugeRing(progress = needle.value, modifier = Modifier.size(220.dp))
                Text(
                    stringResource(R.string.ui_battle_blast_aim_red),
                    color = TextPrimaryOnDark,
                    style = MaterialTheme.typography.labelLarge
                )
                Button(
                    onClick = { stop() },
                    enabled = inputEnabled,
                    modifier = Modifier.fillMaxWidth(0.7f).height(52.dp).testTag("offline-battle-blast-confirm"),
                    shape = CutCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC2185B), contentColor = Color.White)
                ) {
                    Text(
                        stringResource(R.string.ui_battle_blast_stop),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Surface(
                    modifier = Modifier.padding(top = 8.dp),
                    color = BattleBackdrop.copy(alpha = 0x90 / 255f),
                    shape = CutCornerShape(6.dp),
                    border = BorderStroke(1.dp, VitalCyan.copy(alpha = 0.35f))
                ) {
                Column(
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        attackerName,
                        color = VitalCyan.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        specialName,
                        color = TextPrimaryOnDark.copy(alpha = 0.92f),
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }
            }
        }
    }
}

@Composable
private fun BlastGaugeRing(progress: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = size.minDimension * 0.11f
        val radius = size.minDimension / 2f - stroke
        val center = Offset(size.width / 2f, size.height / 2f)
        val frame = Size(radius * 2f, radius * 2f)
        val frameTopLeft = Offset(center.x - radius, center.y - radius)
        drawArc(
            color = Color(0xFF6E6E7A),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(center.x - radius - stroke * 0.9f, center.y - radius - stroke * 0.9f),
            size = Size((radius + stroke * 0.9f) * 2f, (radius + stroke * 0.9f) * 2f),
            style = Stroke(width = stroke * 0.22f)
        )
        drawArc(
            color = Color(0xFF2A2A33),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = frameTopLeft,
            size = frame,
            style = Stroke(width = stroke)
        )
        val redStart = -90f + BLAST_RED_ZONE_START * 360f
        drawArc(
            color = Color(0xFFE5484D),
            startAngle = redStart,
            sweepAngle = 360f - BLAST_RED_ZONE_START * 360f,
            useCenter = false,
            topLeft = frameTopLeft,
            size = frame,
            style = Stroke(width = stroke)
        )
        drawArc(
            color = Color(0xFFFFD447),
            startAngle = -90f,
            sweepAngle = (progress.coerceIn(0f, 1f) * 360f),
            useCenter = false,
            topLeft = frameTopLeft,
            size = frame,
            style = Stroke(width = stroke)
        )
        val angleRad = Math.toRadians((-90.0 + progress.coerceIn(0f, 1f) * 360.0)).toFloat()
        val tip = Offset(
            center.x + radius * kotlin.math.cos(angleRad),
            center.y + radius * kotlin.math.sin(angleRad)
        )
        drawCircle(color = Color.White, radius = stroke * 0.5f, center = tip)
    }
}

private data class TapFusion(val result: String, val special: String?)

/**
 * Fusion names for a Blast tap: the lead's equipped Jogress result, validated
 * against the actual battle partner (universal table, Dex species list or Dex
 * attribute requirement) and DIM presence. Null = plain dual Blast.
 */
private fun resolveTapFusion(
    context: Context,
    lead: CombatantSnapshot,
    allies: List<CombatantSnapshot>,
    fighters: Map<String, BattleFighterPresentation>,
    presentSpecies: Set<String>?
): TapFusion? {
    val choice = lead.jogressResultSpecies?.takeIf { it.isNotBlank() } ?: return null
    val partner = allies.firstOrNull { it.combatantId != lead.combatantId && it.health > 0 } ?: return null
    val data = BlastEvolutionRepository.load(context.applicationContext)
    fun norm(name: String) = BlastEvolutionRepository.normalize(data, name)
    if (presentSpecies != null && !BlastEvolutionRepository.isPresent(data, presentSpecies, choice)) return null
    val leadSpecies = fighters[lead.combatantId]?.speciesName
    val partnerSpecies = fighters[partner.combatantId]?.speciesName
    val uni = BlastEvolutionRepository.resolveLeadJogress(data, leadSpecies, partnerSpecies, choice)
    val dexSpeciesOk = partnerSpecies != null &&
        lead.jogressPartnerSpecies.orEmpty().any { norm(it) == norm(partnerSpecies) }
    val dexAttrOk = lead.jogressPartnerAttribute != null && lead.jogressPartnerAttribute == partner.attribute
    if (uni == null && !dexSpeciesOk && !dexAttrOk) return null
    val special = uni?.resultSpecial
        ?: data.jogress.firstOrNull { norm(it.result) == norm(choice) }?.resultSpecial
    return TapFusion(choice, special)
}

/** Localized line for a failed order; falls back to the sim's raw reason. */
private fun orderFailureText(resources: android.content.res.Resources, update: OrderUpdate): String {
    val res = when (update.reasonCode) {
        OrderFailure.BLAST_PAUSED, OrderFailure.BLAST_NO_WINDOW,
        OrderFailure.BLAST_WINDOW_CLOSED -> R.string.ui_battle_blast_too_late
        OrderFailure.DUO_UNAVAILABLE -> R.string.ui_battle_duo_unavailable
        OrderFailure.RESOURCES_MISSING -> R.string.ui_battle_no_resources
        OrderFailure.BATTLE_ENDED -> R.string.ui_battle_order_err_battle_ended
        OrderFailure.PARTNER_MISSING -> R.string.ui_battle_order_err_partner_missing
        OrderFailure.NOT_ALLIED -> R.string.ui_battle_order_err_not_allied
        OrderFailure.PARTNER_DEFEATED -> R.string.ui_battle_order_err_partner_defeated
        OrderFailure.BAD_LIFETIME -> R.string.ui_battle_order_err_bad_lifetime
        OrderFailure.SUPPORT_PAUSED -> R.string.ui_battle_order_err_support_paused
        OrderFailure.SUPPORT_NO_WINDOW -> R.string.ui_battle_order_err_support_no_window
        OrderFailure.SUPPORT_WINDOW_EXPIRED -> R.string.ui_battle_order_err_support_window_expired
        OrderFailure.FOCUS_NO_TARGET -> R.string.ui_battle_order_err_focus_no_target
        OrderFailure.ORDER_QUEUE_FULL -> R.string.ui_battle_order_err_order_queue_full
        OrderFailure.BAD_DEFEND_DURATION -> R.string.ui_battle_order_err_bad_defend_duration
        OrderFailure.TECHNIQUE_MISSING -> R.string.ui_battle_order_err_technique_missing
        OrderFailure.COUNTER_REACTION_ONLY -> R.string.ui_battle_order_err_counter_reaction_only
        OrderFailure.TECHNIQUE_NOT_OWNED -> R.string.ui_battle_order_err_technique_not_owned
        OrderFailure.TECHNIQUE_COOLDOWN -> R.string.ui_battle_order_err_technique_cooldown
        OrderFailure.TARGET_MISSING -> R.string.ui_battle_order_err_target_missing
        OrderFailure.SPECIAL_CHARGING -> R.string.ui_battle_order_err_special_charging
        OrderFailure.ITEM_MISSING -> R.string.ui_battle_order_err_item_missing
        OrderFailure.ITEM_BAD_TARGET -> R.string.ui_battle_order_err_item_bad_target
        OrderFailure.ITEM_DEPLETED -> R.string.ui_battle_order_err_item_depleted
        OrderFailure.MOVE_NO_TARGET -> R.string.ui_battle_order_err_move_no_target
        OrderFailure.TECHNIQUE_STALE -> R.string.ui_battle_order_err_technique_stale
        OrderFailure.ORDER_INVALID -> R.string.ui_battle_order_err_order_invalid
        OrderFailure.ORDER_EXPIRED -> R.string.ui_battle_order_err_order_expired
        OrderFailure.ORDER_TIMED_OUT -> R.string.ui_battle_order_err_order_timed_out
        OrderFailure.POSITIONING_FAILED -> R.string.ui_battle_order_err_positioning_failed
        OrderFailure.ITEM_UNUSABLE -> R.string.ui_battle_order_err_item_unusable
        OrderFailure.ITEM_UNNEEDED -> R.string.ui_battle_order_err_item_unneeded
        OrderFailure.ORDER_SUPERSEDED -> R.string.ui_battle_order_err_order_superseded
        OrderFailure.STATUS_INTERRUPTED -> R.string.ui_battle_order_err_status_interrupted
        else -> null
    }
    return res?.let { resources.getString(it) } ?: update.reason.orEmpty()
}

@Composable
private fun CommandRow(content: @Composable RowScope.() -> Unit) {    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically, content = content)
}

@Composable
private fun RowScope.CommandButton(
    label: String,
    enabled: Boolean = true,
    primary: Boolean = false,
    height: androidx.compose.ui.unit.Dp = 48.dp,
    onClick: () -> Unit
) {
    if (primary) {
        Button(onClick = onClick, enabled = enabled,
            modifier = Modifier.weight(1f).height(height).testTag("offline-battle-command-slot"),
            contentPadding = PaddingValues(horizontal = 3.dp, vertical = 2.dp),
            shape = CutCornerShape(6.dp), colors = ButtonDefaults.buttonColors(
                containerColor = VitalPurpleBright, contentColor = OnVitalPrimary
            )) {
            Text(label, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall)
        }
    } else {
        OutlinedButton(onClick = onClick, enabled = enabled,
            modifier = Modifier.weight(1f).height(height).testTag("offline-battle-command-slot"),
            contentPadding = PaddingValues(horizontal = 3.dp, vertical = 2.dp),
            shape = CutCornerShape(6.dp), border = BorderStroke(1.dp, SurfaceStroke)) {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun TechniquesDialog(
    snapshot: BattleSnapshot?,
    selectedAlly: CombatantSnapshot?,
    selectedOpponent: CombatantSnapshot?,
    tacticalPauseEnabled: Boolean,
    onTechnique: (TechniqueDefinition, String, String?) -> Unit,
    onDismiss: () -> Unit
) {
    if (selectedAlly == null) return
    TacticalDialogFrame(title = stringResource(R.string.ui_battle_techniques_title, selectedAlly.displayName), onDismiss = onDismiss) {
        Text(if (tacticalPauseEnabled) stringResource(R.string.ui_battle_tactical_pause_on) else stringResource(R.string.ui_battle_tactical_pause_off), color = VitalCyan,
            style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 5.dp))
        val equippedIds = selectedAlly.techniqueIds
        TrainingBattleFactory.techniques.filter { it.techniqueId in equippedIds }.forEach { technique ->
            val isHeal = technique.kind == TechniqueKind.HEAL
            val targetsSelf = isHeal || technique.rangeProfile == com.github.nacabaro.vbhelper.battle.offline.core.TechniqueRangeProfile.SELF
            val targetId = if (targetsSelf) selectedAlly.combatantId else selectedOpponent?.combatantId
            val ready = (selectedAlly.cooldownsMillis[technique.techniqueId] ?: 0L) <= 0L
            val hasResources = selectedAlly.energy - selectedAlly.reservedEnergy >= technique.energyCost &&
                ((snapshot?.commandPoints ?: 0) - (snapshot?.reservedCommandPoints ?: 0)) >= technique.commandPointCost
            OutlinedButton(
                onClick = { targetId?.let { onTechnique(technique, selectedAlly.combatantId, it) } },
                enabled = selectedAlly.health > 0 && targetId != null && ready && hasResources,
                modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp),
                shape = CutCornerShape(5.dp), border = BorderStroke(1.dp, SurfaceStroke)
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(battleTechniqueName(technique.techniqueId, technique.displayName), color = TextPrimaryOnDark, fontWeight = FontWeight.SemiBold)
                        Text("${technique.energyCost} EN",
                            color = VitalCyan, style = MaterialTheme.typography.labelSmall)
                    }
                    Text(
                        text = when {
                            !ready -> stringResource(R.string.ui_battle_cooldown, "%.1f".format((selectedAlly.cooldownsMillis[technique.techniqueId] ?: 0) / 1000f))
                            !hasResources -> stringResource(R.string.ui_battle_no_resources)
                            isHeal -> stringResource(R.string.ui_battle_heal_target, selectedAlly.displayName, technique.healPower)
                            else -> stringResource(R.string.ui_battle_tech_target, selectedOpponent?.displayName ?: stringResource(R.string.ui_battle_no_target), technique.minRange, technique.maxRange, technique.power)
                        },
                        color = TextSecondaryOnDark, style = MaterialTheme.typography.labelSmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    TechniqueStatusBadges(
                        technique = technique,
                        modifier = Modifier.padding(top = 5.dp),
                        enabled = selectedAlly.health > 0 && targetId != null && ready && hasResources
                    )
                }
            }
        }
        OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ui_battle_training_resume)) }
    }
}

@Composable
private fun ItemsDialog(
    snapshot: BattleSnapshot?,
    selectedAlly: CombatantSnapshot?,
    tacticalPauseEnabled: Boolean,
    onUseItem: (BattleItemDefinition, String) -> Unit,
    onDismiss: () -> Unit
) {
    val remaining = snapshot?.trainingItems.orEmpty().associateBy { it.itemId }
    val definitions = (TrainingBattleFactory.trainingItems + com.github.nacabaro.vbhelper.quests.QuestBattleInventory.catalog)
        .filter { it.itemId in remaining }
    TacticalDialogFrame(title = stringResource(R.string.ui_battle_training_items_title), onDismiss = onDismiss) {
        Text(stringResource(R.string.ui_battle_items_restart_note, if (tacticalPauseEnabled) stringResource(R.string.ui_battle_tactical_pause_short_on) else stringResource(R.string.ui_battle_tactical_pause_short_off)), color = VitalCyan,
            style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 5.dp))
        definitions.forEach { item ->
            val itemState = remaining[item.itemId]
            val available = ((itemState?.remaining ?: 0) - (itemState?.reserved ?: 0)).coerceAtLeast(0)
            val hasEffect = selectedAlly != null && when (item.kind) {
                BattleItemKind.HEAL_HEALTH -> selectedAlly.health < selectedAlly.maxHealth
                BattleItemKind.RESTORE_ENERGY -> selectedAlly.energy < selectedAlly.maxEnergy
                BattleItemKind.CLEANSE_STATUS -> selectedAlly.statuses.isNotEmpty()
            }
            OutlinedButton(
                onClick = { selectedAlly?.let { onUseItem(item, it.combatantId) } },
                enabled = available > 0 && hasEffect,
                modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
                shape = CutCornerShape(5.dp), border = BorderStroke(1.dp, SurfaceStroke)
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(battleItemDisplayName(item), color = TextPrimaryOnDark, fontWeight = FontWeight.SemiBold)
                        Text("×$available", color = VitalCyan, fontWeight = FontWeight.Bold)
                    }
                    Text(itemDescription(item), color = TextSecondaryOnDark,
                        style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ui_battle_training_resume)) }
    }
}

@Composable
private fun TacticalDialogFrame(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(max = 620.dp),
            color = SurfaceElevatedPurple,
            shape = CutCornerShape(9.dp),
            border = BorderStroke(1.dp, SurfaceStroke)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = TextPrimaryOnDark, style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.ui_battle_training_close)) }
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp), content = {
                    item { Column(content = content) }
                })
            }
        }
    }
}

@Composable
private fun BattleResultPanel(
    outcome: BattleOutcome,
    statistics: com.github.nacabaro.vbhelper.battle.offline.core.BattleStatistics,
    elapsedMillis: Long,
    outcomeText: String,
    onRematch: (() -> Unit)?,
    onReturn: () -> Unit,
    modifier: Modifier = Modifier,
    rewardContent: @Composable () -> Unit = {}
) {
    Surface(modifier.fillMaxWidth(), color = BattlePanel.copy(alpha = 0xF0 / 255f), shape = CutCornerShape(9.dp),
        border = BorderStroke(1.dp, VitalCyan)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(outcomeText, color = TextPrimaryOnDark, style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.ui_battle_result_duration, formatDuration(elapsedMillis)), color = VitalCyan, style = MaterialTheme.typography.labelLarge)
                Text(stringResource(R.string.ui_battle_result_damage, statistics.damageDealt, statistics.damageReceived, statistics.damagePrevented),
                    color = TextSecondaryOnDark, style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.ui_battle_result_stats, statistics.healingDone, statistics.ordersCompleted, statistics.supportCommands, statistics.itemsUsed, statistics.projectilesHit, statistics.projectilesHit + statistics.projectilesMissed),
                    color = TextSecondaryOnDark, style = MaterialTheme.typography.bodySmall)
                rewardContent()
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onRematch != null) {
                    Button(onClick = onRematch, modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = VitalPurpleBright, contentColor = OnVitalPrimary)) {
                        Text(stringResource(R.string.ui_battle_rematch), fontWeight = FontWeight.Bold)
                    }
                }
                OutlinedButton(onClick = onReturn, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.ui_battle_back)) }
            }
        }
    }
}

@Composable
private fun eventSummary(snapshot: BattleSnapshot, fighters: Map<String, BattleFighterPresentation>): String {
    val event = snapshot.recentEvents.lastOrNull() ?: return stringResource(R.string.ui_battle_feed_idle)
    fun name(id: String) = fighters[id]?.displayName ?: id.substringAfter(':')
    fun specialName(combatantId: String, techniqueId: String) = fighters[combatantId]?.specialDisplayName
        ?: TrainingBattleFactory.techniques.firstOrNull { it.techniqueId == techniqueId }?.displayName
        ?: techniqueId
    return when (event) {
        is BattleEvent.TechniqueHit, is BattleEvent.TechniqueMissed,
        is BattleEvent.ProjectileMissed, is BattleEvent.ProjectileLaunched,
        is BattleEvent.TechniqueStarted, is BattleEvent.SpecialReady,
        is BattleEvent.SpecialResolved -> stringResource(R.string.ui_battle_feed_ongoing)
        is BattleEvent.SpecialStarted -> stringResource(R.string.ui_battle_feed_special, name(event.combatantId), specialName(event.combatantId, event.techniqueId))
        is BattleEvent.BlastTimingOpened -> stringResource(R.string.ui_battle_feed_ongoing)
        is BattleEvent.BlastTimingResolved -> stringResource(R.string.ui_battle_feed_blast_hit, name(event.combatantId))
        is BattleEvent.BlastFormStarted -> stringResource(R.string.ui_battle_feed_blast_form, name(event.combatantId), event.targetSpecies, event.specialName)
        is BattleEvent.BlastFormEnded -> stringResource(R.string.ui_battle_feed_ongoing)
        is BattleEvent.BlastJogressStarted -> stringResource(R.string.ui_battle_feed_jogress, name(event.leadId), name(event.partnerId), event.resultSpecies)
        is BattleEvent.BlastJogressEnded -> stringResource(R.string.ui_battle_feed_ongoing)
        is BattleEvent.ItemUsed -> stringResource(R.string.ui_battle_feed_item, name(event.combatantId), name(event.targetId), event.amount)
        is BattleEvent.SupportSucceeded -> stringResource(R.string.ui_battle_feed_support, name(event.combatantId), event.commandPointsGained)
        is BattleEvent.SupportWindowOpened -> stringResource(R.string.ui_battle_feed_support_window, name(event.combatantId), ((event.expiresAtMillis - snapshot.elapsedMillis).coerceAtLeast(0L) / 1_000f).roundToInt())
        is BattleEvent.CombatantDefeated -> stringResource(R.string.ui_battle_feed_defeated, name(event.combatantId))
        is BattleEvent.OrderChanged -> {
            val update = event.update
            val reason = if (update.status == OrderStatus.FAILED) orderFailureText(LocalResources.current, update)
            else update.reason ?: stringResource(R.string.ui_battle_feed_order_fallback)
            stringResource(R.string.ui_battle_feed_order, update.status.name.lowercase(), reason)
        }
        is BattleEvent.StatusApplied -> stringResource(R.string.ui_battle_feed_status, event.statusId)
        is BattleEvent.StateChanged, is BattleEvent.TargetChanged -> stringResource(R.string.ui_battle_feed_reposition)
        is BattleEvent.CounterTriggered -> stringResource(R.string.ui_battle_feed_counter, name(event.combatantId))
        is BattleEvent.PositioningReplanned -> stringResource(R.string.ui_battle_feed_seeking, name(event.combatantId))
        is BattleEvent.BattleEnded -> trainingOutcomeText(event.result.outcome)
    }
}

@Composable
private fun strategyLabel(strategy: BattleStrategy): String = when (strategy) {
    BattleStrategy.AGGRESSIVE -> stringResource(R.string.ui_battle_strategy_aggressive)
    BattleStrategy.BALANCED -> stringResource(R.string.ui_battle_strategy_balanced)
    BattleStrategy.CONSERVATIVE -> stringResource(R.string.ui_battle_strategy_conservative)
    BattleStrategy.DEFENSIVE -> stringResource(R.string.ui_battle_strategy_defensive)
    BattleStrategy.RANGED -> stringResource(R.string.ui_battle_strategy_ranged)
    BattleStrategy.SUPPORT -> stringResource(R.string.ui_battle_strategy_support)
}

@Composable
private fun battleItemDisplayName(item: BattleItemDefinition): String = when (item.itemId) {
    "training_recovery" -> stringResource(R.string.ui_battle_item_name_training_recovery)
    "training_energy" -> stringResource(R.string.ui_battle_item_name_training_energy)
    "training_cleanse" -> stringResource(R.string.ui_battle_item_name_training_cleanse)
    "quest_recovery" -> stringResource(R.string.ui_battle_item_name_quest_recovery)
    "quest_energy" -> stringResource(R.string.ui_battle_item_name_quest_energy)
    "quest_cleanse" -> stringResource(R.string.ui_battle_item_name_quest_cleanse)
    else -> item.displayName
}

@Composable
private fun itemDescription(item: BattleItemDefinition): String = when (item.kind) {
    BattleItemKind.HEAL_HEALTH -> stringResource(R.string.ui_battle_item_heal, item.amount)
    BattleItemKind.RESTORE_ENERGY -> stringResource(R.string.ui_battle_item_energy, item.amount)
    BattleItemKind.CLEANSE_STATUS -> stringResource(R.string.ui_battle_item_cleanse)
}

@Composable
private fun pauseReasonLabel(reason: String?): String = when (reason) {
    "tech-menu" -> stringResource(R.string.ui_battle_training_pause_reason_tech)
    "item-menu" -> stringResource(R.string.ui_battle_training_pause_reason_item)
    "exit-dialog" -> stringResource(R.string.ui_battle_training_pause_reason_exit)
    "background" -> stringResource(R.string.ui_battle_training_pause_reason_background)
    "screen" -> stringResource(R.string.ui_battle_training_pause_reason_screen)
    "scene" -> stringResource(R.string.ui_battle_training_pause_reason_scene)
    "intro" -> stringResource(R.string.ui_battle_training_pause_reason_intro)
    "manual" -> stringResource(R.string.ui_battle_training_pause_reason_manual)
    "strategy-menu" -> stringResource(R.string.ui_battle_training_pause_reason_strategy)
    "blast-menu" -> stringResource(R.string.ui_battle_training_pause_reason_blast)
    else -> stringResource(R.string.ui_battle_training_pause_reason_other)
}

@Composable
private fun trainingOutcomeText(outcome: BattleOutcome): String = when (outcome) {
    BattleOutcome.ALLIED_VICTORY -> stringResource(R.string.ui_battle_outcome_allied)
    BattleOutcome.OPPOSING_VICTORY -> stringResource(R.string.ui_battle_outcome_opposing)
    BattleOutcome.DRAW -> stringResource(R.string.ui_battle_outcome_draw)
    BattleOutcome.ABANDONED -> stringResource(R.string.ui_battle_outcome_abandoned)
}

@Composable
private fun radarOutcomeText(outcome: BattleOutcome): String = when (outcome) {
    BattleOutcome.ALLIED_VICTORY -> stringResource(R.string.ui_battle_radar_outcome_allied)
    BattleOutcome.OPPOSING_VICTORY -> stringResource(R.string.ui_battle_radar_outcome_opposing)
    BattleOutcome.DRAW -> stringResource(R.string.ui_battle_radar_outcome_draw)
    BattleOutcome.ABANDONED -> stringResource(R.string.ui_battle_radar_outcome_abandoned)
}

private fun formatDuration(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1_000L).coerceAtLeast(0L)
    return "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

private const val PROJECTILE_HEIGHT = 0.85f
private const val LABEL_HEIGHT = 1.9f
private const val STARTUP_EFFECT_HEIGHT = 0.72f
private const val MISS_EFFECT_HEIGHT = 0.72f
private const val STARTUP_EFFECT_CYCLE_MILLIS = 720L
private const val DAMAGE_NUMBER_HEIGHT = 1.35f
private const val DAMAGE_NUMBER_OFFSET_X = 56
private const val DAMAGE_NUMBER_OFFSET_Y = 52
private const val HIT_EFFECT_DURATION_MILLIS = BATTLE_DAMAGE_INDICATOR_MILLIS
private const val MISS_EFFECT_DURATION_MILLIS = 680
private const val MISS_EFFECT_REDUCED_MOTION_MILLIS = 450L
