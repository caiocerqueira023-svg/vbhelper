package com.tamerslab.farm.ui.digifarm

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tamerslab.R
import com.tamerslab.components.VitalButton
import com.tamerslab.components.VitalButtonStyle
import com.tamerslab.components.cyberFrame
import com.tamerslab.components.motionEnabled
import com.tamerslab.di.TamersLabApplication
import com.tamerslab.farm.FarmWorldPreferences
import com.tamerslab.farm.FarmWorldPreferencesSnapshot
import com.tamerslab.farm.diorama.FarmDioramaFrame
import com.tamerslab.farm.diorama.FarmDioramaSeed
import com.tamerslab.farm.diorama.FarmDioramaSimulation
import com.tamerslab.farm.farmWorldStore
import com.tamerslab.farm.map.Digifarm3dMap
import com.tamerslab.dtos.CharacterDtos
import com.tamerslab.rendering.sprite3d.ResidentFrameImage
import com.tamerslab.rendering.sprite3d.SpriteExtrusionGlb
import com.tamerslab.storage.data.StorageRepository
import com.tamerslab.ui.theme.SpaceBlack
import com.tamerslab.ui.theme.SurfaceDeepPurple
import com.tamerslab.ui.theme.SurfaceStroke
import com.tamerslab.ui.theme.TextPrimaryOnDark
import com.tamerslab.ui.theme.TextSecondaryOnDark
import com.tamerslab.ui.theme.VitalCyan
import com.tamerslab.utils.BitmapData
import com.tamerslab.utils.createARGBIntArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * The Digifarm is presentation only (owner revision, 2026-10-30).
 *
 * One home farm and no game state: the active partner plus the explicitly curated farm members live
 * on the island as pure animation. The owner controls how many appear and whether the diorama draws
 * card/VB artwork or Dot artwork. `FarmDioramaSimulation` decides the life on screen — it is a
 * cosmetic clock, not the retired farm simulation that issued needs, activities or rewards, and
 * nothing it does is persisted.
 */
private const val ROTATION_BUCKET_MILLIS = 3_600_000L

