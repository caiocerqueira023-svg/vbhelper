package com.github.nacabaro.vbhelper.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import androidx.room.RewriteQueriesToDropUnusedColumns
import com.github.nacabaro.vbhelper.domain.card.CardCharacter
import com.github.nacabaro.vbhelper.domain.device_data.UserCharacter
import com.github.nacabaro.vbhelper.domain.device_data.BECharacterData
import com.github.nacabaro.vbhelper.domain.device_data.SpecialMissions
import com.github.nacabaro.vbhelper.domain.device_data.TransformationHistory
import com.github.nacabaro.vbhelper.domain.device_data.VBCharacterData
import com.github.nacabaro.vbhelper.domain.device_data.VitalsHistory
import com.github.nacabaro.vbhelper.dtos.CharacterDtos
import kotlinx.coroutines.flow.Flow

@Dao
@RewriteQueriesToDropUnusedColumns
interface UserCharacterDao {
    @Insert
    fun insertCharacterData(characterData: UserCharacter): Long

    @Insert
    fun insertBECharacterData(characterData: BECharacterData)

    @Insert
    fun insertVBCharacterData(characterData: VBCharacterData)

    @Upsert
    fun updateCharacter(character: UserCharacter)

    @Upsert
    fun updateBECharacterData(characterData: BECharacterData)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertTransformationHistory(vararg transformationHistory: TransformationHistory)

    @Upsert
    fun insertSpecialMissions(vararg specialMissions: SpecialMissions)

    @Query(
        """
        SELECT 
            t.id AS id,
            t.stageId AS stageId,
            s.spriteIdle1 AS spriteIdle,
            s.width AS spriteWidth,
            s.height AS spriteHeight,
            c.charaIndex AS monIndex, 
            c.stage AS stage,
            t.transformationDate AS transformationDate
        FROM TransformationHistory t 
        JOIN CardCharacter c ON c.id = t.stageId
        JOIN Sprite s ON s.id = c.spriteId
        WHERE monId = :monId
        ORDER BY t.transformationDate ASC, t.id ASC
    """
    )
    fun getTransformationHistory(monId: Long): Flow<List<CharacterDtos.TransformationHistory>>

    @Query(
        """
        SELECT
            t.stageId as stageId,
            c.charaIndex AS monIndex,
            ca.name as cardName,
            t.transformationDate AS transformationDate
        FROM TransformationHistory t
        JOIN CardCharacter c ON c.id = t.stageId
        JOIN Card ca ON ca.id = c.cardId
        WHERE t.monId = :monId
        ORDER BY t.transformationDate ASC, t.id ASC
    """
    )
    suspend fun getTransformationHistoryForExport(monId: Long): List<CharacterDtos.TransformationHistoryExport>

    @Query(
        """
        SELECT
            uc.*,
            c.stage,
            c.attribute,
            s.spriteIdle1 AS spriteIdle,
            s.spriteIdle2 AS spriteIdle2,
            s.width AS spriteWidth,
            s.height AS spriteHeight,
            c.nameSprite as nameSprite,
            c.nameWidth as nameSpriteWidth,
            c.nameHeight as nameSpriteHeight,
            d.isBEm as isBemCard,
            di.nickname AS nickname,
            a.characterId = uc.id as isInAdventure,
            uc.isActive as active
        FROM UserCharacter uc
        JOIN CardCharacter c ON uc.charId = c.id
        JOIN Card d ON  d.id = c.cardId
        JOIN Sprite s ON s.id = c.spriteId
        LEFT JOIN Adventure a ON a.characterId = uc.id
        LEFT JOIN DigimonIndividual di ON di.individualId = uc.individualId
        """
    )
    fun getAllCharacters(): Flow<List<CharacterDtos.CharacterWithSprites>>

