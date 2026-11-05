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
import com.tamerslab.farm.FARM_MAX_RESIDENTS
import com.tamerslab.farm.FarmSpriteFilter
import com.tamerslab.farm.FarmWorldPreferences
import com.tamerslab.farm.FarmWorldPreferencesSnapshot
import com.tamerslab.farm.farmWorldStore
import com.tamerslab.farm.map.Digifarm3dMap
import com.tamerslab.farm.map.FarmWorldBounds
import com.tamerslab.dtos.CharacterDtos
import com.tamerslab.rendering.sprite3d.ResidentFrameImage
import com.tamerslab.rendering.sprite3d.SpriteExtrusionGlb
import com.tamerslab.storage.data.StorageRepository
import com.tamerslab.species.BundledDotSpriteLibrary
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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The Digifarm is presentation only (owner revision, 2026-10-30).
 *
 * One home farm, no data model, no simulation: the active partner plus the explicitly curated farm
 * members wander the island as pure animation. The owner controls how many appear and whether the diorama may draw card/VB
 * artwork or Dot artwork.
 */
private const val ROTATION_BUCKET_MILLIS = 3_600_000L
private const val ROTATION_SEED_XOR = 0x5bd1e995

/**
 * Longest single position step handed to the renderer, in world units. The renderer interpolates a
 * step only while it stays under `MAX_INTERPOLATED_STEP_SQ` (0.35 units) and snaps anything longer;
 * keeping every step below that is what stops residents from teleporting across the island.
 */
private const val WALK_STEP_UNITS = 0.22f

/** Hard floor between two residents so the diorama never shows two sprites inside each other. */
private const val MIN_RESIDENT_GAP = 0.115f

@Composable
fun rememberFarmWorldController(): FarmWorldController {
    val context = LocalContext.current
    val app = context.applicationContext as TamersLabApplication
    val storageRepository = remember(app) { StorageRepository(app.container.db) }
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
 * Applies the artwork filter, then the owner's resident limit (the active partner always keeps a
 * place), then maps everything to renderer residents with their resolved artwork.
 */
internal suspend fun buildFarmRoster(
    candidates: List<CharacterDtos.CharacterWithSprites>,
    rotationBucket: Long,
    limit: Int,
    filter: FarmSpriteFilter,
    dotLibrary: BundledDotSpriteLibrary?,
): List<FarmWorldResident> {
    val resolved = candidates.mapNotNull { character ->
        val artwork = when (filter) {
            FarmSpriteFilter.MIXED, FarmSpriteFilter.VB_ONLY -> vbArtwork(character)
            FarmSpriteFilter.DOT_ONLY -> dotArtwork(character, dotLibrary)
        // The leader is never filtered off the island by an artwork preference: if the chosen set has
        // nothing for it, fall back to whichever artwork exists rather than dropping it.
        } ?: if (character.active) {
            vbArtwork(character) ?: dotArtwork(character, dotLibrary)
        } else {
            null
        }
        artwork?.let { character to it }
    }
    val capped = limit.coerceIn(1, FARM_MAX_RESIDENTS)
    val selected = if (resolved.size <= capped) {
        resolved
    } else {
        val keepActive = resolved.firstOrNull { it.first.active }
        val pool = if (keepActive != null) resolved.filterNot { it === keepActive } else resolved
        val random = Random((rotationBucket xor ROTATION_SEED_XOR.toLong()).toInt())
        val room = capped - (if (keepActive != null) 1 else 0)
        listOfNotNull(keepActive) + pool.shuffled(random).take(room.coerceAtLeast(0))
    }
    return selected.map { (character, artwork) ->
        FarmWorldResident(
            renderId = "farm-${character.id}",
            characterId = character.id,
            name = character.nickname?.takeIf(String::isNotBlank)
                ?: character.speciesName?.takeIf(String::isNotBlank)
                ?: "Digimon",
            attribute = character.attribute.name,
            isActivePartner = character.active,
            artwork = artwork,
        )
    }
}

/** Card/VB artwork: the four natively stored frames are reused across the pose families. */
private fun vbArtwork(character: CharacterDtos.CharacterWithSprites): FarmArtwork? {
    if (character.spriteIdle.isEmpty()) return null
    return FarmArtwork(
        idle1 = character.spriteIdle,
        idle2 = character.spriteIdle2,
        walk1 = character.spriteRun1,
        walk2 = character.spriteRun2,
        train1 = character.spriteRun1,
        train2 = character.spriteRun2,
        happy = character.spriteIdle,
        sleep = character.spriteIdle2,
        width = character.spriteWidth,
        height = character.spriteHeight,
    )
}

