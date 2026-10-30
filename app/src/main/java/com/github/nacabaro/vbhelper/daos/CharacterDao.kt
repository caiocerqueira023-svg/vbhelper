package com.github.nacabaro.vbhelper.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.github.nacabaro.vbhelper.domain.card.CardCharacter
import com.github.nacabaro.vbhelper.domain.characters.Sprite
import com.github.nacabaro.vbhelper.dtos.CharacterDtos
import com.github.nacabaro.vbhelper.dtos.DebugSpawnCharacter
import kotlinx.coroutines.flow.Flow

@Dao
interface CharacterDao {
    @Query("DELETE FROM PossibleTransformations WHERE charaId IN (SELECT id FROM CardCharacter WHERE cardId = :cardId)")
    suspend fun deleteTransformationsForCard(cardId: Long)

    @Query("SELECT * FROM CardCharacter WHERE cardId = :cardId")
    suspend fun getCharactersForCard(cardId: Long): List<CardCharacter>

    @Query("SELECT * FROM PossibleTransformations WHERE charaId = :charaId AND toCharaId IS NOT NULL")
    suspend fun getTransformationsFrom(charaId: Long): List<com.github.nacabaro.vbhelper.domain.card.PossibleTransformations>

    @Query("SELECT * FROM CardCharacter")
    suspend fun getAllCharacters(): List<CardCharacter>

    @Query("""
        SELECT character.id, character.charaIndex, card.name AS cardName,
            COALESCE(NULLIF(profile.speciesName, ''), NULLIF(profile.matchedName, '')) AS speciesName,
            sprite.spriteIdle1 AS spriteIdle, sprite.width AS spriteWidth, sprite.height AS spriteHeight
        FROM CardCharacter character
        JOIN Card card ON card.id = character.cardId
        JOIN DisplaySprite sprite ON sprite.cardCharacterId = character.id
        LEFT JOIN SpeciesProfile profile ON profile.cardCharacterId = character.id
        ORDER BY card.name COLLATE NOCASE, character.charaIndex, character.id
    """)
    suspend fun getDebugSpawnCharacters(): List<DebugSpawnCharacter>

    /** Catalog Dot definitions for Expanded-mode debug add-to-storage. */
    @Query("""
        SELECT character.id, 0 AS charaIndex, 'Catalog' AS cardName,
            COALESCE(NULLIF(profile.speciesName, ''), NULLIF(profile.matchedName, ''), character.speciesId) AS speciesName,
            sprite.spriteIdle1 AS spriteIdle, sprite.width AS spriteWidth, sprite.height AS spriteHeight
        FROM CardCharacter character
        JOIN DisplaySprite sprite ON sprite.cardCharacterId = character.id
        LEFT JOIN SpeciesProfile profile ON profile.cardCharacterId = character.id
        WHERE character.catalogSpeciesId IS NOT NULL
        ORDER BY speciesName COLLATE NOCASE, character.id
    """)
    suspend fun getDebugCatalogCharacters(): List<DebugSpawnCharacter>

    @Query(
        """
        SELECT cc.*
        FROM CardCharacter cc
        JOIN Card c ON c.id = cc.cardId
        WHERE c.worldSpawnsEnabled = 1
        """
    )
    suspend fun getCharactersForWorldSpawns(): List<CardCharacter>

    @Query("SELECT * FROM CardCharacter WHERE id = :id")
    suspend fun getById(id: Long): CardCharacter?

    /** App-managed catalog definitions; never imported DIM/BEM rows. */
    @Query("SELECT * FROM CardCharacter WHERE catalogSpeciesId IS NOT NULL ORDER BY id")
    suspend fun getCatalogDefinitions(): List<CardCharacter>

    @Query(
        """
        SELECT
            cc.stage as stage,
            cc.attribute as attribute,
            s.spriteIdle1 as spriteIdle,
            s.spriteIdle2 as spriteIdle2,
            s.width as spriteWidth,
            s.height as spriteHeight,
            COALESCE(c.name, '') as cardName
        FROM CardCharacter cc
        JOIN DisplaySprite s ON s.cardCharacterId = cc.id
        LEFT JOIN Card c ON c.id = cc.cardId
        WHERE cc.id = :cardCharacterId
        LIMIT 1
        """
    )
    suspend fun getWildCharacterInfo(cardCharacterId: Long): CharacterDtos.WildCharacterInfo?

