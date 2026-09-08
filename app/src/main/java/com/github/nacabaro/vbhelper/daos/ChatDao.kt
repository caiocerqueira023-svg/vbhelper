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

    @Query("SELECT * FROM ChatMessageEntity WHERE individualId = :individualId AND role = 'assistant' ORDER BY id DESC LIMIT 1")
    fun getLatestAssistantMessage(individualId: String): Flow<ChatMessageEntity?>

    @Query("DELETE FROM ChatMessageEntity WHERE individualId = :individualId")
    suspend fun clearHistory(individualId: String)

    @Query("DELETE FROM ChatMessageEntity WHERE individualId = :individualId AND id >= :messageId")
    suspend fun deleteFromMessage(individualId: String, messageId: Long)
}
