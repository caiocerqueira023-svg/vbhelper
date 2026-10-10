package com.github.nacabaro.vbhelper.battle.offline.tamers

object ArenaRewards {
    const val FIRST_CLEAR_BONUS = 300

    fun victoryBits(stage: Int, format: Int, difficulty: ArenaDifficulty): Int {
        require(stage in 0..5 && format in 1..2)
        return (120 + 40 * stage) * difficulty.rewardMultiplier * format
    }

    fun championshipBits(entries: Int, difficulty: ArenaDifficulty): Int {
        require(entries in setOf(8, 16))
        return entries * 150 * difficulty.rewardMultiplier
    }
}
