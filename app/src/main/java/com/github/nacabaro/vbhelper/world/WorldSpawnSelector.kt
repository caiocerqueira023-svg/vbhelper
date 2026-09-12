package com.github.nacabaro.vbhelper.world

import com.github.nacabaro.vbhelper.domain.card.CardCharacter
import java.util.Locale
import kotlin.random.Random

/**
 * Selects a wild Digimon in two independent steps:
 *
 * 1. Select an available stage according to [STAGE_WEIGHTS].
 * 2. Select one species uniformly within that stage, then one of its imported
 *    card variants uniformly.
 *
 * An official/manual species name, when available, is the canonical identity.
 * Otherwise the name sprite stored on the card is used as a deterministic
 * fallback. The stage is deliberately part of the key: a species in different
 * stages is a different spawn candidate.
 */
internal object WorldSpawnSelector {
    private val STAGE_WEIGHTS = listOf(
        0 to 0.15,
        1 to 0.15,
        2 to 0.40,
        3 to 0.24,
        4 to 0.05,
        5 to 0.01
    )

    fun selectCharacter(
        characters: List<CardCharacter>,
        speciesNames: Map<Long, String?> = emptyMap(),
        random: Random = Random.Default
    ): CardCharacter? {
        val speciesByStage = characters
            .groupBy { it.stage }
            .mapValues { (_, variants) ->
                variants.groupBy { it.speciesKey(speciesNames[it.id]) }.values.toList()
            }

        val availableStages = STAGE_WEIGHTS.filter { (stage, _) -> speciesByStage[stage]?.isNotEmpty() == true }
        if (availableStages.isEmpty()) return null

        val stage = pickWeightedStage(availableStages, random)
        val variants = speciesByStage.getValue(stage).random(random)
        return variants.random(random)
    }

    private fun pickWeightedStage(stages: List<Pair<Int, Double>>, random: Random): Int {
        val roll = random.nextDouble() * stages.sumOf { it.second }
        var cumulative = 0.0
        for ((stage, weight) in stages) {
            cumulative += weight
            if (roll < cumulative) return stage
        }
        return stages.last().first
    }

    private fun CardCharacter.speciesKey(speciesName: String?) = SpeciesKey(
        stage = stage,
        speciesName = speciesName?.trim()?.takeIf { it.isNotEmpty() }?.lowercase(Locale.ROOT),
        nameSprite = nameSprite,
        nameWidth = nameWidth,
        nameHeight = nameHeight
    )

    private class SpeciesKey(
        private val stage: Int,
        private val speciesName: String?,
        private val nameSprite: ByteArray,
        private val nameWidth: Int,
        private val nameHeight: Int
    ) {
        override fun equals(other: Any?): Boolean = other is SpeciesKey &&
            stage == other.stage &&
            speciesName == other.speciesName &&
            (speciesName != null || (
                nameWidth == other.nameWidth &&
                    nameHeight == other.nameHeight &&
                    nameSprite.contentEquals(other.nameSprite)
                ))

        override fun hashCode(): Int = if (speciesName != null) {
            31 * stage + speciesName.hashCode()
        } else {
            (((stage * 31) + nameWidth) * 31 + nameHeight) * 31 + nameSprite.contentHashCode()
        }
    }
}
