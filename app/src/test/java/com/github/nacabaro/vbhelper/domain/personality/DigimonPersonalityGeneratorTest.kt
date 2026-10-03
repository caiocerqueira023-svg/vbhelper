package com.github.nacabaro.vbhelper.domain.personality

import com.github.cfogrady.vbnfc.data.NfcCharacter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class DigimonPersonalityGeneratorTest {
    @Test
    fun `personality generation is deterministic for a seeded random source`() {
        val first = DigimonPersonalityGenerator.generate(
            individualId = "same",
            attribute = NfcCharacter.Attribute.Data,
            stage = 3,
            now = 1L,
            random = Random(2026)
        )
        val second = DigimonPersonalityGenerator.generate(
            individualId = "same",
            attribute = NfcCharacter.Attribute.Data,
            stage = 3,
            now = 1L,
            random = Random(2026)
        )

        assertEquals(first, second)
        assertEquals(CURRENT_PERSONALITY_SYSTEM_VERSION, first.systemVersion)
        assertFalse(
            DigimonPersonalityTraits(
                individualId = "old",
                personalityType = DigimonPersonalityType.FRIENDLY,
                generatedAt = 1L,
                systemVersion = 2
            ).isCurrentSystem
        )
    }

    @Test
    fun `attribute biases new personalities without eliminating other types`() {
        val vaccinePreferred = setOf(
            DigimonPersonalityType.BRAVE,
            DigimonPersonalityType.DEVOTED,
            DigimonPersonalityType.OVERPROTECTIVE,
            DigimonPersonalityType.COMPASSIONATE,
            DigimonPersonalityType.FRIENDLY
        )
        val generated = (0 until 400).map { seed ->
            DigimonPersonalityGenerator.generate(
                individualId = "vaccine-$seed",
                attribute = NfcCharacter.Attribute.Vaccine,
                stage = 3,
                random = Random(seed)
            ).personalityType
        }

        assertTrue(generated.count { it in vaccinePreferred } >= 150)
        assertTrue(generated.any { it !in vaccinePreferred })
    }

    @Test
    fun `personality behavior instructions are available in Japanese`() {
        val english = DigimonPersonalityType.RECKLESS.promptInstruction("en")
        val japanese = DigimonPersonalityType.RECKLESS.promptInstruction("ja")

        assertNotEquals(english, japanese)
        assertTrue(japanese.any { it.code in 0x3000..0x9FFF })
    }
}
