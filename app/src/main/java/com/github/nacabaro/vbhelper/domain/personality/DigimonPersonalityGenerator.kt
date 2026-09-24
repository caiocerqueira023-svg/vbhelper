package com.github.nacabaro.vbhelper.domain.personality

import com.github.cfogrady.vbnfc.data.NfcCharacter
import kotlin.random.Random

object DigimonPersonalityGenerator {
    private val attributePreferences = mapOf(
        NfcCharacter.Attribute.Virus to setOf(
            DigimonPersonalityType.SLY,
            DigimonPersonalityType.RECKLESS,
            DigimonPersonalityType.ASTUTE,
            DigimonPersonalityType.ZEALOUS,
            DigimonPersonalityType.DARING
        ),
        NfcCharacter.Attribute.Data to setOf(
            DigimonPersonalityType.STRATEGIC,
            DigimonPersonalityType.ENLIGHTENED,
            DigimonPersonalityType.ASTUTE,
            DigimonPersonalityType.OPPORTUNISTIC,
            DigimonPersonalityType.SOCIABLE
        ),
        NfcCharacter.Attribute.Vaccine to setOf(
            DigimonPersonalityType.BRAVE,
            DigimonPersonalityType.DEVOTED,
            DigimonPersonalityType.OVERPROTECTIVE,
            DigimonPersonalityType.COMPASSIONATE,
            DigimonPersonalityType.FRIENDLY
        ),
        NfcCharacter.Attribute.Free to setOf(
            DigimonPersonalityType.DARING,
            DigimonPersonalityType.SOCIABLE,
            DigimonPersonalityType.TOLERANT,
            DigimonPersonalityType.SLY,
            DigimonPersonalityType.FRIENDLY
        )
    )

    private val earlyStagePreferences = setOf(
        DigimonPersonalityType.ADORING,
        DigimonPersonalityType.FRIENDLY,
        DigimonPersonalityType.COMPASSIONATE,
        DigimonPersonalityType.TOLERANT,
        DigimonPersonalityType.SOCIABLE
    )

    private val matureStagePreferences = setOf(
        DigimonPersonalityType.ENLIGHTENED,
        DigimonPersonalityType.STRATEGIC,
        DigimonPersonalityType.SLY,
        DigimonPersonalityType.OPPORTUNISTIC,
        DigimonPersonalityType.BRAVE
    )

    fun generate(
        individualId: String,
        attribute: NfcCharacter.Attribute?,
        stage: Int,
        now: Long = System.currentTimeMillis(),
        random: Random = Random.Default
    ): DigimonPersonalityTraits {
        val attributeMatches = attributePreferences[attribute].orEmpty()
        val stageMatches = when (stage.coerceIn(0, 5)) {
            0, 1 -> earlyStagePreferences
            4, 5 -> matureStagePreferences
            else -> emptySet()
        }
        val weightedTypes = DigimonPersonalityType.entries.map { type ->
            val weight = 1 +
                (if (type in attributeMatches) 2 else 0) +
                (if (type in stageMatches) 1 else 0)
            type to weight
        }
        val totalWeight = weightedTypes.sumOf { it.second }
        var draw = random.nextInt(totalWeight)
        val type = weightedTypes.first { (candidate, weight) ->
            draw -= weight
            draw < 0
        }.first
        return DigimonPersonalityTraits(
            individualId = individualId,
            personalityType = type,
            generatedAt = now,
            systemVersion = CURRENT_PERSONALITY_SYSTEM_VERSION
        )
    }
}
