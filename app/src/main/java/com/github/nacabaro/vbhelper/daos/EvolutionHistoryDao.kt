package com.github.nacabaro.vbhelper.daos

import androidx.room.Dao
import androidx.room.Query
import com.github.nacabaro.vbhelper.domain.device_data.EvolutionHistoryRepair.Entry
import com.github.nacabaro.vbhelper.domain.device_data.EvolutionHistoryRepair.Route
import com.github.nacabaro.vbhelper.domain.device_data.EvolutionHistoryRepair.Species

@Dao
interface EvolutionHistoryDao {
    @Query("SELECT id FROM UserCharacter ORDER BY id")
    fun getStoredCharacterIds(): List<Long>

    @Query("""
        SELECT c.id, c.cardId, c.charaIndex, c.stage FROM CardCharacter c
        JOIN UserCharacter u ON u.charId = c.id WHERE u.id = :characterId
    """)
    fun getCurrentSpecies(characterId: Long): Species?

    @Query("SELECT id, cardId, charaIndex, stage FROM CardCharacter WHERE cardId = :cardId")
    fun getSpecies(cardId: Long): List<Species>

    @Query("""
        SELECT p.charaId AS fromId, p.toCharaId AS toId FROM PossibleTransformations p
        JOIN CardCharacter c ON c.id = p.charaId
        WHERE c.cardId = :cardId AND p.toCharaId IS NOT NULL
        UNION
        SELECT f.fromCharaId AS fromId, f.toCharaId AS toId FROM CardFusions f
        JOIN CardCharacter c ON c.id = f.fromCharaId WHERE c.cardId = :cardId
    """)
    fun getRoutes(cardId: Long): List<Route>

    @Query("SELECT stageId AS speciesId, transformationDate AS date FROM TransformationHistory WHERE monId = :characterId ORDER BY id ASC")
    fun getHistory(characterId: Long): List<Entry>

    @Query("DELETE FROM TransformationHistory WHERE monId = :characterId")
    fun deleteHistory(characterId: Long)
}