    @Query("""
        SELECT
            s.spriteIdle1 as cardIcon,
            s.width as cardIconWidth,
            s.height as cardIconHeight
        FROM DisplaySprite s
        WHERE s.cardCharacterId = :charaId
        LIMIT 1
    """)
    fun getDefinitionIconByCharaId(charaId: Long): Flow<com.github.nacabaro.vbhelper.dtos.CardDtos.CardIcon?>

    @Insert
    suspend fun insertCharacter(vararg characterData: CardCharacter)

    @Insert
    suspend fun insertCharacterReturning(characterData: CardCharacter): Long

    @Query("SELECT * FROM CardCharacter WHERE speciesId = :speciesId LIMIT 1")
    suspend fun getBySpeciesId(speciesId: String): CardCharacter?

    @Query("SELECT * FROM CardCharacter WHERE charaIndex = :monIndex AND cardId = :dimId LIMIT 1")
    fun getCharacterByMonIndex(monIndex: Int, dimId: Long): CardCharacter

    @Insert
    suspend fun insertSprite(vararg sprite: Sprite)

    @Query(
        """
        SELECT
            d.cardId as cardId,
            c.charaIndex as charId,
            c.stage as stage,
            c.attribute as attribute,
            COALESCE(cp.currentStage, 0) as currentStage
        FROM CardCharacter c
        JOIN UserCharacter uc ON c.id = uc.charId
        LEFT JOIN Card d ON d.id = c.cardId
        LEFT JOIN CardProgress cp ON d.id = cp.cardId
        WHERE c.id = :charId
    """
    )
    suspend fun getCharacterInfo(charId: Long): CharacterDtos.CardCharacterInfo

    @Query(
        """
        INSERT INTO PossibleTransformations (charaId, requiredVitals, requiredTrophies, requiredBattles, requiredWinRate, changeTimerHours, requiredAdventureLevelCompleted, toCharaId)
        SELECT
            (SELECT id FROM CardCharacter WHERE charaIndex = :fromChraraIndex AND cardId = :cardId),
            :requiredVitals,
            :requiredTrophies,
            :requiredBattles,
            :requiredWinRate,
            :changeTimerHours,
            :requiredAdventureLevelCompleted,
            (SELECT id FROM CardCharacter WHERE charaIndex = :toChraraIndex AND cardId = :cardId)
        WHERE EXISTS (
            SELECT 1 FROM CardCharacter
            WHERE charaIndex = :fromChraraIndex AND cardId = :cardId
        )
        AND EXISTS (
            SELECT 1 FROM CardCharacter
            WHERE charaIndex = :toChraraIndex AND cardId = :cardId
        )
    """
    )
    suspend fun insertPossibleTransformation(
        fromChraraIndex: Int,
        toChraraIndex: Int,
        cardId: Long,
        requiredVitals: Int,
        requiredTrophies: Int,
        requiredBattles: Int,
        changeTimerHours: Int,
        requiredWinRate: Int,
        requiredAdventureLevelCompleted: Int
    )

    @Query(
        """
        SELECT 
            pt.charaId as fromCharaId,
            pt.toCharaId as charaId,
            s.spriteIdle1 as spriteIdle,
            s.width as spriteWidth,
            s.height as spriteHeight,
            d.discoveredOn as discoveredOn,
            pt.requiredTrophies as requiredTrophies,
            pt.requiredVitals as requiredVitals,
            pt.requiredBattles as requiredBattles,
            pt.requiredWinRate as requiredWinRate,
            pt.changeTimerHours as changeTimerHours,
            pt.requiredAdventureLevelCompleted as requiredAdventureLevelCompleted
        FROM
            PossibleTransformations pt
        JOIN CardCharacter c on pt.toCharaId = c.id
        JOIN DisplaySprite s ON s.cardCharacterId = c.id
        LEFT JOIN Dex d ON d.id = pt.toCharaId
        WHERE
            pt.charaId = :characterId
    """
    )
    fun getEvolutionRequirementsForCard(characterId: Long): Flow<List<CharacterDtos.EvolutionRequirementsWithSpritesAndObtained>>
}
