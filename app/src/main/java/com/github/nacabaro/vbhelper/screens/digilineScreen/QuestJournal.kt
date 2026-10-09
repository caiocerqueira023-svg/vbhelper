package com.github.nacabaro.vbhelper.screens.digilineScreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.components.VitalButton
import com.github.nacabaro.vbhelper.quests.*

@Composable
fun QuestJournal(
    quests: List<QuestWithObjectives>,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
    onOpen: ((QuestInstance) -> Unit)? = null,
    onTrack: ((String) -> Unit)? = null,
    onAction: ((QuestInstance, QuestActionType) -> Unit)? = null,
    actorId: String? = null,
    onConfigurePartner: ((QuestInstance) -> Unit)? = null,
    focusQuestId: String? = null
) {
    var history by rememberSaveable { mutableStateOf(false) }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    val visible = quests.filter {
        it.quest.id == focusQuestId ||
            ((if (history) it.quest.state in listOf(QuestState.COMPLETED, QuestState.DECLINED, QuestState.ABANDONED)
            else it.quest.state in listOf(QuestState.OFFERED, QuestState.ACTIVE, QuestState.READY) || pendingFollowUp(it.quest)) &&
                (category == null || it.quest.category.name == category))
    }
    LazyColumn(modifier, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !history, onClick = { history = false }, label = { Text(stringResource(R.string.quest_current)) })
                FilterChip(selected = history, onClick = { history = true }, label = { Text(stringResource(R.string.quest_history)) })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = category == QuestCategory.NORMAL.name,
                    onClick = { category = if (category == QuestCategory.NORMAL.name) null else QuestCategory.NORMAL.name },
                    label = { Text(stringResource(R.string.quest_normal)) })
                FilterChip(selected = category == QuestCategory.RECRUITMENT.name,
                    onClick = { category = if (category == QuestCategory.RECRUITMENT.name) null else QuestCategory.RECRUITMENT.name },
                    label = { Text(stringResource(R.string.quest_recruitment)) })
            }
        }
        if (visible.isEmpty()) item {
            Text(stringResource(if (history) R.string.quest_empty_history else R.string.quest_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
        items(visible, key = { it.quest.id }) { details ->
            val next = quests.firstOrNull { it.quest.id == details.quest.followUpQuestId }?.quest
            QuestEntry(details, busy, onOpen, onTrack, onAction, actorId, onConfigurePartner, next) {
                history = next?.state in listOf(QuestState.COMPLETED, QuestState.DECLINED, QuestState.ABANDONED)
                category = next?.category?.name
                if ((onAction == null || (actorId != null && actorId != details.quest.giverId)) && next != null) onOpen?.invoke(next)
            }
            HorizontalDivider(Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun QuestEntry(details: QuestWithObjectives, busy: Boolean,
                       onOpen: ((QuestInstance) -> Unit)?, onTrack: ((String) -> Unit)?,
                       onAction: ((QuestInstance, QuestActionType) -> Unit)?, actorId: String?,
                       onConfigurePartner: ((QuestInstance) -> Unit)?, nextQuest: QuestInstance?, onShowNext: () -> Unit) {
    val quest = details.quest
    val current = QuestSteps.current(quest, details.objectives)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(questTitle(quest.templateId), style = MaterialTheme.typography.titleMedium)
        if (quest.parentQuestId != null || quest.followUpTemplateId != null) {
            Text(stringResource(R.string.quest_chain_part, quest.chainDepth + 1),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(stringResource(R.string.quest_giver_state, quest.giverName, questState(quest.state)),
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        quest.partnerName?.let { Text(stringResource(R.string.quest_bound_partner, it),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        details.objectives.groupBy { it.phase }.toSortedMap().forEach { (phase, objectives) ->
            if (details.objectives.any { it.phase > 0 }) {
                val state = when {
                    objectives.all { it.progress >= it.required } -> R.string.quest_step_done
                    phase == quest.currentPhase && quest.state == QuestState.ACTIVE -> R.string.quest_step_current
                    phase == quest.currentPhase && quest.state == QuestState.OFFERED -> R.string.quest_step_start
                    else -> R.string.quest_step_locked
                }
                Text(stringResource(R.string.quest_step_label, phase + 1, stringResource(state)),
                    style = MaterialTheme.typography.titleSmall)
            }
            objectives.sortedBy { it.position }.forEach { objective ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(objectiveLabel(objective, details.tokens), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                    color = if (objective.phase > quest.currentPhase) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                Text(if (objective.type == QuestObjectiveType.PARTNER_STAGE)
                    stringResource(if (objective.progress >= objective.required) R.string.quest_met else R.string.quest_not_met)
                else "${objective.progress}/${objective.required}",
                    color = if (objective.progress >= objective.required) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            }
            if (objective.type == QuestObjectiveType.WIN_BATTLE_CONDITION) {
                QuestBattleRequirements(objective)
                details.battleAttempts.filter { it.objectiveId == objective.id }.maxByOrNull { it.recordedAt }?.let { attempt ->
                    Text(if (attempt.failure == null) stringResource(R.string.quest_trial_met)
                        else stringResource(R.string.quest_trial_failed, trialFailureLabel(attempt.failure)),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            }
        }
        details.tokens.filter { it.state == QuestTokenState.HELD }.forEach { token ->
            Text(stringResource(R.string.quest_held_object, questTokenLabel(token.kind)),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        if (details.objectives.any { it.type in listOf(QuestObjectiveType.WATCH_BATTLES, QuestObjectiveType.WATCH_WINS, QuestObjectiveType.WATCH_TROPHIES,
                QuestObjectiveType.WATCH_PP_EARN, QuestObjectiveType.WATCH_PP_REACH, QuestObjectiveType.WATCH_ADVENTURE_ADVANCE) } &&
            quest.state in listOf(QuestState.OFFERED, QuestState.ACTIVE)) {
            Text(stringResource(R.string.quest_sync_hint), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (quest.watchNotice != null) Text(stringResource(when (quest.watchNotice) {
            QuestWatchNotice.PP_DISCONTINUITY.name -> R.string.quest_pp_discontinuity
            QuestWatchNotice.ADVENTURE_DISCONTINUITY.name -> R.string.quest_adventure_discontinuity
            else -> R.string.quest_watch_discontinuity
        }),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        quest.availabilityIssue?.let { Text(availabilityLabel(it), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error) }
        if (quest.state == QuestState.ACTIVE && quest.partnerId != null && onConfigurePartner != null &&
            (actorId == null || actorId == quest.giverId) && current.any { it.techniqueId != null }) {
            TextButton(onClick = { onConfigurePartner(quest) }, enabled = !busy) { Text(stringResource(R.string.quest_configure_techniques)) }
        }
        if (quest.category == QuestCategory.RECRUITMENT) {
            Text(stringResource(R.string.quest_join_reward, quest.giverName), style = MaterialTheme.typography.bodyMedium)
        } else {
            Text(stringResource(R.string.quest_reward, quest.rewardBits, quest.rewardTrust), style = MaterialTheme.typography.bodyMedium)
            quest.rewardItemName?.let { Text("${quest.rewardItemQuantity} × $it", style = MaterialTheme.typography.bodySmall) }
            quest.rewardBattleItemId?.let { Text("${quest.rewardBattleItemQuantity} × ${battleItemLabel(it)}", style = MaterialTheme.typography.bodySmall) }
        }
        if (onAction != null && (actorId == null || actorId == quest.giverId)) {
            val primary = when (quest.state) {
                QuestState.OFFERED -> QuestActionType.ACCEPT
                QuestState.READY -> QuestActionType.TURN_IN
                QuestState.ACTIVE -> when {
                    current.any { it.type == QuestObjectiveType.COLLECT_TOKEN && it.targetIndividualId == null } -> QuestActionType.COLLECT
                    current.any { it.type in listOf(QuestObjectiveType.DELIVER_ITEM, QuestObjectiveType.DELIVER_TOKEN) && it.targetIndividualId == null } -> QuestActionType.DELIVER
                    else -> QuestActionType.STATUS
                }
                QuestState.DECLINED, QuestState.ABANDONED -> if (quest.category == QuestCategory.RECRUITMENT) QuestActionType.ACCEPT else null
                else -> null
            }
            primary?.let { action ->
                VitalButton(onClick = { onAction(quest, action) }, enabled = !busy && (quest.availabilityIssue == null ||
                    action == QuestActionType.STATUS || (action == QuestActionType.TURN_IN && quest.category == QuestCategory.NORMAL && quest.state == QuestState.READY)), modifier = Modifier.fillMaxWidth()) {
                    Text(questActionLabel(action))
                }
            }
            val secondary = when (quest.state) {
                QuestState.OFFERED -> QuestActionType.DECLINE
                QuestState.ACTIVE, QuestState.READY -> QuestActionType.ABANDON
                else -> null
            }
            secondary?.let { action -> TextButton(onClick = { onAction(quest, action) }, enabled = !busy) { Text(questActionLabel(action)) } }
        } else if (onOpen != null && (quest.state != QuestState.COMPLETED || pendingFollowUp(quest))) {
            VitalButton(onClick = { onOpen(quest) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.quest_open_contact)) }
        }
        if (quest.state == QuestState.ACTIVE) current.filter { it.targetIndividualId != null }.distinctBy { it.targetIndividualId }.forEach { target ->
            if (onTrack != null) TextButton(onClick = { onTrack(requireNotNull(target.targetIndividualId)) }, enabled = !busy) {
                Text(stringResource(R.string.quest_track_named_target, target.targetName.orEmpty()))
            }
        }
        quest.followUpTemplateId?.let { nextId ->
            Text(stringResource(R.string.quest_follow_up_plan, questTitle(nextId)), style = MaterialTheme.typography.bodyMedium)
            when {
                nextQuest != null -> TextButton(onClick = onShowNext, enabled = !busy) { Text(stringResource(R.string.quest_show_next)) }
                quest.followUpClosed -> Text(stringResource(R.string.quest_follow_up_closed,
                    followUpBlockLabel(quest.followUpBlock)), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                pendingFollowUp(quest) -> {
                    Text(stringResource(R.string.quest_follow_up_waiting, followUpBlockLabel(quest.followUpBlock)),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (onAction != null && (actorId == null || actorId == quest.giverId)) {
                        VitalButton(onClick = { onAction(quest, QuestActionType.STATUS) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.quest_prepare_next))
                        }
                        TextButton(onClick = { onAction(quest, QuestActionType.SKIP_FOLLOW_UP) }, enabled = !busy) {
                            Text(stringResource(R.string.quest_skip_next))
                        }
                    }
                }
                else -> Text(stringResource(R.string.quest_follow_up_after_completion), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val template = QuestTemplates.get(quest.templateId)
        Text(stringResource(R.string.quest_inspiration, template.sourceQuest), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun questTitle(id: String): String = stringResource(when (id) {
    "supplies" -> R.string.quest_title_supplies
    "rival" -> R.string.quest_title_rival
    "patrol" -> R.string.quest_title_patrol
    "training" -> R.string.quest_title_training
    "medicine" -> R.string.quest_title_medicine
    "missing" -> R.string.quest_title_missing
    "property" -> R.string.quest_title_property
    "courier" -> R.string.quest_title_courier
    "rescue" -> R.string.quest_title_rescue
    "supply_round" -> R.string.quest_title_supply_round
    "time_trial" -> R.string.quest_title_time_trial
    "careful_victory" -> R.string.quest_title_careful_victory
    "technique_trial" -> R.string.quest_title_technique_trial
    "recruitment_trial" -> R.string.quest_title_recruitment_trial
    "recruitment_service" -> R.string.quest_title_recruitment_service
    "recruitment_rescue" -> R.string.quest_title_recruitment_rescue
    "pp_training" -> R.string.quest_title_pp_training
    "pp_milestone" -> R.string.quest_title_pp_milestone
    "watch_adventure" -> R.string.quest_title_watch_adventure
    else -> R.string.quest_title_recruitment
})

@Composable
fun battleItemLabel(id: String): String = stringResource(when (id) {
    "quest_energy" -> R.string.quest_item_energy
    "quest_cleanse" -> R.string.quest_item_cleanse
    else -> R.string.quest_item_recovery
})

@Composable
private fun questState(state: QuestState): String = stringResource(when (state) {
    QuestState.OFFERED -> R.string.quest_state_offered
    QuestState.ACTIVE -> R.string.quest_state_active
    QuestState.READY -> R.string.quest_state_ready
    QuestState.COMPLETED -> R.string.quest_state_completed
    QuestState.DECLINED -> R.string.quest_state_declined
    QuestState.ABANDONED -> R.string.quest_state_abandoned
})

@Composable
private fun questActionLabel(action: QuestActionType): String = stringResource(when (action) {
    QuestActionType.ACCEPT -> R.string.quest_accept
    QuestActionType.DECLINE -> R.string.quest_decline
    QuestActionType.DELIVER -> R.string.quest_deliver
    QuestActionType.COLLECT -> R.string.quest_collect
    QuestActionType.TURN_IN -> R.string.quest_turn_in
    QuestActionType.ABANDON -> R.string.quest_abandon
    QuestActionType.STATUS -> R.string.quest_refresh
    QuestActionType.SKIP_FOLLOW_UP -> R.string.quest_skip_next
})

@Composable
private fun objectiveLabel(objective: QuestObjective, tokens: List<QuestToken>): String = when (objective.type) {
    QuestObjectiveType.DELIVER_ITEM -> if (objective.targetIndividualId == null)
        stringResource(R.string.quest_objective_deliver, objective.itemName.orEmpty())
        else stringResource(R.string.quest_objective_deliver_object, objective.itemName.orEmpty(), objective.targetName.orEmpty())
    QuestObjectiveType.RADAR_VICTORIES -> stringResource(R.string.quest_objective_radar)
    QuestObjectiveType.DEFEAT_TARGET -> stringResource(R.string.quest_objective_target, objective.targetName.orEmpty())
    QuestObjectiveType.WATCH_BATTLES -> stringResource(R.string.quest_objective_watch_battles)
    QuestObjectiveType.WATCH_WINS -> stringResource(R.string.quest_objective_watch_wins)
    QuestObjectiveType.WATCH_TROPHIES -> stringResource(R.string.quest_objective_watch_trophies)
    QuestObjectiveType.WATCH_PP_EARN -> stringResource(R.string.quest_objective_pp_earn)
    QuestObjectiveType.WATCH_PP_REACH -> stringResource(R.string.quest_objective_pp_reach)
    QuestObjectiveType.WATCH_ADVENTURE_ADVANCE -> stringResource(R.string.quest_objective_watch_adventure, objective.watchCardName.orEmpty())
    QuestObjectiveType.REACH_VITALS -> stringResource(R.string.quest_objective_vitals)
    QuestObjectiveType.PARTNER_STAGE -> stringResource(R.string.quest_objective_stage, objective.required + 1)
    QuestObjectiveType.USE_BATTLE_ITEM -> stringResource(R.string.quest_objective_battle_item, battleItemLabel(objective.battleItemId.orEmpty()))
    QuestObjectiveType.MEET_TARGET -> stringResource(R.string.quest_objective_meet, objective.targetName.orEmpty())
    QuestObjectiveType.RECOVER_PROPERTY -> stringResource(R.string.quest_objective_recover, objective.targetName.orEmpty())
    QuestObjectiveType.EQUIP_TECHNIQUE -> stringResource(R.string.quest_objective_equip, objective.techniqueName.orEmpty())
    QuestObjectiveType.WIN_BATTLE_CONDITION -> stringResource(R.string.quest_objective_trial, objective.targetName.orEmpty())
    QuestObjectiveType.COLLECT_TOKEN -> stringResource(R.string.quest_objective_collect,
        questTokenLabel(tokens.firstOrNull { it.id == objective.producesTokenId }?.kind ?: QuestTokenKind.LETTER))
    QuestObjectiveType.DELIVER_TOKEN -> {
        val item = questTokenLabel(tokens.firstOrNull { it.id == objective.tokenId }?.kind ?: QuestTokenKind.REPLY)
        if (objective.targetIndividualId == null) stringResource(R.string.quest_objective_return_object, item)
        else stringResource(R.string.quest_objective_deliver_object, item, objective.targetName.orEmpty())
    }
}

@Composable
fun QuestBattleRequirements(objective: QuestObjective) {
    val style = MaterialTheme.typography.bodySmall
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    objective.maxBattleMillis?.let { Text(stringResource(R.string.quest_condition_time, it / 1000), style = style, color = color) }
    objective.maxBattleItems?.let { Text(stringResource(R.string.quest_condition_items, it), style = style, color = color) }
    objective.minHealthPercent?.let { Text(stringResource(R.string.quest_condition_health, it), style = style, color = color) }
    objective.techniqueId?.let {
        Text(stringResource(R.string.quest_condition_hits, objective.techniqueName.orEmpty(), objective.minTechniqueHits ?: 1), style = style, color = color)
    }
    if (objective.alliedTeamSize != null && objective.opposingTeamSize != null) {
        Text(stringResource(R.string.quest_condition_teams, objective.alliedTeamSize, objective.opposingTeamSize), style = style, color = color)
    }
}

@Composable
private fun trialFailureLabel(failure: QuestBattleFailure): String = stringResource(when (failure) {
    QuestBattleFailure.NO_VICTORY -> R.string.quest_trial_no_victory
    QuestBattleFailure.REPORT_MISSING -> R.string.quest_trial_report_missing
    QuestBattleFailure.TEAM_SIZE -> R.string.quest_trial_team_size
    QuestBattleFailure.TIME_LIMIT -> R.string.quest_trial_time_limit
    QuestBattleFailure.ITEM_LIMIT -> R.string.quest_trial_item_limit
    QuestBattleFailure.HEALTH_LIMIT -> R.string.quest_trial_health_limit
    QuestBattleFailure.TECHNIQUE_HITS -> R.string.quest_trial_technique_hits
})

@Composable
fun questTokenLabel(kind: QuestTokenKind): String = stringResource(when (kind) {
    QuestTokenKind.LOST_PROPERTY -> R.string.quest_object_keepsake
    QuestTokenKind.LETTER -> R.string.quest_object_letter
    QuestTokenKind.REPLY -> R.string.quest_object_reply
})

fun pendingFollowUp(quest: QuestInstance): Boolean = quest.state == QuestState.COMPLETED &&
    quest.followUpTemplateId != null && quest.followUpQuestId == null && !quest.followUpClosed

@Composable
private fun followUpBlockLabel(reason: QuestFollowUpBlock?): String = stringResource(when (reason) {
    QuestFollowUpBlock.NEEDS_ACTIVE_PARTNER -> R.string.quest_follow_up_partner
    QuestFollowUpBlock.NEEDS_TARGET_CARDS -> R.string.quest_follow_up_cards
    QuestFollowUpBlock.NEEDS_SUPPLIES -> R.string.quest_follow_up_supplies
    QuestFollowUpBlock.NEEDS_RECOVERY_STOCK -> R.string.quest_follow_up_recovery
    QuestFollowUpBlock.WATCH_COUNTER_CAPACITY -> R.string.quest_follow_up_counter
    QuestFollowUpBlock.ANOTHER_REQUEST_OPEN -> R.string.quest_follow_up_existing
    QuestFollowUpBlock.GIVER_JOINED -> R.string.quest_follow_up_giver_joined
    QuestFollowUpBlock.RELATED_TARGET_JOINED -> R.string.quest_follow_up_target_joined
    QuestFollowUpBlock.RELATED_TARGET_UNAVAILABLE -> R.string.quest_follow_up_target_unavailable
    QuestFollowUpBlock.DECLINED -> R.string.quest_follow_up_declined
    QuestFollowUpBlock.PARENT_NOT_VERIFIED -> R.string.quest_follow_up_parent
    null -> R.string.quest_follow_up_preparing
})

@Composable
private fun availabilityLabel(issue: QuestAvailabilityIssue): String = stringResource(when (issue) {
    QuestAvailabilityIssue.GIVER_CARD_MISSING -> R.string.quest_unavailable_giver
    QuestAvailabilityIssue.TARGET_CARD_MISSING -> R.string.quest_unavailable_target
    QuestAvailabilityIssue.TARGET_JOINED -> R.string.quest_unavailable_joined
    QuestAvailabilityIssue.PARTNER_MISSING -> R.string.quest_unavailable_partner
    QuestAvailabilityIssue.WATCH_CARD_MISSING -> R.string.quest_unavailable_watch_card
    QuestAvailabilityIssue.WATCH_AREAS_EXHAUSTED -> R.string.quest_unavailable_watch_areas
})
