package com.tamerslab.transfer.data.gameSave

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryBootstrapPolicyTest {
    @Test
    fun `bootstrap level follows the owner reference point`() {
        assertEquals(75, StoryBootstrapPolicy.levelFor(7500))
        assertEquals(1, StoryBootstrapPolicy.levelFor(0))
        assertEquals(99, StoryBootstrapPolicy.levelFor(9999))
    }

    @Test
    fun `a provider is required before bootstrap can invent stats`() {
        assertFalse(StoryBootstrapPolicy.canBootstrap(null))
        val provider = object : NativeCreationStats {
            override fun statsFor(speciesId: Long, level: Int) = CreationStats(
                level = level, hp = 100, maxHp = 100, mp = 10, maxMp = 10,
                attack = 10, defense = 10, spirit = 10, speed = 10,
            )
        }
        assertTrue(StoryBootstrapPolicy.canBootstrap(provider))
        val stats = requireNotNull(provider.statsFor(77L, 75))
        assertEquals(75, stats.level)
        assertEquals(100, stats.maxHp)
    }
}
