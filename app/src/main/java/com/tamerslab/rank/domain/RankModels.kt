package com.tamerslab.rank.domain

import com.tamerslab.R

/**
 * Tamer XP sources. Everything the app does with a Digimon (of any supported origin) can feed the
 * Tamer profile; the rank itself is derived from level milestones, mirroring the shipped VB Lab
 * help text ("gaining EXP raises Tamer Level; reaching certain levels raises Tamer Rank").
 */
enum class TxpSource(val defaultAmount: Int, val dailyCap: Int) {
    NFC_READ(defaultAmount = 8, dailyCap = 40),
    NFC_WRITE(defaultAmount = 15, dailyCap = 60),
    NFC_TRANSFER(defaultAmount = 15, dailyCap = 60),
    NFC_MISSION(defaultAmount = 20, dailyCap = 60),
    BATTLE_WIN(defaultAmount = 30, dailyCap = 200),
    BATTLE_LOSS(defaultAmount = 8, dailyCap = 80),
    QUEST(defaultAmount = 40, dailyCap = 250),
    DEX_NEW(defaultAmount = 40, dailyCap = 120),
    DEX_EVOLUTION(defaultAmount = 20, dailyCap = 60),
    ITEM_USE(defaultAmount = 2, dailyCap = 40),
    STORAGE(defaultAmount = 5, dailyCap = 30),
    WORLD(defaultAmount = 10, dailyCap = 100),
    ADVENTURE(defaultAmount = 15, dailyCap = 90),
    DAILY(defaultAmount = 20, dailyCap = 20),
}

enum class RewardKind { CURRENCY, COSMETIC, TITLE, UNLOCK, ITEM }

/**
 * A single rank-up reward. [itemId] is only consumed when the item catalogue is available; the
 * default table stays on currency/metadata rewards so the offline build never depends on a
 * particular item row existing.
 */
data class RankReward(
    val rank: Int,
    val slot: Int,
    val kind: RewardKind,
    val amount: Int = 0,
    val itemId: Long? = null,
    val labelKey: String = "",
)

/** One of the 16 original VB Lab emblems (`tamerrankicon_0..15`). No invented tier names. */
object TamerRanks {
    const val COUNT = 16
    private val emblems = intArrayOf(
        R.drawable.vb_rank_00, R.drawable.vb_rank_01, R.drawable.vb_rank_02, R.drawable.vb_rank_03,
        R.drawable.vb_rank_04, R.drawable.vb_rank_05, R.drawable.vb_rank_06, R.drawable.vb_rank_07,
        R.drawable.vb_rank_08, R.drawable.vb_rank_09, R.drawable.vb_rank_10, R.drawable.vb_rank_11,
        R.drawable.vb_rank_12, R.drawable.vb_rank_13, R.drawable.vb_rank_14, R.drawable.vb_rank_15,
    )

    fun emblem(index: Int): Int = emblems[index.coerceIn(0, emblems.lastIndex)]
}

/**
 * Default balance table. Mirrors `analysis/rank-system/PLAN.md`:
 * - level cost grows linearly (`60 + 3 * (level - 1)`), cap 100;
 * - rank is a pure function of level, at the shipped VB Lab's "certain level" milestones;
 * - ~19.4k TXP reaches level 96 (rank 15) which is roughly 8 weeks of normal use.
 */