/** Dot artwork: only available when the species has a bundled Dot sprite set installed. */
private suspend fun dotArtwork(
    character: CharacterDtos.CharacterWithSprites,
    dotLibrary: BundledDotSpriteLibrary?,
): FarmArtwork? {
    val library = dotLibrary ?: return null
    val speciesName = character.speciesName?.takeIf(String::isNotBlank) ?: return null
    return runCatching {
        val catalog = library.catalog()
        val species = catalog.resolve(speciesName) ?: return@runCatching null
        if (!catalog.isExpandedReady(species.id)) return@runCatching null
        val setId = species.dotSprites.defaultSetId ?: return@runCatching null
        val sprite = library.sprite(setId)
        FarmArtwork(
            idle1 = sprite.spriteIdle1,
            idle2 = sprite.spriteIdle2,
            walk1 = sprite.spriteWalk1,
            walk2 = sprite.spriteWalk2,
            train1 = sprite.spriteTrain1,
            train2 = sprite.spriteTrain2,
            happy = sprite.spriteHappy,
            sleep = sprite.spriteSleep,
            width = sprite.width,
            height = sprite.height,
        )
    }.getOrNull()
}

/**
 * Full-bleed 3D diorama. Chrome, dock and partner sheet are drawn above by `FarmHomeScreen`; the
 * surface pauses (never unmounts) when the sheet covers it or a place is open.
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
        roster.joinToString(",") { it.renderId + ":" + it.artwork.idle1.contentHashCode() }
    }
    val bounds = remember { Digifarm3dMap.defaultPlayableBounds }

    var sceneView by remember { mutableStateOf<Digifarm3dSceneView?>(null) }
    var sceneIssue by remember { mutableStateOf<String?>(null) }
    var framesReady by remember(rosterKey) { mutableStateOf(false) }
    val actors = remember(rosterKey) { seedPoses(roster, bounds).toMutableList() }

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
        if (framesReady) view.updateResidentPoses(actors.toList())
    }

    // Natural-looking life, not a simulation. Every actor runs its own loop with its own rhythm, so
    // nothing moves in lockstep: some wander, some nap, some train, and pairs walk up to each other,
    // face, celebrate briefly and drift apart. Nothing is persisted and no game state is touched.
    LaunchedEffect(rosterKey, sceneView, reduceMotion, framesReady) {
        val view = sceneView ?: return@LaunchedEffect
        if (!framesReady) return@LaunchedEffect
        if (reduceMotion) {
            view.updateResidentPoses(actors.toList())
            return@LaunchedEffect
        }
        coroutineScope {
            actors.indices.forEach { index ->
                launch { runDioramaActor(index, actors, bounds, view) }
            }
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
    val poses = mapOf(
        "idle" to residentFrame(art.idle1, art),
        "idle2" to residentFrame(art.idle2, art),
        "sleep" to residentFrame(art.sleep, art),
        "train" to residentFrame(art.train1, art),
        "train2" to residentFrame(art.train2, art),
        "happy" to residentFrame(art.happy, art),
        "happy2" to residentFrame(art.happy, art),
        "walk" to residentFrame(art.walk1, art),
        "walk2" to residentFrame(art.walk2, art),
    )
    return ResidentFrames(
        id = resident.renderId,
        poses = poses,
        modelGlb = SpriteExtrusionGlb.build(poses),
        setKey = "${art.width}x${art.height}:${art.idle1.contentHashCode()}",
    )
}

private fun residentFrame(bytes: ByteArray, art: FarmArtwork): ResidentFrameImage {
    val argb = runCatching {
        BitmapData(bytes, art.width, art.height).createARGBIntArray()
    }.getOrDefault(IntArray(0))
    return ResidentFrameImage(argb = argb, width = art.width, height = art.height)
}

private fun seedPoses(
    roster: List<FarmWorldResident>,
    bounds: FarmWorldBounds,
): List<ResidentPose> {
    val poses = roster.mapIndexed { index, resident ->
        val random = Random(resident.renderId.hashCode())
        val angle = (index.toFloat() / roster.size.coerceAtLeast(1)) * 2f * PI.toFloat() +
            random.nextFloat() * 0.4f
        val radius = 0.22f + random.nextFloat() * 0.18f
        val opening = when (index % 6) {
            0, 4 -> "IDLE"
            1 -> "PLAY"
            2 -> "REST"
            3 -> "TRAIN"
            else -> "IDLE"
        }
        ResidentPose(
            id = resident.renderId,
            worldX = (cos(angle) * radius).coerceIn(bounds.minX + 0.06f, bounds.maxX - 0.06f),
            worldZ = (sin(angle) * radius).coerceIn(bounds.minZ + 0.06f, bounds.maxZ - 0.06f),
            activity = opening,
            facingLeft = random.nextBoolean(),
            selected = false,
        )
    }.toMutableList()
    // Relax the opening tableau so nobody starts inside a neighbour.
    repeat(6) {
        for (index in poses.indices) {
            val (x, z) = separate(index, poses[index].worldX, poses[index].worldZ, poses, bounds)
            poses[index] = poses[index].copy(worldX = x, worldZ = z)
        }
    }
    return poses
}

/**
 * One resident's own life loop. Each actor owns a private random source and its own timing, so no two
 * residents ever move or rest in lockstep: a wanderer crosses the island while a neighbour naps, then
 * naps itself. Social beats are a short approach, a shared celebration pose, and a drift apart.
 */
