package com.tamerslab.world.ui

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import com.tamerslab.components.DimLogo
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import com.tamerslab.components.showAppFeedback
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavBackStackEntry
import com.tamerslab.R
import com.tamerslab.ar.ArAvailability
import com.tamerslab.ar.ArBattleContent
import com.tamerslab.ar.ArBattleStage
import com.tamerslab.ar.ArPlacement
import com.tamerslab.ar.ArStagePhase
import com.tamerslab.ar.checkArAvailability
import com.tamerslab.components.motionEnabled
import com.tamerslab.components.VitalButton
import com.tamerslab.components.cyberFrame
import com.tamerslab.di.TamersLabApplication
import com.tamerslab.domain.card.Card
import com.tamerslab.dtos.CharacterDtos
import com.tamerslab.dtos.DebugSpawnCharacter
import com.tamerslab.dtos.WorldDtos
import com.tamerslab.navigation.AppRoute
import com.tamerslab.navigation.NavigationItems
import com.tamerslab.battle.ui.offlineBattleParticipant
import com.tamerslab.species.ui.card.dialogs.DexCharaDetailsDialog
import com.tamerslab.battle.ui.offline.OfflineArenaManifest
import com.tamerslab.battle.ui.offline.WorldRadarBattleContent
import com.tamerslab.battle.ui.offline.offlineBattleViewModel
import com.tamerslab.species.data.DexRepository
import com.tamerslab.ui.theme.RadarCompass
import com.tamerslab.ui.theme.RadarFollower
import com.tamerslab.ui.theme.SpaceBlack
import com.tamerslab.ui.theme.SurfaceDeepPurple
import com.tamerslab.ui.theme.SurfaceElevatedPurple
import com.tamerslab.ui.theme.SurfaceStroke
import com.tamerslab.ui.theme.TextPrimaryOnDark
import com.tamerslab.ui.theme.TextSecondaryOnDark
import com.tamerslab.ui.theme.VitalCyan
import com.tamerslab.utils.BitmapData
import com.tamerslab.utils.getBitmap
import com.tamerslab.world.WorldBiome
import com.tamerslab.world.GeoPoint
import com.tamerslab.world.RadarWorldGeometry
import com.tamerslab.world.ecosystem.WorldInteraction
import com.tamerslab.world.ecosystem.WorldInteractionException
import com.tamerslab.world.ecosystem.InteractionFailure
import com.tamerslab.world.ecosystem.WorldPlayerFix
import com.tamerslab.world.ecosystem.EcosystemSeed
import com.tamerslab.world.ecosystem.EcosystemStatus
import com.tamerslab.world.ecosystem.RadarCommand
import com.tamerslab.world.ecosystem.RadarCommandStamp
import com.tamerslab.world.ecosystem.RadarCommandKind
import com.tamerslab.world.ecosystem.RadarCommandResult
import com.tamerslab.world.ecosystem.RadarCommandException
import com.tamerslab.world.ecosystem.RadarRejection
import com.tamerslab.world.ecosystem.WorldPauseReason
import com.tamerslab.world.ecosystem.InteractionOrigin
import com.tamerslab.world.ecosystem.InteractionType
import com.tamerslab.world.ecosystem.InteractionState
import com.tamerslab.world.ecosystem.EcosystemInteractionSummary
import com.tamerslab.world.ecosystem.commandRejection
import com.tamerslab.world.ecosystem.getOrThrow
import com.tamerslab.world.ecosystem.displayPosition
import com.tamerslab.world.ecosystem.publicInteractionFor
import com.tamerslab.world.ecosystem.speechFor
import com.tamerslab.world.ecosystem.isPlayerBattle
import com.tamerslab.battle.offline.core.BattleOutcome
import com.tamerslab.battle.offline.session.BattleSceneChannel
import com.tamerslab.world.worldRadarBattleParticipant
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Radius, in meters, within which the player can tap a Digimon to chat. */
private const val INTERACTION_RANGE_METERS = RadarWorldGeometry.INTERACTION_RANGE_METERS
private const val GRID_SIZE_METERS = 60.0
private const val RADAR_RING_INTERVAL_METERS = 200

