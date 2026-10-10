package com.github.nacabaro.vbhelper

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.nacabaro.vbhelper.battle.offline.core.BattlePosition
import com.github.nacabaro.vbhelper.screens.assetOfflineBattleParticipant
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

/** Real 2v2 motion sampling and captures in the isolated validation application. */
@RunWith(AndroidJUnit4::class)
class OfflineBattleRepositioningTest {
    @get:Rule val compose = createAndroidComposeRule<OfflineBattleValidationActivity>()

    @Test fun twoVsTwoFindsSpaceAndKeepsMovingBetweenAttacks() {
        val activity = compose.activity
        check(activity.packageName.endsWith(".integritycheck"))
        val model = activity.battleModel
        val a = assetOfflineBattleParticipant("dim000_mon03", "Pulsemon", 8, 3, stage = 3)
        val b = assetOfflineBattleParticipant("dim012_mon03", "Agumon", 3000, 1100, stage = 3)
        try {
            compose.runOnUiThread { model.start(activity, "repositioning-review", listOf(a, b), listOf(b, a), 19) }
            compose.waitUntil(45_000) {
                model.state.value.error != null || (model.state.value.countdown == 0 &&
                    (model.state.value.snapshot?.elapsedMillis ?: 0L) > 300)
            }
            assertNull(model.state.value.error)
            val initial = requireNotNull(model.state.value.snapshot)
            val previous = (initial.alliedMembers + initial.opposingMembers).associate { it.combatantId to it.position }.toMutableMap()
            val travelled = previous.keys.associateWith { 0f }.toMutableMap()
            val lateral = previous.keys.associateWith { 0f }.toMutableMap()
            val startZ = previous.mapValues { it.value.z }
            for (sample in 0 until 80) {
                Thread.sleep(100)
                val state = requireNotNull(model.state.value.snapshot)
                for (member in state.alliedMembers + state.opposingMembers) {
                    travelled[member.combatantId] = travelled.getValue(member.combatantId) +
                        previous.getValue(member.combatantId).distanceTo(member.position)
                    lateral[member.combatantId] = maxOf(lateral.getValue(member.combatantId),
                        abs(member.position.z - startZ.getValue(member.combatantId)))
                    previous[member.combatantId] = member.position
                    assertTrue("Fighter escaped the playable floor", member.position.distanceTo(BattlePosition(0f, 0f)) < 8f)
                }
                if (sample in listOf(10, 40, 75)) {
                    compose.runOnUiThread { model.setPaused("capture", true) }
                    capture("repositioning-$sample")
                    compose.runOnUiThread { model.setPaused("capture", false) }
                }
            }
            val after = requireNotNull(model.state.value.snapshot)
            assertTrue(after.elapsedMillis > initial.elapsedMillis + 4_000)
            assertTrue("Fighters stayed fixed instead of claiming space", travelled.values.count { it > 1f } >= 2)
            assertTrue("Fighters remained on a straight locked lane", lateral.values.count { it > 0.3f } >= 2)
            println("REPOSITIONING_REVIEW elapsed=${after.elapsedMillis} travelled=$travelled lateral=$lateral")
            assertNull(model.state.value.error)
        } finally {
            compose.runOnUiThread { model.finishSession() }
        }
    }

    private fun capture(name: String) {
        Thread.sleep(160)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val image = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "battle-validation").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
    }
}
