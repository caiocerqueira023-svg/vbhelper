package com.github.nacabaro.vbhelper.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.github.nacabaro.vbhelper.domain.chat.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Insert
    suspend fun insertMessage(message: ChatMessageEntity): Long

    @Query("SELECT * FROM ChatMessageEntity WHERE characterId = :characterId ORDER BY id ASC")
    fun getMessages(characterId: Long): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM ChatMessageEntity WHERE characterId = :characterId ORDER BY id ASC")
    suspend fun getMessagesSync(characterId: Long): List<ChatMessageEntity>

    @Query("DELETE FROM ChatMessageEntity WHERE characterId = :characterId")
    suspend fun clearHistory(characterId: Long)
}