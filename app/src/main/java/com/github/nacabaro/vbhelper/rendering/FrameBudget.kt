package com.github.nacabaro.vbhelper.rendering

/**
 * Frame pacing for an always-mounted 3D surface (PRESENTATION_REDESIGN_PLAN §7 "Pause and frame
 * budget"). Kept pure and free of Android types so the state machine can be unit-tested on the JVM:
 * the view layer only reports inputs and obeys the returned tier.
 */
enum class SceneFrameTier(val frameIntervalNanos: Long) {
    /** No frames at all: covered by a route, app backgrounded, window hidden or no surface yet. */
    Paused(0L),
    /** Everything is settled: 30fps is indistinguishable from 60fps for a slow idle animation. */
    Idle(1_000_000_000L / 30L),
    /** Touch, camera easing, walking residents or asset streaming: full rate. */
    Active(1_000_000_000L / 60L),
}

/** One immutable snapshot of everything that decides whether the farm may burn frames. */
data class SceneFrameInputs(
    /** The surface is on screen and the host asked for it (HUD detent / route visibility). */
    val worldActive: Boolean = true,
    val lifecycleResumed: Boolean = true,
    val windowVisible: Boolean = true,
    val surfaceAvailable: Boolean = true,
    /** A finger is down, or was released less than [SceneFrameBudget.INTERACTION_GRACE_NANOS] ago. */
    val interacting: Boolean = false,
    /** Residents walking, camera easing back onto the partner, resident GLBs still streaming. */
    val animating: Boolean = false,
)

object SceneFrameBudget {
    const val IDLE_FPS = 30
    const val ACTIVE_FPS = 60

    /** A released gesture keeps the surface at full rate briefly so the last frames stay smooth. */
    const val INTERACTION_GRACE_NANOS = 800_000_000L

    fun tier(inputs: SceneFrameInputs): SceneFrameTier = when {
        !inputs.worldActive || !inputs.lifecycleResumed || !inputs.windowVisible || !inputs.surfaceAvailable ->
            SceneFrameTier.Paused
        inputs.interacting || inputs.animating -> SceneFrameTier.Active
        else -> SceneFrameTier.Idle
    }
}

/**
 * Decides whether the frame that is about to run may touch the GL stack.
 *
 * `Choreographer` delivers a callback every vsync; the pacer keeps the rendered rate at the tier's
 * interval instead of relying on `Thread.sleep`, and tolerates one vsync of jitter so 60fps stays
 * 60fps on a 60Hz panel instead of collapsing to 30fps through rounding.
 */
class SceneFramePacer(private val toleranceNanos: Long = 2_500_000L) {
    private var lastFrameNanos = 0L

    /** True when this callback should render. Always false while [SceneFrameTier.Paused]. */
    fun claim(frameTimeNanos: Long, tier: SceneFrameTier): Boolean {
        if (tier == SceneFrameTier.Paused) return false
        val elapsed = frameTimeNanos - lastFrameNanos
        if (lastFrameNanos != 0L && elapsed < tier.frameIntervalNanos - toleranceNanos) return false
        lastFrameNanos = frameTimeNanos
        return true
    }

    /** Drops the accumulated clock so resuming after a pause renders immediately. */
    fun reset() {
        lastFrameNanos = 0L
    }
}
