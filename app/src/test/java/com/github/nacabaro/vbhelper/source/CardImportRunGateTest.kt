package com.github.nacabaro.vbhelper.source

import org.junit.Assert.*
import org.junit.Test

class CardImportRunGateTest {
    @Test fun cancellationDoesNotAdmitAReplacementUntilTheOldJobActuallyCompletes() {
        val gate = CardImportRunGate()
        val first = gate.begin()!!
        assertNull(gate.begin())
        assertTrue(gate.owns(first))
        gate.finish(first)
        assertNotNull(gate.begin())
    }

    @Test fun staleProgressAndCompletionCannotOwnOrReleaseAReplacement() {
        val gate = CardImportRunGate()
        val first = gate.begin()!!
        gate.finish(first)
        val second = gate.begin()!!
        assertFalse(gate.owns(first))
        gate.finish(first)
        assertTrue(gate.owns(second))
        assertNull(gate.begin())
    }
}
