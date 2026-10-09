package com.github.nacabaro.vbhelper.quests

import com.github.nacabaro.vbhelper.battle.offline.core.BattleSide
import com.github.nacabaro.vbhelper.battle.offline.core.BattleSnapshot
import com.github.nacabaro.vbhelper.database.AppDatabase
import com.github.nacabaro.vbhelper.world.ecosystem.InteractionOrigin
import com.github.nacabaro.vbhelper.world.ecosystem.InteractionRole
import com.github.nacabaro.vbhelper.world.ecosystem.InteractionSide
import com.github.nacabaro.vbhelper.world.ecosystem.InteractionState

/** Only the local radar battle owner supplies a terminal snapshot; dialogue never supplies reports. */
class QuestBattleFacts(private val db: AppDatabase) {
    suspend fun recordTerminalLocked(id: String, snapshot: BattleSnapshot, now: Long) {
        val result = snapshot.result ?: return
        val dao = db.questDao()
        if (dao.battleReport(id) != null) return
        val event = db.worldInteractionDao().getInteraction(id) ?: return
        if (event.origin == InteractionOrigin.AUTONOMOUS || event.state != InteractionState.PLAYER_CONTROLLED ||
            event.type != com.github.nacabaro.vbhelper.world.ecosystem.InteractionType.BATTLE) return
        require(result.elapsedMillis == snapshot.elapsedMillis && snapshot.elapsedMillis >= 0 && snapshot.statistics.itemsUsed >= 0)
        val participants = db.worldInteractionDao().getParticipants(id)
        val combatants = snapshot.alliedMembers + snapshot.opposingMembers
        require(combatants.size == participants.size && combatants.map { it.combatantId }.distinct().size == combatants.size)
        val identities = participants.associate { participant ->
            val allied = participant.side == InteractionSide.ALLIED
            val side = if (allied) BattleSide.ALLIED else BattleSide.OPPOSING
            val matching = combatants.single { combatant ->
                combatant.side == side && if (participant.role == InteractionRole.OWNED) {
                    combatant.sourceCharacterId == participant.ownedCharacterId
                } else {
                    combatant.combatantId == "${if (allied) "ally" else "opponent"}:${participant.individualId}"
                }
            }
            require(matching.maxHealth > 0 && matching.health in 0..matching.maxHealth)
            matching.combatantId to participant
        }
        require(identities.size == participants.size)
        val members = combatants.map { member ->
            QuestBattleMember(id, identities.getValue(member.combatantId).individualId,
                member.side == BattleSide.ALLIED, member.health, member.maxHealth)
        }
        val hits = snapshot.techniqueHitCounts.map { hit ->
            val actor = identities.getValue(hit.combatantId)
            val target = identities.getValue(hit.targetId)
            require(actor.side != target.side && hit.hits > 0)
            QuestBattleTechniqueHit(id, actor.individualId, target.individualId, hit.techniqueId, hit.hits)
        }
        val report = QuestBattleReport(id, result.outcome, snapshot.elapsedMillis, snapshot.statistics.itemsUsed,
            snapshot.alliedMembers.size, snapshot.opposingMembers.size, snapshot.rulesetVersion, now)
        if (dao.insertBattleReport(report) != -1L) {
            dao.insertBattleMembers(members)
            if (hits.isNotEmpty()) dao.insertBattleHits(hits)
        }
    }
}
