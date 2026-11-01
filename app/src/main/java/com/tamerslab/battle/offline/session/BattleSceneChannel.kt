package com.tamerslab.battle.offline.session

import com.tamerslab.BuildConfig
import com.tamerslab.battle.offline.core.BattleSnapshot

/**
 * Phase 5.4 render/UI channel split for the offline battle screen.
 *
 * The simulator ticks at 60 Hz and publishes an immutable [BattleSnapshot]. Until
 * this change that snapshot was copied into the single Compose UI state object on
 * every tick, so the whole battle screen recomposed 60 times per second, including
 * a deep `data class` equality check and every `LaunchedEffect(snapshot)` restart.
 *
 * The split keeps the full-rate stream for the Filament scene (consumed directly
 * through a `StateFlow`, never through Compose state) and throttles the Compose UI
 * state to [UI_SNAPSHOT_INTERVAL_MILLIS] with immediate publication for the events
 * that the UI must not miss (pause, finisher sequence, terminal result).
 *
 * The whole split is behind [BuildConfig.BATTLE_SCENE_CHANNEL_ENABLED] so the old
 * path can be restored by flipping one buildConfig field. It defaults to `false`
 * until the on-device Perfetto/FrameMetrics validation in
 * `docs/performance/BASELINE.md` confirms the win and a game-play parity pass.
 */
object BattleSceneChannel {
    /** Minimum spacing between full snapshots published into Compose UI state. */
    const val UI_SNAPSHOT_INTERVAL_MILLIS = 100L

    /** Compile-time rollback switch. */
    val enabled: Boolean = BuildConfig.BATTLE_SCENE_CHANNEL_ENABLED

    /**
     * Whether [next] must be copied into Compose UI state now.
     *
     * [previous] is the snapshot currently held by the UI state (null before the
     * first snapshot), and [elapsedSinceUiSnapshotMillis] is the time since the UI
     * state last received a snapshot. When the channel is disabled this always
     * returns true, preserving the original per-tick behaviour.
     *
     * @param channelEnabled injectable only so the policy can be unit-tested for
     * both flag states; production callers use the default.
     */
    fun shouldPublishUiSnapshot(
        previous: BattleSnapshot?,
        next: BattleSnapshot,
        elapsedSinceUiSnapshotMillis: Long,
        channelEnabled: Boolean = enabled,
    ): Boolean {
        if (!channelEnabled) return true
        if (previous == null) return true
        // The HUD reacts to game flow, not to per-tick positions: these transitions
        // must never wait for the throttle window.
        if (next.result != null || previous.result != null) return true
        if (next.isPaused != previous.isPaused) return true
        if (next.pauseReason != previous.pauseReason) return true
        if (next.finisher?.sequenceId != previous.finisher?.sequenceId) return true
        if (next.pendingBlastTiming != previous.pendingBlastTiming) return true
        if (next.pendingSupportCombatantIds != previous.pendingSupportCombatantIds) return true
        return elapsedSinceUiSnapshotMillis >= UI_SNAPSHOT_INTERVAL_MILLIS
    }
}
