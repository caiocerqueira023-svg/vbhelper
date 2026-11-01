package com.tamerslab.species

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeciesArtworkPolicyTest {
    @Test fun dotFirstUsesDotWhenBothSourcesExist() {
        assertEquals(SpeciesArtworkSource.DOT, SpeciesArtworkPolicy.choose(SpritePreference.DOT_FIRST, true, true, true, true))
    }

    @Test fun vbFirstUsesLoadedChoiceThenNativeArtThenVbAssetBeforeDotFallback() {
        assertEquals(SpeciesArtworkSource.VB_CHOICE, SpeciesArtworkPolicy.choose(SpritePreference.VB_FIRST, true, true, true, true))
        assertEquals(SpeciesArtworkSource.VB_IMPORTED, SpeciesArtworkPolicy.choose(SpritePreference.VB_FIRST, true, false, true, true))
        assertEquals(SpeciesArtworkSource.VB_ASSET, SpeciesArtworkPolicy.choose(SpritePreference.VB_FIRST, true, false, false, true))
        assertEquals(SpeciesArtworkSource.DOT, SpeciesArtworkPolicy.choose(SpritePreference.VB_FIRST, true, false, false, false))
    }

    @Test fun dotFirstFallsBackToTheUsersLoadedVbChoiceWhenDotIsMissing() {
        assertEquals(SpeciesArtworkSource.VB_CHOICE, SpeciesArtworkPolicy.choose(SpritePreference.DOT_FIRST, false, true, true, true))
        assertEquals(SpeciesArtworkSource.VB_IMPORTED, SpeciesArtworkPolicy.choose(SpritePreference.DOT_FIRST, false, false, true, true))
        assertEquals(SpeciesArtworkSource.VB_ASSET, SpeciesArtworkPolicy.choose(SpritePreference.DOT_FIRST, false, false, false, true))
    }

    @Test fun missingBothSourcesNeedsAPlaceholderForEitherPreference() {
        SpritePreference.entries.forEach { preference ->
            assertEquals(SpeciesArtworkSource.NONE, SpeciesArtworkPolicy.choose(preference, false, false, false, false))
        }
    }
}
