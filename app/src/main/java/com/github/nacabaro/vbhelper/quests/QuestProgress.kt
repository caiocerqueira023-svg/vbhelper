package com.github.nacabaro.vbhelper.quests

import com.github.cfogrady.vbnfc.data.NfcCharacter
import com.github.cfogrady.vbnfc.vb.VBNfcCharacter
import com.github.cfogrady.vbnfc.be.BENfcCharacter
import com.github.nacabaro.vbhelper.utils.DeviceType
import com.github.nacabaro.vbhelper.battle.offline.core.BattleOutcome
import com.github.nacabaro.vbhelper.database.AppDatabase
import com.github.nacabaro.vbhelper.domain.identity.WatchTransfer
import com.github.nacabaro.vbhelper.world.ecosystem.*

/** All methods run inside their producer's Room transaction, before destructive effects. */
class QuestProgress(private val db: AppDatabase) {
    private val dao get() = db.questDao()

    fun beginSourceLocked(quest: QuestInstance, sourceId: String, producedAt: Long, now: Long): Boolean {
        if (quest.state != QuestState.ACTIVE || producedAt < QuestSteps.startsAt(quest)) return false
        dao.recordPhase(QuestPhaseReceipt(quest.id, sourceId, quest.currentPhase, now))
        return dao.phaseReceipt(quest.id, sourceId)?.phase == quest.currentPhase
    }

    fun creditLocked(quest: QuestInstance, objective: QuestObjective, sourceId: String, amount: Int, now: Long) {
        if (quest.state != QuestState.ACTIVE || objective.phase != quest.currentPhase || amount <= 0 || objective.progress >= objective.required) return
        val credited = minOf(amount, objective.required - objective.progress)
        if (dao.recordEvidence(QuestEvidence(quest.id, objective.id, sourceId, credited, now)) == -1L) return
        check(dao.updateObjective(objective.copy(progress = objective.progress + credited)) == 1)
    }

    fun refreshLocked(id: String, now: Long = System.currentTimeMillis()) {
        val quest = dao.getQuest(id) ?: return
        if (quest.state != QuestState.ACTIVE) return
        val objectives = dao.objectives(id)
        val next = QuestSteps.nextPhase(objectives)
        if (objectives.isNotEmpty() && next == null) {
            check(dao.updateQuest(quest.copy(state = QuestState.READY, revision = quest.revision + 1)) == 1)
        } else if (next != null && next > quest.currentPhase) {
            check(dao.updateQuest(quest.copy(currentPhase = next, phaseStartedAt = now, revision = quest.revision + 1)) == 1)
        }
    }

    fun battleLocked(event: WorldInteraction, participants: List<WorldInteractionParticipant>, outcome: BattleOutcome, now: Long) {
        if (event.origin == InteractionOrigin.AUTONOMOUS) return
        val partners = participants.filter { it.role == InteractionRole.OWNED && it.side == InteractionSide.ALLIED }.map { it.individualId }.toSet()
        val targets = participants.filter { it.role == InteractionRole.WILD && it.side == InteractionSide.OPPOSING }.map { it.individualId }.toSet()
        for (quest in dao.active().filter { it.state == QuestState.ACTIVE && it.availabilityIssue == null && it.partnerId in partners && it.acceptedAt!! <= event.createdAt }) {
            if (!beginSourceLocked(quest, "battle:${event.id}", event.createdAt, now)) continue
            val requiredStage = dao.objectives(quest.id).firstOrNull { it.type == QuestObjectiveType.PARTNER_STAGE }?.required ?: 0
            val participantStage = participants.firstOrNull { it.individualId == quest.partnerId }?.cardCharacterId?.let(dao::stageForCardCharacter)
            if (participantStage == null || participantStage < requiredStage) continue
            for (objective in QuestSteps.current(quest, dao.objectives(quest.id))) {
                if (objective.type == QuestObjectiveType.PARTNER_STAGE) {
                    if (outcome == BattleOutcome.ALLIED_VICTORY) creditLocked(quest, objective, "battle:${event.id}", objective.required - objective.progress, now)
                    continue
                }
                if (objective.type == QuestObjectiveType.WIN_BATTLE_CONDITION &&
                    (objective.targetIndividualId == null || objective.targetIndividualId in targets)) {
                    val failure = QuestBattleConditions(db).failure(objective, requireNotNull(quest.partnerId), event.id, outcome)
                    dao.recordBattleAttempt(QuestBattleAttempt(quest.id, objective.id, event.id, failure, now))
                    if (failure == null) creditLocked(quest, objective, "battle:${event.id}", 1, now)
                    continue
                }
                val matches = objective.type == QuestObjectiveType.RADAR_VICTORIES ||
                    (objective.type == QuestObjectiveType.DEFEAT_TARGET && objective.targetIndividualId in targets)
                if (matches && outcome == BattleOutcome.ALLIED_VICTORY) creditLocked(quest, objective, "battle:${event.id}", 1, now)
            }
            refreshLocked(quest.id, now)
        }
    }

