package com.github.nacabaro.vbhelper.screens.digifarmScreen

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.components.TopBanner
import com.github.nacabaro.vbhelper.components.VitalButton
import com.github.nacabaro.vbhelper.di.VBHelper
import com.github.nacabaro.vbhelper.digifarm.social.FarmConversationOrchestrator
import com.github.nacabaro.vbhelper.digifarm.map.Digifarm3dManifest
import com.github.nacabaro.vbhelper.digifarm.map.Digifarm3dMap
import com.github.nacabaro.vbhelper.digifarm.map.MapPoint
import com.github.nacabaro.vbhelper.domain.digifarm.Farm
import com.github.nacabaro.vbhelper.dtos.FarmResidentWithDetails
import com.github.nacabaro.vbhelper.navigation.NavigationItems
import com.github.nacabaro.vbhelper.screens.storageScreen.StorageCharacterPickerDialog
import com.github.nacabaro.vbhelper.source.StorageRepository
import com.github.nacabaro.vbhelper.components.cyberFrame
import com.github.nacabaro.vbhelper.screens.worldScreen.WorldSectionTabs
import com.github.nacabaro.vbhelper.ui.theme.SpaceBlack
import com.github.nacabaro.vbhelper.ui.theme.SurfaceDeepPurple
import com.github.nacabaro.vbhelper.ui.theme.SurfaceElevatedPurple
import com.github.nacabaro.vbhelper.ui.theme.SurfaceStroke
import com.github.nacabaro.vbhelper.ui.theme.TextMutedOnDark
import com.github.nacabaro.vbhelper.ui.theme.TextPrimaryOnDark
import com.github.nacabaro.vbhelper.ui.theme.TextSecondaryOnDark
import com.github.nacabaro.vbhelper.ui.theme.VitalCyan
import com.github.nacabaro.vbhelper.utils.BitmapData
import com.github.nacabaro.vbhelper.utils.createARGBIntArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun DigifarmScreen(
    navController: NavController,
    selectedWorldTab: Int = 1,
    onWorldTabSelected: (Int) -> Unit = {}
) {
    val context = LocalContext.current
    val app = context.applicationContext as VBHelper
    val repository = app.container.digifarmRepository
    val scope = rememberCoroutineScope()
    val farms by repository.observeFarms().collectAsState(emptyList())
    var selectedFarmId by remember { mutableStateOf<String?>(null) }
    val selectedFarm = farms.firstOrNull { it.id == selectedFarmId } ?: farms.firstOrNull()
    LaunchedEffect(farms, selectedFarmId) {
        if (selectedFarmId == null && farms.isNotEmpty()) selectedFarmId = farms.first().id
    }

    if (selectedFarm == null) {
        EmptyFarmState(onCreate = { name ->
            scope.launch(Dispatchers.IO) {
                val farm = repository.createFarm(name)
                withContext(Dispatchers.Main) { selectedFarmId = farm.id }
            }
        }, selectedWorldTab, onWorldTabSelected)
        return
    }

    FarmWorld(
        navController = navController,
        farm = selectedFarm,
        farms = farms,
        onSelectFarm = { selectedFarmId = it },
        onCreateFarm = { name ->
            scope.launch(Dispatchers.IO) {
                val farm = repository.createFarm(name)
                withContext(Dispatchers.Main) { selectedFarmId = farm.id }
            }
        },
        selectedWorldTab = selectedWorldTab,
        onWorldTabSelected = onWorldTabSelected
    )
}

