package com.github.nacabaro.vbhelper

import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.github.nacabaro.vbhelper.screens.offlineBattle.OfflineBattleSceneView
import com.github.nacabaro.vbhelper.ui.theme.AppTheme
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class EnvironmentThemeRuntimeTest {
    @Test fun battleStartsInANonDefaultThemeAndKeepsCombatReadyWhileRecoloring() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(context, OfflineBattleValidationActivity::class.java).putExtra("theme", "vb_lab")
        ActivityScenario.launch<OfflineBattleValidationActivity>(intent).use { scenario ->
            try {
                val deadline = SystemClock.elapsedRealtime() + 45_000
                var started = false
                while (SystemClock.elapsedRealtime() < deadline && !started) {
                    scenario.onActivity {
                        assertNull(it.battleModel.state.value.error)
                        val scene = battleScene(it.window.decorView)
                        assertNull(scene?.environmentThemeFailure)
                        started = (it.battleModel.state.value.snapshot?.elapsedMillis ?: 0) > 500 &&
                            scene?.appliedEnvironmentTheme == AppTheme.VB_LAB
                    }
                    SystemClock.sleep(100)
                }
                assertTrue("Combat never left the loading state in VB Lab", started)
                var original: OfflineBattleSceneView? = null
                var fighterLoads = 0
                scenario.onActivity { original = battleScene(it.window.decorView); fighterLoads = original!!.fighterAssetLoadCount }
                for (theme in listOf(AppTheme.VB_ARENA, AppTheme.DIGIMON_NET, AppTheme.VB_HELPER)) {
                    scenario.onActivity { it.previewTheme = theme }
                    val end = SystemClock.elapsedRealtime() + 10_000
                    var applied = false
                    while (SystemClock.elapsedRealtime() < end && !applied) {
                        scenario.onActivity {
                            val scene = battleScene(it.window.decorView)
                            assertSame(original, scene)
                            assertNull(scene!!.environmentThemeFailure)
                            applied = scene.appliedEnvironmentTheme == theme
                            assertEquals(fighterLoads, scene.fighterAssetLoadCount)
                            assertFalse(it.battleModel.state.value.loading)
                        }
                        SystemClock.sleep(100)
                    }
                    assertTrue("$theme did not apply to the running battle", applied)
                }
            } finally {
                scenario.onActivity { it.battleModel.finishSession() }
            }
        }
    }

    @Test fun farmChangesAllPalettesOnItsExistingRenderer() = verify("farm", setOf(
        "island_top_baked", "island_side_baked", "voxel_block_dark_baked", "voxel_block_lit_baked"))
    @Test fun colosseumChangesAllPalettesAndRethemesAfterRetry() = verify("colosseum", setOf("Ground", "Sphere001"))
    @Test fun radarBattleChangesFloorsPanelsAndDome() = verify("radar", setOf(
        "radar_floor", "world_grid", "battle_boundary", "voxel_block_dark_baked", "voxel_block_lit_baked", "RadarSkyDome"))
    @Test fun radarFirstPersonChangesItsExistingScene() = verify("radar_fp", setOf(
        "radar_floor", "world_grid", "battle_boundary", "voxel_block_dark_baked", "voxel_block_lit_baked", "RadarSkyDome"))

    private fun verify(environment: String, expectedMaterials: Set<String>) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(context, EnvironmentThemeValidationActivity::class.java).putExtra("environment", environment)
        ActivityScenario.launch<EnvironmentThemeValidationActivity>(intent).use { scenario ->
            await(scenario, AppTheme.VB_LAB)
            var original: TextureView? = null
            scenario.onActivity { original = it.nativeView(); assertNotNull(original); assertEquals(1, it.probe()!!.loads) }
            for (theme in AppTheme.entries) {
                scenario.onActivity { it.previewTheme = theme }
                await(scenario, theme)
                // Sample after several real frames so async texture uploads and pulsing emission have run.
                SystemClock.sleep(350)
                scenario.onActivity {
                    assertSame(original, it.nativeView())
                    assertEquals(1, it.probe()!!.loads)
                    assertEquals(expectedMaterials, it.probe()!!.materials)
                    assertNull(it.probe()!!.failure)
                    val bitmap = checkNotNull(it.nativeView()!!.bitmap)
                    val folder = File(it.getExternalFilesDir(null), "environment-theme-captures").apply { mkdirs() }
                    File(folder, "$environment-${theme.preferenceValue}.png").outputStream().use { output ->
                        assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                    }
                    bitmap.recycle()
                }
            }
            if (environment != "farm") {
                scenario.onActivity { it.renderingActive = false; it.previewTheme = AppTheme.VB_ARENA }
                await(scenario, AppTheme.VB_ARENA)
                scenario.onActivity { it.renderingActive = true; assertSame(original, it.nativeView()) }
            }
            if (environment == "colosseum") {
                scenario.onActivity { (it.nativeView() as OfflineBattleSceneView).retryScene() }
                await(scenario, AppTheme.VB_ARENA, 2)
            }
        }
    }

    private fun await(scenario: ActivityScenario<EnvironmentThemeValidationActivity>, theme: AppTheme, loads: Int = 1) {
        val deadline = SystemClock.elapsedRealtime() + 45_000
        while (SystemClock.elapsedRealtime() < deadline) {
            var ready = false
            scenario.onActivity {
                assertNull(it.renderingFailure)
                val probe = it.probe()
                assertNull(probe?.failure)
                ready = probe?.theme == theme && probe.loads == loads
            }
            if (ready) return
            SystemClock.sleep(100)
        }
        fail("$theme did not apply to the native environment in 45 seconds")
    }

    private fun battleScene(view: View): OfflineBattleSceneView? {
        if (view is OfflineBattleSceneView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) battleScene(view.getChildAt(i))?.let { return it }
        return null
    }
}
