package com.tamerslab.baselineprofile

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Frame-time harness for the Phase 5.0 / 5.4 budgets (battle p95 <=16.7 ms, farm
 * 60 fps active / 30 fps idle). Device-gated, never part of CI.
 *
 * The first seconds after startup are measured for the farm (Home hosts the 3D
 * Digifarm). The battle run opens the battle entry through the overflow menu and
 * measures while the scene renders. Record the Perfetto/gfxinfo cross-check and the
 * printed frame percentiles in `docs/performance/BASELINE.md`; these numbers do not
 * gate anything until they are recorded there.
 */
@RunWith(AndroidJUnit4::class)
class FrameTimingBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun farmFrameTiming() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.DEFAULT,
        startupMode = StartupMode.COLD,
        iterations = 3,
        setupBlock = {
            pressHome()
        },
    ) {
        startActivityAndWait()
        Thread.sleep(3_000)
    }

    @Test
    fun battleFrameTiming() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.DEFAULT,
        startupMode = StartupMode.COLD,
        iterations = 3,
        setupBlock = {
            pressHome()
        },
    ) {
        startActivityAndWait()
        Journeys.openFromMenu(device, listOf("Battles", "Batalhas", "バトル"))
        Thread.sleep(3_000)
    }
}
