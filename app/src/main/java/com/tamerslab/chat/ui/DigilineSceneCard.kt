package com.tamerslab.chat.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tamerslab.R
import com.tamerslab.components.VitalButton
import com.tamerslab.digiline.gateway.DigilineScene
import com.tamerslab.ui.theme.SurfaceElevatedPurple
import com.tamerslab.ui.theme.SurfaceStroke
import com.tamerslab.ui.theme.TextPrimaryOnDark
import com.tamerslab.ui.theme.TextSecondaryOnDark
import com.tamerslab.ui.theme.VitalCyan

/**
 * One authored conversation scene, stripped to what the player needs to act:
 * the current line, the Digimon's reaction to the previous answer, an optional price, and the
 * authored answers. Titles, phase/tier badges and rapport-style readings are deliberately absent —
 * the conversation itself carries that context.
 */
@Composable
fun DigilineSceneCard(
    scene: DigilineScene,
    enabled: Boolean,
    onOptionSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    maxHeight: Dp = 380.dp,
) {
    val contextText = digilineContextText(scene.contextKey)
    val outcomeText = digilineOutcomeText(scene.outcomeKey)
    val scrollState = rememberScrollState()

    Surface(
        color = SurfaceElevatedPurple,
        shape = CutCornerShape(8.dp),
        border = BorderStroke(1.dp, SurfaceStroke),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            // Polite live region: TalkBack announces each new scene line/answer without stealing focus.
            .semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(scrollState)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            contextText?.let {
                Text(text = it, style = MaterialTheme.typography.labelMedium, color = TextSecondaryOnDark)
            }
            // The reaction to the previous answer sits above the new prompt, so a choice is
            // acknowledged before the next question arrives.
            scene.reactionLine?.takeIf { it.isNotBlank() }?.let { reaction ->
                Text(
                    text = reaction,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondaryOnDark
                )
            }
            scene.lines.filter { it.isNotBlank() }.forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimaryOnDark
                )
            }
            scene.demandBits?.let { bits ->
                Text(
                    text = stringResource(R.string.digiline_demand_bits, bits.toString()),
                    style = MaterialTheme.typography.labelMedium,
                    color = VitalCyan
                )
            }
            outcomeText?.let {
                Text(text = it, style = MaterialTheme.typography.labelMedium, color = VitalCyan)
            }
            scene.options.forEach { option ->
                VitalButton(
                    onClick = { onOptionSelected(option.id) },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(option.text)
                }
            }
            if (scene.canDismiss || scene.syncLabel != null) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    scene.syncLabel?.let { label ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondaryOnDark
                        )
                    }
                    if (scene.canDismiss) {
                        TextButton(
                            onClick = onDismiss,
                            enabled = enabled,
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text(
                                stringResource(
                                    when (scene.kind) {
                                        DigilineScene.Kind.REACTION -> R.string.digiline_continue
                                        DigilineScene.Kind.NEGOTIATION,
                                        DigilineScene.Kind.PERSUASION -> R.string.digiline_leave_for_now
                                        else -> R.string.digiline_scene_later
                                    }
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun digilineContextText(key: String?): String? = when (key) {
    "wild_new" -> stringResource(R.string.digiline_context_wild_new)
    "wild_resume" -> stringResource(R.string.digiline_context_wild_resume)
    "persuasion_new" -> stringResource(R.string.digiline_context_persuasion_new)
    "persuasion_resume" -> stringResource(R.string.digiline_context_persuasion_resume)
    "owned_question" -> stringResource(R.string.digiline_context_owned_question)
    "owned_quiz" -> stringResource(R.string.digiline_context_owned_quiz)
    else -> null
}

@Composable
private fun digilineOutcomeText(key: String?): String? = when (key) {
    // Only quiz results need a mechanical verdict line; authored dialogue already carries the
    // meaning of a negotiation outcome, and the app feedback/toast covers the consequence.
    "QUIZ_CORRECT" -> stringResource(R.string.digiline_outcome_quiz_correct)
    "QUIZ_WRONG" -> stringResource(R.string.digiline_outcome_quiz_wrong)
    else -> null
}
