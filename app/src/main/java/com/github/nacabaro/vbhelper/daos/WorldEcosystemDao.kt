package com.github.nacabaro.vbhelper.daos

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import com.github.nacabaro.vbhelper.world.ecosystem.WorldEcosystemSession
import com.github.nacabaro.vbhelper.world.ecosystem.WorldDen
import com.github.nacabaro.vbhelper.world.ecosystem.WildPairBond
import com.github.nacabaro.vbhelper.world.ecosystem.WorldEcosystemInput

@Dao
interface WorldEcosystemDao {
    @Query("SELECT * FROM WorldEcosystemSession WHERE id = :id")
    suspend fun getSession(id: String = WorldEcosystemSession.LOCAL_SESSION_ID): WorldEcosystemSession?

    @Upsert
    suspend fun saveSession(session: WorldEcosystemSession)

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertDen(den: WorldDen)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertBond(bond: WildPairBond)
    @Upsert suspend fun saveBond(bond: WildPairBond)
    @Query("SELECT * FROM WildPairBond ORDER BY individualA, individualB") suspend fun getBonds(): List<WildPairBond>
    @Query("SELECT * FROM WildPairBond WHERE individualA = :a AND individualB = :b") suspend fun getBond(a: String, b: String): WildPairBond?
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun recordInput(input: WorldEcosystemInput)
    @Query("DELETE FROM WorldEcosystemInput WHERE tick < :oldestTick") suspend fun pruneInputs(oldestTick: Long)
    @Query("DELETE FROM WorldDen WHERE id NOT IN (SELECT denId FROM WorldSpawn WHERE denId IS NOT NULL)") suspend fun pruneDens()
    @Query("DELETE FROM WildPairBond WHERE lastInteractionAt < :cutoff AND individualA NOT IN (SELECT individualId FROM WorldSpawn) AND individualB NOT IN (SELECT individualId FROM WorldSpawn)")
    suspend fun pruneBonds(cutoff: Long)
}
