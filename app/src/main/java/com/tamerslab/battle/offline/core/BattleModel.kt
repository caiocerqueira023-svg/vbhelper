package com.tamerslab.battle.offline.core

import kotlin.math.hypot
import com.tamerslab.battle.domain.BattleAttribute
import com.tamerslab.battle.domain.BattleRules
import com.tamerslab.battle.domain.BattleSide
import com.tamerslab.battle.domain.BattleStatusEffect
import com.tamerslab.battle.domain.BattleStrategy
import com.tamerslab.battle.domain.TechniqueKind
import com.tamerslab.domain.personality.DigimonPersonalityType

/** Coordinates on the arena's horizontal (X/Z) plane, in game-space units. */
data class BattlePosition(val x: Float, val z: Float) {
    fun distanceTo(other: BattlePosition): Float = hypot(x - other.x, z - other.z)
}

enum class CombatantState {
    IDLE,
    SELECT_TARGET,
    MOVE_TO_TARGET,
    MOVE_AWAY,
    POSITIONING,
    ATTACK_STARTUP,
    ATTACK_ACTIVE,
    RECOVERING,
    DEFENDING,
    STUNNED,
    KNOCKBACK,
    USING_SPECIAL,
    USING_ITEM_EFFECT,
    WAITING,
    DEFEATED
}

/** The DiM/BEM attack-art variant is selected by attack class, not technique rank. */
internal fun attackSpriteVariantFor(kind: TechniqueKind): String? = when (kind) {
    TechniqueKind.SPECIAL -> "large"
    TechniqueKind.BASIC, TechniqueKind.MELEE, TechniqueKind.PROJECTILE, TechniqueKind.AREA -> "small"
    TechniqueKind.HEAL, TechniqueKind.SUPPORT -> null
}

enum class BattleItemKind { HEAL_HEALTH, RESTORE_ENERGY, CLEANSE_STATUS }

/** Session-only inventory. It never refers to an ItemDao row. */
data class BattleItemDefinition(
    val itemId: String,
    val displayName: String,
    val kind: BattleItemKind,
    val quantity: Int,
    val amount: Int = 0
)

data class BattleItemSnapshot(
    val itemId: String,
    val displayName: String,
    val remaining: Int,
    val reserved: Int
)

data class ProjectileSnapshot(
    val projectileId: Long,
    val ownerId: String,
    val targetId: String,
    val techniqueId: String,
    val visual: String?,
    val position: BattlePosition,
    val velocityX: Float,
    val velocityZ: Float
)

internal const val BATTLE_IMPACT_LIFETIME_MILLIS = 700L

data class BattleImpactSnapshot(
    val impactId: Long,
    val targetId: String,
    val damage: Int,
    val critical: Boolean,
    val remainingMillis: Long,
    val techniqueId: String? = null,
    val isSpecial: Boolean = false
)

data class BattleStatistics(
    val damageDealt: Int = 0,
    val damageReceived: Int = 0,
    val damagePrevented: Int = 0,
    val healingDone: Int = 0,
    val ordersCompleted: Int = 0,
    val supportCommands: Int = 0,
    val itemsUsed: Int = 0,
    val projectilesHit: Int = 0,
    val projectilesMissed: Int = 0,
    val specialsUsed: Int = 0,
    val specialsHit: Int = 0,
    val specialsMissed: Int = 0,
    /** Specials resolved with a confirmed Blast HIT grade. */
    val blastTimedHits: Int = 0
)

data class BattleConfiguration(
    val alliedTeamId: String = "allies",
    val opposingTeamId: String = "opponents",
    val arenaRadius: Float = 8f,
    val lineupRowSpacing: Float = 1.65f,
    val lineupDepthRatio: Float = 0.38f,
    val minimumLineupDepth: Float = 1.2f,
    val commandPoints: Int = 0,
    val maxCommandPoints: Int = 100,
    val decisionIntervalMillis: Long = 200L,
    val defaultPaused: Boolean = false,
    val randomSeed: Long = 1L,
    val maxDurationMillis: Long = 300_000L,
    val rulesetVersion: Int = BattleRules.CURRENT_VERSION,
    val damageFormula: BattleDamageFormula = BattleDamageFormula.ADAPTED,
    /** Independent replay version; checkpoint recovery explicitly pins absent fields to legacy. */
    val movementRulesVersion: Int = BattleMovementRules.CURRENT_VERSION,
    /** Opt-in arena rules; legacy training/radar checkpoints retain their original behavior. */
    val itemCooldownMillis: Long = 0L,
    val strictFinisherEligibility: Boolean = false,
)

