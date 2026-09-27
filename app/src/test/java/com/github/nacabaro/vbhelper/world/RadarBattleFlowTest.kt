package com.github.nacabaro.vbhelper.world

import com.github.cfogrady.vbnfc.data.NfcCharacter
import com.github.nacabaro.vbhelper.battle.offline.core.BattleOutcome
import com.github.nacabaro.vbhelper.battle.offline.data.VitalStatScale
import com.github.nacabaro.vbhelper.domain.world.RecruitmentState
import com.github.nacabaro.vbhelper.dtos.WorldDtos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RadarBattleFlowTest {
    @Test
    fun `victory records a win and removes the defeated spawn`() {
        val effect = BattleOutcome.ALLIED_VICTORY.toRadarBattleEffect()

        assertTrue(effect.recordsBattle)
        assertEquals(true, effect.won)
        assertTrue(effect.removesSpawn)
    }

    @Test
    fun `defeat records a loss and keeps the wild Digimon on the radar`() {
        val effect = BattleOutcome.OPPOSING_VICTORY.toRadarBattleEffect()

        assertTrue(effect.recordsBattle)
        assertEquals(false, effect.won)
        assertFalse(effect.removesSpawn)
    }

    @Test
    fun `draw and abandonment do not distort win rate`() {
        listOf(BattleOutcome.DRAW, BattleOutcome.ABANDONED).forEach { outcome ->
            val effect = outcome.toRadarBattleEffect()

            assertFalse(effect.recordsBattle)
            assertNull(effect.won)
            assertFalse(effect.removesSpawn)
        }
    }

    @Test
    fun `wild participant keeps all three card stats and a stable individual personality`() {
        val spawn = WorldDtos.SpawnWithDetails(
            id = 7L,
            cardCharacterId = 19L,
            individualId = "wild-individual-19",
            latitude = 0.0,
            longitude = 0.0,
            spawnedAt = 1L,
            expiresAt = 2L,
            interacted = false,
            charaIndex = 2,
            stage = 3,
            cardId = 12L,
            attribute = NfcCharacter.Attribute.Vaccine,
            baseHp = 3700,
            baseBp = 1450,
            baseAp = 1180,
            isBemCard = true,
            spriteIdle = byteArrayOf(1),
            spriteIdle2 = byteArrayOf(2),
            spriteWalk = byteArrayOf(3),
            spriteWalk2 = byteArrayOf(4),
            spriteRun = byteArrayOf(5),
            spriteRun2 = byteArrayOf(6),
            spriteWidth = 1,
            spriteHeight = 1,
            speciesName = "Testmon",
            mood = 72,
            recruitmentState = RecruitmentState.WILD
        )

        val first = worldRadarBattleParticipant(spawn)
        val second = worldRadarBattleParticipant(spawn.copy(id = 99L))

        assertEquals(3700, first.vitalStats?.baseHp)
        assertEquals(1450, first.vitalStats?.baseBp)
        assertEquals(1180, first.vitalStats?.baseAp)
        assertEquals(VitalStatScale.BEM, first.vitalStats?.scale)
        assertEquals("dim012_mon03", first.externalCharacterId)
        assertEquals(first.personalityType, second.personalityType)
        assertEquals("wild-individual-19", first.stableRngKey)
        assertEquals("wild-individual-19", first.stableId)
        assertEquals(1, first.spriteSet?.width)
        assertTrue(first.spriteSet?.idle.contentEquals(byteArrayOf(1)))
    }
}
