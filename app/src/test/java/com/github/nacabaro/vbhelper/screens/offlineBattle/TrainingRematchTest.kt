package com.github.nacabaro.vbhelper.screens.offlineBattle

import com.github.nacabaro.vbhelper.battle.offline.core.*
import com.github.nacabaro.vbhelper.battle.offline.data.GenericTechniqueCatalog
import com.github.nacabaro.vbhelper.battle.offline.data.TrainingBattleFactory
import org.junit.Assert.*
import org.junit.Test

class TrainingRematchTest {
    private val arena = OfflineArenaManifest("fixture.glb", 1f, 1f, 1f, 8f, 10.0,
        2f, .5f, 1f, 12.0, .3, 0.0, 5.0, 20.0, .1, 1.0)
    private val configuration = BattleConfiguration(defaultPaused = true, randomSeed = 41L, arenaRadius = 8f)
    private val ally = CombatantDefinition("ally:a", displayName = "Partner", side = BattleSide.ALLIED,
        maxHealth = 1000, maxEnergy = 100, attack = 100, defense = 40, movementSpeed = 3f, techniqueIds = emptyList())
    private val opponent = ally.copy(combatantId = "opponent:b", displayName = "Opponent", side = BattleSide.OPPOSING)
    private fun art(id: String, key: String) = BattleFighterPresentation(id, id, id, byteArrayOf(1, 2, 3),
        setOf("idle"), key, emptyMap(), emptyMap())

    private fun prepared(): TrainingBattlePresentation {
        val simulator = BattleSimulator(configuration,
            BattleTeam(configuration.alliedTeamId, BattleSide.ALLIED, listOf(ally)),
            BattleTeam(configuration.opposingTeamId, BattleSide.OPPOSING, listOf(opponent)),
            GenericTechniqueCatalog.definitionsForVersion(configuration.rulesetVersion), TrainingBattleFactory.trainingItems)
        return TrainingBattlePresentation(simulator, listOf(ally), listOf(opponent),
            mapOf(ally.combatantId to art(ally.combatantId, "base-a"), opponent.combatantId to art(opponent.combatantId, "base-b")),
            arena, configuration = configuration)
    }

    @Test fun rematchResetsCombatButRetainsAllPreparedArt() {
        val previous = prepared()
        previous.simulator.setPaused(false)
        previous.simulator.advance(100)
        previous.simulator.abandon()
        val next = previous.rematch(42L)
        assertNotSame(previous.simulator, next.simulator)
        assertSame(previous.fighters, next.fighters)
        assertSame(previous.preparedForms, next.preparedForms)
        assertSame(previous.arenaManifest, next.arenaManifest)
        assertEquals(42L, next.configuration.randomSeed)
        assertEquals(0L, next.simulator.snapshot().elapsedMillis)
        assertNull(next.simulator.snapshot().result)
        assertEquals(1000, next.simulator.snapshot().alliedMembers.single().health)
        assertTrue(next.simulator.snapshot().isPaused)
    }

    @Test fun rendererOnlyReusesReadyMatchingBaseAndTransformationAssets() {
        val base = prepared().fighters
        val forms = mapOf("form-a" to art(ally.combatantId, "form-a"))
        assertTrue(canReuseBattleRenderAssets(true, base, base, forms, forms))
        assertFalse(canReuseBattleRenderAssets(false, base, base, forms, forms))
        assertFalse(canReuseBattleRenderAssets(true, base, emptyMap(), forms, forms))
        assertFalse(canReuseBattleRenderAssets(true, base, base, forms, emptyMap()))
        val changed = base + (ally.combatantId to art(ally.combatantId, "changed-a"))
        assertFalse(canReuseBattleRenderAssets(true, base, changed, forms, forms))
    }
}
