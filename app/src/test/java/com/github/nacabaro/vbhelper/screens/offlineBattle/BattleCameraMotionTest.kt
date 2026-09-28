package com.github.nacabaro.vbhelper.screens.offlineBattle

import com.github.nacabaro.vbhelper.battle.offline.core.BattleImpactSnapshot
import com.github.nacabaro.vbhelper.battle.offline.core.BattlePosition
import com.github.nacabaro.vbhelper.battle.offline.core.CombatantState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BattleCameraMotionTest {
    @Test
    fun manualFocusWinsOverCinematicAction() {
        val cues = listOf(
            BattleCameraCue("manual", BattlePosition(5f, 1f), CombatantState.IDLE),
            BattleCameraCue("actor", BattlePosition(-4f, 2f), CombatantState.USING_SPECIAL),
        )

        val focus = chooseBattleCameraFocus(
            manualFighterId = "manual",
            cues = cues,
            impacts = listOf(BattleImpactSnapshot(9L, "actor", 100, true, 200L)),
        )

        assertEquals("manual", focus?.fighterId)
        assertEquals(1f, focus?.weight ?: 0f, 0f)
    }

    @Test
    fun impactThenSpecialThenAttackDriveAutomaticFocus() {
        val cues = listOf(
            BattleCameraCue("impact", BattlePosition(3f, 0f), CombatantState.IDLE),
            BattleCameraCue("special", BattlePosition(1f, 0f), CombatantState.USING_SPECIAL),
            BattleCameraCue("attack", BattlePosition(-2f, 0f), CombatantState.ATTACK_ACTIVE),
        )

        assertEquals(
            "impact",
            chooseBattleCameraFocus(null, cues, listOf(BattleImpactSnapshot(7L, "impact", 40, false, 300L)))?.fighterId,
        )
        assertEquals("special", chooseBattleCameraFocus(null, cues, emptyList())?.fighterId)
        assertEquals(
            "attack",
            chooseBattleCameraFocus(null, cues.filterNot { it.fighterId == "special" }, emptyList())?.fighterId,
        )
    }

    @Test
    fun oneOnOneAutomaticTargetIsTheMidpointBetweenBothFighters() {
        val cues = listOf(
            BattleCameraCue("ally", BattlePosition(-4f, 2f), CombatantState.ATTACK_ACTIVE),
            BattleCameraCue("opponent", BattlePosition(6f, -2f), CombatantState.USING_SPECIAL),
        )

        val target = chooseBattleCameraTarget(
            manualFighterId = null,
            cues = cues,
            impacts = listOf(BattleImpactSnapshot(7L, "opponent", 40, false, 300L)),
        )

        assertEquals(1f, target.x, 0.0001f)
        assertEquals(0f, target.z, 0.0001f)
    }

    @Test
    fun oneOnOneCentersTheFighterExplicitlySelectedByTheUser() {
        val cues = listOf(
            BattleCameraCue("ally", BattlePosition(-4f, 2f), CombatantState.IDLE),
            BattleCameraCue("opponent", BattlePosition(6f, -2f), CombatantState.IDLE),
        )

        val target = chooseBattleCameraTarget("ally", cues, emptyList())

        assertEquals(-4f, target.x, 0.0001f)
        assertEquals(2f, target.z, 0.0001f)
    }

    @Test
    fun automaticOrbitIsFrameRateIndependentAndPausesForManualControl() {
        val oneStep = advanceBattleCameraYaw(1.0, deltaSeconds = 1.0 / 30.0, automatic = true)
        val twoSteps = advanceBattleCameraYaw(
            advanceBattleCameraYaw(1.0, deltaSeconds = 1.0 / 60.0, automatic = true),
            deltaSeconds = 1.0 / 60.0,
            automatic = true,
        )

        assertEquals(oneStep, twoSteps, 0.000001)
        assertEquals(1.0, advanceBattleCameraYaw(1.0, 1.0, automatic = false), 0.0)
        assertTrue(oneStep > 1.0)
    }

    @Test
    fun exponentialCameraApproachIsFrameRateIndependent() {
        val oneStep = approachCameraAxis(0.0, 10.0, deltaSeconds = 1.0 / 30.0)
        val twoSteps = approachCameraAxis(
            approachCameraAxis(0.0, 10.0, deltaSeconds = 1.0 / 60.0),
            10.0,
            deltaSeconds = 1.0 / 60.0,
        )

        assertEquals(oneStep, twoSteps, 0.000001)
        assertTrue(oneStep in 0.0..10.0)
    }

    @Test
    fun impactShakeDecaysAndCriticalHitIsStronger() {
        val normal = battleImpactShake(impactId = 3L, remainingMillis = 260L, critical = false)
        val critical = battleImpactShake(impactId = 3L, remainingMillis = 260L, critical = true)
        val ended = battleImpactShake(impactId = 3L, remainingMillis = 0L, critical = true)

        assertTrue(critical.magnitude() > normal.magnitude())
        assertEquals(0.0, ended.magnitude(), 0.000001)
    }
}
