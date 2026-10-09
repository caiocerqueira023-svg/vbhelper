package com.github.nacabaro.vbhelper.screens.offlineBattle

import org.junit.Assert.*
import org.junit.Test

class BlastGaugeTest {
    @Test fun hitOnlyInsideRedZone() {
        assertFalse(blastNeedleHit(0f))
        assertFalse(blastNeedleHit(BLAST_RED_ZONE_START - 0.01f))
        assertTrue(blastNeedleHit(BLAST_RED_ZONE_START))
        assertTrue(blastNeedleHit(0.95f))
        assertTrue(blastNeedleHit(1f))
    }

    @Test fun redZoneSitsAtSweepEnd() {
        assertTrue(BLAST_RED_ZONE_START in 0.5f..1f)
        assertTrue(BLAST_SWEEP_MILLIS in 500..5000)
    }
}
