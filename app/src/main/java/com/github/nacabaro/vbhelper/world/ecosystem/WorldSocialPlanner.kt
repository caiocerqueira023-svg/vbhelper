package com.github.nacabaro.vbhelper.world.ecosystem

import com.github.nacabaro.vbhelper.domain.personality.DigimonSocialProfile
import com.github.nacabaro.vbhelper.domain.personality.SocialRandom
import kotlin.math.ln

enum class SocialMotive {
    CURIOUS_APPROACH, RECOGNITION, COMPANY, PLAYFUL_TEASING, CHALLENGE_SPARRING,
    TERRITORIAL_WARNING, HOSTILE_ATTACK, CHECK_IN, RECONCILE, AVOIDANCE, REFUSAL, OBSERVATION, SHARED_ACTIVITY
}

data class SocialCandidate(
    val individualId: String,
    val profile: DigimonSocialProfile,
    val emotion: Int = 0,
    val trust: Int = 50,
    val familiarity: Int = 0,
    val lastInitiationTick: Long = -1_000L,
    val territoryPressure: Double = 0.0,
    val needsHelp: Boolean = false
)

data class SocialPairCandidate(val first: SocialCandidate, val second: SocialCandidate,
    val affinity: Int = 0, val meetings: Int = 0, val lastInteractionTick: Long = -1_000,
    val cooldownUntilTick: Long = 0)

data class SocialEncounterDecision(val initiatorId: String, val targetId: String,
    val motive: SocialMotive, val variation: Int, val listenerSpeaks: Boolean = true,
    val listenerDeclines: Boolean = false, val contextDetail: String? = null) {
    val hostile: Boolean get() = motive == SocialMotive.HOSTILE_ATTACK
    val type: InteractionType get() = if (hostile) InteractionType.BATTLE else InteractionType.CHAT
    fun description(): String = when (motive) {
        SocialMotive.CURIOUS_APPROACH -> "Curious introduction"
        SocialMotive.RECOGNITION -> "Recognition after a previous encounter"
        SocialMotive.COMPANY -> "Looking for company"
        SocialMotive.PLAYFUL_TEASING -> "Playful teasing"
        SocialMotive.CHALLENGE_SPARRING -> "Friendly training invitation"
        SocialMotive.TERRITORIAL_WARNING -> "A warning about personal space"
        SocialMotive.HOSTILE_ATTACK -> "A confrontational territorial encounter"
        SocialMotive.CHECK_IN -> "Checking on a companion"
        SocialMotive.RECONCILE -> "Trying to settle a disagreement"
        SocialMotive.AVOIDANCE -> "Keeping some distance"
        SocialMotive.REFUSAL -> "Declining an invitation"
        SocialMotive.OBSERVATION -> "Sharing an observation"
        SocialMotive.SHARED_ACTIVITY -> "An invitation to spend time together"
    }
}

/** Selects intent before prose. All weights use real local state; attributes are not moral alignments. */
object WorldSocialPlanner {
    const val PLAYER_GLOBAL_COOLDOWN_TICKS = 40L
    const val IGNORED_GREETING_LIFETIME_TICKS = 24L

    fun choosePlayer(seed: Long, tick: Long, candidates: List<SocialCandidate>, hasOwnedPartner: Boolean): SocialEncounterDecision? {
        val eligible = candidates.filter { tick - it.lastInitiationTick >= it.profile.playerCooldownTicks }
            .filter { SocialRandom.unit(seed, it.individualId, "player-willingness", tick) < .25 + it.profile.initiative * .65 }
        val actor = choose(seed, tick, eligible, { it.individualId }, { .15 + it.profile.initiative }) ?: return null
        val p = actor.profile
        val friendship = (actor.trust / 100.0).coerceIn(0.0, 1.0)
        val weights = linkedMapOf(
            SocialMotive.CURIOUS_APPROACH to .25 + p.curiosity,
            SocialMotive.COMPANY to .15 + p.initiative * p.warmth,
            SocialMotive.PLAYFUL_TEASING to p.playfulness * .8,
            SocialMotive.OBSERVATION to .15 + p.patience * .5,
            SocialMotive.SHARED_ACTIVITY to p.curiosity * .65,
            SocialMotive.TERRITORIAL_WARNING to p.territoriality * actor.territoryPressure * (1.1 - friendship),
            SocialMotive.AVOIDANCE to p.boundarySensitivity * (1.0 - friendship) * .3,
            SocialMotive.CHECK_IN to p.empathy * if (actor.needsHelp) 1.4 else .2,
            SocialMotive.RECOGNITION to if (actor.familiarity > 0) .4 + friendship * p.loyalty else 0.0,
            SocialMotive.RECONCILE to if (actor.familiarity > 0 && actor.trust < 40) p.empathy * .6 else 0.0,
            SocialMotive.CHALLENGE_SPARRING to if (hasOwnedPartner) p.challenge * .65 else 0.0,
            SocialMotive.HOSTILE_ATTACK to if (hasOwnedPartner) {
                (.05 + p.challenge * .45 + p.territoriality * actor.territoryPressure * .5 +
                    (-actor.emotion).coerceIn(0, 50) / 50.0 * .5) * (1.15 - friendship).coerceAtLeast(.1)
            } else 0.0
        )
        val motive = weightedMotive(seed, actor.individualId, tick, weights)
        return SocialEncounterDecision(actor.individualId, "trainer", motive,
            (SocialRandom.unit(seed, actor.individualId, "opening-variation", tick) * 64).toInt(),
            contextDetail = when {
                motive == SocialMotive.CHECK_IN && actor.needsHelp -> "The player's active companion is recorded as injured."
                motive == SocialMotive.HOSTILE_ATTACK && actor.territoryPressure > .25 -> "The player is near this individual's home area."
                motive == SocialMotive.HOSTILE_ATTACK -> "This individual chose a confrontation; no player insult or provocation is assumed."
                else -> null
            })
    }

