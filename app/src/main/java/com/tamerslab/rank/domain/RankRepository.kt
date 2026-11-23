package com.tamerslab.rank.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Local, offline Tamer rank store. No network, no server, no per-Digimon requirements: any activity
 * from any supported Digimon origin can submit TXP and the profile is shared.
 */
interface RankRepository {
    val profile: Flow<TamerProfileSnapshot>
    val rewards: Flow<List<RankRewardState>>
    val activity: Flow<List<RankActivityEntry>>
    val notifications: SharedFlow<RankNotification>

    /** TXP already credited today, per source. Used by the "how to earn" panel. */
    fun dailyCaps(day: String): Flow<Map<TxpSource, Int>>

    suspend fun award(awards: List<TxpAward>)
    suspend fun claim(rank: Int, slot: Int)
    suspend fun claimAll()
}
