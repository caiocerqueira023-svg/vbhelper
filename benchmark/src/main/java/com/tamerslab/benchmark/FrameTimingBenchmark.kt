package com.tamerslab.benchmark

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
 * 60 fps active / 30 fps idle). Device-gated; separate from the profile generator module.
 *
 * Idle/active farm windows are measured after warm-up so 3D asset streaming is excluded.
 */
@RunWith(AndroidJUnit4::class)
class FrameTimingBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    /** Idle farm after warm-up: target 30 fps (frame tier Idle). */
    @Test
    fun farmIdleFrameTiming() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.DEFAULT,
        startupMode = StartupMode.COLD,
        iterations = 3,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            Thread.sleep(4_000)
        },
    ) {
        Thread.sleep(3_000)
    }

    /** Active farm after warm-up (drag/orbit): target 60 fps. */
    @Test
    fun farmActiveFrameTiming() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.DEFAULT,
        startupMode = StartupMode.COLD,
        iterations = 3,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            Thread.sleep(4_000)
        },
    ) {
        val cx = device.displayWidth / 2
        val cy = device.displayHeight / 2
        repeat(10) {
            device.swipe(cx - 220, cy, cx + 220, cy, 10)
            Thread.sleep(250)
        }
    }

    /** Battle scene after warm-up. */
    @Test
    fun battleFrameTiming() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.DEFAULT,
        startupMode = StartupMode.COLD,
        iterations = 3,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            Journeys.openFromMenu(device, listOf("Battles", "Batalhas", "バトル"))
            Thread.sleep(2_000)
        },
    ) {
        Thread.sleep(3_000)
    }
}