private suspend fun runDioramaActor(
    index: Int,
    actors: MutableList<ResidentPose>,
    bounds: FarmWorldBounds,
    view: Digifarm3dSceneView,
) {
    val random = Random(actors[index].id.hashCode() * 31 + index)
    delay(300L + random.nextLong(4_000L))
    while (true) {
        val roll = random.nextInt(100)
        when {
            roll < 26 -> wanderStep(index, actors, bounds, view, random)
            roll < 42 -> socialBeat(index, actors, bounds, view, random)
            roll < 54 -> holdActivity(index, actors, view, "REST", 7_000L..15_000L, random)
            roll < 68 -> holdActivity(index, actors, view, "TRAIN", 3_000L..6_500L, random)
            roll < 80 -> holdActivity(index, actors, view, "PLAY", 2_500L..5_000L, random)
            else -> holdActivity(index, actors, view, "IDLE", 3_500L..9_000L, random)
        }
    }
}

private suspend fun wanderStep(
    index: Int,
    actors: MutableList<ResidentPose>,
    bounds: FarmWorldBounds,
    view: Digifarm3dSceneView,
    random: Random,
) {
    val target = freeSpot(actors, index, bounds, random) ?: return
    var guard = 0
    while (guard < 8) {
        val reached = stepToward(
            index = index,
            actors = actors,
            targetX = target.first,
            targetZ = target.second,
            activity = "EXPLORE",
            view = view,
            bounds = bounds,
            maxStep = WALK_STEP_UNITS,
        )
        if (reached) break
        delay(1_150L + random.nextLong(450L))
        guard++
    }
    delay(300L + random.nextLong(1_600L))
    if (random.nextInt(100) < 35) {
        holdActivity(index, actors, view, "IDLE", 1_800L..4_200L, random)
    }
}

/**
 * A short social beat: walk to within a talking distance of the nearest neighbour, both face each
 * other and celebrate, then the initiator wanders off. Purely animated; the neighbour reacts by
 * taking the shared pose for the duration.
 */
private suspend fun socialBeat(
    index: Int,
    actors: MutableList<ResidentPose>,
    bounds: FarmWorldBounds,
    view: Digifarm3dSceneView,
    random: Random,
) {
    if (actors.size < 2) return
    val self = actors[index]
    val partnerIndex = actors.indices
        .filter { it != index }
        .minByOrNull { sqDistance(actors[it], self) } ?: return
    var partner = actors[partnerIndex]
    var distance = sqrt(sqDistance(partner, self))
    var guard = 0
    while (distance > 0.14f && guard < 4) {
        stepToward(
            index = index,
            actors = actors,
            targetX = partner.worldX,
            targetZ = partner.worldZ,
            activity = "EXPLORE",
            view = view,
            bounds = bounds,
            maxStep = 0.16f,
        )
        delay(1_150L + random.nextLong(350L))
        partner = actors[partnerIndex]
        distance = sqrt(sqDistance(partner, actors[index]))
        guard++
    }
    // Facing each other falls out of the approach heading; the celebration pose makes it read.
    setActivity(index, actors, "PLAY", view)
    setActivity(partnerIndex, actors, "PLAY", view)
    delay(2_400L + random.nextLong(2_600L))
    val away = freeSpot(actors, index, bounds, random)
    if (away != null) {
        var steps = 0
        while (steps < 4) {
            val reached = stepToward(
                index = index,
                actors = actors,
                targetX = away.first,
                targetZ = away.second,
                activity = "EXPLORE",
                view = view,
                bounds = bounds,
            )
            if (reached) break
            delay(1_150L + random.nextLong(400L))
            steps++
        }
    }
}

