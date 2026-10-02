package com.github.nacabaro.vbhelper.screens.worldScreen

import android.Manifest
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.location.Location
import android.widget.Toast
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
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
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.components.TopBanner
import com.github.nacabaro.vbhelper.components.motionEnabled
import com.github.nacabaro.vbhelper.components.VitalButton
import com.github.nacabaro.vbhelper.components.cyberFrame
import com.github.nacabaro.vbhelper.di.VBHelper
import com.github.nacabaro.vbhelper.domain.card.Card
import com.github.nacabaro.vbhelper.dtos.CharacterDtos
import com.github.nacabaro.vbhelper.dtos.WorldDtos
import com.github.nacabaro.vbhelper.navigation.NavigationItems
import com.github.nacabaro.vbhelper.screens.offlineBattleParticipant
import com.github.nacabaro.vbhelper.screens.cardScreen.dialogs.DexCharaDetailsDialog
import com.github.nacabaro.vbhelper.screens.offlineBattle.OfflineArenaManifest
import com.github.nacabaro.vbhelper.screens.offlineBattle.WorldRadarBattleContent
import com.github.nacabaro.vbhelper.screens.offlineBattle.offlineBattleViewModel
import com.github.nacabaro.vbhelper.source.DexRepository
import com.github.nacabaro.vbhelper.ui.theme.RadarCompass
import com.github.nacabaro.vbhelper.ui.theme.RadarFollower
import com.github.nacabaro.vbhelper.ui.theme.SpaceBlack
import com.github.nacabaro.vbhelper.ui.theme.SurfaceDeepPurple
import com.github.nacabaro.vbhelper.ui.theme.SurfaceElevatedPurple
import com.github.nacabaro.vbhelper.ui.theme.SurfaceStroke
import com.github.nacabaro.vbhelper.ui.theme.TextPrimaryOnDark
import com.github.nacabaro.vbhelper.ui.theme.TextSecondaryOnDark
import com.github.nacabaro.vbhelper.ui.theme.VitalCyan
import com.github.nacabaro.vbhelper.utils.BitmapData
import com.github.nacabaro.vbhelper.utils.getBitmap
import com.github.nacabaro.vbhelper.world.WorldBiome
import com.github.nacabaro.vbhelper.world.GeoPoint
import com.github.nacabaro.vbhelper.world.RadarWorldGeometry
import com.github.nacabaro.vbhelper.world.ecosystem.WorldInteraction
import com.github.nacabaro.vbhelper.world.ecosystem.WorldInteractionException
import com.github.nacabaro.vbhelper.world.ecosystem.InteractionFailure
import com.github.nacabaro.vbhelper.world.ecosystem.WorldPlayerFix
import com.github.nacabaro.vbhelper.world.ecosystem.EcosystemSeed
import com.github.nacabaro.vbhelper.world.ecosystem.EcosystemStatus
import com.github.nacabaro.vbhelper.world.ecosystem.RadarCommand
import com.github.nacabaro.vbhelper.world.ecosystem.RadarCommandKind
import com.github.nacabaro.vbhelper.world.ecosystem.RadarCommandResult
import com.github.nacabaro.vbhelper.world.ecosystem.RadarCommandException
import com.github.nacabaro.vbhelper.world.ecosystem.RadarRejection
import com.github.nacabaro.vbhelper.world.ecosystem.WorldPauseReason
import com.github.nacabaro.vbhelper.world.ecosystem.InteractionOrigin
import com.github.nacabaro.vbhelper.world.ecosystem.InteractionType
import com.github.nacabaro.vbhelper.world.ecosystem.InteractionState
import com.github.nacabaro.vbhelper.world.ecosystem.EcosystemInteractionSummary
import com.github.nacabaro.vbhelper.world.ecosystem.commandRejection
import com.github.nacabaro.vbhelper.world.ecosystem.getOrThrow
import com.github.nacabaro.vbhelper.world.ecosystem.displayPosition
import com.github.nacabaro.vbhelper.world.ecosystem.publicInteractionFor
import com.github.nacabaro.vbhelper.world.ecosystem.speechFor
import com.github.nacabaro.vbhelper.world.ecosystem.isPlayerBattle
import com.github.nacabaro.vbhelper.battle.offline.core.BattleOutcome
import com.github.nacabaro.vbhelper.world.worldRadarBattleParticipant
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
    firstPersonPreferred: Boolean = false,
    onViewPreferenceChanged: (Boolean) -> Unit = {},
    labelsPreferred: Boolean = false,
    onLabelsPreferenceChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val app = context.applicationContext as VBHelper
    val ecosystem = app.container.worldEcosystemCoordinator
    val worldSnapshot by ecosystem.snapshot.collectAsState()
    var firstPersonEnabled by rememberSaveable { mutableStateOf(firstPersonPreferred) }
    LaunchedEffect(firstPersonEnabled) { onViewPreferenceChanged(firstPersonEnabled) }
    var firstPersonFailed by remember { mutableStateOf(false) }
    var requestedBattleRenderer by remember { mutableStateOf(false) }
    val rendererOwnership = remember { RadarRendererOwnership() }
    val rendererState by rendererOwnership.state.collectAsState()
    val battleRendererPresent = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    val firstPersonRendererPresent = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    var cameraPitch by rememberSaveable { mutableFloatStateOf(RADAR_DEFAULT_PITCH) }
    val currentPitch by rememberUpdatedState(cameraPitch)
    var showEntityLabels by rememberSaveable { mutableStateOf(labelsPreferred) }
    LaunchedEffect(showEntityLabels) { onLabelsPreferenceChanged(showEntityLabels) }
    var fpProjection by remember { mutableStateOf(emptyList<RadarProjection>()) }
    var firstPersonViewportWidth by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val radarLease = remember { "radar:${java.util.UUID.randomUUID()}" }
    var radarResumed by remember { mutableStateOf(false) }
    val viewModelOwner = checkNotNull(LocalViewModelStoreOwner.current)
    val battleViewModel = remember(viewModelOwner) { offlineBattleViewModel(viewModelOwner) }
    val battleSessionState by battleViewModel.state.collectAsState()
    var location by remember { mutableStateOf<Location?>(null) }
    var origin by remember { mutableStateOf<GeoPoint?>(null) }
    var selectedEncounterId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedEncounterName by rememberSaveable { mutableStateOf<String?>(null) }
    var observedInteractionId by rememberSaveable { mutableStateOf<String?>(null) }
    var incomingChallengeId by rememberSaveable { mutableStateOf<String?>(null) }
    var privateChallenge by remember { mutableStateOf<com.github.nacabaro.vbhelper.world.ecosystem.WorldDialogueIntent?>(null) }
    // The outgoing destination remains composed during tab transitions, even
    // after it is removed from NavController's active back stack.
    val worldState = remember(worldEntry) { worldEntry.savedStateHandle }
    val interactionPayload by remember(worldState) { worldState.getStateFlow<String?>("radar-interaction",null) }.collectAsState()
    LaunchedEffect(interactionPayload) {
        interactionPayload?.let { observedInteractionId=it;worldState["radar-interaction"]=null }
    }
    val challengePayload by remember(worldState) {
        worldState.getStateFlow<String?>("radar-challenge",null)
    }.collectAsState()
    LaunchedEffect(challengePayload) {
        challengePayload?.let { id ->
            incomingChallengeId=id
            privateChallenge=withContext(Dispatchers.IO) { app.container.db.worldInteractionDao().getIntent(id) }
            worldState.set<String?>("radar-challenge",null)
        }
    }
    var preparingSpawnId by remember { mutableStateOf<Long?>(null) }
    var debugSpawnInProgress by remember { mutableStateOf(false) }
    var battleSpawnId by rememberSaveable { mutableStateOf<Long?>(null) }
    var battleCharacterId by rememberSaveable { mutableStateOf<Long?>(null) }
    var battleSessionId by rememberSaveable { mutableStateOf<String?>(null) }
    var committedBattleSessionId by rememberSaveable { mutableStateOf<String?>(null) }
    var battleTerminalOutcome by rememberSaveable { mutableStateOf<BattleOutcome?>(null) }
    var battleExitInProgress by remember { mutableStateOf(false) }
    var pendingBattleEventId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingBattleOutcome by rememberSaveable { mutableStateOf<BattleOutcome?>(null) }
    val battleActive = battleSessionId != null
    val desiredRenderer = when {
        battleActive || requestedBattleRenderer -> RadarRendererOwner.BATTLE
        firstPersonEnabled && radarResumed && pendingBattleEventId == null -> RadarRendererOwner.FIRST_PERSON
        else -> RadarRendererOwner.NONE
    }
    LaunchedEffect(desiredRenderer) {
        rendererOwnership.request(desiredRenderer)
        // A loading/cancelled battle can own permission without having created a renderer.
        val owner=rendererOwnership.owner
        val present=when(owner) {
            RadarRendererOwner.BATTLE -> battleRendererPresent.get()
            RadarRendererOwner.FIRST_PERSON -> firstPersonRendererPresent.get()
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
    LaunchedEffect(battleActive) {
        onFullScreenBattleChanged(battleActive)
    }
    val explorationActive = radarResumed && !battleActive && pendingBattleEventId == null && worldSnapshot.status != EcosystemStatus.FROZEN
    val compass = rememberWorldCompass(location, enabled = explorationActive)
    val heading = compass.heading ?: 0f // Uncalibrated map is explicitly north-up.
    var frozenRadarHeading by remember { mutableFloatStateOf(0f) }
    SideEffect {
        if (explorationActive) frozenRadarHeading = heading
    }
    val radarHeading = if (explorationActive) heading else frozenRadarHeading
    val isDebuggableBuild = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    var showCompassDiagnostics by remember { mutableStateOf(false) }
    val spawns by app.container.worldRepository.observeSpawns().collectAsState(initial = emptyList())
    val activeOwned by app.container.db.userCharacterDao().getActiveCharacter().collectAsState(initial=null)
    val attemptedWildAttacks=remember { mutableMapOf<String,Int>() }
    val pendingRecruits by app.container.worldRepository.observePendingRecruits()
        .collectAsState(initial = emptyList())
    val worldSpawnCardsState by app.container.db.cardDao().observeCardsForWorldSpawns()
        .collectAsState(initial = null)
    val worldSpawnCards = worldSpawnCardsState.orEmpty()
    val currentBiome by app.container.worldRepository.currentBiome.collectAsState()
    val scope = rememberCoroutineScope()
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

    fun displayedCommand(kind: RadarCommandKind, id: String? = null): RadarCommand =
        RadarCommand(displayedCommandStamp ?: throw RadarCommandException(rejection = RadarRejection.NOT_READY), kind, id)

    suspend fun awaitBattleRenderer() {
        requestedBattleRenderer = true
        rendererOwnership.request(RadarRendererOwner.BATTLE)
        withTimeout(30_000) { rendererOwnership.state.first { it.owner==RadarRendererOwner.BATTLE && !it.releasing } }
    }

    fun showRadarFailure(failure: Throwable, fallback: Int) {
        val message = when (failure) {
            is RadarCommandException -> resources.getString(failure.messageResource())
            is WorldInteractionException -> resources.getString(failure.messageResource())
            else -> failure.message ?: resources.getString(fallback)
        }
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
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
                val biome = withContext(Dispatchers.IO) { app.container.worldRepository.resolveSpawnBiome(fix.position) }
                val current = ecosystem.snapshot.value
                if (current.leaseEpoch != epoch) return@launch
                val ticket = current.commandStamp ?: return@launch
                val result = withContext(Dispatchers.IO) {
                    ecosystem.executeCommand(radarLease, RadarCommand(ticket, RadarCommandKind.REGION)) { command ->
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
                        if (outcome != null) app.container.worldRepository.recordRadarBattleResult(id, outcome)
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
                if(chat!=null && app.container.db.worldInteractionDao().getResult(id)!=null) {
                    navController.navigate(NavigationItems.WildContact.route.replace("{individualId}",android.net.Uri.encode(chat.first))
                        .replace("{cardCharacterId}",chat.second.toString())) { launchSingleTop=true }
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
        battleViewModel.finishSession()
        battleSpawnId = null
        battleCharacterId = null
        battleSessionId = null
        committedBattleSessionId = null
        battleTerminalOutcome = null
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
                val command=RadarCommand(displayedCommandStamp ?: throw RadarCommandException(rejection=RadarRejection.NOT_READY),
                    RadarCommandKind.EVENT_RESERVATION,interactionId=sourceId)
                val prepared=withContext(Dispatchers.IO) {
                    ecosystem.executeCommand(radarLease,command) { context ->
                        val characterDao=app.container.db.userCharacterDao()
                        val active=characterDao.getActiveCharacter().first() ?: error(resources.getString(R.string.ui_world_battle_choose_active))
                        val source=app.container.db.worldInteractionDao().getInteraction(sourceId) ?: throw WorldInteractionException(InteractionFailure.UNAVAILABLE)
                        val record=app.container.db.worldInteractionDao().getNpcBattle(sourceId) ?: throw WorldInteractionException(InteractionFailure.UNAVAILABLE)
                        val remaining=com.github.nacabaro.vbhelper.world.ecosystem.NpcBattleAdapter.recover(source,record).snapshot()
                        val event=app.container.worldRepository.interactions.reserveJoinedBattle(sessionId,sourceId,sourceRevision,
                            active.id,secondOwned,alliedWild,context.playerFix,context.session.tickIndex)
                        val profiles=characterDao.getAllBattleParticipantProfiles()
                        val ownedIds=if(alliedWild!=null)listOf(active.id) else listOf(active.id,secondOwned!!)
                        val allies=ownedIds.map { id -> offlineBattleParticipant(characterDao.getCharacterWithSprites(id),profiles.first { it.sourceCharacterId==id }) }.toMutableList()
                        val opponents=mutableListOf<com.github.nacabaro.vbhelper.screens.OfflineBattleParticipant>()
                        val health=(remaining.alliedMembers+remaining.opposingMembers).associateBy { it.combatantId.substringAfter(':') }
                        app.container.db.worldInteractionDao().getParticipants(sessionId).filter { it.role==com.github.nacabaro.vbhelper.world.ecosystem.InteractionRole.WILD }.forEach { p ->
                            val spawn=app.container.worldRepository.getSpawn(p.spawnId!!) ?: throw WorldInteractionException(InteractionFailure.UNAVAILABLE)
                            val state=health[p.individualId] ?: throw WorldInteractionException(InteractionFailure.UNAVAILABLE)
                            val wild=worldRadarBattleParticipant(spawn).copy(initialHealth=state.health,initialEnergy=state.energy)
                            if(p.side==com.github.nacabaro.vbhelper.world.ecosystem.InteractionSide.ALLIED)allies+=wild else opponents+=wild
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
        preparingSpawnId = spawn.id
        val sessionId = "world-radar:${java.util.UUID.randomUUID()}"
        scope.launch {
            try {
                val ticket = if(wildAttack==null) displayedCommand(RadarCommandKind.BATTLE_RESERVATION, spawn.individualId)
                    else RadarCommand(displayedCommandStamp ?: throw RadarCommandException(rejection=RadarRejection.NOT_READY),RadarCommandKind.EVENT_RESERVATION,interactionId=wildAttack.id)
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
                    ecosystem.executeCommand(radarLease, ticket) { command ->
                        val activeCharacter = owned.first
                        val currentOwned = app.container.db.userCharacterDao().getCharacterSync(activeCharacter.id)
                            ?: throw WorldInteractionException(InteractionFailure.UNAVAILABLE)
                        if (currentOwned.individualId != owned.second.individualId || currentOwned.charId != activeCharacter.charId) {
                            throw WorldInteractionException(InteractionFailure.UNAVAILABLE)
                        }
                        val checkpoint = command.session
                        val challenge=challengeId?.let { app.container.db.worldInteractionDao().getIntent(it) }
                        if(challengeId!=null) {
                            if(challenge==null || challenge.status!=com.github.nacabaro.vbhelper.world.ecosystem.DialogueIntentStatus.PENDING ||
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
                            com.github.nacabaro.vbhelper.world.ecosystem.WorldChatMemoryRepository(app.container.db).capturePrivateBattle(interaction.id,challenge)
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
                            app.container.db.worldInteractionDao().updateIntent(intent.copy(status=com.github.nacabaro.vbhelper.world.ecosystem.DialogueIntentStatus.ACCEPTED,linkedBattleId=sessionId))
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
                showRadarFailure(failure, R.string.ui_world_battle_start_failed)
            } finally {
                preparingSpawnId = null
                requestedBattleRenderer = false
            }
        }
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
    LaunchedEffect(worldSnapshot.session?.tickIndex,worldSnapshot.status,activeOwned?.id,radarResumed,battleActive,spawns) {
        if(!regionMutationsEnabled || battleActive || preparingSpawnId!=null || activeOwned==null || incomingChallengeId!=null || privateChallenge!=null) return@LaunchedEffect
        val attack=worldSnapshot.interactions.firstOrNull { it.type==InteractionType.BATTLE && it.state==InteractionState.ACTIVE &&
            it.isPlayerBattle && (worldSnapshot.session?.tickIndex ?: 0)>=it.nextActionTick &&
            (attemptedWildAttacks[it.id] ?: 0)<3 } ?: return@LaunchedEffect
        val attacker=spawns.firstOrNull { it.individualId in attack.participantIds } ?: return@LaunchedEffect
        val position=worldSnapshot.individuals.firstOrNull { it.individualId==attacker.individualId }?.position ?: return@LaunchedEffect
        if(worldSnapshot.playerFix?.let { RadarWorldGeometry.relative(it.position,position).withinInteractionRange }!=true) return@LaunchedEffect
        attemptedWildAttacks[attack.id]=(attemptedWildAttacks[attack.id] ?: 0)+1
        if(attack.publicReason?.startsWith("WILD_ATTACK:")==true) Toast.makeText(context,resources.getString(R.string.ui_world_wild_attack),Toast.LENGTH_SHORT).show()
        startRadarBattle(attacker,wildAttack=attack)
    }
    LaunchedEffect(privateChallenge,regionMutationsEnabled,activeOwned?.id,worldSnapshot.claimedIndividuals) {
        val intent=privateChallenge ?: return@LaunchedEffect
        if((intent.sparring && intent.type!=com.github.nacabaro.vbhelper.world.ecosystem.DialogueIntentType.ACCEPT_CHALLENGE) || incomingChallengeId==null || !regionMutationsEnabled || activeOwned==null || preparingSpawnId!=null || battleActive || intent.initiatorId in worldSnapshot.claimedIndividuals) return@LaunchedEffect
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
        navController.navigate(NavigationItems.WorldConversation.route.replace("{interactionId}",android.net.Uri.encode(id))) { launchSingleTop=true }
    }
    fun openInteraction(event: EcosystemInteractionSummary) {
        if(event.type==InteractionType.CHAT) openConversation(event.id) else observedInteractionId=event.id
    }
    LaunchedEffect(observedInteractionId,worldSnapshot.interactions,radarResumed) {
        if(radarResumed) worldSnapshot.interactions.firstOrNull { it.id==observedInteractionId && it.type==InteractionType.CHAT }
            ?.let { openConversation(it.id) }
    }

    Scaffold(
        topBar = {
            AnimatedVisibility(
                visible = !battleActive,
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
                Column {
                    TopBanner(text = when {
                        compass.heading != null -> "${stringResource(R.string.nav_world)} • ${cardinalDirection(heading)}"
                        else -> stringResource(R.string.nav_world)
                    }, onGearClick = { showWorldSpawnSettings = true })
                    WorldSectionTabs(
                        selectedTab = selectedWorldTab,
                        onTabSelected = onWorldTabSelected
                    )
                }
            }
        },
        contentWindowInsets = WindowInsets.statusBars
    ) { contentPadding ->
        Column(
            Modifier
                .padding(contentPadding)
                .fillMaxSize()
                .then(if (battleActive) Modifier else Modifier.verticalScroll(rememberScrollState()))
                .padding(16.dp)
        ) {
            WorldRadarBattleContent(
                viewModel = battleViewModel,
                battleActive = battleActive,
                rendererAllowed = rendererState.owner == RadarRendererOwner.BATTLE && rendererState.requested == RadarRendererOwner.BATTLE && !rendererState.releasing,
                onRendererCreated = { battleRendererPresent.set(true) },
                onRendererReleased = {
                    if(rendererOwnership.released(RadarRendererOwner.BATTLE, battleRendererToken)) battleRendererPresent.set(false)
                },
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
                        scope.launch {
                            runCatching {
                                withContext(NonCancellable + Dispatchers.IO) {
                                    app.container.worldRepository.recordRadarBattleResult(
                                        interactionId = activeSessionId,
                                        outcome = outcome
                                    )
                                }
                            }.onFailure { failure ->
                                committedBattleSessionId = null
                                Toast.makeText(
                                    context,
                                    failure.message ?: resources.getString(R.string.ui_world_battle_record_failed),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                },
                onExit = ::leaveRadarBattle,
                radarViewport = {
            if (firstPersonEnabled && rendererState.owner == RadarRendererOwner.FIRST_PERSON &&
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
                        onFailure = { firstPersonFailed = true; firstPersonEnabled = false },
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
                                    selectedSpecies=withContext(Dispatchers.IO) { DexRepository(app.container.db).getCharactersByCardId(spawn.cardId).first().firstOrNull { it.id==spawn.cardCharacterId } }
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
            } else if (firstPersonEnabled || rendererState.releasing) {
                Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) { Text(stringResource(R.string.ui_world_renderer_handoff)) }
            } else {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(battleActive) {
                        detectTransformGestures { _, _, gestureZoom, _ ->
                            if (!battleActive) zoom = (zoom * gestureZoom).coerceIn(0.5f, 4f)
                        }
                    }
            ) {
                val density = LocalDensity.current
                val cardinalNames = stringArrayResource(R.array.world_cardinal_directions_8)
                val boxWidthPx = with(density) { maxWidth.toPx() }
                val center = Offset(boxWidthPx / 2f, boxWidthPx / 2f)
                val visibleRadiusMeters = 500.0 / zoom
                val scale = (boxWidthPx / 2f) / visibleRadiusMeters.toFloat()
                val interactionRadiusPx = (INTERACTION_RANGE_METERS * scale).toFloat()
                val distanceRingMeters = (RADAR_RING_INTERVAL_METERS..visibleRadiusMeters.toInt())
                    .step(RADAR_RING_INTERVAL_METERS)
                    .toList()

                // O jogador permanece sempre no centro da tela. O mundo é que se desloca
                // em sentido contrário ao movimento do jogador.
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

                Canvas(modifier = Modifier.fillMaxSize()) {
                    // A grade representa o mundo, se desloca com o jogador e gira com a bússola.
                    withTransform({
                        rotate(degrees = -radarHeading, pivot = center)
                    }) {
                        val gridSpacingPx = (GRID_SIZE_METERS * scale).toFloat().coerceAtLeast(20f)
                        val originX = center.x - playerDisplacement.x
                        val originY = center.y - playerDisplacement.y

                        // Desenha a grade em uma área maior que a tela para que a rotação
                        // nunca revele as bordas vazias do Canvas.
                        val diagonal = sqrt(
                            size.width * size.width +
                                size.height * size.height
                        )

                        var x = originX % gridSpacingPx
                        while (x > -diagonal) x -= gridSpacingPx
                        while (x < size.width + diagonal) {
                            drawLine(
                                SurfaceStroke.copy(alpha = 0.72f),
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
                                SurfaceStroke.copy(alpha = 0.72f),
                                Offset(-diagonal, y),
                                Offset(size.width + diagonal, y),
                                strokeWidth = 2f
                            )
                            y += gridSpacingPx
                        }
                    }

                    distanceRingMeters.forEach { meters ->
                        drawCircle(
                            color = TextPrimaryOnDark.copy(alpha = 0.18f),
                            radius = meters * scale,
                            center = playerOffset,
                            style = Stroke(width = 1.5f)
                        )
                    }

                    val pulseRadiusPx = radarPulse.value * visibleRadiusMeters.toFloat() * scale
                    drawCircle(
                        color = VitalCyan.copy(alpha = 0.65f * (1f - radarPulse.value)),
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
                                                        DexRepository(app.container.db)
                                                            .getCharactersByCardId(spawn.cardId)
                                                            .first()
                                                            .firstOrNull { it.id == spawn.cardCharacterId }
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
                        90f to RadarCompass,
                        180f to RadarCompass,
                        270f to RadarCompass
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
            }

                },
                radarControls = {
            observedInteractionId?.let { id ->
                WorldInteractionOverlay(id,app.container.db,worldSnapshot,onClose={observedInteractionId=null},hasOwnedPartner=activeOwned!=null,
                    onOpenConversation={openConversation(id)},
                    onDefend={
                        val attack=worldSnapshot.interactions.firstOrNull { it.id==id }
                        val attacker=attack?.let { event -> spawns.firstOrNull { it.individualId in event.participantIds } }
                        if(attack!=null && attacker!=null) startRadarBattle(attacker,wildAttack=attack)
                    },
                    onJoinBattle={ revision,ally,second -> startJoinedRadarBattle(id,revision,ally,second) },
                    )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.ui_world_nearby_count, worldSnapshot.individuals.size),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentWidth()
            )

            RadarSessionStatusPanel(surfaceState, onRecovery = ::recoverRadar, modifier = Modifier.padding(top = 8.dp))

            Row(Modifier.fillMaxWidth().padding(top=8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                VitalButton(onClick={firstPersonEnabled=false},enabled=firstPersonEnabled,modifier=Modifier.weight(1f).heightIn(min=48.dp)) { Text(stringResource(R.string.ui_world_mode_2d)) }
                VitalButton(onClick={firstPersonFailed=false;firstPersonEnabled=true},enabled=!firstPersonEnabled,modifier=Modifier.weight(1f).heightIn(min=48.dp)) { Text(stringResource(R.string.ui_world_mode_fp)) }
            }
            if(firstPersonFailed) Text(stringResource(R.string.ui_world_fp_failure),color=MaterialTheme.colorScheme.error,modifier=Modifier.padding(top=8.dp))
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text(stringResource(R.string.ui_world_show_labels),modifier=Modifier.weight(1f))
                Switch(checked=showEntityLabels,onCheckedChange={showEntityLabels=it})
            }
            if(firstPersonEnabled) {
                Text(stringResource(R.string.ui_world_vertical_look),style=MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    VitalButton(onClick={cameraPitch=radarPitchStep(cameraPitch,true)},modifier=Modifier.weight(1f).heightIn(min=48.dp)) { Text(stringResource(R.string.ui_world_look_up)) }
                    TextButton(onClick={cameraPitch=RADAR_DEFAULT_PITCH},modifier=Modifier.heightIn(min=48.dp)) { Text(stringResource(R.string.ui_world_look_reset)) }
                    VitalButton(onClick={cameraPitch=radarPitchStep(cameraPitch,false)},modifier=Modifier.weight(1f).heightIn(min=48.dp)) { Text(stringResource(R.string.ui_world_look_down)) }
                }
            }

            Text(stringResource(R.string.ui_world_scene_hint),style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(top=8.dp))

            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                VitalButton(onClick = { zoom = (zoom / 1.5f).coerceAtLeast(.5f) }) { Text("-") }
                Text(
                    stringResource(R.string.ui_world_zoom_label, (zoom * 100).toInt()),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)
                )
                VitalButton(onClick = { zoom = (zoom * 1.5f).coerceAtMost(4f) }) { Text("+") }
            }
            if (isDebuggableBuild) {
                VitalButton(
                    onClick = {
                        if (debugSpawnInProgress) return@VitalButton
                        debugSpawnInProgress = true
                        scope.launch {
                            try {
                                val spawnId = withContext(Dispatchers.IO) {
                                    ecosystem.executeCommand(radarLease, displayedCommand(RadarCommandKind.REGION)) { command ->
                                        val position = command.playerFix.position
                                        app.container.worldRepository.spawnDebugDigimon(position.latitude, position.longitude)
                                    }.getOrThrow()
                                }
                                Toast.makeText(
                                    context,
                                    resources.getString(
                                        if (spawnId != null) R.string.ui_world_debug_spawn_success
                                        else R.string.ui_world_debug_spawn_unavailable
                                    ),
                                    Toast.LENGTH_SHORT
                                ).show()
                            } catch (failure: Exception) {
                                if (failure is CancellationException) throw failure
                                showRadarFailure(failure, R.string.ui_world_debug_spawn_unavailable)
                            } finally {
                                debugSpawnInProgress = false
                            }
                        }
                    },
                    enabled = regionMutationsEnabled && !debugSpawnInProgress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentWidth()
                        .padding(top = 8.dp)
                ) {
                    Text(
                        stringResource(
                            if (debugSpawnInProgress) R.string.ui_world_debug_spawn_loading
                            else R.string.ui_world_debug_spawn_button
                        )
                    )
                }
            }
            if (pendingRecruits.isNotEmpty()) {
                VitalButton(
                    onClick = { navController.navigate(NavigationItems.WorldRecruits.route) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentWidth()
                        .padding(top = 8.dp)
                ) {
                    Text(stringResource(R.string.ui_world_recruits_button))
                }
            }

            if (!hasLocationPermission || location == null) {
                VitalButton(
                    onClick = {
                        launcher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentWidth()
                        .padding(top = 8.dp)
                ) { Text(stringResource(R.string.ui_world_enable_location)) }
            }
                },
                modifier = Modifier.fillMaxWidth().then(if (battleActive) Modifier.weight(1f) else Modifier)
            )
    }

    if (!battleActive) {
        selectedEncounterId?.let { individualId ->
            val spawn = spawns.firstOrNull { it.individualId == individualId }
            val blocker = if (displayedCommandStamp == null) RadarRejection.NOT_READY else
                worldSnapshot.commandRejection(RadarCommand(displayedCommandStamp, RadarCommandKind.ENCOUNTER, individualId), worldSnapshot.observedAt)
            WorldEncounterActionSheet(
                spawn = spawn,
                displayName = spawn?.speciesName ?: selectedEncounterName,
                preparingBattle = spawn != null && preparingSpawnId == spawn.id,
                blockedReason = blocker?.let { resources.getString(RadarCommandException(rejection = it).messageResource()) },
                onDismiss = { if (preparingSpawnId == null) selectedEncounterId = null },
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
                            navController.navigate(NavigationItems.WildContact.route.replace("{individualId}",android.net.Uri.encode(chatTarget.first))
                                .replace("{cardCharacterId}",chatTarget.second.toString()))
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            showRadarFailure(failure, R.string.ui_world_encounter_unavailable)
                        }
                    }
                },
                onBattle = { spawn?.let { startRadarBattle(it) } }
            )
        }
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

    privateChallenge?.takeIf { incomingChallengeId!=null && it.sparring && it.type!=com.github.nacabaro.vbhelper.world.ecosystem.DialogueIntentType.ACCEPT_CHALLENGE }?.let { intent ->
        val spawn=spawns.firstOrNull { it.individualId==intent.initiatorId }
        AlertDialog(onDismissRequest={incomingChallengeId=null;privateChallenge=null},title={Text(stringResource(R.string.ui_world_team_preview))},
            text={Text((spawn?.speciesName ?: "Digimon")+" · 1 × 1\n"+intent.reason)},
            confirmButton={VitalButton(onClick={
                val id=intent.id;incomingChallengeId=null;privateChallenge=null
                spawn?.let { startRadarBattle(it,id) }
            },enabled=spawn!=null && regionMutationsEnabled) { Text(stringResource(R.string.ui_world_accept_challenge)) }},
            dismissButton={TextButton(onClick={scope.launch(Dispatchers.IO) { app.container.worldInteractionOrchestrator.declineIntent(intent.id) };incomingChallengeId=null;privateChallenge=null}) {
                Text(stringResource(R.string.ui_world_decline_challenge))
            }})
    }
}
}

private data class WorldDimSpecies(
    val character: CharacterDtos.CardCharaProgress,
    val name: String?,
    val fallbackNumber: Int
)

private data class PreparedRadarBattle(
    val activeCharacterId: Long,
    val ally: com.github.nacabaro.vbhelper.screens.OfflineBattleParticipant,
    val opponent: com.github.nacabaro.vbhelper.screens.OfflineBattleParticipant,
    val interaction: WorldInteraction
)

private data class PreparedJoinedRadarBattle(val activeCharacterId:Long,val interaction:WorldInteraction,
    val allies:List<com.github.nacabaro.vbhelper.screens.OfflineBattleParticipant>,val opponents:List<com.github.nacabaro.vbhelper.screens.OfflineBattleParticipant>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorldEncounterActionSheet(
    spawn: WorldDtos.SpawnWithDetails?,
    displayName: String?,
    preparingBattle: Boolean,
    blockedReason: String?,
    onDismiss: () -> Unit,
    onChat: () -> Unit,
    onBattle: () -> Unit
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
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
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
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onChat,
                    enabled = !preparingBattle && blockedReason == null && spawn != null,
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp)
                ) {
                    Text(stringResource(R.string.ui_world_encounter_chat))
                }
                Button(
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
            Image(
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
    LaunchedEffect(Unit) {
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
