package com.tamerslab.rank.data

import androidx.room.withTransaction
import com.tamerslab.data.database.AppDatabase
import com.tamerslab.items.data.CurrencyRepository
import com.tamerslab.rank.domain.RankActivityEntry
import com.tamerslab.rank.domain.RankCalculator
import com.tamerslab.rank.domain.RankConfig
import com.tamerslab.rank.domain.RankEventSink
import com.tamerslab.rank.domain.RankNotification
import com.tamerslab.rank.domain.RankRepository
import com.tamerslab.rank.domain.RankRewardState
import com.tamerslab.rank.domain.RewardKind
import com.tamerslab.rank.domain.TamerProfileSnapshot
import com.tamerslab.rank.domain.TxpAward
import com.tamerslab.rank.domain.TxpSource
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Offline Tamer Rank storage. TXP events are idempotent, capped per source/day and applied
 * transactionally; level and rank are always derived from the accumulated total so a rebuild or a
 * clock change cannot drift them.
 */
class RankRepositoryImpl(
    private val db: AppDatabase,
    private val config: RankConfig = RankConfig(),
    private val currencyRepository: CurrencyRepository? = null,
    private val clock: () -> Long = System::currentTimeMillis,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : RankRepository, RankEventSink {

    private val dao get() = db.rankDao()
    private val _notifications = MutableSharedFlow<RankNotification>(extraBufferCapacity = 8)

    override val notifications: SharedFlow<RankNotification> = _notifications.asSharedFlow()

    override val profile: Flow<TamerProfileSnapshot> = dao.observeProfile().map { entity ->
        val now = clock()
        val base = entity ?: TamerProfileEntity(createdAt = now, updatedAt = now)
        TamerProfileSnapshot(
            totalTxp = base.totalTxp,
            level = RankCalculator.levelForTotal(base.totalTxp, config)
                .coerceAtMost(config.levelCap),
            rank = RankCalculator.rankForTotal(base.totalTxp, config),
            lastLevelUpAt = base.lastLevelUpAt,
            lastRankUpAt = base.lastRankUpAt,
        )
    }

    override val rewards: Flow<List<RankRewardState>> = dao.observeRewards().map { rows ->
        rows.map { row ->
            RankRewardState(
                rank = row.rank,
                slot = row.slot,
                kind = runCatching { RewardKind.valueOf(row.kind) }.getOrDefault(RewardKind.COSMETIC),
                amount = row.amount,
                labelKey = row.labelKey,
                claimedAt = row.claimedAt,
            )
        }
    }

    override fun dailyCaps(day: String): Flow<Map<TxpSource, Int>> =
        dao.observeDailyCaps(day).map { rows ->
            rows.mapNotNull { row ->
                runCatching { TxpSource.valueOf(row.source) }.getOrNull()?.let { it to row.total }
            }.toMap()
        }

    override val activity: Flow<List<RankActivityEntry>> = dao.observeActivity().map { rows ->
        rows.map { row ->
            RankActivityEntry(
                id = row.id,
                occurredAt = row.occurredAt,
                source = runCatching { TxpSource.valueOf(row.source) }.getOrDefault(TxpSource.DAILY),
                delta = row.delta,
                summary = row.summary,
            )
        }
    }

    override fun submit(event: TxpAward) {
        scope.launch { award(listOf(event)) }
    }

    override suspend fun award(awards: List<TxpAward>) = withContext(Dispatchers.IO) {
        awards.forEach { awardOne(it) }
    }

    private suspend fun awardOne(award: TxpAward) {
        var notification: RankNotification? = null
        db.withTransaction {
            val now = clock()
            val day = LocalDate.now().toString()
            val eventId = dao.insertEvent(
                TamerTxpEventEntity(
                    source = award.source.name,
                    amount = 0,
                    idempotencyKey = award.idempotencyKey,
                    digimonRef = award.digimonRef,
                    origin = award.origin,
                    metadata = award.metadata,
                    occurredAt = now,
                ),
            )
            if (eventId == -1L) return@withTransaction

            val cap = dao.dailyCap(day, award.source.name)?.total ?: 0
            val allowance = ((config.dailyCaps[award.source] ?: award.source.dailyCap) - cap)
                .coerceAtLeast(0)
            val granted = award.amount.coerceIn(0, allowance)
            if (granted > 0) {
                dao.saveDailyCap(TamerDailyCapEntity(day = day, source = award.source.name, total = cap + granted))
                val previous = dao.profile()
                    ?: TamerProfileEntity(createdAt = now, updatedAt = now)
                val total = previous.totalTxp + granted
                val level = RankCalculator.levelForTotal(total, config).coerceAtMost(config.levelCap)
                val rank = RankCalculator.rankForTotal(total, config)
                val updated = previous.copy(
                    totalTxp = total,
                    level = level,
                    rank = rank,
                    lastLevelUpAt = if (level > previous.level) now else previous.lastLevelUpAt,
                    lastRankUpAt = if (rank > previous.rank) now else previous.lastRankUpAt,
                    updatedAt = now,
                )
                dao.saveProfile(updated)
                if (rank > previous.rank) {
                    val newRewards = config.rewards.filter { it.rank in (previous.rank + 1)..rank }
                        .map { reward ->
                            TamerRankRewardEntity(
                                rank = reward.rank,
                                slot = reward.slot,
                                kind = reward.kind.name,
                                amount = reward.amount,
                                labelKey = reward.labelKey,
                            )
                        }
                    if (newRewards.isNotEmpty()) dao.insertRewards(newRewards)
                }
                dao.insertActivity(
                    TamerActivityEntity(
                        occurredAt = now,
                        source = award.source.name,
                        delta = granted,
                        summary = award.digimonRef ?: award.origin ?: "",
                    ),
                )
                if (level > previous.level || rank > previous.rank) {
                    notification = RankNotification(
                        oldLevel = previous.level,
                        newLevel = level,
                        oldRank = previous.rank,
                        newRank = rank,
                        totalTxp = total,
                    )
                }
                dao.updateEventAmount(eventId, granted)
            }
        }
        notification?.let { _notifications.tryEmit(it) }
    }

    override suspend fun claim(rank: Int, slot: Int) = withContext(Dispatchers.IO) {
        val reward = dao.reward(rank, slot) ?: return@withContext
        if (reward.claimedAt != null) return@withContext
        grant(reward)
        dao.claimReward(rank, slot, clock())
    }

    override suspend fun claimAll() = withContext(Dispatchers.IO) {
        val pending = dao.unclaimedRewards()
        pending.forEach { grant(it) }
        if (pending.isNotEmpty()) dao.claimAllRewards(clock())
    }

    private suspend fun grant(reward: TamerRankRewardEntity) {
        when (runCatching { RewardKind.valueOf(reward.kind) }.getOrDefault(RewardKind.COSMETIC)) {
            RewardKind.CURRENCY -> if (reward.amount > 0) currencyRepository?.credit(reward.amount)
            RewardKind.ITEM -> {
                val itemId = reward.labelKey.takeIf { it.startsWith("item:") }?.removePrefix("item:")?.toLongOrNull()
                if (itemId != null && reward.amount > 0) {
                    runCatching { db.itemDao().grantQuestItem(itemId, reward.amount) }
                }
            }
            RewardKind.COSMETIC, RewardKind.TITLE, RewardKind.UNLOCK -> Unit
        }
    }
}