private suspend fun holdActivity(
    index: Int,
    actors: MutableList<ResidentPose>,
    view: Digifarm3dSceneView,
    activity: String,
    duration: LongRange,
    random: Random,
) {
    setActivity(index, actors, activity, view)
    delay(duration.first + random.nextLong(duration.last - duration.first + 1))
}

private fun setActivity(
    index: Int,
    actors: MutableList<ResidentPose>,
    activity: String,
    view: Digifarm3dSceneView,
) {
    val current = actors[index]
    if (current.activity == activity) return
    actors[index] = current.copy(activity = activity)
    view.updateResidentPoses(actors.toList())
}

/** One capped step toward a target; true when the remaining distance is negligible. */
private fun stepToward(
    index: Int,
    actors: MutableList<ResidentPose>,
    targetX: Float,
    targetZ: Float,
    activity: String,
    view: Digifarm3dSceneView,
    bounds: FarmWorldBounds,
    maxStep: Float = WALK_STEP_UNITS,
): Boolean {
    val current = actors[index]
    val dx = targetX - current.worldX
    val dz = targetZ - current.worldZ
    val distance = sqrt(dx * dx + dz * dz)
    if (distance <= 0.05f) return true
    val step = distance.coerceAtMost(maxStep)
    moveResident(
        index = index,
        actors = actors,
        x = current.worldX + dx / distance * step,
        z = current.worldZ + dz / distance * step,
        activity = activity,
        view = view,
        bounds = bounds,
    )
    return distance - step <= 0.05f
}

private fun moveResident(
    index: Int,
    actors: MutableList<ResidentPose>,
    x: Float,
    z: Float,
    activity: String,
    view: Digifarm3dSceneView,
    bounds: FarmWorldBounds,
) {
    val current = actors[index]
    // Collision first: pushed out of any neighbour, then clamped to the playable island.
    val (separatedX, separatedZ) = separate(index, x, z, actors, bounds)
    actors[index] = current.copy(
        worldX = separatedX,
        worldZ = separatedZ,
        activity = activity,
        facingLeft = separatedX < current.worldX,
    )
    view.updateResidentPoses(actors.toList())
}

/**
 * Pushes a proposed position out of every neighbour that is closer than [MIN_RESIDENT_GAP].
 * Three relaxation passes are plenty for twelve residents; the result is clamped to the island.
 */
private fun separate(
    index: Int,
    x: Float,
    z: Float,
    actors: List<ResidentPose>,
    bounds: FarmWorldBounds,
): Pair<Float, Float> {
    var px = x
    var pz = z
    repeat(3) {
        var pushed = false
        actors.forEachIndexed { otherIndex, other ->
            if (otherIndex == index) return@forEachIndexed
            val dx = px - other.worldX
            val dz = pz - other.worldZ
            val distanceSquared = dx * dx + dz * dz
            if (distanceSquared < MIN_RESIDENT_GAP * MIN_RESIDENT_GAP) {
                val distance = sqrt(distanceSquared)
                val nx = if (distance < 1e-4f) 1f else dx / distance
                val nz = if (distance < 1e-4f) 0f else dz / distance
                val push = MIN_RESIDENT_GAP - distance + 0.002f
                px += nx * push
                pz += nz * push
                pushed = true
            }
        }
        if (!pushed) return@repeat
    }
    return px.coerceIn(bounds.minX + 0.05f, bounds.maxX - 0.05f) to
        pz.coerceIn(bounds.minZ + 0.05f, bounds.maxZ - 0.05f)
}

/** Picks a spot away from the others so residents spread out instead of clustering in a blob. */
private fun freeSpot(
    actors: List<ResidentPose>,
    index: Int,
    bounds: FarmWorldBounds,
    random: Random,
): Pair<Float, Float>? {
    if (actors.isEmpty()) return null
    var best: Pair<Float, Float>? = null
    var bestScore = -1f
    repeat(6) {
        val x = (bounds.minX + 0.08f) +
            random.nextFloat() * ((bounds.maxX - 0.08f) - (bounds.minX + 0.08f))
        val z = (bounds.minZ + 0.08f) +
            random.nextFloat() * ((bounds.maxZ - 0.08f) - (bounds.minZ + 0.08f))
        val nearest = actors.indices
            .filter { it != index }
            .minOfOrNull { other ->
                val dx = actors[other].worldX - x
                val dz = actors[other].worldZ - z
                dx * dx + dz * dz
            } ?: Float.MAX_VALUE
        if (nearest > bestScore) {
            bestScore = nearest
            best = x to z
        }
    }
    return best
}

private fun sqDistance(a: ResidentPose, b: ResidentPose): Float {
    val dx = a.worldX - b.worldX
    val dz = a.worldZ - b.worldZ
    return dx * dx + dz * dz
}
