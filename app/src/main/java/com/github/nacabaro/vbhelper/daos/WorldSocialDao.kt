package com.github.nacabaro.vbhelper.daos

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.github.nacabaro.vbhelper.world.ecosystem.IndividualSocialState
import com.github.nacabaro.vbhelper.world.ecosystem.WorldSocialMemory

@Dao
interface WorldSocialDao {
    @Query("SELECT * FROM IndividualSocialState WHERE individualId = :id")
    suspend fun getState(id: String): IndividualSocialState?
    @Upsert suspend fun saveState(state: IndividualSocialState)
    @Upsert suspend fun saveMemory(memory: WorldSocialMemory)
    @Query("SELECT * FROM WorldSocialMemory WHERE observerId = :id ORDER BY createdAt DESC, eventId DESC LIMIT :limit")
    suspend fun recent(id: String, limit: Int = 24): List<WorldSocialMemory>
    @Query("SELECT * FROM WorldSocialMemory WHERE observerId = :id AND partnerId = :partner ORDER BY createdAt DESC, eventId DESC LIMIT :limit")
    suspend fun withPartner(id: String, partner: String, limit: Int = 5): List<WorldSocialMemory>
    @Query("SELECT COUNT(*) FROM WorldSocialMemory WHERE observerId = :id AND partnerId = :partner")
    suspend fun familiarity(id: String, partner: String): Int
    @Query("DELETE FROM WorldSocialMemory WHERE observerId = :id AND eventId NOT IN (SELECT eventId FROM WorldSocialMemory WHERE observerId = :id ORDER BY createdAt DESC,eventId DESC LIMIT 40)")
    suspend fun prune(id: String)
}
