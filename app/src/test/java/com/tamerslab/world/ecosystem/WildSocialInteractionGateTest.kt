package com.tamerslab.world.ecosystem

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Owner decision (2026-11-04): wild↔wild chat and battles are paused for current versions while the
 * code is retained. These tests pin the exact family the gate covers so a future re-enable cannot
 * accidentally pause player encounters too.
 */
class WildSocialInteractionGateTest {

    @Test
    fun `the default build pauses wild pair chat and battle`() {
        assertTrue(WildSocialInteractionGate.paused)
        assertFalse(WildSocialInteractionGate.ENABLED)
        assertTrue(
            WildSocialInteractionGate.pauses(
                InteractionOrigin.AUTONOMOUS, InteractionType.CHAT, wildParticipants = 2, totalParticipants = 2
            )
        )
        assertTrue(
            WildSocialInteractionGate.pauses(
                InteractionOrigin.AUTONOMOUS, InteractionType.BATTLE, wildParticipants = 2, totalParticipants = 2
            )
        )
    }

    @Test
    fun `wild to player encounters stay allowed`() {
        // A wild → trainer greeting/challenge has one wild participant and no owned participant row.
        assertFalse(
            WildSocialInteractionGate.pauses(
                InteractionOrigin.AUTONOMOUS, InteractionType.CHAT, wildParticipants = 1, totalParticipants = 1
            )
        )
        assertFalse(
            WildSocialInteractionGate.pauses(
                InteractionOrigin.AUTONOMOUS, InteractionType.BATTLE, wildParticipants = 1, totalParticipants = 1
            )
        )
    }

    @Test
    fun `player directed and joined encounters are never paused`() {
        assertFalse(
            WildSocialInteractionGate.pauses(
                InteractionOrigin.DIRECT_PLAYER, InteractionType.BATTLE, wildParticipants = 1, totalParticipants = 2
            )
        )
        assertFalse(
            WildSocialInteractionGate.pauses(
                InteractionOrigin.DIRECT_PLAYER, InteractionType.CHAT, wildParticipants = 1, totalParticipants = 1
            )
        )
        assertFalse(
            WildSocialInteractionGate.pauses(
                InteractionOrigin.JOINED_PLAYER, InteractionType.BATTLE, wildParticipants = 2, totalParticipants = 3
            )
        )
    }

    @Test
    fun `re-enabling the gate restores the retained wild pair behaviour`() {
        assertFalse(
            WildSocialInteractionGate.pauses(
                InteractionOrigin.AUTONOMOUS, InteractionType.CHAT, wildParticipants = 2, totalParticipants = 2,
                enabled = true,
            )
        )
        assertFalse(
            WildSocialInteractionGate.pauses(
                InteractionOrigin.AUTONOMOUS, InteractionType.BATTLE, wildParticipants = 2, totalParticipants = 2,
                enabled = true,
            )
        )
    }
}
