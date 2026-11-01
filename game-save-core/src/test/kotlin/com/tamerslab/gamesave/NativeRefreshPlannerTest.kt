package com.tamerslab.gamesave

import org.junit.Assert.*
import org.junit.Test

class NativeRefreshPlannerTest {
    private val app = RefreshAppValues(null, "BRAVE", 3, 10)
    @Test fun sourceOnlyPersonalityChangeRecommendsSourceWithoutInventingANickname() {
        val review = NativeRefreshPlanner.fields("Old native", "New native", "BRAVE", "FRIENDLY", app)
        assertFalse(review.nameConflict)
        assertFalse(review.personalityConflict)
        assertEquals(RefreshChoice.KEEP_APP, review.nameRecommendation)
        assertEquals(RefreshChoice.USE_SOURCE, review.personalityRecommendation)
        val resolved = NativeRefreshPlanner.resolve(review, review.recommendedChoices(), "New native", "FRIENDLY", app, 20)
        assertNull(resolved.nickname)
        assertEquals("FRIENDLY", resolved.personality)
        assertEquals(20, resolved.personalityUpdatedAt)
    }
    @Test fun bothSidesChangingDifferentlyRequiresExplicitResolution() {
        val current = app.copy(nickname = "App name", personality = "STRATEGIC")
        val review = NativeRefreshPlanner.fields("Old", "Game name", "BRAVE", "FRIENDLY", current)
        assertTrue(review.nameConflict)
        assertTrue(review.personalityConflict)
        assertThrows(IllegalArgumentException::class.java) {
            NativeRefreshPlanner.resolve(review, review.recommendedChoices(), "Game name", "FRIENDLY", current, 20)
        }
        val kept = NativeRefreshPlanner.resolve(review, RefreshChoices(RefreshChoice.KEEP_APP, RefreshChoice.KEEP_APP), "Game name", "FRIENDLY", current, 20)
        assertEquals(current, kept)
        val imported = NativeRefreshPlanner.resolve(review, RefreshChoices(RefreshChoice.USE_SOURCE, RefreshChoice.USE_SOURCE), "Game name", "FRIENDLY", current, 20)
        assertEquals("Game name", imported.nickname)
        assertEquals("FRIENDLY", imported.personality)
    }
    @Test fun appOnlyChangesArePreservedAndUnchangedNativeValuesDoNotResetTimestamps() {
        val current = app.copy(nickname = "Mine", personality = "STRATEGIC")
        val review = NativeRefreshPlanner.fields("Old", "Old", "BRAVE", "BRAVE", current)
        assertEquals(RefreshChoice.KEEP_APP, review.personalityRecommendation)
        assertEquals(current, NativeRefreshPlanner.resolve(review, review.recommendedChoices(), "Old", "BRAVE", current, 20))
    }
    @Test fun convergentPersonalityChangesAreNotConflicts() {
        val current = app.copy(personality = "FRIENDLY")
        val review = NativeRefreshPlanner.fields("Old", "Old", "BRAVE", "FRIENDLY", current)
        assertFalse(review.personalityConflict)
        assertEquals(current, NativeRefreshPlanner.resolve(review, RefreshChoices(RefreshChoice.KEEP_APP, RefreshChoice.USE_SOURCE), "Old", "FRIENDLY", current, 20))
    }
    @Test fun emptySourceNameCanOnlyClearNicknameThroughAnExplicitChoice() {
        val current = app.copy(nickname = "Mine")
        val review = NativeRefreshPlanner.fields("Old", "", "BRAVE", "BRAVE", current)
        val kept = NativeRefreshPlanner.resolve(review, RefreshChoices(RefreshChoice.KEEP_APP, RefreshChoice.KEEP_APP), "", "BRAVE", current, 20)
        assertEquals("Mine", kept.nickname)
        assertNull(NativeRefreshPlanner.resolve(review, RefreshChoices(RefreshChoice.USE_SOURCE, RefreshChoice.KEEP_APP), "", "BRAVE", current, 20).nickname)
    }
}
