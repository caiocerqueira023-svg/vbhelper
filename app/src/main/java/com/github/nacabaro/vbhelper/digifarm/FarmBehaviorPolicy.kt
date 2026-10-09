package com.github.nacabaro.vbhelper.digifarm

import com.github.nacabaro.vbhelper.domain.digifarm.FarmResident
import com.github.nacabaro.vbhelper.domain.personality.DigimonSocialProfile
import com.github.nacabaro.vbhelper.domain.personality.SocialRandom
import com.github.nacabaro.vbhelper.domain.digifarm.FarmRelationship
import kotlin.math.hypot
import kotlin.math.ln

/** Absolute bucket differences preserve fractional decay across short foreground steps. */
object FarmBehaviorPolicy {
    fun advanceNeeds(resident: FarmResident, now: Long): FarmResident {
        if (now <= resident.updatedAt) return resident
        val end = now / 30_000
        val start = (resident.updatedAt / 30_000).coerceAtLeast(end - 960)
        val ticks = (end - start).toInt()
        fun decay(period: Long) = (end / period - start / period).toInt()
        return resident.copy(
            energy = (resident.energy - decay(2) + when (resident.activity) { "REST" -> ticks * 2; "TRAIN" -> -ticks; else -> 0 }).coerceIn(10, 100),
            satiety = (resident.satiety - decay(3) + if (resident.activity == "EAT") ticks * 3 else 0).coerceIn(10, 100),
            social = (resident.social - decay(6) + if (resident.activity in listOf("PLAY", "SOCIALIZE")) ticks else 0).coerceIn(10, 100),
            funLevel = (resident.funLevel - decay(6) + if (resident.activity == "PLAY") ticks * 2 else 0).coerceIn(10, 100),
            updatedAt = now
        )
    }

    fun activity(resident: FarmResident, profile: DigimonSocialProfile, now: Long, seed: Long): String {
        if (resident.energy < 20) return "REST"
        if (resident.satiety < 20) return "EAT"
        if (now - resident.activityStartedAt < profile.activityCommitmentMillis) return resident.activity
        val weights = linkedMapOf(
            "REST" to .1 + (100 - resident.energy) / 55.0,
            "EAT" to .1 + (100 - resident.satiety) / 55.0,
            "PLAY" to profile.playfulness * 2 + (100 - resident.funLevel) / 45.0,
            "TRAIN" to profile.training * 2,
            "SOCIALIZE" to profile.initiative * 2 + (100 - resident.social) / 45.0,
            "EXPLORE" to profile.curiosity * 2
        )
        var draw = SocialRandom.unit(seed, resident.individualId, "farm-activity", now / 20_000) * weights.values.sum()
        weights.forEach { (activity, weight) -> draw -= weight; if (draw < 0) return activity }
        return "EXPLORE"
    }

    fun available(resident: FarmResident, profile: DigimonSocialProfile, now: Long): Boolean =
        resident.energy >= 25 && resident.satiety >= 25 && resident.activity !in listOf("REST", "EAT") &&
            (resident.activity != "TRAIN" || now - resident.activityStartedAt >= profile.activityCommitmentMillis)

    fun partner(resident: FarmResident, others: List<FarmResident>, profile: DigimonSocialProfile,
        relationships: Map<String, FarmRelationship>, seed: Long, now: Long): FarmResident? {
        fun score(other: FarmResident): Double {
            val relationship = relationships[other.individualId]
            val familiar = (relationship?.familiarity ?: 0) / 100.0
            val affinity = (relationship?.affinity ?: 50) / 100.0
            val distance = hypot((other.positionX - resident.positionX).toDouble(), (other.positionY - resident.positionY).toDouble())
            val recent = if (now - (relationship?.lastInteractionAt ?: 0) < 120_000) .4 else 1.0
            return (.1 + familiar * profile.loyalty + (1 - familiar) * profile.curiosity + affinity * profile.warmth) *
                recent / (1 + distance / 500)
        }
        return others.filter { it.individualId != resident.individualId && it.energy >= 25 && it.satiety >= 25 &&
            it.activity !in listOf("REST", "EAT") }.minWithOrNull(compareBy<FarmResident> {
            -ln(SocialRandom.unit(seed, it.individualId + resident.individualId, "farm-partner", now / 20_000).coerceAtLeast(1e-12)) / score(it)
        }.thenBy { it.individualId })
    }
}
