package com.tamerslab.baselineprofile

import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until

/** The installed application package id (the namespace is `com.tamerslab`). */
internal const val TARGET_PACKAGE = "com.tamerslab.vbhelper"

/**
 * Best-effort UI journeys shared by baseline-profile generation and the
 * macrobenchmark harness. The device under test can be in English, Brazilian
 * Portuguese or Japanese, so each step tries the known labels for those locales
 * and never fails a profile/benchmark run when a screen is not present: the goal
 * is to exercise additional code paths, while the cold-start/measurement block
 * stays deterministic.
 */
internal object Journeys {
    private const val FIND_TIMEOUT_MS = 2_500L

    /** Labels used by the bottom navigation and overflow menu, in each shipped locale. */
    private val moreLabels = listOf("More", "Mais", "その他")
    private val battleLabels = listOf("Battles", "Batalhas", "バトル")
    private val transferLabels = listOf("Transfer", "Transferência", "転送")
    private val deviceLabels = listOf("Devices", "Dispositivos", "デバイス", "Scan", "Escanear", "スキャン")

    fun clickAny(device: UiDevice, labels: List<String>): Boolean {
        for (label in labels) {
            val target = runCatching {
                device.wait(Until.findObject(By.text(label)), FIND_TIMEOUT_MS)
            }.getOrNull()
            if (target != null) {
                runCatching {
                    target.click()
                    device.waitForIdle()
                }
                return true
            }
        }
        return false
    }

    /** Opens the overflow ("More") menu and clicks the first matching entry. */
    fun openFromMenu(device: UiDevice, labels: List<String>): Boolean {
        if (!clickAny(device, moreLabels)) return false
        Thread.sleep(300)
        return clickAny(device, labels)
    }

    /**
     * Exercises the main journeys for baseline-profile generation: farm/orbit
     * interaction, the battle entry, and the transfer/scan surfaces. Blocking
     * waits are intentional in this device-only harness.
     */
    fun runAll(device: UiDevice) {
        runCatching {
            device.waitForIdle()
            // Home renders the 3D Digifarm; a drag also exercises camera interaction.
            val width = device.displayWidth
            val height = device.displayHeight
            device.swipe(width / 2 - 200, height / 2, width / 2 + 200, height / 2, 10)
            device.waitForIdle()
        }

        runCatching {
            if (openFromMenu(device, battleLabels)) {
                Thread.sleep(700)
                device.waitForIdle()
            }
        }

        runCatching {
            device.pressHome()
            device.waitForIdle()
            // Return to the app after the battle journey; this also profiles a warm start.
            device.executeShellCommand(
                "monkey -p $TARGET_PACKAGE -c android.intent.category.LAUNCHER 1",
            )
            device.waitForIdle()
        }

        runCatching {
            if (openFromMenu(device, transferLabels)) {
                Thread.sleep(500)
                clickAny(device, deviceLabels)
                device.waitForIdle()
            }
        }
    }
}
