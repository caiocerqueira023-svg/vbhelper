package com.github.nacabaro.vbhelper.world.ecosystem

import com.github.nacabaro.vbhelper.battle.offline.core.BattleOutcome
import org.junit.Assert.*
import org.junit.Test

class WorldPrivateDuelPolicyTest {
    private val accepted=DialogueProposal(DialogueIntentType.ACCEPT_CHALLENGE,"hackmon",listOf("trainer"),
        listOf("private:12","this:hackmon"),"If Hackmon wins the trainer reveals their name; if the trainer wins Hackmon obeys.",sparring=false)

    @Test fun acceptingThePlayersWagerStartsANonlethalDuelEvenWithAnAggressivePersona() {
        assertEquals(PrivateDuelDisposition.ACCEPTED_FRIENDLY,WorldPrivateDuelPolicy.classify(accepted,"hackmon",12,setOf(12,13)))
    }
    @Test fun anInvitationNeedsAcceptanceButARefusalOrUnattributedReplyCannotStartCombat() {
        assertEquals(PrivateDuelDisposition.INVITATION,WorldPrivateDuelPolicy.classify(accepted.copy(type=DialogueIntentType.CHALLENGE_BATTLE,sparring=true),"hackmon",12,setOf(12,13)))
        assertNull(WorldPrivateDuelPolicy.classify(accepted.copy(type=DialogueIntentType.DECLINE_CHALLENGE),"hackmon",12,setOf(12,13)))
        assertNull(WorldPrivateDuelPolicy.classify(accepted,"other",12,setOf(12,13)))
        assertNull(WorldPrivateDuelPolicy.classify(accepted,"hackmon",99,setOf(12,13)))
    }
    @Test fun friendlyVictoriesKeepTheOpponentWhileHostileVictoriesRetainTheirExistingPolicy() {
        assertEquals(InteractionBattleEffects(true,false),WorldInteractionPolicy.battleEffects(InteractionOrigin.DIRECT_PLAYER,BattleOutcome.ALLIED_VICTORY,friendly=true))
        assertEquals(InteractionBattleEffects(true,true),WorldInteractionPolicy.battleEffects(InteractionOrigin.DIRECT_PLAYER,BattleOutcome.ALLIED_VICTORY,friendly=false))
    }
    @Test fun resultReactionUsesTheWildsPerspectiveAndTheOriginalWager() {
        val memory=WorldBattleMemory("battle","hackmon",100,"Hackmon","Agumon",BattleMemoryPerspective.WON,true,
            accepted.reason,"""[{"role":"user","speaker":"Trainer","text":"Let's battle; if you win I tell you my name."},{"role":"assistant","speaker":"Hackmon","text":"If I win you tell me your name; if you win I will obey you."}]""",1000,needsReaction=true)
        val prompt=WorldBattleMemoryPrompts.reaction(memory)
        assertTrue(prompt.contains("WON"))
        assertTrue(prompt.contains("if you win I tell you my name"))
        assertTrue(prompt.contains("ask"))
        assertTrue(prompt.contains("wager"))
        assertFalse(prompt.contains("configured name"))
        assertTrue(WorldBattleMemoryPrompts.reaction(memory.copy(perspective=BattleMemoryPerspective.LOST)).contains("LOST"))
    }
}
