package com.github.nacabaro.vbhelper.world.ecosystem

import com.github.nacabaro.vbhelper.battle.offline.core.*
import com.github.nacabaro.vbhelper.battle.offline.data.*
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class NpcBattleAdapterTest {
    private fun input(id:String,initial:Int?=null) = TrainingParticipantInput(instanceId=id,displayName=id,stage=3,maxHealth=3000,attack=1000,
        initialHealth=initial)

    @Test fun startingInjuryChangesCurrentHpWithoutReducingMaximumHp() {
        val maximum=TrainingBattleFactory.definition(input("a"),BattleSide.ALLIED).maxHealth
        val simulator=TrainingBattleFactory.create(listOf(input("a",maximum/2)),listOf(input("b")))
        val fighter=simulator.snapshot().alliedMembers.single()
        assertEquals(maximum,fighter.maxHealth)
        assertEquals(maximum/2,fighter.health)
    }
    @Test fun radarTwoAgainstOneUsesThreeDistinctCombatants() {
        val battle=TrainingBattleFactory.create(listOf(input("owned"),input("wild-ally")),listOf(input("wild-opponent")),allowAlliedAdvantage=true)
        assertEquals(2,battle.snapshot().alliedMembers.size)
        assertEquals(1,battle.snapshot().opposingMembers.size)
        assertThrows(IllegalArgumentException::class.java) { TrainingBattleFactory.create(listOf(input("owned"),input("wild-ally")),listOf(input("wild-opponent"))) }
    }
    @Test fun seedDefinitionAndElapsedReplayRecoverTheWholeMechanicalSnapshot() {
        val event=WorldInteraction("npc",InteractionType.BATTLE,InteractionOrigin.AUTONOMOUS,InteractionState.ACTIVE,42,
            startTick=1,nextActionTick=1,createdAt=0,expiresAt=Long.MAX_VALUE)
        val definitions=listOf(TrainingBattleFactory.definition(input("a"),BattleSide.ALLIED),TrainingBattleFactory.definition(input("b"),BattleSide.OPPOSING))
        val record=WorldNpcBattle("npc",Gson().toJson(definitions),startTick=1,nextRoundTick=1)
        val live=NpcBattleAdapter.recover(event,record)
        repeat(120) { live.advance(50) }
        val replay=NpcBattleAdapter.recover(event,record.copy(elapsedMillis=live.snapshot().elapsedMillis))
        assertEquals(live.snapshot(),replay.snapshot())
    }
    @Test fun defeatedParticipantsCannotBePassedAsStartingConditions() {
        assertThrows(IllegalArgumentException::class.java) { TrainingBattleFactory.create(listOf(input("a",0)),listOf(input("b"))) }
    }
}
