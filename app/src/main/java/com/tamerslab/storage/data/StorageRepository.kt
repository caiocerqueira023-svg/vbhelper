package com.tamerslab.storage.data

import androidx.room.withTransaction
import com.tamerslab.data.database.AppDatabase
import com.tamerslab.domain.device_data.BECharacterData
import com.tamerslab.domain.device_data.SpecialMissions
import com.tamerslab.domain.device_data.VBCharacterData
import com.tamerslab.domain.personality.DigimonPersonalityGenerator
import com.tamerslab.domain.personality.DigimonPersonalityTraits
import com.tamerslab.dtos.CharacterDtos
import com.tamerslab.dtos.ItemDtos
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class StorageRepository (
    private val db: AppDatabase,
    /** Applies the VB-first shipped-art priority to the sprite bytes the database view resolved as Dot. */
    private val appearances: com.tamerslab.species.SpeciesAppearanceRepository? = null,
) {
    fun getAllCharacters(): Flow<List<CharacterDtos.CharacterWithSprites>> {
        return db.userCharacterDao().getAllCharacters()
            .map { com.tamerslab.species.BundledVbArtwork.characters(it, appearances) }
    }

    suspend fun getSingleCharacter(id: Long): CharacterDtos.CharacterWithSprites {
        return com.tamerslab.species.BundledVbArtwork.character(
            db.userCharacterDao().getCharacterWithSprites(id), appearances)
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
        preservedImportedPersonality(character.individualId)?.let { return it }
        db.digimonIndividualDao().getPersonality(character.individualId)
            ?.takeIf { it.isCurrentSystem }
            ?.let { return it }

        val characterInfo = db.userCharacterDao().getCharacterInfo(characterId)
        val generated = DigimonPersonalityGenerator.generate(
            individualId = character.individualId,
            attribute = characterInfo.attribute,
            stage = characterInfo.stage
        )
        db.digimonIndividualDao().upsertPersonality(generated)
        return db.digimonIndividualDao().getPersonality(character.individualId) ?: generated
    }

    suspend fun getOrCreatePersonalityForIndividual(
        individualId: String,
        attribute: com.tamerslab.hardware.nfc.data.NfcCharacter.Attribute?,
        stage: Int
    ): DigimonPersonalityTraits {
        preservedImportedPersonality(individualId)?.let { return it }
        db.digimonIndividualDao().getPersonality(individualId)
            ?.takeIf { it.isCurrentSystem }
            ?.let { return it }
        val generated = DigimonPersonalityGenerator.generate(
            individualId = individualId,
            attribute = attribute,
            stage = stage
        )
        db.digimonIndividualDao().upsertPersonality(generated)
        return db.digimonIndividualDao().getPersonality(individualId) ?: generated
    }

    private suspend fun preservedImportedPersonality(individualId: String): DigimonPersonalityTraits? {
        val current = db.digimonIndividualDao().getPersonality(individualId)
        if (current != null && current.systemVersion >= com.tamerslab.domain.personality.CURRENT_PERSONALITY_SYSTEM_VERSION) return current
        if (db.digimonIndividualDao().getIndividual(individualId)?.originKind != com.tamerslab.domain.device_data.IndividualOrigin.IMPORTED) return null
        val preserved = current ?: db.nativeMaterializationDao().latestSnapshotId(individualId)?.let { snapshotId ->
            db.nativeMaterializationDao().snapshot(snapshotId)?.let { snapshot ->
                val reviewed = snapshot.reviewAuditJson?.let { raw -> runCatching {
                    com.google.gson.Gson().fromJson(raw, com.tamerslab.transfer.data.gameSave.NativeRefreshAudit::class.java).afterAppValues
                }.getOrNull() }
                DigimonPersonalityTraits(individualId, com.tamerslab.domain.personality.DigimonPersonalityType.valueOf(
                    reviewed?.personality ?: snapshot.mappedPersonality), reviewed?.personalityUpdatedAt ?: snapshot.importedAt)
            }
        } ?: return null
        // A rules upgrade must not randomize a verified import or an explicit later app edit.
        val upgraded = preserved.copy(systemVersion = com.tamerslab.domain.personality.CURRENT_PERSONALITY_SYSTEM_VERSION)
        db.digimonIndividualDao().upsertPersonality(upgraded)
        return upgraded
    }

    suspend fun ensureAllPersonalities() {
        for (characterId in db.userCharacterDao().getAllCharacterIds()) {
            getOrCreatePersonality(characterId)
        }
    }

    fun getActiveCharacter(): Flow<CharacterDtos.CharacterWithSprites?> {
        return db.userCharacterDao().getActiveCharacter()
            .map { character -> character?.let { com.tamerslab.species.BundledVbArtwork.character(it, appearances) } }
    }

    suspend fun updateNickname(characterId: Long, nickname: String?) {
        db.digimonIndividualDao().updateNicknameForCharacter(characterId, nickname)
    }

    fun getAdventureCharacters(): Flow<List<CharacterDtos.AdventureCharacterWithSprites>> {
        return db.adventureDao().getAdventureCharacters()
    }

    suspend fun getBECharacters(): List<CharacterDtos.CharacterWithSprites> {
        return com.tamerslab.species.BundledVbArtwork.characters(db.userCharacterDao().getBECharacters(), appearances)
    }

    suspend fun getVBCharacters(): List<CharacterDtos.CharacterWithSprites> {
        return com.tamerslab.species.BundledVbArtwork.characters(db.userCharacterDao().getVBDimCharacters(), appearances)
    }

    suspend fun setOnlyActive(characterId: Long) = db.withTransaction {
        db.participation?.snapshot()?.requireOwned(characterId)
        db.userCharacterDao().setOnlyActiveCharacter(characterId)
    }

    suspend fun favouriteIds(): List<Long> = db.userCharacterDao().favouriteCharacterIds()

    suspend fun setFavorite(characterId: Long, isFavorite: Boolean) =
        db.userCharacterDao().setFavorite(characterId, isFavorite)

    suspend fun individualId(characterId: Long): String? =
        db.userCharacterDao().getCharacterSync(characterId)?.individualId

    suspend fun delete(characterId: Long) = db.userCharacterDao().deleteCharacterById(characterId)
}
