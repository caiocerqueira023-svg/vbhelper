package com.github.nacabaro.vbhelper.screens.digifarmScreen

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.components.TopBanner
import com.github.nacabaro.vbhelper.components.VitalButton
import com.github.nacabaro.vbhelper.di.VBHelper
import com.github.nacabaro.vbhelper.digifarm.social.FarmConversationOrchestrator
import com.github.nacabaro.vbhelper.domain.digifarm.Farm
import com.github.nacabaro.vbhelper.dtos.CharacterDtos
import com.github.nacabaro.vbhelper.dtos.FarmResidentWithDetails
import com.github.nacabaro.vbhelper.navigation.NavigationItems
import com.github.nacabaro.vbhelper.source.StorageRepository
import com.github.nacabaro.vbhelper.ui.theme.SpaceBlack
import com.github.nacabaro.vbhelper.ui.theme.SurfaceStroke
import com.github.nacabaro.vbhelper.ui.theme.VitalCyan
import com.github.nacabaro.vbhelper.utils.BitmapData
import com.github.nacabaro.vbhelper.utils.getBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun DigifarmScreen(navController: NavController) {
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
        })
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
        }
    )
}

@Composable
private fun EmptyFarmState(onCreate: (String) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }
    Scaffold(topBar = { TopBanner(text = stringResource(R.string.ui_digifarm_title)) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(stringResource(R.string.ui_digifarm_empty_title), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.ui_digifarm_empty_body), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(20.dp))
            VitalButton(onClick = { showDialog = true }) { Text(stringResource(R.string.ui_digifarm_create)) }
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
    onCreateFarm: (String) -> Unit
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
    val scope = rememberCoroutineScope()
    var selectedResident by remember { mutableStateOf<FarmResidentWithDetails?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var showCreate by remember { mutableStateOf(false) }
    var farmMenu by remember { mutableStateOf(false) }
    var scale by remember(farm.id) { mutableFloatStateOf(farm.cameraScale) }
    var panX by remember(farm.id) { mutableFloatStateOf(farm.cameraX) }
    var panY by remember(farm.id) { mutableFloatStateOf(farm.cameraY) }
    var frame by remember { mutableIntStateOf(0) }
    var dialogueIssue by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(farm.id) {
        while (true) {
            runCatching { withContext(Dispatchers.IO) { repository.simulateStep(farm.id) } }
            frame = 1 - frame
            delay(900L)
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

    Scaffold(
        topBar = {
            TopBanner(
                text = "${farm.name}  •  ${residents.size}/${farm.capacity}",
                onGearClick = { farmMenu = true }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).background(SpaceBlack)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    TextButton(onClick = { farmMenu = true }) { Text(farm.name) }
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
                    }
                }
                Row {
                    TextButton(onClick = { showAdd = true }, enabled = residents.size < farm.capacity) {
                        Text(stringResource(R.string.ui_digifarm_add_resident))
                    }
                    TextButton(onClick = {
                        navController.navigate(NavigationItems.FarmGroup.route.replace("{farmId}", farm.id))
                    }) { Text(stringResource(R.string.ui_digifarm_group)) }
                }
            }
            dialogueIssue?.let {
                Text(
                    stringResource(R.string.ui_digifarm_dialogue_unavailable),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            BoxWithConstraints(
                Modifier.fillMaxWidth().weight(1f).clip(MaterialTheme.shapes.medium)
                    .border(1.dp, SurfaceStroke, MaterialTheme.shapes.medium)
            ) {
                val density = LocalDensity.current
                val mapWidthDp = minOf(maxWidth, maxHeight * (512f / 736f))
                val mapHeightDp = mapWidthDp * (736f / 512f)
                val mapWidthPx = with(density) { mapWidthDp.toPx() }
                val mapHeightPx = with(density) { mapHeightDp.toPx() }
                Box(
                    Modifier.fillMaxSize().pointerInput(farm.id, mapWidthPx, mapHeightPx) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val nextScale = (scale * zoom).coerceIn(1f, 4f)
                            val maxX = ((mapWidthPx * nextScale - size.width) / 2f).coerceAtLeast(0f)
                            val maxY = ((mapHeightPx * nextScale - size.height) / 2f).coerceAtLeast(0f)
                            scale = nextScale
                            panX = (panX + pan.x).coerceIn(-maxX, maxX)
                            panY = (panY + pan.y).coerceIn(-maxY, maxY)
                        }
                    },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier.width(mapWidthDp).height(mapHeightDp)
                        .graphicsLayer(scaleX = scale, scaleY = scale, translationX = panX, translationY = panY)
                    ) {
                    Image(
                        painter = painterResource(R.drawable.bird_digifarm),
                        contentDescription = stringResource(R.string.ui_digifarm_map_description),
                        modifier = Modifier.fillMaxSize()
                    )
                    residents.sortedBy { it.positionY }.forEach { resident ->
                        val activityLabel = farmActivityLabel(resident.activity)
                        val currentFrame = when (resident.activity) {
                            "REST" -> resident.spriteSleep
                            "TRAIN" -> if (frame == 0) resident.spriteTrain else resident.spriteTrain2
                            "PLAY" -> resident.spriteHappy
                            else -> if (frame == 0) resident.spriteWalk else resident.spriteWalk2
                        }
                        val bitmap = remember(currentFrame, resident.spriteWidth, resident.spriteHeight) {
                            runCatching { BitmapData(currentFrame, resident.spriteWidth, resident.spriteHeight).getBitmap().asImageBitmap() }.getOrNull()
                        }
                        val spriteWidth = 48.dp
                        val spriteHeight = 48.dp
                        val px = resident.positionX / 512f * mapWidthPx
                        val py = resident.positionY / 736f * mapHeightPx
                        Box(
                            Modifier.offset { IntOffset((px - with(density) { spriteWidth.toPx() / 2 }).roundToInt(), (py - with(density) { spriteHeight.toPx() }).roundToInt()) }
                                .size(spriteWidth, spriteHeight).clickable { selectedResident = resident }
                        ) {
                            bitmap?.let {
                                Image(
                                    bitmap = it,
                                    contentDescription = "${resident.displayName}: $activityLabel",
                                    filterQuality = FilterQuality.None,
                                    modifier = Modifier.fillMaxSize().then(if (resident.facingLeft) Modifier.scale(scaleX = -1f, scaleY = 1f) else Modifier)
                                )
                            }
                            if (selectedResident?.individualId == resident.individualId) {
                                Box(Modifier.fillMaxSize().border(2.dp, VitalCyan))
                            }
                        }
                        messages.firstOrNull {
                            it.authorIndividualId == resident.individualId &&
                                System.currentTimeMillis() - it.timestamp <= SPEECH_BUBBLE_MILLIS
                        }?.let { message ->
                            Surface(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = .94f),
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.offset {
                                    IntOffset((px - 55 * density.density).roundToInt(), (py - 82 * density.density).roundToInt())
                                }.width(110.dp).graphicsLayer(
                                    scaleX = 1f / scale,
                                    scaleY = 1f / scale,
                                    transformOrigin = TransformOrigin(.5f, 1f)
                                )
                            ) {
                                Text(message.body, style = MaterialTheme.typography.labelSmall, maxLines = 3, modifier = Modifier.padding(6.dp))
                            }
                        }
                    }
                }
            }
            }
            Row(
                Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                VitalButton(onClick = { scale = (scale / 1.35f).coerceAtLeast(1f) }) { Text("−") }
                Text("${(scale * 100).roundToInt()}%", Modifier.padding(horizontal = 12.dp))
                VitalButton(onClick = { scale = (scale * 1.35f).coerceAtMost(4f) }) { Text("+") }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { scale = 1f; panX = 0f; panY = 0f }) { Text(stringResource(R.string.ui_digifarm_fit)) }
            }
        }
    }

    if (showAdd) AddResidentDialog(
        characters = storageCharacters.filterNot { it.id in residentCharacterIds },
        onDismiss = { showAdd = false },
        onAdd = { character ->
            scope.launch(Dispatchers.IO) {
                val result = runCatching { repository.addResident(farm.id, character.id) }
                withContext(Dispatchers.Main) {
                    result.exceptionOrNull()?.let { Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() }
                    if (result.isSuccess) showAdd = false
                }
            }
        }
    )
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
            onActivity = { activity ->
                selectedResident = null
                scope.launch(Dispatchers.IO) { repository.setActivity(resident.individualId, farm.id, activity) }
            },
            onRemove = {
                selectedResident = null
                scope.launch(Dispatchers.IO) { repository.removeResident(resident.individualId) }
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

@Composable
private fun AddResidentDialog(
    characters: List<CharacterDtos.CharacterWithSprites>,
    onDismiss: () -> Unit,
    onAdd: (CharacterDtos.CharacterWithSprites) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ui_digifarm_choose_resident)) },
        text = {
            if (characters.isEmpty()) Text(stringResource(R.string.ui_digifarm_no_available))
            else LazyColumn(Modifier.height(360.dp)) {
                items(characters, key = { it.id }) { character ->
                    TextButton(onClick = { onAdd(character) }, modifier = Modifier.fillMaxWidth()) {
                        Text(character.nickname ?: character.speciesName ?: "Digimon")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_close)) } }
    )
}

@Composable
private fun ResidentDialog(
    resident: FarmResidentWithDetails,
    onDismiss: () -> Unit,
    onPrivateChat: () -> Unit,
    onGroup: () -> Unit,
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
