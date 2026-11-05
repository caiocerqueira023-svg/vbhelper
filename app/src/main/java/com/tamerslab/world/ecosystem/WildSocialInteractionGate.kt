package com.tamerslab.world.ecosystem

/**
 * Owner decision (2026-11-04): **wild Digimon ↔ wild Digimon chat and battles are paused for the
 * current versions.** Nothing is deleted: the social planner, dialogue service, NPC battle
 * simulation, persistence, history and UI paths all stay in the codebase. This gate only unwires the
 * autonomous pair scheduling/creation/activation, closes pairs left open by a previous build and
 * hides them from the public ecosystem snapshot.
 *
 * Wild → player greetings/challenges and every player-directed encounter (chat, private chat,
 * Radar battles, joined battles, AR battles) are deliberately unaffected. Turning this back on is a
 * one-line change when the owner wants those interactions again.
 */
internal object WildSocialInteractionGate {

    /** Master switch for this feature family. Retained code is ready to resume when flipped. */
    const val ENABLED: Boolean = false

    val paused: Boolean get() = !ENABLED

    /**
     * True exactly for the paused family: an autonomous interaction whose participants are two or
     * more wild Digimon and no owned partner. Encounters that include the trainer (one wild
     * participant) or player-originated/joined encounters are not paused.
     */
    fun pauses(
        origin: InteractionOrigin,
        type: InteractionType,
        wildParticipants: Int,
        totalParticipants: Int,
        enabled: Boolean = ENABLED,
    ): Boolean {
        if (enabled) return false
        if (origin != InteractionOrigin.AUTONOMOUS) return false
        if (type != InteractionType.CHAT && type != InteractionType.BATTLE) return false
        return wildParticipants >= 2 && wildParticipants == totalParticipants
    }
}
