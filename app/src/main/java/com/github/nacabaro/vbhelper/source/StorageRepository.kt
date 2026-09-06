package com.github.nacabaro.vbhelper.source

import com.github.nacabaro.vbhelper.database.AppDatabase
import com.github.nacabaro.vbhelper.domain.device_data.BECharacterData
import com.github.nacabaro.vbhelper.domain.device_data.SpecialMissions
import com.github.nacabaro.vbhelper.domain.device_data.VBCharacterData
import com.github.nacabaro.vbhelper.domain.personality.DigimonPersonalityGenerator
import com.github.nacabaro.vbhelper.domain.personality.DigimonPersonalityTraits
import com.github.nacabaro.vbhelper.dtos.CharacterDtos
import com.github.nacabaro.vbhelper.dtos.ItemDtos
import kotlinx.coroutines.flow.Flow

class StorageRepository (
    private val db: AppDatabase
) {
    fun getAllCharacters(): Flow<List<CharacterDtos.CharacterWithSprites>> {
        return db.userCharacterDao().getAllCharacters()
    }

    suspend fun getSingleCharacter(id: Long): CharacterDtos.CharacterWithSprites {
        return db.userCharacterDao().getCharacterWithSprites(id)
    }

    fun getCharacterBeData(id: Long): Flow<BECharacterData> {
        return db.userCharacterDao().getBeData(id)
    }

    fun getTransformationHistory(characterId: Long): Flow<List<CharacterDtos.TransformationHistory>> {
        return db.userCharacterDao().getTransformationHistory(characterId)
    }

    fun getCharacterVbData(id: Long): Flow<VBCharacterData> {
        return db.userCharacterDao().getVbData(id)
    }

    fun getSpecialMissions(id: Long): Flow<List<SpecialMissions>> {
        return db.userCharacterDao().getSpecialMissions(id)
    }

    suspend fun getItem(id: Long): ItemDtos.ItemsWithQuantities {
        return db.itemDao().getItem(id)
    }

    suspend fun getOrCreatePersonality(characterId: Long): DigimonPersonalityTraits {
        val character = db.userCharacterDao().getCharacter(characterId)
        db.digimonIndividualDao().getPersonality(character.individualId)?.let { return it }

        val characterInfo = db.userCharacterDao().getCharacterInfo(characterId)
        val generated = DigimonPersonalityGenerator.generate(
            individualId = character.individualId,
            attribute = characterInfo.attribute,
            stage = characterInfo.stage
        )
        db.digimonIndividualDao().insertPersonality(generated)
        return db.digimonIndividualDao().getPersonality(character.individualId) ?: generated
    }

    suspend fun ensureAllPersonalities() {
        for (characterId in db.userCharacterDao().getAllCharacterIds()) {
            getOrCreatePersonality(characterId)
        }
    }

    fun getActiveCharacter(): Flow<CharacterDtos.CharacterWithSprites?> {
        return db.userCharacterDao().getActiveCharacter()
    }

    suspend fun updateNickname(characterId: Long, nickname: String?) {
        db.digimonIndividualDao().updateNicknameForCharacter(characterId, nickname)
    }

    fun deleteCharacter(id: Long) {
        return db.userCharacterDao().deleteCharacterById(id)
    }

    fun getAdventureCharacters(): Flow<List<CharacterDtos.AdventureCharacterWithSprites>> {
        return db.adventureDao().getAdventureCharacters()
    }

    suspend fun getBECharacters(): List<CharacterDtos.CharacterWithSprites> {
        return db.userCharacterDao().getBECharacters()
    }

    suspend fun getVBCharacters(): List<CharacterDtos.CharacterWithSprites> {
        return db.userCharacterDao().getVBDimCharacters()
    }
}