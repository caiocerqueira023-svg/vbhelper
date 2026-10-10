package com.github.nacabaro.vbhelper.screens.tamerArena

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.battle.offline.tamers.*
import com.github.nacabaro.vbhelper.components.CyberEmptyState
import com.github.nacabaro.vbhelper.components.CyberPanel

@Composable
internal fun ArenaCups(state: TamerArenaState, actions: ArenaUiActions, series: String?, onSeries: (String?) -> Unit,
    modifier: Modifier = Modifier) {
    var size by rememberSaveable { mutableIntStateOf(8) }
    val ready = remember(state.resolver, state.stages, state.format, series) {
        if (state.teamReady) state.resolver?.tamers.orEmpty().count {
            (series == null || it.series == series) && state.resolver?.resolve(it.id, state.stages) != null
        } else 0
    }
    LazyColumn(modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ArenaHeading(stringResource(R.string.arena_enter_cup))
                Text(stringResource(R.string.arena_cup_short_rules), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(8, 16).forEach { entries -> FilterChip(size == entries, onClick = { size = entries },
                        enabled = !state.busy, label = { Text(stringResource(R.string.arena_cup_size, entries)) },
                        modifier = Modifier.heightIn(min = 48.dp)) }
                }
                ArenaSeriesFilter(state.resolver?.tamers?.map { it.series }?.distinct()?.sorted().orEmpty(), series, onSeries)
                Text(stringResource(R.string.arena_cup_pool, ready), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                ArenaAction(stringResource(R.string.arena_start_cup), { actions.onStartCup(size, series) },
                    Modifier.fillMaxWidth(), enabled = !state.busy && state.teamReady && ready >= size - 1,
                    icon = Icons.Outlined.EmojiEvents)
                if (ready < size - 1) Text(stringResource(R.string.arena_not_enough_tamers), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (state.runs.isNotEmpty()) item { ArenaHeading(stringResource(R.string.arena_saved_cups)) }
        items(state.runs, key = { it.id }) { run ->
            CyberPanel(Modifier.fillMaxWidth(), active = !run.withdrawn && run.championId == null,
                contentPadding = PaddingValues(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.EmojiEvents, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(run.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.arena_cup_details, run.entrants.size, run.format, run.format),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(4.dp))
                val status = when {
                    run.withdrawn -> stringResource(R.string.arena_withdrawn)
                    run.championId != null -> stringResource(R.string.arena_champion, run.entrants.firstOrNull { it.id == run.championId }?.name.orEmpty())
                    else -> stringResource(R.string.arena_round_progress, ArenaPresentation.defaultRound(run) + 1, ArenaPresentation.totalRounds(run))
                }
                Text(status, style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!run.withdrawn && run.championId == null) ArenaAction(stringResource(R.string.arena_resume),
                        { actions.onResumeCup(run.id) }, Modifier.weight(1f), enabled = !state.busy)
                    ArenaAction(stringResource(R.string.arena_view_bracket), { actions.onShowBracket(run.id) },
                        Modifier.weight(1f), enabled = !state.busy, primary = false)
                }
            }
        }
        if (state.runs.isEmpty()) item {
            Text(stringResource(R.string.arena_no_tournaments), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun ArenaCupBracket(run: ArenaTournament, busy: Boolean, onResume: () -> Unit, onWithdraw: () -> Unit,
    modifier: Modifier = Modifier) {
    val names = run.entrants.associate { it.id to it.name }
    var round by rememberSaveable(run.id) { mutableIntStateOf(ArenaPresentation.defaultRound(run)) }
    val rounds = run.matches.map { it.round }.distinct().sorted()
    val shown = if (round in rounds) round else rounds.last()
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(run.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.arena_cup_details, run.entrants.size, run.format, run.format),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rounds.forEach { index -> FilterChip(shown == index, onClick = { round = index },
                    label = { Text(stringResource(R.string.arena_round, index + 1)) }, modifier = Modifier.heightIn(min = 48.dp)) }
            }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(run.matches.filter { it.round == shown }.sortedBy { it.position }, key = { it.id }) { match ->
                val playerBout = match.leftId == ArenaRepository.PLAYER_ID || match.rightId == ArenaRepository.PLAYER_ID
                CyberPanel(Modifier.fillMaxWidth(), active = playerBout, contentPadding = PaddingValues(12.dp)) {
                    listOf(match.leftId, match.rightId).forEach { entry ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 42.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(names[entry].orEmpty(), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (match.winnerId == entry || entry == ArenaRepository.PLAYER_ID) FontWeight.SemiBold else FontWeight.Normal)
                            if (match.winnerId == entry) Icon(Icons.Outlined.CheckCircle, stringResource(R.string.arena_advances, names[entry].orEmpty()),
                                Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    if (match.winnerId == null) Text(stringResource(if (playerBout) R.string.arena_your_bout else R.string.arena_pending_bout),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                run.withdrawn -> Text(stringResource(R.string.arena_withdrawn), style = MaterialTheme.typography.bodyMedium)
                run.championId != null -> Text(stringResource(R.string.arena_champion, names[run.championId].orEmpty()),
                    style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                else -> {
                    ArenaAction(stringResource(R.string.arena_resume), onResume, Modifier.fillMaxWidth(), enabled = !busy)
                    TextButton(onClick = onWithdraw, enabled = !busy) { Text(stringResource(R.string.arena_withdraw)) }
                }
            }
        }
    }
}

@Composable
internal fun ArenaRecords(state: TamerArenaState, onBrowse: () -> Unit, modifier: Modifier = Modifier) {
    if (state.records.isEmpty()) {
        CyberEmptyState(stringResource(R.string.arena_no_records), modifier, title = stringResource(R.string.arena_records),
            actionLabel = stringResource(R.string.arena_browse_tamers), onAction = onBrowse)
        return
    }
    val trophies = state.runs.filter { it.championId == ArenaRepository.PLAYER_ID }
    LazyColumn(modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ArenaStat(stringResource(R.string.arena_wins), state.records.sumOf { it.victories }.toString(), Modifier.weight(1f))
                ArenaStat(stringResource(R.string.arena_bouts), state.records.sumOf { it.fights }.toString(), Modifier.weight(1f))
                ArenaStat(stringResource(R.string.arena_cup_trophies), trophies.size.toString(), Modifier.weight(1f))
            }
        }
        if (trophies.isNotEmpty()) item { ArenaHeading(stringResource(R.string.arena_cup_trophies)) }
        items(trophies, key = { "trophy:${it.id}" }) { run ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.EmojiEvents, null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.arena_trophy, run.title, run.entrants.size), style = MaterialTheme.typography.bodyMedium)
            }
        }
        item { ArenaHeading(stringResource(R.string.arena_tamer_records)) }
        items(state.records.sortedByDescending { it.victories }, key = { it.tamerId }) { record ->
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(state.resolver?.tamers?.firstOrNull { it.id == record.tamerId }?.name ?: record.tamerId,
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.arena_record, record.victories, record.defeats, record.fights),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider()
            }
        }
    }
}