@Composable
fun rememberFarmWorldController(): FarmWorldController {
    val context = LocalContext.current
    val app = context.applicationContext as TamersLabApplication
    val storageRepository = remember(app) { StorageRepository(app.container.db) }
    val database = app.container.db
    val preferences = remember(app) { FarmWorldPreferences(app.farmWorldStore) }
    val dotLibrary = app.container.dotSpriteLibrary
    val scope = rememberCoroutineScope()

    val charactersState = storageRepository.getAllCharacters().collectAsStateWithLifecycle(emptyList())
    val preferencesState = preferences.snapshot.collectAsStateWithLifecycle(FarmWorldPreferencesSnapshot())
    val prefs = preferencesState.value
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(350)
        settled = true
    }
    val bucket = remember { System.currentTimeMillis() / ROTATION_BUCKET_MILLIS }
    val candidates = remember(charactersState.value, prefs.farmMemberIds) {
        orderedFarmCandidates(charactersState.value, prefs.farmMemberIds)
    }
    val candidateKey = remember(candidates) { candidates.joinToString(",") { it.id.toString() } }

    // Artwork resolution hits SQLite and, for Dot sprites, the bundled pack: keep it off the UI thread
    // and only rerun when the candidates, the limit or the artwork filter change.
    val rosterState = produceState(
        initialValue = emptyList<FarmWorldResident>(),
        candidateKey,
        prefs.residentLimit,
        prefs.spriteFilter,
        dotLibrary,
    ) {
        value = withContext(Dispatchers.IO) {
            buildFarmRoster(
                candidates = candidates,
                rotationBucket = bucket,
                limit = prefs.residentLimit,
                filter = prefs.spriteFilter,
                dotLibrary = dotLibrary,
                db = database,
            )
        }
    }
    val roster = rosterState.value

    // The follow choice is a persisted preference keyed by character id, so rotation or a roster
    // refresh can never re-point it; only a deliberate follow changes it. While the chosen partner is
    // not on screen the camera falls back to the leader, and it returns to the choice as soon as the
    // partner is visible again.
    val followedRenderId = remember(roster, prefs.followedCharacterId) {
        val stored = prefs.followedCharacterId
        val chosen = roster.firstOrNull { it.characterId == stored }
            ?: roster.firstOrNull { it.isActivePartner }
            ?: roster.firstOrNull()
        chosen?.renderId
    }

    val memberOptions = remember(charactersState.value, prefs.farmMemberIds) {
        val participating = charactersState.value.filter { !it.isInAdventure }
        val activeId = participating.firstOrNull { it.active }?.id
        val explicit = prefs.farmMemberIds
        val seeded = participating.filter { it.isFavorite }.map { it.id }.toSet()
        val members = explicit ?: seeded
        participating
            .map { character ->
                FarmMemberOption(
                    characterId = character.id,
                    name = character.nickname?.takeIf(String::isNotBlank)
                        ?: character.speciesName?.takeIf(String::isNotBlank)
                        ?: "Digimon",
                    isMember = character.id == activeId || character.id in members,
                    isActive = character.id == activeId,
                    character = character,
                )
            }
            .sortedWith(compareBy({ !it.isActive }, { it.name.lowercase() }))
    }
    val summary = FarmWorldSummary(
        residents = roster,
        followedRenderId = followedRenderId,
        loading = !settled && roster.isEmpty(),
        residentLimit = prefs.residentLimit,
        spriteFilter = prefs.spriteFilter,
        candidateCount = candidates.size,
        memberOptions = memberOptions,
    )
    val summaryState = rememberUpdatedState(summary)
    val sceneSettled = remember { mutableStateOf(false) }
    val recenterSignal = remember { mutableIntStateOf(0) }

    return FarmWorldController(
        summary = summaryState,
        setFollowTarget = { renderId ->
            scope.launch {
                preferences.setFollowCharacter(renderId?.removePrefix("farm-")?.toLongOrNull())
            }
        },
        recenterOnPartner = { recenterSignal.intValue = recenterSignal.intValue + 1 },
        setResidentLimit = { limit ->
            scope.launch { preferences.setResidentLimit(limit) }
        },
        setSpriteFilter = { filter ->
            scope.launch { preferences.setSpriteFilter(filter) }
        },
        removeFromFarm = { characterId ->
            scope.launch {
                preferences.updateFarmMembers(
                    favouriteIds = charactersState.value.filter { it.isFavorite }.map { it.id },
                    remove = characterId,
                )
            }
        },
        setFarmMember = { characterId, member ->
            scope.launch {
                preferences.updateFarmMembers(
                    favouriteIds = charactersState.value.filter { it.isFavorite }.map { it.id },
                    add = if (member) characterId else null,
                    remove = if (member) null else characterId,
                )
            }
        },
        sceneReady = sceneSettled,
        notifySceneSettled = { sceneSettled.value = true },
        recenterRequests = recenterSignal,
    )
}

/** Active partner first, then favourites (ordered by id so the selection is stable). */
private fun orderedFarmCandidates(
    characters: List<CharacterDtos.CharacterWithSprites>,
    memberIds: Set<Long>?,
): List<CharacterDtos.CharacterWithSprites> {
    val participating = characters.filter { !it.isInAdventure }
    val active = participating.firstOrNull { it.active }
    // Membership is explicit. `null` means the owner has never curated the island, so it falls back to
    // favourites once for continuity with the old behaviour; after the first edit, favourites have no
    // influence on who lives here.
    val members = if (memberIds != null) {
        participating.filter { it.id in memberIds }
    } else {
        participating.filter { it.isFavorite }
    }
    val others = members.filter { it.id != active?.id }.sortedBy { it.id }
    return buildList {
        active?.let(::add)
        addAll(others)
    }
}

/**
 * Full-bleed 3D diorama. Chrome, dock and partner sheet are drawn above by `FarmHomeScreen`; the
 * surface pauses (never unmounts) when the sheet covers it or a place is open.
 *
 * The residents are not scripted per-actor here: `FarmDioramaSimulation` owns the whole cast and
 * hands over one immutable frame per beat, so pacing (how many may walk, sleep or socialise at
 * once), collision and the social beats are decided in one deterministic place instead of by a
 * dozen independent coroutines. This composable only drives the clock, builds the extruded pose
 * meshes once per roster/art change and projects the emote layer.
 */
