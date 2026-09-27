package com.github.nacabaro.vbhelper.screens.offlineBattle

import android.graphics.Bitmap
import android.graphics.RectF
import android.content.pm.ApplicationInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.github.nacabaro.vbhelper.battle.offline.core.BattleEvent
import com.github.nacabaro.vbhelper.battle.offline.core.BattleItemDefinition
import com.github.nacabaro.vbhelper.battle.offline.core.BattleItemKind
import com.github.nacabaro.vbhelper.battle.offline.core.BattleOutcome
import com.github.nacabaro.vbhelper.battle.offline.core.BattleSide
import com.github.nacabaro.vbhelper.battle.offline.core.BattleSnapshot
import com.github.nacabaro.vbhelper.battle.offline.core.BattleStrategy
import com.github.nacabaro.vbhelper.battle.offline.core.CombatantSnapshot
import com.github.nacabaro.vbhelper.battle.offline.core.CombatantState
import com.github.nacabaro.vbhelper.battle.offline.core.TechniqueDefinition
import com.github.nacabaro.vbhelper.battle.offline.core.TechniqueKind
import com.github.nacabaro.vbhelper.battle.offline.core.TrainerAction
import com.github.nacabaro.vbhelper.battle.offline.core.OrderStatus
import com.github.nacabaro.vbhelper.battle.offline.data.TrainingBattleFactory
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import com.github.nacabaro.vbhelper.ui.theme.DeepPurpleBgAlt
import com.github.nacabaro.vbhelper.ui.theme.SpaceBlack
import com.github.nacabaro.vbhelper.ui.theme.SurfaceElevatedPurple
import com.github.nacabaro.vbhelper.ui.theme.SurfaceStroke
import com.github.nacabaro.vbhelper.ui.theme.TextPrimaryOnDark
import com.github.nacabaro.vbhelper.ui.theme.TextSecondaryOnDark
import com.github.nacabaro.vbhelper.ui.theme.VitalCyan
import com.github.nacabaro.vbhelper.ui.theme.VitalPurpleBright
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private enum class TrainingTacticalMenu { TECHNIQUES, ITEMS }