@Composable
private fun EmptyFarmState(
    onCreate: (String) -> Unit,
    selectedWorldTab: Int,
    onWorldTabSelected: (Int) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            Column {
                TopBanner(text = stringResource(R.string.ui_digifarm_title))
                WorldSectionTabs(selectedWorldTab, onWorldTabSelected)
            }
        },
        contentWindowInsets = WindowInsets.statusBars
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(SpaceBlack)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp)
                    .background(SurfaceElevatedPurple.copy(alpha = 0.58f))
                    .cyberFrame()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    stringResource(R.string.ui_digifarm_empty_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimaryOnDark
                )
                Text(
                    stringResource(R.string.ui_digifarm_empty_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondaryOnDark
                )
                VitalButton(
                    onClick = { showDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.ui_digifarm_create))
                }
            }
        }
    }
    if (showDialog) FarmNameDialog(onDismiss = { showDialog = false }) {
        showDialog = false
        onCreate(it)
    }
}

@Composable
private fun FarmWorld(
    navController: NavController,
    farm: Farm,
    farms: List<Farm>,
    onSelectFarm: (String) -> Unit,
    onCreateFarm: (String) -> Unit,
    selectedWorldTab: Int,
    onWorldTabSelected: (Int) -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as VBHelper
    val repository = app.container.digifarmRepository
    val storageRepository = remember { StorageRepository(app.container.db) }
    val residents by repository.observeResidents(farm.id).collectAsState(emptyList())
    val messages by repository.observeMessages(farm.id).collectAsState(emptyList())
    val storageCharacters by storageRepository.getAllCharacters().collectAsState(emptyList())
    val residentCharacterIds by repository.observeResidentCharacterIds().collectAsState(emptyList())
    val orchestrator = remember { FarmConversationOrchestrator(repository, app.container.chatRepository) }
    val coordinator = app.container.farmSessionCoordinator
    val scope = rememberCoroutineScope()
    var selectedResident by remember { mutableStateOf<FarmResidentWithDetails?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var isAddingResident by remember { mutableStateOf(false) }
    var showCreate by remember { mutableStateOf(false) }
    var showTransfer by remember { mutableStateOf<FarmResidentWithDetails?>(null) }
    var showResidentList by remember { mutableStateOf(false) }
    var followId by remember(farm.id) { mutableStateOf<String?>(null) }
    var farmMenu by remember { mutableStateOf(false) }
    var scale by remember(farm.id) { mutableFloatStateOf(farm.cameraScale) }
    var panX by remember(farm.id) { mutableFloatStateOf(farm.cameraX) }
    var panY by remember(farm.id) { mutableFloatStateOf(farm.cameraY) }
    var frame by remember { mutableIntStateOf(0) }
    var sceneView by remember { mutableStateOf<Digifarm3dSceneView?>(null) }
    var sceneIssue by remember(farm.id) { mutableStateOf<String?>(null) }
    // Tron wireframe toggle from general settings; key() below rebuilds the
    // GL view with the matching scene when it changes.
    val tronWireframe by app.container.speciesSettingsRepository.digifarmTronWireframe
        .collectAsState(initial = false)
    var dialogueIssue by remember { mutableStateOf<String?>(null) }
    var offlineSummary by remember(farm.id) { mutableStateOf<String?>(null) }

    // Single detailed session per app (§13): map and group share the coordinator.
    LaunchedEffect(farm.id) {
        withContext(Dispatchers.IO) { coordinator.acquire(farm.id) }
        offlineSummary = withContext(Dispatchers.IO) { repository.summarizeReturn(farm.id) }
        try {
            while (true) {
                delay(900L)
                frame = 1 - frame
            }
        } finally {
            withContext(Dispatchers.IO) { coordinator.release(farm.id) }
        }
    }
    LaunchedEffect(farm.id, residents.size, farm.autonomousDialogueEnabled) {
        if (residents.size < 2 || !farm.autonomousDialogueEnabled) return@LaunchedEffect
        while (true) {
            delay(20_000L)
            val result = runCatching { withContext(Dispatchers.IO) { orchestrator.maybeGenerateAutonomous(farm.id) } }
            dialogueIssue = result.exceptionOrNull()?.message
            if (result.isFailure) break
        }
    }
    LaunchedEffect(farm.id, scale, panX, panY) {
        delay(500L)
        withContext(Dispatchers.IO) { repository.saveCamera(farm.id, scale, panX, panY) }
    }
    // Reference-like default: a fresh farm opens zoomed in on a resident; the
    // follow effect centers the camera. Saved camera values are respected on revisit.
    LaunchedEffect(farm.id, residents.size) {
        if (residents.isNotEmpty() && followId == null &&
            scale == MIN_FARM_SCALE && panX == 0f && panY == 0f &&
            defaultedFarmViews.add(farm.id)
        ) {
            scale = DEFAULT_FARM_SCALE
            followId = residents.first().individualId
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopBanner(
                    text = "${farm.name}  •  ${residents.size}/${farm.capacity}",
                    onGearClick = { farmMenu = true }
                )
                WorldSectionTabs(selectedWorldTab, onWorldTabSelected)
            }
        },
        contentWindowInsets = WindowInsets.statusBars
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    VitalButton(
                        onClick = { showAdd = true },
                        enabled = residents.size < farm.capacity,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            stringResource(R.string.ui_digifarm_add_resident),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    VitalButton(
                        onClick = {
                            navController.navigate(NavigationItems.FarmGroup.route.replace("{farmId}", farm.id))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            stringResource(R.string.ui_digifarm_group),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                DropdownMenu(expanded = farmMenu, onDismissRequest = { farmMenu = false }) {
                    farms.forEach { item ->
                        DropdownMenuItem(text = { Text(item.name) }, onClick = {
                            farmMenu = false
                            onSelectFarm(item.id)
                        })
                    }
                    DropdownMenuItem(text = { Text(stringResource(R.string.ui_digifarm_create_another)) }, onClick = {
                        farmMenu = false
                        showCreate = true
                    })
                    DropdownMenuItem(
                        text = {
                            Text(stringResource(if (farm.autonomousDialogueEnabled) R.string.ui_digifarm_pause_dialogue else R.string.ui_digifarm_enable_dialogue))
                        },
                        onClick = {
                            farmMenu = false
                            scope.launch(Dispatchers.IO) {
                                repository.setAutonomousDialogue(farm.id, !farm.autonomousDialogueEnabled)
                            }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.ui_digifarm_residents)) },
                        onClick = {
                            farmMenu = false
                            showResidentList = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.ui_digifarm_archive)) },
                        onClick = {
                            farmMenu = false
                            scope.launch(Dispatchers.IO) {
                                repository.archiveFarm(farm.id)
                                withContext(Dispatchers.Main) {
                                    onSelectFarm(farms.firstOrNull { it.id != farm.id }?.id ?: "")
                                }
                            }
                        }
                    )
                }
            }

            if (dialogueIssue != null || offlineSummary != null) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(SurfaceDeepPurple.copy(alpha = 0.72f))
                        .cyberFrame()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    dialogueIssue?.let {
                        Text(
                            stringResource(R.string.ui_digifarm_dialogue_unavailable),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    offlineSummary?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMutedOnDark,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Canonical island positions shared by the Filament billboards,
            // the invisible tap targets and the speech bubbles.
            val worldPositions = remember(residents) {
                residents.associate { resident ->
                    resident.individualId to Digifarm3dMap.legacyToWorld(
                        MapPoint(resident.positionX, resident.positionY),
                        Digifarm3dManifest(playableBounds = Digifarm3dMap.defaultPlayableBounds)
                    )
                }
            }
            // Bumped while the 3D camera moves so bubbles/tap targets follow.
            var projectionTick by remember(farm.id) { mutableIntStateOf(0) }
            // Following centers the orbit target on the resident; the view
            // eases toward it (and tracks it while it walks).
            LaunchedEffect(followId, sceneView) {
                sceneView?.followId = followId
            }
            LaunchedEffect(residents) {
                if (followId != null && residents.none { it.individualId == followId }) {
                    followId = null
                }
            }
            // Full sprite sets go to Filament only when the roster or the
            // sprite art changes. Animation ticks only swap already-uploaded
            // textures (no GL create/destroy churn while the farm is open).
            val rosterKey = remember(residents) {
                residents.associate { it.individualId to residentSpriteSetKey(it) }
            }
            LaunchedEffect(rosterKey, sceneView) {
                val view = sceneView ?: return@LaunchedEffect
                val sets = withContext(Dispatchers.Default) {
                    residents.map { resident ->
                        ResidentFrames(
                            id = resident.individualId,
                            poses = mapOf(
                                "sleep" to residentFrameImage(resident.spriteSleep, resident),
                                "train" to residentFrameImage(resident.spriteTrain, resident),
                                "train2" to residentFrameImage(resident.spriteTrain2, resident),
                                "happy" to residentFrameImage(resident.spriteHappy, resident),
                                "walk" to residentFrameImage(resident.spriteWalk, resident),
                                "walk2" to residentFrameImage(resident.spriteWalk2, resident)
                            ),
                            setKey = rosterKey[resident.individualId] ?: resident.individualId,
                            facingLeft = resident.facingLeft
                        )
                    }
                }
                view.setResidentFrames(sets)
            }
            // Cheap per-tick update: pose swap + position/facing/selection.
            // Compose draws no Digimon copy: the 3D scene owns sprites.
            LaunchedEffect(residents, frame, selectedResident?.individualId, sceneView) {
                val view = sceneView ?: return@LaunchedEffect
                view.updateResidentPoses(residents.map { resident ->
                    val pose = when (resident.activity) {
                        "REST" -> "sleep"
                        "TRAIN" -> if (frame == 0) "train" else "train2"
                        "PLAY" -> "happy"
                        else -> if (frame == 0) "walk" else "walk2"
                    }
                    val world = worldPositions[resident.individualId]
                    ResidentPose(
                        id = resident.individualId,
                        pose = pose,
                        worldX = world?.x ?: 0f,
                        worldZ = world?.z ?: 0f,
                        selected = selectedResident?.individualId == resident.individualId
                    )
                })
            }
            // The viewport is a real GLB scene. Compose only layers UI above
            // it: speech bubbles and invisible tap targets positioned with
            // the same camera projection as the 3D billboards.
            val mapDescription = stringResource(R.string.ui_digifarm_map_description)
            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .border(2.dp, SurfaceStroke, MaterialTheme.shapes.medium)
                    .clip(MaterialTheme.shapes.medium)
                    .background(SpaceBlack)
                    .semantics {
                        contentDescription = mapDescription
                    }
            ) {
                val density = LocalDensity.current
                val now = System.currentTimeMillis()
                val visibleBubbles = remember(messages, selectedResident) {
                    val recent = messages.filter { it.authorIndividualId != null && now - it.timestamp <= SPEECH_BUBBLE_MILLIS }
                    val selected = selectedResident?.individualId?.let { id -> recent.filter { it.authorIndividualId == id } } ?: emptyList()
                    val others = recent.filter { it !in selected }.sortedByDescending { it.timestamp }
                    (selected.sortedByDescending { it.timestamp } + others).distinctBy { it.authorIndividualId }.take(3)
                }
                Box(Modifier.fillMaxSize()) {
                    // The scene hot-swaps inside the live GL view (see the
                    // update block): toggling never tears the Engine down.
                    Digifarm3dViewport(
                        modifier = Modifier.fillMaxSize(),
                        assetName = if (tronWireframe) {
                            Digifarm3dMap.tronRuntimeAsset
                        } else {
                            Digifarm3dMap.runtimeAsset
                        },
                        onReady = {
                            sceneView = it
                            // A recreated AndroidView starts a fresh load; do
                            // not keep an error banner from the previous view.
                            sceneIssue = null
                            it.onCameraChange = { projectionTick++ }
                        },
                        onAssetError = { sceneIssue = it }
                    )
                    sceneIssue?.let {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = .92f),
                            modifier = Modifier.align(Alignment.TopCenter).padding(8.dp)
                        ) {
                            Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                                Text(
                                    stringResource(R.string.ui_digifarm_3d_unavailable),
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Text(
                                    it,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    // Subscribe to camera motion so the overlay below follows.
                    @Suppress("UNUSED_EXPRESSION")
                    projectionTick
                    // Invisible tap targets over the 3D billboards, projected
                    // with the same camera. The sprite itself lives in
                    // Filament (depth-tested, feet-anchored, with shadow).
                    residents.forEach { resident ->
                        val world = worldPositions[resident.individualId] ?: return@forEach
                        val activityLabel = farmActivityLabel(resident.activity)
                        val view = sceneView ?: return@forEach
                        val foot = view.projectWorld(world.x, 0f, world.z) ?: return@forEach
                        val head = view.projectWorld(world.x, TAP_HEAD_WORLD_Y, world.z) ?: return@forEach
                        val pixelH = (foot.second - head.second).coerceAtLeast(24f)
                        val aspect = if (resident.spriteHeight > 0) {
                            (resident.spriteWidth.toFloat() / resident.spriteHeight.toFloat()).coerceIn(0.4f, 2.5f)
                        } else 1f
                        val pixelW = (pixelH * aspect).coerceAtLeast(24f)
                        val tapW = with(density) { pixelW.toDp() }
                        val tapH = with(density) { pixelH.toDp() }
                        Box(
                            Modifier.offset {
                                IntOffset(
                                    (foot.first - pixelW / 2f).roundToInt(),
                                    head.second.roundToInt()
                                )
                            }.size(tapW, tapH)
                                .clickable(onClickLabel = "${resident.displayName}: $activityLabel") {
                                    selectedResident = resident
                                }
                        )
                    }
                    visibleBubbles.forEach { message ->
                        val authorId = message.authorIndividualId ?: return@forEach
                        val world = worldPositions[authorId] ?: return@forEach
                        val anchor = sceneView?.projectWorld(world.x, BUBBLE_WORLD_Y, world.z) ?: return@forEach
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = .94f),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.offset {
                                IntOffset(
                                    (anchor.first - 55 * density.density).roundToInt(),
                                    (anchor.second - BUBBLE_ABOVE_DP * density.density).roundToInt()
                                )
                            }.width(110.dp)
                        ) {
                            Text(message.body, style = MaterialTheme.typography.labelSmall, maxLines = 3, modifier = Modifier.padding(6.dp))
                        }
                    }
                }
            }
            if (followId != null) {
                VitalButton(
                    onClick = { followId = null },
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = VitalCyan,
                    contentColor = VitalCyan
                ) {
                    Text(
                        stringResource(R.string.ui_digifarm_stop_follow),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (residents.isNotEmpty()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(SurfaceElevatedPurple.copy(alpha = 0.58f))
                        .cyberFrame()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        stringResource(R.string.ui_digifarm_residents),
                        style = MaterialTheme.typography.labelLarge,
                        color = TextSecondaryOnDark
                    )
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(residents, key = { it.individualId }) { resident ->
                            val following = followId == resident.individualId
                            VitalButton(
                                onClick = {
                                    selectedResident = resident
                                    followId = resident.individualId
                                },
                                modifier = Modifier.height(40.dp),
                                borderColor = if (following) VitalCyan else SurfaceStroke,
                                contentColor = if (following) VitalCyan else TextPrimaryOnDark,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                            ) {
                                Text(
                                    resident.displayName,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        StorageCharacterPickerDialog(
            characters = storageCharacters.filterNot { it.id in residentCharacterIds },
            title = stringResource(R.string.ui_digifarm_choose_resident),
            emptyMessage = stringResource(R.string.ui_digifarm_no_available),
            isLoading = isAddingResident,
            onDismiss = { showAdd = false },
            onCharacterSelected = { characterId ->
                if (!isAddingResident) {
                    isAddingResident = true
                    scope.launch(Dispatchers.IO) {
                        val result = runCatching { repository.addResident(farm.id, characterId) }
                        withContext(Dispatchers.Main) {
                            result.exceptionOrNull()?.let {
                                Toast.makeText(context, it.message, Toast.LENGTH_LONG).show()
                            }
                            if (result.isSuccess) showAdd = false
                            isAddingResident = false
                        }
                    }
                }
            }
        )
    }
    if (showCreate) FarmNameDialog(onDismiss = { showCreate = false }) {
        showCreate = false
        onCreateFarm(it)
    }
    selectedResident?.let { resident ->
        ResidentDialog(
            resident = resident,
            onDismiss = { selectedResident = null },
            onPrivateChat = {
                selectedResident = null
                navController.navigate(NavigationItems.Chat.route.replace("{characterId}", resident.characterId.toString()))
            },
            onGroup = {
                selectedResident = null
                navController.navigate(NavigationItems.FarmGroup.route.replace("{farmId}", farm.id))
            },
            onFollow = {
                followId = resident.individualId
                selectedResident = null
            },
            onTransfer = { showTransfer = resident; selectedResident = null },
            onActivity = { activity ->
                selectedResident = null
                scope.launch(Dispatchers.IO) { repository.setActivity(resident.individualId, farm.id, activity) }
            },
            onRemove = {
                selectedResident = null
                followId = null
                scope.launch(Dispatchers.IO) { repository.removeResident(resident.individualId) }
            }
        )
    }
    showTransfer?.let { resident ->
        TransferResidentDialog(
            farms = farms.filter { it.id != farm.id },
            onDismiss = { showTransfer = null },
            onTransfer = { targetId ->
                scope.launch(Dispatchers.IO) {
                    val result = runCatching { repository.transferResident(resident.individualId, targetId) }
                    withContext(Dispatchers.Main) {
                        result.exceptionOrNull()?.let { Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() }
                        showTransfer = null
                    }
                }
            }
        )
    }
    if (showResidentList) {
        AlertDialog(
            onDismissRequest = { showResidentList = false },
            title = { Text(stringResource(R.string.ui_digifarm_residents)) },
            text = {
                if (residents.isEmpty()) Text(stringResource(R.string.ui_digifarm_empty_body))
                else LazyColumn(Modifier.height(360.dp)) {
                    items(residents, key = { it.individualId }) { resident ->
                        TextButton(
                            onClick = { selectedResident = resident; showResidentList = false },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("${resident.displayName} • ${farmActivityLabel(resident.activity)}") }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showResidentList = false }) { Text(stringResource(R.string.ui_close)) } }
        )
    }
}

@Composable
private fun FarmNameDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ui_digifarm_name_title)) },
        text = { OutlinedTextField(value = name, onValueChange = { name = it.take(40) }, singleLine = true) },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_cancel)) } },
        confirmButton = { VitalButton(onClick = { onCreate(name) }) { Text(stringResource(R.string.ui_digifarm_create)) } }
    )
}