data class RankConfig(
    val levelCap: Int = 100,
    val levelStepBase: Int = 60,
    val levelStepGrowth: Int = 3,
    val rankLevelThresholds: List<Int> = listOf(
        1, 4, 8, 12, 17, 22, 28, 34, 41, 48, 56, 64, 72, 80, 88, 96,
    ),
    val rewards: List<RankReward> = defaultRewards(),
    val dailyCaps: Map<TxpSource, Int> = TxpSource.entries.associateWith { it.dailyCap },
) {
    init {
        require(levelCap > 0)
        require(levelStepBase > 0)
        require(levelStepGrowth >= 0)
        require(rankLevelThresholds.size == TamerRanks.COUNT)
        require(rankLevelThresholds.first() <= 1)
    }

    fun levelCost(level: Int): Int =
        (levelStepBase + levelStepGrowth * (level - 1).coerceAtLeast(0)).coerceAtLeast(1)

    fun xpForLevel(level: Int): Long {
        val target = level.coerceIn(1, levelCap)
        var total = 0L
        for (current in 1 until target) total += levelCost(current)
        return total
    }

    fun rankForLevel(level: Int): Int {
        var rank = 0
        for (index in rankLevelThresholds.indices) {
            if (level >= rankLevelThresholds[index]) rank = index
        }
        return rank.coerceIn(0, TamerRanks.COUNT - 1)
    }

    fun nextRankLevel(rank: Int): Int? =
        rankLevelThresholds.getOrNull(rank + 1)

    fun levelsForRank(rank: Int): IntRange? {
        val start = rankLevelThresholds.getOrNull(rank) ?: return null
        val end = rankLevelThresholds.getOrNull(rank + 1)?.minus(1) ?: levelCap
        return start..end
    }

    companion object {
        private fun defaultRewards(): List<RankReward> = buildList {
            add(RankReward(1, 0, RewardKind.CURRENCY, amount = 100, labelKey = "rank_reward_currency_small"))
            add(RankReward(2, 0, RewardKind.CURRENCY, amount = 150, labelKey = "rank_reward_currency_small"))
            add(RankReward(3, 0, RewardKind.TITLE, labelKey = "rank_reward_title"))
            add(RankReward(4, 0, RewardKind.CURRENCY, amount = 200, labelKey = "rank_reward_currency_small"))
            add(RankReward(5, 0, RewardKind.COSMETIC, labelKey = "rank_reward_emblem"))
            add(RankReward(6, 0, RewardKind.CURRENCY, amount = 250, labelKey = "rank_reward_currency_medium"))
            add(RankReward(7, 0, RewardKind.TITLE, labelKey = "rank_reward_title"))
            add(RankReward(8, 0, RewardKind.CURRENCY, amount = 300, labelKey = "rank_reward_currency_medium"))
            add(RankReward(9, 0, RewardKind.COSMETIC, labelKey = "rank_reward_emblem"))
            add(RankReward(10, 0, RewardKind.CURRENCY, amount = 350, labelKey = "rank_reward_currency_medium"))
            add(RankReward(11, 0, RewardKind.TITLE, labelKey = "rank_reward_title"))
            add(RankReward(12, 0, RewardKind.CURRENCY, amount = 400, labelKey = "rank_reward_currency_large"))
            add(RankReward(13, 0, RewardKind.COSMETIC, labelKey = "rank_reward_emblem"))
            add(RankReward(14, 0, RewardKind.CURRENCY, amount = 450, labelKey = "rank_reward_currency_large"))
            add(RankReward(15, 0, RewardKind.TITLE, labelKey = "rank_reward_title_rare"))
        }
    }
}

/** Pure progression rules shared by the repository, the UI and the tests. */
object RankCalculator {
    fun levelForTotal(totalTxp: Long, config: RankConfig): Int {
        var level = 1
        var spent = 0L
        while (level < config.levelCap) {
            val cost = config.levelCost(level)
            if (totalTxp < spent + cost) break
            spent += cost
            level++
        }
        return level
    }

    fun rankForTotal(totalTxp: Long, config: RankConfig): Int =
        config.rankForLevel(levelForTotal(totalTxp, config))

    /** Progress within the current level, in 0..1. */
    fun levelProgress(totalTxp: Long, config: RankConfig): Float {
        val level = levelForTotal(totalTxp, config)
        if (level >= config.levelCap) return 1f
        val start = config.xpForLevel(level)
        val cost = config.levelCost(level).toLong()
        return ((totalTxp - start).toFloat() / cost).coerceIn(0f, 1f)
    }

    fun levelRemaining(totalTxp: Long, config: RankConfig): Long {
        val level = levelForTotal(totalTxp, config)
        if (level >= config.levelCap) return 0
        return (config.xpForLevel(level + 1) - totalTxp).coerceAtLeast(0)
    }

    /** Progress towards the next rank milestone, in 0..1 (1 when at the last rank). */
    fun rankProgress(totalTxp: Long, config: RankConfig): Float {
        val level = levelForTotal(totalTxp, config)
        val rank = config.rankForLevel(level)
        val next = config.nextRankLevel(rank) ?: return 1f
        val start = config.xpForLevel(config.rankLevelThresholds[rank])
        val end = config.xpForLevel(next)
        if (end <= start) return 1f
        return ((totalTxp - start).toFloat() / (end - start).toFloat()).coerceIn(0f, 1f)
    }

    fun clampAward(source: TxpSource, requested: Int, config: RankConfig): Int =
        requested.coerceIn(0, config.dailyCaps[source] ?: source.dailyCap)
}

data class TamerProfileSnapshot(
    val totalTxp: Long,
    val level: Int,
    val rank: Int,
    val lastLevelUpAt: Long?,
    val lastRankUpAt: Long?,
) {
    val emblemRes: Int get() = TamerRanks.emblem(rank)
}

data class RankRewardState(
    val rank: Int,
    val slot: Int,
    val kind: RewardKind,
    val amount: Int,
    val labelKey: String,
    val claimedAt: Long?,
)

data class RankActivityEntry(
    val id: Long,
    val occurredAt: Long,
    val source: TxpSource,
    val delta: Int,
    val summary: String,
)

/** A single point grant. [idempotencyKey] makes retries safe (NFC retry, recomposition, etc.). */
data class TxpAward(
    val source: TxpSource,
    val idempotencyKey: String,
    val amount: Int = source.defaultAmount,
    val digimonRef: String? = null,
    val origin: String? = null,
    val metadata: String? = null,
)

data class RankNotification(
    val oldLevel: Int,
    val newLevel: Int,
    val oldRank: Int,
    val newRank: Int,
    val totalTxp: Long,
) {
    val levelUp: Boolean get() = newLevel > oldLevel
    val rankUp: Boolean get() = newRank > oldRank
}

interface RankEventSink {
    fun submit(event: TxpAward)
}
