package com.github.nacabaro.vbhelper.screens.offlineBattle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FinisherAttackProfileTest {
    @Test
    fun terraForceAndGaiaForceShareTheProjectileProfile() {
        assertAliases(FinisherAttackStyle.PROJECTILE, "Terra Force", "Gaia Force")
    }

    @Test
    fun omnimonSwordAliasesUseMeleeAndCannonAliasesUseBeam() {
        assertAliases(FinisherAttackStyle.MELEE, "Grey Sword", "Transcendent Sword")
        assertAliases(FinisherAttackStyle.BEAM, "Garuru Cannon", "Supreme Cannon")
    }

    @Test
    fun paildramonStrikeAliasesUseMeleeAndBlasterAliasesUseBarrage() {
        assertAliases(FinisherAttackStyle.MELEE, "Sting Strike", "Spiking Strike")
        assertAliases(FinisherAttackStyle.BARRAGE, "Desperado Blaster", "Death Parade Blaster")
    }

    @Test
    fun exactAliasesIgnoreCaseSpacingAndPunctuation() {
        assertEquals(resolveFinisherAttackProfile("Terra Force"), resolveFinisherAttackProfile("  TERRA-force!  "))
        assertEquals(resolveFinisherAttackProfile("Grey Sword"), resolveFinisherAttackProfile("grey_sword"))
        assertEquals(resolveFinisherAttackProfile("Garuru Cannon"), resolveFinisherAttackProfile("GARURU\tCANNON"))
        assertEquals(resolveFinisherAttackProfile("Death Parade Blaster"), resolveFinisherAttackProfile("death.parade.blaster"))
    }

    @Test
    fun unknownOrMissingSpecialsUseTheSameNeutralProjectile() {
        val neutral = resolveFinisherAttackProfile(null)
        assertEquals(FinisherAttackStyle.PROJECTILE, neutral.style)
        assertEquals(0xFFE0E0E0.toInt(), neutral.colorArgb)
        for (name in listOf("", "  ", "!!!", "Unknown Move", "Omnimon", "Paildramon", "WarGreymon")) {
            assertEquals("No style should be guessed for $name", neutral, resolveFinisherAttackProfile(name))
        }
    }

    @Test
    fun partialMatchesAndUnverifiedSwordCannonAndBlasterNamesStayNeutral() {
        val neutral = resolveFinisherAttackProfile(null)
        for (name in listOf("Mega Terra Force", "Grey Sword X", "Supreme Cannonball", "Sword", "Cannon", "Blaster")) {
            assertEquals("Only exact verified aliases should match: $name", neutral, resolveFinisherAttackProfile(name))
        }
    }

    @Test
    fun metadataConstructorKeepsDefaultStyleAndEmitterHeight() {
        val profile = FinisherAttackProfile(colorArgb = 0xFF123456.toInt())
        assertEquals(FinisherAttackStyle.PROJECTILE, profile.style)
        assertEquals(0.7f, profile.emitterHeight, 0f)
        assertEquals(0xFF123456.toInt(), profile.colorArgb)
    }

    @Test
    fun existingFighterConstructorsDefaultToNeutralProfileAndNoTransition() {
        val fighter = BattleFighterPresentation(
            combatantId = "ally:a", externalCharacterId = "a", displayName = "Source",
            modelGlb = ByteArray(0), poseNames = emptySet(), setKey = "base",
            attackVisuals = emptyMap(), impactModels = emptyMap(),
        )
        assertEquals(resolveFinisherAttackProfile(null), fighter.attackProfile)
        assertNull(fighter.transitionGlb)
    }

    @Test
    fun formAndJogressTargetsAreBothIncludedWhenEquipped() {
        assertEquals(listOf("WarGreymon X", "Omnimon"),
            equippedFinisherResultSpecies("FORM", "WarGreymon X", "Omnimon"))
    }

    @Test
    fun powerOrNoneIgnoresAStaleFormTargetButKeepsJogress() {
        for (mode in listOf("POWER", "NONE")) {
            assertEquals(listOf("Omnimon"), equippedFinisherResultSpecies(mode, "WarGreymon X", "Omnimon"))
        }
        assertEquals(emptyList<String>(), equippedFinisherResultSpecies("FORM", "  ", null))
        assertEquals(listOf("WarGreymon X"), equippedFinisherResultSpecies("FORM", "WarGreymon X", ""))
    }

    private fun assertAliases(style: FinisherAttackStyle, first: String, second: String) {
        val profile = resolveFinisherAttackProfile(first)
        assertEquals(style, profile.style)
        assertEquals(profile, resolveFinisherAttackProfile(second))
        assertEquals(0.7f, profile.emitterHeight, 0f)
        assertEquals(0xFF, profile.colorArgb ushr 24)
    }
}
