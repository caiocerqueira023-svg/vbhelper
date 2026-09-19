package com.github.nacabaro.vbhelper.battle

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.github.nacabaro.vbhelper.dtos.CharacterDtos
import com.github.nacabaro.vbhelper.utils.BitmapData
import com.github.nacabaro.vbhelper.utils.getBitmap
import kotlinx.coroutines.delay

/**
 * Renders the sprite bytes already stored from a user's DiM/BEM.
 * This is the preferred source for the local player and offline battles.
 */
@Composable
fun DatabaseAnimatedSpriteImage(
    character: CharacterDtos.CharacterWithSprites,
    animationType: DigimonAnimationType = DigimonAnimationType.IDLE,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    reloadMappings: Boolean = false,
    animationOffset: Long = 0L
) {
    val frames = remember(character.id, animationType) {
        when (animationType) {
            DigimonAnimationType.IDLE,
            DigimonAnimationType.IDLE2 -> listOf(character.spriteIdle, character.spriteIdle2)
            DigimonAnimationType.WALK,
            DigimonAnimationType.WALK2,
            DigimonAnimationType.RUN,
            DigimonAnimationType.RUN2,
            DigimonAnimationType.ATTACK -> listOf(character.spriteRun1, character.spriteRun2)
            DigimonAnimationType.SLEEP -> listOf(character.spriteIdle2)
            else -> listOf(character.spriteIdle, character.spriteIdle2)
        }.filter { it.isNotEmpty() }
    }

    if (frames.isEmpty() || character.spriteWidth <= 0 || character.spriteHeight <= 0) return

    var frameIndex by remember(character.id, animationType) {
        mutableStateOf(if (animationOffset > 0L) 1 % frames.size else 0)
    }

    LaunchedEffect(character.id, animationType, frames.size) {
        var index = frameIndex
        while (true) {
            delay(if (animationType == DigimonAnimationType.IDLE) 700L else 180L)
            index = (index + 1) % frames.size
            frameIndex = index
        }
    }

    val bitmap = remember(character.id, animationType, frameIndex) {
        runCatching {
            BitmapData(
                bitmap = frames[frameIndex],
                width = character.spriteWidth,
                height = character.spriteHeight
            ).getBitmap()
        }.getOrNull()
    }

    bitmap?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = character.speciesName ?: character.nickname ?: "Stored Digimon",
            modifier = modifier,
            contentScale = contentScale
        )
    }
}

@Composable
fun BattleCharacterImage(
    characterId: String,
    databaseCharacter: CharacterDtos.CharacterWithSprites?,
    animationType: DigimonAnimationType,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    reloadMappings: Boolean = false,
    animationOffset: Long = 0L
) {
    if (databaseCharacter != null) {
        DatabaseAnimatedSpriteImage(
            character = databaseCharacter,
            animationType = animationType,
            modifier = modifier,
            contentScale = contentScale,
            reloadMappings = reloadMappings,
            animationOffset = animationOffset
        )
    } else {
        AnimatedSpriteImage(
            characterId = characterId,
            animationType = animationType,
            modifier = modifier,
            contentScale = contentScale,
            reloadMappings = reloadMappings,
            animationOffset = animationOffset
        )
    }
}
