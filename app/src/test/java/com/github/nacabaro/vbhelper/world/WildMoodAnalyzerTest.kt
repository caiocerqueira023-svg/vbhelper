package com.github.nacabaro.vbhelper.world

import org.junit.Assert.*
import org.junit.Test

class WildMoodAnalyzerTest {
    @Test fun everyTurnMovesTrust() {
        assertTrue(WildMoodAnalyzer.resolveDelta("Hello there", "Hi!", null) > 0)
        assertTrue(WildMoodAnalyzer.resolveDelta("Lets do it!", "Ha! Finally.", null) > 0)
        assertTrue(WildMoodAnalyzer.resolveDelta("No thanks", "Suit yourself.", null) < 0)
    }

    @Test fun modelMarkersAreBoundedButNeverZero() {
        assertEquals(2, WildMoodAnalyzer.resolveDelta("Hello", "Hi!", 0))
        assertEquals(4, WildMoodAnalyzer.resolveDelta("Hello", "Hi!", 9))
        assertEquals(-4, WildMoodAnalyzer.resolveDelta("Hello", "Hi!", -9))
        assertEquals(-2, WildMoodAnalyzer.resolveDelta("Hello", "Hi!", -2))
    }
}