    fun captureExportLocked(token: String, individualId: String, character: NfcCharacter, now: Long) {
        val cardId = db.watchTransferDao().get(token)?.cardId ?: dao.cardForIndividual(individualId)
        val limit = cardId?.let { db.cardDao().getCardById(it)?.stageCount }?.takeIf { it in 1..254 }?.plus(1)
        val baseline = QuestWatchBaseline(token, individualId, character.charIndex.toInt(),
            WatchTransfer.capture(token, individualId, character).history,
            character.totalBattlesWon.toInt(), character.totalBattlesLost.toInt(), character.trophies.toInt(),
            (character as? VBNfcCharacter)?.totalTrophies?.toInt(), now,
            family = if (character is BENfcCharacter) DeviceType.BEDevice else DeviceType.VBDevice,
            cardId = cardId, adventureNext = character.nextAdventureMissionStage.toInt() and 0xff, adventureLimit = limit)
        val previous = dao.baseline(token)
        if (previous == null) dao.insertBaseline(baseline)
        else check(previous.copy(capturedAt = now, family = previous.family ?: baseline.family,
            cardId = previous.cardId ?: baseline.cardId, adventureNext = previous.adventureNext ?: baseline.adventureNext,
            adventureLimit = previous.adventureLimit ?: baseline.adventureLimit) == baseline) {
            "Watch quest baseline changed during an unfinished transfer."
        }
    }

    fun watchImportLocked(token: String?, individualId: String, character: NfcCharacter, fingerprint: String, now: Long) {
        val baseline = token?.let(dao::baseline) ?: return
        if (baseline.individualId != individualId) return
        val history = WatchTransfer.capture(token, individualId, character).history
        val observed = QuestWatchObservation(if (character is BENfcCharacter) DeviceType.BEDevice else DeviceType.VBDevice,
            dao.cardForIndividual(individualId), character.charIndex.toInt(), history, character.totalBattlesWon.toInt(),
            character.totalBattlesLost.toInt(), character.trophies.toInt(), (character as? VBNfcCharacter)?.totalTrophies?.toInt(),
            character.nextAdventureMissionStage.toInt() and 0xff)
        val evidence = QuestWatchEvidence.evaluate(baseline, observed)
        for (quest in dao.forPartner(individualId).filter { it.state == QuestState.ACTIVE && it.availabilityIssue == null && it.acceptedAt!! <= baseline.capturedAt }) {
            if (!beginSourceLocked(quest, "watch:$fingerprint", baseline.capturedAt, now)) continue
            for (objective in QuestSteps.current(quest, dao.objectives(quest.id))) {
                val delta = when (objective.type) {
                    QuestObjectiveType.WATCH_BATTLES -> evidence.battles ?: 0
                    QuestObjectiveType.WATCH_WINS -> evidence.wins ?: 0
                    QuestObjectiveType.WATCH_TROPHIES -> evidence.trophiesEarned ?: 0
                    QuestObjectiveType.WATCH_PP_EARN -> evidence.ppEarned ?: 0
                    QuestObjectiveType.WATCH_PP_REACH -> if ((evidence.ppCurrent ?: -1) >= objective.required) objective.required - objective.progress else 0
                    QuestObjectiveType.WATCH_ADVENTURE_ADVANCE -> if (objective.watchCardId == baseline.cardId) evidence.adventureAdvance ?: 0 else 0
                    else -> 0
                }
                creditLocked(quest, objective, "watch:$fingerprint", delta, now)
            }
            val outstanding = dao.objectives(quest.id).filter { it.phase == quest.currentPhase && it.progress < it.required }
            val notice = when {
                outstanding.any { it.type == QuestObjectiveType.WATCH_PP_EARN } && evidence.ppEarned == null -> QuestWatchNotice.PP_DISCONTINUITY.name
                outstanding.any { it.type == QuestObjectiveType.WATCH_ADVENTURE_ADVANCE } && evidence.adventureAdvance == null -> QuestWatchNotice.ADVENTURE_DISCONTINUITY.name
                outstanding.any { it.type == QuestObjectiveType.WATCH_TROPHIES } && evidence.trophiesEarned == null -> QuestWatchNotice.TROPHY_DISCONTINUITY.name
                outstanding.any { it.type in listOf(QuestObjectiveType.WATCH_BATTLES, QuestObjectiveType.WATCH_WINS) } && evidence.battles == null -> QuestWatchNotice.BATTLE_DISCONTINUITY.name
                else -> null
            }
            check(dao.updateQuest(quest.copy(lastWatchSyncAt = now, revision = quest.revision + 1,
                watchNotice = notice)) == 1)
            refreshLocked(quest.id, now)
        }
        dao.deleteBaseline(token)
    }
}
