package com.github.nacabaro.vbhelper

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.nacabaro.vbhelper.battle.offline.core.BattleFinisherPhase
import com.github.nacabaro.vbhelper.battle.offline.core.OrderStatus
import com.github.nacabaro.vbhelper.battle.offline.core.TrainerAction
import com.github.nacabaro.vbhelper.screens.offlineBattle.OfflineBattleSceneView
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Real scene/Compose bounds and repeated GPU reuse, in the isolated integrity-check application. */
@RunWith(AndroidJUnit4::class)
class OfflineBattleFinisherPresentationTest {
    @get:Rule val compose = createAndroidComposeRule<OfflineBattleValidationActivity>()

    @Test fun repeatedFinishersKeepArenaAndEveryCommandStationaryInBothOrientations() {
        val model = compose.activity.battleModel
        check(compose.activity.packageName.endsWith(".integritycheck"))
        try {
            for ((index, orientation) in listOf(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE).withIndex()) {
                compose.runOnUiThread { compose.activity.requestedOrientation = orientation }
                compose.waitUntil(15_000) {
                    compose.activity.resources.configuration.orientation ==
                        if (orientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) Configuration.ORIENTATION_PORTRAIT
                        else Configuration.ORIENTATION_LANDSCAPE
                }
                if (index > 0) compose.runOnUiThread { model.retry(compose.activity) }
                compose.waitUntil(60_000) {
                    val state = model.state.value
                    state.error != null || state.snapshot?.result != null ||
                        (state.countdown == 0 && state.snapshot?.alliedMembers?.firstOrNull()?.specialCharge == 100)
                }
                assertNull(model.state.value.error)
                assertNull("The fixture must survive until its special is charged", model.state.value.snapshot!!.result)
                compose.runOnUiThread { model.setPaused("capture", true) }
                val scene = requireNotNull(findScene(compose.activity.window.decorView))
                val loads = scene.fighterAssetLoadCount
                val arena = bounds("offline-battle-arena-viewport")
                val commands = commandBounds()
                assertEquals(9, commands.size)
                capture("finisher-$index-before")

                val ready = model.state.value.snapshot!!
                val lead = ready.alliedMembers.first()
                val opponent = ready.opposingMembers.first { it.health > 0 }
                compose.runOnUiThread {
                    val update = model.issueOrder(lead.combatantId,
                        TrainerAction.UseTechnique(requireNotNull(lead.specialTechniqueId), opponent.combatantId))
                    assertEquals(OrderStatus.QUEUED, update!!.status)
                    model.setPaused("capture", false)
                }
                compose.waitUntil(20_000) { lead.combatantId in model.state.value.snapshot!!.pendingBlastTiming }
                compose.runOnUiThread {
                    assertEquals(OrderStatus.COMPLETED,
                        model.issueOrder(lead.combatantId, TrainerAction.ConfirmBlastTiming())!!.status)
                    model.setPaused("blast-menu", false)
                    model.setPaused("capture", true)
                }
                compose.waitUntil(5_000) { model.state.value.snapshot?.finisher != null }
                assertBoundsEqual(arena, bounds("offline-battle-arena-viewport"))
                assertCommandBoundsEqual(commands, commandBounds())
                assertSame(scene, findScene(compose.activity.window.decorView))
                assertEquals(loads, scene.fighterAssetLoadCount)

                compose.runOnUiThread { model.setPaused("capture", false) }
                compose.waitUntil(10_000) {
                    model.state.value.snapshot?.finisher?.let {
                        it.phase == BattleFinisherPhase.RELEASE && it.phaseProgress >= 0.65f
                    } == true
                }
                compose.runOnUiThread { model.setPaused("capture", true) }
                assertBoundsEqual(arena, bounds("offline-battle-arena-viewport"))
                assertCommandBoundsEqual(commands, commandBounds())
                capture("finisher-$index-flight")

                compose.runOnUiThread { model.setPaused("capture", false) }
                compose.waitUntil(5_000) { model.state.value.snapshot?.finisher?.let {
                    it.phase == BattleFinisherPhase.IMPACT && it.phaseProgress >= 0.28f
                } == true }
                compose.runOnUiThread { model.setPaused("capture", true) }
                println("FINISHER_REVIEW orientation=$orientation state=${model.state.value.snapshot?.finisher}")
                capture("finisher-$index-impact")
                assertEquals(loads, scene.fighterAssetLoadCount)
                compose.runOnUiThread { model.setPaused("capture", false) }
                compose.waitUntil(10_000) { model.state.value.snapshot?.finisher == null }
                compose.runOnUiThread { model.setPaused("capture", true) }
                assertBoundsEqual(arena, bounds("offline-battle-arena-viewport"))
                assertCommandBoundsEqual(commands, commandBounds())
                assertEquals(loads, scene.fighterAssetLoadCount)
            }
        } finally {
            compose.runOnUiThread { model.finishSession() }
        }
    }

    private fun bounds(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
    private fun commandBounds() = compose.onAllNodesWithTag("offline-battle-command-slot")
        .fetchSemanticsNodes().map { it.boundsInRoot }

    private fun assertCommandBoundsEqual(expected: List<Rect>, actual: List<Rect>) {
        assertEquals(expected.size, actual.size)
        expected.zip(actual).forEach { (a, b) -> assertBoundsEqual(a, b) }
    }

    private fun assertBoundsEqual(expected: Rect, actual: Rect) {
        assertEquals(expected.left, actual.left, 1f)
        assertEquals(expected.top, actual.top, 1f)
        assertEquals(expected.right, actual.right, 1f)
        assertEquals(expected.bottom, actual.bottom, 1f)
    }

    private fun capture(name: String) {
        Thread.sleep(160)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "battle-validation").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun findScene(view: View): OfflineBattleSceneView? {
        if (view is OfflineBattleSceneView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) findScene(view.getChildAt(index))?.let { return it }
        return null
    }
}
