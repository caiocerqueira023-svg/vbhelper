package com.github.nacabaro.vbhelper.battle.offline.tamers

import com.github.nacabaro.vbhelper.battle.offline.core.BattleAttribute
import com.github.nacabaro.vbhelper.battle.offline.core.BattleSpeciesIdentity
import com.github.nacabaro.vbhelper.battle.offline.data.VitalBattleProfile

data class ArenaSpecies(
    val name: String,
    val stage: Int,
    val assetId: String?,
    val cardCharacterId: Long?,
    val attribute: BattleAttribute,
    val specialMoves: List<String>,
    val vitalStats: VitalBattleProfile?,
    val description: String? = null,
)

data class ResolvedTamerPartner(
    val partnerId: String,
    val ownerId: String,
    val anchor: String,
    val species: ArenaSpecies,
    val origin: ArenaPartnerOrigin,
    val inferred: Boolean,
    val battleStage: Int = species.stage,
    val scaled: Boolean = false,
    val componentPartnerIds: Set<String> = setOf(partnerId),
    val sourceUri: String? = null,
    /** Name the tamer uses for this individual, if any; species matching never uses it. */
    val nickname: String? = null,
)
data class ResolvedTamerTeam(val tamer: TamerDefinition, val members: List<ResolvedTamerPartner>)

