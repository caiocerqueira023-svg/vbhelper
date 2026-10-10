package com.github.nacabaro.vbhelper.battle.offline.tamers

import com.github.nacabaro.vbhelper.battle.offline.core.*
import com.github.nacabaro.vbhelper.battle.offline.data.GenericTechniqueCatalog
import com.github.nacabaro.vbhelper.battle.offline.data.GenericTechniqueFamily
import com.github.nacabaro.vbhelper.screens.OfflineBattleParticipant

object TamerBattleLoadouts {
    private fun norm(name: String) = BattleSpeciesIdentity.normalize(name)

    fun policy(style: TamerStyle, difficulty: ArenaDifficulty): TrainerAiPolicy = TrainerAiPolicy(
        strategy = style.strategy,
        reactionMillis = when (difficulty) { ArenaDifficulty.CASUAL -> 1_500L; ArenaDifficulty.NORMAL -> 900L; ArenaDifficulty.EXPERT -> 510L },
        healThreshold = when (style) { TamerStyle.SUPPORT -> 0.55f; TamerStyle.RUSH -> 0.28f; else -> 0.38f },
        energyThreshold = if (style == TamerStyle.RUSH) 0.35f else 0.22f,
        finisherHitChance = when (difficulty) { ArenaDifficulty.CASUAL -> 0.55f; ArenaDifficulty.NORMAL -> 0.80f; ArenaDifficulty.EXPERT -> 0.94f },
        focusVulnerable = style in setOf(TamerStyle.TACTICAL, TamerStyle.RUSH, TamerStyle.CONTROL),
    )

    fun participants(team: ResolvedTamerTeam, resolver: TamerTeamResolver): List<OfflineBattleParticipant> {
        val members = team.members.mapIndexed { slot, member ->
            val species = member.species
            val techniques = techniques(species.copy(stage = member.battleStage), team.tamer.style)
            val form = resolver.soloBlastForm(member)
            OfflineBattleParticipant(character = null, assetCharacterId = species.assetId,
                displayName = member.nickname ?: species.name, maxHp = 1, attackPower = 1, stage = member.battleStage, visualStage = species.stage,
                externalCharacterId = species.assetId, vitalStats = species.vitalStats, attribute = species.attribute,
                battleInstanceId = "tamer:${team.tamer.id}:$slot", stableRngKey = "tamer:${team.tamer.id}:$slot:ai-v1",
                strategy = team.tamer.style.strategy,
                techniqueIds = techniques.filter { it.kind != TechniqueKind.SPECIAL }.map { it.techniqueId },
                specialTechniqueId = techniques.last().techniqueId, battleTechniques = techniques,
                specialDisplayNameOverride = techniques.last().displayName,
                blastMode = if (form != null) "FORM" else "POWER", blastTargetSpecies = form?.name,
                blastFormSpecial = form?.specialMoves?.firstOrNull(), speciesName = species.name,
                cardCharacterId = species.cardCharacterId,
                aiProfile = BattleAiProfile(profileId = "tamer:${team.tamer.id}:$slot",
                    techniqueWeights = techniques.filter { it.kind != TechniqueKind.SPECIAL }.mapIndexed { index, technique ->
                        technique.techniqueId to listOf(1.15f, 1f, 0.85f)[index]
                    }.toMap(), selectionMode = BattleSelectionMode.WEIGHTED,
                    targetPolicy = if (team.tamer.style == TamerStyle.CONTROL) BattleTargetPolicy.STATUS else BattleTargetPolicy.VULNERABLE,
                    chargeMode = if (team.tamer.style == TamerStyle.RUSH) BattleChargeMode.PRESSURE else BattleChargeMode.BALANCED,
                    counterChance = if (team.tamer.style == TamerStyle.GUARD) 0.45f else 0.25f,
                    autonomousSpecial = false))
        }
        if (members.size != 2) return members
        val pair = members.map { norm(it.speciesName.orEmpty()) }.toSet()
        val approved = TamerCanonicalPartners.permittedJogress[team.tamer.id].orEmpty().map(::norm).toSet()
        val fusion = TamerCanonicalPartners.jogressPairs
            .firstOrNull { setOf(norm(it.first), norm(it.second)) == pair && norm(it.third) in approved }
            ?.let { triple -> resolver.speciesNamed(triple.third)?.let { triple to it } } ?: return members
        return members.mapIndexed { index, member -> if (index != 0) member else member.copy(
            jogressResultSpecies = fusion.second.name,
            jogressPartnerSpecies = listOf(members[1].speciesName.orEmpty()),
            jogressSpecial = fusion.second.specialMoves.firstOrNull()) }
    }

