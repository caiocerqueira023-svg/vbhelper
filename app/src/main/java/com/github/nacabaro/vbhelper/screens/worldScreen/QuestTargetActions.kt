package com.github.nacabaro.vbhelper.screens.worldScreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.components.VitalButton
import com.github.nacabaro.vbhelper.quests.QuestObjectiveType
import com.github.nacabaro.vbhelper.quests.QuestTargetTask
import com.github.nacabaro.vbhelper.quests.QuestBattleTargetTask

@Composable
fun QuestTargetActions(tasks: List<QuestTargetTask>, enabled: Boolean, trials: List<QuestBattleTargetTask> = emptyList(),
                       onAction: (QuestTargetTask) -> Unit) {
    trials.forEach { trial ->
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.quest_target_for, trial.giverName, trial.objective.phase + 1),
                style = MaterialTheme.typography.bodyMedium)
            com.github.nacabaro.vbhelper.screens.digilineScreen.QuestBattleRequirements(trial.objective)
        }
    }
    tasks.forEach { task ->
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.quest_target_for, task.giverName, task.phase + 1),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (task.type == QuestObjectiveType.DELIVER_ITEM) Text(stringResource(R.string.quest_supply_requested,
                task.itemQuantity, task.itemName.orEmpty()), style = MaterialTheme.typography.bodyMedium)
            VitalButton(onClick = { onAction(task) }, enabled = enabled && task.canInteract, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(when (task.type) {
                    QuestObjectiveType.RECOVER_PROPERTY -> R.string.quest_recover_property
                    QuestObjectiveType.DELIVER_TOKEN -> R.string.quest_deliver_object
                    QuestObjectiveType.DELIVER_ITEM -> R.string.quest_deliver_supplies
                    else -> R.string.quest_meet_target
                }))
            }
            if (!task.canInteract) Text(stringResource(if (task.type == QuestObjectiveType.DELIVER_ITEM)
                R.string.quest_supply_missing else R.string.quest_object_missing), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
