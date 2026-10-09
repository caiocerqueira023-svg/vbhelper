package com.github.nacabaro.vbhelper.quests

/** Objectives in a phase run in parallel; phases run in strictly increasing order. */
object QuestSteps {
    fun nextPhase(objectives: List<QuestObjective>): Int? = objectives.filter { it.progress < it.required }.minOfOrNull { it.phase }
    fun current(quest: QuestInstance, objectives: List<QuestObjective>): List<QuestObjective> =
        objectives.filter { it.phase == quest.currentPhase && it.progress < it.required }

    fun needsPartner(objectives: List<QuestObjective>): Boolean = objectives.any { it.type in setOf(
        QuestObjectiveType.RADAR_VICTORIES, QuestObjectiveType.DEFEAT_TARGET,
        QuestObjectiveType.WATCH_BATTLES, QuestObjectiveType.WATCH_WINS,
        QuestObjectiveType.WATCH_TROPHIES, QuestObjectiveType.REACH_VITALS,
        QuestObjectiveType.PARTNER_STAGE, QuestObjectiveType.USE_BATTLE_ITEM,
        QuestObjectiveType.EQUIP_TECHNIQUE, QuestObjectiveType.WIN_BATTLE_CONDITION,
        QuestObjectiveType.WATCH_PP_EARN, QuestObjectiveType.WATCH_PP_REACH, QuestObjectiveType.WATCH_ADVENTURE_ADVANCE
    ) }

    fun startsAt(quest: QuestInstance): Long = quest.phaseStartedAt ?: quest.acceptedAt ?: Long.MAX_VALUE
}
