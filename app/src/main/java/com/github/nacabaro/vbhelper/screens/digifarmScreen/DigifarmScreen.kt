package com.github.nacabaro.vbhelper.screens.digifarmScreen

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
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
import com.github.nacabaro.vbhelper.rendering.sprite3d.ResidentFrameImage
import com.github.nacabaro.vbhelper.rendering.sprite3d.SpriteExtrusionGlb
import com.github.nacabaro.vbhelper.domain.digifarm.Farm
import com.github.nacabaro.vbhelper.dtos.FarmResidentWithDetails
import com.github.nacabaro.vbhelper.navigation.NavigationItems
import com.github.nacabaro.vbhelper.screens.storageScreen.StorageCharacterPickerDialog
import com.github.nacabaro.vbhelper.source.StorageRepository
import com.github.nacabaro.vbhelper.components.cyberFrame
import com.github.nacabaro.vbhelper.screens.worldScreen.WorldSectionTabs
import com.github.nacabaro.vbhelper.ui.theme.SpaceBlack
import com.github.nacabaro.vbhelper.ui.theme.DeepPurpleBgAlt
import com.github.nacabaro.vbhelper.ui.theme.SurfaceDeepPurple
import com.github.nacabaro.vbhelper.ui.theme.SurfaceElevatedPurple
import com.github.nacabaro.vbhelper.ui.theme.SurfaceStroke
import com.github.nacabaro.vbhelper.ui.theme.TextPrimaryOnDark
import com.github.nacabaro.vbhelper.ui.theme.TextSecondaryOnDark
import com.github.nacabaro.vbhelper.ui.theme.VitalCyan
import com.github.nacabaro.vbhelper.utils.BitmapData
import com.github.nacabaro.vbhelper.utils.createARGBIntArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.awaitCancellation
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
    val residentsByName = remember(residents) {
        residents.sortedWith(
            compareBy<FarmResidentWithDetails> { it.displayName.lowercase() }
                .thenBy { it.individualId }
        )
    }
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
    var showFarmSettings by remember { mutableStateOf(false) }
    var scale by remember(farm.id) { mutableFloatStateOf(farm.cameraScale) }
    var panX by remember(farm.id) { mutableFloatStateOf(farm.cameraX) }
    var panY by remember(farm.id) { mutableFloatStateOf(farm.cameraY) }
    var sceneView by remember { mutableStateOf<Digifarm3dSceneView?>(null) }
    var sceneIssue by remember(farm.id) { mutableStateOf<String?>(null) }
    var dialogueIssue by remember { mutableStateOf<String?>(null) }
    var offlineSummary by remember(farm.id) { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Single detailed session per app (§13): map and group share the coordinator.
    LaunchedEffect(farm.id) {
        withContext(Dispatchers.IO) { coordinator.acquire(farm.id) }
        offlineSummary = withContext(Dispatchers.IO) { repository.summarizeReturn(farm.id) }
        try {
            awaitCancellation()
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
                    onGearClick = { showFarmSettings = true }
                )
                WorldSectionTabs(selectedWorldTab, onWorldTabSelected)
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
            // Canonical island positions shared by the Filament residents,
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
            // Build silhouette-extruded pose meshes off the UI thread, once
            // per roster/art change. The renderer switches poses with motion.
            val rosterKey = remember(residents) {
                residents.associate { it.individualId to residentSpriteSetKey(it) }
            }
            LaunchedEffect(rosterKey, sceneView) {
                val view = sceneView ?: return@LaunchedEffect
                val sets = withContext(Dispatchers.Default) {
                    residents.map { resident ->
                        val poses = mapOf(
                            "idle" to residentFrameImage(resident.spriteIdle, resident),
                            "idle2" to residentFrameImage(resident.spriteIdle2, resident),
                            "sleep" to residentFrameImage(resident.spriteSleep, resident),
                            "train" to residentFrameImage(resident.spriteTrain, resident),
                            "train2" to residentFrameImage(resident.spriteTrain2, resident),
                            "happy" to residentFrameImage(resident.spriteHappy, resident),
                            "happy2" to residentFrameImage(resident.spriteIdle2, resident),
                            "walk" to residentFrameImage(resident.spriteWalk, resident),
                            "walk2" to residentFrameImage(resident.spriteWalk2, resident)
                        )
                        ResidentFrames(
                            id = resident.individualId,
                            poses = poses,
                            modelGlb = SpriteExtrusionGlb.build(poses),
                            setKey = rosterKey[resident.individualId] ?: resident.individualId
                        )
                    }
                }
                view.setResidentFrames(sets)
            }
            // DB updates provide collision-checked steps; Filament interpolates
            // those steps and turns each model toward its actual travel.
            LaunchedEffect(residents, followId, selectedResident?.individualId, sceneView) {
                val view = sceneView ?: return@LaunchedEffect
                view.updateResidentPoses(residents.map { resident ->
                    val world = worldPositions[resident.individualId]
                    ResidentPose(
                        id = resident.individualId,
                        worldX = world?.x ?: 0f,
                        worldZ = world?.z ?: 0f,
                        activity = resident.activity,
                        facingLeft = resident.facingLeft,
                        selected = followId == resident.individualId ||
                            selectedResident?.individualId == resident.individualId
                    )
                })
            }
            // The viewport is a real GLB scene. Compose only layers UI above
            // it: speech bubbles and invisible tap targets projected onto
            // the interpolated 3D residents.
            val mapDescription = stringResource(R.string.ui_digifarm_map_description)
            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .border(2.dp, SurfaceStroke, MaterialTheme.shapes.medium)
                    .clip(MaterialTheme.shapes.medium)
                    .background(DeepPurpleBgAlt)
                    .semantics {
                        contentDescription = mapDescription
                    }
            ) {
                val density = LocalDensity.current
                val now = System.currentTimeMillis()
                val visibleBubbles = remember(messages, selectedResident) {
                    val recent = messages.filter { it.authorIndividualId != null && now - it.timestamp <= SPEECH_BUBBLE_MILLIS }
                val focusId = selectedResident?.individualId ?: followId
                val selected = focusId?.let { id -> recent.filter { it.authorIndividualId == id } } ?: emptyList()
                    val others = recent.filter { it !in selected }.sortedByDescending { it.timestamp }
                    (selected.sortedByDescending { it.timestamp } + others).distinctBy { it.authorIndividualId }.take(3)
                }
                Box(Modifier.fillMaxSize()) {
                    Digifarm3dViewport(
                        modifier = Modifier.fillMaxSize(),
                        assetName = Digifarm3dMap.runtimeAsset,
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
                    // Subscribe to scene motion so tap targets and bubbles follow.
                    @Suppress("UNUSED_EXPRESSION")
                    projectionTick
                    // Invisible tap targets over the extruded residents.
                    residents.forEach { resident ->
                        val view = sceneView ?: return@forEach
                        val foot = view.projectResident(resident.individualId, 0f) ?: return@forEach
                        val head = view.projectResident(resident.individualId, TAP_HEAD_WORLD_Y) ?: return@forEach
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
                                .clickable(onClickLabel = stringResource(R.string.ui_digifarm_open_settings)) {
                                    selectedResident = resident
                                }
                        )
                    }
                    visibleBubbles.forEach { message ->
                        val authorId = message.authorIndividualId ?: return@forEach
                        val anchor = sceneView?.projectResident(authorId, BUBBLE_WORLD_Y) ?: return@forEach
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
                            color = TextSecondaryOnDark,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
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
                        items(residentsByName, key = { it.individualId }) { resident ->
                            val following = followId == resident.individualId
                            Surface(
                                modifier = Modifier
                                    .width(132.dp)
                                    .height(48.dp)
                                    .combinedClickable(
                                        role = Role.Button,
                                        onClickLabel = stringResource(R.string.ui_digifarm_follow_resident),
                                        onLongClickLabel = stringResource(R.string.ui_digifarm_open_settings),
                                        onClick = { followId = resident.individualId },
                                        onLongClick = { selectedResident = resident }
                                    ),
                                shape = MaterialTheme.shapes.small,
                                color = if (following) VitalCyan.copy(alpha = 0.12f) else SurfaceDeepPurple,
                                contentColor = if (following) VitalCyan else TextPrimaryOnDark,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (following) VitalCyan else SurfaceStroke
                                )
                            ) {
                                Box(
                                    Modifier.fillMaxSize().padding(horizontal = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        resident.displayName,
                                        style = MaterialTheme.typography.labelLarge,
                                        textAlign = TextAlign.Center,
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
    if (showFarmSettings) {
        FarmSettingsSheet(
            farm = farm,
            farms = farms,
            residentCount = residents.size,
            onDismiss = { showFarmSettings = false },
            onSelectFarm = { selectedFarmId ->
                showFarmSettings = false
                onSelectFarm(selectedFarmId)
            },
            onCreateFarm = {
                showFarmSettings = false
                showCreate = true
            },
            onToggleDialogue = {
                scope.launch(Dispatchers.IO) {
                    repository.setAutonomousDialogue(farm.id, !farm.autonomousDialogueEnabled)
                }
            },
            onOpenResidents = {
                showFarmSettings = false
                showResidentList = true
            },
            onArchive = {
                showFarmSettings = false
                scope.launch(Dispatchers.IO) {
                    repository.archiveFarm(farm.id)
                    withContext(Dispatchers.Main) {
                        onSelectFarm(farms.firstOrNull { it.id != farm.id }?.id ?: "")
                    }
                }
            }
        )
    }
    if (showCreate) FarmNameDialog(onDismiss = { showCreate = false }) {
        showCreate = false
        onCreateFarm(it)
    }
    selectedResident?.let { selected ->
        val resident = residents.firstOrNull { it.individualId == selected.individualId } ?: selected
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
                scope.launch(Dispatchers.IO) {
                    val result = runCatching { repository.setActivity(resident.individualId, farm.id, activity) }
                    withContext(Dispatchers.Main) {
                        val message = if (result.isSuccess) {
                            context.getString(
                                R.string.ui_digifarm_activity,
                                context.getString(farmActivityLabelRes(activity))
                            )
                        } else {
                            result.exceptionOrNull()?.localizedMessage?.takeIf(String::isNotBlank)
                                ?: context.getString(R.string.ui_digifarm_activity_failed)
                        }
                        snackbarHostState.showSnackbar(message)
                    }
                }
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
        ResidentsSheet(
            residents = residentsByName,
            followId = followId,
            onDismiss = { showResidentList = false },
            onFollow = { resident ->
                followId = resident.individualId
                showResidentList = false
            },
            onOpenSettings = { resident ->
                selectedResident = resident
                showResidentList = false
            }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FarmSettingsSheet(
    farm: Farm,
    farms: List<Farm>,
    residentCount: Int,
    onDismiss: () -> Unit,
    onSelectFarm: (String) -> Unit,
    onCreateFarm: () -> Unit,
    onToggleDialogue: () -> Unit,
    onOpenResidents: () -> Unit,
    onArchive: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDeepPurple,
        contentColor = TextPrimaryOnDark
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    farm.name,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "$residentCount/${farm.capacity} · ${stringResource(R.string.ui_digifarm_residents)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondaryOnDark
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.ui_digifarm_select_farm),
                    style = MaterialTheme.typography.titleSmall,
                    color = VitalCyan
                )
                farms.forEach { item ->
                    val selected = item.id == farm.id
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .background(
                                if (selected) VitalCyan.copy(alpha = 0.12f)
                                else SurfaceElevatedPurple.copy(alpha = 0.6f)
                            )
                            .cyberFrame()
                            .clickable(role = Role.RadioButton) {
                                if (!selected) onSelectFarm(item.id)
                            }
                            .padding(start = 12.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            item.name,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (selected) VitalCyan else TextPrimaryOnDark,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        RadioButton(
                            selected = selected,
                            onClick = { if (!selected) onSelectFarm(item.id) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = VitalCyan,
                                unselectedColor = SurfaceStroke
                            )
                        )
                    }
                }
            }

            Box(Modifier.fillMaxWidth().height(1.dp).background(SurfaceStroke.copy(alpha = 0.7f)))

            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .background(SurfaceElevatedPurple.copy(alpha = 0.62f))
                    .cyberFrame()
                    .padding(start = 12.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.ui_digifarm_autonomous_dialogue),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge
                )
                Switch(
                    checked = farm.autonomousDialogueEnabled,
                    onCheckedChange = { onToggleDialogue() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = SurfaceDeepPurple,
                        checkedTrackColor = VitalCyan,
                        uncheckedThumbColor = TextSecondaryOnDark,
                        uncheckedTrackColor = SurfaceStroke
                    )
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FarmSettingsActionRow(
                    text = stringResource(R.string.ui_digifarm_create_another),
                    onClick = onCreateFarm,
                    highlighted = true
                )
                FarmSettingsActionRow(
                    text = stringResource(R.string.ui_digifarm_residents),
                    onClick = onOpenResidents
                )
                FarmSettingsActionRow(
                    text = stringResource(R.string.ui_digifarm_archive),
                    onClick = onArchive,
                    destructive = true
                )
            }
        }
    }
}

@Composable
private fun FarmSettingsActionRow(
    text: String,
    onClick: () -> Unit,
    highlighted: Boolean = false,
    destructive: Boolean = false
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .background(
                when {
                    destructive -> MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                    highlighted -> VitalCyan.copy(alpha = 0.12f)
                    else -> SurfaceElevatedPurple.copy(alpha = 0.6f)
                }
            )
            .cyberFrame()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = when {
                destructive -> MaterialTheme.colorScheme.error
                highlighted -> VitalCyan
                else -> TextPrimaryOnDark
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDeepPurple,
        contentColor = TextPrimaryOnDark
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    resident.displayName,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    stringResource(R.string.ui_digifarm_activity, farmActivityLabel(resident.activity)),
                    style = MaterialTheme.typography.labelLarge,
                    color = VitalCyan
                )
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(SurfaceElevatedPurple.copy(alpha = 0.68f))
                    .cyberFrame()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    stringResource(R.string.ui_digifarm_needs_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondaryOnDark
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FarmNeedMetric(stringResource(R.string.ui_digifarm_energy), resident.energy, Modifier.weight(1f))
                    FarmNeedMetric(stringResource(R.string.ui_digifarm_food), resident.satiety, Modifier.weight(1f))
                    FarmNeedMetric(stringResource(R.string.ui_digifarm_social), resident.social, Modifier.weight(1f))
                    FarmNeedMetric(stringResource(R.string.ui_digifarm_fun), resident.funLevel, Modifier.weight(1f))
                }
            }
            Text(
                stringResource(R.string.ui_digifarm_activities),
                style = MaterialTheme.typography.titleSmall,
                color = VitalCyan
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VitalButton(
                    onClick = { onActivity("PLAY") },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    borderColor = if (resident.activity == "PLAY") VitalCyan else SurfaceStroke,
                    contentColor = if (resident.activity == "PLAY") VitalCyan else TextPrimaryOnDark,
                    containerColor = if (resident.activity == "PLAY") VitalCyan.copy(alpha = 0.12f) else SurfaceDeepPurple
                ) {
                    Text(stringResource(R.string.ui_digifarm_play), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                VitalButton(
                    onClick = { onActivity("TRAIN") },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    borderColor = if (resident.activity == "TRAIN") VitalCyan else SurfaceStroke,
                    contentColor = if (resident.activity == "TRAIN") VitalCyan else TextPrimaryOnDark,
                    containerColor = if (resident.activity == "TRAIN") VitalCyan.copy(alpha = 0.12f) else SurfaceDeepPurple
                ) {
                    Text(stringResource(R.string.ui_digifarm_train), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VitalButton(
                    onClick = { onActivity("EAT") },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    borderColor = if (resident.activity == "EAT") VitalCyan else SurfaceStroke,
                    contentColor = if (resident.activity == "EAT") VitalCyan else TextPrimaryOnDark,
                    containerColor = if (resident.activity == "EAT") VitalCyan.copy(alpha = 0.12f) else SurfaceDeepPurple
                ) {
                    Text(stringResource(R.string.ui_digifarm_feed), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                VitalButton(
                    onClick = { onActivity("REST") },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    borderColor = if (resident.activity == "REST") VitalCyan else SurfaceStroke,
                    contentColor = if (resident.activity == "REST") VitalCyan else TextPrimaryOnDark,
                    containerColor = if (resident.activity == "REST") VitalCyan.copy(alpha = 0.12f) else SurfaceDeepPurple
                ) {
                    Text(stringResource(R.string.ui_digifarm_rest), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(SurfaceStroke.copy(alpha = 0.7f)))
            Text(
                stringResource(R.string.ui_digifarm_more_actions),
                style = MaterialTheme.typography.labelLarge,
                color = TextSecondaryOnDark
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onPrivateChat, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.ui_digifarm_private_chat), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                TextButton(onClick = onGroup, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.ui_digifarm_group), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onFollow, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.ui_digifarm_follow), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                TextButton(onClick = onTransfer, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.ui_digifarm_transfer), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            TextButton(onClick = onRemove, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.ui_digifarm_remove), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun FarmNeedMetric(label: String, value: Int, modifier: Modifier = Modifier) {
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            value.toString(),
            style = MaterialTheme.typography.titleSmall,
            color = TextPrimaryOnDark
        )
        Text(
            label,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondaryOnDark,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        LinearProgressIndicator(
            progress = { value.coerceIn(0, 100) / 100f },
            modifier = Modifier.fillMaxWidth().height(4.dp),
            color = VitalCyan,
            trackColor = SurfaceStroke
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResidentsSheet(
    residents: List<FarmResidentWithDetails>,
    followId: String?,
    onDismiss: () -> Unit,
    onFollow: (FarmResidentWithDetails) -> Unit,
    onOpenSettings: (FarmResidentWithDetails) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDeepPurple,
        contentColor = TextPrimaryOnDark
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(stringResource(R.string.ui_digifarm_residents), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.ui_digifarm_resident_interaction_hint),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondaryOnDark
            )
            if (residents.isEmpty()) {
                Text(
                    stringResource(R.string.ui_digifarm_empty_body),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    color = TextSecondaryOnDark,
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    items(residents, key = { it.individualId }) { resident ->
                        val following = followId == resident.individualId
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    role = Role.Button,
                                    onClickLabel = stringResource(R.string.ui_digifarm_follow_resident),
                                    onLongClickLabel = stringResource(R.string.ui_digifarm_open_settings),
                                    onClick = { onFollow(resident) },
                                    onLongClick = { onOpenSettings(resident) }
                                ),
                            shape = MaterialTheme.shapes.small,
                            color = if (following) VitalCyan.copy(alpha = 0.12f) else SurfaceElevatedPurple.copy(alpha = 0.58f),
                            contentColor = if (following) VitalCyan else TextPrimaryOnDark,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (following) VitalCyan else SurfaceStroke
                            )
                        ) {
                            Row(
                                Modifier.heightIn(min = 60.dp).padding(horizontal = 14.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        resident.displayName,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        farmActivityLabel(resident.activity),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (following) VitalCyan else TextSecondaryOnDark
                                    )
                                }
                                if (following) {
                                    Text(
                                        stringResource(R.string.ui_digifarm_following),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = VitalCyan
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
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
    farmActivityLabelRes(activity)
)

private fun farmActivityLabelRes(activity: String): Int =
    when (activity) {
        "EXPLORE" -> R.string.ui_digifarm_explore
        "PLAY" -> R.string.ui_digifarm_play
        "TRAIN" -> R.string.ui_digifarm_train
        "SOCIALIZE" -> R.string.ui_digifarm_socialize
        "EAT" -> R.string.ui_digifarm_feed
        "REST" -> R.string.ui_digifarm_rest
        else -> R.string.ui_digifarm_idle
    }

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
// World-space heights matching the extruded residents (island ~1.9 wide).
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
    append(resident.spriteIdle.contentHashCode()).append(',')
    append(resident.spriteIdle2.contentHashCode()).append(',')
    append(resident.spriteSleep.contentHashCode()).append(',')
    append(resident.spriteTrain.contentHashCode()).append(',')
    append(resident.spriteTrain2.contentHashCode()).append(',')
    append(resident.spriteHappy.contentHashCode()).append(',')
    append(resident.spriteWalk.contentHashCode()).append(',')
    append(resident.spriteWalk2.contentHashCode())
}