data class BattleOrder(
    val orderId: Long,
    val actorId: String,
    val action: TrainerAction,
    val issuedAtMillis: Long,
    val expiresAtMillis: Long? = null,
    val interruptCurrentAction: Boolean = false
)

sealed interface TrainerAction {
    data class FocusTarget(val targetId: String) : TrainerAction
    data class UseTechnique(val techniqueId: String, val targetId: String? = null) : TrainerAction
    data class UseItem(val itemId: String, val targetId: String) : TrainerAction
    data class Defend(val durationMillis: Long = 1_500L) : TrainerAction
    data class ChangeStrategy(val strategy: BattleStrategy) : TrainerAction
    data object MoveCloser : TrainerAction
    data object KeepDistance : TrainerAction
    data object Support : TrainerAction
    /** Confirms the Blast timing while the actor's special is in STARTUP; never queued. */
    data class ConfirmBlastTiming(
        val fusionResult: String? = null,
        val fusionSpecial: String? = null
    ) : TrainerAction
}

enum class BlastGrade { MISS, HIT }

enum class OrderStatus { QUEUED, EXECUTING, COMPLETED, FAILED, CANCELLED, EXPIRED }

data class OrderUpdate(
    val orderId: Long,
    val status: OrderStatus,
    val reason: String? = null,
    /** Stable machine-readable failure key; UI maps it to a localized line. */
    val reasonCode: String? = null
)

/** Stable order-failure keys published in [OrderUpdate.reasonCode]. */
object OrderFailure {
    const val BATTLE_ENDED = "battle_ended"
    const val PARTNER_MISSING = "partner_missing"
    const val NOT_ALLIED = "not_allied"
    const val PARTNER_DEFEATED = "partner_defeated"
    const val BAD_LIFETIME = "bad_lifetime"
    const val SUPPORT_PAUSED = "support_paused"
    const val SUPPORT_NO_WINDOW = "support_no_window"
    const val SUPPORT_WINDOW_EXPIRED = "support_window_expired"
    const val BLAST_PAUSED = "blast_paused"
    const val BLAST_NO_WINDOW = "blast_no_window"
    const val BLAST_WINDOW_CLOSED = "blast_window_closed"
    const val FOCUS_NO_TARGET = "focus_no_target"
    const val ORDER_QUEUE_FULL = "order_queue_full"
    const val BAD_DEFEND_DURATION = "bad_defend_duration"
    const val TECHNIQUE_MISSING = "technique_missing"
    const val COUNTER_REACTION_ONLY = "counter_reaction_only"
    const val TECHNIQUE_NOT_OWNED = "technique_not_owned"
    const val TECHNIQUE_COOLDOWN = "technique_cooldown"
    const val TARGET_MISSING = "target_missing"
    const val SPECIAL_CHARGING = "special_charging"
    const val RESOURCES_MISSING = "resources_missing"
    const val ITEM_MISSING = "item_missing"
    const val ITEM_BAD_TARGET = "item_bad_target"
    const val ITEM_DEPLETED = "item_depleted"
    const val DUO_UNAVAILABLE = "duo_unavailable"
    const val MOVE_NO_TARGET = "move_no_target"
    const val TECHNIQUE_STALE = "technique_stale"
    const val ORDER_INVALID = "order_invalid"
    const val ORDER_EXPIRED = "order_expired"
    const val ORDER_TIMED_OUT = "order_timed_out"
    const val POSITIONING_FAILED = "positioning_failed"
    const val ITEM_UNUSABLE = "item_unusable"
    const val ITEM_UNNEEDED = "item_unneeded"
    const val ITEM_COOLDOWN = "item_cooldown"
    const val ORDER_SUPERSEDED = "order_superseded"
    const val STATUS_INTERRUPTED = "status_interrupted"
}

