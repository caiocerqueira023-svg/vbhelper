package com.github.nacabaro.vbhelper.battle.offline.core

import java.util.ArrayDeque
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.random.Random

/** Single-writer, fixed-step simulation. UI/rendering consume snapshots and submit orders. */
class BattleSimulator(
    private val configuration: BattleConfiguration,
    alliedTeam: BattleTeam,
    opposingTeam: BattleTeam,
    techniqueCatalog: Collection<TechniqueDefinition>,
    trainingItems: Collection<BattleItemDefinition> = emptyList()
) {
    private val enhanced = configuration.rulesetVersion == BattleRules.CURRENT_VERSION
    private val techniques = techniqueCatalog.associate { it.techniqueId to it.copy(statusEffects = it.statusEffects.toList()) }.toMutableMap()
    private val combatants = linkedMapOf<String, Fighter>()
    private val itemDefinitions = trainingItems.associateBy { it.itemId }
    private val itemCounts = trainingItems.associate { it.itemId to it.quantity }.toMutableMap()
    private val itemReservations = mutableMapOf<Long, String>()
    private val projectiles = linkedMapOf<Long, ActiveProjectile>()
    private val impacts = linkedMapOf<Long, ActiveImpact>()
    private val recentEvents = ArrayDeque<BattleEvent>()
    private val pendingSupport = linkedMapOf<String, Long>()
    private val reservations = mutableMapOf<Long, Reservation>()
    private var accumulatedMillis = 0L
    private var thinkAccumulatedMillis = 0L
    private var elapsedMillis = 0L
    private var nextOrderId = 1L
    private var eventCount = 0L
    private var nextImpactId = 1L
    private var paused = configuration.defaultPaused
    private var pauseReason: String? = if (paused) "menu" else null
    private var outcome: BattleResult? = null
    private var commandPoints = configuration.commandPoints
    private var nextProjectileId = 1L
    private var statistics = BattleStatistics()

    init {
        require(configuration.rulesetVersion in BattleRules.LEGACY_VERSION..BattleRules.CURRENT_VERSION)
        require(configuration.arenaRadius.isFinite() && configuration.arenaRadius > 0f)
        require(configuration.lineupRowSpacing.isFinite() && configuration.lineupRowSpacing > 0f)
        require(configuration.lineupDepthRatio.isFinite() && configuration.lineupDepthRatio in 0f..1f)
        require(configuration.minimumLineupDepth.isFinite() && configuration.minimumLineupDepth >= 0f)
        require(configuration.maxCommandPoints >= 0 && commandPoints in 0..configuration.maxCommandPoints)
        require(configuration.decisionIntervalMillis > 0 && configuration.maxDurationMillis > 0)
        require(alliedTeam.side == BattleSide.ALLIED && opposingTeam.side == BattleSide.OPPOSING)
        require(alliedTeam.teamId != opposingTeam.teamId)
        require(techniques.size == techniqueCatalog.size) { "Duplicate technique IDs" }
        require(itemDefinitions.size == trainingItems.size) { "Duplicate training item IDs" }
        trainingItems.forEach { require(it.itemId.isNotBlank() && it.displayName.isNotBlank() && it.quantity >= 0 && it.amount >= 0) }
        require(BASIC.techniqueId !in techniques) { "The fallback basic attack is reserved" }
        techniqueCatalog.forEach(::validateTechnique)
        techniques[BASIC.techniqueId] = BASIC
        listOf(alliedTeam, opposingTeam).forEach { team ->
            require(team.members.isNotEmpty())
            val positions = defaultLineupPositions(
                team.members.size, team.side, configuration.arenaRadius,
                configuration.lineupRowSpacing, configuration.lineupDepthRatio, configuration.minimumLineupDepth
            )
            team.members.forEachIndexed { index, source ->
                require(source.side == team.side && source.combatantId.isNotBlank())
                require(source.combatantId !in combatants) { "Duplicate combatant IDs" }
                require(source.maxHealth > 0 && source.maxEnergy >= 0 && source.attack > 0 && source.defense >= 0)
                require(source.initialHealth == null || source.initialHealth in 1..source.maxHealth)
                require(source.initialEnergy == null || source.initialEnergy in 0..source.maxEnergy)
                require(source.movementSpeed.isFinite() && source.movementSpeed >= 0f)
                require(source.energyRegenerationPerSecond.isFinite() && source.energyRegenerationPerSecond >= 0f)
                require(source.techniqueCooldownMultiplier.isFinite() && source.techniqueCooldownMultiplier > 0f)
                require(source.collisionRadius.isFinite() && source.collisionRadius > 0f && source.collisionRadius < configuration.arenaRadius)
                require(source.preferredDistance.isFinite() && source.preferredDistance >= 0f)
                require(source.decisionDelayMinMillis in 0..configuration.maxDurationMillis)
                require(source.decisionDelayMaxMillis in source.decisionDelayMinMillis..configuration.maxDurationMillis)
                require(source.techniqueIds.all { it in techniques }) { "Unknown technique" }
                require(source.techniqueIds.none { techniques.getValue(it).kind == TechniqueKind.SPECIAL }) {
                    "The innate special cannot occupy a regular loadout slot"
                }
                require(source.specialTechniqueId == null || source.specialTechniqueId in techniques)
                require(source.specialTechniqueId == null || techniques.getValue(source.specialTechniqueId).kind == TechniqueKind.SPECIAL) {
                    "The innate special must use a SPECIAL technique"
                }
                require(source.specialTechniqueId !in source.techniqueIds) { "The innate special is separate from the regular loadout" }
                if (enhanced) {
                    require(source.readinessRegenerationPerSecond.isFinite() && source.readinessRegenerationPerSecond > 0f)
                    require(source.aiProfile.buffLimit in 0..10)
                    require(source.aiProfile.preparationPriority.isFinite() && source.aiProfile.preparationPriority in 0f..50f)
                    require(source.aiProfile.targetSwitchThreshold.isFinite() && source.aiProfile.targetSwitchThreshold in 0f..1f)
                    require(source.aiProfile.counterChance.isFinite() && source.aiProfile.counterChance in 0f..1f)
                    require(source.aiProfile.techniqueWeights.all { (id, weight) -> id in source.techniqueIds && weight.isFinite() && weight in 0f..4f })
                    require(source.counterTechniqueId == null || techniques[source.counterTechniqueId]?.reactionOnly == true)
                    require(source.signatureTechniqueId == null || source.signatureTechniqueId in source.techniqueIds)
                    require(source.signaturePowerBonus in 0..12)
                }
                // Gson's old array checkpoints bypass Kotlin defaults for newly added fields.
                val definition = source.copy(techniqueIds = source.techniqueIds.toList(),
                    aiProfile = if (enhanced) source.aiProfile else BattleAiProfile(profileId = "legacy"))
                val randomKey = definition.stableRngKey.ifBlank { definition.combatantId }
                combatants[definition.combatantId] = Fighter(
                    definition = definition,
                    position = clamp(positions[index], definition.collisionRadius),
                    personality = PersonalityBattleProfile.from(definition.personalityType),
                    profile = if (enhanced) definition.aiProfile.copy(techniqueWeights = definition.aiProfile.techniqueWeights.toMap()) else BattleAiProfile(profileId = "legacy"),
                    scheduleRandom = randomFor(randomKey, "decision-timing"),
                    movementRandom = randomFor(randomKey, "positioning"),
                    reactionRandom = randomFor(randomKey, "guard-reaction"),
                    choiceRandom = randomFor(randomKey, "action-choice"),
                    combatRandom = randomFor(randomKey, "damage-and-critical"),
                    varianceRandom = randomFor(randomKey, "damage-variance-v3"),
                    criticalRandom = randomFor(randomKey, "critical-v3"),
                    statusRandom = randomFor(randomKey, "status-procs-v3"),
                    counterRandom = randomFor(randomKey, "counter-v3"),
                    accuracyRandom = randomFor(randomKey, "accuracy-v3")
                )
            }
        }
        combatants.values.forEach { scheduleAutonomousRead(it, opening = true) }
    }

    fun setPaused(paused: Boolean, reason: String? = null) {
        if (outcome != null) return
        this.paused = paused
        pauseReason = if (paused) reason ?: "menu" else null
    }

    /** Long stalls are capped; the owner must also pause while Android is in the background. */
    fun advance(frameDeltaMillis: Long) {
        if (paused || outcome != null || frameDeltaMillis <= 0) return
        accumulatedMillis += frameDeltaMillis.coerceAtMost(250L)
        while (accumulatedMillis >= STEP && outcome == null) {
            accumulatedMillis -= STEP
            step()
        }
    }

    fun issueOrder(
        actorId: String,
        action: TrainerAction,
        interruptCurrentAction: Boolean = false,
        lifetimeMillis: Long = 8_000L
    ): OrderUpdate {
        val id = nextOrderId++
        fun reject(reason: String): OrderUpdate {
            combatants[actorId]?.lastOrderFailure = reason
            return publish(id, OrderStatus.FAILED, reason)
        }
        if (outcome != null) return reject("A batalha terminou.")
        val actor = combatants[actorId] ?: return reject("Parceiro não encontrado.")
        if (actor.definition.side != BattleSide.ALLIED) return reject("Só os parceiros recebem ordens.")
        if (actor.health <= 0) return reject("Este parceiro foi derrotado.")
        actor.lastOrderFailure = null
        if (lifetimeMillis <= 0) return reject("A ordem precisa de um prazo válido.")
        // Trainer actions do not wait behind an animation or occupy the physical queue.
        when (action) {
            TrainerAction.Support -> {
                if (paused) return reject("Incentivo indisponível durante a pausa.")
                val window = pendingSupport[actorId] ?: return reject("Não há janela de incentivo ativa.")
                if (window <= elapsedMillis) return reject("A janela de incentivo terminou.")
                pendingSupport.remove(actorId)
                val gained = min(8, configuration.maxCommandPoints - commandPoints)
                commandPoints += gained
                statistics = statistics.copy(supportCommands = statistics.supportCommands + 1)
                actor.lastDecision = "Incentivo reconhecido; +$gained CP."
                publish(id, OrderStatus.EXECUTING)
                emit(BattleEvent.SupportSucceeded(actorId, gained))
                return publish(id, OrderStatus.COMPLETED)
            }
            is TrainerAction.ChangeStrategy -> {
                actor.strategy = action.strategy
                actor.lastDecision = "Estratégia alterada para ${action.strategy.name}."
                publish(id, OrderStatus.EXECUTING)
                return publish(id, OrderStatus.COMPLETED)
            }
            is TrainerAction.FocusTarget -> {
                if (target(actor, action.targetId, false) == null) return reject("Alvo indisponível.")
                actor.focusId = action.targetId
                actor.focusUntil = elapsedMillis + 8_000L
                actor.lastDecision = "Foco definido em ${action.targetId}."
                if (actor.currentOrder == null && actor.active == null) {
                    actor.plannedTechniqueId = null
                    setTarget(actor, action.targetId)
                }
                publish(id, OrderStatus.EXECUTING)
                return publish(id, OrderStatus.COMPLETED)
            }
            else -> Unit
        }
        if (actor.orders.size >= 4) return reject("A fila de ordens está cheia.")
        if (action is TrainerAction.Defend && action.durationMillis <= 0) return reject("Duração de defesa inválida.")
        if (action is TrainerAction.UseTechnique) {
            val technique = techniques[action.techniqueId] ?: return reject("Técnica inexistente.")
            if (enhanced && technique.reactionOnly) return reject("Contra-ataques reagem a uma defesa; não são ataques diretos.")
            if (action.techniqueId !in actor.definition.techniqueIds && actor.definition.specialTechniqueId != action.techniqueId && technique != BASIC) {
                return reject("Esta técnica não pertence ao parceiro.")
            }
            if (!ready(actor, technique)) return reject("A técnica está em cooldown.")
            if (action.targetId != null && target(actor, action.targetId, isSupport(technique)) == null) return reject("Alvo indisponível.")
            val specialChargeCost = if (technique.kind == TechniqueKind.SPECIAL) SPECIAL_CHARGE_MAX else 0
            if (specialChargeCost > 0 && availableSpecialCharge(actor) < specialChargeCost) {
                return reject("O golpe especial ainda está carregando.")
            }
            if (availableEnergy(actor) < technique.energyCost || availableCommandPoints() < technique.commandPointCost) {
                return reject("Faltam energia ou pontos de comando disponíveis.")
            }
            reservations[id] = Reservation(actorId, technique.energyCost, technique.commandPointCost, specialChargeCost)
        }
        if (action is TrainerAction.UseItem) {
            val item = itemDefinitions[action.itemId] ?: return reject("Item de treino inexistente.")
            if (target(actor, action.targetId, true) == null) return reject("O item precisa de um parceiro vivo como alvo.")
            val reserved = itemReservations.values.count { it == item.itemId }
            if ((itemCounts[item.itemId] ?: 0) - reserved <= 0) return reject("Não há mais ${item.displayName} nesta sessão.")
            itemReservations[id] = item.itemId
        }
        val order = BattleOrder(id, actorId, action, elapsedMillis,
            elapsedMillis + lifetimeMillis.coerceAtMost(configuration.maxDurationMillis), interruptCurrentAction)
        if (interruptCurrentAction) actor.orders.addFirst(order) else actor.orders.addLast(order)
        actor.lastDecision = "Ordem #${order.orderId} enfileirada."
        return publish(id, OrderStatus.QUEUED)
    }

    fun snapshot(): BattleSnapshot {
        val members = combatants.values.map { actor ->
            val def = actor.definition
            val debugTarget = actor.targetId?.let(combatants::get)?.takeIf { it.health > 0 }
            CombatantSnapshot(def.combatantId, def.sourceCharacterId, def.externalCharacterId, def.displayName,
                def.side, actor.health, def.maxHealth, actor.energy, def.maxEnergy, actor.position, actor.targetId,
                actor.state, actor.strategy, actor.active?.technique?.techniqueId ?: actor.plannedTechniqueId,
                actor.statuses.map { it.effect.copy(durationMillis = it.remaining) },
                actor.cooldowns.filterValues { it > elapsedMillis }.mapValues { it.value - elapsedMillis },
                actor.currentOrder?.orderId, actor.orders.map { it.orderId }, actor.energy - availableEnergy(actor),
                CombatantDebugSnapshot(
                    decision = actor.lastDecision,
                    targetDistance = debugTarget?.let { actor.position.distanceTo(it.position) },
                    techniqueScores = actor.techniqueScores.toMap(),
                    techniqueScoreComponents = actor.techniqueScoreComponents.mapValues { it.value.toMap() },
                    personalityType = def.personalityType,
                    personalityCore = actor.personality.core,
                    threatByCombatant = actor.threat.toMap(),
                    lastOrderFailure = actor.lastOrderFailure,
                    readiness = actor.readiness,
                    readinessRequired = actor.plannedTechniqueId?.let(techniques::get)?.let { readinessRequired(actor, it) } ?: 0f,
                    buffsRemaining = actor.buffsRemaining,
                    positioningReplans = actor.positioningReplans,
                    targetReason = actor.targetReason,
                    encounterProfileId = actor.profile.profileId,
                    counterReady = actor.definition.counterTechniqueId?.let(techniques::get)?.let {
                        enhanced && ready(actor, it) && availableEnergy(actor) >= it.energyCost
                    } == true,
                    techniqueUses = actor.techniqueUses.toMap(), guardCount = actor.guardCount,
                    counterCount = actor.counterCount, targetChanges = actor.targetChanges,
                    incapacitatedMillis = actor.incapacitatedMillis,
                    readinessWaitingMillis = actor.readinessWaitingMillis,
                    meanTargetDistance = if (actor.distanceSamples > 0) (actor.distanceSum / actor.distanceSamples).toFloat() else 0f,
                    meanReadiness = if (actor.readinessSamples > 0) (actor.readinessSum / actor.readinessSamples).toFloat() else actor.readiness
                ), def.techniqueIds, def.specialTechniqueId, actor.specialCharge, SPECIAL_CHARGE_MAX,
                actor.specialCharge - availableSpecialCharge(actor), actor.active?.technique?.kind)
        }
        return BattleSnapshot(elapsedMillis, paused, pauseReason, commandPoints, configuration.maxCommandPoints,
            members.filter { it.side == BattleSide.ALLIED }, members.filter { it.side == BattleSide.OPPOSING },
            pendingSupport.keys.toSet(), outcome, recentEvents.toList(), eventCount, commandPoints - availableCommandPoints(),
            projectiles.values.map { projectile -> ProjectileSnapshot(projectile.id, projectile.ownerId,
                projectile.targetId, projectile.technique.techniqueId,
                attackSpriteVariantFor(projectile.technique.kind) ?: projectile.technique.attackVisual,
                projectile.position, projectile.velocityX, projectile.velocityZ) },
            itemDefinitions.values.map { item -> BattleItemSnapshot(item.itemId, item.displayName,
                itemCounts[item.itemId] ?: 0, itemReservations.values.count { it == item.itemId }) }, statistics,
            impacts.values.map { impact -> BattleImpactSnapshot(impact.id, impact.targetId, impact.damage,
                impact.critical, (impact.expiresAtMillis - elapsedMillis).coerceAtLeast(0L),
                    impact.techniqueId, impact.isSpecial) }, rulesetVersion = configuration.rulesetVersion)
    }

    fun abandon(): BattleResult {
        finish(BattleOutcome.ABANDONED)
        return requireNotNull(outcome)
    }

    private fun step() {
        elapsedMillis += STEP
        impacts.entries.removeAll { it.value.expiresAtMillis <= elapsedMillis }
        thinkAccumulatedMillis += STEP
        val interval = configuration.decisionIntervalMillis.coerceAtLeast(STEP)
        val think = thinkAccumulatedMillis >= interval
        if (think) thinkAccumulatedMillis %= interval
        pendingSupport.entries.removeAll { it.value <= elapsedMillis }
        val actors = combatants.values.sortedBy { it.definition.combatantId }
        actors.filter { it.health > 0 }.forEach { actor ->
            expireOrders(actor)
            actor.cooldowns.entries.removeAll { it.value <= elapsedMillis }
            tickStatuses(actor)
            actor.energyRemainder += actor.definition.energyRegenerationPerSecond * STEP / 1000.0
            val regenerated = actor.energyRemainder.toInt()
            actor.energyRemainder -= regenerated
            actor.energy = min(actor.definition.maxEnergy, actor.energy + regenerated)
            if (enhanced && actor.statuses.none { it.effect.preventsActions }) {
                val shock = if (actor.statuses.any { it.effect.mechanic == BattleStatusMechanic.SHOCK }) 0.5f else 1f
                val tempo = actor.statuses.fold(1f) { value, status -> value / status.effect.cooldownMultiplier }
                    .coerceIn(0.5f, 1.3f)
                actor.readiness = min(BattleRules.READINESS_MAX, actor.readiness +
                    actor.definition.readinessRegenerationPerSecond * shock * tempo * STEP / 1000f)
                if (actor.definition.specialTechniqueId != null) {
                    actor.specialChargeRemainder += STEP / 800.0
                    val charge = actor.specialChargeRemainder.toInt()
                    actor.specialChargeRemainder -= charge
                    addSpecialCharge(actor, charge)
                }
            }
        }
        if (enhanced && think) {
            // Read every target/startup before any actor commits this tick's new action.
            val eligible = actors.filter { it.health > 0 && it.active == null && it.currentOrder == null &&
                it.orders.isEmpty() && it.defendUntil <= elapsedMillis && it.knockbackRemainingMillis <= 0 &&
                it.statuses.none { status -> status.effect.preventsActions } }
            val targets = eligible.associateWith { chooseEnemy(it) }
            eligible.forEach { actor ->
                if (actor.plannedTechniqueId == null && elapsedMillis >= actor.nextDecisionAt) {
                    planAutonomousAction(actor, targets[actor])
                } else targets[actor]?.let { tryAutonomousGuard(actor, it) }
            }
        }
        val impacts = mutableListOf<Pair<Fighter, ActiveTechnique>>()
        val movementIntents = mutableMapOf<Fighter, BattlePosition>()
        actors.filter { it.health > 0 }.forEach { actor ->
            val observedPosition = actor.position
            try {
                advanceFighter(actor, think, impacts)
            } finally {
                if (enhanced) {
                    movementIntents[actor] = actor.position
                    actor.position = observedPosition
                }
            }
        }
        movementIntents.forEach { (actor, position) -> actor.position = position }
        // Commit simultaneous hits together: an opponent's already active hit
        // must not be suppressed simply because its ID sorts after the killer.
        impacts.forEach { (actor, active) ->
            if (active.technique.kind == TechniqueKind.PROJECTILE) launchProjectile(actor, active)
            else resolveTechnique(actor, active)
        }
        advanceProjectiles()
        if (enhanced) actors.filter { it.health > 0 }.forEach(::startPendingCounter)
        separateCombatants()
        if (enhanced) actors.forEach { actor ->
            if (actor.health > 0) {
                if (actor.statuses.any { it.effect.preventsActions } || actor.knockbackRemainingMillis > 0) actor.incapacitatedMillis += STEP
                actor.readinessSum += actor.readiness
                actor.readinessSamples++
                target(actor, actor.targetId, false)?.let {
                    actor.distanceSum += actor.position.distanceTo(it.position)
                    actor.distanceSamples++
                }
            }
        }
        val allies = actors.any { it.health > 0 && it.definition.side == BattleSide.ALLIED }
        val enemies = actors.any { it.health > 0 && it.definition.side == BattleSide.OPPOSING }
        when {
            !allies && !enemies -> finish(BattleOutcome.DRAW)
            !allies -> finish(BattleOutcome.OPPOSING_VICTORY)
            !enemies -> finish(BattleOutcome.ALLIED_VICTORY)
            elapsedMillis >= configuration.maxDurationMillis -> finish(BattleOutcome.DRAW)
        }
    }

    private fun advanceFighter(actor: Fighter, think: Boolean, impacts: MutableList<Pair<Fighter, ActiveTechnique>>) {
        if (actor.statuses.any { it.effect.preventsActions }) {
            transition(actor, CombatantState.STUNNED)
            return
        }
        if (actor.knockbackRemainingMillis > 0) {
            val activeMillis = min(STEP, actor.knockbackRemainingMillis)
            actor.position = clamp(BattlePosition(
                actor.position.x + actor.knockbackVelocityX * activeMillis / 1000f,
                actor.position.z + actor.knockbackVelocityZ * activeMillis / 1000f
            ), actor.definition.collisionRadius)
            actor.knockbackRemainingMillis -= activeMillis
            transition(actor, if (actor.knockbackRemainingMillis > 0) CombatantState.KNOCKBACK else CombatantState.SELECT_TARGET)
            if (actor.knockbackRemainingMillis > 0) return
        }
        if (actor.defendUntil > 0 && actor.defendUntil <= elapsedMillis) {
            actor.defendUntil = 0
            finishOrder(actor, OrderStatus.COMPLETED)
            transition(actor, CombatantState.SELECT_TARGET)
        }
        processOrders(actor)
        if (actor.defendUntil > elapsedMillis) {
            transition(actor, CombatantState.DEFENDING)
            return
        }
        if (actor.active != null) {
            progressTechnique(actor, impacts)
            return
        }
        val movementOrder = actor.currentOrder?.action
        if (movementOrder == TrainerAction.KeepDistance || movementOrder == TrainerAction.MoveCloser) {
            val enemy = target(actor, actor.targetId, false)
            if (enemy == null) finishOrder(actor, OrderStatus.FAILED, "O alvo não está disponível.")
            else {
                val distance = if (movementOrder == TrainerAction.KeepDistance) max(4f, actor.definition.preferredDistance)
                    else actor.definition.collisionRadius + enemy.definition.collisionRadius + 0.05f
                move(actor, enemy, distance, STEP)
                if (abs(actor.position.distanceTo(enemy.position) - distance) <= POSITION_EPSILON) {
                    finishOrder(actor, OrderStatus.COMPLETED)
                    transition(actor, CombatantState.POSITIONING)
                }
            }
            return
        }
        if (actor.currentOrder == null && actor.plannedTechniqueId == null && elapsedMillis < actor.nextDecisionAt) {
            continueAutonomousRead(actor)
            return
        }
        if (!enhanced && actor.currentOrder == null && think) planAutonomousAction(actor)
        actor.plannedTechniqueId?.let(techniques::get)?.let { approachAndExecute(actor, it) }
    }

    private fun expireOrders(actor: Fighter) {
        val iterator = actor.orders.iterator()
        while (iterator.hasNext()) {
            val order = iterator.next()
            if ((order.expiresAtMillis ?: Long.MAX_VALUE) <= elapsedMillis) {
                iterator.remove()
                reservations.remove(order.orderId)
                itemReservations.remove(order.orderId)
                publish(order.orderId, OrderStatus.EXPIRED, "A ordem expirou antes da execução.")
            }
        }
        // Techniques already started finish their phases; approaching a target has a deadline.
        val current = actor.currentOrder
        if (current != null && actor.active == null && (current.expiresAtMillis ?: Long.MAX_VALUE) <= elapsedMillis) {
            actor.defendUntil = 0
            finishOrder(actor, OrderStatus.EXPIRED, "Não foi possível concluir a ordem a tempo.")
        }
    }

    private fun processOrders(actor: Fighter) {
        val next = actor.orders.peekFirst() ?: return
        val occupied = actor.currentOrder != null || actor.active != null || actor.defendUntil > elapsedMillis
        if (occupied) {
            if (!next.interruptCurrentAction || !canInterrupt(actor)) return
            cancelAction(actor, "Ação substituída por uma ordem.")
        }
        actor.orders.removeFirst()
        actor.plannedTechniqueId = null
        actor.currentOrder = next
        actor.lastDecision = "Executando ordem #${next.orderId}."
        publish(next.orderId, OrderStatus.EXECUTING)
        when (val action = next.action) {
            is TrainerAction.Defend -> {
                actor.defendUntil = elapsedMillis + action.durationMillis.coerceAtMost(configuration.maxDurationMillis)
                if (enhanced) actor.guardCount++
                transition(actor, CombatantState.DEFENDING)
            }
            TrainerAction.KeepDistance, TrainerAction.MoveCloser -> {
                setTarget(actor, chooseEnemy(actor)?.definition?.combatantId)
                if (actor.targetId == null) finishOrder(actor, OrderStatus.FAILED, "Não há alvo disponível.")
            }
            is TrainerAction.UseTechnique -> {
                val technique = techniques.getValue(action.techniqueId)
                val recipient = if (technique.rangeProfile == TechniqueRangeProfile.SELF) actor
                    else if (action.targetId != null) target(actor, action.targetId, isSupport(technique))
                    else if (isSupport(technique)) chooseAlly(actor) else chooseEnemy(actor)
                if (recipient == null || !ready(actor, technique)) finishOrder(actor, OrderStatus.FAILED, "Alvo ou técnica indisponível.")
                else {
                    setTarget(actor, recipient.definition.combatantId)
                    actor.plannedTechniqueId = technique.techniqueId
                }
            }
            is TrainerAction.UseItem -> executeItem(actor, next, action)
            else -> finishOrder(actor, OrderStatus.FAILED, "Ordem inválida na fila física.")
        }
    }

    private fun scheduleAutonomousRead(actor: Fighter, opening: Boolean = false) {
        if (actor.health <= 0 || outcome != null) return
        val openingFactor = if (opening) (1f + actor.personality.openingBias).coerceIn(0.85f, 1.15f) else 1f
        val minimum = (actor.definition.decisionDelayMinMillis * openingFactor).roundToLong().coerceAtLeast(0L)
        val maximum = (actor.definition.decisionDelayMaxMillis * openingFactor).roundToLong().coerceAtLeast(minimum)
        val delay = if (minimum == maximum) minimum else actor.scheduleRandom.nextLong(minimum, maximum + 1L)
        actor.nextDecisionAt = elapsedMillis + delay
        val enemy = chooseEnemy(actor)
        actor.roamDestination = enemy?.let { tacticalRoamDestination(actor, it) }
        actor.lastDecision = if (opening) {
            "Lendo o campo e procurando espaço."
        } else {
            "Observando o adversário antes da próxima ação."
        }
        transition(actor, CombatantState.WAITING)
    }

    private fun tacticalRoamDestination(actor: Fighter, enemy: Fighter): BattlePosition {
        val dx = actor.position.x - enemy.position.x
        val dz = actor.position.z - enemy.position.z
        val length = hypot(dx, dz).coerceAtLeast(0.001f)
        val radialX = dx / length
        val radialZ = dz / length
        val candidates = autonomousTechniqueCandidates(actor, chooseAlly(actor))
        // Do not guess a generic stance before the action is selected. A self/field/support
        // option needs no enemy-range movement, while incompatible attack lanes would make
        // the Digimon walk one way and immediately reverse after choosing its technique.
        if (candidates.any { isSupport(it) || !it.rangeProfile.isPositionalProfile() }) return actor.position
        val equippedWindows = candidates.asSequence()
            .map { tacticalRangeWindow(actor, enemy, it) }
            .toList()
        if (equippedWindows.isEmpty()) return actor.position
        val sharedMinimum = equippedWindows.maxOf { it.tacticalMin }
        val sharedMaximum = equippedWindows.minOf { it.tacticalMax }
        if (sharedMinimum > sharedMaximum) return actor.position
        val sharedPreferred = equippedWindows.map { it.preferred }.average().toFloat()
        val strategyOffset = when (actor.strategy) {
            BattleStrategy.AGGRESSIVE -> -0.65f
            BattleStrategy.RANGED -> 0.65f
            BattleStrategy.DEFENSIVE -> 0.4f
            BattleStrategy.CONSERVATIVE -> 0.2f
            BattleStrategy.SUPPORT -> 0.1f
            BattleStrategy.BALANCED -> (actor.movementRandom.nextFloat() - 0.5f) * 0.7f
        }
        val strategyDistance = (sharedPreferred + strategyOffset).coerceIn(sharedMinimum, sharedMaximum)
        val desiredDistance = (strategyDistance + actor.personality.preferredRangeBias)
            .coerceIn(sharedMinimum, sharedMaximum)
            .coerceAtLeast(actor.definition.collisionRadius + enemy.definition.collisionRadius + 0.1f)
        val direction = if (actor.movementRandom.nextBoolean()) 1f else -1f
        val lateralScale = (desiredDistance / 3f).coerceIn(0.35f, 1f)
        val lateral = direction * (0.7f + actor.movementRandom.nextFloat() * 1.15f) * lateralScale
        return clamp(
            BattlePosition(
                x = enemy.position.x + radialX * desiredDistance - radialZ * lateral,
                z = enemy.position.z + radialZ * desiredDistance + radialX * lateral
            ),
            actor.definition.collisionRadius
        )
    }

    private fun continueAutonomousRead(actor: Fighter) {
        val destination = actor.roamDestination ?: run {
            transition(actor, CombatantState.WAITING)
            return
        }
        val dx = destination.x - actor.position.x
        val dz = destination.z - actor.position.z
        val distance = hypot(dx, dz)
        if (distance <= POSITION_EPSILON || actor.definition.movementSpeed <= 0f) {
            actor.roamDestination = null
            transition(actor, CombatantState.WAITING)
            return
        }
        val slow = actor.statuses.filter { it.effect.slowsMovement }.maxOfOrNull { it.effect.magnitude } ?: 0f
        val movementMultiplier = movementMultiplier(actor)
        val step = min(distance, actor.definition.movementSpeed * movementMultiplier *
            (1f - slow.coerceIn(0f, 1f)) * STEP / 1000f)
        actor.position = clamp(
            BattlePosition(actor.position.x + dx / distance * step, actor.position.z + dz / distance * step),
            actor.definition.collisionRadius
        )
        transition(actor, CombatantState.POSITIONING)
    }

    private fun planAutonomousAction(actor: Fighter, observedEnemy: Fighter? = chooseEnemy(actor)) {
        actor.roamDestination = null
        val enemy = observedEnemy ?: run {
            actor.plannedTechniqueId = null
            actor.techniqueScores = emptyMap()
            actor.techniqueScoreComponents = emptyMap()
            actor.lastDecision = "Sem alvo válido para continuar."
            setTarget(actor, null)
            transition(actor, CombatantState.IDLE)
            return
        }
        if (enhanced && actor.profile.autonomousSpecial && actor.definition.specialTechniqueId != null &&
            availableSpecialCharge(actor) >= SPECIAL_CHARGE_MAX) {
            val special = techniques.getValue(actor.definition.specialTechniqueId)
            if (ready(actor, special) && availableEnergy(actor) >= special.energyCost && special.commandPointCost == 0) {
                actor.plannedTechniqueId = special.techniqueId
                setTarget(actor, enemy.definition.combatantId)
                actor.lastDecision = "Golpe especial pronto; preparando alcance."
                return
            }
        }
        val ally = chooseAlly(actor)
        val incoming = enemy.active?.takeIf {
            it.phase == Phase.STARTUP && it.targetId == actor.definition.combatantId &&
                inRange(enemy, actor, it.technique)
        }
        if (tryAutonomousGuard(actor, enemy)) return
        val candidates = autonomousTechniqueCandidates(actor, ally)
        val energyRatio = if (actor.definition.maxEnergy > 0) {
            (availableEnergy(actor).toFloat() / actor.definition.maxEnergy).coerceIn(0f, 1f)
        } else 1f
        val reservePressure = if (actor.personality.energyReserve > 0f && energyRatio < actor.personality.energyReserve) {
            ((actor.personality.energyReserve - energyRatio) / actor.personality.energyReserve).coerceIn(0f, 1f)
        } else 0f
        val selfLowHpPressure = lowHpPressure(actor)
        val scoreComponents = linkedMapOf<String, Map<String, Float>>()
        val scores = candidates.associate { technique ->
            val recipient = if (technique.rangeProfile == TechniqueRangeProfile.SELF) actor
                else if (isSupport(technique)) ally else enemy
            val distance = actor.position.distanceTo(recipient.position)
            val rangeWindow = tacticalRangeWindow(actor, recipient, technique)
            val preferredRange = rangeWindow.preferred
            val accuracyFactor = if (enhanced) technique.accuracyPercent / 100f else 1f
            val baseTechniqueScore = technique.power * 0.72f * accuracyFactor - technique.energyCost * 0.32f -
                (technique.startupMillis + technique.recoveryMillis) * 0.015f
            val positionScore = when {
                technique.rangeProfile == TechniqueRangeProfile.SELF ||
                    technique.rangeProfile == TechniqueRangeProfile.ALL_FIELD -> 28f
                distance in rangeWindow.tacticalMin..rangeWindow.tacticalMax ->
                    28f - abs(distance - preferredRange) * 3f
                distance in rangeWindow.activationMin..rangeWindow.activationMax ->
                    10f - abs(distance - preferredRange) * 5f
                else -> -abs(distance - preferredRange) * 7f
            }
            val repetitionScore = if (technique.techniqueId == actor.lastTechniqueId) -62f else 0f
            val encounterScore = if (enhanced) {
                val weight = actor.profile.techniqueWeights[technique.techniqueId] ?: 1f
                (weight - 1f) * 25f + if (technique.rangeProfile == TechniqueRangeProfile.SELF) {
                    actor.profile.preparationPriority * (1f - elapsedMillis / 20_000f).coerceIn(0f, 1f)
                } else 0f
            } else 0f
            val crowdScore = if (enhanced && technique.impactShape != TechniqueImpactShape.SINGLE_TARGET) {
                combatants.values.count { it.health > 0 && it.definition.side != actor.definition.side &&
                    (technique.impactShape == TechniqueImpactShape.ALL_OPPONENTS || it.position.distanceTo(
                        if (technique.impactShape == TechniqueImpactShape.AROUND_USER) actor.position else enemy.position
                    ) <= technique.areaRadius) }.let { (it - 1).coerceAtLeast(0) * 12f }
            } else 0f
            var supportScore = 0f
            if (isSupport(technique)) {
                val missingFraction = 1f - recipient.health.toFloat() / recipient.definition.maxHealth
                supportScore = min(technique.healPower, recipient.definition.maxHealth - recipient.health) * missingFraction
            }
            val trainerStrategyScore = when (actor.strategy) {
                BattleStrategy.AGGRESSIVE -> technique.power * 0.35f
                BattleStrategy.CONSERVATIVE -> -technique.energyCost * if (energyRatio < 0.25f) 2.0f else 0.8f
                BattleStrategy.DEFENSIVE -> 18f + technique.maxRange * 3f - technique.startupMillis * 0.03f
                BattleStrategy.RANGED -> if (technique.maxRange >= 4f) 30f else -15f
                BattleStrategy.SUPPORT -> if (isSupport(technique)) 80f else 0f
                BattleStrategy.BALANCED -> 0f
            }
            val personalityTechnique = technique.power * 0.72f * (actor.personality.offenseWeight - 1f) -
                technique.energyCost * 0.32f * (actor.personality.efficiencyWeight - 1f)
            val energyReserveScore = -technique.energyCost * 0.65f * reservePressure
            val lowHpScore = -selfLowHpPressure * technique.startupMillis * 0.012f * actor.personality.safetyWeight
            val teamPersonalityScore = if (isSupport(technique)) {
                supportScore * (actor.personality.teamWeight - 1f)
            } else {
                val controlValue = technique.statusEffects.sumOf { effect ->
                    when {
                        effect.preventsActions -> 14.0
                        effect.damagePerSecond > 0f -> 8.0
                        else -> 3.0
                    }
                }.toFloat()
                controlValue * (actor.personality.teamWeight - 1f)
            }
            val personalityScore = (personalityTechnique + energyReserveScore + lowHpScore + teamPersonalityScore)
                .coerceIn(-12f, 12f)
            val vulnerableFraction = if (isSupport(technique)) 0f else
                (1f - enemy.health.toFloat() / enemy.definition.maxHealth).coerceIn(0f, 1f)
            val opportunityScore = technique.power * 0.12f * vulnerableFraction *
                (actor.personality.opportunityWeight - 0.5f).coerceIn(-0.5f, 0.5f)
                .coerceIn(-6f, 6f)
            val incomingTechnique = incoming?.technique
            val riskScore = if (incomingTechnique != null) {
                val remainingStartup = (incomingTechnique.startupMillis - enemy.active!!.phaseElapsed).coerceAtLeast(0L)
                val commitmentAfterImpact = (technique.startupMillis - remainingStartup).coerceAtLeast(0L)
                val incomingPressure = incomingTechnique.power * enemy.definition.attack.toFloat() /
                    (100f + actor.definition.defense).coerceAtLeast(1f)
                -min(24f, incomingPressure * 0.07f * actor.personality.safetyWeight *
                    (1f + selfLowHpPressure) * (commitmentAfterImpact.toFloat() / 500f).coerceAtMost(1.5f))
            } else 0f
            val components = linkedMapOf(
                "technique" to baseTechniqueScore,
                "position" to positionScore,
                "repeat" to repetitionScore,
                "support" to supportScore,
                "personality" to personalityScore,
                "opportunity" to opportunityScore,
                "strategy" to trainerStrategyScore,
                "risk" to riskScore
            )
            if (enhanced) {
                components["encounter"] = encounterScore
                components["crowd"] = crowdScore
            }
            scoreComponents[technique.techniqueId] = components
            technique.techniqueId to components.values.sum()
        }
        actor.techniqueScores = scores
        actor.techniqueScoreComponents = scoreComponents
        val temperature = actor.personality.actionTemperature.coerceIn(18f, 46f)
        val chosenId = if (enhanced && actor.profile.selectionMode == BattleSelectionMode.WEIGHTED) {
            chooseEncounterWeighted(actor, candidates, scores)?.techniqueId
        } else chooseSoftmax(actor, candidates, scores, temperature)?.techniqueId
        val chosen = candidates.firstOrNull { it.techniqueId == chosenId }
        actor.plannedTechniqueId = chosen?.techniqueId
        setTarget(actor, when {
            chosen?.rangeProfile == TechniqueRangeProfile.SELF -> actor.definition.combatantId
            chosen != null && isSupport(chosen) -> ally.definition.combatantId
            else -> enemy.definition.combatantId
        })
        if (chosen == null) {
            actor.lastDecision = "Nenhuma técnica pronta com recursos disponíveis."
            transition(actor, CombatantState.WAITING)
            scheduleAutonomousRead(actor)
        } else {
            val components = scoreComponents[chosen.techniqueId].orEmpty()
            actor.lastDecision = "${actor.definition.personalityType.name}/${actor.personality.core.name}: " +
                "${chosen.displayName} (${scores[chosen.techniqueId]?.toInt() ?: 0}; " +
                "técnica ${components["technique"]?.toInt() ?: 0}, posição ${components["position"]?.toInt() ?: 0}, " +
                "personalidade ${components["personality"]?.toInt() ?: 0}, oportunidade ${components["opportunity"]?.toInt() ?: 0}, " +
                "estratégia ${components["strategy"]?.toInt() ?: 0}, " +
                "risco ${components["risk"]?.toInt() ?: 0}; T=${temperature.toInt()})."
        }
    }

    private fun tryAutonomousGuard(actor: Fighter, enemy: Fighter): Boolean {
        val incoming = enemy.active?.takeIf { it.phase == Phase.STARTUP &&
            it.targetId == actor.definition.combatantId && inRange(enemy, actor, it.technique) } ?: return false
        if (elapsedMillis < actor.nextAutoGuardAt) return false
        val chance = (actor.personality.guardReadBias + lowHpPressure(actor) * 0.16f)
            .coerceIn(0f, 0.62f) * (incoming.technique.power / 175f).coerceIn(0.45f, 1.25f)
        if (actor.strategy != BattleStrategy.DEFENSIVE && actor.reactionRandom.nextDouble() >= chance) return false
        actor.nextAutoGuardAt = elapsedMillis + 3_500L
        actor.defendUntil = elapsedMillis + 1_000L
        if (enhanced) actor.guardCount++
        actor.plannedTechniqueId = null
        actor.positioningTechniqueId = null
        actor.roamDestination = null
        actor.techniqueScores = emptyMap()
        actor.techniqueScoreComponents = emptyMap()
        actor.lastDecision = if (actor.strategy == BattleStrategy.DEFENSIVE) {
            "Antecipou o ataque por estratégia e assumiu defesa."
        } else "${actor.definition.personalityType.name} leu o startup e protegeu-se."
        setTarget(actor, enemy.definition.combatantId)
        transition(actor, CombatantState.DEFENDING)
        return true
    }

    private fun chooseEncounterWeighted(actor: Fighter, candidates: List<TechniqueDefinition>, scores: Map<String, Float>): TechniqueDefinition? {
        val weights = candidates.map { technique ->
            val configured = actor.profile.techniqueWeights[technique.techniqueId] ?: 1f
            technique to configured * exp(((scores[technique.techniqueId] ?: 0f) / 70f).coerceIn(-6f, 6f).toDouble())
        }
        val total = weights.sumOf { it.second }
        if (total <= 0.0) return null
        var draw = actor.choiceRandom.nextDouble() * total
        weights.forEach { (technique, weight) ->
            draw -= weight
            if (draw < 0.0) return technique
        }
        return weights.lastOrNull()?.first
    }

    private fun chooseSoftmax(
        actor: Fighter,
        candidates: List<TechniqueDefinition>,
        scores: Map<String, Float>,
        temperature: Float
    ): TechniqueDefinition? {
        val maximum = scores.values.maxOrNull() ?: return null
        val weights = candidates.mapNotNull { technique ->
            val score = scores[technique.techniqueId] ?: return@mapNotNull null
            technique to exp(((score - maximum) / temperature).toDouble())
        }
        val total = weights.sumOf { it.second }
        if (!total.isFinite() || total <= 0.0) {
            return candidates.maxByOrNull { scores[it.techniqueId] ?: Float.NEGATIVE_INFINITY }
        }
        var draw = actor.choiceRandom.nextDouble() * total
        weights.forEach { (technique, weight) ->
            draw -= weight
            if (draw <= 0.0) return technique
        }
        return weights.lastOrNull()?.first
    }

    private fun autonomousTechniqueCandidates(actor: Fighter, ally: Fighter): List<TechniqueDefinition> {
        val equippedCandidates = actor.definition.techniqueIds.distinct().map(techniques::getValue)
            .filter {
                ready(actor, it) && it.energyCost <= availableEnergy(actor) &&
                    it.commandPointCost == 0 && it.kind != TechniqueKind.SPECIAL
            }
            .filter {
                !isSupport(it) || (it.healPower > 0 && ally.health < ally.definition.maxHealth) ||
                    it.statusEffects.isNotEmpty()
            }
            .filter { technique -> !enhanced || !technique.reactionOnly &&
                (actor.unreachableUntil[technique.techniqueId] ?: 0L) <= elapsedMillis &&
                (actor.profile.techniqueWeights[technique.techniqueId] ?: 1f) > 0f &&
                (technique.rangeProfile != TechniqueRangeProfile.SELF || technique.statusEffects.none(::isBuff) || actor.buffsRemaining > 0 &&
                    technique.statusEffects.any { effect -> actor.statuses.none { it.effect.id == effect.id } }) }
        // Re:Digitize techniques consume MP. The free fallback only prevents a
        // permanent stalemate when no equipped technique can currently run.
        // Partner specials are trainer commands; encounter profiles can enable automatic enemy finishers.
        return equippedCandidates.ifEmpty {
            listOf(BASIC).filter { ready(actor, it) && availableEnergy(actor) >= it.energyCost }
        }
    }

    private fun lowHpPressure(actor: Fighter): Float {
        val threshold = actor.personality.lowHpRiskThreshold.coerceIn(0.05f, 0.9f)
        val healthRatio = actor.health.toFloat() / actor.definition.maxHealth
        return if (healthRatio < threshold) ((threshold - healthRatio) / threshold).coerceIn(0f, 1f) else 0f
    }

    private fun randomFor(stableKey: String, streamTag: String): Random {
        // Stable FNV-1a instead of String.hashCode: same seed/identity/tag is replayable across runs.
        var hash = -3750763034362895579L
        "${configuration.randomSeed}:$stableKey:$streamTag".forEach { character ->
            hash = (hash xor character.code.toLong()) * 1099511628211L
        }
        return Random(hash)
    }

    private fun approachAndExecute(actor: Fighter, technique: TechniqueDefinition) {
        val recipient = if (technique.rangeProfile == TechniqueRangeProfile.SELF) actor
        else target(actor, actor.targetId, isSupport(technique))
        if (recipient == null || !ready(actor, technique)) {
            finishOrder(actor, OrderStatus.FAILED, "Alvo ou técnica indisponível.")
            actor.plannedTechniqueId = null
            if (enhanced) scheduleAutonomousRead(actor)
            return
        }
        val explicit = actor.currentOrder != null
        val enoughEnergy = if (explicit) actor.energy >= technique.energyCost else availableEnergy(actor) >= technique.energyCost
        val enoughSpecialCharge = technique.kind != TechniqueKind.SPECIAL || actor.specialCharge >= SPECIAL_CHARGE_MAX
        if (!enoughEnergy || explicit && commandPoints < technique.commandPointCost || !enoughSpecialCharge) {
            finishOrder(actor, OrderStatus.FAILED, "Os recursos não estão disponíveis.")
            actor.plannedTechniqueId = null
            return
        }
        val rangeWindow = tacticalRangeWindow(actor, recipient, technique)
        val distance = actor.position.distanceTo(recipient.position)
        if (enhanced && actor.positioningTechniqueId != technique.techniqueId) {
            actor.positioningTechniqueId = technique.techniqueId
            actor.positioningStartedAt = elapsedMillis
            actor.positioningProgressAt = elapsedMillis
            actor.positioningBestError = abs(distance - rangeWindow.preferred)
            actor.positioningSettled = false
        }
        if (enhanced) {
            val error = abs(distance - rangeWindow.preferred)
            if (error < actor.positioningBestError - POSITION_EPSILON) {
                actor.positioningBestError = error
                actor.positioningProgressAt = elapsedMillis
            }
            if (distance in rangeWindow.tacticalMin..rangeWindow.tacticalMax) actor.positioningSettled = true
        }
        val hysteresis = if (enhanced && actor.positioningSettled) 0.15f else 0f
        val shouldClaimTacticalRange = technique.rangeProfile.isPositionalProfile() &&
            distance !in max(rangeWindow.activationMin, rangeWindow.tacticalMin - hysteresis)..
                min(rangeWindow.activationMax, rangeWindow.tacticalMax + hysteresis)
        if (enhanced && (shouldClaimTacticalRange || !inRange(actor, recipient, technique)) &&
            (elapsedMillis - actor.positioningProgressAt >= BattleRules.POSITIONING_STALL_MILLIS ||
                elapsedMillis - actor.positioningStartedAt >= BattleRules.POSITIONING_TIMEOUT_MILLIS)) {
            actor.positioningReplans++
            actor.unreachableUntil[technique.techniqueId] = elapsedMillis + 2_000L
            actor.positioningTechniqueId = null
            emit(BattleEvent.PositioningReplanned(actor.definition.combatantId, technique.techniqueId))
            finishOrder(actor, OrderStatus.FAILED, "Não foi possível alcançar uma posição válida.")
            actor.plannedTechniqueId = null
            actor.lastDecision = "Posicionamento sem progresso; escolhendo outra ação."
            scheduleAutonomousRead(actor)
            return
        }
        if (shouldClaimTacticalRange) {
            actor.lastDecision = "Reposicionando para ${technique.displayName} dentro do alcance."
            val moved = move(actor, recipient, rangeWindow.preferred, STEP)
            if (moved || !inRange(actor, recipient, technique)) return
        } else if (!inRange(actor, recipient, technique)) {
            actor.lastDecision = "Buscando alcance para ${technique.displayName}."
            move(actor, recipient, rangeWindow.preferred, STEP)
            return
        }
        if (enhanced && actor.readiness < readinessRequired(actor, technique)) {
            actor.readinessWaitingMillis += STEP
            actor.lastDecision = "Recuperando prontidão (${actor.readiness.toInt()}/${readinessRequired(actor, technique).toInt()})."
            if (chargeMode(actor) == BattleChargeMode.FULL && technique.rangeProfile.isPositionalProfile()) {
                move(actor, recipient, min(rangeWindow.activationMax, rangeWindow.preferred + 0.4f), STEP)
            } else transition(actor, CombatantState.WAITING)
            return
        }
        actor.energy -= technique.energyCost
        if (enhanced) {
            actor.readiness = max(BattleRules.READINESS_MIN, actor.readiness - BattleRules.readinessCost(technique))
            if (technique.rangeProfile == TechniqueRangeProfile.SELF && technique.statusEffects.any(::isBuff) && actor.buffsRemaining > 0) actor.buffsRemaining--
            actor.positioningTechniqueId = null
        }
        if (technique.kind == TechniqueKind.SPECIAL) {
            actor.specialCharge = 0
            statistics = statistics.copy(specialsUsed = statistics.specialsUsed + 1)
        }
        if (explicit) {
            commandPoints -= technique.commandPointCost
            reservations.remove(actor.currentOrder!!.orderId)
        }
        actor.active = ActiveTechnique(technique, recipient.definition.combatantId)
        if (enhanced) actor.techniqueUses[technique.techniqueId] = (actor.techniqueUses[technique.techniqueId] ?: 0) + 1
        actor.plannedTechniqueId = null
        if (explicit) actor.lastDecision = "Executando ${technique.displayName} por ordem do treinador."
        transition(actor, if (technique.kind == TechniqueKind.SPECIAL) CombatantState.USING_SPECIAL else CombatantState.ATTACK_STARTUP)
        if (technique.kind == TechniqueKind.SPECIAL) {
            emit(BattleEvent.SpecialStarted(actor.definition.combatantId, technique.techniqueId, recipient.definition.combatantId))
        }
        emit(BattleEvent.TechniqueStarted(actor.definition.combatantId, technique.techniqueId, recipient.definition.combatantId))
    }

    private fun progressTechnique(actor: Fighter, impacts: MutableList<Pair<Fighter, ActiveTechnique>>) {
        val active = actor.active ?: return
        transition(actor, when (active.phase) {
            Phase.STARTUP -> if (active.technique.kind == TechniqueKind.SPECIAL) CombatantState.USING_SPECIAL else CombatantState.ATTACK_STARTUP
            Phase.ACTIVE -> CombatantState.ATTACK_ACTIVE
            Phase.RECOVERY -> CombatantState.RECOVERING
        })
        active.phaseElapsed += STEP
        while (actor.active != null) {
            val duration = when (active.phase) {
                Phase.STARTUP -> active.technique.startupMillis
                Phase.ACTIVE -> active.technique.activeMillis
                Phase.RECOVERY -> active.technique.recoveryMillis
            }
            if (active.phaseElapsed < duration) return
            active.phaseElapsed -= duration
            when (active.phase) {
                Phase.STARTUP -> {
                    active.phase = Phase.ACTIVE
                    actor.cooldowns[active.technique.techniqueId] =
                        elapsedMillis - active.phaseElapsed + cooldownDuration(actor, active.technique)
                    transition(actor, CombatantState.ATTACK_ACTIVE)
                    impacts += actor to active
                }
                Phase.ACTIVE -> {
                    active.phase = Phase.RECOVERY
                    transition(actor, CombatantState.RECOVERING)
                }
                Phase.RECOVERY -> {
                    actor.lastTechniqueId = active.technique.techniqueId
                    actor.active = null
                    finishOrder(actor, OrderStatus.COMPLETED)
                    scheduleAutonomousRead(actor)
                }
            }
        }
    }

    private fun resolveTechnique(actor: Fighter, active: ActiveTechnique, projectileImpact: Boolean = false, hitTargetId: String? = null) {
        val technique = active.technique
        active.resolved = true
        val primary = if (technique.rangeProfile == TechniqueRangeProfile.SELF) actor
        else target(actor, hitTargetId ?: active.targetId, isSupport(technique))
        if (primary == null || !projectileImpact && !inRange(actor, primary, technique)) {
            emit(BattleEvent.TechniqueMissed(actor.definition.combatantId, technique.techniqueId, "Alvo indisponível ou fora do alcance."))
            resolveSpecialOutcome(actor, technique, active.targetId, success = false)
            return
        }
        if (isSupport(technique)) {
            val restored = min(technique.healPower, primary.definition.maxHealth - primary.health)
            primary.health += restored
            if (actor.definition.side == BattleSide.ALLIED) {
                statistics = statistics.copy(healingDone = statistics.healingDone + restored)
            }
            emit(BattleEvent.TechniqueHit(actor.definition.combatantId, primary.definition.combatantId, technique.techniqueId, -restored))
            combatants.values.filter { it.health > 0 && it.definition.side != actor.definition.side }
                .forEach { addThreat(it, actor, restored / 2) }
            applyStatuses(primary, technique.statusEffects.map { it.copy(sourceCombatantId = actor.definition.combatantId) })
            return
        }
        val victims = when (technique.impactShape) {
            TechniqueImpactShape.AROUND_USER -> combatants.values.filter {
                it.health > 0 && it.definition.side != actor.definition.side &&
                    it.position.distanceTo(actor.position) <= technique.areaRadius
            }
            TechniqueImpactShape.AROUND_TARGET -> combatants.values.filter {
                it.health > 0 && it.definition.side != actor.definition.side &&
                    it.position.distanceTo(primary.position) <= technique.areaRadius
            }
            TechniqueImpactShape.ALL_OPPONENTS -> combatants.values.filter {
                it.health > 0 && it.definition.side != actor.definition.side
            }
            TechniqueImpactShape.SINGLE_TARGET -> if (technique.kind == TechniqueKind.AREA) {
                combatants.values.filter {
                    it.health > 0 && it.definition.side != actor.definition.side &&
                        it.position.distanceTo(primary.position) <= technique.areaRadius
                }
            } else listOf(primary)
        }
        var totalDamage = 0
        val damagedVictims = linkedSetOf<Fighter>()
        val contactedVictims = if (enhanced) victims.filter { victim ->
            val hit = technique.accuracyPercent == 100 || actor.accuracyRandom.nextInt(100) < technique.accuracyPercent
            if (!hit) emit(BattleEvent.TechniqueMissed(actor.definition.combatantId, technique.techniqueId, "O ataque não acertou ${victim.definition.displayName}."))
            hit
        } else victims
        repeat(technique.hitCount) {
            contactedVictims.filter { it.health > 0 }.forEach { victim ->
                val guarding = victim.defendUntil > elapsedMillis && victim.statuses.none { it.effect.preventsActions }
                val hit = damageFor(actor, victim, technique, guarding)
                val critical = hit.critical
                val amount = hit.damage.damage
                val damage = min(amount, victim.health)
                victim.health -= damage
                totalDamage += damage
                if (damage > 0) damagedVictims += victim
                val sourceBaseDamage = hit.damage.unguardedDamage
                statistics = statistics.copy(
                    damageDealt = statistics.damageDealt + if (actor.definition.side == BattleSide.ALLIED) damage else 0,
                    damageReceived = statistics.damageReceived + if (victim.definition.side == BattleSide.ALLIED) damage else 0,
                    damagePrevented = statistics.damagePrevented + if (victim.definition.side == BattleSide.ALLIED)
                        (sourceBaseDamage - amount).coerceAtLeast(0) else 0
                )
                if (damage > 0) {
                    val id = nextImpactId++
                    impacts[id] = ActiveImpact(id, victim.definition.combatantId, damage, critical,
                        elapsedMillis + IMPACT_DURATION_MILLIS, technique.techniqueId,
                        technique.kind == TechniqueKind.SPECIAL)
                    while (impacts.size > MAX_ACTIVE_IMPACTS) impacts.remove(impacts.keys.first())
                }
                addThreat(victim, actor, damage)
                emit(BattleEvent.TechniqueHit(actor.definition.combatantId, victim.definition.combatantId,
                    technique.techniqueId, damage, critical))
                if (damage > 0 && actor.health > 0 && actor.definition.side == BattleSide.ALLIED) {
                    val expiry = elapsedMillis + 1_000L
                    pendingSupport[actor.definition.combatantId] = expiry
                    emit(BattleEvent.SupportWindowOpened(actor.definition.combatantId, expiry))
                }
                if (victim.health == 0) defeat(victim)
                else {
                    if (!enhanced) applyStatuses(victim, technique.statusEffects.map { it.copy(sourceCombatantId = actor.definition.combatantId) })
                    if (!enhanced && technique.staggerPower > 0 && technique.staggerPower > victim.definition.defense * 0.25f) {
                        applyStatuses(victim, listOf(BattleStatusEffect("stun", 500L, preventsActions = true)))
                    }
                    if (!enhanced && technique.knockbackDistance > 0f) applyKnockback(victim, actor, technique.knockbackDistance)
                }
            }
        }
        if (enhanced) contactedVictims.filter { it.health > 0 }.forEach { victim ->
            queueCounter(victim, actor, technique)
            applyStatuses(victim, technique.statusEffects.map { it.copy(sourceCombatantId = actor.definition.combatantId) })
            if (technique.staggerPower > 0 && technique.staggerPower > victim.definition.defense * 0.25f &&
                victim.defendUntil <= elapsedMillis) {
                applyStatuses(victim, listOf(BattleStatusEffect("stun", 500L, preventsActions = true,
                    refreshPolicy = StatusRefreshPolicy.IGNORE)))
            }
            if (technique.knockbackDistance > 0f) applyKnockback(victim, actor, technique.knockbackDistance)
        }
        if (totalDamage > 0 && technique.kind != TechniqueKind.SPECIAL) {
            addSpecialCharge(actor, SPECIAL_CHARGE_ON_ATTACK)
            damagedVictims.forEach { addSpecialCharge(it, SPECIAL_CHARGE_ON_HIT_RECEIVED) }
        }
        resolveSpecialOutcome(actor, technique, primary.definition.combatantId, success = totalDamage > 0)
    }

    private data class ResolvedHit(val damage: BattleDamageResult, val critical: Boolean)

    private fun damageFor(actor: Fighter, victim: Fighter, technique: TechniqueDefinition, guarding: Boolean): ResolvedHit {
        val attackMultiplier = actor.statuses.fold(1f) { value, status -> value * status.effect.attackMultiplier }
            .let { if (enhanced) it.coerceIn(0.5f, 1.3f) else it }
        val defenseMultiplier = victim.statuses.fold(1f) { value, status -> value * status.effect.defenseMultiplier }
            .let { if (enhanced) it.coerceIn(0.5f, 1.3f) else it }
        if (!enhanced) {
            // Preserve the exact v2 floating-point order and RNG consumption for old checkpoints.
            val raw = technique.power.toDouble() * actor.definition.attack * attackMultiplier *
                actor.definition.attribute.damageMultiplierAgainst(victim.definition.attribute) / 100.0 *
                actor.combatRandom.nextDouble(0.92, 1.08)
            val critical = actor.combatRandom.nextDouble() < technique.criticalChance.coerceIn(0f, 1f)
            val criticalRaw = if (critical) raw * technique.criticalMultiplier else raw
            val unguarded = criticalRaw * 100.0 / (100.0 + victim.definition.defense * defenseMultiplier)
            val amount = if (technique.power == 0) 0 else (unguarded * if (guarding) 0.3 else 1.0).toInt().coerceAtLeast(1)
            return ResolvedHit(BattleDamageResult(amount, unguarded.toInt()), critical)
        }
        val variance = actor.varianceRandom.nextInt(90, 111)
        val critical = actor.criticalRandom.nextDouble() < technique.criticalChance
        return ResolvedHit(BattleDamageResolver.resolve(BattleDamageInput(
            power = technique.power, attack = actor.definition.attack, defense = victim.definition.defense,
            variancePercent = variance, attackerAttribute = actor.definition.attribute,
            defenderAttribute = victim.definition.attribute, attackMultiplier = attackMultiplier,
            defenseMultiplier = defenseMultiplier, criticalMultiplier = if (critical) technique.criticalMultiplier else 1f,
            guarding = guarding, burned = actor.statuses.any { it.effect.mechanic == BattleStatusMechanic.BURN },
            frozen = victim.statuses.any { it.effect.mechanic == BattleStatusMechanic.FREEZE },
            counter = technique.reactionOnly, signaturePowerBonus =
                if (technique.techniqueId == actor.definition.signatureTechniqueId) actor.definition.signaturePowerBonus else 0,
            finisher = technique.kind == TechniqueKind.SPECIAL, formula = configuration.damageFormula
        )), critical)
    }

    private fun queueCounter(victim: Fighter, attacker: Fighter, incoming: TechniqueDefinition) {
        if (incoming.reactionOnly || incoming.kind !in listOf(TechniqueKind.BASIC, TechniqueKind.MELEE) ||
            victim.defendUntil <= elapsedMillis || victim.active != null || victim.pendingCounterTargetId != null ||
            victim.statuses.any { it.effect.preventsActions }) return
        val counter = victim.definition.counterTechniqueId?.let(techniques::get) ?: return
        if (!ready(victim, counter) || availableEnergy(victim) < counter.energyCost ||
            !inRange(victim, attacker, counter) || victim.readiness <= 0f) return
        if (victim.counterRandom.nextDouble() < victim.profile.counterChance) {
            victim.pendingCounterTargetId = attacker.definition.combatantId
        }
    }

    private fun startPendingCounter(actor: Fighter) {
        val targetId = actor.pendingCounterTargetId ?: return
        actor.pendingCounterTargetId = null
        val enemy = target(actor, targetId, false) ?: return
        val counter = actor.definition.counterTechniqueId?.let(techniques::get) ?: return
        if (actor.active != null || actor.knockbackRemainingMillis > 0 || actor.statuses.any { it.effect.preventsActions } ||
            !ready(actor, counter) || availableEnergy(actor) < counter.energyCost || !inRange(actor, enemy, counter)) return
        actor.defendUntil = 0
        finishOrder(actor, OrderStatus.COMPLETED)
        actor.plannedTechniqueId = null
        actor.positioningTechniqueId = null
        actor.energy -= counter.energyCost
        actor.readiness = max(BattleRules.READINESS_MIN, actor.readiness - BattleRules.readinessCost(counter))
        actor.active = ActiveTechnique(counter, targetId)
        actor.counterCount++
        actor.techniqueUses[counter.techniqueId] = (actor.techniqueUses[counter.techniqueId] ?: 0) + 1
        setTarget(actor, targetId)
        actor.lastDecision = "Defesa convertida em contra-ataque."
        transition(actor, CombatantState.ATTACK_STARTUP)
        emit(BattleEvent.CounterTriggered(actor.definition.combatantId, targetId))
        emit(BattleEvent.TechniqueStarted(actor.definition.combatantId, counter.techniqueId, targetId))
    }

    private fun launchProjectile(actor: Fighter, active: ActiveTechnique) {
        val recipient = target(actor, active.targetId, false)
        if (recipient == null) {
            emit(BattleEvent.TechniqueMissed(actor.definition.combatantId, active.technique.techniqueId, "Alvo indisponível no lançamento."))
            resolveSpecialOutcome(actor, active.technique, active.targetId, success = false)
            return
        }
        val dx = recipient.position.x - actor.position.x
        val dz = recipient.position.z - actor.position.z
        val distance = hypot(dx, dz).coerceAtLeast(0.001f)
        val projectileId = nextProjectileId++
        projectiles[projectileId] = ActiveProjectile(
            id = projectileId,
            ownerId = actor.definition.combatantId,
            targetId = recipient.definition.combatantId,
            technique = active.technique,
            position = actor.position,
            velocityX = dx / distance * active.technique.projectileSpeed,
            velocityZ = dz / distance * active.technique.projectileSpeed,
            remainingMillis = active.technique.projectileLifetimeMillis
        )
        emit(BattleEvent.ProjectileLaunched(projectileId, actor.definition.combatantId, active.technique.techniqueId))
    }

    /** Swept-circle collision prevents fast attacks from passing through a fighter between steps. */
    private fun advanceProjectiles() {
        val iterator = projectiles.values.iterator()
        while (iterator.hasNext()) {
            val projectile = iterator.next()
            val start = projectile.position
            val stepMillis = min(STEP, projectile.remainingMillis)
            val end = BattlePosition(
                start.x + projectile.velocityX * stepMillis / 1000f,
                start.z + projectile.velocityZ * stepMillis / 1000f
            )
            val owner = combatants[projectile.ownerId]
            val impact = combatants.values.asSequence()
                .filter { it.health > 0 && it.definition.side != owner?.definition?.side }
                .mapNotNull { target ->
                    val fraction = segmentCircleEntry(start, end, target.position,
                        projectile.technique.projectileRadius + target.definition.collisionRadius)
                    fraction?.let { target to it }
                }
                .minWithOrNull(compareBy<Pair<Fighter, Float>> { it.second }.thenBy { it.first.definition.combatantId })
            if (impact != null && owner != null) {
                val (victim, fraction) = impact
                projectile.position = BattlePosition(start.x + (end.x - start.x) * fraction,
                    start.z + (end.z - start.z) * fraction)
                resolveTechnique(owner, ActiveTechnique(projectile.technique, projectile.targetId),
                    projectileImpact = true, hitTargetId = victim.definition.combatantId)
                statistics = statistics.copy(projectilesHit = statistics.projectilesHit + 1)
                iterator.remove()
                continue
            }
            projectile.position = end
            projectile.remainingMillis -= stepMillis
            if (projectile.remainingMillis <= 0L) {
                emit(BattleEvent.ProjectileMissed(projectile.id, projectile.technique.techniqueId))
                statistics = statistics.copy(projectilesMissed = statistics.projectilesMissed + 1)
                iterator.remove()
            }
        }
    }

    private fun segmentCircleEntry(start: BattlePosition, end: BattlePosition, center: BattlePosition, radius: Float): Float? {
        val dx = end.x - start.x
        val dz = end.z - start.z
        val lengthSquared = dx * dx + dz * dz
        if (lengthSquared <= 0f) return if (start.distanceTo(center) <= radius) 0f else null
        val fromX = start.x - center.x
        val fromZ = start.z - center.z
        val c = fromX * fromX + fromZ * fromZ - radius * radius
        if (c <= 0f) return 0f
        val b = 2f * (fromX * dx + fromZ * dz)
        val discriminant = b * b - 4f * lengthSquared * c
        if (discriminant < 0f) return null
        val entry = (-b - kotlin.math.sqrt(discriminant)) / (2f * lengthSquared)
        return entry.takeIf { it in 0f..1f }
    }

    private fun applyKnockback(victim: Fighter, source: Fighter, distance: Float) {
        if (!distance.isFinite() || distance <= 0f || victim.health <= 0) return
        val dx = victim.position.x - source.position.x
        val dz = victim.position.z - source.position.z
        val length = hypot(dx, dz).coerceAtLeast(0.001f)
        val speed = 5f
        cancelAction(victim, "Ação interrompida por impacto.")
        victim.knockbackVelocityX = dx / length * speed
        victim.knockbackVelocityZ = dz / length * speed
        victim.knockbackRemainingMillis = (distance / speed * 1_000f).toLong().coerceAtLeast(1L)
        transition(victim, CombatantState.KNOCKBACK)
    }

    private fun executeItem(actor: Fighter, order: BattleOrder, action: TrainerAction.UseItem) {
        val itemId = itemReservations[order.orderId]
        val item = itemId?.let(itemDefinitions::get)
        val recipient = target(actor, action.targetId, true)
        if (item == null || recipient == null || (itemCounts[item.itemId] ?: 0) <= 0) {
            finishOrder(actor, OrderStatus.FAILED, "Alvo ou item de treino indisponível.")
            return
        }
        val changed = when (item.kind) {
            BattleItemKind.HEAL_HEALTH -> min(item.amount, recipient.definition.maxHealth - recipient.health).also {
                recipient.health += it
            }
            BattleItemKind.RESTORE_ENERGY -> min(item.amount, recipient.definition.maxEnergy - recipient.energy).also {
                recipient.energy += it
            }
            BattleItemKind.CLEANSE_STATUS -> if (enhanced) {
                val removable = recipient.statuses.filter { !isBuff(it.effect) }
                if (removable.any { it.effect.preventsActions }) recipient.controlImmuneUntil = elapsedMillis + BattleRules.CONTROL_IMMUNITY_MILLIS
                recipient.statuses.removeAll(removable.toSet())
                removable.size
            } else recipient.statuses.size.also { recipient.statuses.clear() }
        }
        if (changed <= 0) {
            finishOrder(actor, OrderStatus.FAILED, "O alvo não precisa deste item agora.")
            return
        }
        itemCounts[item.itemId] = (itemCounts[item.itemId] ?: 0) - 1
        itemReservations.remove(order.orderId)
        if (actor.definition.side == BattleSide.ALLIED) statistics = statistics.copy(itemsUsed = statistics.itemsUsed + 1)
        transition(recipient, CombatantState.USING_ITEM_EFFECT)
        emit(BattleEvent.ItemUsed(actor.definition.combatantId, recipient.definition.combatantId, item.itemId, changed))
        finishOrder(actor, OrderStatus.COMPLETED)
        transition(recipient, CombatantState.SELECT_TARGET)
    }

    private fun move(actor: Fighter, recipient: Fighter, desiredDistance: Float, deltaMillis: Long): Boolean {
        val dx = recipient.position.x - actor.position.x
        val dz = recipient.position.z - actor.position.z
        val distance = hypot(dx, dz)
        val difference = distance - desiredDistance
        if (abs(difference) <= POSITION_EPSILON) {
            transition(actor, CombatantState.POSITIONING)
            return false
        }
        val slow = actor.statuses.filter { it.effect.slowsMovement }.maxOfOrNull { it.effect.magnitude } ?: 0f
        val movementMultiplier = movementMultiplier(actor)
        val step = min(abs(difference), actor.definition.movementSpeed * movementMultiplier *
            (1f - slow.coerceIn(0f, 1f)) * deltaMillis / 1000f)
        val sign = if (difference > 0) 1f else -1f
        transition(actor, if (sign > 0) CombatantState.MOVE_TO_TARGET else CombatantState.MOVE_AWAY)
        val nx = if (distance > 0.0001f) dx / distance else 1f
        val nz = if (distance > 0.0001f) dz / distance else 0f
        val previous = actor.position
        actor.position = clamp(BattlePosition(actor.position.x + nx * step * sign, actor.position.z + nz * step * sign), actor.definition.collisionRadius)
        return actor.position.distanceTo(previous) > 0.0001f
    }

    private fun separateCombatants() {
        val living = combatants.values.filter { it.health > 0 }.sortedBy { it.definition.combatantId }
        repeat(3) {
            for (i in living.indices) for (j in i + 1 until living.size) {
                val a = living[i]
                val b = living[j]
                val dx = b.position.x - a.position.x
                val dz = b.position.z - a.position.z
                val distance = hypot(dx, dz)
                val overlap = a.definition.collisionRadius + b.definition.collisionRadius - distance
                if (overlap <= 0) continue
                val nx = if (distance > 0.0001f) dx / distance else 1f
                val nz = if (distance > 0.0001f) dz / distance else 0f
                a.position = clamp(BattlePosition(a.position.x - nx * overlap / 2, a.position.z - nz * overlap / 2), a.definition.collisionRadius)
                b.position = clamp(BattlePosition(b.position.x + nx * overlap / 2, b.position.z + nz * overlap / 2), b.definition.collisionRadius)
            }
        }
    }

    private fun clamp(position: BattlePosition, radius: Float): BattlePosition {
        val limit = configuration.arenaRadius - radius
        val distance = hypot(position.x, position.z)
        if (distance <= limit || distance == 0f) return position
        return BattlePosition(position.x * limit / distance, position.z * limit / distance)
    }

    private fun chooseEnemy(actor: Fighter): Fighter? {
        if (enhanced) return chooseEnemyByPolicy(actor)
        val enemies = combatants.values.filter { it.health > 0 && it.definition.side != actor.definition.side }
        if (actor.focusUntil > elapsedMillis) enemies.firstOrNull { it.definition.combatantId == actor.focusId }?.let { return it }
        val hasRecordedThreat = enemies.any { (actor.threat[it.definition.combatantId] ?: 0L) > 0L }
        val best = if (hasRecordedThreat) {
            enemies.minWithOrNull(
                compareByDescending<Fighter> { actor.threat[it.definition.combatantId] ?: 0L }
                    .thenBy { actor.position.distanceTo(it.position) }
                    .thenBy { it.definition.combatantId }
            )
        } else {
            // Distance changes continuously during the opening lineup. Keep the
            // first target stable so later threat changes are legible and replayable.
            enemies.minByOrNull { it.definition.combatantId }
        } ?: return null
        val bestThreat = actor.threat[best.definition.combatantId] ?: 0L
        val coordinatedTargetId = if (actor.personality.teamWeight >= 1.06f) {
            combatants.values.asSequence()
                .filter { it.health > 0 && it.definition.side == actor.definition.side &&
                    it.definition.combatantId != actor.definition.combatantId }
                .mapNotNull { teammate -> target(actor, teammate.targetId, false)?.definition?.combatantId }
                .groupingBy { it }
                .eachCount()
                .maxWithOrNull(compareBy<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                ?.key
        } else null
        val coordinatedTarget = coordinatedTargetId?.let(combatants::get)
        val coordinatedThreat = coordinatedTarget?.let { actor.threat[it.definition.combatantId] ?: 0L } ?: 0L
        val followsTeamFocus = coordinatedTarget != null && bestThreat <= coordinatedThreat * 1.5 + 5
        val preferred = if (followsTeamFocus) requireNotNull(coordinatedTarget) else best
        val current = target(actor, actor.targetId, false) ?: return preferred
        val currentThreat = actor.threat[current.definition.combatantId] ?: 0L
        val switchThreshold = 1.05 + actor.personality.targetStickiness.coerceIn(0f, 1f).toDouble() * 0.3
        val preferredThreat = actor.threat[preferred.definition.combatantId] ?: 0L
        return if (preferred.definition.combatantId != current.definition.combatantId &&
            (followsTeamFocus || preferredThreat > currentThreat * switchThreshold + 5)) preferred else current
    }

    private fun chooseEnemyByPolicy(actor: Fighter): Fighter? {
        val enemies = combatants.values.filter { it.health > 0 && it.definition.side != actor.definition.side }
        if (actor.focusUntil > elapsedMillis) enemies.firstOrNull { it.definition.combatantId == actor.focusId }?.let {
            actor.targetReason = "Foco do treinador."
            return it
        }
        val maximumThreat = enemies.maxOfOrNull { actor.threat[it.definition.combatantId] ?: 0L }?.coerceAtLeast(1) ?: return null
        fun score(enemy: Fighter): Double {
            val distance = actor.position.distanceTo(enemy.position)
            val proximity = 1.0 / (1.0 + distance)
            val threat = (actor.threat[enemy.definition.combatantId] ?: 0L).toDouble() / maximumThreat
            val vulnerability = 1.0 - enemy.health.toDouble() / enemy.definition.maxHealth
            val status = if (enemy.statuses.any { !isBuff(it.effect) }) 1.0 else 0.0
            val coordinated = if (actor.personality.teamWeight >= 1.06f && combatants.values.any {
                it.health > 0 && it !== actor && it.definition.side == actor.definition.side &&
                    it.targetId == enemy.definition.combatantId
            }) 0.15 else 0.0
            return proximity + coordinated + when (actor.profile.targetPolicy) {
                BattleTargetPolicy.THREAT -> threat
                BattleTargetPolicy.CLOSEST -> 0.0
                BattleTargetPolicy.VULNERABLE -> vulnerability * 0.7 + threat * 0.3
                BattleTargetPolicy.STATUS -> status * 0.6 + vulnerability * 0.2 + threat * 0.3
            }
        }
        val best = enemies.maxWithOrNull(compareBy<Fighter> { score(it) }.thenByDescending { it.definition.stableRngKey }) ?: return null
        val current = target(actor, actor.targetId, false)
        val threshold = actor.profile.targetSwitchThreshold + actor.personality.targetStickiness * 0.15f
        val selected = if (current != null && current !== best && score(best) < score(current) + threshold) current else best
        actor.targetReason = "${actor.profile.targetPolicy.name}: alvo válido com limiar de troca (${(threshold * 100).toInt()}%)."
        return selected
    }

    private fun readinessRequired(actor: Fighter, technique: TechniqueDefinition): Float {
        return BattleRules.readinessRequired(chargeMode(actor), technique)
    }

    private fun chargeMode(actor: Fighter): BattleChargeMode = when (actor.strategy) {
            BattleStrategy.AGGRESSIVE -> BattleChargeMode.PRESSURE
            BattleStrategy.CONSERVATIVE -> BattleChargeMode.FULL
            else -> actor.profile.chargeMode
    }

    private fun movementMultiplier(actor: Fighter): Float = actor.statuses
        .fold(1f) { value, status -> value * status.effect.movementMultiplier }
        .let { if (enhanced) it.coerceIn(0.5f, 1.3f) else it }

    private fun chooseAlly(actor: Fighter): Fighter = combatants.values
        .filter { it.health > 0 && it.definition.side == actor.definition.side }
        .minWith(compareBy<Fighter> { it.health.toDouble() / it.definition.maxHealth }.thenBy { it.definition.combatantId })

    private fun target(actor: Fighter, id: String?, ally: Boolean): Fighter? = combatants[id]
        ?.takeIf { it.health > 0 && (it.definition.side == actor.definition.side) == ally }

    private fun setTarget(actor: Fighter, id: String?) {
        if (actor.targetId == id) return
        actor.targetId = id
        if (enhanced) {
            actor.targetChanges++
            actor.positioningTechniqueId = null
        }
        emit(BattleEvent.TargetChanged(actor.definition.combatantId, id))
    }

    private fun inRange(actor: Fighter, target: Fighter, technique: TechniqueDefinition): Boolean =
        when (technique.rangeProfile) {
            TechniqueRangeProfile.SELF, TechniqueRangeProfile.ALL_FIELD -> true
            else -> tacticalRangeWindow(actor, target, technique).let { window ->
                actor.position.distanceTo(target.position) in window.activationMin..window.activationMax
            }
        }

    private fun tacticalRangeWindow(
        actor: Fighter,
        target: Fighter,
        technique: TechniqueDefinition
    ): TacticalRangeWindow {
        if (technique.rangeProfile == TechniqueRangeProfile.SELF) return TacticalRangeWindow(0f, 0f, 0f, 0f, 0f)
        if (technique.rangeProfile == TechniqueRangeProfile.ALL_FIELD) {
            return TacticalRangeWindow(0f, technique.maxRange, 0f, technique.maxRange, 0f)
        }
        val separation = if (actor === target) 0f else actor.definition.collisionRadius + target.definition.collisionRadius
        val geometryMax = if (technique.impactShape == TechniqueImpactShape.AROUND_USER && technique.areaRadius > 0f) {
            min(technique.maxRange, technique.areaRadius)
        } else technique.maxRange
        val activationMin = technique.minRange.coerceAtMost(geometryMax)
        val activationMax = geometryMax
        val physicalMin = max(separation, activationMin).coerceAtMost(activationMax)
        val span = (activationMax - physicalMin).coerceAtLeast(0f)
        val (tacticalMin, tacticalMax) = when (technique.rangeProfile) {
            TechniqueRangeProfile.CLOSE -> physicalMin to activationMax
            TechniqueRangeProfile.CLOSE_MEDIUM, TechniqueRangeProfile.MEDIUM_LONG ->
                (physicalMin + span * 0.45f) to (physicalMin + span * 0.85f)
            TechniqueRangeProfile.CUSTOM -> physicalMin to activationMax
            TechniqueRangeProfile.SELF, TechniqueRangeProfile.ALL_FIELD -> physicalMin to activationMax
        }
        val base = (tacticalMin + tacticalMax) * 0.5f
        val personalityOffset = if (actor.currentOrder == null) actor.personality.preferredRangeBias else 0f
        val preferred = (base + personalityOffset).coerceIn(tacticalMin, tacticalMax)
        return TacticalRangeWindow(activationMin, activationMax, tacticalMin, tacticalMax, preferred)
    }

    private fun TechniqueRangeProfile.isPositionalProfile(): Boolean = when (this) {
        TechniqueRangeProfile.CLOSE,
        TechniqueRangeProfile.CLOSE_MEDIUM,
        TechniqueRangeProfile.MEDIUM_LONG -> true
        TechniqueRangeProfile.CUSTOM,
        TechniqueRangeProfile.SELF,
        TechniqueRangeProfile.ALL_FIELD -> false
    }

    private fun ready(actor: Fighter, technique: TechniqueDefinition) = (actor.cooldowns[technique.techniqueId] ?: 0L) <= elapsedMillis
    private fun isSupport(technique: TechniqueDefinition) = technique.kind == TechniqueKind.HEAL || technique.kind == TechniqueKind.SUPPORT
    private fun availableEnergy(actor: Fighter) = actor.energy - reservations.values.filter { it.actorId == actor.definition.combatantId }.sumOf { it.energy }
    private fun availableCommandPoints() = commandPoints - reservations.values.sumOf { it.commandPoints }
    private fun availableSpecialCharge(actor: Fighter) = actor.specialCharge - reservations.values
        .filter { it.actorId == actor.definition.combatantId }
        .sumOf { it.specialCharge }

    private fun addSpecialCharge(actor: Fighter, amount: Int) {
        if (amount <= 0 || actor.health <= 0 || actor.definition.specialTechniqueId == null) return
        val previous = actor.specialCharge
        actor.specialCharge = (actor.specialCharge + amount).coerceAtMost(SPECIAL_CHARGE_MAX)
        if (previous < SPECIAL_CHARGE_MAX && actor.specialCharge == SPECIAL_CHARGE_MAX) {
            emit(BattleEvent.SpecialReady(actor.definition.combatantId))
        }
    }

    private fun resolveSpecialOutcome(
        actor: Fighter,
        technique: TechniqueDefinition,
        targetId: String?,
        success: Boolean
    ) {
        if (technique.kind != TechniqueKind.SPECIAL) return
        statistics = if (success) {
            statistics.copy(specialsHit = statistics.specialsHit + 1)
        } else {
            statistics.copy(specialsMissed = statistics.specialsMissed + 1)
        }
        emit(BattleEvent.SpecialResolved(actor.definition.combatantId, technique.techniqueId, targetId, success))
    }

    private fun tickStatuses(actor: Fighter) {
        val iterator = actor.statuses.iterator()
        while (iterator.hasNext()) {
            val status = iterator.next()
            val activeMillis = min(STEP, status.remaining)
            status.remaining -= activeMillis
            status.damageRemainder += status.effect.damagePerSecond.toDouble() * activeMillis / 1000.0
            var damage = status.damageRemainder.toInt()
            status.damageRemainder -= damage
            if (enhanced && status.effect.tickIntervalMillis > 0) {
                status.tickElapsedMillis += activeMillis
                while (status.tickElapsedMillis >= status.effect.tickIntervalMillis) {
                    status.tickElapsedMillis -= status.effect.tickIntervalMillis
                    val percent = actor.statusRandom.nextInt(status.effect.maxHealthPercentPerTickMin,
                        status.effect.maxHealthPercentPerTickMax + 1)
                    damage += (actor.definition.maxHealth.toLong() * percent / 100).toInt()
                }
            }
            val appliedDamage = min(damage, actor.health).coerceAtLeast(0)
            actor.health = (actor.health - appliedDamage).coerceAtLeast(0)
            if (appliedDamage > 0) {
                val source = status.effect.sourceCombatantId?.let(combatants::get)
                statistics = statistics.copy(
                    damageDealt = statistics.damageDealt + if (source?.definition?.side == BattleSide.ALLIED) appliedDamage else 0,
                    damageReceived = statistics.damageReceived + if (actor.definition.side == BattleSide.ALLIED) appliedDamage else 0
                )
                source?.let { addThreat(actor, it, appliedDamage) }
            }
            if (status.remaining <= 0) {
                if (enhanced && status.effect.preventsActions) actor.controlImmuneUntil = elapsedMillis + BattleRules.CONTROL_IMMUNITY_MILLIS
                iterator.remove()
            }
        }
        if (actor.health == 0) defeat(actor)
    }

    private fun applyStatuses(actor: Fighter, effects: List<BattleStatusEffect>) {
        if (actor.health <= 0) return
        effects.filter { it.durationMillis > 0 }.forEach { effect ->
            val existing = actor.statuses.firstOrNull { it.effect.id == effect.id }
            if (enhanced) {
                if (!isBuff(effect) && actor.statuses.any { it.effect.protectsFromStatuses }) return@forEach
                if (effect.preventsActions && elapsedMillis < actor.controlImmuneUntil) return@forEach
                if (existing != null && effect.refreshPolicy == StatusRefreshPolicy.IGNORE) return@forEach
                if (effect.exclusivityGroup != null && actor.statuses.any {
                    it.effect.id != effect.id && it.effect.exclusivityGroup == effect.exclusivityGroup
                }) return@forEach
                if (effect.procChance <= 0f || effect.procChance < 1f && actor.statusRandom.nextDouble() >= effect.procChance) return@forEach
            }
            if (existing == null) actor.statuses += ActiveStatus(effect, effect.durationMillis)
            else {
                existing.effect = effect
                existing.remaining = if (enhanced && effect.refreshPolicy == StatusRefreshPolicy.REPLACE) effect.durationMillis
                    else max(existing.remaining, effect.durationMillis)
                if (enhanced && effect.refreshPolicy == StatusRefreshPolicy.REPLACE) existing.tickElapsedMillis = 0
            }
            emit(BattleEvent.StatusApplied(actor.definition.combatantId, effect.id))
            if (effect.preventsActions) {
                if (actor.active?.phase == Phase.STARTUP && canInterrupt(actor)) cancelAction(actor, "Ação interrompida por um efeito.")
                transition(actor, CombatantState.STUNNED)
            }
        }
    }

    private fun isBuff(effect: BattleStatusEffect): Boolean = effect.attackMultiplier > 1f ||
        effect.defenseMultiplier > 1f || effect.movementMultiplier > 1f || effect.cooldownMultiplier < 1f ||
        effect.protectsFromStatuses

    private fun addThreat(actor: Fighter, source: Fighter, amount: Int) {
        if (amount > 0) actor.threat[source.definition.combatantId] = (actor.threat[source.definition.combatantId] ?: 0L) + amount
    }

    private fun canInterrupt(actor: Fighter): Boolean = actor.active?.let {
        it.phase == Phase.STARTUP && it.technique.interruptibleDuringStartup
    } ?: true

    private fun cancelAction(actor: Fighter, reason: String) {
        actor.active?.let {
            if (it.technique.kind == TechniqueKind.SPECIAL && !it.resolved) {
                resolveSpecialOutcome(actor, it.technique, it.targetId, success = false)
            }
            actor.cooldowns[it.technique.techniqueId] = elapsedMillis + cooldownDuration(actor, it.technique)
        }
        actor.active = null
        actor.defendUntil = 0L
        actor.plannedTechniqueId = null
        actor.positioningTechniqueId = null
        finishOrder(actor, OrderStatus.CANCELLED, reason)
    }

    private fun finishOrder(actor: Fighter, status: OrderStatus, reason: String? = null) {
        val order = actor.currentOrder ?: return
        actor.currentOrder = null
        actor.plannedTechniqueId = null
        reservations.remove(order.orderId)
        itemReservations.remove(order.orderId)
        if (status == OrderStatus.COMPLETED) statistics = statistics.copy(ordersCompleted = statistics.ordersCompleted + 1)
        actor.lastDecision = if (status == OrderStatus.COMPLETED) {
            "Ordem #${order.orderId} concluída; autonomia retomada."
        } else {
            reason ?: "Ordem #${order.orderId} terminou: ${status.name.lowercase()}."
        }
        if (status == OrderStatus.FAILED || status == OrderStatus.EXPIRED) actor.lastOrderFailure = reason
        publish(order.orderId, status, reason)
    }

    private fun defeat(actor: Fighter) {
        if (actor.state == CombatantState.DEFEATED) return
        cancelAction(actor, "O parceiro foi derrotado.")
        cancelQueue(actor, "O parceiro foi derrotado.")
        actor.statuses.clear()
        pendingSupport.remove(actor.definition.combatantId)
        transition(actor, CombatantState.DEFEATED)
        emit(BattleEvent.CombatantDefeated(actor.definition.combatantId))
    }

    private fun cancelQueue(actor: Fighter, reason: String) {
        while (actor.orders.isNotEmpty()) {
            val order = actor.orders.removeFirst()
            reservations.remove(order.orderId)
            itemReservations.remove(order.orderId)
            publish(order.orderId, OrderStatus.CANCELLED, reason)
        }
    }

    private fun finish(result: BattleOutcome) {
        if (outcome != null) return
        paused = true
        pauseReason = "finished"
        combatants.values.forEach {
            cancelAction(it, "A batalha terminou.")
            cancelQueue(it, "A batalha terminou.")
            transition(it, if (it.health > 0) CombatantState.WAITING else CombatantState.DEFEATED)
        }
        pendingSupport.clear()
        outcome = BattleResult(result, elapsedMillis, eventCount + 1, statistics)
        projectiles.clear()
        impacts.clear()
        itemReservations.clear()
        emit(BattleEvent.BattleEnded(requireNotNull(outcome)))
    }

    private fun transition(actor: Fighter, state: CombatantState) {
        if (actor.state == state) return
        actor.state = state
        emit(BattleEvent.StateChanged(actor.definition.combatantId, state))
    }

    private fun publish(id: Long, status: OrderStatus, reason: String? = null): OrderUpdate {
        val update = OrderUpdate(id, status, reason)
        emit(BattleEvent.OrderChanged(update))
        return update
    }

    private fun emit(event: BattleEvent) {
        eventCount++
        if (recentEvents.size == 96) recentEvents.removeFirst()
        recentEvents.addLast(event)
    }

    private fun validateTechnique(technique: TechniqueDefinition) {
        require(technique.techniqueId.isNotBlank())
        require(technique.power >= 0 && technique.healPower >= 0 && technique.energyCost >= 0 && technique.commandPointCost >= 0)
        require(technique.minRange.isFinite() && technique.maxRange.isFinite() && technique.minRange >= 0f && technique.maxRange >= technique.minRange)
        require(technique.areaRadius.isFinite() && technique.areaRadius >= 0f && technique.staggerPower.isFinite() && technique.staggerPower >= 0f)
        require(technique.projectileSpeed.isFinite() && technique.projectileSpeed > 0f)
        require(technique.projectileRadius.isFinite() && technique.projectileRadius >= 0f)
        require(technique.projectileLifetimeMillis in 1..configuration.maxDurationMillis)
        require(technique.hitCount in 1..16)
        require(technique.accuracyPercent in 0..100)
        require(technique.readinessCost == null || technique.readinessCost.isFinite() && technique.readinessCost in 0f..255f)
        require(technique.knockbackDistance.isFinite() && technique.knockbackDistance >= 0f)
        require(technique.criticalChance.isFinite() && technique.criticalChance in 0f..1f)
        require(technique.criticalMultiplier.isFinite() && technique.criticalMultiplier >= 1f)
        require(listOf(technique.startupMillis, technique.activeMillis, technique.recoveryMillis, technique.cooldownMillis).all { it in 0..configuration.maxDurationMillis })
        require(technique.startupMillis > 0 || technique.activeMillis > 0 || technique.recoveryMillis > 0)
        technique.statusEffects.forEach {
            require(it.id.isNotBlank() && it.durationMillis in 0..configuration.maxDurationMillis)
            require(it.magnitude.isFinite() && it.damagePerSecond.isFinite() && it.damagePerSecond >= 0)
            require(it.attackMultiplier.isFinite() && it.attackMultiplier > 0f)
            require(it.defenseMultiplier.isFinite() && it.defenseMultiplier > 0f)
            require(it.movementMultiplier.isFinite() && it.movementMultiplier > 0f)
            require(it.cooldownMultiplier.isFinite() && it.cooldownMultiplier > 0f)
            require(it.procChance.isFinite() && it.procChance in 0f..1f)
            require(it.tickIntervalMillis in 0..configuration.maxDurationMillis)
            require(it.maxHealthPercentPerTickMin in 0..100 && it.maxHealthPercentPerTickMax in it.maxHealthPercentPerTickMin..100)
            require(it.maxHealthPercentPerTickMax == 0 || it.tickIntervalMillis > 0)
        }
    }

    private fun cooldownDuration(actor: Fighter, technique: TechniqueDefinition): Long =
        (technique.cooldownMillis * actor.definition.techniqueCooldownMultiplier *
            actor.statuses.fold(1f) { value, status -> value * status.effect.cooldownMultiplier }
                .let { if (enhanced) it.coerceIn(0.7f, 1.5f) else it })
            .roundToLong()
            .coerceIn(0L, configuration.maxDurationMillis)

    private class Fighter(
        val definition: CombatantDefinition,
        var position: BattlePosition,
        val personality: PersonalityBattleProfile,
        val profile: BattleAiProfile,
        val scheduleRandom: Random,
        val movementRandom: Random,
        val reactionRandom: Random,
        val choiceRandom: Random,
        val combatRandom: Random,
        val varianceRandom: Random,
        val criticalRandom: Random,
        val statusRandom: Random,
        val counterRandom: Random,
        val accuracyRandom: Random
    ) {
        var health = definition.initialHealth ?: definition.maxHealth
        var energy = definition.initialEnergy ?: definition.maxEnergy
        var energyRemainder = 0.0
        var specialCharge = 0
        var specialChargeRemainder = 0.0
        var readiness = BattleRules.READINESS_MAX
        var buffsRemaining = profile.buffLimit
        var targetReason = ""
        var positioningTechniqueId: String? = null
        var positioningStartedAt = 0L
        var positioningProgressAt = 0L
        var positioningBestError = Float.MAX_VALUE
        var positioningSettled = false
        var positioningReplans = 0
        val unreachableUntil = mutableMapOf<String, Long>()
        var controlImmuneUntil = 0L
        var pendingCounterTargetId: String? = null
        val techniqueUses = mutableMapOf<String, Int>()
        var guardCount = 0
        var counterCount = 0
        var targetChanges = 0
        var incapacitatedMillis = 0L
        var readinessWaitingMillis = 0L
        var distanceSum = 0.0
        var distanceSamples = 0L
        var readinessSum = 0.0
        var readinessSamples = 0L
        var state = CombatantState.IDLE
        var strategy = definition.strategy
        var lastDecision = "Aguardando decisão autônoma."
        var techniqueScores: Map<String, Float> = emptyMap()
        var techniqueScoreComponents: Map<String, Map<String, Float>> = emptyMap()
        var lastOrderFailure: String? = null
        var targetId: String? = null
        var focusId: String? = null
        var focusUntil = 0L
        var defendUntil = 0L
        var nextAutoGuardAt = 0L
        var knockbackRemainingMillis = 0L
        var knockbackVelocityX = 0f
        var knockbackVelocityZ = 0f
        var nextDecisionAt = 0L
        var roamDestination: BattlePosition? = null
        var lastTechniqueId: String? = null
        var plannedTechniqueId: String? = null
        var active: ActiveTechnique? = null
        var currentOrder: BattleOrder? = null
        val orders = ArrayDeque<BattleOrder>()
        val cooldowns = mutableMapOf<String, Long>()
        val statuses = mutableListOf<ActiveStatus>()
        val threat = mutableMapOf<String, Long>()
    }

    private enum class Phase { STARTUP, ACTIVE, RECOVERY }
    private class ActiveTechnique(
        val technique: TechniqueDefinition,
        val targetId: String,
        var phase: Phase = Phase.STARTUP,
        var phaseElapsed: Long = 0L,
        var resolved: Boolean = false
    )
    private class ActiveStatus(var effect: BattleStatusEffect, var remaining: Long, var damageRemainder: Double = 0.0)
    {
        var tickElapsedMillis = 0L
    }
    private class ActiveProjectile(
        val id: Long,
        val ownerId: String,
        val targetId: String,
        val technique: TechniqueDefinition,
        var position: BattlePosition,
        val velocityX: Float,
        val velocityZ: Float,
        var remainingMillis: Long
    )
    private data class ActiveImpact(
        val id: Long,
        val targetId: String,
        val damage: Int,
        val critical: Boolean,
        val expiresAtMillis: Long,
        val techniqueId: String,
        val isSpecial: Boolean
    )
    private data class Reservation(
        val actorId: String,
        val energy: Int,
        val commandPoints: Int,
        val specialCharge: Int = 0
    )
    private data class TacticalRangeWindow(
        val activationMin: Float,
        val activationMax: Float,
        val tacticalMin: Float,
        val tacticalMax: Float,
        val preferred: Float
    )

    private companion object {
        const val STEP = BattleRules.STEP_MILLIS
        const val POSITION_EPSILON = 0.05f
        const val IMPACT_DURATION_MILLIS = BATTLE_IMPACT_LIFETIME_MILLIS
        const val MAX_ACTIVE_IMPACTS = 8
        const val SPECIAL_CHARGE_MAX = 100
        const val SPECIAL_CHARGE_ON_ATTACK = 18
        const val SPECIAL_CHARGE_ON_HIT_RECEIVED = 6
        val BASIC = TechniqueDefinition("basic_attack", "Ataque básico", TechniqueKind.BASIC, 48,
            maxRange = 1.8f, startupMillis = 360L, activeMillis = 100L, recoveryMillis = 720L,
            cooldownMillis = 1_800L, attackVisual = "small")
    }
}
