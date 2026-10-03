package com.github.nacabaro.vbhelper.daos

import androidx.room.Dao
import androidx.room.Query
import com.github.nacabaro.vbhelper.dtos.CardDtos
import com.github.nacabaro.vbhelper.dtos.CardEvolutionLink
import com.github.nacabaro.vbhelper.dtos.CharacterDtos
import kotlinx.coroutines.flow.Flow

@Dao
interface DexDao {
    @Query(
        """
        INSERT OR IGNORE INTO Dex(id, discoveredOn)
        VALUES (
            (SELECT id FROM CardCharacter WHERE charaIndex = :charIndex AND cardId = :cardId), 
            :discoveredOn
        )
    """
    )
    fun insertCharacter(charIndex: Int, cardId: Long, discoveredOn: Long)

    @Query(
        """
        SELECT 
            c.id AS id,
            c.charaIndex AS charaIndex,
            s.spriteIdle1 AS spriteIdle,
            s.spriteIdle2 AS spriteIdle2,
            s.width AS spriteWidth,
            s.height AS spriteHeight,
            c.nameSprite AS nameSprite,
            c.nameWidth AS nameSpriteWidth,
            c.nameHeight AS nameSpriteHeight,
            d.discoveredOn AS discoveredOn,
            c.baseHp as baseHp,
            c.baseBp as baseBp,
            c.baseAp as baseAp,
            c.stage as stage,
            c.attribute as attribute,
            EXISTS(
                SELECT 1 FROM UserCharacter uc WHERE uc.charId = c.id
            ) AS isCurrentlyAvailable
        FROM CardCharacter c
        JOIN Sprite s ON c.spriteId = s.id
        LEFT JOIN dex d ON c.id = d.id
        WHERE c.cardId = :cardId
        ORDER BY c.stage, c.charaIndex, c.id
    """
    )
    fun getSingleCardProgress(cardId: Long): Flow<List<CharacterDtos.CardCharaProgress>>

    @Query(
        """
        SELECT
            c.id AS id,
            c.charaIndex AS charaIndex,
            s.spriteIdle1 AS spriteIdle,
            s.spriteIdle2 AS spriteIdle2,
            s.width AS spriteWidth,
            s.height AS spriteHeight,
            c.nameSprite AS nameSprite,
            c.nameWidth AS nameSpriteWidth,
            c.nameHeight AS nameSpriteHeight,
            d.discoveredOn AS discoveredOn,
            c.baseHp AS baseHp,
            c.baseBp AS baseBp,
            c.baseAp AS baseAp,
            c.stage AS stage,
            c.attribute AS attribute,
            EXISTS(SELECT 1 FROM UserCharacter uc WHERE uc.charId = c.id) AS isCurrentlyAvailable
        FROM CardCharacter c
        JOIN Sprite s ON c.spriteId = s.id
        LEFT JOIN Dex d ON c.id = d.id
        WHERE c.id = :characterId
        LIMIT 1
        """
    )
    fun getCharacterProgress(characterId: Long): Flow<CharacterDtos.CardCharaProgress?>

    @Query(
        """
        SELECT DISTINCT pt.charaId AS fromId, pt.toCharaId AS toId, NULL AS fusionAttribute, 0 AS isJogress
        FROM PossibleTransformations pt
        JOIN CardCharacter source ON source.id = pt.charaId
        JOIN CardCharacter destination ON destination.id = pt.toCharaId
        WHERE source.cardId = :cardId AND destination.cardId = :cardId
        UNION
        SELECT DISTINCT cf.fromCharaId AS fromId, cf.toCharaId AS toId, cf.attribute AS fusionAttribute, 1 AS isJogress
        FROM CardFusions cf
        JOIN CardCharacter source ON source.id = cf.fromCharaId
        JOIN CardCharacter destination ON destination.id = cf.toCharaId
        WHERE source.cardId = :cardId AND destination.cardId = :cardId
        UNION
        SELECT j.fromCharaId AS fromId, j.toCharaId AS toId, NULL AS fusionAttribute, 1 AS isJogress
        FROM CardSpecificJogress j
        JOIN CardCharacter source ON source.id = j.fromCharaId
        JOIN CardCharacter destination ON destination.id = j.toCharaId
        WHERE source.cardId = :cardId AND destination.cardId = :cardId
        UNION
        SELECT partner.id AS fromId, j.toCharaId AS toId, NULL AS fusionAttribute, 1 AS isJogress
        FROM CardSpecificJogress j
        JOIN CardCharacter source ON source.id = j.fromCharaId
        JOIN Card sourceCard ON sourceCard.id = source.cardId AND sourceCard.cardId = j.partnerCardNumber
        JOIN CardCharacter partner ON partner.cardId = source.cardId AND partner.charaIndex = j.partnerCharaIndex
        JOIN CardCharacter destination ON destination.id = j.toCharaId
        WHERE source.cardId = :cardId AND destination.cardId = :cardId
        ORDER BY fromId, toId, fusionAttribute
        """
    )
    fun getCardEvolutionLinks(cardId: Long): Flow<List<CardEvolutionLink>>

    @Query(
        """
        SELECT 
            c.id as cardId,
            c.name as cardName,
            c.logo as cardLogo,
            c.logoWidth as logoWidth,
            c.logoHeight as logoHeight, 
            c.officialStatus as officialStatus,
            (SELECT COUNT(*) FROM CardCharacter cc WHERE cc.cardId = c.id) AS totalCharacters,
            (SELECT COUNT(*) FROM Dex d JOIN CardCharacter cc ON d.id = cc.id WHERE cc.cardId = c.id AND d.discoveredOn IS NOT NULL) AS obtainedCharacters
        FROM Card c
        ORDER BY c.name COLLATE NOCASE, c.id
    """
    )
    fun getCardsWithProgress(): Flow<List<CardDtos.CardProgress>>
}
