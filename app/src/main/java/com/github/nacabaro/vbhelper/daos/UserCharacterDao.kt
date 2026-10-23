package com.github.nacabaro.vbhelper.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import androidx.room.RewriteQueriesToDropUnusedColumns
import androidx.room.Transaction
import com.github.nacabaro.vbhelper.domain.card.CardCharacter
import com.github.nacabaro.vbhelper.domain.device_data.UserCharacter
import com.github.nacabaro.vbhelper.domain.device_data.BECharacterData
import com.github.nacabaro.vbhelper.domain.device_data.SpecialMissions
import com.github.nacabaro.vbhelper.domain.device_data.TransformationHistory
import com.github.nacabaro.vbhelper.domain.device_data.VBCharacterData
import com.github.nacabaro.vbhelper.domain.device_data.VitalsHistory
import com.github.nacabaro.vbhelper.battle.offline.data.BattleParticipantProfile
import com.github.nacabaro.vbhelper.dtos.CharacterDtos
import kotlinx.coroutines.flow.Flow

@Dao
@RewriteQueriesToDropUnusedColumns
interface UserCharacterDao {
    @Query("SELECT vitalPoints FROM UserCharacter WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveVitalPoints(): Int?

    @Query("SELECT * FROM UserCharacter WHERE id = :id")
    fun getCharacterSync(id: Long): UserCharacter?

    @Query("SELECT * FROM UserCharacter WHERE individualId = :individualId")
    fun getByIndividualIdSync(individualId: String): List<UserCharacter>

    @Query("SELECT * FROM UserCharacter WHERE individualId = :individualId")
    suspend fun getByIndividualId(individualId: String): List<UserCharacter>

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
        JOIN DisplaySprite s ON s.cardCharacterId = c.id
        WHERE monId = :monId
        ORDER BY t.id ASC
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
        ORDER BY t.id ASC
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
            s.spriteRun1 AS spriteRun1,
            s.spriteRun2 AS spriteRun2,
            s.width AS spriteWidth,
            s.height AS spriteHeight,
            c.nameSprite as nameSprite,
            c.nameWidth as nameSpriteWidth,
            c.nameHeight as nameSpriteHeight,
            COALESCE(d.isBEm, 0) as isBemCard,
            di.nickname AS nickname,
            COALESCE(sp.speciesName, sp.matchedName) AS speciesName,
            a.characterId = uc.id as isInAdventure,
            uc.isActive as active
        FROM UserCharacter uc
        JOIN CardCharacter c ON uc.charId = c.id
        LEFT JOIN Card d ON d.id = c.cardId
        JOIN DisplaySprite s ON s.cardCharacterId = c.id
        LEFT JOIN Adventure a ON a.characterId = uc.id
        LEFT JOIN DigimonIndividual di ON di.individualId = uc.individualId
        LEFT JOIN SpeciesProfile sp ON sp.cardCharacterId = c.id
        """
    )
    fun getAllCharacters(): Flow<List<CharacterDtos.CharacterWithSprites>>

    @Query(
        """
        SELECT
            uc.id AS sourceCharacterId,
            uc.individualId AS individualId,
            CASE WHEN assigned.id IS NOT NULL THEN 'dim' || printf('%03d',assignedCard.cardId) || '_mon' || printf('%02d',assigned.charaIndex+1)
                WHEN ca.id IS NOT NULL THEN 'dim' || printf('%03d',ca.cardId) || '_mon' || printf('%02d',c.charaIndex+1)
                ELSE 'app_mon' || printf('%06d',c.id) END AS externalCharacterId,
            COALESCE(NULLIF(di.nickname, ''), NULLIF(sp.speciesName, ''), NULLIF(sp.matchedName, ''), 'Stored Digimon #' || uc.id) AS displayName,
            COALESCE(assigned.stage,c.stage) AS stage,
            COALESCE(assigned.attribute,c.attribute) AS attribute,
            p.personalityType AS personalityType,
            COALESCE(assigned.baseHp,c.baseHp) AS baseHp,
            COALESCE(assigned.baseBp,c.baseBp) AS baseBp,
            COALESCE(assigned.baseAp,c.baseAp) AS baseAp,
            CASE WHEN assigned.id IS NOT NULL THEN 0 WHEN uc.characterType='APP' THEN COALESCE(app.trainingHp,0) ELSE COALESCE(be.trainingHp,0) END AS trainingHp,
            CASE WHEN assigned.id IS NOT NULL THEN 0 WHEN uc.characterType='APP' THEN COALESCE(app.trainingBp,0) ELSE COALESCE(be.trainingBp,0) END AS trainingBp,
            CASE WHEN assigned.id IS NOT NULL THEN 0 WHEN uc.characterType='APP' THEN COALESCE(app.trainingAp,0) ELSE COALESCE(be.trainingAp,0) END AS trainingAp,
            uc.vitalPoints AS vitalPoints,
            uc.mood AS mood,
            COALESCE(assignedCard.isBEm,ca.isBEm,0) AS isBemCard,
            sp.specialMoves AS specialMovesJson,
            di.blastMode AS blastMode,
            di.blastTargetSpecies AS blastTargetSpecies,
            di.jogressResultSpecies AS jogressResultSpecies,
            uc.characterType='APP' AS isAppPartner,
            assigned.id IS NOT NULL AS usesAssignedDim,
            CASE WHEN assigned.id IS NULL AND uc.characterType='APP' THEN COALESCE(app.originBasisPoints,10000) ELSE 10000 END AS originBasisPoints
        FROM UserCharacter uc
        JOIN CardCharacter c ON c.id = uc.charId
        LEFT JOIN Card ca ON ca.id = c.cardId
        LEFT JOIN DigimonIndividual di ON di.individualId = uc.individualId
        LEFT JOIN SpeciesProfile sp ON sp.cardCharacterId = c.id
        LEFT JOIN DigimonPersonalityTraits p ON p.individualId = uc.individualId
        LEFT JOIN BECharacterData be ON be.id = uc.id
        LEFT JOIN AppPartnerProgress app ON app.individualId = uc.individualId
        LEFT JOIN CardCharacter assigned ON uc.characterType='APP' AND assigned.id=uc.exportCardCharacterId AND assigned.cardId IS NOT NULL AND assigned.speciesId=c.speciesId
        LEFT JOIN Card assignedCard ON assignedCard.id=assigned.cardId
        """
    )
    suspend fun getAllBattleParticipantProfiles(): List<BattleParticipantProfile>

    @Query(
        """
        UPDATE UserCharacter
        SET currentPhaseBattlesWon = currentPhaseBattlesWon + CASE WHEN :won = 1 THEN 1 ELSE 0 END,
            currentPhaseBattlesLost = currentPhaseBattlesLost + CASE WHEN :won = 0 THEN 1 ELSE 0 END,
            totalBattlesWon = totalBattlesWon + CASE WHEN :won = 1 THEN 1 ELSE 0 END,
            totalBattlesLost = totalBattlesLost + CASE WHEN :won = 0 THEN 1 ELSE 0 END
        WHERE id = :characterId
        """
    )
    suspend fun recordBattleResult(characterId: Long, won: Boolean): Int

    @Query(
        """
        SELECT
            uc.*,
            c.stage,
            c.attribute,
            s.spriteIdle1 AS spriteIdle,
            s.spriteIdle2 AS spriteIdle2,
            s.spriteRun1 AS spriteRun1,
            s.spriteRun2 AS spriteRun2,
            s.width AS spriteWidth,
            s.height AS spriteHeight,
            c.nameSprite as nameSprite,
            c.nameWidth as nameSpriteWidth,
            c.nameHeight as nameSpriteHeight,
            COALESCE(d.isBEm, 0) as isBemCard,
            di.nickname AS nickname,
            COALESCE(sp.speciesName, sp.matchedName) AS speciesName,
            a.characterId = uc.id as isInAdventure,
            uc.isActive as active
        FROM UserCharacter uc
        JOIN CardCharacter c ON uc.charId = c.id
        LEFT JOIN Card d ON d.id = c.cardId
        JOIN DisplaySprite s ON s.cardCharacterId = c.id
        LEFT JOIN Adventure a ON a.characterId = uc.id
        LEFT JOIN DigimonIndividual di ON di.individualId = uc.individualId
        LEFT JOIN SpeciesProfile sp ON sp.cardCharacterId = c.id
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

    @Query("SELECT * FROM BECharacterData WHERE id = :id")
    suspend fun getBeDataOrNull(id: Long): BECharacterData?

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
            s.spriteRun1 AS spriteRun1,
            s.spriteRun2 AS spriteRun2,
            s.width AS spriteWidth,
            s.height AS spriteHeight,
            c.nameSprite as nameSprite,
            c.nameWidth as nameSpriteWidth,
            c.nameHeight as nameSpriteHeight,
            COALESCE(d.isBEm, 0) as isBemCard,
            di.nickname AS nickname,
            COALESCE(sp.speciesName, sp.matchedName) AS speciesName,
            a.characterId = uc.id as isInAdventure,
            uc.isActive as active
        FROM UserCharacter uc
        JOIN CardCharacter c ON uc.charId = c.id
        LEFT JOIN Card d ON d.id = c.cardId
        JOIN DisplaySprite s ON s.cardCharacterId = c.id
        LEFT JOIN Adventure a ON a.characterId = uc.id
        LEFT JOIN DigimonIndividual di ON di.individualId = uc.individualId
        LEFT JOIN SpeciesProfile sp ON sp.cardCharacterId = c.id
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

    @Transaction
    fun setOnlyActiveCharacter(id: Long) {
        clearActiveCharacter()
        setActiveCharacter(id)
    }

    @Query("UPDATE UserCharacter SET isFavorite = :isFavorite WHERE id = :id")
    fun setFavorite(id: Long, isFavorite: Boolean)

    @Query("UPDATE UserCharacter SET exportCardCharacterId = :targetId WHERE id = :id")
    suspend fun setExportTarget(id: Long, targetId: Long?)

    @Query("UPDATE UserCharacter SET charId = :stageId, vitalPoints = 0 WHERE id = :characterId")
    fun degenerateCharacter(characterId: Long, stageId: Long)

    @Query("SELECT * FROM TransformationHistory WHERE monId = :characterId AND id = :historyId")
    fun getTransformationHistoryEntry(characterId: Long, historyId: Long): TransformationHistory?

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
          AND id > :historyId
        """
    )
    fun deleteTransformationsAfter(
        characterId: Long,
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
            s.spriteRun1 AS spriteRun1,
            s.spriteRun2 AS spriteRun2,
            s.width AS spriteWidth,
            s.height AS spriteHeight,
            c.nameSprite as nameSprite,
            c.nameWidth as nameSpriteWidth,
            c.nameHeight as nameSpriteHeight,
            COALESCE(d.isBEm, 0) as isBemCard,
            di.nickname AS nickname,
            COALESCE(sp.speciesName, sp.matchedName) AS speciesName,
            a.characterId = uc.id as isInAdventure,
            uc.isActive as active
        FROM UserCharacter uc
        JOIN CardCharacter c ON uc.charId = c.id
        LEFT JOIN Card d ON d.id = c.cardId
        JOIN DisplaySprite s ON s.cardCharacterId = c.id
        LEFT JOIN Adventure a ON a.characterId = uc.id
        LEFT JOIN DigimonIndividual di ON di.individualId = uc.individualId
        LEFT JOIN SpeciesProfile sp ON sp.cardCharacterId = c.id
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
            s.spriteRun1 AS spriteRun1,
            s.spriteRun2 AS spriteRun2,
            s.width AS spriteWidth,
            s.height AS spriteHeight,
            c.nameSprite as nameSprite,
            c.nameWidth as nameSpriteWidth,
            c.nameHeight as nameSpriteHeight,
            COALESCE(d.isBEm, 0) as isBemCard,
            di.nickname AS nickname,
            COALESCE(sp.speciesName, sp.matchedName) AS speciesName,
            a.characterId = uc.id as isInAdventure,
            uc.isActive as active
        FROM UserCharacter uc
        JOIN CardCharacter c ON uc.charId = c.id
        LEFT JOIN Card d ON d.id = c.cardId
        JOIN DisplaySprite s ON s.cardCharacterId = c.id
        LEFT JOIN Adventure a ON a.characterId = uc.id
        LEFT JOIN DigimonIndividual di ON di.individualId = uc.individualId
        LEFT JOIN SpeciesProfile sp ON sp.cardCharacterId = c.id
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
        JOIN DisplaySprite s ON s.cardCharacterId = c.id
        WHERE uc.isActive = 1
        LIMIT 1
        """
    )
    suspend fun getActiveCharacterWidgetSprites(): CharacterDtos.WidgetSprites?
}
