package com.github.nacabaro.vbhelper.battle.offline.core

import kotlin.math.hypot
import com.github.nacabaro.vbhelper.domain.personality.DigimonPersonalityType

/** Coordinates on the arena's horizontal (X/Z) plane, in game-space units. */
data class BattlePosition(val x: Float, val z: Float) {
    fun distanceTo(other: BattlePosition): Float = hypot(x - other.x, z - other.z)
}

enum class BattleSide { ALLIED, OPPOSING }

enum class BattleAttribute {
    NONE,
    VIRUS,
    DATA,
    VACCINE,
    FREE;

    fun hasAdvantageOver(other: BattleAttribute): Boolean = when (this) {
        DATA -> other == VACCINE
        VACCINE -> other == VIRUS
        VIRUS -> other == DATA
        NONE, FREE -> false
    }

    fun damageMultiplierAgainst(other: BattleAttribute): Float = when {
        this == NONE || this == FREE || other == NONE || other == FREE || this == other -> 1f
        hasAdvantageOver(other) -> 1.10f
        other.hasAdvantageOver(this) -> 0.90f
        else -> 1f
    }
}

enum class BattleStrategy {
    AGGRESSIVE,
    BALANCED,
    CONSERVATIVE,
    DEFENSIVE,
    RANGED,
    SUPPORT
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

enum class TechniqueKind { BASIC, MELEE, PROJECTILE, AREA, HEAL, SUPPORT, SPECIAL }

/** Tactical distance accepted before a technique starts; geometry is modelled separately. */
enum class TechniqueRangeProfile {
    CUSTOM,
    SELF,
    CLOSE,
    CLOSE_MEDIUM,
    MEDIUM_LONG,
    ALL_FIELD
}

/** Where a completed technique applies its hit, independently from its activation distance. */
enum class TechniqueImpactShape {
    SINGLE_TARGET,
    AROUND_USER,
    AROUND_TARGET,
    ALL_OPPONENTS
}

data class BattleStatusEffect(
    val id: String,
    val durationMillis: Long,
    val magnitude: Float = 0f,
    val damagePerSecond: Float = 0f,
    val preventsActions: Boolean = false,
    val slowsMovement: Boolean = false,
    val attackMultiplier: Float = 1f,
    val defenseMultiplier: Float = 1f,
    val movementMultiplier: Float = 1f,
    val cooldownMultiplier: Float = 1f,
    val sourceCombatantId: String? = null
)

/** Immutable combat profile. Its IDs refer to the combat instance, not its source storage row. */
data class CombatantDefinition(
    val combatantId: String,
    val sourceCharacterId: Long? = null,
    val externalCharacterId: String? = null,
    val displayName: String,
    val side: BattleSide,
    val attribute: BattleAttribute = BattleAttribute.NONE,
    val maxHealth: Int,
    val maxEnergy: Int,
    val attack: Int,
    val defense: Int,
    val movementSpeed: Float,
    val energyRegenerationPerSecond: Float = 2f,
    /** Multiplies technique cooldowns; lower values produce a faster attack rhythm. */
    val techniqueCooldownMultiplier: Float = 1f,
    val collisionRadius: Float = 0.45f,
    val preferredDistance: Float = 1.5f,
    /** Autonomous fighters observe and reposition for this long between actions. */
    val decisionDelayMinMillis: Long = 900L,
    val decisionDelayMaxMillis: Long = 1_800L,
    val strategy: BattleStrategy = BattleStrategy.BALANCED,
    val stableRngKey: String = combatantId,
    val personalityType: DigimonPersonalityType = DigimonPersonalityType.FRIENDLY,
    val techniqueIds: List<String>,
    val specialTechniqueId: String? = null
)

data class TechniqueDefinition(
    val techniqueId: String,
    val displayName: String,
    val kind: TechniqueKind,
    val power: Int,
    val energyCost: Int = 0,
    val commandPointCost: Int = 0,
    val minRange: Float = 0f,
    val maxRange: Float = 1.5f,
    val rangeProfile: TechniqueRangeProfile = TechniqueRangeProfile.CUSTOM,
    val impactShape: TechniqueImpactShape = TechniqueImpactShape.SINGLE_TARGET,
    val areaRadius: Float = 0f,
    val startupMillis: Long = 300L,
    val activeMillis: Long = 100L,
    val recoveryMillis: Long = 500L,
    val cooldownMillis: Long = 1_000L,
    val interruptibleDuringStartup: Boolean = true,
    val staggerPower: Float = 0f,
    val healPower: Int = 0,
    val statusEffects: List<BattleStatusEffect> = emptyList(),
    /** "small", "large", or null; resolved by the presentation layer from existing assets. */
    val attackVisual: String? = null,
    val projectileSpeed: Float = 16f,
    val projectileRadius: Float = 0.2f,
    val projectileLifetimeMillis: Long = 1_800L,
    val hitCount: Int = 1,
    val knockbackDistance: Float = 0f,
    val criticalChance: Float = 0.05f,
    val criticalMultiplier: Float = 1.5f,
    val element: String? = null
)

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
    val specialsMissed: Int = 0
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
    val maxDurationMillis: Long = 300_000L
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
}

enum class OrderStatus { QUEUED, EXECUTING, COMPLETED, FAILED, CANCELLED, EXPIRED }

data class OrderUpdate(
    val orderId: Long,
    val status: OrderStatus,
    val reason: String? = null
)

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
    val reservedSpecialCharge: Int = 0
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
    val lastOrderFailure: String? = null
)

enum class BattleOutcome { ALLIED_VICTORY, OPPOSING_VICTORY, DRAW, ABANDONED }

data class BattleResult(
    val outcome: BattleOutcome,
    val elapsedMillis: Long,
    val eventCount: Long,
    val statistics: BattleStatistics = BattleStatistics()
)

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
    val impacts: List<BattleImpactSnapshot> = emptyList()
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
    data class OrderChanged(val update: OrderUpdate) : BattleEvent
    data class BattleEnded(val result: BattleResult) : BattleEvent
}

data class BattleTeam(
    val teamId: String,
    val side: BattleSide,
    val members: List<CombatantDefinition>
)

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
