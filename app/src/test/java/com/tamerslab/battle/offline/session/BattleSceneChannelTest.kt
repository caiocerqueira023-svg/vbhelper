package com.tamerslab.battle.offline.session

import com.tamerslab.battle.offline.core.BattleSnapshot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BattleSceneChannelTest {
    private fun snapshot(
        elapsedMillis: Long = 0L,
        paused: Boolean = false,
        pauseReason: String? = null,
        result: com.tamerslab.battle.offline.core.BattleResult? = null,
        finisher: com.tamerslab.battle.offline.core.BattleFinisherSnapshot? = null,
    ) = BattleSnapshot(
        elapsedMillis = elapsedMillis,
        isPaused = paused,
        pauseReason = pauseReason,
        alliedMembers = emptyList(),
        opposingMembers = emptyList(),
        result = result,
        recentEvents = emptyList(),
        finisher = finisher,
    )

    @Test
    fun `disabled channel publishes every snapshot`() {
        assertTrue(BattleSceneChannel.shouldPublishUiSnapshot(null, snapshot(), 0L, channelEnabled = false))
        assertTrue(BattleSceneChannel.shouldPublishUiSnapshot(snapshot(), snapshot(elapsedMillis = 16), 0L, channelEnabled = false))
    }

    @Test
    fun `enabled channel throttles steady state but publishes the first snapshot`() {
        val first = snapshot(elapsedMillis = 16)
        assertTrue(BattleSceneChannel.shouldPublishUiSnapshot(null, first, 0L, channelEnabled = true))
        assertFalse(
            BattleSceneChannel.shouldPublishUiSnapshot(
                snapshot(elapsedMillis = 16),
                snapshot(elapsedMillis = 32),
                elapsedSinceUiSnapshotMillis = 16L,
                channelEnabled = true,
            ),
        )
        assertTrue(
            BattleSceneChannel.shouldPublishUiSnapshot(
                snapshot(elapsedMillis = 16),
                snapshot(elapsedMillis = 132),
                elapsedSinceUiSnapshotMillis = BattleSceneChannel.UI_SNAPSHOT_INTERVAL_MILLIS,
                channelEnabled = true,
            ),
        )
    }

    @Test
    fun `enabled channel publishes pause changes immediately`() {
        assertTrue(
            BattleSceneChannel.shouldPublishUiSnapshot(
                snapshot(elapsedMillis = 16),
                snapshot(elapsedMillis = 32, paused = true, pauseReason = "menu"),
                elapsedSinceUiSnapshotMillis = 0L,
                channelEnabled = true,
            ),
        )
    }

    @Test
    fun `enabled channel publishes finisher and result transitions immediately`() {
        val finisher = com.tamerslab.battle.offline.core.BattleFinisherSnapshot(
            sequenceId = 7L,
            kind = com.tamerslab.battle.offline.core.BattleFinisherKind.POWER,
            leadId = "a",
            elapsedMillis = 10L,
            durationMillis = 100L,
            phase = com.tamerslab.battle.offline.core.BattleFinisherPhase.FOCUS,
            phaseProgress = 0.1f,
            impactCommitted = false,
        )
        assertTrue(
            BattleSceneChannel.shouldPublishUiSnapshot(
                snapshot(elapsedMillis = 16),
                snapshot(elapsedMillis = 32, finisher = finisher),
                0L,
                channelEnabled = true,
            ),
        )
        val result = com.tamerslab.battle.offline.core.BattleResult(
            outcome = com.tamerslab.battle.offline.core.BattleOutcome.ALLIED_VICTORY,
            elapsedMillis = 48,
            eventCount = 3,
        )
        assertTrue(
            BattleSceneChannel.shouldPublishUiSnapshot(
                snapshot(elapsedMillis = 32),
                snapshot(elapsedMillis = 48, result = result),
                0L,
                channelEnabled = true,
            ),
        )
    }
}
