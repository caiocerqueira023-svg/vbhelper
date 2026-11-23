package com.tamerslab.battle.ui.arena

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tamerslab.R
import com.tamerslab.battle.BattleAssetPaths
import com.tamerslab.battle.domain.BattleAttribute
import com.tamerslab.battle.domain.BattleStrategy
import com.tamerslab.battle.offline.tamers.*
import com.tamerslab.components.VitalButton
import com.tamerslab.components.VitalButtonStyle
import com.tamerslab.components.motionEnabled
import com.tamerslab.di.TamersLabApplication
import com.tamerslab.ui.SpritePreviewPolicy
import com.tamerslab.species.SpeciesCustomizationRepository
import com.tamerslab.domain.characters.Sprite
import com.tamerslab.utils.BitmapData
import com.tamerslab.utils.createARGBIntArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

private val bundledArtwork = object : LruCache<String, List<ImageBitmap>>(SpritePreviewPolicy.CACHE_BYTES) {
    override fun sizeOf(key: String, value: List<ImageBitmap>): Int =
        value.sumOf { it.asAndroidBitmap().allocationByteCount }
}

@Composable
internal fun ArenaSprite(species: ArenaSpecies?, size: Dp = 64.dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val allowMotion = motionEnabled()
    val application = context as? TamersLabApplication
    val mode by remember(application) {
        application?.container?.spritePreferenceRepository?.preference ?: kotlinx.coroutines.flow.flowOf(null)
    }.collectAsStateWithLifecycle(initialValue = null)
    val appearanceKey = com.tamerslab.species.SpeciesCustomizationRepository.key(species?.name.orEmpty())
    val preferredId by remember(application, appearanceKey) {
        application?.container?.db?.speciesCustomizationDao()?.observeSpriteId(appearanceKey)
            ?: kotlinx.coroutines.flow.flowOf(null)
    }.collectAsStateWithLifecycle(null)
    val frames by key(species?.name, species?.assetId, species?.cardCharacterId, preferredId, mode) {
        produceState<List<ImageBitmap>>(emptyList()) {
            val selected = species ?: return@produceState
            value = withContext(Dispatchers.IO) {
                try {
                    val container = (context as? TamersLabApplication)?.container
                    // Cosmetic priority with mutual fallback; neither source limits gameplay.
                    val resolved = container?.let {
                        com.tamerslab.species.SpeciesAppearanceRepository(
                            it.db, it.dotSpriteLibrary) { it.spritePreferenceRepository.current }
                            .resolve(selected.name, selected.assetId)
                    }
                    val frames = when {
                        resolved?.sprite != null -> arenaIdleFrames(resolved.sprite)
                        resolved?.assetId != null -> loadBundledArenaArtwork(context, resolved.assetId)
                        selected.assetId != null -> loadBundledArenaArtwork(context, selected.assetId)
                        else -> selected.cardCharacterId?.let { id ->
                            (context as TamersLabApplication).container.db.spriteDao().getForCharacter(id)?.let(::arenaIdleFrames)
                        }.orEmpty()
                    }
                    // Shipped frames can miss/corrupt; never leave a resolved species without its Dot art.
                    frames.ifEmpty {
                        val library = container?.dotSpriteLibrary
                        val dot = library?.catalogBlocking()?.resolve(selected.name)
                            ?.takeIf { it.dotSprites.setIds.isNotEmpty() }?.dotSprites?.defaultSetId
                        if (library == null || dot == null) emptyList()
                        else runCatching { library.sprite(dot) }.getOrNull()?.let(::arenaIdleFrames).orEmpty()
                    }
                } catch (failure: Exception) {
                    if (failure is CancellationException) throw failure
                    emptyList()
                }
            }
        }
    }
    var frameIndex by remember(species?.name, species?.assetId, species?.cardCharacterId, preferredId, mode) { mutableIntStateOf(0) }
    val timing = remember(species?.name, species?.assetId, species?.cardCharacterId, preferredId, mode) {
        val identity = (mode?.name ?: "") + (preferredId?.toString() ?: species?.assetId ?: "imported:${species?.cardCharacterId}") + ":" + species?.name
        val seed = (identity.hashCode().toLong() * 1_103_515_245L + 12_345L) and 0x7fff_ffffL
        val interval = 550L + seed % 301L
        (seed / 997L) % interval to interval
    }
    LaunchedEffect(frames, allowMotion) {
        frameIndex = 0
        if (allowMotion && frames.size > 1) {
            delay(timing.first)
            while (isActive) {
                delay(timing.second)
                frameIndex = (frameIndex + 1) % frames.size
            }
        }
    }
    val image = frames.getOrNull(if (allowMotion) frameIndex else 0)
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        if (image != null) Image(requireNotNull(image), species?.name, Modifier.fillMaxSize(), filterQuality = FilterQuality.None)
        else Icon(Icons.Outlined.Pets, contentDescription = null, Modifier.size(size * 0.48f),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun arenaIdleFrames(art: Sprite): List<ImageBitmap> {
    val buffers = if (art.spriteIdle1.contentEquals(art.spriteIdle2)) listOf(art.spriteIdle1)
        else listOf(art.spriteIdle1, art.spriteIdle2)
    return buffers.filter { SpritePreviewPolicy.validFrame(art.width, art.height, it.size) }.map { bytes ->
        Bitmap.createBitmap(BitmapData(bytes, art.width, art.height).createARGBIntArray(),
            art.width, art.height, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
}

private fun loadBundledArenaArtwork(context: android.content.Context, assetId: String): List<ImageBitmap> {
    bundledArtwork.get(assetId)?.let { return it }
    val frames = listOf(1, 2).mapNotNull { frame ->
        runCatching {
            context.assets.open(BattleAssetPaths.characterFrame(assetId, frame)).use { input ->
                BitmapFactory.decodeStream(input)?.asImageBitmap()
            }
        }.getOrNull()
    }
    if (frames.isNotEmpty()) bundledArtwork.put(assetId, frames)
    return frames
}

@Composable
internal fun ArenaAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    primary: Boolean = true, icon: ImageVector? = null) {
    VitalButton(onClick, modifier.heightIn(min = 48.dp), enabled,
        style = if (primary) VitalButtonStyle.PRIMARY else VitalButtonStyle.SECONDARY) {
        if (icon != null) { Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun ArenaBadge(label: String, highlighted: Boolean = false, modifier: Modifier = Modifier) {
    Surface(modifier, color = if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.small) {
        Text(label, Modifier.padding(horizontal = 8.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
internal fun ArenaHeading(title: String, modifier: Modifier = Modifier) {
    Text(title, modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
internal fun ArenaStat(label: String, value: String, modifier: Modifier = Modifier, accent: Boolean = false) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
            color = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun arenaStageName(stage: Int): String = stringArrayResource(R.array.arena_stages)[stage.coerceIn(0, 5)]

@Composable
internal fun arenaDifficultyName(difficulty: ArenaDifficulty): String = stringResource(when (difficulty) {
    ArenaDifficulty.CASUAL -> R.string.arena_casual
    ArenaDifficulty.NORMAL -> R.string.arena_normal
    ArenaDifficulty.EXPERT -> R.string.arena_expert
})

@Composable
internal fun arenaStrategyName(strategy: BattleStrategy): String = stringResource(when (strategy) {
    BattleStrategy.AGGRESSIVE -> R.string.ui_battle_strategy_aggressive
    BattleStrategy.BALANCED -> R.string.ui_battle_strategy_balanced
    BattleStrategy.CONSERVATIVE -> R.string.ui_battle_strategy_conservative
    BattleStrategy.DEFENSIVE -> R.string.ui_battle_strategy_defensive
    BattleStrategy.RANGED -> R.string.ui_battle_strategy_ranged
    BattleStrategy.SUPPORT -> R.string.ui_battle_strategy_support
})