@Composable
fun OfflineTrainingBattleScreen(
    viewModel: OfflineBattleSessionViewModel,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val debugToolsEnabled = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()
    val state by viewModel.state.collectAsState()
    val snapshot = state.snapshot
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
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val wideBattleLayout = isLandscape && configuration.screenWidthDp >= 480
            @Suppress("UNUSED_VARIABLE")
    val cameraFrameVersion = projectionRevision

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
    LaunchedEffect(menu, tacticalMenuPause, showStrategyMenu, showExitDialog, manualPause) {
        viewModel.setPaused("tech-menu", menu == TrainingTacticalMenu.TECHNIQUES && tacticalMenuPause)
        viewModel.setPaused("item-menu", menu == TrainingTacticalMenu.ITEMS && tacticalMenuPause)
        viewModel.setPaused("strategy-menu", showStrategyMenu && tacticalMenuPause)
        viewModel.setPaused("exit-dialog", showExitDialog)
        viewModel.setPaused("manual", manualPause)
    }
    LaunchedEffect(feedbackRevision) {
        delay(4000)
        commandFeedback = null
    }

    fun issue(actor: String, action: TrainerAction, interrupt: Boolean = false) {
        val result = viewModel.issueOrder(actor, action, interrupt) ?: return
        feedbackRevision++
        commandFeedback = if (result.status == OrderStatus.FAILED) result.reason else
            if (result.status == OrderStatus.QUEUED) "Ordem recebida · o parceiro executará assim que possível." else "Comando aplicado."
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
        closeTacticalMenu()
        menu = next
        if (tacticalMenuPause) {
            viewModel.setPaused(if (next == TrainingTacticalMenu.TECHNIQUES) "tech-menu" else "item-menu", true)
        }
    }

    fun requestExit() {
        closeTacticalMenu()
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

    Column(
        modifier = modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text("Arena de treino", color = TextPrimaryOnDark, fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = when {
                        wideBattleLayout && commandFeedback != null -> commandFeedback.orEmpty()
                        snapshot?.isPaused == true -> "Pausado · ${pauseReasonLabel(snapshot.pauseReason)}"
                        snapshot?.result != null -> "Combate encerrado"
                        state.loading -> "Preparando o Coliseu…"
                        else -> "${formatDuration(snapshot?.elapsedMillis ?: 0)} · Combate autônomo"
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
                    enabled = sceneReady,
                    modifier = Modifier.size(46.dp)
                        .border(1.dp, SurfaceStroke, CutCornerShape(6.dp))
                        .clip(CutCornerShape(6.dp))
                        .testTag("offline-battle-recenter-camera")
                ) {
                    Icon(
                        imageVector = Icons.Filled.CenterFocusStrong,
                        contentDescription = "Recentralizar câmera",
                        tint = TextPrimaryOnDark
                    )
                }
                if (debugToolsEnabled && snapshot != null) {
                    OutlinedButton(onClick = { showBattleDebug = true }, shape = CutCornerShape(6.dp),
                        border = BorderStroke(1.dp, VitalCyan), modifier = Modifier.size(width = 52.dp, height = 42.dp),
                        contentPadding = PaddingValues(0.dp)) {
                        Text("IA", color = VitalCyan)
                    }
                }
                OutlinedButton(onClick = ::requestExit, shape = CutCornerShape(6.dp),
                    border = BorderStroke(1.dp, SurfaceStroke), modifier = Modifier.size(width = 66.dp, height = 42.dp),
                    contentPadding = PaddingValues(0.dp)) {
                    Text("Sair", color = TextPrimaryOnDark)
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
                        if (allies.any { it.combatantId == fighterId }) selectedAllyId = fighterId
                        if (opponents.any { it.combatantId == fighterId }) selectedOpponentId = fighterId
                    },
                    onAssetError = {
                        sceneReady = false
                        rendererError = it
                        viewModel.setPaused("renderer-error", true)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            snapshot?.takeIf { sceneReady }?.let { current ->
                BattleVfxOverlay(current, state.fighters, sceneView)
                val labels = mutableListOf<Pair<Float, Float>>()
                val labelWidth = with(density) { 84.dp.toPx() }
                val labelHeight = with(density) { 30.dp.toPx() }
                val pauseStatusBottom = with(density) { 44.dp.toPx() }
                val labelGap = with(density) { 4.dp.toPx() }
                val bodyBounds = (current.alliedMembers + current.opposingMembers).mapNotNull { member ->
                    val foot = sceneView?.projectBattlePosition(member.position.x, 0f, member.position.z)
                    val head = sceneView?.projectBattlePosition(member.position.x, state.arenaManifest?.fighterScale ?: 1.65f, member.position.z)
                    if (foot == null || head == null) null else {
                        val halfWidth = kotlin.math.abs(foot.second - head.second) * 0.6f
                        RectF(head.first - halfWidth, head.second, head.first + halfWidth, foot.second)
                    }
                }
                (current.alliedMembers + current.opposingMembers).forEach { fighter ->
                    val point = sceneView?.projectBattlePosition(fighter.position.x, LABEL_HEIGHT, fighter.position.z)
                    if (point != null) {
                        val centeredX = point.first - labelWidth / 2
                        val aboveY = point.second - labelHeight
                        // Keep labels close to their fighter and clear of the pause indicator.
                        // Crowded fighters still have named, selectable cards in the HUD.
                        val placement = listOf(
                            centeredX to aboveY,
                            (point.first + labelGap) to aboveY,
                            (point.first - labelWidth - labelGap) to aboveY,
                            centeredX to (aboveY - labelHeight - labelGap)
                        ).firstOrNull { (x, y) ->
                            val rectangle = RectF(x, y, x + labelWidth, y + labelHeight)
                            x >= 0 && y >= pauseStatusBottom &&
                                rectangle.right <= (sceneView?.width ?: 0) &&
                                rectangle.bottom <= (sceneView?.height ?: 0) &&
                                bodyBounds.none { RectF.intersects(rectangle, it) } &&
                                labels.none { (otherX, otherY) ->
                                    kotlin.math.abs(otherX - x) < labelWidth + labelGap &&
                                        kotlin.math.abs(otherY - y) < labelHeight + labelGap
                                }
                        } ?: return@forEach
                        val (labelX, labelY) = placement
                        labels += labelX to labelY
                        FighterSceneLabel(
                            fighter = fighter,
                            x = with(density) { labelX.toDp() },
                            y = with(density) { labelY.toDp() }
                        )
                    }
                }
                if (current.isPaused && current.result == null && state.countdown == 0) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
                        color = Color(0xE5161024), shape = CutCornerShape(6.dp),
                        border = BorderStroke(1.dp, VitalCyan.copy(alpha = 0.7f))
                    ) {
                        Text("PAUSADO", Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                            color = VitalCyan, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            if (sceneReady && state.countdown > 0 && snapshot?.result == null) {
                Surface(Modifier.align(Alignment.Center), color = Color(0xD0100D1A), shape = CutCornerShape(8.dp)) {
                    Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Prepare-se", color = TextPrimaryOnDark, style = MaterialTheme.typography.titleMedium)
                        Text("${state.countdown}", color = VitalCyan, style = MaterialTheme.typography.displayMedium)
                    }
                }
            }
            if (state.loading || (snapshot != null && !sceneReady && rendererError == null && state.error == null)) {
                Surface(Modifier.align(Alignment.Center), color = SurfaceElevatedPurple,
                    shape = CutCornerShape(8.dp), border = BorderStroke(1.dp, SurfaceStroke)) {
                    Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Carregando Digimon 3D e arena…", color = TextPrimaryOnDark,
                            fontWeight = FontWeight.SemiBold)
                        LinearProgressIndicator(color = VitalCyan, trackColor = DeepPurpleBgAlt)
                    }
                }
            }
            val error = rendererError ?: state.error
            if (snapshot == null && !state.loading && error == null) {
                Surface(Modifier.align(Alignment.Center), color = SurfaceElevatedPurple, shape = CutCornerShape(8.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("O treino foi interrompido. Prepare uma nova batalha para continuar.", color = TextPrimaryOnDark)
                        Button(onClick = { viewModel.finishSession(); onExit() }) { Text("Voltar às batalhas") }
                    }
                }
            }
            if (error != null) {
                Surface(Modifier.align(Alignment.BottomCenter).padding(8.dp), color = Color(0xF02A1426),
                    shape = CutCornerShape(6.dp), border = BorderStroke(1.dp, Color(0xFFFF7C96))) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(error, color = TextPrimaryOnDark, style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(
                            onClick = {
                                if (state.error != null) viewModel.retry(context)
                                else if (sceneView != null) sceneView?.retryScene()
                                else sceneGeneration++
                            }
                        ) { Text("Tentar novamente") }
                    }
                }
            }
            snapshot?.result?.let { result ->
                BattleResultPanel(
                    outcome = result.outcome,
                    statistics = result.statistics,
                    elapsedMillis = result.elapsedMillis,
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

        val commandsEnabled = snapshot != null && snapshot.result == null && sceneReady &&
            rendererError == null && state.countdown == 0
        val current = snapshot
        val teamContent: @Composable ColumnScope.() -> Unit = {
            BattleTeamRow(
                title = "SEUS PARCEIROS",
                members = current?.alliedMembers.orEmpty(),
                selectedId = selectedAlly?.combatantId,
                color = VitalCyan,
                onSelect = { selectedAllyId = it }
            )
            BattleTeamRow(
                title = "OPONENTES · toque para escolher o foco",
                members = current?.opposingMembers.orEmpty(),
                selectedId = selectedOpponent?.combatantId,
                color = Color(0xFFFF7899),
                onSelect = { selectedOpponentId = it }
            )
            BattleCommandBar(
                selectedAlly = selectedAlly,
                selectedOpponent = selectedOpponent,
                availableCommandPoints = ((current?.commandPoints ?: 0) -
                    (current?.reservedCommandPoints ?: 0)).coerceAtLeast(0),
                supportReady = current != null && !current.isPaused &&
                    selectedAlly?.combatantId?.let(current.pendingSupportCombatantIds::contains) == true,
                commandsEnabled = commandsEnabled,
                manuallyPaused = manualPause,
                onToggleManualPause = { manualPause = !manualPause },
                tacticalMenuPause = tacticalMenuPause,
                onToggleTacticalPause = { tacticalMenuPause = !tacticalMenuPause },
                onFocus = { ally, target -> issue(ally, TrainerAction.FocusTarget(target)) },
                onDefend = { ally -> issue(ally, TrainerAction.Defend(), true) },
                onSupport = { ally -> issue(ally, TrainerAction.Support) },
                onMove = { ally, away -> issue(ally, if (away) TrainerAction.KeepDistance else TrainerAction.MoveCloser, true) },
                onSpecial = { ally, target ->
                    val technique = TrainingBattleFactory.techniques.first { it.kind == TechniqueKind.SPECIAL }
                    issue(ally, TrainerAction.UseTechnique(technique.techniqueId, target))
                },
                onOpenTechniques = { openTacticalMenu(TrainingTacticalMenu.TECHNIQUES) },
                onOpenItems = { openTacticalMenu(TrainingTacticalMenu.ITEMS) },
                onOpenStrategies = { showStrategyMenu = true }
            )
            if (!wideBattleLayout) {
                Text(
                    text = commandFeedback ?: current?.let { eventSummary(it, state.fighters) }
                        ?: if (state.loading) "Preparando a arena…" else "Comandos da luta",
                    modifier = Modifier.fillMaxWidth().heightIn(min = 18.dp, max = 18.dp)
                        .padding(horizontal = 3.dp),
                    color = TextSecondaryOnDark, style = MaterialTheme.typography.labelSmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }

        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            if (wideBattleLayout) {
                val panelWidth = (maxWidth * 0.44f).coerceIn(268.dp, 390.dp)
                val arenaWidth = (maxWidth - panelWidth - 8.dp).coerceAtLeast(150.dp)
                val arenaSize = minOf(arenaWidth, maxHeight)
                Row(Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    arenaContent(Modifier.size(arenaSize))
                    Column(
                        Modifier.width(panelWidth).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        content = teamContent
                    )
                }
            } else {
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
                        arenaContent(Modifier.fillMaxSize())
                    }
                    Column(
                        Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        content = teamContent
                    )
                }
            }
        }
    }

    if (showBattleDebug && debugToolsEnabled && snapshot != null) {
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
                                    Text("Última rejeição: $reason", color = Color(0xFFFF98AB),
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

    if (showStrategyMenu && selectedAlly != null) {
        AlertDialog(
            onDismissRequest = { showStrategyMenu = false },
            title = { Text("Estratégia de ${selectedAlly.displayName}") },
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
            confirmButton = { Button(onClick = { showStrategyMenu = false }) { Text("Fechar") } }
        )
    }

    when (menu) {
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
            title = { Text("Encerrar treino?") },
            text = { Text("A sessão será descartada. Nenhum item ou progresso real foi alterado.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.abandon()
                        viewModel.finishSession()
                        onExit()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB34363))
                ) { Text("Sair do treino") }
            },
            dismissButton = { OutlinedButton(onClick = ::cancelExit) { Text("Continuar") } }
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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()
    val state by viewModel.state.collectAsState()
    val snapshot = state.snapshot
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
    @Suppress("UNUSED_VARIABLE")
    val cameraFrameVersion = projectionRevision

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
    LaunchedEffect(battleActive, menu, tacticalMenuPause, showStrategyMenu, showExitDialog, manualPause) {
        viewModel.setPaused("tech-menu", battleActive && menu == TrainingTacticalMenu.TECHNIQUES && tacticalMenuPause)
        viewModel.setPaused("item-menu", battleActive && menu == TrainingTacticalMenu.ITEMS && tacticalMenuPause)
        viewModel.setPaused("strategy-menu", battleActive && showStrategyMenu && tacticalMenuPause)
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
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(state.sessionId) {
        viewModel.setPaused("scene", true)
        onDispose { viewModel.setPaused("scene", true) }
    }

    fun issue(actor: String, action: TrainerAction, interrupt: Boolean = false) {
        val update = viewModel.issueOrder(actor, action, interrupt) ?: return
        commandFeedback = if (update.status == OrderStatus.FAILED) {
            update.reason
        } else if (update.status == OrderStatus.QUEUED) {
            "Ordem recebida · será executada assim que possível."
        } else {
            "Comando aplicado."
        }
    }

    fun closeMenu() {
        menu = null
        viewModel.setPaused("tech-menu", false)
        viewModel.setPaused("item-menu", false)
    }

    BackHandler(enabled = battleActive) {
        when {
            menu != null -> closeMenu()
            showStrategyMenu -> showStrategyMenu = false
            showExitDialog -> showExitDialog = false
            snapshot?.result != null -> {
                viewModel.finishSession()
                onExit()
            }
            else -> showExitDialog = true
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(5.dp)
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
                (fadeIn(tween(380)) + scaleIn(tween(380), initialScale = 0.98f)) togetherWith
                    (fadeOut(tween(260)) + scaleOut(tween(260), targetScale = 1.02f))
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
                            if (allies.any { it.combatantId == fighterId }) selectedAllyId = fighterId
                            if (opponents.any { it.combatantId == fighterId }) selectedOpponentId = fighterId
                        },
                        onAssetError = {
                            sceneReady = false
                            rendererError = it
                            viewModel.setPaused("renderer-error", true)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    snapshot?.takeIf { sceneReady }?.let { current ->
                        BattleVfxOverlay(
                            snapshot = current,
                            fighters = state.fighters,
                            sceneView = sceneView
                        )
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
                                Text("Transformando o Radar em arena…", color = TextPrimaryOnDark)
                                LinearProgressIndicator(color = VitalCyan, trackColor = DeepPurpleBgAlt)
                            }
                        }
                    }
                    if (sceneReady && state.countdown > 0 && snapshot?.result == null) {
                        Surface(
                            Modifier.align(Alignment.Center),
                            color = Color(0xD0100D1A),
                            shape = CutCornerShape(8.dp)
                        ) {
                            Column(
                                Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Prepare-se", color = TextPrimaryOnDark)
                                Text("${state.countdown}", color = VitalCyan, style = MaterialTheme.typography.displayMedium)
                            }
                        }
                    }
                    (rendererError ?: state.error)?.let { error ->
                        Surface(
                            Modifier.align(Alignment.BottomCenter).padding(8.dp),
                            color = Color(0xF02A1426),
                            shape = CutCornerShape(6.dp),
                            border = BorderStroke(1.dp, Color(0xFFFF7C96))
                        ) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(error, color = TextPrimaryOnDark, style = MaterialTheme.typography.bodySmall)
                                OutlinedButton(onClick = {
                                    if (state.error != null) viewModel.retry(context) else sceneView?.retryScene()
                                }) { Text("Tentar novamente") }
                            }
                        }
                    }
                    snapshot?.result?.let { result ->
                        BattleResultPanel(
                            outcome = result.outcome,
                            statistics = result.statistics,
                            elapsedMillis = result.elapsedMillis,
                            outcomeText = ::radarOutcomeLabel,
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
            BattleTeamRow(
                title = "SEU DIGIMON",
                members = allies,
                selectedId = selectedAlly?.combatantId,
                color = VitalCyan,
                onSelect = { selectedAllyId = it }
            )
            BattleTeamRow(
                title = "DIGIMON SELVAGEM",
                members = opponents,
                selectedId = selectedOpponent?.combatantId,
                color = Color(0xFFFF7899),
                onSelect = { selectedOpponentId = it }
            )
            BattleCommandBar(
                selectedAlly = selectedAlly,
                selectedOpponent = selectedOpponent,
                availableCommandPoints = ((snapshot?.commandPoints ?: 0) -
                    (snapshot?.reservedCommandPoints ?: 0)).coerceAtLeast(0),
                supportReady = snapshot != null && !snapshot.isPaused &&
                    selectedAlly?.combatantId?.let(snapshot.pendingSupportCombatantIds::contains) == true,
                commandsEnabled = snapshot != null && snapshot.result == null && sceneReady &&
                    rendererError == null && state.countdown == 0,
                manuallyPaused = manualPause,
                onToggleManualPause = { manualPause = !manualPause },
                tacticalMenuPause = tacticalMenuPause,
                onToggleTacticalPause = { tacticalMenuPause = !tacticalMenuPause },
                onFocus = { ally, target -> issue(ally, TrainerAction.FocusTarget(target)) },
                onDefend = { ally -> issue(ally, TrainerAction.Defend(), true) },
                onSupport = { ally -> issue(ally, TrainerAction.Support) },
                onMove = { ally, away ->
                    issue(ally, if (away) TrainerAction.KeepDistance else TrainerAction.MoveCloser, true)
                },
                onSpecial = { ally, target ->
                    val technique = TrainingBattleFactory.techniques.first { it.kind == TechniqueKind.SPECIAL }
                    issue(ally, TrainerAction.UseTechnique(technique.techniqueId, target))
                },
                onOpenTechniques = {
                    menu = TrainingTacticalMenu.TECHNIQUES
                    if (tacticalMenuPause) viewModel.setPaused("tech-menu", true)
                },
                onOpenItems = {
                    menu = TrainingTacticalMenu.ITEMS
                    if (tacticalMenuPause) viewModel.setPaused("item-menu", true)
                },
                onOpenStrategies = { showStrategyMenu = true }
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = commandFeedback ?: snapshot?.let { eventSummary(it, state.fighters) }
                        ?: "Preparando combate…",
                    color = TextSecondaryOnDark,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(onClick = { showExitDialog = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Sair")
                }
            }
        } else {
            radarControls()
        }
    }

    if (battleActive && showStrategyMenu && selectedAlly != null) {
        AlertDialog(
            onDismissRequest = { showStrategyMenu = false },
            title = { Text("Estratégia de ${selectedAlly.displayName}") },
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
            confirmButton = { OutlinedButton(onClick = { showStrategyMenu = false }) { Text("Fechar") } }
        )
    }

    when (if (battleActive) menu else null) {
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
            onDismissRequest = { showExitDialog = false },
            title = { Text("Sair da batalha?") },
            text = { Text("A luta será encerrada sem alterar o win rate.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.abandon()
                        viewModel.finishSession()
                        onExit()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB34363))
                ) { Text("Sair da batalha") }
            },
            dismissButton = { OutlinedButton(onClick = { showExitDialog = false }) { Text("Continuar") } }
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
    Box(Modifier.fillMaxSize()) {
        snapshot.projectiles.forEach { projectile ->
            val point = sceneView?.projectBattlePosition(
                projectile.position.x, PROJECTILE_HEIGHT, projectile.position.z
            )
            val image = fighters[projectile.ownerId]?.attackVisuals?.get(projectile.visual ?: "small")
            if (point != null && image != null) {
                val size = if (projectile.visual == "large") 36.dp else 24.dp
                val next = sceneView?.projectBattlePosition(
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

        val combatantsById = (snapshot.alliedMembers + snapshot.opposingMembers)
            .associateBy { it.combatantId }
        snapshot.impacts.forEach { impact ->
            val target = combatantsById[impact.targetId] ?: return@forEach
            val point = sceneView?.projectBattlePosition(target.position.x, 1.0f, target.position.z)
            if (point != null) {
                DamageImpactOverlay(
                    bitmap = fighters[impact.targetId]?.impactVisual,
                    damage = impact.damage,
                    critical = impact.critical,
                    remainingMillis = impact.remainingMillis,
                    x = with(density) { point.first.toDp() } - 31.dp,
                    y = with(density) { point.second.toDp() } - 32.dp
                )
            }
        }
    }
}

@Composable
private fun ProjectileImage(bitmap: Bitmap, x: androidx.compose.ui.unit.Dp, y: androidx.compose.ui.unit.Dp, size: androidx.compose.ui.unit.Dp, rotation: Float) {
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = "Projétil de batalha",
        modifier = Modifier.offset(x, y).size(size).graphicsLayer {
            rotationZ = rotation
            scaleX = -1f
        }
    )
}

@Composable
private fun DamageImpactOverlay(
    bitmap: Bitmap?,
    damage: Int,
    critical: Boolean,
    remainingMillis: Long,
    x: androidx.compose.ui.unit.Dp,
    y: androidx.compose.ui.unit.Dp
) {
    Box(
        modifier = Modifier.offset(x, y).size(width = 64.dp, height = 68.dp)
            .graphicsLayer { alpha = (remainingMillis / 320f).coerceIn(0f, 1f) },
        contentAlignment = Alignment.Center
    ) {
        bitmap?.let {
            Image(bitmap = it.asImageBitmap(), contentDescription = "Impacto", modifier = Modifier.size(56.dp))
        }
        Text(
            text = if (critical) "CRÍTICO · $damage" else "$damage",
            color = if (critical) VitalCyan else Color.White,
            fontWeight = FontWeight.Black,
            fontSize = if (critical) 13.sp else 14.sp,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun FighterSceneLabel(fighter: CombatantSnapshot, x: androidx.compose.ui.unit.Dp, y: androidx.compose.ui.unit.Dp) {
    Surface(
        modifier = Modifier.offset(x, y).width(84.dp),
        color = Color(0xCF100D1A),
        shape = CutCornerShape(4.dp),
        border = BorderStroke(1.dp, if (fighter.side == BattleSide.ALLIED) VitalCyan else Color(0xFFFF7899))
    ) {
        Column(Modifier.padding(horizontal = 5.dp, vertical = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(fighter.displayName, color = TextPrimaryOnDark, style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            LinearProgressIndicator(
                progress = { fighter.health.toFloat() / fighter.maxHealth.coerceAtLeast(1) },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = if (fighter.side == BattleSide.ALLIED) VitalCyan else Color(0xFFFF7899),
                trackColor = Color(0xFF45394C)
            )
        }
    }
}

@Composable
private fun BattleTeamRow(
    title: String,
    members: List<CombatantSnapshot>,
    selectedId: String?,
    color: Color,
    onSelect: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (members.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth().height(44.dp),
                color = DeepPurpleBgAlt,
                shape = CutCornerShape(5.dp),
                border = BorderStroke(1.dp, SurfaceStroke)
            ) {
                Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.padding(horizontal = 9.dp)) {
                    Text("Preparando equipe…", color = TextSecondaryOnDark,
                        style = MaterialTheme.typography.labelSmall)
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                members.forEach { member ->
                    val selected = member.combatantId == selectedId
                    Surface(
                        modifier = Modifier.weight(1f).height(44.dp).clickable { onSelect(member.combatantId) },
                        color = if (selected) color.copy(alpha = 0.13f) else DeepPurpleBgAlt,
                        shape = CutCornerShape(5.dp),
                        border = BorderStroke(1.dp, if (selected) color else SurfaceStroke)
                    ) {
                        Column(Modifier.fillMaxSize().padding(horizontal = 7.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(member.displayName, color = TextPrimaryOnDark,
                                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelMedium)
                            LinearProgressIndicator(
                                progress = { member.health.toFloat() / member.maxHealth.coerceAtLeast(1) },
                                modifier = Modifier.fillMaxWidth().height(3.dp),
                                color = color, trackColor = Color(0xFF45394C)
                            )
                            Text("HP ${member.health}/${member.maxHealth} · EN ${member.energy}/${member.maxEnergy} · ${stateLabel(member.state)}",
                                color = TextSecondaryOnDark, style = MaterialTheme.typography.labelSmall,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BattleCommandBar(
    selectedAlly: CombatantSnapshot?,
    selectedOpponent: CombatantSnapshot?,
    availableCommandPoints: Int,
    supportReady: Boolean,
    commandsEnabled: Boolean,
    manuallyPaused: Boolean,
    onToggleManualPause: () -> Unit,
    tacticalMenuPause: Boolean,
    onToggleTacticalPause: () -> Unit,
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
    val special = TrainingBattleFactory.techniques.first { it.kind == TechniqueKind.SPECIAL }
    val specialReady = alive && targetAlive && availableCommandPoints >= special.commandPointCost &&
        selectedAlly.energy - selectedAlly.reservedEnergy >= special.energyCost &&
        (selectedAlly.cooldownsMillis[special.techniqueId] ?: 0) == 0L
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        CommandRow {
            CommandButton("Técnicas", enabled = alive, primary = true, onClick = onOpenTechniques)
            CommandButton("Itens", enabled = alive, onClick = onOpenItems)
            CommandButton("Defender", enabled = alive) { selectedAlly?.let { onDefend(it.combatantId) } }
        }
        CommandRow {
            CommandButton("Apoiar · $availableCommandPoints", enabled = supportReady && commandsEnabled) {
                selectedAlly?.let { onSupport(it.combatantId) }
            }
            CommandButton("Especial · ${special.commandPointCost} CP", enabled = specialReady) {
                selectedAlly?.let { onSpecial(it.combatantId, selectedOpponent?.combatantId) }
            }
            CommandButton("Focar", enabled = alive && targetAlive) {
                if (selectedAlly != null && selectedOpponent != null) onFocus(selectedAlly.combatantId, selectedOpponent.combatantId)
            }
        }
        CommandRow {
            CommandButton("Estratégia", enabled = alive, onClick = onOpenStrategies)
            CommandButton("Aproximar", enabled = alive) { selectedAlly?.let { onMove(it.combatantId, false) } }
            CommandButton("Afastar", enabled = alive) { selectedAlly?.let { onMove(it.combatantId, true) } }
        }
        CommandRow {
            CommandButton(if (manuallyPaused) "Continuar" else "Pausar", enabled = commandsEnabled,
                onClick = onToggleManualPause)
            CommandButton("Tática · ${if (tacticalMenuPause) "ON" else "OFF"}", enabled = commandsEnabled,
                onClick = onToggleTacticalPause)
            Spacer(Modifier.weight(1f).height(40.dp))
        }
    }
}

@Composable
private fun CommandRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically, content = content)
}

@Composable
private fun RowScope.CommandButton(label: String, enabled: Boolean = true, primary: Boolean = false, onClick: () -> Unit) {
    if (primary) {
        Button(onClick = onClick, enabled = enabled, modifier = Modifier.weight(1f).height(40.dp),
            contentPadding = PaddingValues(horizontal = 3.dp, vertical = 2.dp),
            shape = CutCornerShape(6.dp), colors = ButtonDefaults.buttonColors(
                containerColor = VitalPurpleBright, contentColor = Color(0xFF0A0812)
            )) {
            Text(label, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall)
        }
    } else {
        OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.weight(1f).height(40.dp),
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
    TacticalDialogFrame(title = "Técnicas · ${selectedAlly.displayName}", onDismiss = onDismiss) {
        Text(if (tacticalPauseEnabled) "Pausa tática ativa enquanto você escolhe." else "O combate continua enquanto você escolhe.", color = VitalCyan,
            style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 5.dp))
        TrainingBattleFactory.techniques.forEach { technique ->
            val special = technique.kind == TechniqueKind.SPECIAL
            val isHeal = technique.kind == TechniqueKind.HEAL
            val targetId = if (isHeal) selectedAlly.combatantId else selectedOpponent?.combatantId
            val ready = (selectedAlly.cooldownsMillis[technique.techniqueId] ?: 0L) <= 0L
            val hasResources = selectedAlly.energy - selectedAlly.reservedEnergy >= technique.energyCost &&
                ((snapshot?.commandPoints ?: 0) - (snapshot?.reservedCommandPoints ?: 0)) >= technique.commandPointCost
            OutlinedButton(
                onClick = { targetId?.let { onTechnique(technique, selectedAlly.combatantId, it) } },
                enabled = selectedAlly.health > 0 && targetId != null && ready && hasResources,
                modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp),
                shape = CutCornerShape(5.dp), border = BorderStroke(1.dp, if (special) VitalPurpleBright else SurfaceStroke)
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(technique.displayName, color = TextPrimaryOnDark, fontWeight = FontWeight.SemiBold)
                        Text(if (special) "${technique.energyCost} EN · ${technique.commandPointCost} CP" else "${technique.energyCost} EN",
                            color = VitalCyan, style = MaterialTheme.typography.labelSmall)
                    }
                    Text(
                        text = when {
                            !ready -> "Recarga: ${((selectedAlly.cooldownsMillis[technique.techniqueId] ?: 0) / 1000f).let { "%.1f".format(it) }} s"
                            !hasResources -> "Energia ou pontos de comando insuficientes"
                            isHeal -> "Cura ${selectedAlly.displayName} · poder ${technique.healPower}"
                            else -> "${selectedOpponent?.displayName ?: "Sem alvo"} · alcance ${technique.minRange}–${technique.maxRange} · poder ${technique.power}"
                        },
                        color = TextSecondaryOnDark, style = MaterialTheme.typography.labelSmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Retomar combate") }
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
    val definitions = TrainingBattleFactory.trainingItems
    TacticalDialogFrame(title = "Itens de treino", onDismiss = onDismiss) {
        Text("${if (tacticalPauseEnabled) "Pausa tática ativa" else "O combate continua"} · quantidades reiniciam na revanche.", color = VitalCyan,
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
                        Text(item.displayName, color = TextPrimaryOnDark, fontWeight = FontWeight.SemiBold)
                        Text("×$available", color = VitalCyan, fontWeight = FontWeight.Bold)
                    }
                    Text(itemDescription(item), color = TextSecondaryOnDark,
                        style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Retomar combate") }
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
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Fechar") }
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
    outcomeText: (BattleOutcome) -> String = ::outcomeLabel,
    onRematch: (() -> Unit)?,
    onReturn: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier.fillMaxWidth(), color = Color(0xF0161024), shape = CutCornerShape(9.dp),
        border = BorderStroke(1.dp, VitalCyan)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(outcomeText(outcome), color = TextPrimaryOnDark, style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold)
            Text("Duração ${formatDuration(elapsedMillis)}", color = VitalCyan, style = MaterialTheme.typography.labelLarge)
            Text("Dano ${statistics.damageDealt} · recebido ${statistics.damageReceived} · prevenido ${statistics.damagePrevented}",
                color = TextSecondaryOnDark, style = MaterialTheme.typography.bodySmall)
            Text("Curas ${statistics.healingDone} · ordens ${statistics.ordersCompleted} · incentivos ${statistics.supportCommands} · itens ${statistics.itemsUsed} · projéteis ${statistics.projectilesHit}/${statistics.projectilesHit + statistics.projectilesMissed}",
                color = TextSecondaryOnDark, style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onRematch != null) {
                    Button(onClick = onRematch, modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = VitalPurpleBright, contentColor = Color(0xFF0A0812))) {
                        Text("Revanche", fontWeight = FontWeight.Bold)
                    }
                }
                OutlinedButton(onClick = onReturn, modifier = Modifier.weight(1f)) { Text("Voltar") }
            }
        }
    }
}

private fun eventSummary(snapshot: BattleSnapshot, fighters: Map<String, BattleFighterPresentation>): String {
    val event = snapshot.recentEvents.lastOrNull() ?: return "Os parceiros lutam de forma autônoma."
    fun name(id: String) = fighters[id]?.displayName ?: id.substringAfter(':')
    return when (event) {
        is BattleEvent.TechniqueHit -> "${name(event.combatantId)} acertou ${name(event.targetId)} · ${event.amount} dano${if (event.critical) " crítico" else ""}"
        is BattleEvent.TechniqueMissed -> "${name(event.combatantId)} não conseguiu usar a técnica: ${event.reason}"
        is BattleEvent.ProjectileMissed -> "Um projétil errou o alvo."
        is BattleEvent.ItemUsed -> "${name(event.combatantId)} usou item em ${name(event.targetId)} · ${event.amount}"
        is BattleEvent.SupportSucceeded -> "${name(event.combatantId)} recebeu incentivo · +${event.commandPointsGained} CP"
        is BattleEvent.SupportWindowOpened -> "${name(event.combatantId)} pode receber incentivo por ${((event.expiresAtMillis - snapshot.elapsedMillis).coerceAtLeast(0L) / 1_000f).roundToInt()} s"
        is BattleEvent.CombatantDefeated -> "${name(event.combatantId)} foi derrotado."
        is BattleEvent.OrderChanged -> "Ordem ${event.update.status.name.lowercase()}: ${event.update.reason ?: "parceiro"}"
        is BattleEvent.ProjectileLaunched -> "${name(event.ownerId)} lançou um projétil."
        is BattleEvent.TechniqueStarted -> "${name(event.combatantId)} iniciou uma técnica."
        is BattleEvent.StatusApplied -> "Status ${event.statusId} aplicado."
        is BattleEvent.StateChanged, is BattleEvent.TargetChanged -> "Os parceiros estão reposicionando no Coliseu."
        is BattleEvent.BattleEnded -> outcomeLabel(event.result.outcome)
    }
}

private fun stateLabel(state: CombatantState): String = when (state) {
    CombatantState.IDLE, CombatantState.SELECT_TARGET, CombatantState.WAITING -> "Pronto"
    CombatantState.MOVE_TO_TARGET, CombatantState.MOVE_AWAY, CombatantState.POSITIONING -> "Movendo"
    CombatantState.ATTACK_STARTUP, CombatantState.ATTACK_ACTIVE -> "Atacando"
    CombatantState.RECOVERING -> "Recupera"
    CombatantState.DEFENDING -> "Defende"
    CombatantState.STUNNED -> "Atordoado"
    CombatantState.KNOCKBACK -> "Recuo"
    CombatantState.USING_SPECIAL -> "Especial"
    CombatantState.USING_ITEM_EFFECT -> "Item"
    CombatantState.DEFEATED -> "Derrotado"
}

private fun strategyLabel(strategy: BattleStrategy): String = when (strategy) {
    BattleStrategy.AGGRESSIVE -> "Agressiva"
    BattleStrategy.BALANCED -> "Equilibrada"
    BattleStrategy.CONSERVATIVE -> "Conservadora"
    BattleStrategy.DEFENSIVE -> "Defensiva"
    BattleStrategy.RANGED -> "Longa distância"
    BattleStrategy.SUPPORT -> "Suporte"
}

private fun itemDescription(item: BattleItemDefinition): String = when (item.kind) {
    BattleItemKind.HEAL_HEALTH -> "Recupera até ${item.amount} HP do parceiro selecionado."
    BattleItemKind.RESTORE_ENERGY -> "Recupera até ${item.amount} de energia do parceiro selecionado."
    BattleItemKind.CLEANSE_STATUS -> "Remove efeitos negativos do parceiro selecionado."
}

private fun pauseReasonLabel(reason: String?): String = when (reason) {
    "tech-menu" -> "escolha uma técnica"
    "item-menu" -> "escolha um item"
    "exit-dialog" -> "confirme a saída"
    "background" -> "app em segundo plano"
    "screen" -> "tela suspensa"
    "scene" -> "carregando a arena"
    "intro" -> "preparando o combate"
    "manual" -> "pausa manual"
    "strategy-menu" -> "escolha a estratégia"
    else -> "tática"
}

private fun outcomeLabel(outcome: BattleOutcome): String = when (outcome) {
    BattleOutcome.ALLIED_VICTORY -> "Vitória no treino"
    BattleOutcome.OPPOSING_VICTORY -> "Derrota no treino"
    BattleOutcome.DRAW -> "Empate no treino"
    BattleOutcome.ABANDONED -> "Treino encerrado"
}

private fun radarOutcomeLabel(outcome: BattleOutcome): String = when (outcome) {
    BattleOutcome.ALLIED_VICTORY -> "Vitória no Radar"
    BattleOutcome.OPPOSING_VICTORY -> "Derrota no Radar"
    BattleOutcome.DRAW -> "Empate no Radar"
    BattleOutcome.ABANDONED -> "Batalha encerrada"
}

private fun formatDuration(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1_000L).coerceAtLeast(0L)
    return "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

private const val PROJECTILE_HEIGHT = 0.85f
private const val LABEL_HEIGHT = 1.9f
