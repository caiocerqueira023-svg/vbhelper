package com.tamerslab.adventure.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import com.tamerslab.dtos.CharacterDtos
import kotlinx.coroutines.flow.Flow


@Dao
@RewriteQueriesToDropUnusedColumns
interface AdventureDao {
    @Query("""
        INSERT INTO Adventure (characterId, originalDuration, finishesAdventure)
        VALUES (:characterId, :originalDuration, strftime('%s', 'now') + :timeInSeconds)
    """)
    fun insertNewAdventure(characterId: Long, originalDuration: Long, timeInSeconds: Long)

    @Query("""
        SELECT COUNT(*) FROM Adventure
    """)
    fun getAdventureCount(): Int

    @Query(
        """
        SELECT
            uc.*,
            c.stage,
            c.attribute,
            s.spriteIdle1 AS spriteIdle,
            s.width AS spriteWidth,
            s.height AS spriteHeight,
            COALESCE(d.isBEm, 0) as isBemCard,
            a.finishesAdventure AS finishesAdventure,
            a.originalDuration AS originalTimeInMinutes
        FROM UserCharacter uc
        JOIN CardCharacter c ON uc.charId = c.id
        JOIN DisplaySprite s ON s.cardCharacterId = c.id
        LEFT JOIN Card d ON c.cardId = d.id
        JOIN Adventure a ON uc.id = a.characterId
    """
    )
    fun getAdventureCharacters(): Flow<List<CharacterDtos.AdventureCharacterWithSprites>>

    @Query("""
        DELETE FROM Adventure
        WHERE characterId = :characterId
    """)
    suspend fun deleteAdventure(characterId: Long)
}
