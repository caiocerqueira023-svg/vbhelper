package com.github.nacabaro.vbhelper.battle.offline

import com.github.cfogrady.vbnfc.data.NfcCharacter
import com.github.nacabaro.vbhelper.battle.offline.core.BattleAttribute
import com.github.nacabaro.vbhelper.battle.offline.data.BlastEvolutionData
import com.github.nacabaro.vbhelper.battle.offline.data.DexJogressResolver
import com.github.nacabaro.vbhelper.battle.offline.data.BlastEvolutionRepository
import com.github.nacabaro.vbhelper.battle.offline.data.BlastJogressEntry
import com.github.nacabaro.vbhelper.battle.offline.data.BlastSoloForm
import com.github.nacabaro.vbhelper.screens.offlineBattle.JogressOption
import com.github.nacabaro.vbhelper.screens.offlineBattle.mergeJogressOptions
import org.junit.Assert.*
import org.junit.Test

class BlastEvolutionTableTest {
    private fun data() = BlastEvolutionData(
        version = 1,
        soloForms = listOf(
            BlastSoloForm("WarGreymon", "WarGreymon X", "X_ANTIBODY"),
            BlastSoloForm("ShineGreymon", "ShineGreymon Burst Mode", "BURST_MODE", special = "Glorious Burst")
        ),
        jogress = listOf(
            BlastJogressEntry("WarGreymon", "MetalGarurumon", "Omegamon", resultSpecial = "Garuru Cannon"),
            BlastJogressEntry("XV-mon", "Stingmon", "Paildramon", resultSpecial = "Desperado Blaster")
        ),
        aliases = mapOf("exveemon" to "XV-mon")
    )

    @Test fun soloFormsMatchCaseInsensitively() {
        val forms = BlastEvolutionRepository.soloFormsFor(data(), "wargreymon")
        assertEquals(listOf("WarGreymon X"), forms.map { it.target })
        assertTrue(BlastEvolutionRepository.soloFormsFor(data(), null).isEmpty())
        assertTrue(BlastEvolutionRepository.soloFormsFor(data(), "Agumon").isEmpty())
    }

    @Test fun jogressMatchesEitherComponentWithAliases() {
        val byDub = BlastEvolutionRepository.jogressFor(data(), "ExVeemon")
        assertEquals(listOf("Paildramon"), byDub.map { it.result })
        val byPartner = BlastEvolutionRepository.jogressFor(data(), "MetalGarurumon")
        assertEquals(listOf("Omegamon"), byPartner.map { it.result })
    }

    @Test fun leadResolutionDependsOnWhoLeads() {        val table = data()
        val warLead = BlastEvolutionRepository.resolveLeadJogress(table, "WarGreymon", "MetalGarurumon", "Omegamon")
        assertEquals("Omegamon", warLead?.result)
        // Same pair, but the lead equipped nothing jogress-compatible.
        assertNull(BlastEvolutionRepository.resolveLeadJogress(table, "MetalGarurumon", "WarGreymon", null))
        assertNull(BlastEvolutionRepository.resolveLeadJogress(table, "WarGreymon", "Agumon", "Omegamon"))
        assertNull(BlastEvolutionRepository.resolveLeadJogress(table, "WarGreymon", "MetalGarurumon", "Paildramon"))
    }

    @Test fun presenceGateKeepsOnlyLoadedSpecies() {
        val table = data()
        val present = BlastEvolutionRepository.presentSet(table, listOf("WarGreymon X", "Omegamon", "MetalGarurumon", null, ""))
        assertTrue(BlastEvolutionRepository.isPresent(table, present, "omegamon"))
        assertFalse(BlastEvolutionRepository.isPresent(table, present, "Paildramon"))
        assertFalse(BlastEvolutionRepository.isPresent(table, present, null))
        // WarGreymon X present, Burst form target absent.
        val forms = BlastEvolutionRepository.availableForms(table, "WarGreymon", present)
        assertEquals(listOf("WarGreymon X"), forms.map { it.target })
        // Omegamon + partner MetalGarurumon both present.
        val jogress = BlastEvolutionRepository.availableJogress(table, "WarGreymon", present)
        assertEquals(listOf("Omegamon"), jogress.map { it.result })
        // Result present but partner missing: option hidden.
        val noPartner = BlastEvolutionRepository.availableJogress(table, "WarGreymon",
            BlastEvolutionRepository.presentSet(table, listOf("Omegamon")))
        assertTrue(noPartner.isEmpty())
        // Alias-aware: ExVeemon matches XV-mon entry, result gated the same way.
        val aliased = BlastEvolutionRepository.availableJogress(table, "ExVeemon",
            BlastEvolutionRepository.presentSet(table, listOf("Paildramon", "Stingmon")))
        assertEquals(listOf("Paildramon"), aliased.map { it.result })
    }

    @Test fun mergePrefersUniversalAndDedupesDexResults() {        val merged = mergeJogressOptions(
            universal = listOf(JogressOption("Omegamon", "MetalGarurumon")),
            dex = listOf(
                JogressOption("Omegamon", "Black War Greymon", fromDex = true),
                JogressOption("Chaosmon", "Darkdramon", fromDex = true)
            )
        )
        assertEquals(listOf("Omegamon", "Chaosmon"), merged.map { it.result })
        assertFalse(merged.first { it.result == "Omegamon" }.fromDex)
        assertTrue(merged.first { it.result == "Chaosmon" }.fromDex)
    }

    @Test fun nfcAttributesMapToBattleAttributes() {
        assertEquals(BattleAttribute.VIRUS, DexJogressResolver.nfcToBattleAttribute(NfcCharacter.Attribute.Virus))
        assertEquals(BattleAttribute.DATA, DexJogressResolver.nfcToBattleAttribute(NfcCharacter.Attribute.Data))
        assertEquals(BattleAttribute.VACCINE, DexJogressResolver.nfcToBattleAttribute(NfcCharacter.Attribute.Vaccine))
        assertEquals(BattleAttribute.FREE, DexJogressResolver.nfcToBattleAttribute(NfcCharacter.Attribute.Free))
        assertEquals(BattleAttribute.NONE, DexJogressResolver.nfcToBattleAttribute(NfcCharacter.Attribute.None))
    }
}
