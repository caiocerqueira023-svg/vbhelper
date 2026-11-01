package com.tamerslab.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the committed baseline profiles on a connected device.
 *
 * Run with: `.\gradlew.bat :app:generateBaselineProfile` (device required). The startup
 * block feeds `startup-prof.txt`; the journeys feed the regular profile so scan, battle,
 * farm and transfer entry paths are covered as required by Phase 1.4. Journeys are
 * best-effort by design — a missing screen must not make profile generation fail.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun startupAndJourneys() = baselineProfileRule.collect(
        packageName = TARGET_PACKAGE,
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()
        Journeys.runAll(device)
    }
}
