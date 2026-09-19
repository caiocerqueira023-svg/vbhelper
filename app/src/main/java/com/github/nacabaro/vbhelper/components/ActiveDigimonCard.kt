package com.github.nacabaro.vbhelper.components

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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.dtos.CharacterDtos
import com.github.nacabaro.vbhelper.ui.theme.SurfaceHighlightPurple
import com.github.nacabaro.vbhelper.ui.theme.VitalCyan
import com.github.nacabaro.vbhelper.utils.BitmapData

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
    val nextLabel = stringResource(R.string.home_next_favorite)
    val previousLabel = stringResource(R.string.home_previous_favorite)
    val detailsLabel = stringResource(R.string.home_open_dex_details)
    val positionLabel = stringResource(
        R.string.home_favorite_position,
        favoriteIndex + 1,
        favoriteCount
    )

    val accessibilityModifier = if (canCycleFavorites) {
        Modifier.semantics {
            stateDescription = positionLabel
            customActions = listOf(
                CustomAccessibilityAction(nextLabel) {
                    onFavoriteSwipe(1)
                    true
                },
                CustomAccessibilityAction(previousLabel) {
                    onFavoriteSwipe(-1)
                    true
                }
            )
        }
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
        Box(modifier = Modifier.fillMaxSize()) {
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
                onClick = onClick,
                onLongClick = onLongClick,
                longClickLabel = detailsLabel,
                onVerticalSwipe = if (canCycleFavorites) onFavoriteSwipe else null,
                modifier = Modifier.fillMaxSize()
            )

            if (canCycleFavorites) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 12.dp)
                        .background(
                            color = SurfaceHighlightPurple.copy(alpha = 0.9f),
                            shape = CutCornerShape(6.dp)
                        )
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = null,
                        tint = VitalCyan
                    )
                    Text(
                        text = "${favoriteIndex + 1}/$favoriteCount",
                        style = MaterialTheme.typography.labelSmall,
                        color = VitalCyan
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = VitalCyan
                    )
                }
            }
        }
    }
}
