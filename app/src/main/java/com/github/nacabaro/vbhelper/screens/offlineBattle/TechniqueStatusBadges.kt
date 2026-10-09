package com.github.nacabaro.vbhelper.screens.offlineBattle

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.github.nacabaro.vbhelper.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.nacabaro.vbhelper.battle.offline.core.TechniqueDefinition
import com.github.nacabaro.vbhelper.battle.offline.data.TechniqueStatusPresentation
import com.github.nacabaro.vbhelper.battle.offline.data.TechniqueStatusTone
import com.github.nacabaro.vbhelper.ui.theme.StatusRed
import com.github.nacabaro.vbhelper.ui.theme.VitalCyan

@Composable
internal fun TechniqueStatusBadges(
    technique: TechniqueDefinition,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val statuses = remember(technique.statusEffects) {
        TechniqueStatusPresentation.forTechnique(technique)
    }
    if (statuses.isEmpty()) return

    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        statuses.forEach { status ->
            val signal = when (status.tone) {
                TechniqueStatusTone.AILMENT -> StatusRed
                TechniqueStatusTone.BOOST -> VitalCyan
            }
            val alpha = if (enabled) 1f else 0.55f
            Surface(
                color = signal.copy(alpha = 0.12f * alpha),
                contentColor = signal.copy(alpha = alpha),
                border = BorderStroke(1.dp, signal.copy(alpha = 0.72f * alpha)),
                shape = CutCornerShape(topStart = 4.dp, bottomEnd = 4.dp)
            ) {
                Text(
                    text = "${if (status.tone == TechniqueStatusTone.AILMENT) stringResource(R.string.ui_battle_badge_status) else stringResource(R.string.ui_battle_badge_buff)} · ${battleStatusLabel(status.effectId, status.name)}" +
                        if (status.chancePercent < 100) " · ${status.chancePercent}%" else "",
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    color = Color.Unspecified,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
