package com.github.nacabaro.vbhelper.quests

import com.github.nacabaro.vbhelper.battle.offline.core.BattleOutcome
import com.github.nacabaro.vbhelper.database.AppDatabase

class QuestBattleConditions(private val db: AppDatabase) {
    fun failure(objective: QuestObjective, partnerId: String, id: String, outcome: BattleOutcome): QuestBattleFailure? {
        val hits = objective.techniqueId?.let { db.questDao().techniqueHits(id, partnerId, it, objective.targetIndividualId) } ?: 0
        return evaluate(objective, partnerId, id, outcome, db.questDao().battleReport(id), db.questDao().battleMember(id, partnerId), hits)
    }

    companion object {
    fun evaluate(objective: QuestObjective, partnerId: String, id: String, outcome: BattleOutcome,
                 report: QuestBattleReport?, member: QuestBattleMember?, hits: Int): QuestBattleFailure? {
        if (outcome != BattleOutcome.ALLIED_VICTORY) return QuestBattleFailure.NO_VICTORY
        if (report == null || member == null || report.interactionId != id || report.outcome != outcome ||
            member.interactionId != id || member.individualId != partnerId || !member.allied) return QuestBattleFailure.REPORT_MISSING
        if ((objective.alliedTeamSize != null && objective.alliedTeamSize != report.alliedTeamSize) ||
            (objective.opposingTeamSize != null && objective.opposingTeamSize != report.opposingTeamSize)) return QuestBattleFailure.TEAM_SIZE
        if (objective.maxBattleMillis != null && report.elapsedMillis > objective.maxBattleMillis) return QuestBattleFailure.TIME_LIMIT
        if (objective.maxBattleItems != null && report.itemsUsed > objective.maxBattleItems) return QuestBattleFailure.ITEM_LIMIT
        if (objective.minHealthPercent != null && member.health.toLong() * 100 < member.maxHealth.toLong() * objective.minHealthPercent) {
            return QuestBattleFailure.HEALTH_LIMIT
        }
        if (objective.techniqueId != null) {
            if (hits < (objective.minTechniqueHits ?: 1)) return QuestBattleFailure.TECHNIQUE_HITS
        }
        return null
    }
    }
}