@Composable
private fun ResidentDialog(
    resident: FarmResidentWithDetails,
    onDismiss: () -> Unit,
    onPrivateChat: () -> Unit,
    onGroup: () -> Unit,
    onFollow: () -> Unit,
    onTransfer: () -> Unit,
    onActivity: (String) -> Unit,
    onRemove: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(resident.displayName) },
        text = {
            Column {
                Text(stringResource(R.string.ui_digifarm_activity, farmActivityLabel(resident.activity)))
                Text(stringResource(R.string.ui_digifarm_needs, resident.energy, resident.satiety, resident.social, resident.funLevel))
                TextButton(onClick = onPrivateChat) { Text(stringResource(R.string.ui_digifarm_private_chat)) }
                TextButton(onClick = onGroup) { Text(stringResource(R.string.ui_digifarm_group)) }
                TextButton(onClick = onFollow) { Text(stringResource(R.string.ui_digifarm_follow)) }
                TextButton(onClick = onTransfer) { Text(stringResource(R.string.ui_digifarm_transfer)) }
                Row {
                    TextButton(onClick = { onActivity("PLAY") }) { Text(stringResource(R.string.ui_digifarm_play)) }
                    TextButton(onClick = { onActivity("TRAIN") }) { Text(stringResource(R.string.ui_digifarm_train)) }
                }
                Row {
                    TextButton(onClick = { onActivity("EAT") }) { Text(stringResource(R.string.ui_digifarm_feed)) }
                    TextButton(onClick = { onActivity("REST") }) { Text(stringResource(R.string.ui_digifarm_rest)) }
                }
                TextButton(onClick = onRemove) { Text(stringResource(R.string.ui_digifarm_remove)) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_close)) } }
    )
}

