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

    @Query("SELECT * FROM ChatMessageEntity WHERE individualId = :individualId ORDER BY id ASC")
    fun getMessages(individualId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM ChatMessageEntity WHERE individualId = :individualId ORDER BY id ASC")
    suspend fun getMessagesSync(individualId: String): List<ChatMessageEntity>

    @Query("DELETE FROM ChatMessageEntity WHERE individualId = :individualId")
    suspend fun clearHistory(individualId: String)
}
