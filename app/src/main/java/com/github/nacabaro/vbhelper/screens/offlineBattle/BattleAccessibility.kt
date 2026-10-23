package com.github.nacabaro.vbhelper.screens.offlineBattle

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.*
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.battle.offline.core.CombatantSnapshot

/** Native equivalents for pixel-rendered selection, without a second visual command deck. */
@Composable
internal fun Modifier.accessibleBattle(
    allies: List<CombatantSnapshot>, opponents: List<CombatantSnapshot>,
    selectedAlly: CombatantSnapshot?, selectedOpponent: CombatantSnapshot?, enabled: Boolean,
    onSelectAlly: (String) -> Unit, onSelectOpponent: (String) -> Unit
): Modifier {
    val resources = LocalResources.current
    val description = (allies + opponents).joinToString("\n") { fighter ->
        resources.getString(R.string.audit_fighter_status, fighter.displayName, fighter.health, fighter.maxHealth, fighter.energy, fighter.maxEnergy)
    }
    val allyLabel = resources.getString(R.string.audit_select_partner)
    val opponentLabel = resources.getString(R.string.audit_select_opponent)
    val chosen = listOfNotNull(selectedAlly?.displayName, selectedOpponent?.displayName).joinToString(" · ")
    val actions = if (!enabled) emptyList() else allies.filter { it.health > 0 }.map { fighter ->
        CustomAccessibilityAction("$allyLabel: ${fighter.displayName}") { onSelectAlly(fighter.combatantId); true }
    } + opponents.filter { it.health > 0 }.map { fighter ->
        CustomAccessibilityAction("$opponentLabel: ${fighter.displayName}") { onSelectOpponent(fighter.combatantId); true }
    }
    return semantics {
        isTraversalGroup = true
        contentDescription = description.ifBlank { resources.getString(R.string.audit_battle_status) }
        stateDescription = chosen
        customActions = actions
    }
}
