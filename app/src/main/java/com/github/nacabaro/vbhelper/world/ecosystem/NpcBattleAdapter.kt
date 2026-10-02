package com.github.nacabaro.vbhelper.world.ecosystem

import com.github.nacabaro.vbhelper.battle.offline.core.*
import com.github.nacabaro.vbhelper.battle.offline.data.GenericTechniqueCatalog
import com.google.gson.Gson

/** Recover from immutable definitions, seed and elapsed fixed steps, never from HP alone. */
object NpcBattleAdapter {
    fun recover(event:WorldInteraction,record:WorldNpcBattle):BattleSimulator {
        val definitions=Gson().fromJson(record.definitionsJson,Array<CombatantDefinition>::class.java).toList()
        val simulator=BattleSimulator(BattleConfiguration(randomSeed=event.seed,defaultPaused=false,arenaRadius=8f),
            BattleTeam("allies",BattleSide.ALLIED,definitions.filter { it.side==BattleSide.ALLIED }),
            BattleTeam("opponents",BattleSide.OPPOSING,definitions.filter { it.side==BattleSide.OPPOSING }),GenericTechniqueCatalog.battleDefinitions)
        var elapsed=0L
        while(elapsed<record.elapsedMillis && simulator.snapshot().result==null) { simulator.advance(50);elapsed+=50 }
        return simulator
    }
}
