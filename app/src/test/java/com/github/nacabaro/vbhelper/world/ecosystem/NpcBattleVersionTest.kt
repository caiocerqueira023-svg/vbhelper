package com.github.nacabaro.vbhelper.world.ecosystem

import com.github.nacabaro.vbhelper.battle.offline.core.*
import com.github.nacabaro.vbhelper.battle.offline.data.*
import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class NpcBattleVersionTest {
    private val event = WorldInteraction("npc", InteractionType.BATTLE, InteractionOrigin.AUTONOMOUS,
        InteractionState.ACTIVE, 42, startTick = 1, nextActionTick = 1, createdAt = 0, expiresAt = Long.MAX_VALUE)
    private val definitions = listOf(BattleSide.ALLIED, BattleSide.OPPOSING).map { side ->
        TrainingBattleFactory.definition(TrainingParticipantInput(side.name, displayName = side.name,
            stage = 3, maxHealth = 3000, attack = 1000), side)
    }

    @Test fun newCheckpointPinsMechanicsCatalogAndFullConfiguration() {
        val configuration = BattleConfiguration(randomSeed = 42, arenaRadius = 6f, decisionIntervalMillis = 136)
        val json = NpcBattleAdapter.encode(definitions, configuration)
        val record = WorldNpcBattle("npc", json, startTick = 1, nextRoundTick = 1)
        val live = NpcBattleAdapter.recover(event, record)
        repeat(137) { live.advance(34) }
        val replay = NpcBattleAdapter.recover(event, record.copy(elapsedMillis = live.snapshot().elapsedMillis))
        assertEquals(live.snapshot(), replay.snapshot())
        assertEquals(OfflineBattleRuleset.VERSION, replay.snapshot().rulesetVersion)
    }

    @Test fun oldArrayCheckpointUsesLegacyRulesAndReplaysExactFixedSteps() {
        val array = JsonParser.parseString(Gson().toJson(definitions)).asJsonArray
        array.forEach { element ->
            listOf("aiProfile", "readinessRegenerationPerSecond", "counterTechniqueId", "signatureTechniqueId", "signaturePowerBonus")
                .forEach(element.asJsonObject::remove)
        }
        val record = WorldNpcBattle("npc", array.toString(), startTick = 1, nextRoundTick = 1)
        val live = NpcBattleAdapter.recover(event, record)
        repeat(121) { live.advance(50) }
        val replay = NpcBattleAdapter.recover(event, record.copy(elapsedMillis = live.snapshot().elapsedMillis))
        assertEquals(live.snapshot(), replay.snapshot())
        assertEquals(2, replay.snapshot().rulesetVersion)
    }

    @Test fun unknownCheckpointVersionIsRejectedInsteadOfReplayedUsingCurrentRules() {
        val json = NpcBattleAdapter.encode(definitions, BattleConfiguration(randomSeed = 42))
            .replace("\"rulesetVersion\":3", "\"rulesetVersion\":999")
        assertThrows(IllegalArgumentException::class.java) {
            NpcBattleAdapter.recover(event, WorldNpcBattle("npc", json, startTick = 1, nextRoundTick = 1))
        }
    }

    @Test fun checkpointCannotChangeItsSeedOrReplayAPartialMechanicalStep() {
        val record = WorldNpcBattle("npc", NpcBattleAdapter.encode(definitions, BattleConfiguration(randomSeed = 42)),
            startTick = 1, nextRoundTick = 1)
        assertThrows(IllegalArgumentException::class.java) { NpcBattleAdapter.recover(event.copy(seed = 43), record) }
        assertThrows(IllegalArgumentException::class.java) { NpcBattleAdapter.recover(event, record.copy(elapsedMillis = 35)) }
    }
}