data class CombatantSnapshot(
    val combatantId: String,
    val sourceCharacterId: Long?,
    val externalCharacterId: String?,
    val displayName: String,
    val side: BattleSide,
    val health: Int,
    val maxHealth: Int,
    val energy: Int,
    val maxEnergy: Int,
    val position: BattlePosition,
    val targetId: String?,
    val state: CombatantState,
    val strategy: BattleStrategy,
    val activeTechniqueId: String?,
    val statuses: List<BattleStatusEffect>,
    val cooldownsMillis: Map<String, Long>,
    val currentOrderId: Long? = null,
    val queuedOrderIds: List<Long> = emptyList(),
    val reservedEnergy: Int = 0,
    val debug: CombatantDebugSnapshot = CombatantDebugSnapshot(),
    val techniqueIds: List<String> = emptyList(),
    val specialTechniqueId: String? = null,
    val specialCharge: Int = 0,
    val maxSpecialCharge: Int = 100,
    val reservedSpecialCharge: Int = 0,
    val activeTechniqueKind: TechniqueKind? = null,
    val specialDisplayNameOverride: String? = null,
    val blastMode: String = "NONE",
    val blastTargetSpecies: String? = null,
    val blastFormSpecial: String? = null,
    val jogressResultSpecies: String? = null,
    /** Species currently shown in place of the base form during a Blast transform. */
    val blastFormSpecies: String? = null,
    val attribute: BattleAttribute = BattleAttribute.NONE,
    val jogressPartnerSpecies: List<String> = emptyList(),
    val jogressPartnerAttribute: BattleAttribute? = null,
    /** Read-only visual progress for the current STARTUP; null outside wind-up. */
    val activeTechniqueStartupProgress: Float? = null,
)

/** Bounded diagnostics for developer tooling; never persisted with the training session. */
data class CombatantDebugSnapshot(
    val decision: String = "Aguardando decisão.",
    val targetDistance: Float? = null,
    val techniqueScores: Map<String, Float> = emptyMap(),
    val techniqueScoreComponents: Map<String, Map<String, Float>> = emptyMap(),
    val personalityType: DigimonPersonalityType? = null,
    val personalityCore: PersonalityCore? = null,
    val threatByCombatant: Map<String, Long> = emptyMap(),
    val lastOrderFailure: String? = null,
    val readiness: Float = 100f,
    val readinessRequired: Float = 0f,
    val buffsRemaining: Int = 0,
    val positioningReplans: Int = 0,
    val targetReason: String = "",
    val encounterProfileId: String = "balanced",
    val counterReady: Boolean = false,
    val techniqueUses: Map<String, Int> = emptyMap(),
    val guardCount: Int = 0,
    val counterCount: Int = 0,
    val targetChanges: Int = 0,
    val incapacitatedMillis: Long = 0L,
    val readinessWaitingMillis: Long = 0L,
    val meanTargetDistance: Float = 0f,
    val meanReadiness: Float = 100f
)

enum class BattleOutcome { ALLIED_VICTORY, OPPOSING_VICTORY, DRAW, ABANDONED }

data class BattleResult(
    val outcome: BattleOutcome,
    val elapsedMillis: Long,
    val eventCount: Long,
    val statistics: BattleStatistics = BattleStatistics()
)

/** Cumulative successful damaging hits; independent of the bounded presentation-event tail. */
data class BattleTechniqueHitCount(val combatantId: String, val targetId: String, val techniqueId: String, val hits: Int)

data class BattleSnapshot(
    val elapsedMillis: Long,
    val isPaused: Boolean,
    val pauseReason: String?,
    val commandPoints: Int,
    val maxCommandPoints: Int,
    val alliedMembers: List<CombatantSnapshot>,
    val opposingMembers: List<CombatantSnapshot>,
    val pendingSupportCombatantIds: Set<String>,
    val result: BattleResult?,
    val recentEvents: List<BattleEvent>,
    /** Total emitted events; lets observers deduplicate the bounded recentEvents tail. */
    val eventCount: Long = 0,
    val reservedCommandPoints: Int = 0,
    val projectiles: List<ProjectileSnapshot> = emptyList(),
    val trainingItems: List<BattleItemSnapshot> = emptyList(),
    val statistics: BattleStatistics = BattleStatistics(),
    val impacts: List<BattleImpactSnapshot> = emptyList(),
    val rulesetVersion: Int = BattleRules.CURRENT_VERSION,
    val techniqueHitCounts: List<BattleTechniqueHitCount> = emptyList(),
    /** Allied combatantId to Blast window deadline (elapsed millis); absence means MISS. */
    val pendingBlastTiming: Map<String, Long> = emptyMap(),
    val finisher: BattleFinisherSnapshot? = null,
    /** Presentation effects through this event were already shown by a completed cinematic. */
    val lastFinisherEventCount: Long = 0L,
    val opposingItems: List<BattleItemSnapshot> = emptyList(),
    val opposingCommandPoints: Int = 0,
    val alliedItemCooldownMillis: Long = 0L,
    val opposingItemCooldownMillis: Long = 0L,
)

