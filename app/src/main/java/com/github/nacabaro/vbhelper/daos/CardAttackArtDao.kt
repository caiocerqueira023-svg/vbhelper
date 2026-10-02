package com.github.nacabaro.vbhelper.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.nacabaro.vbhelper.domain.card.CardAttackArt

@Dao
interface CardAttackArtDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(art: List<CardAttackArt>)

    @Query("SELECT * FROM CardAttackArt WHERE cardCharacterId = :characterId")
    suspend fun getForCharacter(characterId: Long): CardAttackArt?

    @Query("SELECT a.* FROM CardAttackArt a JOIN CardCharacter c ON c.id = a.cardCharacterId WHERE c.cardId = :cardId")
    suspend fun getForCard(cardId: Long): List<CardAttackArt>
}
