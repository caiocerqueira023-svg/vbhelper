package com.tamerslab.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Frozen-startup benchmark harness for the Phase 5.0 budget. Device-gated; this module is
 * intentionally separate from `:baselineprofile` so profile generation never runs these
 * launch loops.
 *
 * `TraceSectionMetric`s attribute startup to the `StartupTrace` sections emitted by the
 * app: `startup:app-locale`, `startup:koin`, `startup:activity-oncreate`,
 * `startup:deferred-init-dispatch`.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalMetricApi::class)
class StartupBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartup() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(
            StartupTimingMetric(),
            TraceSectionMetric("startup:app-locale"),
            TraceSectionMetric("startup:koin"),
            TraceSectionMetric("startup:activity-oncreate"),
            TraceSectionMetric("startup:deferred-init-dispatch"),
        ),
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
