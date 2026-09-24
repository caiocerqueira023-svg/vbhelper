package com.github.nacabaro.vbhelper.chat

import com.github.cfogrady.vbnfc.data.NfcCharacter
import com.github.nacabaro.vbhelper.dtos.CharacterDtos
import com.github.nacabaro.vbhelper.domain.personality.DigimonPersonalityTraits
import com.github.nacabaro.vbhelper.domain.personality.DigimonPersonalityType
import com.github.nacabaro.vbhelper.domain.personality.DigimonRoleplayVoice
import com.github.nacabaro.vbhelper.domain.species.SpeciesProfile
import com.github.nacabaro.vbhelper.domain.species.SpeciesSource
import com.github.nacabaro.vbhelper.species.SpeciesConversationEntry
import com.github.nacabaro.vbhelper.species.SpeciesConversationExchange
import com.github.nacabaro.vbhelper.utils.DeviceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DigimonPersonaBuilderTest {
    @Test
    fun `default prompt injects stable individual voice and source examples once`() {
        val voice = DigimonRoleplayVoice.create(
            "individual-a",
            DigimonPersonalityType.SLY,
            3,
            NfcCharacter.Attribute.Data.ordinal
        )
        val prompt = DigimonPersonaBuilder.buildSystemPrompt(
            character = character(speciesName = "Junomon"),
            cardName = "Test Card",
            speciesProfile = speciesProfile("Junomon"),
            personality = personality(DigimonPersonalityType.SLY),
            individualId = "individual-a",
            conversationExamples = listOf(
                SpeciesConversationEntry(
                    characterId = "JUNOMON",
                    speciesName = "Junomon",
                    opening = "Am I... the jealous type?",
                    exchanges = listOf(
                        SpeciesConversationExchange(
                            tamer = "Not at all. You're compassionate and kind.",
                            digimon = "I shall keep my jealousy in check..."
                        )
                    )
                )
            )
        )

        assertTrue(prompt.contains(voice.promptInstruction("en")))
        assertTrue(prompt.contains("Am I... the jealous type?"))
        assertTrue(prompt.contains("I shall keep my jealousy in check..."))
        assertTrue(prompt.contains("Avoid generic assistant phrasing"))
        assertFalse(prompt.contains("{roleplay_voice}"))
        assertEquals(1, Regex("Species conversation examples").findAll(prompt).count())
    }

    @Test
    fun `custom template receives voice block without duplicate append`() {
        val voice = DigimonRoleplayVoice.create(
            "individual-b",
            DigimonPersonalityType.DARING,
            3,
            NfcCharacter.Attribute.Data.ordinal
        )
        val prompt = DigimonPersonaBuilder.buildSystemPrompt(
            character = character(speciesName = "Agumon"),
            cardName = "Test Card",
            speciesProfile = speciesProfile("Agumon"),
            personality = personality(DigimonPersonalityType.DARING),
            individualId = "individual-b",
            promptTemplate = "Character voice: {roleplay_voice}"
        )

        assertEquals(1, Regex("Character voice").findAll(prompt).count())
        assertEquals(1, Regex("Voice fingerprint").findAll(prompt).count())
        assertTrue(prompt.contains(voice.promptInstruction("en")))
    }

    @Test
    fun `custom template without voice placeholder gets one localized supplemental block`() {
        val prompt = DigimonPersonaBuilder.buildSystemPrompt(
            character = character(speciesName = "Junomon"),
            cardName = "Test Card",
            speciesProfile = speciesProfile("Junomon"),
            personality = personality(DigimonPersonalityType.ASTUTE),
            individualId = "individual-c",
            languageTag = "pt-BR",
            promptTemplate = "Você é {digimon_name}."
        )

        assertEquals(1, Regex("Voz individual de personagem").findAll(prompt).count())
        assertTrue(prompt.contains("Não soe como um assistente genérico"))
    }

    @Test
    fun `default prompts no longer impose a tiny uniform answer`() {
        listOf("en", "pt-BR", "ja").forEach { languageTag ->
            val template = DigimonPersonaBuilder.DEFAULT_SYSTEM_PROMPT_TEMPLATE
            val prompt = if (languageTag == "pt-BR") {
                PromptLocalization.defaultSystemPrompt(languageTag)
            } else {
                PromptLocalization.defaultSystemPrompt(languageTag)
            }

            assertTrue(template.contains("{roleplay_voice}"))
            assertFalse(prompt.contains("10 to 60 words"))
            assertFalse(prompt.contains("10 a 60 palavras"))
            assertFalse(prompt.contains("10〜60語"))
            assertFalse(prompt.contains("a simple response is enough"))
            assertFalse(prompt.contains("resposta simples é suficiente"))
        }
    }

    private fun character(speciesName: String): CharacterDtos.CharacterWithSprites =
        CharacterDtos.CharacterWithSprites(
            id = 1,
            charId = 10,
            stage = 3,
            attribute = NfcCharacter.Attribute.Data,
            ageInDays = 100,
            mood = 60,
            vitalPoints = 3,
            transformationCountdown = 0,
            injuryStatus = NfcCharacter.InjuryStatus.None,
            trophies = 1,
            currentPhaseBattlesWon = 2,
            currentPhaseBattlesLost = 1,
            totalBattlesWon = 5,
            totalBattlesLost = 3,
            activityLevel = 4,
            heartRateCurrent = 80,
            characterType = DeviceType.VBDevice,
            spriteIdle = byteArrayOf(),
            spriteIdle2 = byteArrayOf(),
            spriteRun1 = byteArrayOf(),
            spriteRun2 = byteArrayOf(),
            spriteWidth = 1,
            spriteHeight = 1,
            nameSprite = byteArrayOf(),
            nameSpriteWidth = 0,
            nameSpriteHeight = 0,
            isBemCard = false,
            nickname = "Amy",
            speciesName = speciesName,
            isInAdventure = false,
            active = true,
            isFavorite = true
        )

    private fun speciesProfile(name: String) = SpeciesProfile(
        cardCharacterId = 10,
        speciesName = name,
        matchedName = name,
        level = "Adult",
        type = "Dragon",
        profileDescription = "A proud dragon Digimon.",
        specialMoves = listOf("Dragon Fire"),
        source = SpeciesSource.OFFICIAL_MATCHED
    )

    private fun personality(type: DigimonPersonalityType) = DigimonPersonalityTraits(
        individualId = "test",
        personalityType = type,
        generatedAt = 1L
    )
}
