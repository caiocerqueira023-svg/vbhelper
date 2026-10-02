package com.github.nacabaro.vbhelper.daos

import androidx.room.*
import com.github.nacabaro.vbhelper.world.ecosystem.*

@Dao
interface WorldChatMemoryDao {
    @Upsert suspend fun saveContext(context: WorldBattleContext)
    @Query("SELECT * FROM WorldBattleContext WHERE interactionId=:id") suspend fun getContext(id: String): WorldBattleContext?
    @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun insertMemory(memory: WorldBattleMemory): Long
    @Query("SELECT * FROM WorldBattleMemory WHERE individualId=:id ORDER BY createdAt DESC,interactionId DESC LIMIT 6")
    suspend fun getMemories(id: String): List<WorldBattleMemory>
    @Query("SELECT * FROM WorldBattleMemory WHERE individualId=:id AND needsReaction=1 AND reactionMessageId IS NULL ORDER BY createdAt,interactionId")
    suspend fun getPendingReactions(id: String): List<WorldBattleMemory>
    @Query("SELECT * FROM WorldBattleMemory WHERE interactionId=:event AND individualId=:individual")
    suspend fun getMemory(event: String, individual: String): WorldBattleMemory?
    @Query("UPDATE WorldBattleMemory SET reactionMessageId=:message WHERE interactionId=:event AND individualId=:individual AND reactionMessageId IS NULL")
    suspend fun finishReaction(event: String, individual: String, message: Long): Int
    @Query("SELECT * FROM WorldPrivateChatLink WHERE sourceMessageId=:id") suspend fun getLink(id: String): WorldPrivateChatLink?
    @Insert suspend fun insertLink(link: WorldPrivateChatLink)
}