    @Query(
        """
        SELECT
            uc.*,
            c.stage,
            c.attribute,
            s.spriteIdle1 AS spriteIdle,
            s.spriteIdle2 AS spriteIdle2,
            s.width AS spriteWidth,
            s.height AS spriteHeight,
            c.nameSprite as nameSprite,
            c.nameWidth as nameSpriteWidth,
            c.nameHeight as nameSpriteHeight,
            d.isBEm as isBemCard,
            di.nickname AS nickname,
            a.characterId = uc.id as isInAdventure,
            uc.isActive as active
        FROM UserCharacter uc
        JOIN CardCharacter c ON uc.charId = c.id
        JOIN Card d ON c.cardId = d.id
        JOIN Sprite s ON s.id = c.spriteId
        LEFT JOIN Adventure a ON a.characterId = uc.id
        LEFT JOIN DigimonIndividual di ON di.individualId = uc.individualId
        WHERE uc.id = :id
    """
    )
    suspend fun getCharacterWithSprites(id: Long): CharacterDtos.CharacterWithSprites

    @Query("SELECT mood FROM UserCharacter WHERE id = :id")
    fun observeMood(id: Long): Flow<Int>

    @Query("SELECT * FROM UserCharacter WHERE id = :id")
    suspend fun getCharacter(id: Long): UserCharacter

    @Query("SELECT id FROM UserCharacter")
    suspend fun getAllCharacterIds(): List<Long>

    @Query("SELECT individualId FROM UserCharacter WHERE id = :id")
    fun getIndividualId(id: Long): Flow<String>

    @Query("SELECT * FROM BECharacterData WHERE id = :id")
    fun getBeData(id: Long): Flow<BECharacterData>

    @Query("SELECT * FROM VBCharacterData WHERE id = :id")
    fun getVbData(id: Long): Flow<VBCharacterData>

    @Query("SELECT * FROM VBCharacterData WHERE id = :id")
    suspend fun getVbDataOrNull(id: Long): VBCharacterData?

    @Query("SELECT * FROM SpecialMissions WHERE characterId = :id")
    fun getSpecialMissions(id: Long): Flow<List<SpecialMissions>>

    @Query(
        """
        SELECT
            uc.*,
            c.stage,
            c.attribute,
            s.spriteIdle1 AS spriteIdle,
            s.spriteIdle2 AS spriteIdle2,
            s.width AS spriteWidth,
            s.height AS spriteHeight,
            c.nameSprite as nameSprite,
            c.nameWidth as nameSpriteWidth,
            c.nameHeight as nameSpriteHeight,
            d.isBEm as isBemCard,
            di.nickname AS nickname,
            a.characterId as isInAdventure,
            uc.isActive as active
        FROM UserCharacter uc
        JOIN CardCharacter c ON uc.charId = c.id
        JOIN Card d ON c.cardId = d.id
        JOIN Sprite s ON s.id = c.spriteId
        LEFT JOIN Adventure a ON a.characterId = uc.id
        LEFT JOIN DigimonIndividual di ON di.individualId = uc.individualId
        WHERE uc.isActive = 1
        LIMIT 1
    """
    )
    fun getActiveCharacter(): Flow<CharacterDtos.CharacterWithSprites?>

    @Query("DELETE FROM UserCharacter WHERE id = :id")
    fun deleteCharacterById(id: Long)

    @Query("UPDATE UserCharacter SET isActive = 0 WHERE isActive = 1")
    fun clearActiveCharacter()

    @Query("UPDATE UserCharacter SET isActive = 1 WHERE id = :id")
    fun setActiveCharacter(id: Long)

    @Query("UPDATE UserCharacter SET charId = :stageId, vitalPoints = 0 WHERE id = :characterId")
    fun degenerateCharacter(characterId: Long, stageId: Long)

    @Query(
        """
        UPDATE UserCharacter
        SET mood = MAX(0, MIN(100, mood + :delta))
        WHERE id = :characterId
        """
    )
    suspend fun adjustMood(characterId: Long, delta: Int)

