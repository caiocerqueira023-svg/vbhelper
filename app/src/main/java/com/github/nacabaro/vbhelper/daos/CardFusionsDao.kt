package com.github.nacabaro.vbhelper.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import com.github.cfogrady.vbnfc.data.NfcCharacter
import com.github.nacabaro.vbhelper.dtos.CharacterDtos
import com.github.nacabaro.vbhelper.dtos.CardSpecificJogressDetails
import com.github.nacabaro.vbhelper.domain.card.CardSpecificJogress
import kotlinx.coroutines.flow.Flow

@Dao
@RewriteQueriesToDropUnusedColumns
interface CardFusionsDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSpecificJogress(route: CardSpecificJogress)

    @Query("DELETE FROM CardFusions WHERE fromCharaId IN (SELECT id FROM CardCharacter WHERE cardId = :cardId)")
    suspend fun deleteAttributeRoutesForCard(cardId: Long)

    @Query("DELETE FROM CardSpecificJogress WHERE fromCharaId IN (SELECT id FROM CardCharacter WHERE cardId = :cardId)")
    suspend fun deleteSpecificRoutesForCard(cardId: Long)

    @Query("""
        SELECT destination.id AS charaId, s.spriteIdle1 AS spriteIdle, s.width AS spriteWidth,
            s.height AS spriteHeight, d.discoveredOn AS discoveredOn,
            CASE WHEN j.fromCharaId = :characterId THEN j.partnerCardNumber ELSE sourceCard.cardId END AS partnerCardNumber,
            CASE WHEN j.fromCharaId = :characterId THEN j.partnerCharaIndex ELSE source.charaIndex END AS partnerCharaIndex
        FROM CardSpecificJogress j
        JOIN CardCharacter source ON source.id = j.fromCharaId
        JOIN Card sourceCard ON sourceCard.id = source.cardId
        JOIN CardCharacter destination ON destination.id = j.toCharaId AND destination.cardId = source.cardId
        JOIN Sprite s ON s.id = destination.spriteId
        LEFT JOIN Dex d ON d.id = destination.id
        WHERE j.fromCharaId = :characterId OR EXISTS (
            SELECT 1 FROM CardCharacter partner WHERE partner.id = :characterId
            AND partner.cardId = source.cardId AND partner.charaIndex = j.partnerCharaIndex
            AND sourceCard.cardId = j.partnerCardNumber
        )
        ORDER BY destination.charaIndex
    """)
    fun getSpecificJogressForCharacter(characterId: Long): Flow<List<CardSpecificJogressDetails>>

    @Query("""
        INSERT INTO
            CardFusions (
                fromCharaId,
                attribute,
                toCharaId
            )
        SELECT
            (SELECT id FROM CardCharacter WHERE cardId = :cardId AND charaIndex = :fromCharaId),
            :attribute,
            (SELECT id FROM CardCharacter WHERE cardId = :cardId AND charaIndex = :toCharaId)
    """)
    suspend fun insertNewFusion(
        cardId: Long,
        fromCharaId: Int,
        attribute: NfcCharacter.Attribute,
        toCharaId: Int
    )

    @Query("""
        SELECT 
            cf.toCharaId as charaId,
            cf.fromCharaId as fromCharaId,
            s.spriteIdle1 as spriteIdle,
            cc.attribute as attribute,
            s.width as spriteWidth,
            s.height as spriteHeight,
            d.discoveredOn as discoveredOn,
            cf.attribute as fusionAttribute
        FROM CardFusions cf
        JOIN CardCharacter cc ON cc.id = cf.toCharaId
        JOIN Sprite s ON s.id = cc.spriteId
        LEFT JOIN Dex d ON d.id = cc.id
        WHERE cf.fromCharaId = :charaId
        ORDER BY cc.charaIndex
    """)
    fun getFusionsForCharacter(charaId: Long): Flow<List<CharacterDtos.FusionsWithSpritesAndObtained>>
}
