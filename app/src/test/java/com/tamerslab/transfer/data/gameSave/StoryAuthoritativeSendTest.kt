package com.tamerslab.transfer.data.gameSave

import com.tamerslab.domain.transfer.ProgressSeedSource
import com.tamerslab.domain.transfer.StoryProgressDomain
import com.tamerslab.gamesave.WorldDsStatConversion
import org.junit.Assert.assertEquals
import org.junit.Test

/** A tracked story block must be written exactly, not re-converted from the app/VB state. */
class StoryAuthoritativeSendTest {
    private fun story() = StoryProgressDomain(
        individualId = "individual-story",
        level = 63,
        expTotal = 123_456L,
        hp = 3_210,
        maxHp = 4_000,
        mp = 210,
        maxMp = 260,
        attack = 540,
        defense = 480,
        spirit = 410,
        speed = 95,
        aptitude = 74,
        seededAt = 1_700_000_000_000L,
        seedSource = ProgressSeedSource.IMPORT,
        revision = 4L,
        updatedAt = 1_700_000_000_000L,
    )

    private fun appStats() = WorldDsStatConversion.AppStats(
        stage = 3, baseHp = 40, baseBp = 30, baseAp = 20,
        trainingHp = 12, trainingBp = 8, trainingAp = 5, vitalPoints = 4200, mood = 60,
        wins = 2, losses = 1, generation = 2, rank = 1,
    )

    @Test
    fun `world ds writes the tracked story level and stats exactly`() {
        val state = WorldDsOutboundState(
            dsSpeciesId = 2,
            canonicalSpeciesId = "digimon.greymon",
            displayName = "CAIO",
            fallbackName = "GREYMON",
            stage = 3,
            baseHp = 40,
            baseBp = 30,
            baseAp = 20,
            appStats = appStats(),
        )
        val record = WorldDsOutboundConversion.generate(
            state, seedBase64 = null, nativeStats = StoryProgressTracking.toWorldDsNative(story()),
        )
        val decoded = requireNotNull(WorldDsReceiveConversion.nativeStats(record))
        assertEquals(63, decoded.level)
        assertEquals(4_000, decoded.maxHp)
        assertEquals(3_210, decoded.hp)
        assertEquals(260, decoded.maxMp)
        assertEquals(540, decoded.attack)
        assertEquals(480, decoded.defense)
        assertEquals(410, decoded.spirit)
        assertEquals(95, decoded.speed)
        assertEquals(74, decoded.aptitude)
        assertEquals(123_456L, decoded.exp.sum())
    }

    @Test
    fun `lost evolution writes the tracked story level and stats exactly`() {
        val state = LeOutboundState(
            dsSpeciesId = 77,
            canonicalSpeciesId = "digimon.agumon",
            displayName = "Agumon",
            fallbackName = "Agumon",
            appStats = appStats(),
            defaults = null,
            aptitude = 100,
        )
        val record = LeOutboundConversion.generate(
            state, seedBase64 = null, nativeStats = StoryProgressTracking.toLeNative(story()),
        )
        val decoded = requireNotNull(LeStatConversion.nativeStats(record))
        assertEquals(63, decoded.level)
        assertEquals(4_000, decoded.maxHp)
        assertEquals(3_210, decoded.hp)
        assertEquals(540, decoded.attack)
        assertEquals(480, decoded.defense)
        assertEquals(410, decoded.spirit)
        assertEquals(95, decoded.speed)
        assertEquals(74, decoded.aptitude)
    }
}
