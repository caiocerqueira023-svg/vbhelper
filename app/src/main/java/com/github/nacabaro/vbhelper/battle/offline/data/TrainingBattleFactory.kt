package com.github.nacabaro.vbhelper.battle.offline.data

import com.github.nacabaro.vbhelper.battle.offline.core.BattleConfiguration
import com.github.nacabaro.vbhelper.battle.offline.core.BattleAttribute
import com.github.nacabaro.vbhelper.battle.offline.core.BattleItemDefinition
import com.github.nacabaro.vbhelper.battle.offline.core.BattleItemKind
import com.github.nacabaro.vbhelper.battle.offline.core.BattleSide
import com.github.nacabaro.vbhelper.battle.offline.core.BattleSimulator
import com.github.nacabaro.vbhelper.battle.offline.core.BattleStrategy
import com.github.nacabaro.vbhelper.battle.offline.core.BattleTeam
import com.github.nacabaro.vbhelper.battle.offline.core.CombatantDefinition
import com.github.nacabaro.vbhelper.battle.offline.core.TechniqueDefinition
import com.github.nacabaro.vbhelper.battle.offline.core.TechniqueKind
import com.github.nacabaro.vbhelper.domain.personality.DigimonPersonalityType

/** Immutable copy of the participant values needed to enter a disposable training session. */
data class TrainingParticipantInput(
    val instanceId: String,
    val sourceCharacterId: Long? = null,
    val externalCharacterId: String? = null,
    val displayName: String,
    val stage: Int,
    val maxHealth: Int,
    val attack: Int,
    val strategy: BattleStrategy = BattleStrategy.BALANCED,
    val vitalStats: VitalBattleProfile? = null,
    val attribute: BattleAttribute = BattleAttribute.NONE,
    val stableRngKey: String = instanceId,
    val personalityType: DigimonPersonalityType = DigimonPersonalityType.FRIENDLY
)

/** Builds the first training loadout without reading or modifying Room during a battle tick. */
object TrainingBattleFactory {
    private const val PRACTICE_SPECIAL_ID = "practice_special"

    val techniques: List<TechniqueDefinition> = listOf(
        TechniqueDefinition(
            techniqueId = "practice_quick_burst",
            displayName = "Disparo rápido",
            kind = TechniqueKind.PROJECTILE,
            power = 95,
            energyCost = 15,
            minRange = 2f,
            maxRange = 8f,
            startupMillis = 350L,
            activeMillis = 100L,
            recoveryMillis = 650L,
            cooldownMillis = 1_200L,
            attackVisual = "small"
        ),
        TechniqueDefinition(
            techniqueId = "practice_heavy_burst",
            displayName = "Disparo pesado",
            kind = TechniqueKind.PROJECTILE,
            power = 175,
            energyCost = 42,
            minRange = 3f,
            maxRange = 11f,
            startupMillis = 700L,
            activeMillis = 120L,
            recoveryMillis = 1_150L,
            cooldownMillis = 3_500L,
            staggerPower = 40f,
            attackVisual = "large",
            knockbackDistance = 1.1f
        ),
        TechniqueDefinition(
            techniqueId = "practice_guard_break",
            displayName = "Impacto próximo",
            kind = TechniqueKind.MELEE,
            power = 130,
            energyCost = 23,
            minRange = 0.8f,
            maxRange = 2.2f,
            startupMillis = 430L,
            activeMillis = 110L,
            recoveryMillis = 800L,
            cooldownMillis = 1_800L,
            staggerPower = 22f,
            attackVisual = "small"
        ),
        TechniqueDefinition(
            techniqueId = PRACTICE_SPECIAL_ID,
            displayName = "Golpe especial",
            kind = TechniqueKind.SPECIAL,
            power = 260,
            energyCost = 110,
            commandPointCost = 25,
            minRange = 2f,
            maxRange = 12f,
            startupMillis = 1_100L,
            activeMillis = 150L,
            recoveryMillis = 1_700L,
            cooldownMillis = 12_000L,
            staggerPower = 70f,
            attackVisual = "large"
        )
    )

    /** Reset on every new practice session and on every rematch. */
    val trainingItems: List<BattleItemDefinition> = listOf(
        BattleItemDefinition("training_recovery", "Kit de recuperação", BattleItemKind.HEAL_HEALTH, 2, 900),
        BattleItemDefinition("training_energy", "Ração energética", BattleItemKind.RESTORE_ENERGY, 2, 120),
        BattleItemDefinition("training_cleanse", "Antídoto de treino", BattleItemKind.CLEANSE_STATUS, 1)
    )

    fun create(
        allies: List<TrainingParticipantInput>,
        opponents: List<TrainingParticipantInput>,
        configuration: BattleConfiguration = BattleConfiguration()
    ): BattleSimulator {
        require(allies.size in 1..2) { "O treino aceita um ou dois parceiros." }
        require(opponents.size in 1..2) { "O treino aceita um ou dois oponentes." }
        require(allies.size <= opponents.size) { "Formatos disponíveis: 1×1, 1×2 e 2×2." }
        require((allies + opponents).all { it.instanceId.isNotBlank() }) { "Instância sem identidade." }
        val alliedSources = allies.mapNotNull { it.sourceCharacterId }
        require(alliedSources.distinct().size == alliedSources.size) { "Um parceiro não pode ocupar dois slots aliados." }
        val allIds = allies.map { "ally:${it.instanceId}" } + opponents.map { "opponent:${it.instanceId}" }
        require(allIds.distinct().size == allIds.size) { "Cada combatente precisa ter uma instância própria." }

        return BattleSimulator(
            configuration = configuration,
            alliedTeam = BattleTeam(
                teamId = configuration.alliedTeamId,
                side = BattleSide.ALLIED,
                members = allies.map { it.toDefinition(BattleSide.ALLIED) }
            ),
            opposingTeam = BattleTeam(
                teamId = configuration.opposingTeamId,
                side = BattleSide.OPPOSING,
                members = opponents.map { it.toDefinition(BattleSide.OPPOSING) }
            ),
            techniqueCatalog = techniques,
            trainingItems = trainingItems
        )
    }

    fun definition(input: TrainingParticipantInput, side: BattleSide): CombatantDefinition = input.toDefinition(side)

    private fun TrainingParticipantInput.toDefinition(side: BattleSide): CombatantDefinition {
        val stage = stage.coerceIn(0, 5)
        val stats = TrainingBattleStats.forParticipant(stage, vitalStats)
        return CombatantDefinition(
            combatantId = "${if (side == BattleSide.ALLIED) "ally" else "opponent"}:$instanceId",
            sourceCharacterId = sourceCharacterId,
            externalCharacterId = externalCharacterId,
            displayName = displayName,
            side = side,
            attribute = attribute,
            maxHealth = stats.health,
            maxEnergy = stats.energy,
            attack = stats.attack,
            defense = stats.defense,
            energyRegenerationPerSecond = stats.energyRegenerationPerSecond,
            movementSpeed = stats.movementSpeed,
            techniqueCooldownMultiplier = stats.cooldownMultiplier,
            collisionRadius = 0.42f + stage * 0.015f,
            preferredDistance = 2f,
            decisionDelayMinMillis = stats.decisionDelayMinMillis,
            decisionDelayMaxMillis = stats.decisionDelayMaxMillis,
            strategy = strategy,
            stableRngKey = stableRngKey,
            personalityType = personalityType,
            techniqueIds = listOf("practice_quick_burst", "practice_heavy_burst", "practice_guard_break"),
            specialTechniqueId = PRACTICE_SPECIAL_ID
        )
    }
}
