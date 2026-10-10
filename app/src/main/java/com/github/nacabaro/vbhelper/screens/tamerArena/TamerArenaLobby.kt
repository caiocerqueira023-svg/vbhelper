package com.github.nacabaro.vbhelper.screens.tamerArena

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.battle.offline.tamers.*
import com.github.nacabaro.vbhelper.components.CyberEmptyState
import com.github.nacabaro.vbhelper.components.CyberPanel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TamerArenaLobby(state: TamerArenaState, actions: ArenaUiActions,
    tab: Int, onTab: (Int) -> Unit, query: String, onQuery: (String) -> Unit,
    series: String?, onSeries: (String?) -> Unit, availableOnly: Boolean, onAvailableOnly: (Boolean) -> Unit,
    modifier: Modifier = Modifier) {
    var editTeam by rememberSaveable { mutableStateOf(false) }
    val rosterScroll = rememberLazyListState()
    val selected = state.resolver?.tamers?.firstOrNull { it.id == state.selectedTamerId }
    val bracket = state.activeRun?.takeIf { tab == 1 && selected == null }
    fun back() = when {
        editTeam -> { editTeam = false }
        selected != null -> actions.onSelectTamer(null)
        bracket != null -> actions.onShowBracket(null)
        else -> actions.onBack()
    }
    BackHandler { back() }
    BoxWithConstraints(modifier.fillMaxSize().testTag("tamer-arena")) {
        val wide = maxWidth >= 720.dp
        Column(Modifier.widthIn(max = 1_040.dp).fillMaxWidth().fillMaxHeight().align(Alignment.TopCenter)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = ::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.ui_back)) }
                Text(if (editTeam) stringResource(R.string.arena_team_setup_title) else selected?.name
                    ?: if (bracket != null) stringResource(R.string.arena_bracket_title) else stringResource(R.string.arena_title),
                    Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (selected == null && bracket == null) IconButton(onClick = actions.onReload, enabled = !state.loading && !state.busy) {
                    Icon(Icons.Outlined.Refresh, stringResource(R.string.arena_reload))
                }
            }
            if (selected == null && bracket == null) PrimaryTabRow(selectedTabIndex = tab,
                containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0f)) {
                listOf(R.string.arena_tamers, R.string.arena_tournaments, R.string.arena_records).forEachIndexed { index, label ->
                    Tab(tab == index, onClick = { editTeam = false; onTab(index) }, text = { Text(stringResource(label)) },
                        modifier = Modifier.heightIn(min = 48.dp))
                }
            }
            state.error?.let { error ->
                Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(error, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer)
                        IconButton(onClick = actions.onDismissError) { Icon(Icons.Outlined.Close, stringResource(R.string.arena_dismiss)) }
                    }
                }
            }
            if (state.busy || state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.loading && state.resolver == null) {
                Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(stringResource(R.string.arena_loading), style = MaterialTheme.typography.bodyMedium)
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    repeat(3) { Surface(Modifier.fillMaxWidth().height(84.dp), color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = MaterialTheme.shapes.medium) {} }
                }
            } else if (bracket != null) {
                ArenaCupBracket(bracket, state.busy, onResume = { actions.onResumeCup(bracket.id) },
                    onWithdraw = { actions.onWithdraw(bracket.id) }, modifier = Modifier.weight(1f))
            } else {
                if (tab != 2 || selected != null) {
                    ArenaTeamSummary(state, onEdit = { editTeam = !editTeam }, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
                when {
                    editTeam -> ArenaTeamEditor(state, actions, onDone = { editTeam = false }, modifier = Modifier.weight(1f))
                    selected != null && state.resolver != null -> ArenaBriefing(state, selected, state.resolver,
                        onChallenge = { actions.onChallenge(selected.id) }, onConfigure = { editTeam = true },
                        onReload = actions.onReload, wide = wide, modifier = Modifier.weight(1f))
                    tab == 0 -> {
                        val allEntries = remember(state.resolver, state.stages, state.format, query, series) {
                            ArenaPresentation.roster(state, query, series, false)
                        }
                        val entries = if (availableOnly && state.teamReady) allEntries.filter { it.team != null } else allEntries
                        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(query, onValueChange = onQuery,
                                label = { Text(stringResource(R.string.arena_search)) }, singleLine = true,
                                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                                trailingIcon = { if (query.isNotBlank()) IconButton(onClick = { onQuery("") }) {
                                    Icon(Icons.Outlined.Clear, stringResource(R.string.arena_clear_search))
                                } }, shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.fillMaxWidth().testTag("tamer-arena-search"))
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ArenaSeriesFilter(state.resolver?.tamers?.map { it.series }?.distinct()?.sorted().orEmpty(), series, onSeries)
                                FilterChip(availableOnly, onClick = { onAvailableOnly(!availableOnly) },
                                    label = { Text(stringResource(R.string.arena_ready_filter, allEntries.count { it.team != null })) },
                                    leadingIcon = { if (availableOnly) Icon(Icons.Outlined.Check, null, Modifier.size(16.dp)) },
                                    modifier = Modifier.heightIn(min = 48.dp).testTag("arena-ready-filter"))
                            }
                            Text(stringResource(R.string.arena_roster_summary, entries.size), style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (entries.isEmpty()) CyberEmptyState(stringResource(R.string.arena_no_search_results), Modifier.weight(1f),
                            title = stringResource(R.string.arena_no_opponents), actionLabel = stringResource(R.string.arena_reset_filters),
                            onAction = { onQuery(""); onSeries(null); onAvailableOnly(false) })
                        else LazyColumn(Modifier.fillMaxWidth().weight(1f), state = rosterScroll,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                            items(entries, key = { it.tamer.id }) { entry ->
                                ArenaRosterRow(entry, state.teamReady, !state.busy, onClick = { actions.onSelectTamer(entry.tamer.id) })
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                    tab == 1 -> ArenaCups(state, actions, series, onSeries, Modifier.weight(1f))
                    else -> ArenaRecords(state, onBrowse = { onTab(0) }, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ArenaRosterRow(entry: ArenaRosterEntry, configured: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 88.dp).clickable(enabled, role = Role.Button, onClick = onClick)
        .padding(vertical = 8.dp).testTag("tamer-row-${entry.tamer.id}"),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        val members = entry.team?.members.orEmpty()
        if (members.size == 2) Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            members.forEach { ArenaSprite(it.species, 36.dp) }
        } else ArenaSprite(entry.preview, 60.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(entry.tamer.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(entry.tamer.series, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(when {
                entry.team != null -> entry.team.members.joinToString(" + ") { it.nickname ?: it.species.name }
                entry.missingArtwork.isNotEmpty() -> stringResource(R.string.arena_needs_art, entry.missingArtwork.joinToString(", "))
                configured -> stringResource(R.string.arena_team_unavailable_short)
                else -> entry.tamer.primary
            }, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (entry.team != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Tune, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(arenaStrategyName(entry.tamer.style.strategy), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun ArenaTeamSummary(state: TamerArenaState, onEdit: () -> Unit, modifier: Modifier = Modifier) {
    CyberPanel(modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                var summary = ""
                state.selectedPartners.forEachIndexed { index, partner ->
                    if (index > 0) summary += " · "
                    summary += "${partner.displayName} (${arenaStageName(partner.stage)})"
                }
                Text(
                    if (state.selectedPartners.isEmpty()) stringResource(R.string.arena_no_partners) else summary,
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text("${state.format} × ${state.format} · ${arenaDifficultyName(state.difficulty)}",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onEdit, enabled = !state.busy,
                modifier = Modifier.height(48.dp).testTag("arena-edit-team")) {
                Text(stringResource(R.string.arena_edit_team))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArenaTeamEditor(state: TamerArenaState, actions: ArenaUiActions, onDone: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.arena_format_label), style = MaterialTheme.typography.labelLarge)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            (1..2).forEach { count -> SegmentedButton(state.format == count, onClick = { actions.onFormat(count) },
                shape = SegmentedButtonDefaults.itemShape(count - 1, 2, CutCornerShape(8.dp)), enabled = !state.busy,
                label = { Text("$count × $count") }, modifier = Modifier.heightIn(min = 48.dp)) }
        }
        Text(stringResource(R.string.arena_difficulty_label), style = MaterialTheme.typography.labelLarge)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            ArenaDifficulty.entries.forEachIndexed { index, difficulty -> SegmentedButton(state.difficulty == difficulty,
                onClick = { actions.onDifficulty(difficulty) }, shape = SegmentedButtonDefaults.itemShape(index, 3, CutCornerShape(8.dp)),
                enabled = !state.busy, label = { Text(arenaDifficultyName(difficulty)) }, modifier = Modifier.heightIn(min = 48.dp)) }
        }
        repeat(state.format) { slot ->
            val partner = state.partners.firstOrNull { it.stableId == state.selectedPartnerIds.getOrNull(slot) }
            ArenaAction(stringResource(R.string.arena_pick_slot, slot + 1, partner?.displayName ?: stringResource(R.string.arena_choose_partner)),
                { actions.onPickPartner(slot) }, Modifier.fillMaxWidth(), enabled = state.partners.isNotEmpty() && !state.busy,
                primary = false, icon = Icons.Outlined.SwapHoriz)
        }
        }
        ArenaAction(stringResource(R.string.arena_done), onDone, Modifier.fillMaxWidth())
    }
}

@Composable
internal fun ArenaSeriesFilter(series: List<String>, selected: String?, onSelect: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilterChip(selected != null, onClick = { expanded = true },
            label = { Text(selected ?: stringResource(R.string.arena_all_series), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            trailingIcon = { Icon(Icons.Outlined.ArrowDropDown, null, Modifier.size(18.dp)) }, modifier = Modifier.heightIn(min = 48.dp))
        DropdownMenu(expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 420.dp)) {
            DropdownMenuItem(text = { Text(stringResource(R.string.arena_all_series)) }, onClick = { onSelect(null); expanded = false })
            series.forEach { name -> DropdownMenuItem(text = { Text(name) }, onClick = { onSelect(name); expanded = false }) }
        }
    }
}