/** Canonical identity is selected before artwork; only genuine stage gaps allow stat scaling. */
class TamerTeamResolver(
    val tamers: List<TamerDefinition>,
    val species: List<ArenaSpecies>,
    val nicknames: Map<Pair<String, String>, PartnerNickname> = emptyMap(),
) {
    private val byId = tamers.associateBy { it.id }
    private val bySpecies = species.groupBy { BattleSpeciesIdentity.normalize(it.name) }

    fun speciesNamed(name: String): ArenaSpecies? = bySpecies[BattleSpeciesIdentity.normalize(name)]?.firstOrNull()

    private data class StageChoice(val names: List<String>, val stage: Int, val sourceUri: String,
        val components: Set<String> = emptySet(), val fusionResult: String? = null)

    private fun identity(owner: TamerDefinition, anchor: String): String = TamerCanonicalPartners.sharedPartnerId(owner.id, anchor)
        ?: when (BattleSpeciesIdentity.normalize(anchor)) {
        BattleSpeciesIdentity.normalize(owner.primary) -> "${owner.id}:primary"
        owner.secondary?.let(BattleSpeciesIdentity::normalize) -> "${owner.id}:secondary"
        else -> "${owner.id}:other:${BattleSpeciesIdentity.normalize(anchor)}"
    }

    private fun choices(owner: TamerDefinition, anchor: String, primary: Boolean, solo: Boolean = false): Map<Int, StageChoice> {
        val history = TamerCanonicalPartners.history(owner.id, anchor)
        val result = history?.stages?.mapValues { (stage, names) ->
            StageChoice(names, stage, history.sourceUri)
        }?.toMutableMap() ?: mutableMapOf()
        // An explicitly documented partner remains canonical at its reference level,
        // even when the character page documents no other evolutions.
        if (history == null) bySpecies[BattleSpeciesIdentity.normalize(anchor)].orEmpty().forEach { art ->
            result.putIfAbsent(art.stage, StageChoice(listOf(anchor), art.stage, owner.sourceUri))
        }
        if (primary) TamerCanonicalPartners.fusionLeads[owner.id].orEmpty().forEach { fusion ->
            // A solo-only combined form leads single fights; beside a partner
            // the components field and equip it instead.
            if (fusion.soloOnly && !solo) return@forEach
            // The combined form leads; documented solo forms at the same tier stay
            // reachable when its artwork is missing.
            val existing = result[fusion.stage]?.names.orEmpty()
            result[fusion.stage] = StageChoice(
                (listOf(fusion.species) + existing).distinct(), fusion.stage, fusion.sourceUri,
                fusion.components, fusion.species)
        }
        return result
    }

    private fun materialize(owner: TamerDefinition, anchor: String, battleStage: Int, origin: ArenaPartnerOrigin,
        choice: StageChoice): ResolvedTamerPartner? {
        val found = choice.names.firstNotNullOfOrNull { name ->
            val candidates = bySpecies[BattleSpeciesIdentity.normalize(name)].orEmpty()
            candidates.firstOrNull { it.stage == choice.stage } ?: candidates.firstOrNull()
        } ?: return null
        val partnerId = identity(owner, anchor)
        // Absorbing components only applies when the combined form itself is
        // fielded; a scaled solo form never consumes its teammates' slots.
        val fused = choice.fusionResult?.let {
            BattleSpeciesIdentity.normalize(it) == BattleSpeciesIdentity.normalize(found.name)
        } == true
        val components = (if (fused) choice.components else emptySet()) + partnerId +
            TamerCanonicalPartners.extraComponents(owner.id, found.name)
        return ResolvedTamerPartner(partnerId, owner.id, anchor, found.copy(stage = choice.stage), origin,
            inferred = false, battleStage = battleStage, scaled = battleStage != choice.stage,
            componentPartnerIds = components, sourceUri = choice.sourceUri,
            nickname = TamerNicknames.forPartner(nicknames, owner.id, anchor, found.name)?.nickname)
    }

    /** Display label: the tamer's own name for the individual, else the species. */
    fun memberLabel(member: ResolvedTamerPartner): String = member.nickname ?: member.species.name

    fun nicknameMatches(tamerId: String, query: String): Boolean {
        val needle = query.trim().lowercase(java.util.Locale.ROOT)
        if (needle.isEmpty()) return true
        return nicknames.values.any { it.tamerId == tamerId &&
            it.nickname.lowercase(java.util.Locale.ROOT).contains(needle) }
    }

    private fun partner(owner: TamerDefinition, anchor: String, stage: Int, origin: ArenaPartnerOrigin,
        primary: Boolean = false, allowScaling: Boolean = true, solo: Boolean = false): ResolvedTamerPartner? {
        val documented = choices(owner, anchor, primary, solo)
        // A documented form without artwork falls through to the nearest art-backed
        // form of the same partner, stat-scaled to the requested tier. Forms from
        // other partners or tamers are never substituted.
        documented[stage]?.let { choice ->
            materialize(owner, anchor, stage, origin, choice)?.let { return it }
        }
        if (!allowScaling) return null
        return documented.values.sortedWith(compareBy<StageChoice> { kotlin.math.abs(it.stage - stage) }
            .thenBy { if (it.stage <= stage) 0 else 1 }.thenBy { it.stage })
            .firstNotNullOfOrNull { materialize(owner, anchor, stage, origin, it) }
    }

    private fun ownCompanions(owner: TamerDefinition): List<Pair<String, ArenaPartnerOrigin>> = buildList {
        owner.secondary?.let { add(it to owner.secondaryOrigin) }
        TamerCanonicalPartners.companions[owner.id].orEmpty().forEach { add(it to ArenaPartnerOrigin.OWNED) }
    }.distinctBy { BattleSpeciesIdentity.normalize(it.first) }

    private fun primary(owner: TamerDefinition, stage: Int, origin: ArenaPartnerOrigin = ArenaPartnerOrigin.OWNED,
        solo: Boolean = false): ResolvedTamerPartner? {
        val documented = choices(owner, owner.primary, primary = true, solo = solo)
        // Exact documented form first; a missing artwork falls through to
        // the lead's nearest art-backed form, then to documented teammates.
        if (stage in documented) {
            partner(owner, owner.primary, stage, origin, primary = true, allowScaling = false, solo = solo)?.let { return it }
        }
        // The lead keeps its own line through the nearest art-backed form before
        // documented teammates step in; this preserves both identity and fusion pairs.
        partner(owner, owner.primary, stage, origin, primary = true, solo = solo)?.let { return it }
        // Multi-partner game characters use a documented team member as a last resort.
        for ((anchor, relationship) in ownCompanions(owner)) {
            partner(owner, anchor, stage, if (origin == ArenaPartnerOrigin.GUEST) origin else relationship,
                allowScaling = false)?.let { return it }
        }
        return partner(owner, owner.primary, stage, origin, primary = true)
    }

    /** A solo Blast must not fabricate a stage, or merge another on-field partner implicitly. */
    fun soloBlastForm(member: ResolvedTamerPartner): ArenaSpecies? {
        TamerCanonicalPartners.mode(member.ownerId, member.species.name)?.let { mode ->
            speciesNamed(mode)?.let { return it }
        }
        val owner = byId[member.ownerId] ?: return null
        val history = TamerCanonicalPartners.history(owner.id, member.anchor) ?: return null
        val next = history.stages[member.species.stage + 1].orEmpty()
        val ownedFusions = TamerCanonicalPartners.fusionLeads[owner.id].orEmpty().map {
            BattleSpeciesIdentity.normalize(it.species)
        }.toSet()
        return next.filter { BattleSpeciesIdentity.normalize(it) !in ownedFusions &&
            TamerCanonicalPartners.extraComponents(owner.id, it).isEmpty() }
            .firstNotNullOfOrNull(::speciesNamed)
    }

    fun resolve(tamerId: String, stages: List<Int>): ResolvedTamerTeam? {
        require(stages.size in 1..2 && stages.all { it in 0..5 })
        val tamer = byId[tamerId] ?: return null
        val first = primary(tamer, stages[0], solo = stages.size == 1) ?: return null
        if (stages.size == 1) return ResolvedTamerTeam(tamer, listOf(first))
        fun independent(member: ResolvedTamerPartner?) = member?.takeIf {
            first.componentPartnerIds.intersect(it.componentPartnerIds).isEmpty()
        }
        // Second slot: owned companions and allied guests compete openly. A
        // candidate that completes an approved fusion with the lead wins; owned
        // beats guest on ties, list order breaks the rest. Absorbed components
        // stay excluded even at other stages.
        val own = ownCompanions(tamer).mapNotNull { (anchor, relationship) ->
            if (identity(tamer, anchor) in first.componentPartnerIds) null
            else independent(partner(tamer, anchor, stages[1], relationship))?.let { it to true }
        }
        val guests = (listOfNotNull(tamer.guestId) + TamerCanonicalPartners.guestAlternatives[tamer.id].orEmpty())
            .distinct().filter { it != tamer.id }.mapNotNull { id ->
                byId[id]?.let { independent(primary(it, stages[1], ArenaPartnerOrigin.GUEST)) }?.let { it to false }
            }
        val second = (own + guests).sortedWith(
            compareByDescending<Pair<ResolvedTamerPartner, Boolean>> { (member, _) ->
                TamerCanonicalPartners.completesApprovedFusion(tamer.id, first.species.name, member.species.name)
            }.thenBy { (_, owned) -> if (owned) 0 else 1 }
        ).firstOrNull()?.first ?: return null
        return ResolvedTamerTeam(tamer, listOf(first, second))
    }

    fun coverage(tamerId: String): Int = (0..5).count { resolve(tamerId, listOf(it)) != null }

    fun missingArtwork(tamerId: String, stages: List<Int>): List<String> {
        val owner = byId[tamerId] ?: return emptyList()
        if (resolve(tamerId, stages) != null) return emptyList()
        val solo = stages.size == 1
        if (primary(owner, stages[0], solo = solo) == null) {
            return choices(owner, owner.primary, primary = true, solo = solo)[stages[0]]?.names?.take(2) ?: listOf(owner.primary)
        }
        if (stages.size < 2) return emptyList()
        val first = primary(owner, stages[0], solo = solo) ?: return emptyList()
        val secondary = owner.secondary?.takeIf { identity(owner, it) !in first.componentPartnerIds }
        return secondary?.let { choices(owner, it, primary = false)[stages[1]]?.names?.take(2) } ?: emptyList()
    }
}