@Composable
fun FarmHomeWorld(
    controller: FarmWorldController,
    modifier: Modifier = Modifier,
    isActive: Boolean = true,
    onResidentTap: (renderId: String) -> Unit = {},
) {
    val summary by controller.summary
    val reduceMotion = !motionEnabled()
    val roster = summary.residents
    val rosterKey = remember(roster) {
        roster.joinToString(",") { it.renderId + ":" + it.artwork.contentKey }
    }
    val bounds = remember { Digifarm3dMap.defaultPlayableBounds }

    var sceneView by remember { mutableStateOf<Digifarm3dSceneView?>(null) }
    var sceneIssue by remember { mutableStateOf<String?>(null) }
    var framesReady by remember(rosterKey) { mutableStateOf(false) }
    // One cast, one clock. The simulation is kept in `remember` so the same tableau survives a pause
    // when a place covers Home or the partner sheet expands. Its seed is fresh entropy every time the
    // cast is created (app start or roster change): no session replays a previous show, and every
    // Digimon owns its own random stream on top of that. The fixed-seed constructor exists only for
    // tests.
    val diorama = remember(rosterKey) {
        FarmDioramaSimulation(
            seeds = roster.map { FarmDioramaSeed(id = it.renderId, bodyRadius = it.artwork.bodyRadius) },
            bounds = bounds,
            seed = Random.nextLong(),
        )
    }

    // Build the extruded pose meshes once per roster/art change.
    LaunchedEffect(rosterKey, sceneView) {
        val view = sceneView ?: return@LaunchedEffect
        if (roster.isEmpty()) {
            framesReady = false
            return@LaunchedEffect
        }
        val followed = summary.followedRenderId
        val ordered = if (followed == null) roster else {
            roster.filter { it.renderId == followed } +
                roster.filterNot { it.renderId == followed }
        }
        val sets = mutableListOf<ResidentFrames>()
        for (batch in ordered.chunked(2)) {
            val batchSets = withContext(Dispatchers.Default) {
                coroutineScope {
                    batch.map { resident -> async { buildResidentFrames(resident) } }.awaitAll()
                }
            }
            if (batchSets.isEmpty()) continue
            sets += batchSets
            view.setResidentFrames(batchSets, completeRoster = false)
        }
        view.setResidentFrames(sets, completeRoster = true)
        framesReady = sets.isNotEmpty()
    }

    // Drive the diorama. One beat per 100 ms is enough for 10 Hz probability rolls and smooth walking
    // once the renderer interpolates each step; the loop is tied to `isActive` so a covered farm
    // (place open, sheet expanded) costs no work, and it resumes from the simulated state instead of
    // replaying the opening tableau.
    LaunchedEffect(rosterKey, sceneView, reduceMotion, framesReady, isActive) {
        val view = sceneView ?: return@LaunchedEffect
        if (!framesReady || !isActive) return@LaunchedEffect
        view.updateResidentPoses(diorama.currentFrame().toResidentPoses())
        // Reduced motion keeps the staged tableau: no walking, no social beats, no gestures. The whole
        // island is alive only through the static poses, exactly like the frame budget contract.
        if (reduceMotion) return@LaunchedEffect
        while (true) {
            // Cancellation happens in delay(); no flag needed and no work is lost mid-beat.
            delay(diorama.beatMillis)
            view.updateResidentPoses(diorama.step().toResidentPoses())
        }
    }

    LaunchedEffect(summary.followedRenderId, sceneView) {
        sceneView?.followId = summary.followedRenderId
    }
    LaunchedEffect(controller.recenterRequests.value, sceneView) {
        if (controller.recenterRequests.value > 0) sceneView?.recenterOnFollow()
    }

    Box(modifier) {
        Digifarm3dViewport(
            modifier = Modifier.fillMaxSize(),
            assetName = Digifarm3dMap.runtimeAsset,
            framing = FarmFraming.Home,
            worldActive = isActive,
            reduceMotion = reduceMotion,
            onReady = { view ->
                sceneView = view
                sceneIssue = null
            },
            onAssetError = { sceneIssue = it },
            onResidentTap = onResidentTap,
            onSceneSettled = { controller.notifySceneSettled() },
        )
        sceneIssue?.let { message ->
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 96.dp, start = 16.dp, end = 16.dp)
                    .background(SpaceBlack.copy(alpha = 0.86f))
                    .cyberFrame()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(R.string.ui_digifarm_3d_unavailable),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextPrimaryOnDark,
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondaryOnDark,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Cosmetic resident detail. The farm is not a management surface: no needs, activities, grouping or
 * transfers — just who this is and the two entry points that still make sense.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FarmResidentSheet(
    resident: FarmWorldResident,
    isFollowed: Boolean,
    onDismiss: () -> Unit,
    onOpenChat: (characterId: Long) -> Unit = {},
    onFollow: (renderId: String) -> Unit = {},
    onSetActive: (characterId: Long) -> Unit = {},
    onSwitchFromStorage: () -> Unit = {},
    onRemoveFromFarm: () -> Unit = {},
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDeepPurple,
        contentColor = TextPrimaryOnDark,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    resident.name,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    resident.attribute?.let {
                        Text(it, style = MaterialTheme.typography.labelLarge, color = VitalCyan)
                    }
                    if (resident.isActivePartner) {
                        Text(
                            stringResource(R.string.home_roster_active_partner),
                            style = MaterialTheme.typography.labelLarge,
                            color = TextSecondaryOnDark,
                        )
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(SurfaceStroke.copy(alpha = 0.7f)))
            VitalButton(
                onClick = {
                    onFollow(resident.renderId)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                style = VitalButtonStyle.PRIMARY,
            ) {
                Text(
                    stringResource(
                        if (isFollowed) R.string.home_roster_followed else R.string.home_roster_follow
                    )
                )
            }
            if (!resident.isActivePartner) {
                VitalButton(
                    onClick = {
                        onSetActive(resident.characterId)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    style = VitalButtonStyle.SECONDARY,
                ) {
                    Text(stringResource(R.string.home_resident_set_active))
                }
            }
            VitalButton(
                onClick = {
                    onDismiss()
                    onSwitchFromStorage()
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                style = VitalButtonStyle.SECONDARY,
            ) {
                Text(stringResource(R.string.home_resident_switch_storage))
            }
            if (!resident.isActivePartner) {
                TextButton(
                    onClick = {
                        onDismiss()
                        onRemoveFromFarm()
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.farm_resident_remove))
                }
            }
            TextButton(
                onClick = {
                    onDismiss()
                    onOpenChat(resident.characterId)
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.home_resident_chat))
            }
        }
    }
}

// ---- Diorama helpers -------------------------------------------------------------------------

private fun buildResidentFrames(resident: FarmWorldResident): ResidentFrames {
    val art = resident.artwork
    // Every pose the renderer can bind for a resident. Frames may repeat when the source artwork
    // lacks a pose (Dot sets substitute idle for walk); the extrusion builder dedupes identical
    // frames into one mesh, so a repeated pose costs no extra geometry.
    val poses = mapOf(
        "idle" to residentFrame(art.idle1, art),
        "idle2" to residentFrame(art.idle2, art),
        "walk" to residentFrame(art.walk1, art),
        "walk2" to residentFrame(art.walk2, art),
        "run" to residentFrame(art.run1, art),
        "run2" to residentFrame(art.run2, art),
        "sleep" to residentFrame(art.sleep, art),
        "train" to residentFrame(art.train1, art),
        "train2" to residentFrame(art.train2, art),
        "happy" to residentFrame(art.happy, art),
    )
    return ResidentFrames(
        id = resident.renderId,
        poses = poses,
        modelGlb = SpriteExtrusionGlb.build(poses),
        setKey = art.contentKey.toString(),
        bodyAspect = art.bodyAspect,
    )
}

private fun residentFrame(bytes: ByteArray, art: FarmArtwork): ResidentFrameImage {
    val argb = runCatching {
        BitmapData(bytes, art.width, art.height).createARGBIntArray()
    }.getOrDefault(IntArray(0))
    return ResidentFrameImage(argb = argb, width = art.width, height = art.height)
}

/**
 * Renderer hand-off. The farm never marks a resident selected: selection lives in the HUD sheet, so
 * every pose is drawn at the normal resident height.
 */
private fun FarmDioramaFrame.toResidentPoses(): List<ResidentPose> = poses.map { pose ->
    ResidentPose(
        id = pose.id,
        worldX = pose.x,
        worldZ = pose.z,
        activity = pose.activity.name,
        facingLeft = pose.facingLeft,
        selected = false,
        stepMillis = pose.stepMillis,
        running = pose.running,
        faceX = pose.lookAtX,
        faceZ = pose.lookAtZ,
    )
}
