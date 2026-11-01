package com.tamerslab.battle.ui.arena

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
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tamerslab.R
import com.tamerslab.battle.offline.tamers.*
import com.tamerslab.components.CyberEmptyState
import com.tamerslab.components.CollectionLoad
import com.tamerslab.components.CollectionStatePanel
import com.tamerslab.components.CyberPanel
import com.tamerslab.components.VitalSectionHeader
import com.tamerslab.battle.ui.OfflineBattleParticipant
import com.tamerslab.components.TopBanner
import com.tamerslab.components.TopBannerAction
import com.tamerslab.components.VitalTabs
import com.tamerslab.components.VitalSearchField
import com.tamerslab.components.InstrumentSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TamerArenaLobby(state: TamerArenaState, actions: ArenaUiActions,
    tab: Int, onTab: (Int) -> Unit, query: String, onQuery: (String) -> Unit,
    series: String?, onSeries: (String?) -> Unit, availableOnly: Boolean, onAvailableOnly: (Boolean) -> Unit,
    modifier: Modifier = Modifier, requireTeamConfirmation: Boolean = false, onConfirmTeam: () -> Unit = {}) {
    var editTeam by rememberSaveable { mutableStateOf(requireTeamConfirmation) }
    var filtersExpanded by rememberSaveable { mutableStateOf(false) }
    val rosterScroll = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    val selected = state.resolver?.tamers?.firstOrNull { it.id == state.selectedTamerId }
    val bracket = state.activeRun?.takeIf { tab == 1 && selected == null }
    LaunchedEffect(query, series, availableOnly, state.stages, state.format) {
        rosterScroll.scrollToItem(0)
    }
    fun back() = when {
        editTeam && requireTeamConfirmation -> actions.onBack()
        editTeam -> { editTeam = false }
        selected != null -> actions.onSelectTamer(null)
        bracket != null -> actions.onShowBracket(null)
        else -> actions.onBack()
    }
    BackHandler { back() }
    BoxWithConstraints(modifier.fillMaxSize().testTag("tamer-arena")) {
        val wide = maxWidth >= 720.dp
        Column(Modifier.widthIn(max = 1_040.dp).fillMaxWidth().fillMaxHeight().align(Alignment.TopCenter)) {
            TopBanner(if (editTeam) stringResource(R.string.arena_team_setup_title) else selected?.name
                ?: if (bracket != null) stringResource(R.string.arena_bracket_title) else stringResource(R.string.arena_title),
                onBackClick = ::back, trailingAction = if (!editTeam && bracket == null) ({
                    Box(Modifier.testTag("arena-edit-team")) {
                        TopBannerAction(Icons.Outlined.Tune, stringResource(R.string.arena_team_setup_title), {
                            focusManager.clearFocus()
                            editTeam = true
                        }, enabled = state.resolver != null && !state.loading && !state.busy)
                    }
                }) else null)
            if (!editTeam && selected == null && bracket == null) VitalTabs(tab,
                listOf(stringResource(R.string.arena_tamers), stringResource(R.string.arena_tournaments), stringResource(R.string.arena_records)),
                { editTeam = false; onTab(it) })
            state.error?.takeIf { state.resolver != null }?.let { error ->
                Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(error, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer)
                        IconButton(onClick = actions.onDismissError) { Icon(Icons.Outlined.Close, stringResource(R.string.arena_dismiss)) }
                    }
                }
            }
            if (state.error != null) com.tamerslab.components.DebugDetails(state.errorDetails)
            if (state.resolver != null && (state.busy || state.loading)) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.resolver == null) {
                CollectionStatePanel(CollectionLoad<ArenaRosterEntry>(null,
                    failed = !state.loading, retry = actions.onReload), Modifier.weight(1f))
            } else if (bracket != null) {
                ArenaCupBracket(bracket, state.busy, onResume = { actions.onResumeCup(bracket.id) },
                    onWithdraw = { actions.onWithdraw(bracket.id) }, modifier = Modifier.weight(1f))
            } else {
                when {
                    editTeam -> ArenaTeamEditor(state, actions, onDone = {
                        onConfirmTeam()
                        editTeam = false
                    }, modifier = Modifier.weight(1f))
                    selected != null && state.resolver != null -> ArenaBriefing(state, selected, state.resolver,
                        onChallenge = { actions.onChallenge(selected.id) }, onConfigure = { editTeam = true },
                        onReload = actions.onReload, wide = wide, modifier = Modifier.weight(1f))
                    tab == 0 -> {
                        val allEntries = remember(state.resolver, state.stages, state.format, query, series) {
                            ArenaPresentation.roster(state, query, series, false)
                        }
                        val entries = if (availableOnly && state.teamReady) allEntries.filter { it.team != null } else allEntries
                        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                VitalSearchField(query, onQuery, stringResource(R.string.uniform_search_tamers),
                                    Modifier.weight(1f).testTag("tamer-arena-search"), hint = stringResource(R.string.arena_search))
                                IconToggleButton(checked = filtersExpanded, onCheckedChange = {
                                    focusManager.clearFocus()
                                    filtersExpanded = it
                                }, modifier = Modifier.size(48.dp)) {
                                    Icon(Icons.Outlined.FilterList, stringResource(R.string.arena_filters),
                                        tint = if (filtersExpanded || series != null || availableOnly) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (filtersExpanded) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        ArenaSeriesFilter(state.resolver?.tamers?.map { it.series }?.distinct()?.sorted().orEmpty(), series, onSeries)
                                        FilterChip(availableOnly, onClick = { onAvailableOnly(!availableOnly) },
                                            label = { Text(stringResource(R.string.arena_ready_filter, allEntries.count { it.team != null })) },
                                            leadingIcon = { if (availableOnly) Icon(Icons.Outlined.Check, null, Modifier.size(16.dp)) },
                                            modifier = Modifier.heightIn(min = 48.dp).testTag("arena-ready-filter"))
                                    }
                                    IconButton(onClick = actions.onReload, enabled = !state.loading && !state.busy) {
                                        Icon(Icons.Outlined.Refresh, stringResource(R.string.arena_reload))
                                    }
                                }
                                Text(stringResource(R.string.arena_roster_summary, entries.size), style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (entries.isEmpty()) CyberEmptyState(stringResource(R.string.arena_no_search_results), Modifier.weight(1f),
                            title = stringResource(R.string.arena_no_opponents), actionLabel = stringResource(R.string.arena_reset_filters),
                            onAction = { onQuery(""); onSeries(null); onAvailableOnly(false) })
                        else LazyColumn(Modifier.fillMaxWidth().weight(1f), state = rosterScroll,
                            contentPadding = PaddingValues(horizontal = InstrumentSpacing.Screen, vertical = 8.dp)) {
                            items(entries, key = { it.tamer.id }) { entry ->
                                ArenaRosterRow(entry, state.teamReady, !state.busy, onClick = {
                                    focusManager.clearFocus()
                                    actions.onSelectTamer(entry.tamer.id)
                                })
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArenaTeamEditor(state: TamerArenaState, actions: ArenaUiActions, onDone: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        VitalSectionHeader(stringResource(R.string.arena_your_team))
        repeat(state.format) { slot ->
            val partner = state.partners.firstOrNull { it.stableId == state.selectedPartnerIds.getOrNull(slot) }
            ArenaPartnerSetup(partner, slot, enabled = state.partners.isNotEmpty() && !state.busy && !state.loading,
                onChoose = { actions.onPickPartner(slot) })
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        VitalSectionHeader(stringResource(R.string.arena_format_label))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            (1..2).forEach { count -> SegmentedButton(state.format == count, onClick = { actions.onFormat(count) },
                shape = SegmentedButtonDefaults.itemShape(count - 1, 2, CutCornerShape(8.dp)), enabled = !state.busy,
                label = { Text("$count × $count") }, modifier = Modifier.heightIn(min = 48.dp)) }
        }
        Text(stringResource(if (state.format == 1) R.string.arena_format_solo_hint else R.string.arena_format_duo_hint),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        VitalSectionHeader(stringResource(R.string.arena_difficulty_label))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            ArenaDifficulty.entries.forEachIndexed { index, difficulty -> SegmentedButton(state.difficulty == difficulty,
                onClick = { actions.onDifficulty(difficulty) }, shape = SegmentedButtonDefaults.itemShape(index, 3, CutCornerShape(8.dp)),
                enabled = !state.busy, label = { Text(arenaDifficultyName(difficulty)) }, modifier = Modifier.heightIn(min = 48.dp)) }
        }
        Text(stringResource(when (state.difficulty) {
            ArenaDifficulty.CASUAL -> R.string.arena_casual_hint
            ArenaDifficulty.NORMAL -> R.string.arena_normal_hint
            ArenaDifficulty.EXPERT -> R.string.arena_expert_hint
        }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        }
        ArenaAction(stringResource(R.string.arena_done), onDone, Modifier.fillMaxWidth(),
            enabled = !state.busy && !state.loading, icon = Icons.Outlined.Check)
    }
}

@Composable
private fun ArenaPartnerSetup(partner: OfflineBattleParticipant?, slot: Int, enabled: Boolean, onChoose: () -> Unit) {
    CyberPanel(Modifier.fillMaxWidth(), active = partner != null, onClick = onChoose.takeIf { enabled },
        contentPadding = PaddingValues(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val species = partner?.let { ArenaSpecies(it.speciesName ?: it.displayName, it.visualStage ?: it.stage,
                it.assetCharacterId, it.character?.charId ?: it.cardCharacterId, it.attribute, emptyList(), it.vitalStats) }
            ArenaSprite(species, 80.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.arena_partner_slot, slot + 1), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(partner?.displayName ?: stringResource(R.string.arena_choose_partner),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                partner?.let {
                    Text(arenaStageName(it.visualStage ?: it.stage), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val stats = it.trainingStats()
                    Text(stringResource(R.string.arena_setup_stats, stats.health, stats.attack, stats.defense),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Icon(Icons.Outlined.SwapHoriz, null, tint = if (enabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant)
        }
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
