package com.tamerslab.benchmark

import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until

/** The installed application package id (the namespace is `com.tamerslab`). */
internal const val TARGET_PACKAGE = "com.tamerslab.vbhelper"

/**
 * Minimal UI journey helper for the macrobenchmarks. This is a copy of the
 * baseline-profile module's helper on purpose: the profile generator module must stay
 * free of benchmark classes so `:app:generateBaselineProfile` does not execute
 * macrobenchmark launch loops, and a `com.android.test` module cannot share sources with
 * another test module. Labels cover the shipped locales; failures are swallowed so a
 * missing screen cannot fail a measurement.
 */
internal object Journeys {
    private const val FIND_TIMEOUT_MS = 2_500L

    private val moreLabels = listOf("More", "Mais", "その他")
    private val battleLabels = listOf("Battles", "Batalhas", "バトル")

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
}
