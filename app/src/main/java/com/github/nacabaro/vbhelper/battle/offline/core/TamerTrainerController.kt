package com.github.nacabaro.vbhelper.battle.offline.core

import kotlin.random.Random

data class TrainerAiPolicy(
    val strategy: BattleStrategy = BattleStrategy.BALANCED,
    val reactionMillis: Long = 900L,
    val healThreshold: Float = 0.38f,
    val energyThreshold: Float = 0.22f,
    val finisherHitChance: Float = 0.80f,
    val focusVulnerable: Boolean = true,
)

data class TrainerDecision(val actorId: String, val action: TrainerAction, val interrupt: Boolean = false)

/** A simulation-owned, seeded team trainer. It has no renderer, Room, network or wall clock. */
class TamerTrainerController(private val side: BattleSide, private val policy: TrainerAiPolicy, seed: Long) {
    private val random = Random(seed xor if (side == BattleSide.ALLIED) 0x513AF1L else 0x79CD23L)
    private var nextDecisionAt = 0L
    private val timingPlans = mutableMapOf<Pair<String, Long>, Pair<Long, Boolean>>()

    init {
        require(policy.reactionMillis in 34L..10_000L)
        require(policy.healThreshold in 0f..1f && policy.energyThreshold in 0f..1f && policy.finisherHitChance in 0f..1f)
    }

    fun decide(snapshot: BattleSnapshot, catalog: Map<String, TechniqueDefinition>): List<TrainerDecision> {
        if (snapshot.isPaused || snapshot.result != null || snapshot.finisher != null) return emptyList()
        val team = (if (side == BattleSide.ALLIED) snapshot.alliedMembers else snapshot.opposingMembers).filter { it.health > 0 }
        val enemies = (if (side == BattleSide.ALLIED) snapshot.opposingMembers else snapshot.alliedMembers).filter { it.health > 0 }
        if (team.isEmpty() || enemies.isEmpty()) return emptyList()
        val now = snapshot.elapsedMillis
        timingPlans.keys.removeAll { (id, deadline) -> snapshot.pendingBlastTiming[id] != deadline }
        for (member in team.sortedBy { it.combatantId }) {
            val deadline = snapshot.pendingBlastTiming[member.combatantId] ?: continue
            val plan = timingPlans.getOrPut(member.combatantId to deadline) {
                val remaining = (deadline - now).coerceAtLeast(1L)
                (now + (remaining * (0.30 + random.nextDouble() * 0.25)).toLong()) to
                    (random.nextFloat() < policy.finisherHitChance)
            }
            if (plan.second && now >= plan.first && now < deadline) {
                return listOf(TrainerDecision(member.combatantId, TrainerAction.ConfirmBlastTiming()))
            }
        }
        if (now < nextDecisionAt) return emptyList()
        nextDecisionAt = now + policy.reactionMillis
        val available = team.filter { it.currentOrderId == null && it.queuedOrderIds.isEmpty() &&
            it.activeTechniqueId == null && it.state !in setOf(CombatantState.STUNNED, CombatantState.KNOCKBACK, CombatantState.DEFENDING) }
        val actor = available.firstOrNull() ?: return emptyList()
        val inventory = if (side == BattleSide.ALLIED) snapshot.trainingItems else snapshot.opposingItems
        val cooldown = if (side == BattleSide.ALLIED) snapshot.alliedItemCooldownMillis else snapshot.opposingItemCooldownMillis
        if (cooldown == 0L) {
            val endangered = team.minBy { it.health.toFloat() / it.maxHealth }
            val afflicted = team.firstOrNull { it.statuses.any { status ->
                status.preventsActions || status.mechanic in setOf(BattleStatusMechanic.POISON, BattleStatusMechanic.BURN,
                    BattleStatusMechanic.FREEZE, BattleStatusMechanic.SHOCK)
            } }
            val exhausted = team.minBy { if (it.maxEnergy == 0) 1f else it.energy.toFloat() / it.maxEnergy }
            val wanted = buildList {
                if (endangered.health.toFloat() / endangered.maxHealth < 0.20f) add("recovery" to endangered)
                if (afflicted != null) add("cleanse" to afflicted)
                if (endangered.health.toFloat() / endangered.maxHealth <= policy.healThreshold) add("recovery" to endangered)
                if (exhausted.maxEnergy > 0 && exhausted.energy.toFloat() / exhausted.maxEnergy < policy.energyThreshold) add("energy" to exhausted)
            }
            wanted.forEach { (kind, target) ->
                inventory.firstOrNull { it.itemId.endsWith(kind) && it.remaining > it.reserved }?.let {
                    return listOf(TrainerDecision(actor.combatantId, TrainerAction.UseItem(it.itemId, target.combatantId)))
                }
            }
        }
        val decisions = mutableListOf<TrainerDecision>()
        for (member in available) {
            val energy = if (member.maxEnergy == 0) 0f else member.energy.toFloat() / member.maxEnergy
            val strategy = when {
                member.health.toFloat() / member.maxHealth < 0.25f -> BattleStrategy.DEFENSIVE
                energy < 0.18f -> BattleStrategy.CONSERVATIVE
                else -> policy.strategy
            }
            if (member.strategy != strategy) decisions += TrainerDecision(member.combatantId, TrainerAction.ChangeStrategy(strategy))
            val enemy = if (policy.focusVulnerable) enemies.minBy { it.health.toFloat() / it.maxHealth }
                else enemies.minBy { member.position.distanceTo(it.position) }
            if (member.targetId != enemy.combatantId) decisions += TrainerDecision(member.combatantId, TrainerAction.FocusTarget(enemy.combatantId))
            val special = member.specialTechniqueId?.let(catalog::get)
            if (special != null && member.specialCharge >= member.maxSpecialCharge &&
                member.energy - member.reservedEnergy >= special.energyCost && special.techniqueId !in member.cooldownsMillis) {
                val target = if (special.healPower > 0) team.minBy { it.health.toFloat() / it.maxHealth } else enemy
                if (special.healPower == 0 || target.health < target.maxHealth) {
                    decisions += TrainerDecision(member.combatantId, TrainerAction.UseTechnique(special.techniqueId, target.combatantId))
                    break
                }
            }
        }
        team.firstOrNull { it.combatantId in snapshot.pendingSupportCombatantIds }?.let {
            decisions += TrainerDecision(it.combatantId, TrainerAction.Support)
        }
        return decisions
    }
}
