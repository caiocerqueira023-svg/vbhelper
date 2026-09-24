package com.github.nacabaro.vbhelper.battle.offline.core

import java.util.ArrayDeque
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/** Single-writer, fixed-step simulation. UI/rendering consume snapshots and submit orders. */
class BattleSimulator(
    private val configuration: BattleConfiguration,
    alliedTeam: BattleTeam,
    opposingTeam: BattleTeam,
    techniqueCatalog: Collection<TechniqueDefinition>,
    trainingItems: Collection<BattleItemDefinition> = emptyList()
) {
    private val random = Random(configuration.randomSeed)
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
                require(source.movementSpeed.isFinite() && source.movementSpeed >= 0f)
                require(source.collisionRadius.isFinite() && source.collisionRadius > 0f && source.collisionRadius < configuration.arenaRadius)
                require(source.preferredDistance.isFinite() && source.preferredDistance >= 0f)
                require(source.decisionDelayMinMillis in 0..configuration.maxDurationMillis)
                require(source.decisionDelayMaxMillis in source.decisionDelayMinMillis..configuration.maxDurationMillis)
                require(source.techniqueIds.all { it in techniques }) { "Unknown technique" }
                require(source.specialTechniqueId == null || source.specialTechniqueId in techniques)
                val definition = source.copy(techniqueIds = source.techniqueIds.toList())
                combatants[definition.combatantId] = Fighter(definition, clamp(positions[index], definition.collisionRadius))
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
            if (action.techniqueId !in actor.definition.techniqueIds && actor.definition.specialTechniqueId != action.techniqueId && technique != BASIC) {
                return reject("Esta técnica não pertence ao parceiro.")
            }
            if (!ready(actor, technique)) return reject("A técnica está em cooldown.")
            if (action.targetId != null && target(actor, action.targetId, isSupport(technique)) == null) return reject("Alvo indisponível.")
            if (availableEnergy(actor) < technique.energyCost || availableCommandPoints() < technique.commandPointCost) {
                return reject("Faltam energia ou pontos de comando disponíveis.")
            }
            reservations[id] = Reservation(actorId, technique.energyCost, technique.commandPointCost)
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
                    threatByCombatant = actor.threat.toMap(),
                    lastOrderFailure = actor.lastOrderFailure
                ))
        }
        return BattleSnapshot(elapsedMillis, paused, pauseReason, commandPoints, configuration.maxCommandPoints,
            members.filter { it.side == BattleSide.ALLIED }, members.filter { it.side == BattleSide.OPPOSING },
            pendingSupport.keys.toSet(), outcome, recentEvents.toList(), eventCount, commandPoints - availableCommandPoints(),
            projectiles.values.map { projectile -> ProjectileSnapshot(projectile.id, projectile.ownerId,
                projectile.targetId, projectile.technique.techniqueId, projectile.technique.attackVisual,
                projectile.position, projectile.velocityX, projectile.velocityZ) },
            itemDefinitions.values.map { item -> BattleItemSnapshot(item.itemId, item.displayName,
                itemCounts[item.itemId] ?: 0, itemReservations.values.count { it == item.itemId }) }, statistics,
            impacts.values.map { impact -> BattleImpactSnapshot(impact.id, impact.targetId, impact.damage,
                impact.critical, (impact.expiresAtMillis - elapsedMillis).coerceAtLeast(0L)) })
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
        }
        val impacts = mutableListOf<Pair<Fighter, ActiveTechnique>>()
        actors.filter { it.health > 0 }.forEach { actor ->
            if (actor.statuses.any { it.effect.preventsActions }) {
                transition(actor, CombatantState.STUNNED)
                return@forEach
            }
            if (actor.knockbackRemainingMillis > 0) {
                val activeMillis = min(STEP, actor.knockbackRemainingMillis)
                actor.position = clamp(BattlePosition(
                    actor.position.x + actor.knockbackVelocityX * activeMillis / 1000f,
                    actor.position.z + actor.knockbackVelocityZ * activeMillis / 1000f
                ), actor.definition.collisionRadius)
                actor.knockbackRemainingMillis -= activeMillis
                transition(actor, if (actor.knockbackRemainingMillis > 0) CombatantState.KNOCKBACK else CombatantState.SELECT_TARGET)
                if (actor.knockbackRemainingMillis > 0) return@forEach
            }
            if (actor.defendUntil > 0 && actor.defendUntil <= elapsedMillis) {
                actor.defendUntil = 0
                finishOrder(actor, OrderStatus.COMPLETED)
                transition(actor, CombatantState.SELECT_TARGET)
            }
            processOrders(actor)
            if (actor.defendUntil > elapsedMillis) {
                transition(actor, CombatantState.DEFENDING)
                return@forEach
            }
            if (actor.active != null) {
                progressTechnique(actor, impacts)
                return@forEach
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
                return@forEach
            }
            if (actor.currentOrder == null && actor.plannedTechniqueId == null && elapsedMillis < actor.nextDecisionAt) {
                continueAutonomousRead(actor)
                return@forEach
            }
            if (actor.currentOrder == null && think) planAutonomousAction(actor)
            actor.plannedTechniqueId?.let { techniques[it] }?.let { approachAndExecute(actor, it) }
        }
        // Commit simultaneous hits together: an opponent's already active hit
        // must not be suppressed simply because its ID sorts after the killer.
        impacts.forEach { (actor, active) ->
            if (active.technique.kind == TechniqueKind.PROJECTILE) launchProjectile(actor, active)
            else resolveTechnique(actor, active)
        }
        advanceProjectiles()
        separateCombatants()
        val allies = actors.any { it.health > 0 && it.definition.side == BattleSide.ALLIED }
        val enemies = actors.any { it.health > 0 && it.definition.side == BattleSide.OPPOSING }
        when {
            !allies && !enemies -> finish(BattleOutcome.DRAW)
            !allies -> finish(BattleOutcome.OPPOSING_VICTORY)
            !enemies -> finish(BattleOutcome.ALLIED_VICTORY)
            elapsedMillis >= configuration.maxDurationMillis -> finish(BattleOutcome.DRAW)
        }
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
                transition(actor, CombatantState.DEFENDING)
            }
            TrainerAction.KeepDistance, TrainerAction.MoveCloser -> {
                setTarget(actor, chooseEnemy(actor)?.definition?.combatantId)
                if (actor.targetId == null) finishOrder(actor, OrderStatus.FAILED, "Não há alvo disponível.")
            }
            is TrainerAction.UseTechnique -> {
                val technique = techniques.getValue(action.techniqueId)
                val recipient = if (action.targetId != null) target(actor, action.targetId, isSupport(technique))
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
        val minimum = actor.definition.decisionDelayMinMillis
        val maximum = actor.definition.decisionDelayMaxMillis
        val delay = if (minimum == maximum) minimum else random.nextLong(minimum, maximum + 1L)
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
        val desiredDistance = when (actor.strategy) {
            BattleStrategy.AGGRESSIVE -> 1.45f
            BattleStrategy.RANGED, BattleStrategy.DEFENSIVE -> 4.25f
            BattleStrategy.CONSERVATIVE, BattleStrategy.SUPPORT -> 3.45f
            BattleStrategy.BALANCED -> 2.1f + random.nextFloat() * 1.7f
        }.coerceAtLeast(actor.definition.collisionRadius + enemy.definition.collisionRadius + 0.1f)
        val direction = if (random.nextBoolean()) 1f else -1f
        val lateral = direction * (0.7f + random.nextFloat() * 1.15f)
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
        val step = min(distance, actor.definition.movementSpeed * (1f - slow.coerceIn(0f, 1f)) * STEP / 1000f)
        actor.position = clamp(
            BattlePosition(actor.position.x + dx / distance * step, actor.position.z + dz / distance * step),
            actor.definition.collisionRadius
        )
        transition(actor, CombatantState.POSITIONING)
    }

    private fun planAutonomousAction(actor: Fighter) {
        actor.roamDestination = null
        val enemy = chooseEnemy(actor) ?: run {
            actor.plannedTechniqueId = null
            actor.techniqueScores = emptyMap()
            actor.lastDecision = "Sem alvo válido para continuar."
            setTarget(actor, null)
            transition(actor, CombatantState.IDLE)
            return
        }
        val ally = chooseAlly(actor)
        if (actor.strategy == BattleStrategy.DEFENSIVE && elapsedMillis >= actor.nextAutoGuardAt &&
            enemy.active?.phase == Phase.STARTUP && enemy.active?.targetId == actor.definition.combatantId &&
            enemy.active?.technique?.let { inRange(enemy, actor, it) } == true) {
            actor.nextAutoGuardAt = elapsedMillis + 3_500L
            actor.defendUntil = elapsedMillis + 1_000L
            actor.plannedTechniqueId = null
            actor.lastDecision = "Antecipou o ataque e assumiu defesa."
            setTarget(actor, enemy.definition.combatantId)
            transition(actor, CombatantState.DEFENDING)
            return
        }
        val equippedCandidates = actor.definition.techniqueIds.distinct().map(techniques::getValue)
            .filter { ready(actor, it) && it.energyCost <= availableEnergy(actor) && it.commandPointCost == 0 && it.kind != TechniqueKind.SPECIAL }
            .filter { !isSupport(it) || (it.healPower > 0 && ally.health < ally.definition.maxHealth) ||
                it.statusEffects.isNotEmpty() }
        // Re:Digitize techniques consume MP. The free fallback only prevents a
        // permanent stalemate when no equipped technique can currently run.
        val candidates = equippedCandidates.ifEmpty {
            listOf(BASIC).filter { ready(actor, it) && availableEnergy(actor) >= it.energyCost }
        }
        val scores = candidates.associate { technique ->
            val recipient = if (isSupport(technique)) ally else enemy
            val distance = actor.position.distanceTo(recipient.position)
            var score = technique.power * 0.72f - technique.energyCost * 0.32f
            score -= (technique.startupMillis + technique.recoveryMillis) * 0.015f
            if (distance in technique.minRange..technique.maxRange) score += 28f
            else score -= abs(distance - idealRange(actor, recipient, technique)) * 6f
            if (technique.techniqueId == actor.lastTechniqueId) score -= 62f
            // Personalities in Decode create tendencies rather than a fixed
            // rotation. Seeded variation keeps the same session reproducible.
            score += random.nextDouble(0.0, 72.0).toFloat()
            if (isSupport(technique)) {
                val missingFraction = 1f - recipient.health.toFloat() / recipient.definition.maxHealth
                score += min(technique.healPower, recipient.definition.maxHealth - recipient.health) * missingFraction
                if (actor.strategy == BattleStrategy.SUPPORT) score += 80f
            }
            when (actor.strategy) {
                BattleStrategy.AGGRESSIVE -> score += technique.power * 0.35f
                BattleStrategy.CONSERVATIVE -> score -= technique.energyCost * if (actor.energy.toLong() * 4 < actor.definition.maxEnergy) 1.4f else 0.35f
                BattleStrategy.DEFENSIVE -> score += technique.maxRange * 3f - technique.startupMillis * 0.03f
                BattleStrategy.RANGED -> score += if (technique.maxRange >= 4f) 30f else -15f
                else -> Unit
            }
            technique.techniqueId to score
        }
        actor.techniqueScores = scores
        val chosenId = scores.maxByOrNull { it.value }?.key
        val chosen = candidates.firstOrNull { it.techniqueId == chosenId }
        actor.plannedTechniqueId = chosen?.techniqueId
        setTarget(actor, if (chosen != null && isSupport(chosen)) ally.definition.combatantId else enemy.definition.combatantId)
        if (chosen == null) {
            actor.lastDecision = "Nenhuma técnica pronta com recursos disponíveis."
            transition(actor, CombatantState.WAITING)
            scheduleAutonomousRead(actor)
        } else {
            actor.lastDecision = "Escolheu ${chosen.displayName} (${scores[chosen.techniqueId]?.toInt() ?: 0} pontos)."
        }
    }

    private fun approachAndExecute(actor: Fighter, technique: TechniqueDefinition) {
        val recipient = target(actor, actor.targetId, isSupport(technique))
        if (recipient == null || !ready(actor, technique)) {
            finishOrder(actor, OrderStatus.FAILED, "Alvo ou técnica indisponível.")
            actor.plannedTechniqueId = null
            return
        }
        val explicit = actor.currentOrder != null
        val enoughEnergy = if (explicit) actor.energy >= technique.energyCost else availableEnergy(actor) >= technique.energyCost
        if (!enoughEnergy || explicit && commandPoints < technique.commandPointCost) {
            finishOrder(actor, OrderStatus.FAILED, "Os recursos não estão disponíveis.")
            actor.plannedTechniqueId = null
            return
        }
        if (!inRange(actor, recipient, technique)) {
            actor.lastDecision = "Reposicionando para ${technique.displayName} dentro do alcance."
            move(actor, recipient, idealRange(actor, recipient, technique), STEP)
            return
        }
        actor.energy -= technique.energyCost
        if (explicit) {
            commandPoints -= technique.commandPointCost
            reservations.remove(actor.currentOrder!!.orderId)
        }
        actor.active = ActiveTechnique(technique, recipient.definition.combatantId)
        actor.plannedTechniqueId = null
        if (explicit) actor.lastDecision = "Executando ${technique.displayName} por ordem do treinador."
        transition(actor, if (technique.kind == TechniqueKind.SPECIAL) CombatantState.USING_SPECIAL else CombatantState.ATTACK_STARTUP)
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
                    actor.cooldowns[active.technique.techniqueId] = elapsedMillis - active.phaseElapsed + active.technique.cooldownMillis
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
        val primary = target(actor, hitTargetId ?: active.targetId, isSupport(technique))
        if (primary == null || !projectileImpact && !inRange(actor, primary, technique)) {
            emit(BattleEvent.TechniqueMissed(actor.definition.combatantId, technique.techniqueId, "Alvo indisponível ou fora do alcance."))
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
        val victims = if (technique.kind == TechniqueKind.AREA) combatants.values.filter {
            it.health > 0 && it.definition.side != actor.definition.side && it.position.distanceTo(primary.position) <= technique.areaRadius
        } else listOf(primary)
        victims.forEach { victim ->
            val defense = if (victim.defendUntil > elapsedMillis && victim.statuses.none { it.effect.preventsActions }) 0.3 else 1.0
            val attackMultiplier = actor.statuses.fold(1f) { value, status -> value * status.effect.attackMultiplier }
            val defenseMultiplier = victim.statuses.fold(1f) { value, status -> value * status.effect.defenseMultiplier }
            val attributeMultiplier = actor.definition.attribute.damageMultiplierAgainst(victim.definition.attribute)
            val raw = technique.power.toDouble() * actor.definition.attack * attackMultiplier * attributeMultiplier /
                100.0 * random.nextDouble(0.92, 1.08)
            val critical = random.nextDouble() < technique.criticalChance.coerceIn(0f, 1f)
            val criticalRaw = if (critical) raw * technique.criticalMultiplier else raw
            val amount = if (technique.power == 0) 0 else
                (criticalRaw * 100.0 / (100.0 + victim.definition.defense * defenseMultiplier) * defense).toInt().coerceAtLeast(1)
            val damage = min(amount, victim.health)
            victim.health -= damage
            val sourceBaseDamage = if (damage > 0 && defenseMultiplier > 0f) {
                (criticalRaw * 100.0 / (100.0 + victim.definition.defense * defenseMultiplier)).toInt()
            } else amount
            statistics = statistics.copy(
                damageDealt = statistics.damageDealt + if (actor.definition.side == BattleSide.ALLIED) damage else 0,
                damageReceived = statistics.damageReceived + if (victim.definition.side == BattleSide.ALLIED) damage else 0,
                damagePrevented = statistics.damagePrevented + if (victim.definition.side == BattleSide.ALLIED)
                    (sourceBaseDamage - amount).coerceAtLeast(0) else 0
            )
            if (damage > 0) {
                val id = nextImpactId++
                impacts[id] = ActiveImpact(id, victim.definition.combatantId, damage, critical,
                    elapsedMillis + IMPACT_DURATION_MILLIS)
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
                applyStatuses(victim, technique.statusEffects.map { it.copy(sourceCombatantId = actor.definition.combatantId) })
                if (technique.staggerPower > 0 && technique.staggerPower > victim.definition.defense * 0.25f) {
                    applyStatuses(victim, listOf(BattleStatusEffect("stun", 500L, preventsActions = true)))
                }
                if (technique.knockbackDistance > 0f) applyKnockback(victim, actor, technique.knockbackDistance)
            }
        }
    }

    private fun launchProjectile(actor: Fighter, active: ActiveTechnique) {
        val recipient = target(actor, active.targetId, false)
        if (recipient == null) {
            emit(BattleEvent.TechniqueMissed(actor.definition.combatantId, active.technique.techniqueId, "Alvo indisponível no lançamento."))
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
            BattleItemKind.CLEANSE_STATUS -> recipient.statuses.size.also { recipient.statuses.clear() }
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

    private fun move(actor: Fighter, recipient: Fighter, desiredDistance: Float, deltaMillis: Long) {
        val dx = recipient.position.x - actor.position.x
        val dz = recipient.position.z - actor.position.z
        val distance = hypot(dx, dz)
        val difference = distance - desiredDistance
        if (abs(difference) <= POSITION_EPSILON) {
            transition(actor, CombatantState.POSITIONING)
            return
        }
        val slow = actor.statuses.filter { it.effect.slowsMovement }.maxOfOrNull { it.effect.magnitude } ?: 0f
        val step = min(abs(difference), actor.definition.movementSpeed * (1f - slow.coerceIn(0f, 1f)) * deltaMillis / 1000f)
        val sign = if (difference > 0) 1f else -1f
        transition(actor, if (sign > 0) CombatantState.MOVE_TO_TARGET else CombatantState.MOVE_AWAY)
        val nx = if (distance > 0.0001f) dx / distance else 1f
        val nz = if (distance > 0.0001f) dz / distance else 0f
        actor.position = clamp(BattlePosition(actor.position.x + nx * step * sign, actor.position.z + nz * step * sign), actor.definition.collisionRadius)
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
        val enemies = combatants.values.filter { it.health > 0 && it.definition.side != actor.definition.side }
        if (actor.focusUntil > elapsedMillis) enemies.firstOrNull { it.definition.combatantId == actor.focusId }?.let { return it }
        val best = enemies.minWithOrNull(compareByDescending<Fighter> { actor.threat[it.definition.combatantId] ?: 0L }
            .thenBy { actor.position.distanceTo(it.position) }.thenBy { it.definition.combatantId }) ?: return null
        val current = target(actor, actor.targetId, false) ?: return best
        val currentThreat = actor.threat[current.definition.combatantId] ?: 0L
        val bestThreat = actor.threat[best.definition.combatantId] ?: 0L
        return if (bestThreat > currentThreat * 1.2 + 5) best else current
    }

    private fun chooseAlly(actor: Fighter): Fighter = combatants.values
        .filter { it.health > 0 && it.definition.side == actor.definition.side }
        .minWith(compareBy<Fighter> { it.health.toDouble() / it.definition.maxHealth }.thenBy { it.definition.combatantId })

    private fun target(actor: Fighter, id: String?, ally: Boolean): Fighter? = combatants[id]
        ?.takeIf { it.health > 0 && (it.definition.side == actor.definition.side) == ally }

    private fun setTarget(actor: Fighter, id: String?) {
        if (actor.targetId == id) return
        actor.targetId = id
        emit(BattleEvent.TargetChanged(actor.definition.combatantId, id))
    }

    private fun inRange(actor: Fighter, target: Fighter, technique: TechniqueDefinition): Boolean =
        actor.position.distanceTo(target.position) in technique.minRange..technique.maxRange

    private fun idealRange(actor: Fighter, target: Fighter, technique: TechniqueDefinition): Float {
        val separation = if (actor === target) 0f else actor.definition.collisionRadius + target.definition.collisionRadius
        return max(separation, (technique.minRange + technique.maxRange) / 2f).coerceAtMost(technique.maxRange)
    }

    private fun ready(actor: Fighter, technique: TechniqueDefinition) = (actor.cooldowns[technique.techniqueId] ?: 0L) <= elapsedMillis
    private fun isSupport(technique: TechniqueDefinition) = technique.kind == TechniqueKind.HEAL || technique.kind == TechniqueKind.SUPPORT
    private fun availableEnergy(actor: Fighter) = actor.energy - reservations.values.filter { it.actorId == actor.definition.combatantId }.sumOf { it.energy }
    private fun availableCommandPoints() = commandPoints - reservations.values.sumOf { it.commandPoints }

    private fun tickStatuses(actor: Fighter) {
        val iterator = actor.statuses.iterator()
        while (iterator.hasNext()) {
            val status = iterator.next()
            val activeMillis = min(STEP, status.remaining)
            status.remaining -= activeMillis
            status.damageRemainder += status.effect.damagePerSecond.toDouble() * activeMillis / 1000.0
            val damage = status.damageRemainder.toInt()
            status.damageRemainder -= damage
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
            if (status.remaining <= 0) iterator.remove()
        }
        if (actor.health == 0) defeat(actor)
    }

    private fun applyStatuses(actor: Fighter, effects: List<BattleStatusEffect>) {
        if (actor.health <= 0) return
        effects.filter { it.durationMillis > 0 }.forEach { effect ->
            val existing = actor.statuses.firstOrNull { it.effect.id == effect.id }
            if (existing == null) actor.statuses += ActiveStatus(effect, effect.durationMillis)
            else {
                existing.effect = effect
                existing.remaining = max(existing.remaining, effect.durationMillis)
            }
            emit(BattleEvent.StatusApplied(actor.definition.combatantId, effect.id))
            if (effect.preventsActions) {
                if (actor.active?.phase == Phase.STARTUP && canInterrupt(actor)) cancelAction(actor, "Ação interrompida por um efeito.")
                transition(actor, CombatantState.STUNNED)
            }
        }
    }

    private fun addThreat(actor: Fighter, source: Fighter, amount: Int) {
        if (amount > 0) actor.threat[source.definition.combatantId] = (actor.threat[source.definition.combatantId] ?: 0L) + amount
    }

    private fun canInterrupt(actor: Fighter): Boolean = actor.active?.let {
        it.phase == Phase.STARTUP && it.technique.interruptibleDuringStartup
    } ?: true

    private fun cancelAction(actor: Fighter, reason: String) {
        actor.active?.let { actor.cooldowns[it.technique.techniqueId] = elapsedMillis + it.technique.cooldownMillis }
        actor.active = null
        actor.defendUntil = 0L
        actor.plannedTechniqueId = null
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
        }
    }

    private class Fighter(val definition: CombatantDefinition, var position: BattlePosition) {
        var health = definition.maxHealth
        var energy = definition.maxEnergy
        var energyRemainder = 0.0
        var state = CombatantState.IDLE
        var strategy = definition.strategy
        var lastDecision = "Aguardando decisão autônoma."
        var techniqueScores: Map<String, Float> = emptyMap()
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
    private class ActiveTechnique(val technique: TechniqueDefinition, val targetId: String,
        var phase: Phase = Phase.STARTUP, var phaseElapsed: Long = 0L)
    private class ActiveStatus(var effect: BattleStatusEffect, var remaining: Long, var damageRemainder: Double = 0.0)
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
        val expiresAtMillis: Long
    )
    private data class Reservation(val actorId: String, val energy: Int, val commandPoints: Int)

    private companion object {
        const val STEP = 34L
        const val POSITION_EPSILON = 0.05f
        const val IMPACT_DURATION_MILLIS = 320L
        const val MAX_ACTIVE_IMPACTS = 8
        val BASIC = TechniqueDefinition("basic_attack", "Ataque básico", TechniqueKind.BASIC, 48,
            maxRange = 1.8f, startupMillis = 360L, activeMillis = 100L, recoveryMillis = 720L,
            cooldownMillis = 1_800L, attackVisual = "small")
    }
}
