package com.tamerslab.baselineprofile

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Frozen-startup benchmark harness for the Phase 5.0 budget
 * (`>=15% faster than the Phase 0 release baseline`). This module is device-gated:
 * it is not part of CI and must not run on an emulator quote.
 *
 * Run against a release-like build via the benchmark Gradle task for this module and
 * record the JSON/console result with commit, device, and artifact in
 * `docs/performance/BASELINE.md`.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartup() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = CompilationMode.DEFAULT,
        startupMode = StartupMode.COLD,
        iterations = 5,
        setupBlock = {
            pressHome()
        },
    ) {
        startActivityAndWait()
    }
}
