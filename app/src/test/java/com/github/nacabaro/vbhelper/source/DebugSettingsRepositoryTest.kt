package com.github.nacabaro.vbhelper.source

import org.junit.Assert.*
import org.junit.Test

class DebugSettingsRepositoryTest {
    private fun repository(values: MutableMap<String, Boolean> = mutableMapOf()) =
        DebugSettingsRepository(
            readBoolean = { key, fallback -> values[key] ?: fallback },
            writeBoolean = { key, value -> values[key] = value }
        )

    @Test fun legacyInstantScanDoesNotEnableDebugOrChangeNormalRewards() {
        val settings = repository(mutableMapOf("debug_instant_digimon_scan" to true))
        assertFalse(settings.state.value.enabled)
        assertEquals(20, settings.state.value.percentagePerDefeat)
        assertFalse(settings.state.value.allowAnyDistance)
    }

    @Test fun testOverridesRequireAnExplicitMasterOptIn() {
        val settings = repository()
        settings.setInstantScan(true)
        settings.setAnyDistance(true)
        assertEquals(20, settings.state.value.percentagePerDefeat)
        assertFalse(settings.state.value.allowAnyDistance)
        settings.setEnabled(true)
        assertEquals(20, settings.state.value.percentagePerDefeat)
        settings.setInstantScan(true)
        settings.setAnyDistance(true)
        assertEquals(100, settings.state.value.percentagePerDefeat)
        assertTrue(settings.state.value.allowAnyDistance)
    }

    @Test fun disablingRevokesOverridesImmediatelyAndReenableDoesNotRestoreDistanceBypass() {
        val settings = repository()
        settings.setEnabled(true)
        settings.setInstantScan(true)
        settings.setAnyDistance(true)
        settings.setEnabled(false)
        assertFalse(settings.state.value.enabled)
        assertFalse(settings.state.value.allowAnyDistance)
        assertEquals(20, settings.state.value.percentagePerDefeat)
        settings.setEnabled(true)
        assertFalse(settings.state.value.allowAnyDistance)
    }

    @Test fun restartPreservesExplicitSettingsButNeverRestoresAnActiveDistanceOverride() {
        val values = mutableMapOf<String, Boolean>()
        val first = repository(values)
        first.setEnabled(true)
        first.setInstantScan(true)
        first.setAnyDistance(true)
        val restarted = repository(values)
        assertTrue(restarted.state.value.enabled)
        assertEquals(100, restarted.state.value.percentagePerDefeat)
        assertFalse(restarted.state.value.allowAnyDistance)
    }
}
