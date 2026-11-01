package com.tamerslab.transfer.data.gameSave

import com.tamerslab.species.*
import org.junit.Assert.*
import org.junit.Test

class NativeCanonicalMappingPolicyTest {
    private val catalog = CanonicalSpeciesCatalog(CanonicalSpeciesDatabaseDto(species = listOf(
        CanonicalSpeciesDto("digimon.agumon", "Agumon", aliases = listOf("Nickname")),
        CanonicalSpeciesDto("digimon.agumon.black", "Agumon (Black)"),
        CanonicalSpeciesDto("other", "Other", kind = "OTHER"))))

    @Test fun acceptsExactCanonicalDigimonEvenWithoutArtwork() {
        assertEquals("digimon.agumon", NativeCanonicalMappingPolicy.target(catalog, "digimon.agumon").id)
        assertFalse(catalog.isExpandedReady("digimon.agumon"))
    }
    @Test fun doesNotPromoteNamesNicknamesOrAliasesIntoMappings() {
        for (value in listOf("Agumon", "Nickname", "missing")) {
            assertThrows(IllegalArgumentException::class.java) { NativeCanonicalMappingPolicy.target(catalog, value) }
        }
    }
    @Test fun keepsFormsDistinct() {
        assertEquals("digimon.agumon.black", NativeCanonicalMappingPolicy.target(catalog, "digimon.agumon.black").id)
    }
    @Test fun nonDigimonReferencesCannotBecomeGameplayPartners() {
        assertThrows(IllegalArgumentException::class.java) { NativeCanonicalMappingPolicy.target(catalog, "other") }
    }
}
