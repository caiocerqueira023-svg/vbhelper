package com.github.nacabaro.vbhelper.quests

import androidx.room.withTransaction
import com.github.nacabaro.vbhelper.database.AppDatabase
import com.github.nacabaro.vbhelper.domain.device_data.DigimonIndividual
import com.github.nacabaro.vbhelper.domain.device_data.UserCharacter
import com.github.nacabaro.vbhelper.domain.digifarm.WildRelationship
import com.github.nacabaro.vbhelper.domain.identity.IndividualIdentity
import com.github.nacabaro.vbhelper.domain.personality.DigimonPersonalityGenerator
import com.github.nacabaro.vbhelper.source.CurrencyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.random.Random

enum class QuestActionType { ACCEPT, DECLINE, COLLECT, DELIVER, STATUS, TURN_IN, ABANDON, SKIP_FOLLOW_UP }
data class QuestDialogueAction(val type: QuestActionType, val questId: String, val revision: Long, val evidenceId: String,
                               val partnerId: String? = null)
data class QuestActionResult(val questId: String, val type: QuestActionType, val message: String, val recruited: Boolean = false)

class QuestRepository(private val db: AppDatabase, private val currency: CurrencyRepository? = null,
                      private val clock: () -> Long = System::currentTimeMillis) {
    private val dao get() = db.questDao()
    private val progress get() = QuestProgress(db)
    fun observeAll(): Flow<List<QuestWithObjectives>> = dao.observeAll()
    fun observeGiver(id: String): Flow<List<QuestWithObjectives>> = dao.observeGiver(id)
    fun observeContact(id: String): Flow<List<QuestWithObjectives>> = dao.observeContact(id)
    fun observePartners(): Flow<List<QuestPartnerOption>> = dao.observePartners()
    fun observeTargetTasks(): Flow<List<QuestTargetTask>> = dao.observeTargetTasks()
    fun observeBattleTargets(): Flow<List<QuestBattleTargetTask>> = dao.observeBattleTargets()

    private fun unavailableTemplate(id: String, hasActivePartner: Boolean, hasTargets: Boolean): QuestFollowUpBlock {
        val partnerDependent = id in setOf("rescue", "rival", "patrol", "training", "medicine", "technique_trial", "time_trial", "careful_victory")
        return when {
            partnerDependent && !hasActivePartner -> QuestFollowUpBlock.NEEDS_ACTIVE_PARTNER
            id == "medicine" -> QuestFollowUpBlock.NEEDS_RECOVERY_STOCK
            id == "training" -> QuestFollowUpBlock.WATCH_COUNTER_CAPACITY
            id == "supplies" || (id == "supply_round" && hasTargets) -> QuestFollowUpBlock.NEEDS_SUPPLIES
            else -> QuestFollowUpBlock.NEEDS_TARGET_CARDS
        }
    }

    private fun blockFollowUpLocked(parent: QuestInstance, reason: QuestFollowUpBlock, closed: Boolean = false) {
        val current = dao.getQuest(parent.id) ?: return
        if (current.followUpQuestId != null || current.followUpClosed) return
        if (current.followUpBlock != reason || current.followUpClosed != closed) {
            check(dao.updateQuest(current.copy(followUpBlock = reason, followUpClosed = closed,
                revision = current.revision + 1)) == 1)
        }
    }

    private fun closeUnmaterializedFollowUpsLocked(giverId: String, reason: QuestFollowUpBlock) {
        dao.forGiver(giverId).filter { it.followUpTemplateId != null && it.followUpQuestId == null && !it.followUpClosed }
            .forEach { blockFollowUpLocked(it, reason, closed = true) }
    }

    private suspend fun materializeFollowUpsLocked(giver: WildRelationship) {
        if (giver.recruitmentState == "RECRUITED") {
            closeUnmaterializedFollowUpsLocked(giver.individualId, QuestFollowUpBlock.GIVER_JOINED)
            return
        }
        for (parent in dao.pendingFollowUps(giver.individualId)) {
            val parentGoals = dao.objectives(parent.id)
            if (dao.reward(parent.id) == null || parentGoals.isEmpty() || parentGoals.any { it.progress < it.required }) {
                blockFollowUpLocked(parent, QuestFollowUpBlock.PARENT_NOT_VERIFIED)
                continue
            }
            val existing = dao.child(parent.id)
            if (existing != null) {
                check(dao.updateQuest(parent.copy(followUpQuestId = existing.id, followUpBlock = null,
                    revision = parent.revision + 1)) == 1)
                continue
            }
            if (dao.forGiver(giver.individualId).any { it.category == QuestCategory.NORMAL &&
                    it.state in listOf(QuestState.OFFERED, QuestState.ACTIVE, QuestState.READY) }) {
                blockFollowUpLocked(parent, QuestFollowUpBlock.ANOTHER_REQUEST_OPEN)
                continue
            }
            val child = createOffer(giver, false, dao.forGiver(giver.individualId).size,
                forcedTemplateId = requireNotNull(parent.followUpTemplateId), parent = parent)
            if (child != null) {
                val current = requireNotNull(dao.getQuest(parent.id))
                check(dao.updateQuest(current.copy(followUpQuestId = child.id, followUpBlock = null,
                    revision = current.revision + 1)) == 1)
            } else if (dao.getQuest(parent.id)?.followUpBlock == null) {
                blockFollowUpLocked(parent, QuestFollowUpBlock.NEEDS_TARGET_CARDS)
            }
        }
    }

    suspend fun ensureOffers(individualId: String) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val giver = db.wildRelationshipDao().get(individualId) ?: return@withTransaction
            if (giver.recruitmentState == "RECRUITED") {
                closeUnmaterializedFollowUpsLocked(individualId, QuestFollowUpBlock.GIVER_JOINED)
                return@withTransaction
            }
            if ((giver.contactUnlockedAt == null && giver.trust <= 75) ||
                dao.unfinishedTargetCount(individualId) > 0) return@withTransaction
            val recruiting = giver.trust >= 100 || giver.recruitmentState == "PENDING_RECRUITMENT"
            val before = dao.forGiver(individualId)
            if (recruiting && before.none { it.category == QuestCategory.RECRUITMENT }) createOffer(giver, true, before.size)
            materializeFollowUpsLocked(giver)
            val previous = dao.forGiver(individualId)
            if (!recruiting && dao.pendingFollowUps(individualId).isEmpty() &&
                previous.none { it.category == QuestCategory.NORMAL && it.state in listOf(QuestState.OFFERED, QuestState.ACTIVE, QuestState.READY) } &&
                previous.filter { it.category == QuestCategory.NORMAL }.maxOfOrNull { it.finishedAt ?: it.createdAt }
                    ?.let { clock() - it >= 6 * 60 * 60 * 1000L } != false) {
                createOffer(giver, false, previous.size)
            }
        }
    }

    private suspend fun createOffer(giver: WildRelationship, recruitment: Boolean, ordinal: Int,
                                    forcedTemplateId: String? = null, parent: QuestInstance? = null): QuestInstance? {
        val species = db.characterDao().getById(giver.cardCharacterId) ?: return null
        val active = db.userCharacterDao().getActiveCharacter().first()?.let { db.userCharacterDao().getCharacter(it.id) }
        val seed = parent?.let { it.seed * 31 + it.chainDepth + 1 } ?: ((giver.individualId.hashCode().toLong() shl 32) xor ordinal.toLong() xor species.id)
        val random = Random(seed)
        val items = db.itemDao().questItems()
        val supplies = items.filter { it.quantity > 0 }
        val worldCharacters = db.characterDao().getCharactersForWorldSpawns().sortedBy { it.id }
        val candidates = worldCharacters.filter { it.stage in 2..(active?.let {
            db.characterDao().getById(it.charId)?.stage?.plus(1)
        } ?: species.stage).coerceAtLeast(2) }
        val activeStage = active?.let { db.characterDao().getById(it.charId)?.stage }
        val activeCard = active?.let { dao.cardForCharacter(it.charId) }?.let { db.cardDao().getCardById(it) }
        val nextArea = activeCard?.let { db.cardProgressDao().getCardProgressSync(it.id) }
        val beTraining = active?.takeIf { it.characterType == com.github.nacabaro.vbhelper.utils.DeviceType.BEDevice }
            ?.let { db.userCharacterDao().getBeDataOrNull(it.id) }
        val trialCandidates = candidates.filter { it.stage <= (activeStage ?: 0) }
        val demonstrationTechniques = com.github.nacabaro.vbhelper.battle.offline.data.GenericTechniqueCatalog.selectableEntries
            .filter { it.rank <= 2 && it.definition.power > 0 }
        val effort = QuestDifficulty.recruitment(species.stage)
        val recruitmentCandidates = worldCharacters.filter { it.stage in (species.stage - 1).coerceAtLeast(2)..species.stage.coerceAtLeast(2) }
        val available = buildList {
            if (supplies.isNotEmpty()) add(QuestTemplates.supplies)
            if (candidates.isNotEmpty()) {
                add(QuestTemplates.missing)
                add(QuestTemplates.property)
                add(QuestTemplates.courier)
                if (supplies.sumOf { it.quantity.toLong() } >= 2) add(QuestTemplates.supplyRound)
            }
            if (active != null) {
                add(QuestTemplates.patrol)
                if (candidates.isNotEmpty()) {
                    add(QuestTemplates.rival)
                    add(QuestTemplates.rescue)
                    if (demonstrationTechniques.isNotEmpty()) add(QuestTemplates.techniqueTrial)
                }
                if (trialCandidates.isNotEmpty()) {
                    add(QuestTemplates.timeTrial)
                    add(QuestTemplates.carefulVictory)
                }
                if (active.totalBattlesWon + active.totalBattlesLost < 65000) add(QuestTemplates.training)
                if (dao.battleStock().any { it.itemId == "quest_recovery" && it.quantity > 0 }) add(QuestTemplates.medicine)
                if (beTraining != null) {
                    add(QuestTemplates.ppMilestone)
                    if (beTraining.remainingTrainingTimeInMinutes > 0 && active.trophies <= 65532) add(QuestTemplates.ppTraining)
                }
                if (activeCard != null && activeCard.stageCount in 1..254 && nextArea != null && nextArea in 1..activeCard.stageCount) add(QuestTemplates.watchAdventure)
            }
        }
        val profile = com.github.nacabaro.vbhelper.world.ecosystem.WorldSocialRepository(db).profile(giver.individualId)
        val weighted = available.flatMap { option ->
            val preference = when (option.id) {
                "rival", "patrol", "time_trial" -> profile.challenge
                "technique_trial", "training", "pp_training", "pp_milestone" -> profile.training
                "careful_victory" -> profile.patience
                "rescue", "medicine", "supply_round", "supplies" -> profile.empathy
                "courier" -> profile.loyalty
                else -> profile.curiosity
            }
            List(1 + (preference * 3).toInt().coerceIn(0, 3)) { option }
        }
        if (forcedTemplateId != null && available.none { it.id == forcedTemplateId }) {
            parent?.let { blockFollowUpLocked(it, unavailableTemplate(forcedTemplateId, active != null, candidates.isNotEmpty())) }
            return null
        }
        if (parent?.templateId == "missing" && forcedTemplateId == "rescue") {
            val related = dao.objectives(parent.id).firstOrNull { it.type == QuestObjectiveType.MEET_TARGET }
            val relatedId = related?.targetIndividualId
            if (relatedId == null || related.targetCardCharacterId?.let { db.characterDao().getById(it) } == null) {
                blockFollowUpLocked(parent, QuestFollowUpBlock.RELATED_TARGET_UNAVAILABLE)
                return null
            }
            if (db.userCharacterDao().getByIndividualIdSync(relatedId).isNotEmpty() ||
                db.wildRelationshipDao().get(relatedId)?.recruitmentState == "RECRUITED" ||
                db.watchTransferDao().getByIndividualId(relatedId) != null) {
                blockFollowUpLocked(parent, QuestFollowUpBlock.RELATED_TARGET_JOINED, closed = true)
                return null
            }
        }
        val recruitmentRoutes = if (species.stage >= 3 && recruitmentCandidates.isNotEmpty()) buildList {
            repeat(1 + (maxOf(profile.challenge, profile.training) * 3).toInt()) { add(QuestTemplates.recruitmentTrial) }
            repeat(1 + (profile.empathy * 3).toInt()) { add(QuestTemplates.recruitmentRescue) }
            if (supplies.sumOf { it.quantity.toLong() } >= effort.radarWins) {
                repeat(1 + (profile.loyalty * 3).toInt()) { add(QuestTemplates.recruitmentService) }
            }
        } else emptyList()
        val template = when {
            forcedTemplateId != null -> QuestTemplates.get(forcedTemplateId)
            recruitment -> if (recruitmentRoutes.isEmpty()) QuestTemplates.recruitment else recruitmentRoutes[random.nextInt(recruitmentRoutes.size)]
            else -> weighted.getOrNull(random.nextInt(weighted.size.coerceAtLeast(1))) ?: return null
        }
        val id = if (recruitment) "recruit:${giver.individualId}" else parent?.let { "followup:${it.id}" } ?: "quest:${giver.individualId}:$ordinal"
        val depth = parent?.chainDepth?.plus(1) ?: 0
        val rewardUnits = when (template.id) {
            "rescue", "supply_round" -> 3
            "property", "courier", "time_trial", "careful_victory", "technique_trial" -> 2
            else -> 1
        }
        val reward = items.filter { item ->
            item.itemType == com.github.nacabaro.vbhelper.domain.items.ItemType.UNIVERSAL ||
                (active?.characterType == com.github.nacabaro.vbhelper.utils.DeviceType.BEDevice && item.itemType == com.github.nacabaro.vbhelper.domain.items.ItemType.BEITEM) ||
                (active?.characterType == com.github.nacabaro.vbhelper.utils.DeviceType.VBDevice && item.itemType in listOf(
                    com.github.nacabaro.vbhelper.domain.items.ItemType.VBITEM, com.github.nacabaro.vbhelper.domain.items.ItemType.SPECIALMISSION))
        }.takeIf { it.isNotEmpty() }?.let { it[random.nextInt(it.size)] }
        val quest = QuestInstance(id, giver.individualId, species.id, giver.speciesNameSnapshot ?: "Digimon",
            if (recruitment) QuestCategory.RECRUITMENT else QuestCategory.NORMAL, template.id, maxOf(template.version, QuestChains.GENERATION_VERSION), seed,
            species.stage, rewardBits = if (recruitment) 0 else 300 * rewardUnits + 100 * species.stage.coerceAtLeast(0),
            rewardTrust = if (recruitment) 0 else 8 + 4 * (rewardUnits - 1),
            rewardItemId = if (recruitment) null else reward?.id, rewardItemName = if (recruitment) null else reward?.name,
            rewardItemQuantity = if (!recruitment && reward != null) 1 else 0,
            rewardBattleItemId = if (recruitment) null else QuestBattleInventory.catalog[random.nextInt(QuestBattleInventory.catalog.size)].itemId,
            rewardBattleItemQuantity = if (recruitment) 0 else if (rewardUnits == 3) 2 else 1,
            createdAt = clock(), parentQuestId = parent?.id, chainDepth = depth,
            followUpTemplateId = if (recruitment) null else QuestChains.nextTemplate(template.id, depth, profile))
        val objectives = mutableListOf<QuestObjective>()
        val tokens = mutableListOf<QuestToken>()
        fun token(kind: QuestTokenKind, suffix: String): String {
            val tokenId = "$id:object:$suffix"
            tokens += QuestToken(tokenId, id, kind)
            return tokenId
        }
        fun add(type: QuestObjectiveType, required: Int, itemId: Long? = null, itemName: String? = null,
                targetId: String? = null, cardId: Long? = null, name: String? = null,
                phase: Int = 0, tokenId: String? = null, producesTokenId: String? = null,
                techniqueId: String? = null, techniqueName: String? = null,
                maxBattleMillis: Long? = null, maxBattleItems: Int? = null,
                minHealthPercent: Int? = null, minTechniqueHits: Int? = null,
                alliedTeamSize: Int? = null, opposingTeamSize: Int? = null, watchCardId: Long? = null, watchCardName: String? = null) {
            objectives += QuestObjective("$id:${objectives.size}", id, objectives.size, type, required,
                itemId = itemId, itemName = itemName, targetIndividualId = targetId, targetCardCharacterId = cardId, targetName = name,
                phase = phase, tokenId = tokenId, producesTokenId = producesTokenId,
                techniqueId = techniqueId, techniqueName = techniqueName, maxBattleMillis = maxBattleMillis,
                maxBattleItems = maxBattleItems, minHealthPercent = minHealthPercent, minTechniqueHits = minTechniqueHits,
                alliedTeamSize = alliedTeamSize, opposingTeamSize = opposingTeamSize, watchCardId = watchCardId, watchCardName = watchCardName)
        }
        suspend fun target(pool: List<com.github.nacabaro.vbhelper.domain.card.CardCharacter> = candidates): Triple<String, com.github.nacabaro.vbhelper.domain.card.CardCharacter, String> {
            val character = pool[random.nextInt(pool.size)]
            val individual = IndividualIdentity.generate()
            val name = db.speciesProfileDao().getByCardCharacterId(character.id)?.speciesName ?: "Digimon"
            db.digimonIndividualDao().insert(DigimonIndividual(individual, clock()))
            db.digimonIndividualDao().upsertPersonality(DigimonPersonalityGenerator.generate(individual, character.attribute, character.stage, clock()))
            db.wildRelationshipDao().insert(WildRelationship(individual, character.id, name, createdAt = clock(), updatedAt = clock()))
            return Triple(individual, character, name)
        }
        fun watchPreparation() {
            add(QuestObjectiveType.PARTNER_STAGE, effort.partnerStage)
            if (effort.watchBattles > 0) {
                add(QuestObjectiveType.WATCH_BATTLES, effort.watchBattles)
                add(QuestObjectiveType.WATCH_WINS, effort.watchWins)
                if (active?.characterType == com.github.nacabaro.vbhelper.utils.DeviceType.VBDevice) {
                    val total = db.questDao().lifetimeTrophies(active.id) ?: 0
                    if (total <= 65535 - effort.trophies) add(QuestObjectiveType.WATCH_TROPHIES, effort.trophies)
                }
            }
        }
        when (template.id) {
            "supplies" -> {
                val item = supplies[random.nextInt(supplies.size)]
                add(QuestObjectiveType.DELIVER_ITEM, minOf(item.quantity, 1 + random.nextInt(2)), item.id, item.name)
            }
            "rival" -> {
                val rival = target()
                add(QuestObjectiveType.DEFEAT_TARGET, 1, targetId = rival.first, cardId = rival.second.id, name = rival.third)
            }
            "missing" -> {
                val friend = target()
                add(QuestObjectiveType.MEET_TARGET, 1, targetId = friend.first, cardId = friend.second.id, name = friend.third)
            }
            "property" -> {
                val finder = target()
                val property = token(QuestTokenKind.LOST_PROPERTY, "keepsake")
                add(QuestObjectiveType.RECOVER_PROPERTY, 1, targetId = finder.first, cardId = finder.second.id, name = finder.third,
                    producesTokenId = property)
                add(QuestObjectiveType.DELIVER_TOKEN, 1, phase = 1, tokenId = property)
            }
            "courier" -> {
                val recipient = target()
                val letter = token(QuestTokenKind.LETTER, "letter")
                val reply = token(QuestTokenKind.REPLY, "reply")
                add(QuestObjectiveType.COLLECT_TOKEN, 1, producesTokenId = letter)
                add(QuestObjectiveType.DELIVER_TOKEN, 1, targetId = recipient.first, cardId = recipient.second.id,
                    name = recipient.third, phase = 1, tokenId = letter, producesTokenId = reply)
                add(QuestObjectiveType.DELIVER_TOKEN, 1, phase = 2, tokenId = reply)
            }
            "rescue" -> {
                val previousFriend = if (parent?.templateId == "missing") dao.objectives(parent.id).firstOrNull { it.type == QuestObjectiveType.MEET_TARGET } else null
                val friend = if (previousFriend == null) target() else {
                    val friendId = previousFriend.targetIndividualId
                    val friendCard = previousFriend.targetCardCharacterId?.let { db.characterDao().getById(it) }
                    val followParent = parent
                    if (friendId == null || friendCard == null || followParent == null) {
                        followParent?.let { blockFollowUpLocked(it, QuestFollowUpBlock.RELATED_TARGET_UNAVAILABLE) }
                        return null
                    }
                    Triple(friendId, friendCard, previousFriend.targetName ?: "Digimon")
                }
                val threat = target()
                add(QuestObjectiveType.MEET_TARGET, 1, targetId = friend.first, cardId = friend.second.id, name = friend.third)
                add(QuestObjectiveType.DEFEAT_TARGET, 1, targetId = threat.first, cardId = threat.second.id, name = threat.third, phase = 1)
                add(QuestObjectiveType.PARTNER_STAGE, (threat.second.stage - 1).coerceAtLeast(0), phase = 1)
                add(QuestObjectiveType.MEET_TARGET, 1, targetId = friend.first, cardId = friend.second.id, name = friend.third, phase = 2)
            }
            "supply_round" -> {
                val remainingStock = supplies.associate { it.id to it.quantity }.toMutableMap()
                val groups = candidates.groupBy { it.attribute }.values.toList().shuffled(random)
                val stops = minOf(3L, supplies.sumOf { it.quantity.toLong() }).toInt()
                repeat(stops) { stop ->
                    val recipient = target(groups[stop % groups.size])
                    val itemChoices = supplies.filter { (remainingStock[it.id] ?: 0) > 0 }
                    val item = itemChoices[random.nextInt(itemChoices.size)]
                    remainingStock[item.id] = requireNotNull(remainingStock[item.id]) - 1
                    add(QuestObjectiveType.DELIVER_ITEM, 1, itemId = item.id, itemName = item.name,
                        targetId = recipient.first, cardId = recipient.second.id, name = recipient.third)
                }
            }
            "time_trial" -> {
                val opponent = target(trialCandidates)
                add(QuestObjectiveType.WIN_BATTLE_CONDITION, 1, targetId = opponent.first, cardId = opponent.second.id,
                    name = opponent.third, maxBattleMillis = 60_000, alliedTeamSize = 1, opposingTeamSize = 1)
            }
            "careful_victory" -> {
                val opponent = target(trialCandidates)
                add(QuestObjectiveType.WIN_BATTLE_CONDITION, 1, targetId = opponent.first, cardId = opponent.second.id,
                    name = opponent.third, maxBattleItems = 0, minHealthPercent = 40, alliedTeamSize = 1, opposingTeamSize = 1)
            }
            "technique_trial" -> {
                val opponent = target()
                val technique = demonstrationTechniques[random.nextInt(demonstrationTechniques.size)].definition
                add(QuestObjectiveType.EQUIP_TECHNIQUE, 1, techniqueId = technique.techniqueId, techniqueName = technique.displayName)
                add(QuestObjectiveType.WIN_BATTLE_CONDITION, 1, targetId = opponent.first, cardId = opponent.second.id,
                    name = opponent.third, phase = 1, techniqueId = technique.techniqueId, techniqueName = technique.displayName,
                    minTechniqueHits = 2, alliedTeamSize = 1, opposingTeamSize = 1)
            }
            "patrol" -> add(QuestObjectiveType.RADAR_VICTORIES, 2)
            "training" -> add(QuestObjectiveType.WATCH_BATTLES, 3)
            "pp_training" -> add(QuestObjectiveType.WATCH_PP_EARN, 3)
            "pp_milestone" -> add(QuestObjectiveType.WATCH_PP_REACH, 10)
            "watch_adventure" -> add(QuestObjectiveType.WATCH_ADVENTURE_ADVANCE, 1,
                watchCardId = requireNotNull(activeCard).id, watchCardName = activeCard.name)
            "medicine" -> objectives += QuestObjective("$id:0", id, 0, QuestObjectiveType.USE_BATTLE_ITEM, 1,
                battleItemId = "quest_recovery", itemName = "Recovery pack")
            "recruitment" -> {
                add(QuestObjectiveType.RADAR_VICTORIES, effort.radarWins)
                watchPreparation()
            }
            "recruitment_trial" -> {
                watchPreparation()
                val opponent = target(recruitmentCandidates)
                add(QuestObjectiveType.WIN_BATTLE_CONDITION, effort.radarWins,
                    targetId = opponent.first, cardId = opponent.second.id, name = opponent.third, phase = 1,
                    maxBattleItems = if (profile.patience > .65) 0 else null,
                    minHealthPercent = if (profile.patience > .8) 30 else null, alliedTeamSize = 1, opposingTeamSize = 1)
            }
            "recruitment_service" -> {
                watchPreparation()
                val stock = supplies.associate { it.id to it.quantity }.toMutableMap()
                repeat(effort.radarWins) {
                    val recipient = target(recruitmentCandidates)
                    val choices = supplies.filter { (stock[it.id] ?: 0) > 0 }
                    val item = choices[random.nextInt(choices.size)]
                    stock[item.id] = requireNotNull(stock[item.id]) - 1
                    add(QuestObjectiveType.DELIVER_ITEM, 1, item.id, item.name,
                        recipient.first, recipient.second.id, recipient.third, phase = 1)
                }
            }
            "recruitment_rescue" -> {
                watchPreparation()
                val friend = target(if (candidates.isEmpty()) recruitmentCandidates else candidates)
                val threat = target(recruitmentCandidates)
                add(QuestObjectiveType.MEET_TARGET, 1, targetId = friend.first, cardId = friend.second.id, name = friend.third, phase = 1)
                add(QuestObjectiveType.DEFEAT_TARGET, effort.radarWins, targetId = threat.first, cardId = threat.second.id, name = threat.third, phase = 2)
                add(QuestObjectiveType.MEET_TARGET, 1, targetId = friend.first, cardId = friend.second.id, name = friend.third, phase = 3)
            }
        }
        dao.insertQuest(quest)
        dao.insertObjectives(objectives)
        if (tokens.isNotEmpty()) dao.insertTokens(tokens)
        return quest
    }

    suspend fun act(giverId: String, action: QuestDialogueAction): QuestActionResult = withContext(Dispatchers.IO) {
        if (action.type == QuestActionType.TURN_IN) currency?.initializeWallet()
        val result = db.withTransaction {
            val saved = requireNotNull(dao.getQuest(action.questId)) { "Quest is no longer available." }
            require(saved.giverId == giverId) { "This quest belongs to a different contact." }
            require(saved.revision == action.revision) { "Quest progress changed. Open the current quest and try again." }
            val quest = refreshAvailabilityLocked(saved)
            val safeAction = action.type in setOf(QuestActionType.STATUS, QuestActionType.DECLINE, QuestActionType.ABANDON, QuestActionType.SKIP_FOLLOW_UP)
            require(safeAction || quest.availabilityIssue == null || (action.type == QuestActionType.TURN_IN &&
                quest.category == QuestCategory.NORMAL && quest.state == QuestState.READY)) { "Required quest data is unavailable: ${quest.availabilityIssue}. Recorded progress is preserved." }
            when (action.type) {
                QuestActionType.ACCEPT -> {
                    require(quest.state == QuestState.OFFERED || (quest.category == QuestCategory.RECRUITMENT && quest.state in listOf(QuestState.DECLINED, QuestState.ABANDONED)))
                    val active = dao.active()
                    require(active.count { it.category == quest.category } < if (quest.category == QuestCategory.RECRUITMENT) 1 else 3) { "Finish or abandon an active quest in this category first." }
                    for (target in dao.objectives(quest.id).mapNotNull { it.targetIndividualId }.distinct()) {
                        require(db.wildRelationshipDao().get(target)?.recruitmentState != "RECRUITED" &&
                            db.userCharacterDao().getByIndividualIdSync(target).isEmpty() &&
                            db.watchTransferDao().getByIndividualId(target) == null) {
                            "A required participant has joined the collection. Decline this invitation instead of spawning another copy."
                        }
                    }
                    val needsPartner = QuestSteps.needsPartner(dao.objectives(quest.id))
                    val selectedId = action.partnerId ?: quest.partnerId ?: if (needsPartner) dao.partners().firstOrNull { it.isActive }?.individualId else null
                    val partner = selectedId?.let { db.userCharacterDao().getByIndividualIdSync(it).singleOrNull() }
                    require(!needsPartner || partner != null) { "Select a partner in Storage before accepting." }
                    require(action.partnerId == null || partner != null) { "This selected partner is no longer in Storage." }
                    // Once bound, verified progress belongs to this permanent individual, even while on the watch.
                    require(quest.partnerId == null || quest.partnerId == partner?.individualId) { "Select the original quest partner before resuming." }
                    if (dao.objectives(quest.id).any { it.type == QuestObjectiveType.WATCH_TROPHIES }) {
                        require(partner?.characterType == com.github.nacabaro.vbhelper.utils.DeviceType.VBDevice) { "This offer requires a VB partner with a lifetime trophy counter." }
                    }
                    val watchGoals = dao.objectives(quest.id)
                    if (watchGoals.any { it.type in setOf(QuestObjectiveType.WATCH_PP_EARN, QuestObjectiveType.WATCH_PP_REACH) }) {
                        require(partner?.characterType == com.github.nacabaro.vbhelper.utils.DeviceType.BEDevice) { "Select a BE partner for PP objectives." }
                        if (watchGoals.any { it.type == QuestObjectiveType.WATCH_PP_EARN && it.progress < it.required }) {
                            require(requireNotNull(db.userCharacterDao().getBeDataOrNull(requireNotNull(partner).id)).remainingTrainingTimeInMinutes > 0) { "Restore the partner's BE training time before accepting." }
                            require(65535 - partner.trophies >= watchGoals.filter { it.type == QuestObjectiveType.WATCH_PP_EARN }.sumOf { it.required - it.progress }) { "The PP counter has no room for this objective." }
                        }
                    }
                    watchGoals.filter { it.type == QuestObjectiveType.WATCH_ADVENTURE_ADVANCE }.forEach { goal ->
                        require(partner != null && dao.cardForCharacter(partner.charId) == goal.watchCardId) { "Choose a partner from the quest's registered adventure card." }
                        val card = requireNotNull(db.cardDao().getCardById(requireNotNull(goal.watchCardId)))
                        val next = db.cardProgressDao().getCardProgressSync(card.id) ?: 0
                        require(next in 1..card.stageCount && card.stageCount in 1..254 && card.stageCount + 1 - next >= goal.required - goal.progress) { "No new observable adventure areas remain for this outgoing card progress." }
                    }
                    if (partner != null) {
                        val remaining = dao.objectives(quest.id).filter { it.progress < it.required }
                        val winsNeeded = remaining.filter { it.type == QuestObjectiveType.WATCH_WINS }.sumOf { it.required - it.progress }
                        val battlesNeeded = remaining.filter { it.type == QuestObjectiveType.WATCH_BATTLES }.sumOf { it.required - it.progress }
                        val winsCapacity = 65535 - partner.totalBattlesWon
                        val battleCapacity = winsCapacity + 65535 - partner.totalBattlesLost
                        require(winsCapacity >= winsNeeded && battleCapacity >= battlesNeeded) { "Choose a partner with room in its watch battle counters." }
                        val trophiesNeeded = remaining.filter { it.type == QuestObjectiveType.WATCH_TROPHIES }.sumOf { it.required - it.progress }
                        if (trophiesNeeded > 0) {
                            val total = requireNotNull(db.userCharacterDao().getVbDataOrNull(partner.id)).totalTrophies
                            require(65535 - total >= trophiesNeeded) { "Choose a VB partner with room in its lifetime trophy counter." }
                        }
                    }
                    val accepted = quest.copy(state = QuestState.ACTIVE, partnerId = partner?.individualId,
                        partnerDeviceType = partner?.characterType ?: quest.partnerDeviceType,
                        partnerName = dao.partners().firstOrNull { it.individualId == partner?.individualId }?.displayName ?: quest.partnerName,
                        currentPhase = QuestSteps.nextPhase(dao.objectives(quest.id)) ?: quest.currentPhase,
                        phaseStartedAt = clock(), acceptedAt = clock(), finishedAt = null, revision = quest.revision + 1)
                    check(dao.updateQuest(accepted) == 1)
                    if (partner != null) refreshPartnerLocked(accepted, partner) else progress.refreshLocked(accepted.id, clock())
                    QuestActionResult(quest.id, action.type, "Quest accepted. New accomplishments count from now; watch progress updates on NFC return.")
                }
                QuestActionType.DECLINE, QuestActionType.ABANDON -> {
                    require(if (action.type == QuestActionType.DECLINE) quest.state == QuestState.OFFERED else quest.state in listOf(QuestState.ACTIVE, QuestState.READY))
                    check(dao.updateQuest(quest.copy(state = if (action.type == QuestActionType.DECLINE) QuestState.DECLINED else QuestState.ABANDONED,
                        finishedAt = clock(), revision = quest.revision + 1,
                        followUpClosed = quest.followUpTemplateId != null, followUpBlock = if (quest.followUpTemplateId != null) QuestFollowUpBlock.DECLINED else quest.followUpBlock)) == 1)
                    dao.voidTokens(quest.id)
                    QuestActionResult(quest.id, action.type, "Quest closed without a trust penalty. Delivered items are not returned.")
                }
                QuestActionType.SKIP_FOLLOW_UP -> {
                    require(quest.state == QuestState.COMPLETED && quest.followUpTemplateId != null &&
                        quest.followUpQuestId == null && !quest.followUpClosed) { "There is no pending follow-up to skip." }
                    blockFollowUpLocked(quest, QuestFollowUpBlock.DECLINED, closed = true)
                    QuestActionResult(quest.id, action.type, "The pending follow-up was skipped without a trust penalty. Completed rewards remain unchanged.")
                }
                QuestActionType.COLLECT -> {
                    require(quest.state == QuestState.ACTIVE)
                    val outstanding = QuestSteps.current(quest, dao.objectives(quest.id)).filter {
                        it.type == QuestObjectiveType.COLLECT_TOKEN && it.targetIndividualId == null
                    }
                    require(outstanding.isNotEmpty()) { "There is no package to collect in this step." }
                    val source = "collect:${quest.revision}"
                    check(progress.beginSourceLocked(quest, source, clock(), clock()))
                    for (objective in outstanding) {
                        acquireProducedTokenLocked(quest, objective)
                        progress.creditLocked(quest, objective, source, 1, clock())
                    }
                    check(dao.updateQuest(quest.copy(revision = quest.revision + 1)) == 1)
                    progress.refreshLocked(quest.id, clock())
                    QuestActionResult(quest.id, action.type, "The registered quest package is now in your quest-object inventory.")
                }
                QuestActionType.DELIVER -> {
                    require(quest.state == QuestState.ACTIVE)
                    val outstanding = QuestSteps.current(quest, dao.objectives(quest.id)).filter {
                        it.targetIndividualId == null && it.type in listOf(QuestObjectiveType.DELIVER_ITEM, QuestObjectiveType.DELIVER_TOKEN)
                    }
                    require(outstanding.isNotEmpty()) { "There are no outstanding deliveries." }
                    check(progress.beginSourceLocked(quest, "delivery:${quest.revision}", clock(), clock()))
                    for (objective in outstanding) {
                        val quantity = objective.required - objective.progress
                        if (objective.type == QuestObjectiveType.DELIVER_ITEM) {
                            check(db.itemDao().handOver(requireNotNull(objective.itemId), quantity) == 1) { "Not enough ${objective.itemName}." }
                        } else deliverQuestTokenLocked(quest, objective)
                        progress.creditLocked(quest, objective, "delivery:${quest.revision}", quantity, clock())
                    }
                    check(dao.updateQuest(quest.copy(revision = quest.revision + 1)) == 1)
                    progress.refreshLocked(quest.id, clock())
                    QuestActionResult(quest.id, action.type, "The requested supplies or quest objects were delivered and consumed.")
                }
                QuestActionType.STATUS -> {
                    refreshLocalLocked(quest)
                    if (quest.state == QuestState.COMPLETED) db.wildRelationshipDao().get(giverId)?.let { materializeFollowUpsLocked(it) }
                    QuestActionResult(quest.id, action.type, contextFor(quest.id))
                }
                QuestActionType.TURN_IN -> {
                    require(quest.category == QuestCategory.NORMAL) { "Recruitment turn-in must use the recruitment transaction." }
                    refreshLocalLocked(quest)
                    val ready = requireNotNull(dao.getQuest(quest.id))
                    require(ready.state == QuestState.READY) { "Some quest requirements are still incomplete." }
                    claimRewardsLocked(ready)
                    QuestActionResult(quest.id, action.type, "Quest completed. Granted ${ready.rewardBits} bits, ${ready.rewardTrust} trust and the registered item rewards.")
                }
            }
        }
        ensureOffers(giverId)
        result
    }

    fun recruitmentReadyLocked(individualId: String): Boolean {
        val quest = dao.forGiver(individualId).singleOrNull { it.category == QuestCategory.RECRUITMENT } ?: return false
        return quest.state == QuestState.READY && dao.objectives(quest.id).isNotEmpty() &&
            dao.objectives(quest.id).all { it.progress >= it.required } && dao.reward(quest.id) == null
    }

    fun completeRecruitmentLocked(individualId: String) {
        require(recruitmentReadyLocked(individualId)) { "Complete this Digimon's recruitment quest first." }
        val quest = dao.forGiver(individualId).single { it.category == QuestCategory.RECRUITMENT }
        check(dao.recordReward(QuestRewardReceipt(quest.id, clock())) != -1L)
        check(dao.updateQuest(quest.copy(state = QuestState.COMPLETED, finishedAt = clock(), revision = quest.revision + 1)) == 1)
        closeUnmaterializedFollowUpsLocked(individualId, QuestFollowUpBlock.GIVER_JOINED)
    }

    private suspend fun claimRewardsLocked(quest: QuestInstance) {
        check(dao.recordReward(QuestRewardReceipt(quest.id, clock())) != -1L) { "Quest reward already claimed." }
        if (quest.rewardBits > 0) check(dao.addBits(quest.rewardBits) == 1) { "The wallet must be initialized before claiming rewards." }
        if (quest.rewardItemQuantity > 0) check(db.itemDao().grantQuestItem(requireNotNull(quest.rewardItemId), quest.rewardItemQuantity) == 1)
        if (quest.rewardBattleItemQuantity > 0) {
            val item = requireNotNull(quest.rewardBattleItemId)
            dao.initializeStock(QuestBattleStock(item, 0))
            check(dao.adjustStock(item, quest.rewardBattleItemQuantity) == 1)
        }
        val giver = db.wildRelationshipDao().get(quest.giverId)
        if (giver != null && giver.recruitmentState != "RECRUITED") {
            val trust = (giver.trust + quest.rewardTrust).coerceIn(0, 100)
            db.wildRelationshipDao().updateTrust(giver.individualId, trust, clock())
            db.worldSpawnDao().updateMood(giver.individualId, trust)
        }
        check(dao.updateQuest(quest.copy(state = QuestState.COMPLETED, finishedAt = clock(), revision = quest.revision + 1)) == 1)
        db.wildRelationshipDao().get(quest.giverId)?.let { giver ->
            if (giver.recruitmentState == "RECRUITED") closeUnmaterializedFollowUpsLocked(quest.giverId, QuestFollowUpBlock.GIVER_JOINED)
            else materializeFollowUpsLocked(giver)
        }
    }

    suspend fun refreshGiver(id: String) = withContext(Dispatchers.IO) {
        db.withTransaction { dao.forGiver(id).filter { it.state in setOf(QuestState.OFFERED, QuestState.ACTIVE, QuestState.READY) }.forEach {
            val current = refreshAvailabilityLocked(it)
            if (current.state == QuestState.ACTIVE) refreshLocalLocked(current)
        } }
        ensureOffers(id)
    }

    private suspend fun refreshAvailabilityLocked(quest: QuestInstance): QuestInstance {
        val remaining = dao.objectives(quest.id).filter { it.progress < it.required }
        val partner = quest.partnerId
        val transfer = partner?.let { db.watchTransferDao().getByIndividualId(it) }
        var issue: QuestAvailabilityIssue? = if (dao.stageForCardCharacter(quest.giverCardCharacterId) == null) QuestAvailabilityIssue.GIVER_CARD_MISSING else null
        if (issue == null && remaining.any { it.targetCardCharacterId != null && dao.stageForCardCharacter(it.targetCardCharacterId) == null }) issue = QuestAvailabilityIssue.TARGET_CARD_MISSING
        if (issue == null) for (target in remaining.mapNotNull { it.targetIndividualId }.distinct()) {
            if (db.userCharacterDao().getByIndividualIdSync(target).isNotEmpty() || db.watchTransferDao().getByIndividualId(target) != null ||
                db.wildRelationshipDao().get(target)?.recruitmentState == "RECRUITED") { issue = QuestAvailabilityIssue.TARGET_JOINED; break }
        }
        if (issue == null && partner != null && transfer == null && db.userCharacterDao().getByIndividualIdSync(partner).isEmpty()) issue = QuestAvailabilityIssue.PARTNER_MISSING
        if (issue == null) for (goal in remaining.filter { it.type == QuestObjectiveType.WATCH_ADVENTURE_ADVANCE }) {
            val card = goal.watchCardId?.let { db.cardDao().getCardById(it) }
            if (card == null) { issue = QuestAvailabilityIssue.WATCH_CARD_MISSING; break }
            val next = db.cardProgressDao().getCardProgressSync(card.id) ?: 0
            if (transfer == null && (next !in 1..card.stageCount || card.stageCount !in 1..254)) {
                issue = QuestAvailabilityIssue.WATCH_AREAS_EXHAUSTED; break
            }
        }
        if (issue == quest.availabilityIssue) return quest
        val changed = quest.copy(availabilityIssue = issue, revision = quest.revision + 1)
        check(dao.updateQuest(changed) == 1)
        return changed
    }

    private suspend fun refreshLocalLocked(quest: QuestInstance) {
        val partner = quest.partnerId?.let { db.userCharacterDao().getByIndividualIdSync(it).singleOrNull() } ?: return
        refreshPartnerLocked(quest, partner)
    }

    private suspend fun refreshPartnerLocked(quest: QuestInstance, partner: UserCharacter) {
        val stage = db.characterDao().getById(partner.charId)?.stage ?: return
        val loadout = com.github.nacabaro.vbhelper.battle.offline.data.GenericTechniqueLoadout.resolve(
            db.digimonTechniqueLoadoutDao().getForIndividual(partner.individualId).map { it.techniqueId })
        for (objective in QuestSteps.current(quest, dao.objectives(quest.id))) {
            val met = when (objective.type) {
                QuestObjectiveType.PARTNER_STAGE -> stage >= objective.required
                QuestObjectiveType.REACH_VITALS -> partner.vitalPoints >= objective.required
                QuestObjectiveType.EQUIP_TECHNIQUE -> objective.techniqueId in loadout
                else -> false
            }
            if (met) progress.creditLocked(quest, objective, "reach:${partner.charId}:${partner.vitalPoints}", objective.required - objective.progress, clock())
        }
        progress.refreshLocked(quest.id, clock())
    }

    fun contextFor(id: String): String {
        val quest = dao.getQuest(id) ?: return "Quest unavailable."
        val template = QuestTemplates.get(quest.templateId)
        return "Quest ${quest.id}; revision=${quest.revision}; ${template.title}; ${quest.category}; state=${quest.state}; " +
            "chain depth=${quest.chainDepth}; previous quest=${quest.parentQuestId}; planned next=${quest.followUpTemplateId}; " +
            "availability=${quest.availabilityIssue}; " +
            "next quest=${quest.followUpQuestId}; follow-up waiting reason=${quest.followUpBlock}; chain closed=${quest.followUpClosed}; " +
            "current step=${quest.currentPhase + 1}; bound partner=${quest.partnerName.orEmpty()} (${quest.partnerId ?: "not selected"}); rewards=${quest.rewardBits} bits, ${quest.rewardTrust} trust, " +
            "${quest.rewardItemQuantity} ${quest.rewardItemName.orEmpty()}, ${quest.rewardBattleItemQuantity} ${quest.rewardBattleItemId.orEmpty()}.\n" +
            dao.objectives(id).joinToString("\n") { "step=${it.phase + 1}; ${it.type}: ${it.progress}/${it.required}; " +
                "unlocked=${it.phase == quest.currentPhase}; item=${it.itemName.orEmpty()}; target=${it.targetName.orEmpty()} (${it.targetIndividualId.orEmpty()}); " +
                "object=${it.tokenId?.let(dao::token)?.kind}; produces=${it.producesTokenId?.let(dao::token)?.kind}; " +
                "technique=${it.techniqueName.orEmpty()} (${it.techniqueId.orEmpty()}); time limit ms=${it.maxBattleMillis}; " +
                "item limit=${it.maxBattleItems}; final partner health minimum percent=${it.minHealthPercent}; " +
                "successful required-technique hits=${it.minTechniqueHits}; teams=${it.alliedTeamSize} versus ${it.opposingTeamSize}; watch card=${it.watchCardName} (${it.watchCardId})" } +
            "\nQuest-only objects: " + dao.tokens(id).joinToString { "${it.kind}=${it.state}" } +
            "\nLatest recorded trial attempt: " + (dao.battleAttempts(id).firstOrNull()?.let { it.failure?.name ?: "requirements matched" } ?: "none") +
            "\nWatch requirements count newly verified NFC-return deltas, never player claims or app battle totals. " +
            "PARTNER_STAGE is a zero-based minimum stage; DELIVER_ITEM consumes stock through the app action. " +
            "Later steps stay locked until the current step is completed. Future-step activity is not retroactive. " +
            "COLLECT_TOKEN receives the giver's registered package. DELIVER_TOKEN consumes a held quest object; " +
            "delivery to a recipient, MEET_TARGET and RECOVER_PROPERTY require the physical Radar interaction action. " +
            "At the giver, COLLECT/DELIVER can be requested through Digiline. Rescue is meet friend, defeat threat, then return to friend. " +
            "DELIVER_ITEM with a target consumes the exact inventory item at that recipient through Radar. " +
            "EQUIP_TECHNIQUE means having an existing selectable catalogue skill equipped, not learning a new skill. " +
            "WIN_BATTLE_CONDITION counts only a committed victory whose actual terminal report meets ALL registered conditions. " +
            "Technique hits are positive damaging hits by the bound partner against the specified opponent; missed attacks, " +
            "healing, mere loadout presence and teammate attacks do not satisfy a demonstration. " +
            "Radar victories require the bound partner. Required targets appear when Radar has a fresh location. " +
            "A follow-up is a separate offer, never automatically accepted. It requires the parent's actual completion/reward receipt; " +
            "a waiting next request cannot be accepted until it has its own saved quest ID. STATUS may retry preparing a waiting " +
            "follow-up; SKIP_FOLLOW_UP permanently ends that pending edge only when explicitly requested. " +
            "Completed parents cannot be turned in again. Recruitment training is stage-scaled and mandatory for Champion+; " +
            "new recruitment routes unlock their service, challenge or rescue steps after watch preparation. " +
            "WATCH_PP_EARN is new BE PP observed within a continuous form; evolution/reset gaps are not guessed. " +
            "WATCH_PP_REACH is an explicit absolute milestone observed on NFC return; existing PP is allowed. " +
            "WATCH_ADVENTURE_ADVANCE is a new next-area increase against the exact outgoing individual/card baseline, " +
            "never shared card totals or repeated clears. Unavailable assets preserve progress and never reroll accepted requirements. " +
            "\nCurrent blockers: availability=${quest.availabilityIssue?.let(::describeAvailability) ?: "none, all required data is present"}; " +
            "watch sync=${quest.watchNotice?.let(::describeWatchNotice) ?: "no discontinuity"}. " +
            "If this quest is blocked, explain plainly what is missing and what the player must do about it; " +
            "do not promise its completion, invent workarounds, or claim progress the app has not recorded. " +
            "COMPLETED quests are finished history: celebrate them, but never re-accept, re-complete, or re-reward them. " +
            "Explain a motivation that fits your personality and these tasks; do not invent past incidents or relationships."
    }

    fun contextForGiver(id: String): String = dao.forGiver(id).filter {
        it.state in listOf(QuestState.OFFERED, QuestState.ACTIVE, QuestState.READY) ||
            (it.state == QuestState.COMPLETED && it.followUpTemplateId != null && it.followUpQuestId == null && !it.followUpClosed)
    }
        .joinToString("\n\n") { contextFor(it.id) }.ifBlank { "No current quest offer. Maximum trust unlocks a recruitment quest, never automatic joining." }

    fun contextForContact(id: String): String {
        val participating = dao.active().filter { quest -> quest.giverId != id && dao.objectives(quest.id).any { it.targetIndividualId == id } }
        return contextForGiver(id) + participating.joinToString("\n\n", prefix = if (participating.isEmpty()) "" else "\n\nRegistered participant roles:\n") {
            val roles = dao.objectives(it.id).filter { objective ->
                objective.targetIndividualId == id && objective.progress < objective.required
            }.joinToString("; ") { objective ->
                "${objective.type} at step ${objective.phase + 1} (${objective.progress}/${objective.required})" +
                    (objective.tokenId?.let { token -> dao.token(token)?.let { "; quest object ${it.kind} is ${it.state}" } }.orEmpty())
            }.ifBlank { "no outstanding steps involving you" }
            "You are a target/recipient in ${it.giverName}'s quest ${it.id}, not its giver. " +
                "Your recorded role: $roles. Discuss the recorded facts only; " +
                "the player completes physical meetings and handovers using Radar, not by claiming them in chat.\n${contextFor(it.id)}"
        }
    }

    /** Quests this individual is assigned to as the bound partner. Empty when none. */
    fun contextForPartner(id: String): String {
        val bound = dao.forPartner(id).filter { it.state in listOf(QuestState.ACTIVE, QuestState.READY) }
        if (bound.isEmpty()) return ""
        return "Quests you are assigned to as the bound partner:\n" + bound.joinToString("\n\n") {
            "You are the selected partner for ${it.giverName}'s quest ${it.id} (state=${it.state}). " +
                "Radar battles field you automatically; watch objectives advance only through your own NFC returns with new activity. " +
                "Only verified new accomplishments count, never claims.\n${contextFor(it.id)}"
        }
    }

    /** Refreshes local (stage/vitals/loadout) objectives for quests bound to this partner. */
    suspend fun refreshPartner(id: String) = withContext(Dispatchers.IO) {
        db.withTransaction { dao.forPartner(id).filter { it.state == QuestState.ACTIVE }.forEach { refreshLocalLocked(it) } }
    }

    fun partnerContext(): String = dao.partners().take(40).joinToString("\n") {
        "partnerId=${it.individualId}; name=${it.displayName}; stage=${it.stage}; device=${it.deviceType}; active=${it.isActive}"
    }

    private fun describeAvailability(issue: QuestAvailabilityIssue): String = when (issue) {
        QuestAvailabilityIssue.GIVER_CARD_MISSING -> "the giver's card data is missing; recorded progress is safe but no new steps can start"
        QuestAvailabilityIssue.TARGET_CARD_MISSING -> "a required participant's card data is missing; that step cannot complete until the data is restored"
        QuestAvailabilityIssue.TARGET_JOINED -> "a required participant already joined the collection, so this quest cannot create another copy"
        QuestAvailabilityIssue.PARTNER_MISSING -> "the bound partner is neither in Storage nor on a recorded watch transfer"
        QuestAvailabilityIssue.WATCH_CARD_MISSING -> "the registered adventure card is unavailable; another card cannot substitute"
        QuestAvailabilityIssue.WATCH_AREAS_EXHAUSTED -> "no new adventure areas remain in the outgoing card progress"
    }

    private fun describeWatchNotice(notice: String): String = when (notice) {
        "PP_DISCONTINUITY" -> "PP reset or the partner changed form; verified gains stand, unobserved gains are not guessed, start a fresh session before evolving"
        "ADVENTURE_DISCONTINUITY" -> "the last return had no comparable adventure baseline; shared card totals and repeated clears never count"
        "TROPHY_DISCONTINUITY" -> "the trophy counter reset or changed form; verified gains stand"
        else -> "a battle counter reset; verified gains stand, start a fresh watch session"
    }

    /**
     * Fields the bound quest partner for a Radar battle instead of the active
     * partner, by switching the stored active selection. Battles against a quest
     * target use that quest's partner; other battles only switch when exactly one
     * active quest needs battle victories or item use, so multi-quest rosters are
     * never guessed. Returns the partner display name when a switch happened.
     */
    suspend fun useBoundPartnerForBattle(targetIndividualId: String): String? = withContext(Dispatchers.IO) {
        db.withTransaction {
            val targeted = dao.battleTargetFor(targetIndividualId)
            val partnerId = if (targeted != null) {
                dao.getQuest(targeted.objective.questId)
                    ?.takeIf { it.state == QuestState.ACTIVE && it.availabilityIssue == null }?.partnerId
            } else {
                val candidates = dao.active().filter { quest ->
                    quest.state == QuestState.ACTIVE && quest.availabilityIssue == null && quest.partnerId != null &&
                        QuestSteps.current(quest, dao.objectives(quest.id)).any {
                            it.type == QuestObjectiveType.RADAR_VICTORIES || it.type == QuestObjectiveType.USE_BATTLE_ITEM
                        }
                }
                if (candidates.size != 1) return@withTransaction null
                candidates.single().partnerId
            } ?: return@withTransaction null
            val partner = db.userCharacterDao().getByIndividualIdSync(partnerId).singleOrNull()
                ?: return@withTransaction null
            if (partner.isActive) return@withTransaction null
            db.userCharacterDao().setOnlyActiveCharacter(partner.id)
            dao.partners().firstOrNull { it.individualId == partnerId }?.displayName
        }
    }

    private fun acquireProducedTokenLocked(quest: QuestInstance, objective: QuestObjective) {
        val tokenId = requireNotNull(objective.producesTokenId) { "This objective has no registered quest object." }
        check(dao.acquireToken(tokenId, quest.id, clock()) == 1) { "Quest object already collected or unavailable." }
    }

    private fun deliverQuestTokenLocked(quest: QuestInstance, objective: QuestObjective) {
        check(dao.deliverToken(requireNotNull(objective.tokenId), quest.id, clock()) == 1) { "The required quest object is not held." }
        if (objective.producesTokenId != null) acquireProducedTokenLocked(quest, objective)
    }

    /** The coordinator owns the transaction, fresh fix, encounter range and mutation epoch. */
    suspend fun interactTargetLocked(individualId: String, objectiveId: String, expectedRevision: Long,
                                     fix: com.github.nacabaro.vbhelper.world.ecosystem.WorldPlayerFix): QuestInstance {
        val objective = dao.pendingTargets().singleOrNull { it.id == objectiveId && it.targetIndividualId == individualId }
            ?: throw com.github.nacabaro.vbhelper.world.ecosystem.RadarCommandException(rejection = com.github.nacabaro.vbhelper.world.ecosystem.RadarRejection.UNAVAILABLE)
        val quest = dao.getQuest(objective.questId)
            ?: throw com.github.nacabaro.vbhelper.world.ecosystem.RadarCommandException(rejection = com.github.nacabaro.vbhelper.world.ecosystem.RadarRejection.UNAVAILABLE)
        val allowed = objective.type in setOf(QuestObjectiveType.MEET_TARGET, QuestObjectiveType.RECOVER_PROPERTY,
            QuestObjectiveType.DELIVER_TOKEN, QuestObjectiveType.DELIVER_ITEM)
        if (!allowed || quest.revision != expectedRevision || !fix.isFresh(clock())) {
            throw com.github.nacabaro.vbhelper.world.ecosystem.RadarCommandException(rejection = com.github.nacabaro.vbhelper.world.ecosystem.RadarRejection.UNAVAILABLE)
        }
        val spawn = db.worldSpawnDao().getByIndividualId(individualId)
            ?: throw com.github.nacabaro.vbhelper.world.ecosystem.WorldInteractionException(com.github.nacabaro.vbhelper.world.ecosystem.InteractionFailure.UNAVAILABLE)
        if (db.worldInteractionDao().getClaim(individualId) != null) {
            throw com.github.nacabaro.vbhelper.world.ecosystem.WorldInteractionException(com.github.nacabaro.vbhelper.world.ecosystem.InteractionFailure.BUSY)
        }
        if (!com.github.nacabaro.vbhelper.world.RadarDebugInteraction.allowAnyDistance &&
            !com.github.nacabaro.vbhelper.world.RadarWorldGeometry.relative(fix.position,
                com.github.nacabaro.vbhelper.world.GeoPoint(spawn.latitude, spawn.longitude)).withinInteractionRange) {
            throw com.github.nacabaro.vbhelper.world.ecosystem.RadarCommandException(rejection = com.github.nacabaro.vbhelper.world.ecosystem.RadarRejection.OUT_OF_RANGE)
        }
        if (objective.type == QuestObjectiveType.DELIVER_TOKEN && objective.tokenId?.let(dao::token)?.state != QuestTokenState.HELD) {
            throw com.github.nacabaro.vbhelper.world.ecosystem.RadarCommandException(rejection = com.github.nacabaro.vbhelper.world.ecosystem.RadarRejection.UNAVAILABLE)
        }
        val source = "encounter:${java.util.UUID.randomUUID()}"
        check(progress.beginSourceLocked(quest, source, clock(), clock()))
        when (objective.type) {
            QuestObjectiveType.RECOVER_PROPERTY -> acquireProducedTokenLocked(quest, objective)
            QuestObjectiveType.DELIVER_TOKEN -> deliverQuestTokenLocked(quest, objective)
            QuestObjectiveType.DELIVER_ITEM -> {
                if (db.itemDao().handOver(requireNotNull(objective.itemId), objective.required - objective.progress) != 1) {
                    throw com.github.nacabaro.vbhelper.world.ecosystem.RadarCommandException(rejection = com.github.nacabaro.vbhelper.world.ecosystem.RadarRejection.UNAVAILABLE)
                }
            }
            else -> Unit
        }
        progress.creditLocked(quest, objective, source,
            if (objective.type == QuestObjectiveType.DELIVER_ITEM) objective.required - objective.progress else 1, clock())
        check(dao.updateQuest(quest.copy(revision = quest.revision + 1)) == 1)
        db.worldSpawnDao().markInteracted(spawn.id)
        db.wildRelationshipDao().unlockQuestContact(individualId, clock())
        progress.refreshLocked(quest.id, clock())
        return requireNotNull(dao.getQuest(quest.id))
    }
}
