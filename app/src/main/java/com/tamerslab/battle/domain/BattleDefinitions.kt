package com.tamerslab.battle.domain

import com.tamerslab.domain.personality.DigimonPersonalityType

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

enum class StatusRefreshPolicy { EXTEND, REPLACE, IGNORE }
enum class BattleStatusMechanic { MODIFIER, POISON, BURN, FREEZE, SHOCK }

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
    val sourceCombatantId: String? = null,
    val procChance: Float = 1f,
    val tickIntervalMillis: Long = 0L,
    val maxHealthPercentPerTickMin: Int = 0,
    val maxHealthPercentPerTickMax: Int = maxHealthPercentPerTickMin,
    val refreshPolicy: StatusRefreshPolicy = StatusRefreshPolicy.EXTEND,
    val exclusivityGroup: String? = null,
    val mechanic: BattleStatusMechanic = BattleStatusMechanic.MODIFIER,
    val protectsFromStatuses: Boolean = false
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
    val specialTechniqueId: String? = null,
    val initialHealth: Int? = null,
    val initialEnergy: Int? = null,
    val aiProfile: BattleAiProfile = BattleAiProfile(),
    val readinessRegenerationPerSecond: Float = 45f,
    val counterTechniqueId: String? = null,
    val signatureTechniqueId: String? = null,
    /** Bounded additive power, measured in this simulator's compact units. */
    val signaturePowerBonus: Int = 0,
    /** Species special-move name shown for the innate special; null keeps the catalog name. */
    val specialDisplayNameOverride: String? = null,
    /** Solo Blast Evolution slot (NONE/POWER/FORM); behavior lands in Phase 4. */
    val blastMode: String = "NONE",
    /** Target species for a FORM Blast. */
    val blastTargetSpecies: String? = null,
    /** Duo Jogress slot: result species chosen by this individual as fusion lead. */
    val jogressResultSpecies: String? = null,
    /** Resolved FORM attack name (universal table special); null falls back to own special name. */
    val blastFormSpecial: String? = null,
    /** Raw species name for universal-table matching. */
    val speciesName: String? = null,
    /** Partner species accepted for the equipped Jogress result (universal + Dex). */
    val jogressPartnerSpecies: List<String> = emptyList(),
    /** Partner attribute accepted for the equipped Jogress result (Dex attribute routes). */
    val jogressPartnerAttribute: BattleAttribute? = null,
    val jogressSpecial: String? = null,
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
    val element: String? = null,
    val reactionOnly: Boolean = false,
    val readinessCost: Float? = null,
    val accuracyPercent: Int = 100
)

data class BattleTeam(
    val teamId: String,
    val side: BattleSide,
    val members: List<CombatantDefinition>
)
