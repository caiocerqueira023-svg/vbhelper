package com.github.nacabaro.vbhelper.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.nacabaro.vbhelper.domain.card.Card
import com.github.nacabaro.vbhelper.domain.card.OfficialStatus
import com.github.nacabaro.vbhelper.dtos.CardDtos
import kotlinx.coroutines.flow.Flow

@Dao
interface CardDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNewCard(card: Card): Long

    @Query("SELECT * FROM Card")
    fun getAllCards(): List<Card>

    @Query("SELECT * FROM Card ORDER BY name COLLATE NOCASE")
    fun observeCardsForWorldSpawns(): Flow<List<Card>>

    @Query("SELECT * FROM Card WHERE cardId = :id")
    fun getCardByCardId(id: Int): List<Card>

    @Query("SELECT * FROM Card WHERE id = :id")
    fun getCardById(id: Long): Card?

    @Query("SELECT * FROM Card WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    fun getCardByName(name: String): Card?

    @Query(
        """
        SELECT ca.* 
        FROM Card ca
        JOIN CardCharacter ch ON ca.id = ch.cardId
        JOIN UserCharacter uc ON ch.id = uc.charId
        WHERE uc.id = :id
    """
    )
    fun getCardByCharacterId(id: Long): Flow<Card>

    @Query(
        """
        SELECT ca.*
        FROM Card ca
        JOIN CardCharacter ch ON ca.id = ch.cardId
        JOIN UserCharacter uc ON ch.id = uc.charId
        WHERE uc.id = :id
        LIMIT 1
    """
    )
    fun getCardByCharacterIdSync(id: Long): Card?

    @Query(
        """
        SELECT ca.*
        FROM Card ca
        JOIN CardCharacter ch ON ca.id = ch.cardId
        WHERE ch.id = :cardCharacterId
        LIMIT 1
        """
    )
    suspend fun getCardByCardCharacterId(cardCharacterId: Long): Card?

    @Query("UPDATE Card SET name = :newName WHERE id = :id")
    suspend fun renameCard(id: Int, newName: String)

    @Query("UPDATE Card SET officialStatus = :status WHERE id = :id")
    suspend fun updateOfficialStatus(id: Long, status: OfficialStatus)

    @Query("UPDATE Card SET worldSpawnsEnabled = :enabled WHERE id = :id")
    suspend fun setWorldSpawnsEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM Card WHERE id = :id")
    suspend fun deleteCard(id: Long)

    @Query("""
        SELECT 
            c.logo as cardIcon,
            c.logoWidth as cardIconWidth,
            c.logoHeight as cardIconHeight
        FROM Card c
        JOIN CardCharacter cc ON cc.cardId = c.id
        WHERE cc.id = :charaId
    """)
    fun getCardIconByCharaId(charaId: Long): Flow<CardDtos.CardIcon>
}
