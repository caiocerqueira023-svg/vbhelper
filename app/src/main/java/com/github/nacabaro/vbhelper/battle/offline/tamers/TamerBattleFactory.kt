package com.github.nacabaro.vbhelper.battle.offline.tamers

import com.github.nacabaro.vbhelper.battle.offline.core.*
import com.github.nacabaro.vbhelper.battle.offline.data.GenericTechniqueCatalog
import com.github.nacabaro.vbhelper.battle.offline.data.TrainingBattleFactory
import com.github.nacabaro.vbhelper.battle.offline.data.TrainingBattleStats
import com.github.nacabaro.vbhelper.battle.offline.data.TrainingParticipantInput
import com.github.nacabaro.vbhelper.screens.OfflineBattleParticipant

data class ArenaEntrant(
    val id: String,
    val name: String,
    val tamerId: String?,
    val members: List<OfflineBattleParticipant>,
    val policy: TrainerAiPolicy,
)

data class ArenaMatchSpec(
    val id: String,
    val left: ArenaEntrant,
    val right: ArenaEntrant,
    val difficulty: ArenaDifficulty,
    val configuration: BattleConfiguration,
    val tournamentId: String? = null,
    val contentVersion: Int = TamerCatalog.VERSION,
    val aiVersion: Int = 1,
    val resultSpecies: List<ArenaSpecies> = emptyList(),
    val bracketMatchId: String? = null,
    val tiebreakRound: Int = 0,
    val canonRevision: Int = TamerCanonicalPartners.REVISION,
)

object TamerBattleFactory {
    /** Persist source/art identities and combat values, not a mutable owned-character DTO. */
    fun freeze(participant: OfflineBattleParticipant): OfflineBattleParticipant = participant.copy(
        sourceCharacterId = participant.character?.id ?: participant.sourceCharacterId,
        cardCharacterId = participant.character?.charId ?: participant.cardCharacterId,
        battleInstanceId = participant.stableId,
        // The requested tier the opponent mirrors; the simulator coerces it identically.
        stage = participant.stage.coerceIn(0, 5),
        character = null,
        initialHealth = null,
        initialEnergy = null,
    )

    fun input(participant: OfflineBattleParticipant) = TrainingParticipantInput(
        instanceId = participant.stableId, sourceCharacterId = participant.character?.id ?: participant.sourceCharacterId,
        externalCharacterId = participant.externalCharacterId ?: participant.assetCharacterId,
        displayName = participant.displayName, stage = participant.stage, maxHealth = participant.maxHp,
        attack = participant.attackPower, vitalStats = participant.vitalStats, attribute = participant.attribute,
        stableRngKey = participant.stableRngKey, personalityType = participant.personalityType,
        strategy = participant.strategy, techniqueIds = participant.techniqueIds,
        specialDisplayNameOverride = participant.specialDisplayNameOverride,
        blastMode = participant.blastMode, blastTargetSpecies = participant.blastTargetSpecies,
        blastFormSpecial = participant.blastFormSpecial, jogressResultSpecies = participant.jogressResultSpecies,
        speciesName = participant.speciesName, initialHealth = participant.initialHealth, initialEnergy = participant.initialEnergy,
        aiProfile = participant.aiProfile, battleTechniques = participant.battleTechniques,
        specialTechniqueId = participant.specialTechniqueId, jogressPartnerSpecies = participant.jogressPartnerSpecies,
        jogressSpecial = participant.jogressSpecial,
    )

    fun definitions(entrant: ArenaEntrant, side: BattleSide) = entrant.members.map { TrainingBattleFactory.definition(input(it), side) }

    fun techniques(spec: ArenaMatchSpec): List<TechniqueDefinition> =
        (GenericTechniqueCatalog.definitionsForVersion(spec.configuration.rulesetVersion) +
            (spec.left.members + spec.right.members).flatMap { it.battleTechniques }).distinctBy { it.techniqueId }

    fun items(members: List<OfflineBattleParticipant>): List<BattleItemDefinition> {
        val baseline = members.map { TrainingBattleStats.forStage(it.stage) }
        val health = baseline.map { it.health }.average().toInt().coerceAtLeast(1)
        val energy = baseline.map { it.energy }.average().toInt().coerceAtLeast(1)
        return listOf(
            BattleItemDefinition("arena_recovery", "Recovery", BattleItemKind.HEAL_HEALTH, 2, health * 40 / 100),
            BattleItemDefinition("arena_energy", "Energy", BattleItemKind.RESTORE_ENERGY, 2, energy * 40 / 100),
            BattleItemDefinition("arena_cleanse", "Status remedy", BattleItemKind.CLEANSE_STATUS, 1),
        )
    }

    fun create(spec: ArenaMatchSpec, autoplayLeft: Boolean = false, suddenDeath: Boolean = false): BattleSimulator {
        validate(spec)
        val tiebreak = suddenDeath || spec.tiebreakRound > 0
        fun definitionsFor(entrant: ArenaEntrant, side: BattleSide) = definitions(entrant, side).map {
            if (tiebreak) it.copy(initialHealth = (it.maxHealth / 4).coerceAtLeast(1)) else it
        }
        return BattleSimulator(spec.configuration.copy(defaultPaused = false,
            maxDurationMillis = if (tiebreak) 30_000 else spec.configuration.maxDurationMillis),
            BattleTeam(spec.configuration.alliedTeamId, BattleSide.ALLIED, definitionsFor(spec.left, BattleSide.ALLIED)),
            BattleTeam(spec.configuration.opposingTeamId, BattleSide.OPPOSING, definitionsFor(spec.right, BattleSide.OPPOSING)),
            techniques(spec), if (tiebreak) emptyList() else items(spec.left.members),
            if (tiebreak) emptyList() else items(spec.right.members),
            buildMap { put(BattleSide.OPPOSING, spec.right.policy); if (autoplayLeft) put(BattleSide.ALLIED, spec.left.policy) })
    }

    fun validate(spec: ArenaMatchSpec) {
        require(spec.contentVersion == TamerCatalog.VERSION && spec.aiVersion == 1) { "Unsupported arena content version" }
        require(spec.tiebreakRound in 0..1) { "Unsupported arena tiebreak round ${spec.tiebreakRound}" }
        require(spec.canonRevision in 0..TamerCanonicalPartners.REVISION) { "Unsupported canonical partner policy" }
        require(spec.id.isNotBlank() && spec.left.id != spec.right.id) { "Arena entrants need distinct identities" }
        require(spec.left.members.size in 1..2 && spec.left.members.size == spec.right.members.size) {
            "Arena formats are 1x1 and 2x2 with equal team sizes (yours ${spec.left.members.size}, opponent ${spec.right.members.size})"
        }
        require((spec.left.members + spec.right.members).all { it.stage in 0..5 && it.stableId.isNotBlank() }) {
            "Every arena combatant needs a 0-5 stage and a non-blank identity"
        }
        require(spec.left.members.map { it.stableId }.distinct().size == spec.left.members.size) {
            "Your arena team needs one instance per slot"
        }
        require(spec.right.members.map { it.stableId }.distinct().size == spec.right.members.size) {
            "The opponent arena team needs one instance per slot"
        }
        require(spec.configuration.rulesetVersion == BattleRules.CURRENT_VERSION && spec.configuration.strictFinisherEligibility) {
            "Arena matches require the current ruleset with strict finisher eligibility"
        }
    }
}
