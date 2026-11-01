package com.tamerslab.species

import org.junit.Assert.*
import org.junit.Test

class SpeciesEligibilityTest {
    private fun digimon(id: String, name: String, dot: Boolean, loaded: Boolean) =
        SpeciesAvailability(id, true, dot, loaded)

    @Test fun loadedSpeciesRemainsUsableRegardlessOfOrigin() {
        val result = SpeciesEligibility.evaluate(digimon("digimon.agumon", "Agumon", true, true))
        assertEquals(Eligibility(true, EligibilityReason.USABLE), result)
    }

    @Test fun digimonWithoutALoadedCardIsUsable() {
        assertTrue(SpeciesEligibility.evaluate(digimon("digimon.new", "Newmon", true, false)).usable)
    }

    @Test fun missingArtAndCardCannotDisableAKnownDigimon() {
        assertTrue(SpeciesEligibility.evaluate(digimon("digimon.x", "Xmon", false, false)).usable)
    }

    @Test fun referenceObjectsDoNotBecomeDigimon() {
        val reference = SpeciesAvailability("reference.medal", isDigimon = false, hasBundledDot = true, loadedInVb = true)
        assertEquals(EligibilityReason.NOT_A_DIGIMON, SpeciesEligibility.evaluate(reference).reason)
    }

    @Test fun unknownSpeciesUsesTheSharedUnavailableReason() {
        assertFalse(SpeciesEligibility.unavailable().usable)
    }
}

class SpeciesAvailabilityIndexTest {
    private fun catalog(): CanonicalSpeciesCatalog {
        val agumon = CanonicalSpeciesDto("digimon.agumon", "Agumon", level = "Child", kind = "DIGIMON",
            dotSprites = DotSpriteAssociationDto("dot-agumon", listOf("dot-agumon")))
        val missingArt = CanonicalSpeciesDto("digimon.noart", "Noartmon", level = "Child", kind = "DIGIMON")
        val medal = CanonicalSpeciesDto("reference.medal", "Medal", kind = "OBJECT",
            dotSprites = DotSpriteAssociationDto(null, emptyList()))
        val amalgam = CanonicalSpeciesDto("digimon.gallantmon", "Gallantmon", kind = "DIGIMON",
            aliases = listOf("Dukemon"), dotSprites = DotSpriteAssociationDto("dot-gallantmon", listOf("dot-gallantmon")))
        val sets = listOf(
            DotSpriteSetDto("dot-agumon", -100, "Agumon", "Bandai", width = 48, height = 48,
                frames = mapOf("idle_1" to "frames/${"a".repeat(64)}.bin")),
            DotSpriteSetDto("dot-gallantmon", -101, "Gallantmon", "Bandai", width = 48, height = 48,
                frames = mapOf("idle_1" to "frames/${"b".repeat(64)}.bin")),
        )
        return CanonicalSpeciesCatalog(CanonicalSpeciesDatabaseDto(1, species = listOf(agumon, missingArt, medal, amalgam), spriteSets = sets))
    }

    @Test fun mixedCatalogAndLoadedNamesResolveToOneAvailability() {
        val index = SpeciesAvailabilityIndex(catalog(), loadedVbSpeciesNames = setOf("Agumon", "Dukemon", "Mysterymon"))

        assertTrue(index.eligibilityFor("Agumon").usable)
        // Regional alias resolves to the canonical identity and its bundled art.
        assertEquals(EligibilityReason.USABLE, index.eligibilityFor("Dukemon").reason)
        assertTrue(index.eligibilityFor("Mysterymon").usable)
        assertTrue(index.eligibilityFor("Noartmon").usable)
        assertEquals(EligibilityReason.NOT_A_DIGIMON, index.eligibilityFor("Medal").reason)
        // Blank input never crashes or becomes usable.
        assertEquals(SpeciesAvailability.NONE, index.availabilityFor("  "))
        assertEquals(SpeciesAvailability.NONE, index.availabilityFor(null))
    }
}
