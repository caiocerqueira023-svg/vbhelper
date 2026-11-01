package com.tamerslab.species

import org.junit.Assert.*
import org.junit.Test

class PartnerParticipationTest {
    private val art = DotSpriteSetDto("dot", -100, "Gallantmon", "Bandai", width = 48, height = 48,
        frames = mapOf("idle_1" to "frames/${"a".repeat(64)}.bin"))
    private val catalog = CanonicalSpeciesCatalog(CanonicalSpeciesDatabaseDto(species = listOf(
        CanonicalSpeciesDto("digimon.dukemon", "Dukemon", aliases = listOf("Gallantmon"),
            dotSprites = DotSpriteAssociationDto("dot", listOf("dot"))),
        CanonicalSpeciesDto("digimon.noart", "Noartmon"),
        CanonicalSpeciesDto("reference.medal", "Medal", kind = "OBJECT")), spriteSets = listOf(art)))

    private fun snapshot() = PartnerParticipationSnapshot.build(catalog,
        listOf(ParticipationCard(100, "Dukemon"), ParticipationCard(200, "Noartmon"),
            ParticipationCard(300, null), ParticipationCard(400, "Medal")),
        mapOf(1L to ("knight" to 100L), 2L to ("missing" to 200L)),
        mapOf("knight" to 100L, "missing" to 200L, "wild" to 200L),
        mapOf("knight" to "Noartmon"))

    @Test fun regionalNamesAndCanonicalIdsShareLoadedSpeciesWithoutCollapsingForms() {
        val index = SpeciesAvailabilityIndex(catalog, setOf("Gallantmon"))
        assertTrue(index.eligibilityFor("Dukemon").usable)
        assertTrue(index.eligibilityFor("digimon.dukemon").usable)
        assertFalse(index.eligibilityFor("Dukemon Crimson Mode").usable)
    }

    @Test fun currentFormWinsOverTheOldIndividualSnapshot() {
        assertTrue(snapshot().individual("knight").usable)
        assertTrue(snapshot().owned(1).usable)
        assertTrue(snapshot().individual("missing").usable)
    }

    @Test fun storedDefinitionWithoutCatalogIdentificationRemainsUsable() {
        assertTrue(snapshot().card(300).usable)
        assertFalse(snapshot().card(999).usable)
    }

    @Test fun everyStoredPartnerRemainsSelectableIncludingMissingArt() {
        val stored = listOf(1L, 2L)
        val selected = stored.filter { snapshot().owned(it).usable }
        assertEquals(listOf(1L, 2L), selected)
        assertEquals(listOf(1L, 2L), stored)
        assertTrue(snapshot().owned(2).usable)
    }

    @Test fun renamedTemplateUsesItsCanonicalReference() {
        val renamed = PartnerParticipationSnapshot.build(catalog,
            listOf(ParticipationCard(100, "My Knightmon", originalName = "dukemon")), emptyMap(), emptyMap(), emptyMap())
        assertTrue(renamed.card(100).usable)
    }

    @Test fun staleOwnedFormAndWrongIndividualAreRejectedBeforeBattleEntry() {
        val state = snapshot()
        state.requireOwned(1, "knight", 100)
        state.requireOwned(999, "knight", 100) // The same individual after a watch round-trip.
        assertThrows(PartnerUnavailableException::class.java) { state.requireOwned(1, "other", 100) }
        assertThrows(PartnerUnavailableException::class.java) { state.requireOwned(1, "knight", 200) }
        state.requireOwned(2)
        assertThrows(PartnerUnavailableException::class.java) { state.requireOwned(999) }
    }

    @Test fun cardlessBindingUsesCanonicalIdentityAndLoadedSpeciesAcrossOrigins() {
        val state = PartnerParticipationSnapshot.build(catalog,
            listOf(ParticipationCard(100, "Gallantmon")), emptyMap(), emptyMap(), mapOf("imported" to "digimon.dukemon"))
        assertTrue(state.individual("imported").usable)
        assertFalse(state.individual("missing").usable)
    }
}
