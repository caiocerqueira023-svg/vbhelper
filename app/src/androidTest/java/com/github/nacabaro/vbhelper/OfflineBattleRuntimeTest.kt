package com.github.nacabaro.vbhelper

import android.graphics.Bitmap
import android.content.pm.ActivityInfo
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.Lifecycle
import com.github.nacabaro.vbhelper.screens.assetOfflineBattleParticipant
import com.github.nacabaro.vbhelper.screens.offlineBattle.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Real Filament/Compose integration in the isolated .integritycheck application. */
@RunWith(AndroidJUnit4::class)
class OfflineBattleRuntimeTest {
    @get:Rule val compose = createAndroidComposeRule<OfflineBattleValidationActivity>()

    @Test fun arenaLoadsCombatAdvancesAndTacticalMenuFreezesTheSession() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".integritycheck"))
        val model = compose.activity.battleModel
        val a = assetOfflineBattleParticipant("dim000_mon03", "Pulsemon", 8, 3, stage = 3)
        val b = assetOfflineBattleParticipant("dim012_mon03", "Agumon", 3000, 1100, stage = 3)
        try {
            compose.waitUntil(45_000) { model.state.value.error != null || (model.state.value.snapshot?.elapsedMillis ?: 0) >= 5000 }
            assertNull(model.state.value.error)
            assertNull("Combat must not end during loading/opening", model.state.value.snapshot!!.result)
            // Compose waits for idleness; the independent simulation clock deliberately
            // never idles during combat. Stabilize only while locating/tapping the menu.
            compose.runOnUiThread { model.setPaused("test-interaction", true) }
            compose.onNodeWithText("Técnicas", useUnmergedTree = true).performClick()
            compose.runOnUiThread { model.setPaused("test-interaction", false) }
            compose.waitUntil(5000) { model.state.value.snapshot?.pauseReason == "tech-menu" }
            val paused = model.state.value.snapshot!!
            Thread.sleep(1200)
            assertEquals(paused, model.state.value.snapshot)
            capture("techniques-portrait")
            compose.activityRule.scenario.recreate()
            compose.waitUntil(10_000) { model.state.value.snapshot?.pauseReason == "tech-menu" }
            assertEquals(paused.elapsedMillis, model.state.value.snapshot!!.elapsedMillis)
            compose.runOnUiThread { model.setPaused("test-interaction", true) }
            compose.onNodeWithText("Retomar combate").performClick()
            compose.runOnUiThread { model.setPaused("test-interaction", false) }
            compose.waitUntil(5000) { model.state.value.snapshot!!.elapsedMillis > paused.elapsedMillis + 500 }
            compose.runOnUiThread { model.setPaused("capture", true) }
            capture("arena-portrait")
            compose.runOnUiThread { model.setPaused("capture", false) }
            compose.waitUntil(20_000) { model.state.value.snapshot!!.elapsedMillis >= 12_000 }
            assertTrue(model.state.value.snapshot!!.statistics.damageDealt > 0)
            assertTrue(model.state.value.snapshot!!.statistics.damageReceived > 0)
            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            val backgroundTime = model.state.value.snapshot!!.elapsedMillis
            Thread.sleep(1200)
            assertEquals(backgroundTime, model.state.value.snapshot!!.elapsedMillis)
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            compose.waitUntil(10_000) { model.state.value.snapshot!!.elapsedMillis > backgroundTime }
            // Exercise a new lineup in the same renderer, including rematch readiness.
            compose.runOnUiThread {
                model.start(context, "runtime2v2", listOf(a, b), listOf(a, b), 9)
            }
            compose.waitUntil(30_000) { (model.state.value.snapshot?.elapsedMillis ?: 0) > 3000 }
            assertEquals(2, model.state.value.snapshot!!.alliedMembers.size)
            compose.runOnUiThread { model.setPaused("capture", true) }
            capture("arena-2v2")
            compose.runOnUiThread { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            compose.waitUntil(15_000) {
                compose.activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE &&
                    model.state.value.snapshot?.pauseReason == "capture" &&
                    compose.onAllNodesWithText("Carregando Digimon 3D e arena…").fetchSemanticsNodes().isEmpty()
            }
            capture("arena-landscape")
            compose.runOnUiThread {
                model.start(context, "runtime1v2", listOf(a), listOf(a, b), 11)
            }
            compose.waitUntil(30_000) { (model.state.value.snapshot?.elapsedMillis ?: 0) > 3000 }
            assertEquals(1, model.state.value.snapshot!!.alliedMembers.size)
            assertEquals(2, model.state.value.snapshot!!.opposingMembers.size)
            assertNull(model.state.value.snapshot!!.result)
        } finally {
            compose.runOnUiThread { model.setPaused("capture", true) }
            capture("last-runtime-state")
            compose.runOnUiThread { model.finishSession() }
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        Thread.sleep(300)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "battle-validation").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