@Composable
private fun TransferResidentDialog(
    farms: List<Farm>,
    onDismiss: () -> Unit,
    onTransfer: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ui_digifarm_transfer)) },
        text = {
            if (farms.isEmpty()) Text(stringResource(R.string.ui_digifarm_no_other_farm))
            else LazyColumn(Modifier.height(300.dp)) {
                items(farms, key = { it.id }) { target ->
                    TextButton(onClick = { onTransfer(target.id) }, modifier = Modifier.fillMaxWidth()) {
                        Text(target.name)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_close)) } }
    )
}

@Composable
private fun farmActivityLabel(activity: String): String = stringResource(
    when (activity) {
        "EXPLORE" -> R.string.ui_digifarm_explore
        "PLAY" -> R.string.ui_digifarm_play
        "TRAIN" -> R.string.ui_digifarm_train
        "SOCIALIZE" -> R.string.ui_digifarm_socialize
        "EAT" -> R.string.ui_digifarm_feed
        "REST" -> R.string.ui_digifarm_rest
        else -> R.string.ui_digifarm_idle
    }
)

private const val SPEECH_BUBBLE_MILLIS = 12_000L
// Farms already given the reference-like default this process lifetime, so an
// explicitly saved camera origin is respected instead of reapplied on revisit.
private val defaultedFarmViews = mutableSetOf<String>()
// Cover zoom: the map always fills the square viewport, so the image edges
// can never show. The default is the previous max for reference-like framing.
private const val MIN_FARM_SCALE = 1f
private const val DEFAULT_FARM_SCALE = 4f
// Estimated bubble height + gap, in dp, above the projected head anchor.
private const val BUBBLE_ABOVE_DP = 56
// World-space heights matching the Filament billboards (island ~1.9 wide).
private const val TAP_HEAD_WORLD_Y = 0.18f
private const val BUBBLE_WORLD_Y = 0.24f

private fun residentFrameImage(frameBytes: ByteArray, resident: FarmResidentWithDetails): ResidentFrameImage {
    val w = resident.spriteWidth
    val h = resident.spriteHeight
    val argb = runCatching { BitmapData(frameBytes, w, h).createARGBIntArray() }
        .getOrDefault(IntArray(0))
    return ResidentFrameImage(argb = argb, width = w, height = h)
}

private fun residentSpriteSetKey(resident: FarmResidentWithDetails): String = buildString {
    append(resident.spriteWidth).append('x').append(resident.spriteHeight).append(':')
    append(resident.spriteSleep.contentHashCode()).append(',')
    append(resident.spriteTrain.contentHashCode()).append(',')
    append(resident.spriteTrain2.contentHashCode()).append(',')
    append(resident.spriteHappy.contentHashCode()).append(',')
    append(resident.spriteWalk.contentHashCode()).append(',')
    append(resident.spriteWalk2.contentHashCode())
}
