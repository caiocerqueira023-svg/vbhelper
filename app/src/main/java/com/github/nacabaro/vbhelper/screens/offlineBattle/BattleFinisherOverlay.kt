package com.github.nacabaro.vbhelper.screens.offlineBattle

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.battle.offline.core.BattleFinisherKind
import com.github.nacabaro.vbhelper.battle.offline.core.BattleFinisherPhase
import com.github.nacabaro.vbhelper.battle.offline.core.BattleFinisherSnapshot
import com.github.nacabaro.vbhelper.ui.theme.SceneTextShadow
import com.github.nacabaro.vbhelper.ui.theme.SurfaceStroke
import com.github.nacabaro.vbhelper.ui.theme.TextPrimaryOnDark
import com.github.nacabaro.vbhelper.ui.theme.TextSecondaryOnDark
import com.github.nacabaro.vbhelper.ui.theme.VitalCyan

/** Resolve the equipped result slot, never another lead's art or the base form's move. */
internal fun battleFinisherTitle(
    finisher: BattleFinisherSnapshot,
    fighters: Map<String, BattleFighterPresentation>,
    preparedForms: Map<String, BattleFighterPresentation>
): String? {
    val transformed = finisher.kind == BattleFinisherKind.FORM || finisher.kind == BattleFinisherKind.JOGRESS
    val species = finisher.resultSpecies?.takeIf { it.isNotBlank() }
    val result = if (transformed && species != null) preparedFinisherPresentation(finisher, preparedForms) else null
    val lead = fighters[finisher.leadId]
    return when (finisher.phase) {
        BattleFinisherPhase.REVEAL -> if (transformed) result?.displayName ?: species else lead?.displayName
        BattleFinisherPhase.CHARGE, BattleFinisherPhase.RELEASE,
        BattleFinisherPhase.IMPACT, BattleFinisherPhase.AFTERMATH -> {
            if (transformed) result?.specialDisplayName
            else lead?.specialDisplayName?.takeIf { it.isNotBlank() } ?: finisher.specialName
        }
        BattleFinisherPhase.FOCUS, BattleFinisherPhase.TRANSFORM, BattleFinisherPhase.RESTORE -> null
    }?.takeIf { it.isNotBlank() }
}

/** Snapshot-driven caption only; the native renderer owns all arena effects and timing. */
@Composable
internal fun BattleFinisherOverlay(
    finisher: BattleFinisherSnapshot,
    fighters: Map<String, BattleFighterPresentation>,
    preparedForms: Map<String, BattleFighterPresentation>,
    modifier: Modifier = Modifier
) {
    if (finisher.phase !in BattleFinisherPhase.REVEAL..BattleFinisherPhase.AFTERMATH) return
    val title = battleFinisherTitle(finisher, fighters, preparedForms)
        ?: stringResource(if (finisher.phase == BattleFinisherPhase.REVEAL) {
            R.string.ui_battle_finisher_active
        } else R.string.ui_battle_finisher_special)
    Column(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .widthIn(max = 360.dp)
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .testTag("offline-battle-finisher-title"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            color = TextPrimaryOnDark,
            style = MaterialTheme.typography.titleSmall.copy(shadow = Shadow(SceneTextShadow, Offset(1f, 1f), 3f)),
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (finisher.impactCommitted && finisher.phase in BattleFinisherPhase.IMPACT..BattleFinisherPhase.AFTERMATH) {
            Text(
                text = if (finisher.damage > 0) stringResource(R.string.ui_battle_finisher_damage, finisher.damage)
                    else stringResource(R.string.ui_battle_finisher_no_damage),
                color = TextPrimaryOnDark,
                style = MaterialTheme.typography.labelMedium.copy(shadow = Shadow(SceneTextShadow, Offset(1f, 1f), 3f)),
                modifier = Modifier.padding(top = 2.dp).testTag("offline-battle-finisher-damage")
            )
        }
    }
}

/** The only movie controls: native-size pause/resume and exit, with a quiet status label. */
@Composable
internal fun BattleFinisherStatusStrip(
    kind: BattleFinisherKind,
    paused: Boolean,
    manuallyPaused: Boolean,
    pauseEnabled: Boolean,
    onToggleManualPause: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier.fillMaxWidth().height(56.dp).padding(vertical = 4.dp).testTag("offline-battle-finisher-status"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = if (paused) stringResource(R.string.ui_battle_training_paused_badge) else stringResource(when (kind) {
                BattleFinisherKind.POWER -> R.string.ui_battle_finisher_power
                BattleFinisherKind.FORM -> R.string.ui_battle_finisher_form
                BattleFinisherKind.JOGRESS -> R.string.ui_battle_finisher_jogress
                BattleFinisherKind.DUO -> R.string.ui_battle_finisher_duo
            }),
            modifier = Modifier.weight(1f),
            color = if (paused) VitalCyan else TextSecondaryOnDark,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        IconButton(
            onClick = onToggleManualPause,
            enabled = pauseEnabled,
            modifier = Modifier.size(48.dp)
                .border(1.dp, if (manuallyPaused) VitalCyan else SurfaceStroke, CutCornerShape(6.dp))
                .clip(CutCornerShape(6.dp))
                .testTag("offline-battle-finisher-pause")
        ) {
            Icon(
                imageVector = if (manuallyPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                contentDescription = stringResource(if (manuallyPaused) R.string.ui_battle_training_continue else R.string.ui_battle_pause)
            )
        }
        IconButton(
            onClick = onExit,
            modifier = Modifier.size(48.dp)
                .border(1.dp, SurfaceStroke, CutCornerShape(6.dp))
                .clip(CutCornerShape(6.dp))
                .testTag("offline-battle-finisher-exit")
        ) {
            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = stringResource(R.string.ui_battle_training_exit))
        }
    }
}
