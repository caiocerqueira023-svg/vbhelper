package com.github.nacabaro.vbhelper.screens.tamerArena

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.battle.offline.core.TechniqueDefinition
import com.github.nacabaro.vbhelper.battle.offline.core.TechniqueKind
import com.github.nacabaro.vbhelper.battle.offline.tamers.*
import com.github.nacabaro.vbhelper.components.CyberPanel

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun ArenaBriefing(state: TamerArenaState, tamer: TamerDefinition, resolver: TamerTeamResolver,
    onChallenge: () -> Unit, onConfigure: () -> Unit, onReload: () -> Unit,
    wide: Boolean, modifier: Modifier = Modifier) {
    val team = remember(tamer.id, state.stages, state.format, state.teamReady, resolver) {
        if (state.teamReady) resolver.resolve(tamer.id, state.stages) else null
    }
    val participants = remember(team, resolver) { team?.let { TamerBattleLoadouts.participants(it, resolver) }.orEmpty() }
    var inspected by rememberSaveable(tamer.id) { mutableIntStateOf(0) }
    val selected = inspected.coerceIn(0, (participants.size - 1).coerceAtLeast(0))
    val uri = LocalUriHandler.current
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(tamer.series, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (team == null) {
                CyberPanel(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        ArenaSprite(resolver.speciesNamed(tamer.primary), 96.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ArenaHeading(stringResource(if (state.teamReady) R.string.arena_unavailable_heading else R.string.arena_choose_team))
                            val missing = if (state.teamReady) resolver.missingArtwork(tamer.id, state.stages) else emptyList()
                            Text(if (missing.isNotEmpty()) stringResource(R.string.arena_needs_art, missing.joinToString(", "))
                                else stringResource(if (state.teamReady) R.string.arena_missing_art else R.string.arena_no_partners),
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (state.teamReady) ArenaAction(stringResource(R.string.arena_reload_art), onReload,
                    Modifier.fillMaxWidth(), primary = false, icon = Icons.Outlined.Refresh)
            } else {
                CyberPanel(Modifier.fillMaxWidth(), active = true, contentPadding = PaddingValues(16.dp)) {
                    ArenaHeading(stringResource(R.string.arena_their_team))
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        team.members.forEach { member ->
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                ArenaSprite(member.species, if (wide || team.members.size == 1) 120.dp else 88.dp)
                                Text(member.nickname ?: member.species.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(if (member.nickname != null) "${member.species.name} · ${arenaStageName(member.species.stage)}"
                                    else arenaStageName(member.species.stage), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(when (member.origin) {
                                    ArenaPartnerOrigin.OWNED -> stringResource(R.string.arena_owned_partner)
                                    ArenaPartnerOrigin.ASSOCIATED -> stringResource(R.string.arena_associated_partner)
                                    ArenaPartnerOrigin.GUEST -> stringResource(R.string.arena_guest_partner,
                                        resolver.tamers.firstOrNull { it.id == member.ownerId }?.name ?: member.ownerId)
                                }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (member.scaled) ArenaBadge(stringResource(R.string.arena_scaled_tier, arenaStageName(member.battleStage)))
                                if (member.componentPartnerIds.size > 1) ArenaBadge(stringResource(R.string.arena_combined_partner), highlighted = true)
                            }
                        }
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ArenaHeading(stringResource(R.string.arena_battle_plan))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.Tune, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(arenaStrategyName(tamer.style.strategy), style = MaterialTheme.typography.titleSmall)
                }
                Text(stringResource(when (tamer.style) {
                    TamerStyle.RUSH -> R.string.arena_plan_rush
                    TamerStyle.TACTICAL -> R.string.arena_plan_tactical
                    TamerStyle.RANGED -> R.string.arena_plan_ranged
                    TamerStyle.GUARD -> R.string.arena_plan_guard
                    TamerStyle.SUPPORT -> R.string.arena_plan_support
                    TamerStyle.CONTROL -> R.string.arena_plan_control
                    TamerStyle.BALANCED -> R.string.arena_plan_balanced
                }), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (participants.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ArenaHeading(stringResource(R.string.arena_loadout))
                    if (participants.size == 2) PrimaryTabRow(selectedTabIndex = selected, containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0f)) {
                        participants.forEachIndexed { index, participant -> Tab(selected == index, onClick = { inspected = index },
                            text = { Text(participant.displayName, style = MaterialTheme.typography.labelLarge) }, modifier = Modifier.heightIn(min = 48.dp)) }
                    }
                    val participant = participants[selected]
                    val stats = participant.trainingStats()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ArenaStat(stringResource(R.string.arena_stat_hp), stats.health.toString(), Modifier.weight(1f))
                        ArenaStat(stringResource(R.string.arena_stat_attack), stats.attack.toString(), Modifier.weight(1f))
                        ArenaStat(stringResource(R.string.arena_stat_defense), stats.defense.toString(), Modifier.weight(1f))
                        ArenaStat(stringResource(R.string.arena_stat_energy), stats.energy.toString(), Modifier.weight(1f))
                    }
                    HorizontalDivider()
                    val special = participant.battleTechniques.firstOrNull { it.kind == TechniqueKind.SPECIAL }
                    special?.let { ArenaMove(it, signature = true) }
                    participant.battleTechniques.filter { it.kind != TechniqueKind.SPECIAL }.forEach { ArenaMove(it) }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ArenaFinisher(Icons.Outlined.AutoAwesome, stringResource(R.string.arena_blast_label),
                            participant.blastTargetSpecies ?: stringResource(R.string.arena_power_blast))
                        participant.jogressResultSpecies?.let {
                            ArenaFinisher(Icons.Outlined.MergeType, stringResource(R.string.arena_jogress_label), it)
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ArenaHeading(stringResource(R.string.arena_supplies))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ArenaSupply(Icons.Outlined.FavoriteBorder, "2", stringResource(R.string.arena_recovery), Modifier.weight(1f))
                        ArenaSupply(Icons.Outlined.Bolt, "2", stringResource(R.string.arena_energy), Modifier.weight(1f))
                        ArenaSupply(Icons.Outlined.HealthAndSafety, "1", stringResource(R.string.arena_remedy), Modifier.weight(1f))
                    }
                    Text(stringResource(R.string.arena_shared_supplies_note), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ArenaHeading(stringResource(R.string.arena_reward_heading))
                    Text(stringResource(R.string.arena_reward, ArenaPresentation.baseReward(state)),
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.arena_first_clear_note, ArenaRewards.FIRST_CLEAR_BONUS),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(stringResource(R.string.arena_safe_short), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { runCatching { uri.openUri(tamer.sourceUri) } }) {
                Icon(Icons.Outlined.OpenInNew, null, Modifier.size(16.dp)); Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.arena_source))
            }
        }
        HorizontalDivider()
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${state.format} × ${state.format} · ${arenaDifficultyName(state.difficulty)}",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ArenaAction(stringResource(if (state.teamReady) R.string.arena_challenge else R.string.arena_configure_team),
                if (state.teamReady) onChallenge else onConfigure,
                Modifier.fillMaxWidth().testTag("tamer-arena-challenge"),
                enabled = !state.busy && (team != null || !state.teamReady), icon = Icons.Outlined.SportsMartialArts)
        }
    }
}

@Composable
private fun ArenaMove(technique: TechniqueDefinition, signature: Boolean = false) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(when {
            signature -> Icons.Outlined.Bolt
            technique.kind == TechniqueKind.HEAL -> Icons.Outlined.FavoriteBorder
            technique.kind == TechniqueKind.SUPPORT -> Icons.Outlined.Shield
            technique.kind == TechniqueKind.PROJECTILE -> Icons.Outlined.NearMe
            else -> Icons.Outlined.SportsMartialArts
        }, null, Modifier.size(22.dp), tint = if (signature) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(when (technique.displayName) {
                "Guard stance" -> stringResource(R.string.arena_guard_stance)
                "Close strike" -> stringResource(R.string.arena_close_strike)
                "Strike" -> stringResource(R.string.arena_strike)
                else -> technique.displayName
            }, style = MaterialTheme.typography.bodyMedium, fontWeight = if (signature) FontWeight.SemiBold else FontWeight.Normal)
            Text(when {
                technique.healPower > 0 -> stringResource(R.string.arena_heal_stats, technique.healPower, technique.energyCost)
                technique.power == 0 -> stringResource(R.string.arena_support_stats, technique.energyCost)
                else -> stringResource(R.string.arena_move_stats, technique.power, technique.energyCost)
            }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (signature) ArenaBadge(stringResource(R.string.arena_signature), highlighted = true)
    }
}

@Composable
private fun ArenaFinisher(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, target: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(target, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ArenaSupply(icon: androidx.compose.ui.graphics.vector.ImageVector, quantity: String, label: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("×$quantity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