    private fun techniques(species: ArenaSpecies, style: TamerStyle): List<TechniqueDefinition> {
        val names = species.specialMoves.distinct()
        val rank = (species.stage + 1).coerceIn(1, 6)
        val family = when (style) {
            TamerStyle.RUSH -> GenericTechniqueFamily.ASSAULT
            TamerStyle.GUARD, TamerStyle.SUPPORT -> GenericTechniqueFamily.ENDURANCE
            TamerStyle.CONTROL -> GenericTechniqueFamily.CONTROL
            TamerStyle.TACTICAL -> GenericTechniqueFamily.PRECISION
            else -> GenericTechniqueFamily.TACTICS
        }
        val id = "tamer:${BattleSpeciesIdentity.normalize(species.name)}:${style.name.lowercase()}:${species.stage}"
        val projectile = GenericTechniqueCatalog.definition("generic_tactics_focused_shot")
        val melee = GenericTechniqueCatalog.definition("generic_assault_quick_combo")
        fun motion(name: String, fallback: TechniqueDefinition): TechniqueDefinition {
            val key = name.lowercase()
            val template = when {
                listOf("shot", "cannon", "beam", "blaster", "flame", "fireball", "arrow", "missile", "breath").any(key::contains) -> projectile
                key == "double impact" -> projectile
                listOf("claw", "punch", "knuckle", "bite", "slash", "kick", "blade", "rush", "breaker", "horn").any(key::contains) -> melee
                else -> fallback
            }
            return template.copy(statusEffects = emptyList())
        }
        val firstName = names.firstOrNull() ?: "Strike"
        val secondName = names.getOrNull(1) ?: "Close strike"
        val specialist = GenericTechniqueCatalog.entries.first { it.family == family && it.rank == rank }.definition
        val ordinary = mutableListOf(
            motion(firstName, if (style == TamerStyle.RANGED) projectile else specialist).copy(
                techniqueId = "$id:0", displayName = firstName, power = 65 + species.stage * 6,
                energyCost = 15 + species.stage * 3, hitCount = 1, cooldownMillis = 1_500),
            motion(secondName, melee).copy(techniqueId = "$id:1", displayName = secondName,
                power = 38 + species.stage * 4, energyCost = 12 + species.stage * 3, hitCount = 2, cooldownMillis = 1_900),
        )
        if (BattleSpeciesIdentity.normalize(species.name) == "marinangemon") {
            ordinary[0] = TechniqueDefinition("$id:0", "Ocean Love", TechniqueKind.HEAL, 0, energyCost = 45,
                maxRange = 16f, rangeProfile = TechniqueRangeProfile.ALL_FIELD, healPower = 200 + species.stage * 35,
                startupMillis = 500, recoveryMillis = 850, cooldownMillis = 8_000)
            ordinary[1] = melee.copy(techniqueId = "$id:1", displayName = "Close strike", power = 25,
                energyCost = 10, statusEffects = emptyList(), hitCount = 1)
        }
        val third = if (BattleSpeciesIdentity.normalize(species.name) == "marinangemon") {
            GenericTechniqueCatalog.definition("generic_endurance_resistant_stance").copy(
                techniqueId = "$id:2", displayName = "Guard stance")
        } else if (names.size >= 3 && style !in setOf(TamerStyle.SUPPORT, TamerStyle.GUARD)) {
            motion(names[2], specialist).copy(techniqueId = "$id:2", displayName = names[2],
                power = 80 + species.stage * 8, energyCost = 30 + species.stage * 5, hitCount = 1,
                statusEffects = if (style == TamerStyle.CONTROL) listOf(BattleStatusEffect("decode_slow", 3_000,
                    magnitude = 0.25f, slowsMovement = true, procChance = 0.35f)) else emptyList(), cooldownMillis = 3_000)
        } else GenericTechniqueCatalog.definition("generic_endurance_resistant_stance").copy(
            techniqueId = "$id:2", displayName = "Guard stance")
        ordinary += third
        val specialName = names.firstOrNull() ?: "Signature strike"
        val baseSpecial = GenericTechniqueCatalog.definition(GenericTechniqueCatalog.trainingSpecialTechniqueId)
        val specialMotion = motion(specialName, baseSpecial)
        val healer = BattleSpeciesIdentity.normalize(species.name) == "marinangemon"
        val special = baseSpecial.copy(techniqueId = "$id:special", displayName = specialName,
                minRange = if (healer) 0f else specialMotion.minRange,
                maxRange = if (healer) 16f else specialMotion.maxRange,
                rangeProfile = if (healer) TechniqueRangeProfile.ALL_FIELD else specialMotion.rangeProfile,
                statusEffects = emptyList(), impactShape = TechniqueImpactShape.SINGLE_TARGET, areaRadius = 0f,
                hitCount = 1, power = 160 + species.stage * 10,
                healPower = if (healer) 450 else 0)
        return ordinary + special
    }
}
