package com.github.nacabaro.vbhelper.screens.tamerArena

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
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
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.battle.BattleAssetPaths
import com.github.nacabaro.vbhelper.battle.offline.core.BattleAttribute
import com.github.nacabaro.vbhelper.battle.offline.core.BattleStrategy
import com.github.nacabaro.vbhelper.battle.offline.tamers.*
import com.github.nacabaro.vbhelper.components.VitalButton
import com.github.nacabaro.vbhelper.components.VitalButtonStyle
import com.github.nacabaro.vbhelper.di.VBHelper
import com.github.nacabaro.vbhelper.utils.BitmapData
import com.github.nacabaro.vbhelper.utils.createARGBIntArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val bundledArtwork = LruCache<String, ImageBitmap>(48)

@Composable
internal fun ArenaSprite(species: ArenaSpecies?, size: Dp = 64.dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val image by produceState<ImageBitmap?>(null, species?.assetId, species?.cardCharacterId) {
        val selected = species ?: return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                if (selected.assetId != null) bundledArtwork.get(selected.assetId) ?: context.assets.open(
                    BattleAssetPaths.characterFrame(selected.assetId, 1)).use { input ->
                    BitmapFactory.decodeStream(input)?.asImageBitmap()?.also { bundledArtwork.put(selected.assetId, it) }
                } else selected.cardCharacterId?.let { id ->
                    (context as VBHelper).container.db.spriteDao().getForCharacter(id)?.let { art ->
                        val pixels = BitmapData(art.spriteIdle1, art.width, art.height).createARGBIntArray()
                        Bitmap.createBitmap(pixels, art.width, art.height, Bitmap.Config.ARGB_8888).asImageBitmap()
                    }
                }
            }.getOrNull()
        }
    }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        if (image != null) Image(requireNotNull(image), species?.name, Modifier.fillMaxSize(), filterQuality = FilterQuality.None)
        else Icon(Icons.Outlined.Pets, contentDescription = null, Modifier.size(size * 0.48f),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
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
