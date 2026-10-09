package com.github.nacabaro.vbhelper.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.github.nacabaro.vbhelper.domain.scan.DigimonScanProgress
import com.github.nacabaro.vbhelper.domain.scan.DigimonScanReward
import com.github.nacabaro.vbhelper.dtos.DigimonScanRewardDetails
import com.github.nacabaro.vbhelper.dtos.DigimonScanEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface DigimonScanDao {
    @Query("SELECT percentage FROM DigimonScanProgress WHERE cardCharacterId = :cardCharacterId")
    suspend fun getProgress(cardCharacterId: Long): Int?

    @Query("SELECT percentage FROM DigimonScanProgress WHERE cardCharacterId = :cardCharacterId")
    fun observeProgress(cardCharacterId: Long): Flow<Int?>

    @Upsert
    suspend fun saveProgress(progress: DigimonScanProgress)

    @Query("UPDATE DigimonScanProgress SET percentage = 0, updatedAt = :now WHERE cardCharacterId = :cardCharacterId AND percentage >= 100")
    suspend fun consumeCompleteScan(cardCharacterId: Long, now: Long): Int

    @Insert
    suspend fun insertReward(reward: DigimonScanReward)

    @Query("SELECT * FROM DigimonScanReward WHERE interactionId = :interactionId ORDER BY cardCharacterId")
    fun observeRewards(interactionId: String): Flow<List<DigimonScanReward>>

    @Query("""
        SELECT reward.cardCharacterId, reward.percentageBefore, reward.percentageAfter,
            COALESCE(NULLIF(profile.speciesName, ''), NULLIF(profile.matchedName, '')) AS speciesName,
            character.charaIndex, card.name AS cardName,
            sprite.spriteIdle1 AS spriteIdle, sprite.width AS spriteWidth, sprite.height AS spriteHeight
        FROM DigimonScanReward reward
        JOIN CardCharacter character ON character.id = reward.cardCharacterId
        JOIN Card card ON card.id = character.cardId
        JOIN Sprite sprite ON sprite.id = character.spriteId
        LEFT JOIN SpeciesProfile profile ON profile.cardCharacterId = character.id
        WHERE reward.interactionId = :interactionId
        ORDER BY reward.cardCharacterId
    """)
    fun observeRewardDetails(interactionId: String): Flow<List<DigimonScanRewardDetails>>

    @Query("""
        SELECT character.id, character.charaIndex, character.stage, character.attribute,
            character.baseHp, character.baseBp, character.baseAp,
            character.nameSprite, character.nameWidth AS nameSpriteWidth, character.nameHeight AS nameSpriteHeight,
            sprite.spriteIdle1 AS spriteIdle, sprite.spriteIdle2 AS spriteIdle2,
            sprite.width AS spriteWidth, sprite.height AS spriteHeight,
            dex.discoveredOn,
            EXISTS(SELECT 1 FROM UserCharacter owned WHERE owned.charId = character.id) AS isCurrentlyAvailable,
            progress.percentage, card.name AS cardName,
            COALESCE(NULLIF(profile.speciesName, ''), NULLIF(profile.matchedName, '')) AS speciesName
        FROM DigimonScanProgress progress
        JOIN CardCharacter character ON character.id = progress.cardCharacterId
        JOIN Card card ON card.id = character.cardId
        JOIN Sprite sprite ON sprite.id = character.spriteId
        LEFT JOIN Dex dex ON dex.id = character.id
        LEFT JOIN SpeciesProfile profile ON profile.cardCharacterId = character.id
        WHERE progress.percentage > 0
        ORDER BY progress.percentage DESC, card.name COLLATE NOCASE, character.charaIndex, character.id
    """)
    fun observeCollection(): Flow<List<DigimonScanEntry>>
}
