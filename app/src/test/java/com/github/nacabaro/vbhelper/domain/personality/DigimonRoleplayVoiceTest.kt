package com.github.nacabaro.vbhelper.domain.personality

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DigimonRoleplayVoiceTest {
    @Test
    fun `voice fingerprint is stable for the same individual`() {
        val first = DigimonRoleplayVoice.create("same-individual", DigimonPersonalityType.BRAVE, 3)
        val second = DigimonRoleplayVoice.create("same-individual", DigimonPersonalityType.BRAVE, 3)

        assertEquals(first, second)
        assertEquals(first.promptInstruction("pt-BR"), second.promptInstruction("pt-BR"))
    }

    @Test
    fun `different individuals receive a broad set of voice fingerprints`() {
        val voices = (0 until 128).map { index ->
            DigimonRoleplayVoice.create("individual-$index", DigimonPersonalityType.FRIENDLY, 3)
        }.toSet()

        assertTrue(voices.size >= 8)
    }

    @Test
    fun `personality and stage can change an otherwise identical voice`() {
        val base = DigimonRoleplayVoice.create("stable-id", DigimonPersonalityType.FRIENDLY, 2)
        val candidates = listOf(
            DigimonRoleplayVoice.create("stable-id", DigimonPersonalityType.SLY, 2),
            DigimonRoleplayVoice.create("stable-id", DigimonPersonalityType.FRIENDLY, 5)
        )

        assertTrue(candidates.any { it != base })
    }

    @Test
    fun `voice instruction is localized and explicitly avoids generic assistant voice`() {
        val voice = DigimonRoleplayVoice.create("localized", DigimonPersonalityType.SOCIABLE, 4)

        assertTrue(voice.promptInstruction("en").contains("Avoid generic assistant phrasing"))
        assertTrue(voice.promptInstruction("pt-BR").contains("Não soe como um assistente genérico"))
        assertNotEquals(voice.promptInstruction("en"), voice.promptInstruction("ja"))
    }
}
