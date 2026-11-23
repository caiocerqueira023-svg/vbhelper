package com.tamerslab.transfer.data

import com.tamerslab.domain.transfer.ProgressDomainRules
import com.tamerslab.domain.transfer.ProgressSeedSource
import com.tamerslab.domain.transfer.StoryProgressDomain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressDomainRulesTest {
    private fun story(seed: ProgressSeedSource = ProgressSeedSource.IMPORT) = StoryProgressDomain(
        individualId = "individual-1",
        level = 75,
        expTotal = 1234L,
        hp = 400,
        maxHp = 400,
        mp = 80,
        maxMp = 80,
        attack = 120,
        defense = 90,
        spirit = 110,
        speed = 100,
        aptitude = 60,
        seededAt = 1_700_000_000_000L,
        seedSource = seed,
        revision = 1L,
        updatedAt = 1_700_000_000_000L,
    )

    @Test
    fun `vital points curve maps the owner example to level 75`() {
        assertEquals(75, ProgressDomainRules.levelForVitalPoints(7500))
        assertEquals(1, ProgressDomainRules.levelForVitalPoints(0))
        assertEquals(99, ProgressDomainRules.levelForVitalPoints(9999))
    }

    @Test
    fun `vital points and level round trip within one level step`() {
        val level = ProgressDomainRules.levelForVitalPoints(7500)
        val points = ProgressDomainRules.vitalPointsForLevel(level)
        assertTrue(kotlin.math.abs(points - 7500) <= 9999 / 98 + 1)
        assertEquals(level, ProgressDomainRules.levelForVitalPoints(points))
    }

    @Test
    fun `story seeding happens exactly once`() {
        assertTrue(ProgressDomainRules.needsStorySeed(null))
        assertFalse(ProgressDomainRules.needsStorySeed(story()))
    }

    @Test
    fun `clamping keeps story values inside every title range`() {
        val wild = story().copy(
            level = 500,
            expTotal = -5,
            hp = 99_999,
            maxHp = 0,
            mp = 50,
            maxMp = 10,
            attack = -3,
            aptitude = 500,
        )
        val clamped = ProgressDomainRules.clampStory(wild)
        assertEquals(99, clamped.level)
        assertEquals(0L, clamped.expTotal)
        assertEquals(1, clamped.maxHp)
        assertTrue(clamped.hp <= clamped.maxHp)
        assertTrue(clamped.mp <= clamped.maxMp)
        assertEquals(0, clamped.attack)
        assertEquals(99, clamped.aptitude)
    }
}
