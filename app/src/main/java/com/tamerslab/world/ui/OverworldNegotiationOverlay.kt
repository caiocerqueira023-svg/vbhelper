package com.tamerslab.world.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tamerslab.R
import com.tamerslab.chat.ui.DigilineSceneCard
import com.tamerslab.components.hudInk
import com.tamerslab.digiline.gateway.DigilineScene
import com.tamerslab.ui.theme.SpaceBlack
import com.tamerslab.ui.theme.StatusRed
import com.tamerslab.ui.theme.SurfaceElevatedPurple
import com.tamerslab.ui.theme.SurfaceStroke
import com.tamerslab.ui.theme.TextPrimaryOnDark
import com.tamerslab.ui.theme.TextSecondaryOnDark
import com.tamerslab.ui.theme.VitalCyan

/** Target of an in-world conversation (negotiation happens over the Radar, not in DigiLine). */
data class OverworldConversation(
    val individualId: String,
    val cardCharacterId: Long,
    val displayName: String,
    val privateChatId: String? = null,
)

/**
 * In-world conversation presentation: one floating name line and the authored scene over the live
 * Radar world. The world and the Digimon stay visible; no extra headers, badges or duplicate
 * transcript panels sit between the player and the current exchange.
 */
@Composable
fun OverworldNegotiationOverlay(
    conversation: OverworldConversation,
    scene: DigilineScene?,
    busy: Boolean,
    error: String?,
    onChoose: (String) -> Unit,
    onContinue: () -> Unit,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val reduceMotion = !com.tamerslab.components.motionEnabled()
    val screenHeight = LocalConfiguration.current.screenHeightDp
    val cardMaxHeight = (screenHeight * 0.42f).coerceIn(240f, 380f).dp

    Box(
        modifier = modifier
            .fillMaxSize()
            // Swallow taps meant for the Radar underneath; the card's own controls still receive theirs.
            .clickable(interactionSource = interactionSource, indication = null) {}
            .background(
                Brush.verticalGradient(
                    0.55f to Color.Transparent,
                    1f to SpaceBlack.copy(alpha = 0.86f),
                )
            )
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = conversation.displayName,
                    style = MaterialTheme.typography.titleMedium.hudInk(),
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimaryOnDark,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() }
                )
                if (busy && scene == null && error == null) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = VitalCyan,
                        strokeWidth = 2.dp
                    )
                }
                // While a scene card is visible its own exit covers this; the header key is the way
                // out of loading/error states only.
                if (scene == null) {
                    TextButton(onClick = onContinue, enabled = !busy) {
                        Text(stringResource(R.string.overworld_end_talk), color = TextSecondaryOnDark)
                    }
                }
            }

            when {
                error != null -> Surface(
                    color = SurfaceElevatedPurple,
                    shape = CutCornerShape(8.dp),
                    border = BorderStroke(1.dp, SurfaceStroke),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusRed
                        )
                        Row(
                            modifier = Modifier.align(Alignment.End),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (onRetry != null) {
                                TextButton(onClick = onRetry, enabled = !busy) {
                                    Text(stringResource(R.string.digiline_retry), color = VitalCyan)
                                }
                            }
                            TextButton(onClick = onContinue) {
                                Text(stringResource(R.string.digiline_continue), color = VitalCyan)
                            }
                        }
                    }
                }

                scene != null -> AnimatedContent(
                    targetState = scene,
                    transitionSpec = {
                        if (reduceMotion) {
                            EnterTransition.None togetherWith ExitTransition.None
                        } else {
                            (fadeIn(tween(180)) + slideInVertically(tween(180)) { height -> height / 12 })
                                .togetherWith(fadeOut(tween(120)))
                        }
                    },
                    label = "digilineScene",
                ) { currentScene ->
                    DigilineSceneCard(
                        scene = currentScene,
                        enabled = !busy,
                        onOptionSelected = onChoose,
                        onDismiss = onContinue,
                        maxHeight = cardMaxHeight,
                    )
                }
            }
        }
    }
}
