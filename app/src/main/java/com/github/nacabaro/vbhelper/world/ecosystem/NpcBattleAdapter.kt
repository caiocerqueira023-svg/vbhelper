package com.github.nacabaro.vbhelper.world.ecosystem

import com.github.nacabaro.vbhelper.battle.offline.core.*
import com.github.nacabaro.vbhelper.battle.offline.data.GenericTechniqueCatalog
import com.github.nacabaro.vbhelper.battle.offline.data.OfflineBattleRuleset
import com.google.gson.Gson
import com.google.gson.JsonParser

/** Recover from immutable definitions, seed and elapsed fixed steps, never from HP alone. */
object NpcBattleAdapter {
    private data class Checkpoint(
        val formatVersion: Int,
        val catalogVersion: Int,
        val configuration: BattleConfiguration,
        val definitions: List<CombatantDefinition>
    )

    fun encode(definitions: List<CombatantDefinition>, configuration: BattleConfiguration): String {
        require(configuration.rulesetVersion in BattleRules.LEGACY_VERSION..BattleRules.CURRENT_VERSION)
        val catalog = if (configuration.rulesetVersion == BattleRules.LEGACY_VERSION) 2 else OfflineBattleRuleset.CATALOG_VERSION
        return Gson().toJson(Checkpoint(1, catalog, configuration, definitions))
    }

    fun definitions(record: WorldNpcBattle): List<CombatantDefinition> {
        val root = JsonParser.parseString(record.definitionsJson)
        return if (root.isJsonArray) Gson().fromJson(root, Array<CombatantDefinition>::class.java).toList()
        else Gson().fromJson(root, Checkpoint::class.java).definitions
    }

    fun recover(event:WorldInteraction,record:WorldNpcBattle):BattleSimulator {
        val root = JsonParser.parseString(record.definitionsJson)
        val checkpoint = if (root.isJsonArray) Checkpoint(1, 2,
            BattleConfiguration(randomSeed = event.seed, defaultPaused = false, arenaRadius = 8f,
                rulesetVersion = BattleRules.LEGACY_VERSION),
            Gson().fromJson(root, Array<CombatantDefinition>::class.java).toList())
        else Gson().fromJson(root, Checkpoint::class.java)
        require(checkpoint.formatVersion == 1) { "Unsupported NPC battle checkpoint format" }
        require(checkpoint.configuration.rulesetVersion in BattleRules.LEGACY_VERSION..BattleRules.CURRENT_VERSION) {
            "Unsupported NPC battle ruleset"
        }
        require(checkpoint.catalogVersion == checkpoint.configuration.rulesetVersion) { "Incompatible NPC battle catalog" }
        require(checkpoint.configuration.randomSeed == event.seed) { "NPC battle seed mismatch" }
        require(record.elapsedMillis >= 0 && record.elapsedMillis % BattleRules.STEP_MILLIS == 0L) {
            "NPC battle checkpoint is not on a fixed-step boundary"
        }
        val definitions = checkpoint.definitions
        val simulator=BattleSimulator(checkpoint.configuration.copy(defaultPaused = false),
            BattleTeam("allies",BattleSide.ALLIED,definitions.filter { it.side==BattleSide.ALLIED }),
            BattleTeam("opponents",BattleSide.OPPOSING,definitions.filter { it.side==BattleSide.OPPOSING }),
            GenericTechniqueCatalog.definitionsForVersion(checkpoint.catalogVersion))
        var elapsed=0L
        while(elapsed<record.elapsedMillis && simulator.snapshot().result==null) {
            simulator.advance(BattleRules.STEP_MILLIS)
            elapsed += BattleRules.STEP_MILLIS
        }
        return simulator
    }
}