    fun choosePair(seed: Long, tick: Long, pairs: List<SocialPairCandidate>): SocialEncounterDecision? {
        val eligible = pairs.map { pair -> if (pair.first.individualId < pair.second.individualId) pair
            else pair.copy(first = pair.second, second = pair.first) }
            .filter { tick >= it.cooldownUntilTick && tick - it.lastInteractionTick >= 60 }
        val pair = choose(seed, tick, eligible,
            { listOf(it.first.individualId, it.second.individualId).sorted().joinToString(":") },
            { pair ->
                val novelty = if (pair.meetings == 0) (pair.first.profile.curiosity + pair.second.profile.curiosity) * .35 else .0
                val familiarity = (pair.meetings / 6.0).coerceAtMost(1.0) * pair.affinity.coerceAtLeast(0) / 100.0
                val attachment = (pair.first.profile.loyalty + pair.second.profile.loyalty) * familiarity * .3
                .1 + pair.first.profile.initiative + pair.second.profile.initiative + novelty + attachment
            }) ?: return null
        val a = pair.first
        val b = pair.second
        val initiator = if (SocialRandom.unit(seed, a.individualId + "|" + b.individualId, "pair-initiator", tick) <
            a.profile.initiative / (a.profile.initiative + b.profile.initiative)) a else b
        val listener = if (initiator === a) b else a
        val p = initiator.profile
        val weights = linkedMapOf(
            SocialMotive.CURIOUS_APPROACH to if (pair.meetings == 0) .5 + p.curiosity else .15,
            SocialMotive.RECOGNITION to if (pair.meetings > 0 && pair.affinity >= 0) .4 + p.loyalty else 0.0,
            SocialMotive.COMPANY to .2 + p.warmth * (pair.affinity + 100) / 200.0,
            SocialMotive.PLAYFUL_TEASING to p.playfulness * .7,
            SocialMotive.SHARED_ACTIVITY to p.curiosity * .8,
            SocialMotive.OBSERVATION to p.patience * .6,
            SocialMotive.CHECK_IN to p.empathy * if (listener.emotion < -10) 1.5 else .2,
            SocialMotive.RECONCILE to if (pair.affinity < 0) p.empathy * .8 else .0,
            SocialMotive.TERRITORIAL_WARNING to if (pair.affinity < 10) p.territoriality * .6 else .0,
            SocialMotive.CHALLENGE_SPARRING to p.challenge * .7,
            SocialMotive.HOSTILE_ATTACK to if (pair.affinity <= -20) p.challenge * .8 + p.territoriality else .0
        )
        val motive = weightedMotive(seed, initiator.individualId + "|" + listener.individualId, tick, weights)
        val declines = motive in listOf(SocialMotive.CHALLENGE_SPARRING, SocialMotive.SHARED_ACTIVITY) &&
            SocialRandom.unit(seed, listener.individualId, "invitation-receptivity", tick) >
            if (motive == SocialMotive.CHALLENGE_SPARRING) listener.profile.challenge else listener.profile.curiosity
        val speaks = declines || SocialRandom.unit(seed, listener.individualId, "participation", tick) < .4 + listener.profile.initiative * .6
        return SocialEncounterDecision(initiator.individualId, listener.individualId, motive,
            (SocialRandom.unit(seed, initiator.individualId, "pair-opening", tick) * 64).toInt(), speaks, declines)
    }

    private fun weightedMotive(seed: Long, id: String, tick: Long, weights: Map<SocialMotive, Double>): SocialMotive {
        var draw = SocialRandom.unit(seed, id, "encounter-motive", tick) * weights.values.sum()
        weights.forEach { (motive, weight) -> draw -= weight; if (draw < 0) return motive }
        return SocialMotive.OBSERVATION
    }

    private fun <T> choose(seed: Long, tick: Long, items: List<T>, id: (T) -> String, weight: (T) -> Double): T? =
        items.minWithOrNull(compareBy<T> { -ln(SocialRandom.unit(seed, id(it), "social-priority", tick).coerceAtLeast(1e-12)) / weight(it).coerceAtLeast(.01) }
            .thenBy(id))
}
