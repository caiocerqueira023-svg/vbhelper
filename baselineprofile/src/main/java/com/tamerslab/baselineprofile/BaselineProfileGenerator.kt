package com.tamerslab.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the committed baseline profiles on a connected device.
 *
 * Run with: `.\gradlew.bat :app:generateBaselineProfile` (device required).
 *
 * The startup profile and the journey profile are collected separately on purpose:
 * `includeInStartupProfile = true` marks every rule from the test as startup-critical, so
 * mixing journeys into that test bloats `startup-prof.txt` (it used to be byte-identical
 * to `baseline-prof.txt`). Keeping startup to the cold launch and the journeys in a
 * second collection produces a small startup profile plus a broader baseline profile.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    /** Cold app start only: feeds `startup-prof.txt`. */
    @Test
    fun startupProfile() = baselineProfileRule.collect(
        packageName = TARGET_PACKAGE,
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()
    }

    /** Scan/battle/farm/transfer journeys: feeds the regular `baseline-prof.txt`. */
    @Test
    fun appJourneys() = baselineProfileRule.collect(
        packageName = TARGET_PACKAGE,
        includeInStartupProfile = false,
    ) {
        pressHome()
        startActivityAndWait()
        Journeys.runAll(device)
    }
}
