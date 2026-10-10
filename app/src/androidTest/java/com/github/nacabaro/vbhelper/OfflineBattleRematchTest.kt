package com.github.nacabaro.vbhelper

import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.nacabaro.vbhelper.screens.offlineBattle.OfflineBattleSceneView
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineBattleRematchTest {
    @get:Rule val compose = createAndroidComposeRule<OfflineBattleValidationActivity>()

    @Test fun rematchRestartsCombatWithoutReloadingModelsOrReplacingTheRenderer() {
        val activity = compose.activity
        check(activity.packageName.endsWith(".integritycheck"))
        val model = activity.battleModel
        try {
            compose.waitUntil(45_000) { model.state.value.error != null || (model.state.value.snapshot?.elapsedMillis ?: 0) > 500 }
            assertNull(model.state.value.error)
            compose.runOnUiThread { model.abandon() }
            val previous = model.state.value
            val renderer = requireNotNull(findScene(activity.window.decorView))
            val loads = renderer.fighterAssetLoadCount
            assertTrue(loads > 0)
            // The continuously rendered arena does not idle after a terminal result.
            // Invoke the button's action on the UI thread rather than waiting for global idleness.
            compose.runOnUiThread { model.retry(activity) }
            val next = model.state.value
            assertFalse(next.loading)
            assertNotEquals(previous.sessionId, next.sessionId)
            assertEquals(previous.assetGeneration, next.assetGeneration)
            assertSame(previous.fighters, next.fighters)
            assertSame(previous.preparedForms, next.preparedForms)
            assertNull(next.snapshot?.result)
            compose.waitUntil(10_000) { (model.state.value.snapshot?.elapsedMillis ?: 0) > 500 }
            compose.runOnUiThread { model.setPaused("test", true) }
            assertSame(renderer, findScene(activity.window.decorView))
            assertEquals(loads, renderer.fighterAssetLoadCount)
            compose.onAllNodesWithText(activity.getString(R.string.ui_battle_training_loading)).assertCountEquals(0)
        } finally {
            compose.runOnUiThread { model.finishSession() }
        }
    }

    private fun findScene(view: View): OfflineBattleSceneView? {
        if (view is OfflineBattleSceneView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) {
            findScene(view.getChildAt(index))?.let { return it }
        }
        return null
    }
}
