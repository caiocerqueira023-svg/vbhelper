package com.github.nacabaro.vbhelper.domain.personality

import com.github.cfogrady.vbnfc.data.NfcCharacter
import kotlin.random.Random

object DigimonPersonalityGenerator {
    fun generate(
        individualId: String,
        attribute: NfcCharacter.Attribute?,
        stage: Int,
        now: Long = System.currentTimeMillis(),
        random: Random = Random.Default
    ): DigimonPersonalityTraits {
        val temperamentBoosts = mutableMapOf<Temperament, Int>()
        when (attribute) {
            NfcCharacter.Attribute.Vaccine -> temperamentBoosts[Temperament.CALM] = 1
            NfcCharacter.Attribute.Virus -> temperamentBoosts[Temperament.TEMPERAMENTAL] = 1
            else -> Unit
        }
        if (stage >= 4) {
            temperamentBoosts[Temperament.DREAMY] = -1
        }

        return DigimonPersonalityTraits(
            individualId = individualId,
            temperament = weightedTrait(
                Temperament.entries.toList(),
                temperamentBoosts,
                random
            ),
            socialStyle = weightedTrait(
                SocialStyle.entries.toList(),
                when (attribute) {
                    NfcCharacter.Attribute.Vaccine -> mapOf(SocialStyle.FORMAL_POLITE to 1)
                    NfcCharacter.Attribute.Virus -> mapOf(SocialStyle.TOUGH_RUSTIC to 1)
                    NfcCharacter.Attribute.Data -> mapOf(SocialStyle.CURIOUS_TALKATIVE to 1)
                    else -> emptyMap()
                },
                random
            ),
            speechQuirk = weightedTrait(
                SpeechQuirk.entries.toList(),
                if (stage >= 4) {
                    mapOf(SpeechQuirk.PHILOSOPHICAL to 1)
                } else {
                    emptyMap()
                },
                random
            ),
            generatedAt = now
        )
    }

    private fun <T> weightedTrait(
        options: List<T>,
        boosts: Map<T, Int>,
        random: Random
    ): T {
        val weightedOptions = options.flatMap { option ->
            List((1 + boosts.getOrDefault(option, 0)).coerceAtLeast(0)) { option }
        }
        return weightedOptions[random.nextInt(weightedOptions.size)]
    }
}
