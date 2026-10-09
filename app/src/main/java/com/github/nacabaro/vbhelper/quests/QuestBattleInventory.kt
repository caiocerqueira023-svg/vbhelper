package com.github.nacabaro.vbhelper.quests

import com.github.nacabaro.vbhelper.battle.offline.core.BattleItemDefinition
import com.github.nacabaro.vbhelper.battle.offline.core.BattleItemKind
import com.github.nacabaro.vbhelper.battle.offline.core.BattleItemSnapshot
import com.github.nacabaro.vbhelper.database.AppDatabase
import com.github.nacabaro.vbhelper.world.ecosystem.InteractionRole
import com.github.nacabaro.vbhelper.world.ecosystem.InteractionSide
import com.github.nacabaro.vbhelper.world.ecosystem.InteractionState

class QuestBattleInventory(private val db: AppDatabase) {
    companion object {
        val catalog = listOf(
            BattleItemDefinition("quest_recovery", "Recovery pack", BattleItemKind.HEAL_HEALTH, 0, 900),
            BattleItemDefinition("quest_energy", "Energy pack", BattleItemKind.RESTORE_ENERGY, 0, 120),
            BattleItemDefinition("quest_cleanse", "Status remedy", BattleItemKind.CLEANSE_STATUS, 0)
        )
    }

    fun reserveLocked(id: String) {
        val dao = db.questDao()
        if (dao.reservations(id).isNotEmpty()) return
        val stock = dao.battleStock().filter { it.quantity > 0 && it.itemId in catalog.map { item -> item.itemId } }
        val reservations = stock.map { QuestBattleReservation(id, it.itemId, minOf(3, it.quantity)) }
        reservations.forEach { check(dao.adjustStock(it.itemId, -it.quantity) == 1) }
        dao.reserve(reservations)
    }

    fun loadoutLocked(id: String): List<BattleItemDefinition> = db.questDao().reservations(id).mapNotNull { reservation ->
        catalog.firstOrNull { it.itemId == reservation.itemId }?.copy(quantity = reservation.quantity - reservation.used)
    }

    suspend fun checkpointLocked(id: String, items: List<BattleItemSnapshot>, now: Long) {
        val event = db.worldInteractionDao().getInteraction(id) ?: return
        if (event.state != InteractionState.PLAYER_CONTROLLED) return
        val partners = db.worldInteractionDao().getParticipants(id).filter {
            it.role == InteractionRole.OWNED && it.side == InteractionSide.ALLIED
        }.map { it.individualId }.toSet()
        val dao = db.questDao()
        for (reservation in dao.reservations(id)) {
            val remaining = items.singleOrNull { it.itemId == reservation.itemId }?.remaining ?: continue
            val used = reservation.quantity - remaining
            require(used in reservation.used..reservation.quantity)
            if (used == reservation.used) continue
            check(dao.recordUsed(id, reservation.itemId, used) == 1)
            for (quest in dao.active().filter { it.state == QuestState.ACTIVE && it.availabilityIssue == null && it.partnerId in partners && it.acceptedAt!! <= event.createdAt }) {
                val progress = QuestProgress(db)
                val source = "item:$id:${reservation.itemId}:$used"
                if (!progress.beginSourceLocked(quest, source, now, now)) continue
                for (objective in QuestSteps.current(quest, dao.objectives(quest.id)).filter { it.type == QuestObjectiveType.USE_BATTLE_ITEM && it.battleItemId == reservation.itemId }) {
                    progress.creditLocked(quest, objective, source, used - reservation.used, now)
                }
                progress.refreshLocked(quest.id, now)
            }
        }
    }

    fun settleLocked(id: String, interrupted: Boolean = false) {
        val dao = db.questDao()
        for (reservation in dao.reservations(id)) {
            // An interrupted process has no trustworthy final simulator snapshot.
            if (!interrupted && reservation.quantity > reservation.used) {
                check(dao.adjustStock(reservation.itemId, reservation.quantity - reservation.used) == 1)
            }
        }
        dao.clearReservations(id)
    }
}
