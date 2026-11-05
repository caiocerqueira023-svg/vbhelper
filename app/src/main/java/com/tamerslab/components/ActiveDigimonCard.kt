package com.tamerslab.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.tamerslab.R
import com.tamerslab.dtos.CharacterDtos
import com.tamerslab.utils.BitmapData

@Composable
fun ActiveDigimonCard(
    activeMon: CharacterDtos.CharacterWithSprites,
    cardIcon: BitmapData,
    modifier: Modifier = Modifier,
    multiplier: Int = 8,
    speechBubbleText: String? = null,
    transitionDirection: Int = 1,
    favoriteIndex: Int = -1,
    favoriteCount: Int = 0,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit,
    onFavoriteSwipe: (Int) -> Unit
) {
    val allowMotion = motionEnabled()
    val canCycleFavorites = favoriteIndex >= 0 && favoriteCount > 1
    val detailsLabel = stringResource(R.string.home_open_dex_details)
    val positionLabel = stringResource(
        R.string.home_favorite_position,
        favoriteIndex + 1,
        favoriteCount
    )

    val accessibilityModifier = if (canCycleFavorites) {
        // Position is still announced; the swipe/custom actions that switched the active partner are
        // gone with the gesture.
        Modifier.semantics { stateDescription = positionLabel }
    } else {
        Modifier
    }

    AnimatedContent(
        targetState = activeMon,
        contentKey = { it.id },
        contentAlignment = Alignment.Center,
        transitionSpec = {
            if (!allowMotion) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                val direction = if (transitionDirection >= 0) 1 else -1
                (
                    slideInVertically(
                        animationSpec = tween(240, easing = FastOutSlowInEasing),
                        initialOffsetY = { height -> direction * height / 2 }
                    ) + fadeIn(tween(150))
                ) togetherWith (
                    slideOutVertically(
                        animationSpec = tween(210, easing = FastOutSlowInEasing),
                        targetOffsetY = { height -> -direction * height / 2 }
                    ) + fadeOut(tween(130))
                )
            }
        },
        label = "favoriteDigimonTransition",
        modifier = modifier.then(accessibilityModifier)
    ) { character ->
        CharacterEntry(
            icon = BitmapData(
                bitmap = character.spriteIdle,
                width = character.spriteWidth,
                height = character.spriteHeight
            ),
            cardIcon = cardIcon,
            multiplier = multiplier,
            idleFrame2 = BitmapData(
                bitmap = character.spriteIdle2,
                width = character.spriteWidth,
                height = character.spriteHeight
            ),
            animationKey = character.id,
            speechBubbleText = speechBubbleText,
            vitalPoints = character.vitalPoints,
            iconDescription = character.nickname?.takeIf(String::isNotBlank) ?: character.speciesName
                ?: stringResource(R.string.widget_digimon_label),
            onClick = onClick,
            onLongClick = onLongClick,
            longClickLabel = detailsLabel,
            // Switching the active partner by scrolling over the card is retired: the sheet's own
            // controls own that decision now.
            onVerticalSwipe = null,
            modifier = Modifier.fillMaxSize()
        )
    }
}
