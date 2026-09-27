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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NavController
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.components.TopBanner
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
import com.github.nacabaro.vbhelper.world.worldRadarBattleParticipant
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Radius, in meters, within which the player can tap a Digimon to chat. */
private const val INTERACTION_RANGE_METERS = 40.0
private const val GRID_SIZE_METERS = 60.0
private const val RADAR_RING_INTERVAL_METERS = 200

@Composable
fun RadarScreen(
    navController: NavController,
    selectedWorldTab: Int = 0,
    onWorldTabSelected: (Int) -> Unit = {}
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val app = context.applicationContext as VBHelper
    val viewModelOwner = checkNotNull(LocalViewModelStoreOwner.current)
    val battleViewModel = remember(viewModelOwner) { offlineBattleViewModel(viewModelOwner) }
    val battleSessionState by battleViewModel.state.collectAsState()
    var location by remember { mutableStateOf<Location?>(null) }
    var origin by remember { mutableStateOf<Location?>(null) }
    var selectedEncounter by remember { mutableStateOf<WorldDtos.SpawnWithDetails?>(null) }
    var preparingSpawnId by remember { mutableStateOf<Long?>(null) }
    var debugSpawnInProgress by remember { mutableStateOf(false) }
    var battleSpawnId by rememberSaveable { mutableStateOf<Long?>(null) }
    var battleCharacterId by rememberSaveable { mutableStateOf<Long?>(null) }
    var battleSessionId by rememberSaveable { mutableStateOf<String?>(null) }
    var committedBattleSessionId by rememberSaveable { mutableStateOf<String?>(null) }
    val battleActive = battleSessionId != null
    val compass = rememberWorldCompass(location, enabled = !battleActive)
    val heading = compass.heading ?: 0f // Uncalibrated map is explicitly north-up.
    var frozenRadarHeading by remember { mutableFloatStateOf(0f) }
    SideEffect {
        if (!battleActive) frozenRadarHeading = heading
    }
    val radarHeading = if (battleActive) frozenRadarHeading else heading
    val isDebuggableBuild = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    var showCompassDiagnostics by remember { mutableStateOf(false) }
    var status by remember {
        mutableStateOf(resources.getString(R.string.ui_world_grant_location))
    }
    val spawns by app.container.worldRepository.observeSpawns().collectAsState(initial = emptyList())
    val pendingRecruits by app.container.worldRepository.observePendingRecruits()
        .collectAsState(initial = emptyList())
    val worldSpawnCards by app.container.db.cardDao().observeCardsForWorldSpawns()
        .collectAsState(initial = emptyList())
    val currentBiome by app.container.worldRepository.currentBiome.collectAsState()
    val scope = rememberCoroutineScope()
    // At the default zoom, the entire nearby spawn radius (350 m) is visible.
    // Users can still zoom in for detail or out to the full 1 km radar range.
    var zoom by remember { mutableFloatStateOf(1f) }
    var idleFrame by remember { mutableIntStateOf(0) }
    var selectedSpecies by remember { mutableStateOf<CharacterDtos.CardCharaProgress?>(null) }
    var showWorldSpawnSettings by remember { mutableStateOf(false) }
    var selectedWorldDim by remember { mutableStateOf<Card?>(null) }
    var selectedWorldDimSpecies by remember { mutableStateOf(emptyList<WorldDimSpecies>()) }
    val radarPulse = remember { Animatable(0f) }

    LaunchedEffect(battleActive) {
        while (!battleActive) {
            kotlinx.coroutines.delay(700L)
            idleFrame = 1 - idleFrame
        }
    }

    LaunchedEffect(battleActive) {
        while (!battleActive) {
            radarPulse.snapTo(0f)
            radarPulse.animateTo(1f, animationSpec = tween(durationMillis = 2_600))
            kotlinx.coroutines.delay(1_400L)
        }
    }

    val spawnBitmaps = remember(spawns, idleFrame) {
        spawns.mapNotNull { spawn ->
            runCatching {
                val frame = spawn.frameFor(
                    motion = PlayerMotion.IDLE,
                    frame = idleFrame
                )
                spawn to BitmapData(
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

        if (hasLocationPermission) {
            status = resources.getString(R.string.ui_world_location_active)
        } else {
            status = resources.getString(R.string.ui_world_location_required)
        }
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

    // Mantém uma assinatura contínua do GPS em vez de consultar apenas a localização em cache.
    DisposableEffect(hasLocationPermission, battleActive) {
        if (!hasLocationPermission || battleActive) {
            onDispose { }
        } else {
            val client = LocationServices.getFusedLocationProviderClient(context)
            var lastSpawnRefresh: Location? = null

            fun requestSpawnRefresh(target: Location) {
                val shouldRefresh = lastSpawnRefresh == null ||
                    lastSpawnRefresh!!.distanceTo(target) >= 60f
                if (!shouldRefresh) return

                lastSpawnRefresh = Location(target)
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            app.container.worldRepository.ensureSpawns(
                                target.latitude,
                                target.longitude
                            )
                        }
                    }.onFailure {
                        status = resources.getString(R.string.ui_world_load_nearby_failed)
                    }
                }
            }

            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let { newLocation ->
                        if (origin == null) {
                            origin = Location(newLocation).apply {
                                latitude = newLocation.latitude
                                longitude = newLocation.longitude
                            }
                        }

                        location = newLocation
                        status = resources.getString(R.string.ui_world_radar_active)

                        requestSpawnRefresh(newLocation)
                    }
                }
            }

            val request = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                2_000L
            )
                .setMinUpdateIntervalMillis(1_000L)
                .setMinUpdateDistanceMeters(2f)
                .setWaitForAccurateLocation(false)
                .build()

            client.lastLocation.addOnSuccessListener { cached ->
                cached?.let { cachedLocation ->
                    if (origin == null) {
                        origin = Location(cachedLocation).apply {
                            latitude = cachedLocation.latitude
                            longitude = cachedLocation.longitude
                        }
                    }
                    location = cachedLocation
                    // Cached GPS is available before the first live callback on most
                    // devices, so use it to populate the map immediately.
                    requestSpawnRefresh(cachedLocation)
                }
            }

            client.requestLocationUpdates(request, callback, context.mainLooper)
                .addOnFailureListener {
                    status = resources.getString(R.string.ui_world_tracking_failed)
                }

            onDispose {
                client.removeLocationUpdates(callback)
            }
        }
    }

    fun leaveRadarBattle() {
        battleViewModel.finishSession()
        battleSpawnId = null
        battleCharacterId = null
        battleSessionId = null
        committedBattleSessionId = null
    }

    fun startRadarBattle(spawn: WorldDtos.SpawnWithDetails) {
        if (preparingSpawnId != null || battleActive) return
        preparingSpawnId = spawn.id
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val characterDao = app.container.db.userCharacterDao()
                    val activeCharacter = characterDao.getActiveCharacter().first()
                        ?: error(resources.getString(R.string.ui_world_battle_choose_active))
                    val profile = characterDao.getAllBattleParticipantProfiles()
                        .firstOrNull { it.sourceCharacterId == activeCharacter.id }
                        ?: error(resources.getString(R.string.ui_world_battle_stats_unavailable))
                    app.container.worldRepository.markInteracted(spawn.id)
                    PreparedRadarBattle(
                        activeCharacterId = activeCharacter.id,
                        ally = offlineBattleParticipant(activeCharacter, profile),
                        opponent = worldRadarBattleParticipant(spawn)
                    )
                }
            }.onSuccess { prepared ->
                val sessionId = "world-radar:${spawn.id}:${System.nanoTime()}"
                selectedEncounter = null
                battleSpawnId = spawn.id
                battleCharacterId = prepared.activeCharacterId
                battleSessionId = sessionId
                committedBattleSessionId = null
                battleViewModel.start(
                    context = context,
                    sessionId = sessionId,
                    allies = listOf(prepared.ally),
                    opponents = listOf(prepared.opponent),
                    arenaManifestPath = OfflineArenaManifest.RADAR_MANIFEST_PATH
                )
            }.onFailure { failure ->
                Toast.makeText(
                    context,
                    failure.message ?: resources.getString(R.string.ui_world_battle_start_failed),
                    Toast.LENGTH_LONG
                ).show()
            }
            preparingSpawnId = null
        }
    }

    LaunchedEffect(battleSessionId, battleSessionState.sessionId) {
        // A process recreation cannot restore the running simulator. Return to
        // the live Radar instead of leaving a dead 3D viewport on screen.
        if (battleSessionId != null && battleSessionState.sessionId == null) {
            leaveRadarBattle()
        }
    }


    Scaffold(
        topBar = {
            Column {
                TopBanner(text = when {
                    battleActive -> "${stringResource(R.string.nav_world)} • ${stringResource(R.string.ui_world_battle_banner)}"
                    compass.heading != null -> "${stringResource(R.string.nav_world)} • ${cardinalDirection(heading)}"
                    else -> stringResource(R.string.nav_world)
                }, onGearClick = {
                    if (!battleActive) showWorldSpawnSettings = true
                })
                WorldSectionTabs(
                    selectedTab = selectedWorldTab,
                    onTabSelected = { tab -> if (!battleActive) onWorldTabSelected(tab) }
                )
            }
        },
        contentWindowInsets = WindowInsets.statusBars
    ) { contentPadding ->
        Column(
            Modifier
                .padding(contentPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            WorldRadarBattleContent(
                viewModel = battleViewModel,
                battleActive = battleActive,
                onOutcome = { outcome ->
                    val activeSessionId = battleSessionId
                    val spawnId = battleSpawnId
                    val characterId = battleCharacterId
                    if (activeSessionId != null && committedBattleSessionId != activeSessionId &&
                        spawnId != null && characterId != null
                    ) {
                        committedBattleSessionId = activeSessionId
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    app.container.worldRepository.recordRadarBattleResult(
                                        activeCharacterId = characterId,
                                        spawnId = spawnId,
                                        outcome = outcome
                                    )
                                }
                            }.onFailure { failure ->
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
                val playerDisplacement = origin?.let { originLocation ->
                    location?.let { currentLocation ->
                        val northMeters = (currentLocation.latitude - originLocation.latitude) * 111_320.0
                        val eastMeters = (currentLocation.longitude - originLocation.longitude) *
                            111_320.0 * cos(Math.toRadians(originLocation.latitude))
                        Offset(
                            (eastMeters * scale).toFloat(),
                            (-northMeters * scale).toFloat()
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

                // A posição dos Digimon usa a mesma origem fixa do jogador.
                origin?.let { originLocation ->
                    spawnBitmaps.forEach { (spawn, image) ->
                        val northMeters = (spawn.latitude - originLocation.latitude) * 111_320.0
                        val eastMeters = (spawn.longitude - originLocation.longitude) *
                            111_320.0 * cos(Math.toRadians(originLocation.latitude))
                        val distanceFromCenter = sqrt(northMeters * northMeters + eastMeters * eastMeters)

                        if (distanceFromCenter <= visibleRadiusMeters * 1.25) {
                            // Posição geográfica do Digimon em relação ao jogador.
                            // O mesmo vetor é rotacionado junto com a grade.
                            val worldX = (eastMeters * scale).toFloat() - playerDisplacement.x
                            val worldY = (-northMeters * scale).toFloat() - playerDisplacement.y
                            val angle = Math.toRadians((-radarHeading).toDouble())
                            val cosAngle = cos(angle).toFloat()
                            val sinAngle = sin(angle).toFloat()

                            val rotatedX = worldX * cosAngle - worldY * sinAngle
                            val rotatedY = worldX * sinAngle + worldY * cosAngle

                            val px = center.x + rotatedX
                            val py = center.y + rotatedY
                            val playerLocation = location
                            val distanceToPlayer = if (playerLocation != null) {
                                val northToPlayer = (spawn.latitude - playerLocation.latitude) * 111_320.0
                                val eastToPlayer = (spawn.longitude - playerLocation.longitude) *
                                    111_320.0 * cos(Math.toRadians(playerLocation.latitude))
                                sqrt(northToPlayer * northToPlayer + eastToPlayer * eastToPlayer)
                            } else Double.MAX_VALUE
                            val withinRange = distanceToPlayer <= INTERACTION_RANGE_METERS
                            val markerSizeDp = 48.dp

                            Image(
                                bitmap = image,
                                contentDescription = spawn.speciesName ?: "Digimon",
                                filterQuality = FilterQuality.None,
                                modifier = Modifier
                                    .offset {
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
                                                if (withinRange) {
                                                    selectedEncounter = spawn
                                                } else {
                                                    Toast.makeText(
                                                        context,
                                                        resources.getString(R.string.ui_world_too_far),
                                                        Toast.LENGTH_SHORT
                                                    ).show()
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

                },
                radarControls = {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.ui_world_nearby_count, spawns.size),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentWidth()
            )

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
                        val playerLocation = location ?: return@VitalButton
                        if (debugSpawnInProgress) return@VitalButton
                        debugSpawnInProgress = true
                        scope.launch {
                            try {
                                val spawnId = withContext(Dispatchers.IO) {
                                    app.container.worldRepository.spawnDebugDigimon(
                                        latitude = playerLocation.latitude,
                                        longitude = playerLocation.longitude
                                    )
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
                                Toast.makeText(
                                    context,
                                    failure.message ?: resources.getString(R.string.ui_world_debug_spawn_unavailable),
                                    Toast.LENGTH_LONG
                                ).show()
                            } finally {
                                debugSpawnInProgress = false
                            }
                        }
                    },
                    enabled = location != null && !debugSpawnInProgress,
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
                modifier = Modifier.fillMaxWidth()
            )
    }

    if (!battleActive) {
        selectedEncounter?.let { spawn ->
            WorldEncounterActionSheet(
                spawn = spawn,
                preparingBattle = preparingSpawnId == spawn.id,
                onDismiss = { if (preparingSpawnId == null) selectedEncounter = null },
                onChat = {
                    selectedEncounter = null
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            // Protect the encounter from cap eviction before navigation.
                            app.container.worldRepository.markInteracted(spawn.id)
                        }
                        navController.navigate(
                            NavigationItems.WorldChat.route.replace("{spawnId}", spawn.id.toString())
                        )
                    }
                },
                onBattle = { startRadarBattle(spawn) }
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
            onDismiss = { showWorldSpawnSettings = false },
            onEnabledChange = { card, enabled ->
                scope.launch(Dispatchers.IO) {
                    app.container.db.cardDao().setWorldSpawnsEnabled(card.id, enabled)
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
    val opponent: com.github.nacabaro.vbhelper.screens.OfflineBattleParticipant
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorldEncounterActionSheet(
    spawn: WorldDtos.SpawnWithDetails,
    preparingBattle: Boolean,
    onDismiss: () -> Unit,
    onChat: () -> Unit,
    onBattle: () -> Unit
) {
    val wildLabel = stringResource(R.string.ui_world_encounter_wild)
    val sprite = remember(spawn.id, spawn.spriteIdle.contentHashCode()) {
        runCatching {
            BitmapData(
                spawn.frameFor(PlayerMotion.IDLE, 0),
                spawn.spriteWidth.coerceAtLeast(1),
                spawn.spriteHeight.coerceAtLeast(1)
            ).getBitmap().asImageBitmap()
        }.getOrNull()
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
                    contentDescription = spawn.speciesName ?: wildLabel,
                    filterQuality = FilterQuality.None,
                    modifier = Modifier.size(76.dp)
                )
            }
            Text(
                spawn.speciesName ?: wildLabel,
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                stringResource(R.string.ui_world_encounter_prompt),
                color = TextSecondaryOnDark,
                style = MaterialTheme.typography.bodyMedium
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onChat,
                    enabled = !preparingBattle,
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp)
                ) {
                    Text(stringResource(R.string.ui_world_encounter_chat))
                }
                Button(
                    onClick = onBattle,
                    enabled = !preparingBattle,
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
                            onClick = { onEnabledChange(card, !card.worldSpawnsEnabled) },
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