sealed interface BattleEvent {
    data class StateChanged(val combatantId: String, val state: CombatantState) : BattleEvent
    data class TargetChanged(val combatantId: String, val targetId: String?) : BattleEvent
    data class TechniqueStarted(val combatantId: String, val techniqueId: String, val targetId: String?) : BattleEvent
    data class SpecialReady(val combatantId: String) : BattleEvent
    data class SpecialStarted(val combatantId: String, val techniqueId: String, val targetId: String?) : BattleEvent
    data class SpecialResolved(
        val combatantId: String,
        val techniqueId: String,
        val targetId: String?,
        val success: Boolean
    ) : BattleEvent
    data class BlastTimingOpened(val combatantId: String, val techniqueId: String, val expiresAtMillis: Long) : BattleEvent
    data class BlastTimingResolved(val combatantId: String, val techniqueId: String, val grade: BlastGrade) : BattleEvent
    data class BlastFormStarted(val combatantId: String, val targetSpecies: String, val specialName: String) : BattleEvent
    data class BlastFormEnded(val combatantId: String, val targetSpecies: String) : BattleEvent
    data class BlastJogressStarted(val leadId: String, val partnerId: String, val resultSpecies: String) : BattleEvent
    data class BlastJogressEnded(val leadId: String, val resultSpecies: String) : BattleEvent
    data class TechniqueHit(
        val combatantId: String,
        val targetId: String,
        val techniqueId: String,
        val amount: Int,
        val critical: Boolean = false
    ) : BattleEvent
    data class TechniqueMissed(val combatantId: String, val techniqueId: String, val reason: String) : BattleEvent
    data class StatusApplied(val combatantId: String, val statusId: String) : BattleEvent
    data class CombatantDefeated(val combatantId: String) : BattleEvent
    data class SupportWindowOpened(val combatantId: String, val expiresAtMillis: Long) : BattleEvent
    data class SupportSucceeded(val combatantId: String, val commandPointsGained: Int) : BattleEvent
    data class ItemUsed(val combatantId: String, val targetId: String, val itemId: String, val amount: Int) : BattleEvent
    data class ProjectileLaunched(val projectileId: Long, val ownerId: String, val techniqueId: String) : BattleEvent
    data class ProjectileMissed(val projectileId: Long, val techniqueId: String) : BattleEvent
    data class CounterTriggered(val combatantId: String, val attackerId: String) : BattleEvent
    data class PositioningReplanned(val combatantId: String, val techniqueId: String) : BattleEvent
    data class OrderChanged(val update: OrderUpdate) : BattleEvent
    data class BattleEnded(val result: BattleResult) : BattleEvent
}

/** Initial lineups. Seed positions form an opening tableau; later movement is AI-owned. */
fun defaultLineupPositions(
    teamSize: Int,
    side: BattleSide,
    arenaRadius: Float,
    rowSpacing: Float = 1.65f,
    depthRatio: Float = 0.38f,
    minimumDepth: Float = 1.2f
): List<BattlePosition> {
    if (teamSize <= 0) return emptyList()
    val front = (arenaRadius * depthRatio).coerceAtLeast(minimumDepth)
    val x = if (side == BattleSide.ALLIED) -front else front
    return (0 until teamSize).map { index ->
        val centeredIndex = index - (teamSize - 1) / 2f
        BattlePosition(
            x = x,
            z = (centeredIndex * rowSpacing).coerceIn(-arenaRadius * 0.55f, arenaRadius * 0.55f)
        )
    }
}