@Composable
fun RadarScreen(
    navController: NavController,
    worldEntry: NavBackStackEntry,
    selectedWorldTab: Int = 0,
    onWorldTabSelected: (Int) -> Unit = {},
    onFullScreenBattleChanged: (Boolean) -> Unit = {},
    /** Radar renderer; the isometric world is the default and the legacy views are Debug-only. */
    legacyView: RadarLegacyView = RadarLegacyView.NONE,
    onLegacyViewChanged: (RadarLegacyView) -> Unit = {},
    labelsPreferred: Boolean = false,
    onLabelsPreferenceChanged: (Boolean) -> Unit = {},
    /** "AR battles" radar preference, hoisted/persisted like the legacy view choice. */
    arPreferred: Boolean = false,
    onArPreferenceChanged: (Boolean) -> Unit = {},
    /** The legacy World route still shows Radar|Digifarm tabs floating over the radar world. */
    showSectionTabs: Boolean = true
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val app = context.applicationContext as TamersLabApplication
    val ecosystem = app.container.worldEcosystemCoordinator
    val worldSnapshot by ecosystem.snapshot.collectAsStateWithLifecycle()
    var legacyName by rememberSaveable { mutableStateOf(legacyView.name) }
    val hoistedLegacy = runCatching { RadarLegacyView.valueOf(legacyName) }.getOrDefault(RadarLegacyView.NONE)
    LaunchedEffect(hoistedLegacy) { onLegacyViewChanged(hoistedLegacy) }
    var showRadarOptions by rememberSaveable { mutableStateOf(false) }
    var isoProjection by remember { mutableStateOf(emptyList<RadarProjection>()) }
    var isoFailed by remember { mutableStateOf(false) }
    val isometricRendererPresent = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    var requestedBattleRenderer by remember { mutableStateOf(false) }
    val rendererOwnership = remember { RadarRendererOwnership() }
    val rendererState by rendererOwnership.state.collectAsStateWithLifecycle()
    val battleRendererPresent = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    val firstPersonRendererPresent = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    var cameraPitch by rememberSaveable { mutableFloatStateOf(RADAR_DEFAULT_PITCH) }
    val currentPitch by rememberUpdatedState(cameraPitch)
    var showEntityLabels by rememberSaveable { mutableStateOf(labelsPreferred) }
    LaunchedEffect(showEntityLabels) { onLabelsPreferenceChanged(showEntityLabels) }
    var arBattleEnabled by rememberSaveable { mutableStateOf(arPreferred) }
    LaunchedEffect(arBattleEnabled) { onArPreferenceChanged(arBattleEnabled) }
    var fpProjection by remember { mutableStateOf(emptyList<RadarProjection>()) }
    var firstPersonViewportWidth by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val radarLease = remember { "radar:${java.util.UUID.randomUUID()}" }
    var radarResumed by remember { mutableStateOf(false) }
    val viewModelOwner = checkNotNull(LocalViewModelStoreOwner.current)
    val battleViewModel = remember(viewModelOwner) { offlineBattleViewModel(viewModelOwner) }
    val battleSessionState by battleViewModel.state.collectAsStateWithLifecycle()
    var location by remember { mutableStateOf<Location?>(null) }
    var origin by remember { mutableStateOf<GeoPoint?>(null) }
    var selectedEncounterId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedEncounterName by rememberSaveable { mutableStateOf<String?>(null) }
    var observedInteractionId by rememberSaveable { mutableStateOf<String?>(null) }
    var incomingChallengeId by rememberSaveable { mutableStateOf<String?>(null) }
    var privateChallenge by remember { mutableStateOf<com.tamerslab.world.ecosystem.WorldDialogueIntent?>(null) }
    // The outgoing destination remains composed during tab transitions, even
    // after it is removed from NavController's active back stack.
    val worldState = remember(worldEntry) { worldEntry.savedStateHandle }
    val interactionPayload by remember(worldState) { worldState.getStateFlow<String?>("radar-interaction",null) }.collectAsStateWithLifecycle()
    LaunchedEffect(interactionPayload) {
        interactionPayload?.let { observedInteractionId=it;worldState["radar-interaction"]=null }
    }
    val challengePayload by remember(worldState) {
        worldState.getStateFlow<String?>("radar-challenge",null)
    }.collectAsStateWithLifecycle()
    LaunchedEffect(challengePayload) {
        challengePayload?.let { id ->
            incomingChallengeId=id
            privateChallenge=withContext(Dispatchers.IO) { app.container.db.worldInteractionDao().getIntent(id) }
            worldState.set<String?>("radar-challenge",null)
        }
    }
    var preparingSpawnId by remember { mutableStateOf<Long?>(null) }
    var debugSpawnInProgress by remember { mutableStateOf(false) }
    var showDebugSpawnPicker by rememberSaveable { mutableStateOf(false) }
    var debugSpawnCharacters by remember { mutableStateOf<List<DebugSpawnCharacter>?>(null) }
    var debugSpawnPickerFailed by remember { mutableStateOf(false) }
    var debugSpawnPickerAttempt by remember { mutableIntStateOf(0) }
    val debugSettingsRepository = app.container.debugSettingsRepository
    val debugSettings by debugSettingsRepository.state.collectAsStateWithLifecycle()
    LaunchedEffect(debugSettings.enabled) {
        if (!debugSettings.enabled) {
            showDebugSpawnPicker = false
            debugSpawnCharacters = null
        }
    }
    val debugEnabled = debugSettings.enabled
    // The legacy renderers are a Debug instrument: ordinary use always gets the isometric world.
    LaunchedEffect(debugEnabled, hoistedLegacy) {
        if (!debugEnabled && hoistedLegacy != RadarLegacyView.NONE) legacyName = RadarLegacyView.NONE.name
    }
    val effectiveLegacy = if (debugEnabled) hoistedLegacy else RadarLegacyView.NONE
    val cardinalNames = stringArrayResource(R.array.world_cardinal_directions_8).toList()
    DisposableEffect(debugSettingsRepository) {
        onDispose { debugSettingsRepository.setAnyDistance(false) }
    }
    var battleSpawnId by rememberSaveable { mutableStateOf<Long?>(null) }
    var battleCharacterId by rememberSaveable { mutableStateOf<Long?>(null) }
    var battleSessionId by rememberSaveable { mutableStateOf<String?>(null) }
    var committedBattleSessionId by rememberSaveable { mutableStateOf<String?>(null) }
    var battleTerminalOutcome by rememberSaveable { mutableStateOf<BattleOutcome?>(null) }
    var battleExitInProgress by remember { mutableStateOf(false) }
    var pendingBattleEventId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingBattleOutcome by rememberSaveable { mutableStateOf<BattleOutcome?>(null) }
    var pendingBattleSnapshot by remember { mutableStateOf<com.tamerslab.battle.offline.core.BattleSnapshot?>(null) }
    // AR battle entry: the stage is armed (camera/placement visible) before the battle starts and
    // stays mounted through SCANNING -> BATTLE until the battle exits or the user backs out.
    var arStageVisible by remember { mutableStateOf(false) }
    var arSpawn by remember { mutableStateOf<WorldDtos.SpawnWithDetails?>(null) }
    var arPlacement by remember { mutableStateOf<ArPlacement?>(null) }
    var arAvailability by remember { mutableStateOf<ArAvailability?>(null) }
    val battleActive = battleSessionId != null

    /** Removes the AR stage and forgets its placement so the next battle starts a fresh world. */
    fun tearDownArStage() {
        arStageVisible = false
        arPlacement = null
        arSpawn = null
        arAvailability = null
    }
    val desiredRenderer = when {
        battleActive || arStageVisible || requestedBattleRenderer -> RadarRendererOwner.BATTLE
        effectiveLegacy == RadarLegacyView.FIRST_PERSON && radarResumed && pendingBattleEventId == null ->
            RadarRendererOwner.FIRST_PERSON
        effectiveLegacy == RadarLegacyView.NONE && radarResumed && !battleActive && !arStageVisible &&
            pendingBattleEventId == null -> RadarRendererOwner.ISOMETRIC
        else -> RadarRendererOwner.NONE
    }
    LaunchedEffect(desiredRenderer) {
        rendererOwnership.request(desiredRenderer)
        // A loading/cancelled battle can own permission without having created a renderer.
        val owner=rendererOwnership.owner
        val present=when(owner) {
            RadarRendererOwner.BATTLE -> battleRendererPresent.get()
            RadarRendererOwner.FIRST_PERSON -> firstPersonRendererPresent.get()
            RadarRendererOwner.ISOMETRIC -> isometricRendererPresent.get()
            RadarRendererOwner.NONE -> false
        }
        if(owner!=desiredRenderer && owner!=RadarRendererOwner.NONE && !present) rendererOwnership.released(owner,rendererOwnership.generation)
    }
    LaunchedEffect(ecosystem, lifecycleOwner, radarLease) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            try {
                withContext(Dispatchers.IO) { ecosystem.acquire(radarLease) }
                radarResumed = true
                awaitCancellation()
            } finally {
                radarResumed = false
                withContext(NonCancellable + Dispatchers.IO) { ecosystem.release(radarLease) }
            }
        }
    }
    LaunchedEffect(ecosystem, radarResumed, battleActive, pendingBattleEventId, preparingSpawnId, worldSnapshot.status) {
        // The command gate owns live freezes. Only unwind a recovered, ownerless freeze here.
        if (radarResumed && !battleActive && pendingBattleEventId == null && preparingSpawnId == null &&
            battleSessionState.sessionId == null && worldSnapshot.status == EcosystemStatus.FROZEN &&
            worldSnapshot.interactions.none { it.origin == InteractionOrigin.DIRECT_PLAYER && it.type == InteractionType.BATTLE }) {
            withContext(Dispatchers.IO) { ecosystem.setBattlePaused(false) }
        }
    }
    val allowMotion = motionEnabled()
    LaunchedEffect(battleActive, arStageVisible) {
        // AR scanning is already an immersive camera view, so the battle chrome treats it as full screen.
        onFullScreenBattleChanged(battleActive || arStageVisible)
    }
    val explorationActive = radarResumed && !battleActive && !arStageVisible && pendingBattleEventId == null && worldSnapshot.status != EcosystemStatus.FROZEN
    val compass = rememberWorldCompass(location, enabled = explorationActive, diagnosticsEnabled = debugSettings.enabled)
    val heading = compass.heading ?: 0f // Uncalibrated map is explicitly north-up.
    val cardinalLabel = compass.heading?.let { cardinalDirection(heading) }
    var frozenRadarHeading by remember { mutableFloatStateOf(0f) }
    SideEffect {
        if (explorationActive) frozenRadarHeading = heading
    }
    val radarHeading = if (explorationActive) heading else frozenRadarHeading
    // Every flow here is remembered: the compass updates at sensor rate, and re-creating these Room
    // flows on each recomposition used to restart their queries thousands of times a second. That
    // churn is what made the nearby Digimon take so long to appear (the spawn list kept restarting
    // before its first emission and stayed empty).
    val spawnsFlow = remember { app.container.worldRepository.observeSpawns() }
    val spawns by spawnsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val questTasksFlow = remember { app.container.questRepository.observeTargetTasks() }
    val questTasks by questTasksFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val questTrialsFlow = remember { app.container.questRepository.observeBattleTargets() }
    val questTrials by questTrialsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var questInteractionInProgress by remember { mutableStateOf<String?>(null) }
    val questTarget by remember(worldState) { worldState.getStateFlow<String?>("radar-quest-target", null) }.collectAsStateWithLifecycle()
    LaunchedEffect(questTarget, spawns) {
        val target = spawns.firstOrNull { it.individualId == questTarget } ?: return@LaunchedEffect
        selectedEncounterId = target.individualId
        selectedEncounterName = target.speciesName
        worldState.set<String?>("radar-quest-target", null)
    }
    val activeCharacterFlow = remember { app.container.db.userCharacterDao().getActiveCharacter() }
    val activeOwned by activeCharacterFlow.collectAsStateWithLifecycle(initialValue = null)
    // The active partner is the radar's player token: its four stored frames become the extruded
    // 3D model standing at the centre of the isometric world.
    val playerSprite = remember(activeOwned) {
        activeOwned?.let { character ->
            val frames = buildMap {
                if (character.spriteIdle.isNotEmpty()) put("idle", character.spriteIdle)
                if (character.spriteIdle2.isNotEmpty()) put("idle2", character.spriteIdle2)
                if (character.spriteRun1.isNotEmpty()) put("walk", character.spriteRun1)
                if (character.spriteRun2.isNotEmpty()) put("walk2", character.spriteRun2)
            }
            if (frames.isEmpty()) null else RadarPlayerSprite(
                frames = frames,
                width = character.spriteWidth.coerceAtLeast(1),
                height = character.spriteHeight.coerceAtLeast(1),
                stage = character.stage,
            )
        }
    }
    val attemptedWildAttacks=remember { mutableMapOf<String,Int>() }
    val pendingRecruitsFlow = remember { app.container.worldRepository.observePendingRecruits() }
    val pendingRecruits by pendingRecruitsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val worldSpawnCardsFlow = remember { app.container.db.cardDao().observeCardsForWorldSpawns() }
    val worldSpawnCardsState by worldSpawnCardsFlow.collectAsStateWithLifecycle(initialValue = null)
    val worldSpawnCards = worldSpawnCardsState.orEmpty()
    val currentBiomeFlow = remember { app.container.worldRepository.currentBiome }
    val currentBiome by currentBiomeFlow.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    LaunchedEffect(showDebugSpawnPicker, debugSpawnPickerAttempt, debugSettings.enabled) {
        if (!showDebugSpawnPicker || !debugSettings.enabled) return@LaunchedEffect
        debugSpawnCharacters = null
        debugSpawnPickerFailed = false
        try {
            debugSpawnCharacters = withContext(Dispatchers.IO) { app.container.worldRepository.getDebugSpawnCharacters() }
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            debugSpawnPickerFailed = true
        }
    }
    // At the default zoom, the entire nearby spawn radius (350 m) is visible.
    // Users can still zoom in for detail or out to the full 1 km radar range.
    var zoom by rememberSaveable { mutableFloatStateOf(1f) }
    var idleFrame by remember { mutableIntStateOf(0) }
    var selectedSpecies by remember { mutableStateOf<CharacterDtos.CardCharaProgress?>(null) }
    var showWorldSpawnSettings by remember { mutableStateOf(false) }
    var selectedWorldDim by remember { mutableStateOf<Card?>(null) }
    var selectedWorldDimSpecies by remember { mutableStateOf(emptyList<WorldDimSpecies>()) }
    val radarPulse = remember { Animatable(0f) }
    val motionFrame = remember { mutableLongStateOf(System.nanoTime()) }
    LaunchedEffect(explorationActive, allowMotion) {
        if (explorationActive && allowMotion) {
            while (true) withFrameNanos { now ->
                if (now - motionFrame.longValue >= 33_333_333L) motionFrame.longValue = now
            }
        }
    }
    val playerPosition = radarPlayerPosition(worldSnapshot)
    val displayedCommandStamp = worldSnapshot.commandStamp
    val presentations = remember(worldSnapshot.individuals, spawns) { radarPresentations(worldSnapshot, spawns) }
    // The compass needle points at the nearest wild Digimon: its world bearing, plus the localized
    // cardinal for the layer's accessibility description.
    val nearestWildBearing = remember(presentations, playerPosition) {
        val player = playerPosition ?: return@remember null
        presentations.minByOrNull { presentation ->
            RadarWorldGeometry.relative(player, presentation.individual.position).distanceMeters
        }?.let { nearest ->
            RadarWorldGeometry.relative(player, nearest.individual.position).bearingDegrees.toFloat()
        }
    }
    val nearestWildCardinal = nearestWildBearing?.let { cardinalDirection(it) }
    LaunchedEffect(selectedEncounterId,worldSnapshot.interactions) {
        selectedEncounterId?.let { id -> worldSnapshot.publicInteractionFor(id)?.let { event ->
            selectedEncounterId=null
            observedInteractionId=event.id
        } }
    }
    var populationRefreshJob by remember { mutableStateOf<Job?>(null) }
    var populationRefreshInProgress by remember { mutableStateOf(false) }
    var populationRefreshFailed by remember { mutableStateOf(false) }
    var lastPopulationPosition by remember { mutableStateOf<GeoPoint?>(null) }
    var spawnAssetRefreshAttempted by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(explorationActive, allowMotion) {
        if (!allowMotion) idleFrame = 0
        while (explorationActive && allowMotion) {
            kotlinx.coroutines.delay(700L)
            idleFrame = 1 - idleFrame
        }
    }

    LaunchedEffect(explorationActive, allowMotion) {
        if (!allowMotion) radarPulse.snapTo(0f)
        while (explorationActive && allowMotion) {
            radarPulse.snapTo(0f)
            radarPulse.animateTo(1f, animationSpec = tween(durationMillis = 2_600))
            kotlinx.coroutines.delay(1_400L)
        }
    }

    val spawnBitmaps = remember(presentations, idleFrame, allowMotion) {
        presentations.mapNotNull { presentation ->
            val spawn = presentation.assets
            runCatching {
                val frame = presentation.spriteFrame(idleFrame, allowMotion)
                presentation to BitmapData(
                    bitmap = frame,
                    width = spawn.spriteWidth.coerceAtLeast(1),
                    height = spawn.spriteHeight.coerceAtLeast(1)
                ).getBitmap().asImageBitmap()
            }.getOrNull()
        }
    }

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        hasLocationPermission = granted.values.any { it } ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

    }

    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            launcher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    /**
     * Builds a radar command from the *latest* published command stamp.
     *
     * The visible stamp is only used to enable/disable controls. Submitting with the latest stamp
     * keeps an ordinary location update from cancelling the interaction: every accepted GPS fix
     * bumps the session revision, so a command built from the previously rendered stamp would be
     * rejected as a stale snapshot ("localization changed") even though claims, range and state
     * are revalidated inside the command transaction anyway.
     */
    fun latestCommandStamp(): RadarCommandStamp =
        ecosystem.snapshot.value.commandStamp ?: throw RadarCommandException(rejection = RadarRejection.NOT_READY)

    fun displayedCommand(kind: RadarCommandKind, id: String? = null): RadarCommand =
        RadarCommand(latestCommandStamp(), kind, id)

    suspend fun awaitBattleRenderer() {
        requestedBattleRenderer = true
        rendererOwnership.request(RadarRendererOwner.BATTLE)
        withTimeout(30_000) { rendererOwnership.state.first { it.owner==RadarRendererOwner.BATTLE && !it.releasing } }
    }

    fun showRadarFailure(failure: Throwable, fallback: Int) {
        val message = when (failure) {
            is RadarCommandException -> resources.getString(failure.messageResource())
            is WorldInteractionException -> resources.getString(failure.messageResource())
            else -> resources.getString(fallback)
        }
        context.showAppFeedback(message, important = true)
    }

    fun requestPopulationRefresh(force: Boolean = false) {
        val frame = ecosystem.snapshot.value
        val fix = frame.playerFix ?: return
        if (frame.status != EcosystemStatus.READY || !fix.isFresh(System.currentTimeMillis()) || populationRefreshJob?.isActive == true) return
        if (!force && lastPopulationPosition?.let { RadarWorldGeometry.relative(it, fix.position).withinRadius(60.0) } == true) return
        val epoch = frame.leaseEpoch
        populationRefreshInProgress = true
        populationRefreshFailed = false
        populationRefreshJob = scope.launch {
            try {
                val biome = withContext(Dispatchers.IO) {
                    app.container.worldRepository.prepareSpawns()
                    app.container.worldRepository.resolveSpawnBiome(fix.position)
                }
                val current = ecosystem.snapshot.value
                if (current.leaseEpoch != epoch) return@launch
                val result = withContext(Dispatchers.IO) {
                    // Build the command stamp at submission time: the biome lookup above is a long
                    // gap and an ordinary GPS fix during it would otherwise make this refresh stale.
                    ecosystem.executeCommand(radarLease, RadarCommand(latestCommandStamp(), RadarCommandKind.REGION)) { command ->
                        val latest = command.playerFix.position
                        if (!RadarWorldGeometry.relative(fix.position, latest).withinRadius(60.0)) {
                            throw RadarCommandException(rejection = RadarRejection.STALE_SNAPSHOT)
                        }
                        app.container.worldRepository.ensureSpawnsForBiome(latest.latitude, latest.longitude, biome)
                        latest
                    }
                }
                if (result is RadarCommandResult.Applied) {
                    lastPopulationPosition = result.value
                    app.container.worldRepository.publishSpawnBiome(biome)
                } else if (result is RadarCommandResult.Failed) populationRefreshFailed = true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                populationRefreshFailed = true
            } finally {
                populationRefreshInProgress = false
            }
        }
    }

    // The world session can outlive the sixteen-minute spawn window (an overnight pause): the
    // ecosystem still reports its residents, but every sprite asset row has expired, so the radar
    // would draw an empty plate. Re-resolve the region once on entry when that mismatch is seen.
    LaunchedEffect(radarResumed, worldSnapshot.status, worldSnapshot.individuals.size, spawns.size) {
        if (!radarResumed || worldSnapshot.status != EcosystemStatus.READY) return@LaunchedEffect
        if (worldSnapshot.individuals.isEmpty() || spawns.isNotEmpty() || spawnAssetRefreshAttempted) {
            return@LaunchedEffect
        }
        spawnAssetRefreshAttempted = true
        requestPopulationRefresh(force = true)
    }

    DisposableEffect(hasLocationPermission, explorationActive, worldSnapshot.leaseEpoch) {
        if (!hasLocationPermission || !explorationActive) {
            onDispose { }
        } else {
            val client = LocationServices.getFusedLocationProviderClient(context)
            val epoch = worldSnapshot.leaseEpoch
            var attached = true
            lastPopulationPosition = null

            fun acceptLocation(target: Location) {
                if (!attached) return
                val point = GeoPoint.fromOrNull(target.latitude, target.longitude) ?: return
                val input = WorldPlayerFix(point, target.time, target.accuracy.takeIf { target.hasAccuracy() && it.isFinite() && it >= 0f })
                scope.launch {
                    val accepted = withContext(Dispatchers.IO) { ecosystem.acceptPlayerFix(radarLease, epoch, input) }
                    if (!attached || !accepted) return@launch
                    if (origin == null) origin = point
                    location = Location(target)
                    requestPopulationRefresh()
                }
            }

            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let(::acceptLocation)
                }
            }

            val request = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                2_000L
            )
                .setMinUpdateIntervalMillis(1_000L)
                .setMinUpdateDistanceMeters(0f) // Keep a stationary player's fix fresh too.
                .setWaitForAccurateLocation(false)
                .build()

            client.lastLocation.addOnSuccessListener { cached ->
                cached?.let(::acceptLocation)
            }

            client.requestLocationUpdates(request, callback, context.mainLooper)
                .addOnFailureListener {
                    if (attached) populationRefreshFailed = true
                }

            onDispose {
                attached = false
                populationRefreshJob?.cancel()
                client.removeLocationUpdates(callback)
            }
        }
    }

    fun savePendingBattle() {
        if (battleExitInProgress) return
        val id = pendingBattleEventId ?: return
        val outcome = pendingBattleOutcome
        battleExitInProgress = true
        scope.launch {
            try {
                val result = withContext(NonCancellable + Dispatchers.IO) {
                    ecosystem.finishBattle {
                        val recordedOutcome = outcome ?: app.container.db.questDao().battleReport(id)?.outcome
                        if (recordedOutcome != null) app.container.worldRepository.recordRadarBattleResult(id, recordedOutcome, snapshot = pendingBattleSnapshot)
                        else app.container.worldRepository.interactions.cancel(id)
                    }
                }
                result.getOrThrow()
                val chat=withContext(Dispatchers.IO) {
                    val memory=app.container.db.worldChatMemoryDao().getContext(id)
                    memory?.chatIndividualId?.let { individual ->
                        app.container.db.worldInteractionDao().getParticipants(id).firstOrNull { it.individualId==individual }
                            ?.cardCharacterId?.let { card -> individual to card }
                    }
                }
                pendingBattleEventId = null
                pendingBattleOutcome = null
                pendingBattleSnapshot = null
                if(chat!=null && app.container.db.worldInteractionDao().getResult(id)!=null) {
                    navController.navigate(AppRoute.WildContact(chat.first, chat.second)) { launchSingleTop=true }
                }
            } catch (failure: Exception) {
                // Keep the event/outcome and freeze available to the explicit recovery action.
                showRadarFailure(failure, R.string.ui_world_battle_record_failed)
            } finally {
                battleExitInProgress = false
            }
        }
    }

    fun leaveRadarBattle() {
        if (pendingBattleEventId != null || battleExitInProgress) return
        pendingBattleEventId = battleSessionId
        pendingBattleOutcome = battleTerminalOutcome ?: battleSessionState.snapshot?.result?.outcome
        pendingBattleSnapshot = battleSessionState.snapshot?.takeIf { it.result != null }
        battleViewModel.finishSession()
        battleSpawnId = null
        battleCharacterId = null
        battleSessionId = null
        committedBattleSessionId = null
        battleTerminalOutcome = null
        tearDownArStage()
        savePendingBattle()
    }

    suspend fun discardPreparation(id: String) = withContext(NonCancellable) {
        // Failed gated reservations rolled back already. Only a committed freeze needs cleanup.
        if (ecosystem.snapshot.value.session?.pauseReason != WorldPauseReason.PLAYER_BATTLE) return@withContext
        val result = withContext(Dispatchers.IO) {
            ecosystem.finishBattle { app.container.worldRepository.interactions.cancel(id) }
        }
        if (result is RadarCommandResult.Failed) {
            pendingBattleEventId = id
            pendingBattleOutcome = null
        }
    }

    fun startJoinedRadarBattle(sourceId:String,sourceRevision:Long,alliedWild:String?,secondOwned:Long?) {
        if(preparingSpawnId!=null || battleActive) return
        preparingSpawnId=-1L
        val sessionId="world-radar:${java.util.UUID.randomUUID()}"
        scope.launch {
            try {
                val command=RadarCommand(latestCommandStamp(),
                    RadarCommandKind.EVENT_RESERVATION,interactionId=sourceId)
                val prepared=withContext(Dispatchers.IO) {
                    ecosystem.executeCommand(radarLease,command) { context ->
                        val characterDao=app.container.db.userCharacterDao()
                        val active=characterDao.getActiveCharacter().first() ?: error(resources.getString(R.string.ui_world_battle_choose_active))
                        val source=app.container.db.worldInteractionDao().getInteraction(sourceId) ?: throw WorldInteractionException(InteractionFailure.UNAVAILABLE)
                        val record=app.container.db.worldInteractionDao().getNpcBattle(sourceId) ?: throw WorldInteractionException(InteractionFailure.UNAVAILABLE)
                        val remaining=com.tamerslab.world.ecosystem.NpcBattleAdapter.recover(source,record).snapshot()
                        val event=app.container.worldRepository.interactions.reserveJoinedBattle(sessionId,sourceId,sourceRevision,
                            active.id,secondOwned,alliedWild,context.playerFix,context.session.tickIndex)
                        val profiles=characterDao.getAllBattleParticipantProfiles()
                        val ownedIds=if(alliedWild!=null)listOf(active.id) else listOf(active.id,secondOwned!!)
                        val allies=ownedIds.map { id -> offlineBattleParticipant(characterDao.getCharacterWithSprites(id),profiles.first { it.sourceCharacterId==id }) }.toMutableList()
                        val opponents=mutableListOf<com.tamerslab.battle.ui.OfflineBattleParticipant>()
                        val health=(remaining.alliedMembers+remaining.opposingMembers).associateBy { it.combatantId.substringAfter(':') }
                        app.container.db.worldInteractionDao().getParticipants(sessionId).filter { it.role==com.tamerslab.world.ecosystem.InteractionRole.WILD }.forEach { p ->
                            val spawn=app.container.worldRepository.getSpawn(p.spawnId!!) ?: throw WorldInteractionException(InteractionFailure.UNAVAILABLE)
                            val state=health[p.individualId] ?: throw WorldInteractionException(InteractionFailure.UNAVAILABLE)
                            val wild=worldRadarBattleParticipant(spawn).copy(initialHealth=state.health,initialEnergy=state.energy)
                            if(p.side==com.tamerslab.world.ecosystem.InteractionSide.ALLIED)allies+=wild else opponents+=wild
                        }
                        PreparedJoinedRadarBattle(active.id,event,allies,opponents)
                    }.getOrThrow()
                }
                val commit=RadarCommand(ecosystem.snapshot.value.commandStamp!!,RadarCommandKind.BATTLE_COMMIT)
                check(withContext(Dispatchers.IO) { ecosystem.executeCommand(radarLease,commit) { ctx ->
                    app.container.worldRepository.interactions.commitPlayerBattle(sessionId,prepared.interaction.revision,ctx.playerFix,prepared.activeCharacterId)
                }.getOrThrow() })
                awaitBattleRenderer()
                observedInteractionId=null;selectedEncounterId=null
                battleSessionId=sessionId;battleSpawnId=-1L;battleCharacterId=prepared.activeCharacterId
                committedBattleSessionId=null;battleTerminalOutcome=null
                battleViewModel.start(context,sessionId,prepared.allies,prepared.opponents,prepared.interaction.seed,OfflineArenaManifest.RADAR_MANIFEST_PATH)
            } catch(cancelled:CancellationException) {
                discardPreparation(sessionId);throw cancelled
            } catch(failure:Exception) {
                discardPreparation(sessionId);showRadarFailure(failure,R.string.ui_world_battle_start_failed)
            } finally { preparingSpawnId=null;requestedBattleRenderer=false }
        }
    }

    fun startRadarBattle(spawn: WorldDtos.SpawnWithDetails,challengeId:String?=null,wildAttack:EcosystemInteractionSummary?=null) {
        if (preparingSpawnId != null || battleActive) return
        // While the AR stage is scanning, the encounter flow owns battle entry: a stray
        // wild-attack/challenge trigger must not start a battle rendered without the AR scene.
        if (arStageVisible && arPlacement == null) return
        preparingSpawnId = spawn.id
        val sessionId = "world-radar:${java.util.UUID.randomUUID()}"
        scope.launch {
            try {
                // Built at submission time (below), after the preparation reads: a GPS fix during
                // those reads must not turn the battle reservation into a stale-snapshot failure.
                fun battleTicket(): RadarCommand = if (wildAttack == null) {
                    RadarCommand(latestCommandStamp(), RadarCommandKind.BATTLE_RESERVATION, spawn.individualId)
                } else {
                    RadarCommand(latestCommandStamp(), RadarCommandKind.EVENT_RESERVATION, interactionId = wildAttack.id)
                }
                // Quest battles field the bound quest partner automatically instead of
                // requiring the player to switch the active partner manually.
                val switchedPartnerName = withContext(Dispatchers.IO) {
                    app.container.questRepository.useBoundPartnerForBattle(spawn.individualId)
                }
                if (switchedPartnerName != null) {
                    context.showAppFeedback(R.string.quest_partner_auto_selected, switchedPartnerName)
                }
                // Resolve preparation reads before entering the bounded command transaction.
                val owned = withContext(Dispatchers.IO) {
                    val characterDao = app.container.db.userCharacterDao()
                    val activeCharacter = characterDao.getActiveCharacter().first()
                        ?: error(resources.getString(R.string.ui_world_battle_choose_active))
                    val profile = characterDao.getAllBattleParticipantProfiles()
                        .firstOrNull { it.sourceCharacterId == activeCharacter.id }
                        ?: error(resources.getString(R.string.ui_world_battle_stats_unavailable))
                    activeCharacter to profile
                }
                val prepared = withContext(Dispatchers.IO) {
                    ecosystem.executeCommand(radarLease, battleTicket()) { command ->
                        val activeCharacter = owned.first
                        val currentOwned = app.container.db.userCharacterDao().getCharacterSync(activeCharacter.id)
                            ?: throw WorldInteractionException(InteractionFailure.UNAVAILABLE)
                        if (currentOwned.individualId != owned.second.individualId || currentOwned.charId != activeCharacter.charId) {
                            throw WorldInteractionException(InteractionFailure.UNAVAILABLE)
                        }
                        val checkpoint = command.session
                        val challenge=challengeId?.let { app.container.db.worldInteractionDao().getIntent(it) }
                        if(challengeId!=null) {
                            if(challenge==null || challenge.status!=com.tamerslab.world.ecosystem.DialogueIntentStatus.PENDING ||
                                challenge.initiatorId!=spawn.individualId || checkpoint.tickIndex>=challenge.expiresAtTick) throw WorldInteractionException(InteractionFailure.UNAVAILABLE)
                            val evidence=com.google.gson.Gson().fromJson(challenge.evidenceIdsJson,Array<String>::class.java).map { it.removePrefix("private:").toLongOrNull() }
                            val history=app.container.db.chatDao().getMessagesSync(spawn.individualId).map { it.id }.toSet()
                            if(evidence.any { it==null || it !in history })throw WorldInteractionException(InteractionFailure.UNAVAILABLE)
                        }
                        val interaction = if(wildAttack!=null) app.container.worldRepository.interactions.reserveWildAttack(
                            sessionId,wildAttack.id,wildAttack.revision,activeCharacter.id,command.playerFix,checkpoint.tickIndex)
                        else app.container.worldRepository.interactions.reservePlayerBattle(
                            sessionId, activeCharacter.id, spawn.id, command.playerFix,
                            EcosystemSeed.mix(checkpoint.seed, sessionId, "player-battle", checkpoint.tickIndex), checkpoint.tickIndex
                        )
                        if(challenge!=null) {
                            app.container.db.worldInteractionDao().updateInteraction(interaction.copy(parentInteractionId=challenge.interactionId,
                                publicReason=if(challenge.sparring)"SPARRING:Wild challenge" else "DISPUTE:Wild challenge"))
                            com.tamerslab.world.ecosystem.WorldChatMemoryRepository(app.container.db).capturePrivateBattle(interaction.id,challenge)
                        }
                        val currentSpawn = app.container.worldRepository.getSpawn(spawn.id)
                            ?: throw WorldInteractionException(InteractionFailure.UNAVAILABLE)
                        app.container.worldRepository.markInteracted(spawn.id)
                        PreparedRadarBattle(activeCharacter.id, offlineBattleParticipant(activeCharacter, owned.second),
                            worldRadarBattleParticipant(currentSpawn), interaction)
                    }.getOrThrow()
                }
                val commit = RadarCommand(ecosystem.snapshot.value.commandStamp!!, RadarCommandKind.BATTLE_COMMIT)
                check(withContext(Dispatchers.IO) {
                    ecosystem.executeCommand(radarLease, commit) { command ->
                        val committed=app.container.worldRepository.interactions.commitPlayerBattle(sessionId, prepared.interaction.revision, command.playerFix,prepared.activeCharacterId)
                        if(committed && challengeId!=null) app.container.db.worldInteractionDao().getIntent(challengeId)?.let { intent ->
                            app.container.db.worldInteractionDao().updateIntent(intent.copy(status=com.tamerslab.world.ecosystem.DialogueIntentStatus.ACCEPTED,linkedBattleId=sessionId))
                        }
                        committed
                    }.getOrThrow()
                }) { resources.getString(R.string.ui_world_encounter_unavailable) }
                awaitBattleRenderer()
                observedInteractionId = null
                selectedEncounterId = null
                battleSpawnId = spawn.id
                battleCharacterId = prepared.activeCharacterId
                battleSessionId = sessionId
                committedBattleSessionId = null
                battleTerminalOutcome = null
                battleViewModel.start(
                    context = context,
                    sessionId = sessionId,
                    allies = listOf(prepared.ally),
                    opponents = listOf(prepared.opponent),
                    randomSeed = prepared.interaction.seed,
                    arenaManifestPath = OfflineArenaManifest.RADAR_MANIFEST_PATH
                )
            } catch (cancelled: CancellationException) {
                // Cancelled preparation owns no battle; unwind even if cancellation happened
                // just after the IO checkpoint committed but before returning to the UI.
                if (battleSessionId == null) {
                    discardPreparation(sessionId)
                }
                throw cancelled
            } catch (failure: Exception) {
                if (battleSessionId != null) leaveRadarBattle()
                discardPreparation(sessionId)
                // A failed start cannot be retried from the AR stage's placement overlay; drop the
                // stage so the player is back on the radar with the error toast.
                if (arStageVisible && battleSessionId == null) tearDownArStage()
                showRadarFailure(failure, R.string.ui_world_battle_start_failed)
            } finally {
                preparingSpawnId = null
                requestedBattleRenderer = false
            }
        }
    }

    /**
     * Encounter battle entry point. Keeps the historical normal battle when AR is off or the
     * device cannot run ARCore at all; otherwise arms the AR stage and lets the user place the
     * arena on a real surface. Availability is re-checked by the stage while scanning, so a
     * transient failure there still falls back to the normal Coliseum battle.
     */
    fun beginRadarBattle(spawn: WorldDtos.SpawnWithDetails) {
        if (preparingSpawnId != null || battleActive || arStageVisible) return
        if (!arBattleEnabled || checkArAvailability(context) == ArAvailability.UNSUPPORTED) {
            startRadarBattle(spawn)
            return
        }
        selectedEncounterId = null
        arSpawn = spawn
        arPlacement = null
        arAvailability = null
        arStageVisible = true
    }

    /** Placement confirmed: start the battle while keeping the same AR stage mounted for BATTLE. */
    fun confirmArPlacement(placement: ArPlacement) {
        arPlacement = placement
        val spawn = arSpawn
        if (spawn != null) startRadarBattle(spawn) else tearDownArStage()
    }

    /** Back/"Fight normally": tear the stage down, falling back to the normal battle when AR failed. */
    fun cancelArStage() {
        val spawn = arSpawn
        val fallBackToNormalBattle = arAvailability == ArAvailability.UNSUPPORTED ||
            arAvailability == ArAvailability.SUPPORTED_NOT_INSTALLED
        tearDownArStage()
        if (fallBackToNormalBattle && spawn != null) startRadarBattle(spawn)
    }

    LaunchedEffect(battleSessionId, battleSessionState.sessionId) {
        // A process recreation cannot restore the running simulator. Return to
        // the live Radar instead of leaving a dead 3D viewport on screen.
        if (battleSessionId != null && battleSessionState.sessionId == null) {
            leaveRadarBattle()
        }
    }


    val surfaceState = radarSurfaceState(
        worldSnapshot, hasLocationPermission, worldSpawnCardsState?.size,
        worldSpawnCardsState?.count { it.worldSpawnsEnabled },
        populating = populationRefreshInProgress, populationFailed = populationRefreshFailed,
        pendingBattle = pendingBattleEventId != null, savingBattle = battleExitInProgress
    )
    val regionMutationsEnabled = radarResumed && worldSnapshot.status == EcosystemStatus.READY &&
        worldSnapshot.playerFix?.isFresh(worldSnapshot.observedAt) == true && pendingBattleEventId == null
    val settingsMutationsEnabled = radarResumed && worldSnapshot.status == EcosystemStatus.READY && pendingBattleEventId == null

    fun spawnDebugDigimon(cardCharacterId: Long? = null) {
        if (!debugSettingsRepository.enabled || !regionMutationsEnabled || debugSpawnInProgress) return
        debugSpawnInProgress = true
        scope.launch {
            try {
                val spawnId = withContext(Dispatchers.IO) {
                    ecosystem.executeCommand(radarLease, displayedCommand(RadarCommandKind.REGION)) { command ->
                        val position = command.playerFix.position
                        app.container.worldRepository.spawnDebugDigimon(position.latitude, position.longitude, cardCharacterId)
                    }.getOrThrow()
                }
                context.showAppFeedback(
                    if (spawnId != null) R.string.ui_world_debug_spawn_success else R.string.ui_world_debug_spawn_unavailable
                )
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                showRadarFailure(failure, R.string.ui_world_debug_spawn_unavailable)
            } finally {
                debugSpawnInProgress = false
            }
        }
    }
    LaunchedEffect(worldSnapshot.session?.tickIndex,worldSnapshot.status,activeOwned?.id,radarResumed,battleActive,spawns,arStageVisible) {
        if(!regionMutationsEnabled || battleActive || arStageVisible || preparingSpawnId!=null || activeOwned==null || incomingChallengeId!=null || privateChallenge!=null) return@LaunchedEffect
        val attack=worldSnapshot.interactions.firstOrNull { it.type==InteractionType.BATTLE && it.state==InteractionState.ACTIVE &&
            it.isPlayerBattle && (worldSnapshot.session?.tickIndex ?: 0)>=it.nextActionTick &&
            (attemptedWildAttacks[it.id] ?: 0)<3 } ?: return@LaunchedEffect
        val attacker=spawns.firstOrNull { it.individualId in attack.participantIds } ?: return@LaunchedEffect
        val position=worldSnapshot.individuals.firstOrNull { it.individualId==attacker.individualId }?.position ?: return@LaunchedEffect
        if(!debugSettingsRepository.allowAnyDistance &&
            worldSnapshot.playerFix?.let { RadarWorldGeometry.relative(it.position,position).withinInteractionRange }!=true) return@LaunchedEffect
        attemptedWildAttacks[attack.id]=(attemptedWildAttacks[attack.id] ?: 0)+1
        if(attack.publicReason?.startsWith("WILD_ATTACK:")==true) context.showAppFeedback(R.string.ui_world_wild_attack)
        startRadarBattle(attacker,wildAttack=attack)
    }
    LaunchedEffect(privateChallenge,regionMutationsEnabled,activeOwned?.id,worldSnapshot.claimedIndividuals,arStageVisible) {
        val intent=privateChallenge ?: return@LaunchedEffect
        if((intent.sparring && intent.type!=com.tamerslab.world.ecosystem.DialogueIntentType.ACCEPT_CHALLENGE) || incomingChallengeId==null || !regionMutationsEnabled || activeOwned==null || preparingSpawnId!=null || battleActive || arStageVisible || intent.initiatorId in worldSnapshot.claimedIndividuals) return@LaunchedEffect
        val attacker=spawns.firstOrNull { it.individualId==intent.initiatorId } ?: return@LaunchedEffect
        incomingChallengeId=null
        privateChallenge=null
        startRadarBattle(attacker,intent.id)
    }
    val battleRendererToken = rendererState.generation

    fun recoverRadar() {
        when (surfaceState) {
            RadarSurfaceState.BATTLE_SAVE_FAILURE -> savePendingBattle()
            RadarSurfaceState.LOCATION_PERMISSION -> launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            RadarSurfaceState.POPULATION_FAILURE, RadarSurfaceState.EMPTY_REGION -> requestPopulationRefresh(force = true)
            RadarSurfaceState.STORAGE_FAILURE -> scope.launch {
                if (withContext(Dispatchers.IO) { ecosystem.retry(radarLease) }) requestPopulationRefresh(force = true)
            }
            else -> Unit
        }
    }

    fun openConversation(id: String) {
        selectedEncounterId=null
        observedInteractionId=null
        navController.navigate(AppRoute.WorldConversation(id)) { launchSingleTop=true }
    }
    fun openInteraction(event: EcosystemInteractionSummary) {
        if(event.type==InteractionType.CHAT) openConversation(event.id) else observedInteractionId=event.id
    }
    LaunchedEffect(observedInteractionId,worldSnapshot.interactions,radarResumed) {
        if(radarResumed) worldSnapshot.interactions.firstOrNull { it.id==observedInteractionId && it.type==InteractionType.CHAT }
            ?.let { openConversation(it.id) }
    }

    // System back while scanning must leave the AR stage instead of popping the radar destination
    // (which would otherwise leave the full-screen chrome flagged as active).
    BackHandler(enabled = arStageVisible && !battleActive) { cancelArStage() }

    Box(Modifier.fillMaxSize()) {
            val onBattleRendererCreated: () -> Unit = { battleRendererPresent.set(true) }
            val onBattleRendererReleased: () -> Unit = {
                if (rendererOwnership.released(RadarRendererOwner.BATTLE, battleRendererToken)) battleRendererPresent.set(false)
            }
            WorldRadarBattleContent(
                viewModel = battleViewModel,
                battleActive = battleActive,
                rendererAllowed = rendererState.owner == RadarRendererOwner.BATTLE && rendererState.requested == RadarRendererOwner.BATTLE && !rendererState.releasing,
                onRendererCreated = onBattleRendererCreated,
                onRendererReleased = onBattleRendererReleased,
                onOutcome = outcome@ { outcome ->
                    if (battleExitInProgress || battleSessionState.sessionId != battleSessionId ||
                        battleSessionState.snapshot?.result?.outcome != outcome) return@outcome
                    battleTerminalOutcome = outcome
                    val activeSessionId = battleSessionId
                    val spawnId = battleSpawnId
                    val characterId = battleCharacterId
                    if (activeSessionId != null && committedBattleSessionId != activeSessionId &&
                        spawnId != null && characterId != null
                    ) {
                        committedBattleSessionId = activeSessionId
                        val finalItems = battleSessionState.snapshot?.trainingItems
                        val terminalSnapshot = battleSessionState.snapshot
                        scope.launch {
                            runCatching {
                                withContext(NonCancellable + Dispatchers.IO) {
                                    app.container.worldRepository.recordRadarBattleResult(
                                        interactionId = activeSessionId,
                                        outcome = outcome,
                                        items = finalItems,
                                        snapshot = terminalSnapshot
                                    )
                                }
                            }.onFailure { failure ->
                                committedBattleSessionId = null
                                context.showAppFeedback(R.string.ui_world_battle_record_failed, important = true)
                            }
                        }
                    }
                },
                onExit = ::leaveRadarBattle,
                rewardContent = { battleSessionId?.let { DigimonScanBattleRewards(app.container.db, it) } },
                arStage = if (arStageVisible) {
                    { hooks ->
                        ArBattleStage(
                            phase = if (battleActive) ArStagePhase.BATTLE else ArStagePhase.SCANNING,
                            // Single Filament engine invariant: the AR battle scene may only claim
                            // an Engine while the battle renderer owns the renderer slot (a
                            // lingering FIRST_PERSON release must finish first).
                            battle = if (battleActive && arPlacement != null &&
                                rendererState.owner == RadarRendererOwner.BATTLE &&
                                rendererState.requested == RadarRendererOwner.BATTLE &&
                                !rendererState.releasing
                            ) ArBattleContent(
                                snapshot = battleSessionState.snapshot,
                                fighters = battleSessionState.fighters,
                                manifest = battleSessionState.arenaManifest,
                                sessionId = battleSessionState.sessionId,
                                preparedForms = battleSessionState.preparedForms,
                                allowMotion = allowMotion,
                                sceneSnapshotFlow = if (BattleSceneChannel.enabled) battleViewModel.sceneSnapshot else null,
                            ) else null,
                            onPlacementConfirmed = { placement -> confirmArPlacement(placement) },
                            onCancel = { cancelArStage() },
                            onSceneViewReady = hooks.onSceneViewReady,
                            onSceneReady = hooks.onSceneReady,
                            onProjectionChanged = hooks.onProjectionChanged,
                            onFighterTapped = hooks.onFighterTapped,
                            onRendererReleased = onBattleRendererReleased,
                            onRendererCreated = onBattleRendererCreated,
                            onAssetError = hooks.onAssetError,
                            onAvailabilityChanged = { arAvailability = it },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                } else null,
                radarViewport = {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .then(
                                if (effectiveLegacy == RadarLegacyView.NONE)
                                    Modifier.pointerInput(Unit) {
                                        detectTransformGestures { _, _, zoomChange, _ ->
                                            zoom = (zoom * zoomChange).coerceIn(RADAR_ISO_MIN_ZOOM, RADAR_ISO_MAX_ZOOM)
                                        }
                                    }
                                else Modifier
                            )
                    ) {
            if (effectiveLegacy == RadarLegacyView.FIRST_PERSON && rendererState.owner == RadarRendererOwner.FIRST_PERSON &&
                rendererState.requested == RadarRendererOwner.FIRST_PERSON && !rendererState.releasing) {
                val rendererToken = rendererState.generation
                val density=LocalDensity.current
                Box(Modifier.fillMaxSize().onSizeChanged { firstPersonViewportWidth=it.width }.pointerInput(Unit) {
                    detectVerticalDragGestures { change, drag ->
                        change.consume()
                        cameraPitch=radarPitchAfterDrag(currentPitch,drag,size.height.toFloat())
                    }
                }) {
                    RadarFirstPersonViewport(worldSnapshot,presentations,radarHeading,cameraPitch,radarResumed,allowMotion,
                        onProjection = { fpProjection = it },
                        onFailure = { legacyName = RadarLegacyView.NONE.name },
                        onCreated = { firstPersonRendererPresent.set(true) },
                        onReleased = { if(rendererOwnership.released(RadarRendererOwner.FIRST_PERSON,rendererToken)) firstPersonRendererPresent.set(false) },
                        modifier = Modifier.fillMaxSize())
                    fpProjection.sortedByDescending { it.distance }.forEach { projection ->
                        val asset = spawns.firstOrNull { it.individualId == projection.individualId }
                        val hitWidth=maxOf(projection.width,with(density) { 48.dp.toPx() })
                        val hitHeight=maxOf(projection.height,with(density) { 48.dp.toPx() })
                         Box(
                            modifier=Modifier.offset { IntOffset((projection.x-hitWidth/2).toInt(),(projection.y+(projection.height-hitHeight)/2).toInt()) }
                                 .size(with(density) { hitWidth.toDp() },with(density) { hitHeight.toDp() })
                                 .combinedClickable(onClick={
                                    val event=worldSnapshot.publicInteractionFor(projection.individualId)
                                    if(event!=null)openInteraction(event) else { selectedEncounterId=projection.individualId;selectedEncounterName=asset?.speciesName }
                                },onLongClick={ asset?.let { spawn -> scope.launch {
                                     selectedSpecies=withContext(Dispatchers.IO) { val dex = DexRepository(app.container.db)
                                         spawn.cardId?.let { dex.getCharactersByCardId(it).first().firstOrNull { c -> c.id==spawn.cardCharacterId } }
                                             ?: dex.getCharacterProgress(spawn.cardCharacterId).first() }
                                 } } }).semantics { contentDescription=asset?.speciesName ?: "Digimon" }
                          )
                        val activity=worldSnapshot.publicInteractionFor(projection.individualId)
                        RadarOverheadAnnotation(projection.x,projection.y,firstPersonViewportWidth,
                            asset?.speciesName ?: "Digimon",projection.distance.toInt(),showEntityLabels,activity,
                            worldSnapshot.speechFor(projection.individualId),projection.individualId,motionFrame.longValue,allowMotion,
                            onClick={if(activity!=null)openInteraction(activity) else {selectedEncounterId=projection.individualId;selectedEncounterName=asset?.speciesName}})
                    }
                    if(compass.heading==null) Text(stringResource(R.string.ui_world_north_preview),color=TextPrimaryOnDark,
                        modifier=Modifier.align(Alignment.TopCenter).padding(8.dp))
                }
            } else if (effectiveLegacy == RadarLegacyView.FIRST_PERSON || rendererState.releasing) {
                Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) { Text(stringResource(R.string.ui_world_renderer_handoff)) }
            } else if (effectiveLegacy == RadarLegacyView.CLASSIC_2D) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(battleActive) {
                        detectTransformGestures { _, _, gestureZoom, _ ->
                            if (!battleActive) zoom = (zoom * gestureZoom).coerceIn(RADAR_ISO_MIN_ZOOM, RADAR_ISO_MAX_ZOOM)
                        }
                    }
            ) {
                val density = LocalDensity.current
                val boxWidthPx = with(density) { maxWidth.toPx() }
                val center = Offset(boxWidthPx / 2f, boxWidthPx / 2f)
                val visibleRadiusMeters = 500.0 / zoom
                val scale = (boxWidthPx / 2f) / visibleRadiusMeters.toFloat()
                val interactionRadiusPx = (INTERACTION_RANGE_METERS * scale).toFloat()
                val distanceRingMeters = (RADAR_RING_INTERVAL_METERS..visibleRadiusMeters.toInt())
                    .step(RADAR_RING_INTERVAL_METERS)
                    .toList()

                // O jogador permanece sempre no centro da tela. O mundo Ã© que se desloca
                // em sentido contrÃ¡rio ao movimento do jogador.
                val playerOffset = center
                val playerDisplacement = origin?.let { originPosition ->
                    playerPosition?.let { currentPosition ->
                        val displacement = RadarWorldGeometry.relative(
                            originPosition, currentPosition
                        ).mapOffset(0.0)
                        Offset(
                            (displacement.xMeters * scale).toFloat(),
                            (displacement.yMeters * scale).toFloat()
                        )
                    }
                } ?: Offset.Zero

                val primaryColor = MaterialTheme.colorScheme.primary
                val gridColor = SurfaceStroke
                val ringColor = TextPrimaryOnDark
                val pulseColor = VitalCyan
                val compassColor = RadarCompass

                Canvas(modifier = Modifier.fillMaxSize()) {
                    // A grade representa o mundo, se desloca com o jogador e gira com a bÃºssola.
                    withTransform({
                        rotate(degrees = -radarHeading, pivot = center)
                    }) {
                        val gridSpacingPx = (GRID_SIZE_METERS * scale).toFloat().coerceAtLeast(20f)
                        val originX = center.x - playerDisplacement.x
                        val originY = center.y - playerDisplacement.y

                        // Desenha a grade em uma Ã¡rea maior que a tela para que a rotaÃ§Ã£o
                        // nunca revele as bordas vazias do Canvas.
                        val diagonal = sqrt(
                            size.width * size.width +
                                size.height * size.height
                        )

                        var x = originX % gridSpacingPx
                        while (x > -diagonal) x -= gridSpacingPx
                        while (x < size.width + diagonal) {
                            drawLine(
                                gridColor.copy(alpha = 0.72f),
                                Offset(x, -diagonal),
                                Offset(x, size.height + diagonal),
                                strokeWidth = 2f
                            )
                            x += gridSpacingPx
                        }

                        var y = originY % gridSpacingPx
                        while (y > -diagonal) y -= gridSpacingPx
                        while (y < size.height + diagonal) {
                            drawLine(
                                gridColor.copy(alpha = 0.72f),
                                Offset(-diagonal, y),
                                Offset(size.width + diagonal, y),
                                strokeWidth = 2f
                            )
                            y += gridSpacingPx
                        }
                    }

                    distanceRingMeters.forEach { meters ->
                        drawCircle(
                            color = ringColor.copy(alpha = 0.18f),
                            radius = meters * scale,
                            center = playerOffset,
                            style = Stroke(width = 1.5f)
                        )
                    }

                    val pulseRadiusPx = radarPulse.value * visibleRadiusMeters.toFloat() * scale
                    drawCircle(
                        color = pulseColor.copy(alpha = 0.65f * (1f - radarPulse.value)),
                        radius = pulseRadiusPx,
                        center = playerOffset,
                        style = Stroke(width = 2.5f)
                    )

                    drawCircle(
                        color = primaryColor.copy(alpha = 0.35f),
                        radius = interactionRadiusPx,
                        center = playerOffset,
                        style = Stroke(width = 3f)
                    )

                }

                distanceRingMeters.forEach { meters ->
                    val radiusPx = meters * scale
                    Text(
                        text = "$meters m",
                        color = TextPrimaryOnDark.copy(alpha = 0.72f),
                        fontSize = 10.sp,
                        modifier = Modifier.offset {
                            IntOffset(
                                (center.x + radiusPx + 4.dp.toPx()).toInt(),
                                (center.y - 9.dp.toPx()).toInt()
                            )
                        }
                    )
                }

                // Letras cardeais acompanham as setas e giram pela borda do mapa.
                listOf(
                    0f to cardinalNames[0],
                    90f to cardinalNames[2],
                    180f to cardinalNames[4],
                    270f to cardinalNames[6]
                ).forEach { (cardinalAzimuth, label) ->
                    val compassRadiusPx = boxWidthPx / 2f - with(density) { 48.dp.toPx() }
                    val angleRadians = (cardinalAzimuth - radarHeading - 90f) * (PI / 180.0)
                    val labelX = center.x + cos(angleRadians).toFloat() * compassRadiusPx
                    val labelY = center.y + sin(angleRadians).toFloat() * compassRadiusPx
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = TextPrimaryOnDark,
                        modifier = Modifier.offset {
                            IntOffset(
                                (labelX - 8.dp.toPx()).toInt(),
                                (labelY - 10.dp.toPx()).toInt()
                            )
                        }.zIndex(3f)
                    )
                }

                // O marcador azul permanece centralizado; a grade e os objetos do mundo se movem ao redor dele.
                androidx.compose.animation.AnimatedVisibility(
                    visible = currentBiome != WorldBiome.NULL,
                    enter = fadeIn(animationSpec = tween(durationMillis = 250)),
                    exit = fadeOut(animationSpec = tween(durationMillis = 250)),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                        .zIndex(4f)
                ) {
                    Text(
                        text = stringResource(
                            R.string.ui_world_biome_label,
                            currentBiome.displayName()
                        ),
                        color = TextPrimaryOnDark,
                        fontSize = 10.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (playerOffset.x - 14.dp.toPx()).toInt(),
                                (playerOffset.y - 14.dp.toPx()).toInt()
                            )
                        }
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .border(2.dp, TextPrimaryOnDark, CircleShape)
                )

                // Geographic positions come from one coherent snapshot; DTOs supply assets only.
                playerPosition?.let { player ->
                    spawnBitmaps.forEach { (presentation, image) ->
                        val spawn = presentation.assets
                        val activity=worldSnapshot.publicInteractionFor(spawn.individualId)
                        val activitySlot=activity?.participantIds?.sorted()?.indexOf(spawn.individualId) ?: 0
                        val activityOffset=radarInteractionOffset(activitySlot,activity?.participantIds?.size ?: 1,44f)
                        val relative = RadarWorldGeometry.relative(player, presentation.individual.position)
                        if (relative.withinRadius(visibleRadiusMeters * 1.25)) {
                            val markerSizeDp = 48.dp

                            Image(
                                bitmap = image,
                                contentDescription = spawn.speciesName ?: "Digimon",
                                filterQuality = FilterQuality.None,
                                modifier = Modifier
                                    .offset {
                                        val visual = RadarWorldGeometry.relative(player, presentation.individual.displayPosition(motionFrame.longValue, allowMotion))
                                            .mapOffset(radarHeading.toDouble())
                                        val action=if(activity?.type==InteractionType.BATTLE && activity.state==InteractionState.ACTIVE)
                                            radarActivityLunge(motionFrame.longValue,activitySlot,allowMotion)*if(activitySlot==0)8.dp.toPx() else -8.dp.toPx() else 0f
                                        val px = center.x + (visual.xMeters * scale).toFloat()+activityOffset.dp.toPx()+action
                                        val py = center.y + (visual.yMeters * scale).toFloat()
                                        val markerSizePx = markerSizeDp.toPx()
                                        IntOffset(
                                            (px - markerSizePx / 2f).toInt(),
                                            (py - markerSizePx / 2f).toInt()
                                        )
                                    }
                                    .size(markerSizeDp)
                                    .combinedClickable(
                                        onClick = {
                                             if (!battleActive) {
                                                val event=worldSnapshot.publicInteractionFor(spawn.individualId)
                                                if(event!=null)openInteraction(event) else {
                                                    selectedEncounterId = spawn.individualId
                                                    selectedEncounterName = spawn.speciesName
                                                }
                                            }
                                        },
                                        onLongClick = {
                                            if (!battleActive) {
                                                scope.launch {
                                                     selectedSpecies = withContext(Dispatchers.IO) {
                                                         val dex = DexRepository(app.container.db)
                                                         spawn.cardId?.let { cardId ->
                                                             dex.getCharactersByCardId(cardId)
                                                                 .first()
                                                                 .firstOrNull { it.id == spawn.cardCharacterId }
                                                         } ?: dex.getCharacterProgress(spawn.cardCharacterId).first()
                                                     }
                                                }
                                            }
                                        }
                                    )
                            )
                            val visual=RadarWorldGeometry.relative(player,presentation.individual.displayPosition(motionFrame.longValue,allowMotion)).mapOffset(radarHeading.toDouble())
                            RadarOverheadAnnotation((center.x+visual.xMeters*scale+with(density) { activityOffset.dp.toPx() }).toFloat(),
                                (center.y+visual.yMeters*scale-with(density) { 24.dp.toPx() }).toFloat(),boxWidthPx.toInt(),spawn.speciesName ?: "Digimon",
                                relative.distanceMeters.toInt(),showEntityLabels,activity,worldSnapshot.speechFor(spawn.individualId),spawn.individualId,
                                motionFrame.longValue,allowMotion,onClick={if(activity!=null)openInteraction(activity) else {selectedEncounterId=spawn.individualId;selectedEncounterName=spawn.speciesName}})
                        }
                    }
                }

                // Compass pointers are an information layer, so they stay legible above sprites.
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(3f)
                ) {
                    val compassRadius = size.minDimension / 2f - 28.dp.toPx()
                    val arrowLength = 18.dp.toPx()
                    val arrowHalfWidth = 8.dp.toPx()
                    listOf(
                        0f to primaryColor,
                        90f to compassColor,
                        180f to compassColor,
                        270f to compassColor
                    ).forEach { (cardinalAzimuth, arrowColor) ->
                        val screenAngle = (cardinalAzimuth - radarHeading - 90f) * (PI / 180.0)
                        val direction = Offset(
                            cos(screenAngle).toFloat(),
                            sin(screenAngle).toFloat()
                        )
                        val tangent = Offset(-direction.y, direction.x)
                        val tip = center + direction * compassRadius
                        val baseCenter = tip - direction * arrowLength
                        val arrowPath = Path().apply {
                            moveTo(tip.x, tip.y)
                            lineTo(
                                baseCenter.x + tangent.x * arrowHalfWidth,
                                baseCenter.y + tangent.y * arrowHalfWidth
                            )
                            lineTo(
                                baseCenter.x - tangent.x * arrowHalfWidth,
                                baseCenter.y - tangent.y * arrowHalfWidth
                            )
                            close()
                        }
                        drawPath(arrowPath, arrowColor)
                    }
                }
            }
            } else {
                val isoToken = rendererState.generation
                if (rendererState.owner == RadarRendererOwner.ISOMETRIC &&
                    rendererState.requested == RadarRendererOwner.ISOMETRIC && !rendererState.releasing) {
                    RadarIsometricViewport(
                        snapshot = worldSnapshot,
                        presentations = presentations,
                        playerSprite = playerSprite,
                        heading = radarHeading,
                        zoom = zoom,
                        active = radarResumed && !battleActive && !arStageVisible && pendingBattleEventId == null,
                        motion = allowMotion,
                        onProjection = { isoProjection = it },
                        onFailure = { isoFailed = true; legacyName = RadarLegacyView.NONE.name },
                        onCreated = { isometricRendererPresent.set(true) },
                        onReleased = {
                            if (rendererOwnership.released(RadarRendererOwner.ISOMETRIC, isoToken)) {
                                isometricRendererPresent.set(false)
                            }
                        },
                        pitchDegrees = RADAR_ISO_PITCH_DEGREES,
                        rings = emptyList(),
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.ui_world_renderer_handoff),
                            color = TextSecondaryOnDark,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            if (effectiveLegacy == RadarLegacyView.NONE && !isoFailed) {
                // Owner decisions: no distance/range rings; a large minimal tick ring with longer
                // cardinal ticks and a red two-triangle needle at the nearest wild Digimon's bearing.
                // The idle search gesture only runs while the radar is live and motion is allowed.
                RadarCompassLayer(
                    heading = radarHeading,
                    nearestBearingDegrees = nearestWildBearing,
                    nearestCardinalName = nearestWildCardinal,
                    animate = allowMotion && explorationActive,
                    modifier = Modifier.fillMaxSize(),
                )
                if (playerSprite == null) {
                    // No stored partner artwork: keep "you are here" readable instead of an empty plate.
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .size(44.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f), CircleShape)
                            .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    )
                }
                val density = LocalDensity.current
                val markerHalfPx = with(density) { 24.dp.toPx() }
                // Label pills are opt-in and dense clusters would otherwise stack them. A pill only
                // appears when it does not cover one already placed; the marker dot/hit target stays.
                val usedLabelRects = mutableListOf<Rect>()
                val pillHeightPx = with(density) { 24.dp.toPx() }
                val pillGapPx = with(density) { 25.dp.toPx() }
                val pillMaxWidthPx = with(density) { 176.dp.toPx() }
                val pillPaddingPx = with(density) { 16.dp.toPx() }
                val pillCharWidthPx = with(density) { 7.2.dp.toPx() }
                isoProjection.sortedByDescending { it.distance }.forEach { projection ->
                    val entity = spawns.firstOrNull { it.individualId == projection.individualId }
                    val activity = worldSnapshot.publicInteractionFor(projection.individualId)
                    val label = if (showEntityLabels) entity?.speciesName ?: "Digimon" else null
                    val labelWidth = label?.let {
                        (it.length * pillCharWidthPx + pillPaddingPx).coerceAtMost(pillMaxWidthPx)
                    } ?: 0f
                    val labelRect = if (label != null) Rect(
                        left = projection.x - labelWidth / 2f,
                        top = projection.y - pillGapPx - pillHeightPx,
                        right = projection.x + labelWidth / 2f,
                        bottom = projection.y - pillGapPx,
                    ) else null
                    // Farm parity: the sprite itself is the tap target, not a box floating above its
                    // head. The projected billboard rect gets a full-size invisible target (at least
                    // 48dp) with the same tap/long-press actions as the marker.
                    val onTapEncounter: () -> Unit = {
                        if (activity != null) openInteraction(activity) else {
                            selectedEncounterId = projection.individualId
                            selectedEncounterName = entity?.speciesName
                        }
                    }
                    val onLongPressEncounter: () -> Unit = {
                        scope.launch {
                            selectedSpecies = withContext(Dispatchers.IO) {
                                val dex = DexRepository(app.container.db)
                                val spawn = entity ?: return@withContext null
                                spawn.cardId?.let { cardId ->
                                    dex.getCharactersByCardId(cardId).first()
                                        .firstOrNull { character -> character.id == spawn.cardCharacterId }
                                } ?: dex.getCharacterProgress(spawn.cardCharacterId).first()
                            }
                        }
                        Unit
                    }
                    val hitWidthPx = maxOf(projection.width, with(density) { 48.dp.toPx() })
                    val hitHeightPx = maxOf(projection.height, with(density) { 48.dp.toPx() })
                    // The marker itself is the farm-style hit target: it is placed and sized over the
                    // projected sprite, so pressing the Digimon presses that Digimon.
                    val markerModifier = Modifier
                        .offset {
                            IntOffset(
                                (projection.x - hitWidthPx / 2f).toInt(),
                                (projection.y + projection.height / 2f - hitHeightPx / 2f).toInt(),
                            )
                        }
                        .size(
                            with(density) { hitWidthPx.toDp() },
                            with(density) { hitHeightPx.toDp() },
                        )
                    if (labelRect != null && usedLabelRects.any { it.overlaps(labelRect) }) {
                        // Another pill already owns this area: keep the marker, drop only the text.
                        RadarEntityMarker(
                            name = null,
                            distanceMeters = null,
                            hasInteraction = activity != null,
                            onTap = onTapEncounter,
                            onLongPress = onLongPressEncounter,
                            modifier = markerModifier,
                        )
                    } else {
                        labelRect?.let { usedLabelRects += it }
                        RadarEntityMarker(
                            name = label,
                            distanceMeters = if (label != null) projection.distance.toInt() else null,
                            hasInteraction = activity != null,
                            onTap = onTapEncounter,
                            onLongPress = onLongPressEncounter,
                            modifier = markerModifier,
                        )
                    }
                }
                // One bubble only, anchored to the nearest live speaker: no stacked speech.
                val spoken = presentations.mapNotNull { item ->
                    val text = worldSnapshot.speechFor(item.individual.individualId) ?: return@mapNotNull null
                    val projection = isoProjection.firstOrNull { it.individualId == item.individual.individualId }
                        ?: return@mapNotNull null
                    Triple(projection, text, item.individual.individualId)
                }.minByOrNull { it.first.distance }
                spoken?.let { (projection, text, individualId) ->
                    RadarSpeechBubble(
                        text = text,
                        onTap = { worldSnapshot.publicInteractionFor(individualId)?.let { openInteraction(it) } },
                        width = 196.dp,
                        modifier = Modifier.offset {
                            IntOffset(
                                (projection.x - with(density) { 110.dp.toPx() }).toInt(),
                                (projection.y - with(density) { 72.dp.toPx() }).toInt(),
                            )
                        },
                    )
                }
            }

            if (showSectionTabs) {
                Box(Modifier.align(Alignment.TopCenter).padding(top = 96.dp)) {
                    WorldSectionTabs(selectedTab = selectedWorldTab, onTabSelected = onWorldTabSelected)
                }
            }

            RadarTopChrome(
                cardinal = cardinalLabel,
                onBack = { navController.popBackStack() },
                onOptions = { showRadarOptions = true },
                onSpawnSettings = { showWorldSpawnSettings = true },
                debugEnabled = debugEnabled,
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
            )

            val bandStatus = radarChromeStatus(surfaceState)
            RadarSignalBand(
                nearbyCount = worldSnapshot.individuals.size,
                statusText = bandStatus.text?.let { stringResource(it) },
                statusIsError = bandStatus.error,
                recoveryLabel = bandStatus.action?.let { stringResource(it) },
                onRecovery = ::recoverRadar,
                zoom = zoom,
                onZoomIn = { zoom = (zoom * 1.5f).coerceAtMost(RADAR_ISO_MAX_ZOOM) },
                onZoomOut = { zoom = (zoom / 1.5f).coerceAtLeast(RADAR_ISO_MIN_ZOOM) },
                onZoomReset = { zoom = 1f },
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            )
            if (pendingRecruits.isNotEmpty()) {
                VitalButton(
                    onClick = { navController.navigate(AppRoute.WorldRecruits) },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp, bottom = 104.dp),
                ) { Text(stringResource(R.string.ui_world_recruits_button)) }
            }
            observedInteractionId?.let { interactionId ->
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .padding(horizontal = 12.dp, vertical = 104.dp)
                ) {
                    WorldInteractionOverlay(
                        id = interactionId,
                        db = app.container.db,
                        snapshot = worldSnapshot,
                        onClose = { observedInteractionId = null },
                        hasOwnedPartner = activeOwned != null,
                        allowAnyDistance = debugSettings.allowAnyDistance,
                        onOpenConversation = { openConversation(interactionId) },
                        onDefend = {
                            val attack = worldSnapshot.interactions.firstOrNull { it.id == interactionId }
                            val attacker = attack?.let { event -> spawns.firstOrNull { it.individualId in event.participantIds } }
                            if (attack != null && attacker != null) startRadarBattle(attacker, wildAttack = attack)
                        },
                        onJoinBattle = { revision, ally, second -> startJoinedRadarBattle(interactionId, revision, ally, second) },
                    )
                }
            }
                    }

                },
                modifier = Modifier.fillMaxSize()
            )
    }

    RadarOptionsSheet(
        visible = showRadarOptions && !battleActive && !arStageVisible,
        labelsEnabled = showEntityLabels,
        onLabelsChange = { showEntityLabels = it },
        arBattlesEnabled = arBattleEnabled,
        onArBattlesChange = { arBattleEnabled = it },
        debugEnabled = debugEnabled,
        legacyView = effectiveLegacy,
        onLegacyViewChange = { legacy ->
            legacyName = legacy.name
            showRadarOptions = false
        },
        onSpawnSettings = {
            showRadarOptions = false
            showWorldSpawnSettings = true
        },
        onDismiss = { showRadarOptions = false },
        debugExtra = {
            DebugSpawnButton(
                onRandomSpawn = { spawnDebugDigimon() },
                onChooseSpawn = { showDebugSpawnPicker = true },
                loading = debugSpawnInProgress,
                enabled = regionMutationsEnabled && !debugSpawnInProgress,
                modifier = Modifier.fillMaxWidth(),
            )
            com.tamerslab.components.LabeledSwitchRow(
                stringResource(R.string.ui_world_debug_any_distance),
                debugSettings.allowAnyDistance,
                debugSettingsRepository::setAnyDistance,
            )
            Text(
                stringResource(R.string.ui_world_debug_picker_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )

    if (!battleActive && !arStageVisible) {
        selectedEncounterId?.let { individualId ->
            val spawn = spawns.firstOrNull { it.individualId == individualId }
            val blocker = if (displayedCommandStamp == null) RadarRejection.NOT_READY else
                worldSnapshot.commandRejection(RadarCommand(displayedCommandStamp, RadarCommandKind.ENCOUNTER, individualId), worldSnapshot.observedAt,
                    allowAnyDistance = debugSettings.allowAnyDistance)
            WorldEncounterActionSheet(
                spawn = spawn,
                displayName = spawn?.speciesName ?: selectedEncounterName,
                preparingBattle = (spawn != null && preparingSpawnId == spawn.id) || questInteractionInProgress != null,
                blockedReason = blocker?.let { resources.getString(RadarCommandException(rejection = it).messageResource()) },
                onDismiss = { if (preparingSpawnId == null && questInteractionInProgress == null) selectedEncounterId = null },
                onChat = {
                    scope.launch {
                        try {
                            val chatTarget = withContext(Dispatchers.IO) {
                                ecosystem.executeCommand(radarLease, displayedCommand(RadarCommandKind.ENCOUNTER, individualId)) {
                                    val live = app.container.worldRepository.getSpawnEntityByIndividualId(individualId)
                                        ?: throw WorldInteractionException(InteractionFailure.UNAVAILABLE)
                                    app.container.worldRepository.markInteracted(live.id)
                                    live.individualId to live.cardCharacterId
                                }.getOrThrow()
                            }
                            selectedEncounterId = null
                            navController.navigate(AppRoute.WildContact(chatTarget.first, chatTarget.second))
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            showRadarFailure(failure, R.string.ui_world_encounter_unavailable)
                        }
                    }
                },
                onBattle = { spawn?.let { beginRadarBattle(it) } },
                questContent = {
                    QuestTargetActions(questTasks.filter { it.targetIndividualId == individualId },
                        enabled = blocker == null && preparingSpawnId == null && questInteractionInProgress == null,
                        trials = questTrials.filter { it.objective.targetIndividualId == individualId }) { task ->
                        questInteractionInProgress = task.objectiveId
                        scope.launch {
                            try {
                                val updated = withContext(Dispatchers.IO) {
                                    ecosystem.executeCommand(radarLease, displayedCommand(RadarCommandKind.QUEST_INTERACTION, individualId)) { command ->
                                        val quest = app.container.questRepository.interactTargetLocked(individualId,
                                            task.objectiveId, task.revision, command.playerFix)
                                        app.container.db.chatDao().insertMessage(com.tamerslab.domain.chat.ChatMessageEntity(
                                            individualId = quest.giverId, role = "notice",
                                            content = resources.getString(R.string.quest_feedback_target_progress,
                                                task.targetName ?: resources.getString(R.string.ui_world_wild_digimon)),
                                            timestamp = System.currentTimeMillis()))
                                        quest
                                    }.getOrThrow()
                                }
                                selectedEncounterId = null
                                context.showAppFeedback(R.string.quest_target_recorded, updated.giverName)
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        if (app.container.chatRepository.publicDialogueAvailable()) kotlinx.coroutines.withTimeout(20_000) {
                                            app.container.chatRepository.triggerReactionForWildEncounter(updated.giverId, updated.giverCardCharacterId,
                                                "The app committed a physical quest interaction: ${task.type} with ${task.targetName}. " +
                                                    "React naturally to these registered facts in ${com.tamerslab.chat.PromptLocalization.currentLanguageTag()}; " +
                                                    "do not invent progress or rewards.\n${app.container.questRepository.contextFor(updated.id)}")
                                        }
                                    } catch (failure: Exception) {
                                        if (failure is CancellationException && failure !is kotlinx.coroutines.TimeoutCancellationException) throw failure
                                    }
                                }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (failure: Exception) {
                                showRadarFailure(failure, R.string.ui_world_encounter_unavailable)
                            } finally {
                                questInteractionInProgress = null
                            }
                        }
                    }
                }
            )
        }
    }

    if (showDebugSpawnPicker && debugSettings.enabled) {
        DebugSpawnPickerDialog(
            characters = debugSpawnCharacters,
            failed = debugSpawnPickerFailed,
            enabled = regionMutationsEnabled && !debugSpawnInProgress,
            onRetry = { debugSpawnPickerAttempt++ },
            onSelect = { id ->
                showDebugSpawnPicker = false
                spawnDebugDigimon(id)
            },
            onDismiss = { showDebugSpawnPicker = false }
        )
    }

    selectedSpecies?.let { species ->
        DexCharaDetailsDialog(
            currentChara = species,
            obscure = false,
            onClickClose = { selectedSpecies = null },
            onClickCharacter = { }
        )
    }

    if (showWorldSpawnSettings) {
        WorldSpawnDimSettingsDialog(
            cards = worldSpawnCards,
            canEdit = settingsMutationsEnabled,
            onDismiss = { showWorldSpawnSettings = false },
            onEnabledChange = { card, enabled ->
                scope.launch(Dispatchers.IO) {
                    val result = ecosystem.executeCommand(radarLease, displayedCommand(RadarCommandKind.SETTINGS)) {
                        app.container.db.cardDao().setWorldSpawnsEnabled(card.id, enabled)
                    }
                    withContext(Dispatchers.Main) {
                        runCatching { result.getOrThrow() }.onFailure { showRadarFailure(it, R.string.ui_world_storage_failure) }
                    }
                }
            },
            onLongClickCard = { card ->
                scope.launch {
                    selectedWorldDimSpecies = withContext(Dispatchers.IO) {
                        val profiles = app.container.db.speciesProfileDao()
                            .getByCardId(card.id)
                            .associateBy { it.cardCharacterId }
                        DexRepository(app.container.db).getCharactersByCardId(card.id)
                            .first()
                            .mapIndexed { index, character ->
                                WorldDimSpecies(
                                    character = character,
                                    name = profiles[character.id]?.matchedName
                                        ?: profiles[character.id]?.speciesName,
                                    fallbackNumber = index + 1
                                )
                            }
                    }
                    selectedWorldDim = card
                }
            }
        )
    }

    selectedWorldDim?.let { card ->
        WorldDimSpeciesDialog(
            card = card,
            species = selectedWorldDimSpecies,
            onDismiss = { selectedWorldDim = null }
        )
    }

    LaunchedEffect(observedInteractionId,radarResumed) {
        observedInteractionId?.takeIf { radarResumed }?.let { id -> withContext(Dispatchers.IO) { app.container.worldInteractionOrchestrator.recapOnDemand(id) } }
    }

    privateChallenge?.takeIf { incomingChallengeId!=null && it.sparring && it.type!=com.tamerslab.world.ecosystem.DialogueIntentType.ACCEPT_CHALLENGE }?.let { intent ->
        val spawn=spawns.firstOrNull { it.individualId==intent.initiatorId }
        AlertDialog(onDismissRequest={incomingChallengeId=null;privateChallenge=null},title={Text(stringResource(R.string.ui_world_team_preview))},
            text={Text((spawn?.speciesName ?: "Digimon")+" Â· 1 Ã— 1\n"+intent.reason)},
            confirmButton={VitalButton(onClick={
                val id=intent.id;incomingChallengeId=null;privateChallenge=null
                spawn?.let { startRadarBattle(it,id) }
            },enabled=spawn!=null && regionMutationsEnabled) { Text(stringResource(R.string.ui_world_accept_challenge)) }},
            dismissButton={TextButton(onClick={scope.launch(Dispatchers.IO) { app.container.worldInteractionOrchestrator.declineIntent(intent.id) };incomingChallengeId=null;privateChallenge=null}) {
                Text(stringResource(R.string.ui_world_decline_challenge))
            }})
    }
}

private data class WorldDimSpecies(
    val character: CharacterDtos.CardCharaProgress,
    val name: String?,
    val fallbackNumber: Int
)

private data class PreparedRadarBattle(
    val activeCharacterId: Long,
    val ally: com.tamerslab.battle.ui.OfflineBattleParticipant,
    val opponent: com.tamerslab.battle.ui.OfflineBattleParticipant,
    val interaction: WorldInteraction
)

private data class PreparedJoinedRadarBattle(val activeCharacterId:Long,val interaction:WorldInteraction,
    val allies:List<com.tamerslab.battle.ui.OfflineBattleParticipant>,val opponents:List<com.tamerslab.battle.ui.OfflineBattleParticipant>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorldEncounterActionSheet(
    spawn: WorldDtos.SpawnWithDetails?,
    displayName: String?,
    preparingBattle: Boolean,
    blockedReason: String?,
    onDismiss: () -> Unit,
    onChat: () -> Unit,
    onBattle: () -> Unit,
    questContent: (@Composable () -> Unit)? = null
) {
    val wildLabel = stringResource(R.string.ui_world_encounter_wild)
    val sprite = remember(spawn?.id, spawn?.spriteIdle?.contentHashCode()) {
        spawn?.let { runCatching {
            BitmapData(
                spawn.frameFor(PlayerMotion.IDLE, 0),
                spawn.spriteWidth.coerceAtLeast(1),
                spawn.spriteHeight.coerceAtLeast(1)
            ).getBitmap().asImageBitmap()
        }.getOrNull() }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDeepPurple,
        contentColor = TextPrimaryOnDark
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            sprite?.let {
                Image(
                    bitmap = it,
                    contentDescription = displayName ?: wildLabel,
                    filterQuality = FilterQuality.None,
                    modifier = Modifier.size(76.dp)
                )
            }
            Text(
                displayName ?: wildLabel,
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                blockedReason ?: stringResource(R.string.ui_world_encounter_prompt),
                color = TextSecondaryOnDark,
                style = MaterialTheme.typography.bodyMedium
            )
            questContent?.invoke()
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                VitalButton(
                    onClick = onChat,
                    enabled = !preparingBattle && blockedReason == null && spawn != null,
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp)
                ) {
                    Text(stringResource(R.string.ui_world_encounter_chat))
                }
                VitalButton(
                    style = com.tamerslab.components.VitalButtonStyle.PRIMARY,
                    onClick = onBattle,
                    enabled = !preparingBattle && blockedReason == null && spawn != null,
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp)
                ) {
                    Text(
                        stringResource(
                            if (preparingBattle) {
                                R.string.ui_world_encounter_preparing
                            } else {
                                R.string.ui_world_encounter_fight
                            }
                        )
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorldSpawnDimSettingsDialog(
    cards: List<Card>,
    canEdit: Boolean,
    onDismiss: () -> Unit,
    onEnabledChange: (Card, Boolean) -> Unit,
    onLongClickCard: (Card) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDeepPurple,
        contentColor = TextPrimaryOnDark
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(R.string.ui_world_spawn_dims_title),
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1
            )
            if (cards.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(SurfaceElevatedPurple.copy(alpha = 0.58f))
                        .cyberFrame()
                        .padding(16.dp)
                ) {
                    Text(
                        stringResource(R.string.ui_world_spawn_dims_empty),
                        color = TextSecondaryOnDark,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                Text(
                    stringResource(R.string.ui_world_spawn_dims_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondaryOnDark
                )
                LazyColumn(
                    modifier = Modifier.heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(cards, key = { it.id }) { card ->
                        WorldSpawnDimRow(
                            card = card,
                            enabled = canEdit,
                            onClick = { if (canEdit) onEnabledChange(card, !card.worldSpawnsEnabled) },
                            onLongClick = { onLongClickCard(card) },
                            onCheckedChange = { enabled -> onEnabledChange(card, enabled) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WorldSpawnDimRow(
    card: Card,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onCheckedChange: (Boolean) -> Unit
) {
    val logo = remember(card.logo) {
        runCatching {
            BitmapData(card.logo, card.logoWidth, card.logoHeight).getBitmap().asImageBitmap()
        }.getOrNull()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .background(SurfaceElevatedPurple.copy(alpha = 0.62f))
            .cyberFrame()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        logo?.let { image ->
            DimLogo(
                bitmap = image,
                contentDescription = card.name,
                filterQuality = FilterQuality.None,
                modifier = Modifier.size(40.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(card.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(
                    if (card.worldSpawnsEnabled) {
                        R.string.ui_world_spawn_dims_enabled
                    } else {
                        R.string.ui_world_spawn_dims_disabled
                    }
                ),
                style = MaterialTheme.typography.labelMedium,
                color = if (card.worldSpawnsEnabled) VitalCyan else TextSecondaryOnDark,
                maxLines = 1
            )
        }
        Switch(
            checked = card.worldSpawnsEnabled,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.semantics { contentDescription = card.name },
            colors = SwitchDefaults.colors(
                checkedThumbColor = SurfaceDeepPurple,
                checkedTrackColor = VitalCyan,
                uncheckedThumbColor = TextSecondaryOnDark,
                uncheckedTrackColor = SurfaceStroke
            )
        )
    }
}

@Composable
private fun WorldDimSpeciesDialog(
    card: Card,
    species: List<WorldDimSpecies>,
    onDismiss: () -> Unit
) {
    var idleFrame by remember { mutableIntStateOf(0) }
    val animateSprites = com.tamerslab.components.motionEnabled()
    LaunchedEffect(animateSprites) {
        idleFrame = 0
        if (!animateSprites) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(700L)
            idleFrame = 1 - idleFrame
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ui_world_spawn_dim_species_title, card.name)) },
        text = {
            if (species.isEmpty()) {
                Text(stringResource(R.string.ui_world_spawn_dim_species_empty))
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(species, key = { it.character.id }) { item ->
                        val label = item.name ?: stringResource(
                            R.string.ui_world_spawn_dim_species_fallback,
                            item.fallbackNumber
                        )
                        val idleSprite = if (idleFrame == 0) {
                            item.character.spriteIdle
                        } else {
                            item.character.spriteIdle2
                        }
                        val idleImage = remember(idleSprite) {
                            runCatching {
                                BitmapData(
                                    idleSprite,
                                    item.character.spriteWidth,
                                    item.character.spriteHeight
                                ).getBitmap().asImageBitmap()
                            }.getOrNull()
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            idleImage?.let { image ->
                                Image(
                                    bitmap = image,
                                    contentDescription = label,
                                    filterQuality = FilterQuality.None,
                                    modifier = Modifier
                                        .size(48.dp)
                                )
                            }
                            Text(
                                text = label,
                                modifier = Modifier.padding(start = 8.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_close)) }
        }
    )
}

@Composable
private fun cardinalDirection(degrees: Float): String {
    val names = stringArrayResource(R.array.world_cardinal_directions_8)
    val normalized = (degrees + 360f) % 360f
    val index = ((normalized + 22.5f) / 45f).toInt() % 8
    return names[index]
}

private enum class PlayerMotion { IDLE, WALK, RUN }

@Composable
private fun WorldBiome.displayName(): String = stringResource(
    when (this) {
        WorldBiome.URBAN -> R.string.ui_world_biome_urban
        WorldBiome.PARK -> R.string.ui_world_biome_park
        WorldBiome.WATER -> R.string.ui_world_biome_water
        WorldBiome.RURAL -> R.string.ui_world_biome_rural
        WorldBiome.INDUSTRIAL -> R.string.ui_world_biome_industrial
        WorldBiome.ENTERTAINMENT -> R.string.ui_world_biome_entertainment
        WorldBiome.GRASSLAND -> R.string.ui_world_biome_grassland
        WorldBiome.NULL -> R.string.ui_world_biome_null
    }
)

private fun WorldDtos.SpawnWithDetails.frameFor(motion: PlayerMotion, frame: Int): ByteArray = when (motion) {
    PlayerMotion.IDLE -> if (frame == 0) spriteIdle else spriteIdle2
    PlayerMotion.WALK -> if (frame == 0) spriteWalk else spriteWalk2
    PlayerMotion.RUN -> if (frame == 0) spriteRun else spriteRun2
}





