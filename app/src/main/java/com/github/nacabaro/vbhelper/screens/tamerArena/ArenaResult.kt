package com.github.nacabaro.vbhelper.screens.tamerArena

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.battle.offline.core.*
import com.github.nacabaro.vbhelper.battle.offline.tamers.*
import com.github.nacabaro.vbhelper.components.CyberPanel

@Composable
internal fun ArenaResult(snapshot: BattleSnapshot, match: ArenaMatchSpec, settlement: ArenaSettlement?, recording: Boolean,
    error: String?, busy: Boolean, champion: Boolean, onRetryRecord: () -> Unit, onContinue: () -> Unit, onReturn: () -> Unit) {
    val outcome = settlement?.outcome ?: snapshot.result?.outcome ?: BattleOutcome.DRAW
    CyberPanel(Modifier.widthIn(max = 560.dp).fillMaxWidth(), active = true, contentPadding = PaddingValues(16.dp)) {
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(if (outcome == BattleOutcome.ALLIED_VICTORY) Icons.Outlined.CheckCircle else Icons.Outlined.SportsMartialArts,
                    null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(when (outcome) {
                    BattleOutcome.ALLIED_VICTORY -> R.string.arena_victory
                    BattleOutcome.OPPOSING_VICTORY -> R.string.arena_defeat
                    BattleOutcome.DRAW -> R.string.arena_draw
                    BattleOutcome.ABANDONED -> R.string.arena_abandoned
                }), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            Text(match.right.name, style = MaterialTheme.typography.titleMedium)
            val seconds = (snapshot.elapsedMillis / 1_000).toInt()
            Text(stringResource(R.string.arena_result_time, seconds / 60, seconds % 60),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ArenaStat(stringResource(R.string.arena_damage_dealt), snapshot.statistics.damageDealt.toString(), Modifier.weight(1f))
                ArenaStat(stringResource(R.string.arena_damage_received), snapshot.statistics.damageReceived.toString(), Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ArenaStat(stringResource(R.string.arena_your_items), snapshot.statistics.itemsUsed.toString(), Modifier.weight(1f))
                ArenaStat(stringResource(R.string.arena_their_items),
                    (if (match.tiebreakRound == 0) 5 - snapshot.opposingItems.sumOf { it.remaining } else 0).toString(), Modifier.weight(1f))
            }
            if (recording) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(stringResource(R.string.arena_saving), style = MaterialTheme.typography.bodySmall) }
            settlement?.let { Text(stringResource(R.string.arena_reward, it.rewardBits),
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary) }
            if (settlement?.tiebreak == true) Text(stringResource(R.string.arena_tiebreak_resolution), style = MaterialTheme.typography.bodySmall)
            if (champion) Text(stringResource(R.string.arena_trophy_awarded), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.arena_safe_short), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error); TextButton(onClick = onRetryRecord) { Text(stringResource(R.string.app_retry)) } }
        }
        Spacer(Modifier.height(8.dp))
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        ArenaAction(stringResource(when {
            match.tournamentId == null -> R.string.ui_battle_rematch
            outcome == BattleOutcome.DRAW -> R.string.arena_tiebreak
            outcome == BattleOutcome.ALLIED_VICTORY && !champion -> R.string.arena_next_round
            else -> R.string.arena_view_bracket
        }), onContinue, Modifier.fillMaxWidth(), enabled = settlement != null && !recording && !busy)
        ArenaAction(stringResource(R.string.arena_return), onReturn, Modifier.fillMaxWidth(),
            enabled = !recording && !busy, primary = false)
    }
}
