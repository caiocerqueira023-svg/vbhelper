package com.tamerslab.digiline.freechat.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert

/**
 * Free Talk DAO (schema 57, contract §3).
 *
 * Free Talk state is separate from `DigilineDao` so authored card cadence/determinism is untouched.
 * Writes are [Upsert]/[Insert] only; the repository wraps a turn's writes in one Room transaction
 * (contract §3: persist the decision before the scene is displayed).
 */
@Dao
interface FreeChatDao {

    // ----------------------------------------------------------------- FreeChatProfile

    @Query("SELECT * FROM FreeChatProfile WHERE individualId = :individualId LIMIT 1")
    suspend fun profile(individualId: String): FreeChatProfileEntity?

    @Upsert
    suspend fun upsertProfile(profile: FreeChatProfileEntity)

    // -------------------------------------------------------------------- FreeChatTurn

    @Insert
    suspend fun insertTurn(turn: FreeChatTurnEntity): Long

    @Query(
        "SELECT * FROM FreeChatTurn WHERE individualId = :individualId " +
            "ORDER BY id DESC LIMIT :limit"
    )
    suspend fun recentTurns(individualId: String, limit: Int = 20): List<FreeChatTurnEntity>

    @Query("DELETE FROM FreeChatTurn WHERE individualId = :individualId")
    suspend fun clearTurns(individualId: String)

    // -------------------------------------------------------------------------- clear

    @Query("DELETE FROM FreeChatProfile WHERE individualId = :individualId")
    suspend fun clearProfile(individualId: String)

    /**
     * Cross-table cleanup used by "clear conversation": only this feature's transcript rows are
     * deleted (`freechat_user`/`freechat_assistant`); legacy/LLM and DigiLine rows are preserved.
     */
    @Query(
        "DELETE FROM ChatMessageEntity WHERE individualId = :individualId " +
            "AND kind LIKE 'freechat_%'"
    )
    suspend fun clearFreeChatMessages(individualId: String)
}