    @Query(
        """
        DELETE FROM TransformationHistory
        WHERE monId = :characterId
          AND (transformationDate > :transformationDate
            OR (transformationDate = :transformationDate AND id > :historyId))
        """
    )
    fun deleteTransformationsAfter(
        characterId: Long,
        transformationDate: Long,
        historyId: Long
    )

    @Query(
        """
        SELECT c.*
        FROM CardCharacter c
        join UserCharacter uc on c.id = uc.charId
        where uc.id = :charId
        LIMIT 1
        """
    )
    suspend fun getCharacterInfo(charId: Long): CardCharacter


    @Query(
        """
        INSERT INTO TransformationHistory(monId, stageId, transformationDate)
        VALUES 
            (:monId, 
            (SELECT id FROM CardCharacter WHERE charaIndex = :stage AND cardId = :dimId),
            :transformationDate)
    """
    )
    fun insertTransformation(monId: Long, stage: Int, dimId: Long, transformationDate: Long)

    @Upsert
    fun insertVitals(vararg vitalsHistory: VitalsHistory)

    @Query("""SELECT * FROM VitalsHistory WHERE charId = :charId ORDER BY id ASC""")
    suspend fun getVitalsHistory(charId: Long): List<VitalsHistory>

    @Query(
        """
        SELECT
            uc.*,
            c.stage,
            c.attribute,
            s.spriteIdle1 AS spriteIdle,
            s.spriteIdle2 AS spriteIdle2,
            s.width AS spriteWidth,
            s.height AS spriteHeight,
            c.nameSprite as nameSprite,
            c.nameWidth as nameSpriteWidth,
            c.nameHeight as nameSpriteHeight,
            d.isBEm as isBemCard,
            di.nickname AS nickname,
            a.characterId = uc.id as isInAdventure,
            uc.isActive as active
        FROM UserCharacter uc
        JOIN CardCharacter c ON uc.charId = c.id
        JOIN Card d ON  d.id = c.cardId
        JOIN Sprite s ON s.id = c.spriteId
        LEFT JOIN Adventure a ON a.characterId = uc.id
        LEFT JOIN DigimonIndividual di ON di.individualId = uc.individualId
        WHERE uc.characterType = "BEDevice"
        """
    )
    suspend fun getBECharacters(): List<CharacterDtos.CharacterWithSprites>

    @Query(
        """
        SELECT
            uc.*,
            c.stage,
            c.attribute,
            s.spriteIdle1 AS spriteIdle,
            s.spriteIdle2 AS spriteIdle2,
            s.width AS spriteWidth,
            s.height AS spriteHeight,
            c.nameSprite as nameSprite,
            c.nameWidth as nameSpriteWidth,
            c.nameHeight as nameSpriteHeight,
            d.isBEm as isBemCard,
            di.nickname AS nickname,
            a.characterId = uc.id as isInAdventure,
            uc.isActive as active
        FROM UserCharacter uc
        JOIN CardCharacter c ON uc.charId = c.id
        JOIN Card d ON  d.id = c.cardId
        JOIN Sprite s ON s.id = c.spriteId
        LEFT JOIN Adventure a ON a.characterId = uc.id
        LEFT JOIN DigimonIndividual di ON di.individualId = uc.individualId
        WHERE uc.characterType = "VBDevice"
        """
    )
    suspend fun getVBDimCharacters(): List<CharacterDtos.CharacterWithSprites>

    /**
     * Sprites needed by the home-screen Digimon widget (idle + walk frames).
     */
    @Query(
        """
        SELECT
            s.spriteIdle1 AS spriteIdle1,
            s.spriteIdle2 AS spriteIdle2,
            s.spriteWalk1 AS spriteWalk1,
            s.spriteWalk2 AS spriteWalk2,
            s.width AS width,
            s.height AS height
        FROM UserCharacter uc
        JOIN CardCharacter c ON uc.charId = c.id
        JOIN Sprite s ON s.id = c.spriteId
        WHERE uc.isActive = 1
        LIMIT 1
        """
    )
    suspend fun getActiveCharacterWidgetSprites(): CharacterDtos.WidgetSprites?
}
