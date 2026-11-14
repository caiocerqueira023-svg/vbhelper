package com.tamerslab.rendering.sprite3d

import java.util.Locale

/**
 * Vertical life the renderer layers on top of a resident's pose.
 *
 * The extruded cardboard sprite is planar, so the only cheap, readable body language available is
 * translation along Y ([HOP]) and a feet-planted squash/stretch ([BREATHE]). Both are driven by the
 * view from the same choice object that picks the pose, so a gesture can never outlive its family.
 */
enum class ResidentGesture {
    /** No extra motion: the pose cycle is the whole performance. */
    NONE,

    /** One decaying series of small jumps, used for a happy/play social beat. */
    HOP,

    /** A slow vertical scale "breath", used while sleeping so a held pose still reads alive. */
    BREATHE,
}

/**
 * One animation decision for a resident: which pose to show, the next frame to alternate with, how
 * fast to alternate, and which gesture rides along.
 *
 * @property pose primary pose name; the caller binds this immediately and must have it available.
 * @property alternate second frame cycled after [frameMillis]; null when the family has no second
 *   frame (or when the second frame is not part of the resident's artwork).
 * @property frameMillis time each frame is shown for; 0 means "hold this pose, do not cycle".
 * @property gesture vertical motion layered on top of the pose.
 */
data class ResidentAnimationChoice(
    val pose: String,
    val alternate: String?,
    val frameMillis: Long,
    val gesture: ResidentGesture,
)

/**
 * Pure animation policy for the extruded Digifarm residents.
 *
 * The farm simulation decides *where* every resident stands, whether it walks or runs, and what it
 * is doing; this object turns that into a pose family. It stays free of Android/Compose types so the
 * whole decision table can be unit-tested on the JVM — the renderer only binds the returned pose and
 * cycles it with the returned cadence.
 *
 * Rules (in priority order):
 *  - walking at a run tempo: run / run2, 220 ms; without a run pose it falls through to walk;
 *  - walking: walk / walk2, 250 ms (the diorama's pre-liveliness cadence);
 *  - sleeping (new `SLEEP` or legacy `REST`): sleep, held, breathing;
 *  - training: train / train2, 360 ms;
 *  - happy social beats (`HAPPY`, legacy `PLAY`/`SOCIAL`/`SOCIALIZE`/`EAT`): happy, held, hopping;
 *  - anything else: idle / idle2, 900 ms.
 *
 * Fallback: run → walk → idle, sleep → idle, train → idle, happy → idle, walk → idle. When a set is
 * non-empty the returned [ResidentAnimationChoice.pose] always belongs to it, and [alternate] is only
 * returned when it is present, so the renderer can bind the choice without re-checking the artwork.
 * An empty set returns the nominal family (the caller has no art to bind yet); the function never
 * throws on an unknown activity or a missing pose.
 */
object ResidentPoseAnimation {
    /**
     * Restored to the cadence the diorama used before the resident-life pass (owner preference): the
     * walk pose holds 250 ms per frame, and a jog is the same pose family at a slightly quicker
     * 220 ms. The stride and the step rhythm come from the simulation, which repeats the old
     * 0.22-unit / 1.1 s stepping, so the animation is no longer solved from speed.
     */
    private const val RUN_FRAME_MILLIS = 220L
    private const val WALK_FRAME_MILLIS = 250L
    private const val TRAIN_FRAME_MILLIS = 360L
    private const val IDLE_FRAME_MILLIS = 900L

    /**
     * Decides the pose family for one resident at one frame.
     *
     * @param activity simulation activity token; compared case-insensitively and trimmed.
     * @param walking true while the renderer is interpolating a movement segment.
     * @param running true while that movement is at a jog/run tempo.
     * @param availablePoses pose names actually present in the resident's extruded GLB.
     */
    fun choice(
        activity: String,
        walking: Boolean,
        running: Boolean,
        availablePoses: Set<String>,
    ): ResidentAnimationChoice = when {
        walking && running -> runFamily(availablePoses)
        walking -> walkFamily(availablePoses)
        else -> when (activity.trim().uppercase(Locale.ROOT)) {
            "SLEEP", "REST" -> holdFamily("sleep", ResidentGesture.BREATHE, availablePoses)
            "TRAIN" -> cycleFamily(
                pose = "train",
                alternate = "train2",
                frameMillis = TRAIN_FRAME_MILLIS,
                gesture = ResidentGesture.NONE,
                availablePoses = availablePoses,
            ) ?: idleFamily(availablePoses, ResidentGesture.NONE)
            "HAPPY", "PLAY", "SOCIAL", "SOCIALIZE", "EAT" ->
                holdFamily("happy", ResidentGesture.HOP, availablePoses)
            else -> idleFamily(availablePoses, ResidentGesture.NONE)
        }
    }

    private fun runFamily(availablePoses: Set<String>): ResidentAnimationChoice =
        cycleFamily(
            pose = "run",
            alternate = "run2",
            frameMillis = RUN_FRAME_MILLIS,
            gesture = ResidentGesture.NONE,
            availablePoses = availablePoses,
        ) ?: walkFamily(availablePoses)

    private fun walkFamily(availablePoses: Set<String>): ResidentAnimationChoice =
        cycleFamily(
            pose = "walk",
            alternate = "walk2",
            frameMillis = WALK_FRAME_MILLIS,
            gesture = ResidentGesture.NONE,
            availablePoses = availablePoses,
        ) ?: idleFamily(availablePoses, ResidentGesture.NONE)

    private fun idleFamily(
        availablePoses: Set<String>,
        gesture: ResidentGesture,
    ): ResidentAnimationChoice =
        cycleFamily(
            pose = "idle",
            alternate = "idle2",
            frameMillis = IDLE_FRAME_MILLIS,
            gesture = gesture,
            availablePoses = availablePoses,
        ) ?: ResidentAnimationChoice(
            // No idle art at all: hold whatever the resident does have instead of failing the bind.
            pose = availablePoses.first(),
            alternate = null,
            frameMillis = 0L,
            gesture = gesture,
        )

    /**
     * A two-frame family, or null when the primary pose is absent from a non-empty set (the caller
     * then falls back along its chain). With no art loaded the nominal family is returned so the
     * renderer can still log what it wanted.
     */
    private fun cycleFamily(
        pose: String,
        alternate: String,
        frameMillis: Long,
        gesture: ResidentGesture,
        availablePoses: Set<String>,
    ): ResidentAnimationChoice? {
        if (availablePoses.isNotEmpty() && pose !in availablePoses) return null
        return ResidentAnimationChoice(
            pose = pose,
            alternate = alternate.takeIf { it in availablePoses },
            frameMillis = frameMillis,
            gesture = gesture,
        )
    }

    /** A held pose family (sleep/happy) that falls back to a held idle when its own art is missing. */
    private fun holdFamily(
        pose: String,
        gesture: ResidentGesture,
        availablePoses: Set<String>,
    ): ResidentAnimationChoice {
        if (availablePoses.isEmpty()) {
            return ResidentAnimationChoice(pose, null, 0L, gesture)
        }
        val resolved = when {
            pose in availablePoses -> pose
            "idle" in availablePoses -> "idle"
            else -> availablePoses.first()
        }
        return ResidentAnimationChoice(resolved, null, 0L, gesture)
    }
}
