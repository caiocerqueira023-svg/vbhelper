package com.tamerslab.rank

import com.tamerslab.rank.domain.RankCalculator
import com.tamerslab.rank.domain.RankConfig
import com.tamerslab.rank.domain.TxpSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pure progression tests: the rank ladder is a documented function of the level, and TXP awards are
 * capped by source. No Android/Room dependencies here.
 */
class RankCalculatorTest {
    private val config = RankConfig()

    @Test
    fun `level one starts at zero xp`() {
        assertEquals(1, RankCalculator.levelForTotal(0L, config))
        assertEquals(0L, config.xpForLevel(1))
    }

    @Test
    fun `level thresholds follow the linear cost`() {
        assertEquals(60, config.levelCost(1))
        assertEquals(63, config.levelCost(2))
        assertEquals(60L, config.xpForLevel(2))
        assertEquals(123L, config.xpForLevel(3))
        assertEquals(189L, config.xpForLevel(4))
        assertEquals(1, RankCalculator.levelForTotal(59L, config))
        assertEquals(2, RankCalculator.levelForTotal(60L, config))
        assertEquals(4, RankCalculator.levelForTotal(189L, config))
    }

    @Test
    fun `rank milestones match the configured levels`() {
        assertEquals(0, config.rankForLevel(1))
        assertEquals(0, config.rankForLevel(3))
        assertEquals(1, config.rankForLevel(4))
        assertEquals(14, config.rankForLevel(95))
        assertEquals(15, config.rankForLevel(96))
    }

    @Test
    fun `rank fifteen needs level ninety six and about nineteen thousand xp`() {
        val total = config.xpForLevel(96)
        assertEquals(19095L, total)
        assertEquals(96, RankCalculator.levelForTotal(total, config))
        assertEquals(15, RankCalculator.rankForTotal(total, config))
        assertNull(config.nextRankLevel(15))
    }

    @Test
    fun `level progress is in range and monotonic at the boundary`() {
        assertEquals(0f, RankCalculator.levelProgress(0L, config), 0.0001f)
        assertEquals(0.5f, RankCalculator.levelProgress(30L, config), 0.0001f)
        // At the exact level cost the new level starts, so progress resets to zero.
        assertEquals(0f, RankCalculator.levelProgress(60L, config), 0.0001f)
        assertEquals(1f, RankCalculator.levelProgress(config.xpForLevel(100), config), 0.0001f)
    }

    @Test
    fun `rank progress reaches one at the next milestone`() {
        val end = config.xpForLevel(8)
        // One XP before the milestone the bar is almost full; reaching it starts the next rank.
        assertTrue(RankCalculator.rankProgress(end - 1, config) > 0.98f)
        assertEquals(0f, RankCalculator.rankProgress(end, config), 0.0001f)
        // The final rank has no next milestone, so it reads as complete.
        assertEquals(1f, RankCalculator.rankProgress(config.xpForLevel(96), config), 0.0001f)
    }

    @Test
    fun `regional caps clamp suspicious awards`() {
        assertEquals(200, RankCalculator.clampAward(TxpSource.BATTLE_WIN, 9999, config))
        assertEquals(80, RankCalculator.clampAward(TxpSource.BATTLE_LOSS, 9999, config))
        assertEquals(40, RankCalculator.clampAward(TxpSource.NFC_READ, 9999, config))
        assertEquals(0, RankCalculator.clampAward(TxpSource.NFC_READ, -5, config))
    }

    @Test
    fun `remaining xp never goes negative`() {
        assertEquals(0L, RankCalculator.levelRemaining(config.xpForLevel(100), config))
        assertEquals(
            60L - 25L,
            RankCalculator.levelRemaining(25L, config),
        )
    }
}
