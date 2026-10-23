package com.github.nacabaro.vbhelper.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.github.nacabaro.vbhelper.domain.transfer.*
import kotlinx.coroutines.flow.Flow

/** A Storage row whose individual currently lives inside a linked game save. */
data class AwayCharacterRow(val characterId: Long, val individualId: String, val sourceId: String)

@Dao
interface NativeMaterializationDao {
    @Query("SELECT * FROM NativeGameSnapshot WHERE id = :id")
    suspend fun snapshot(id: Long): NativeGameSnapshot?
    @Query("SELECT * FROM ExternalIndividualBinding WHERE bindingId = :id")
    suspend fun binding(id: String): ExternalIndividualBinding?
    @Query("SELECT MAX(id) FROM NativeGameSnapshot WHERE individualId = :id")
    suspend fun latestSnapshotId(id: String): Long?
    @Query("SELECT * FROM NativeGameSnapshot WHERE individualId=:id ORDER BY id LIMIT 1")
    suspend fun originSnapshot(id: String): NativeGameSnapshot?
    @Query("SELECT * FROM NativeCanonicalMapping WHERE snapshotId = :id")
    suspend fun mapping(id: Long): NativeCanonicalMapping?
    @Query("SELECT * FROM NativeCanonicalMapping")
    fun mappings(): Flow<List<NativeCanonicalMapping>>
    @Upsert suspend fun saveMapping(mapping: NativeCanonicalMapping)
    @Query("SELECT * FROM NativeMaterializationReceipt WHERE individualId = :id")
    suspend fun receipt(id: String): NativeMaterializationReceipt?
    @Insert suspend fun insertReceipt(receipt: NativeMaterializationReceipt)
    @Update suspend fun updateReceipt(receipt: NativeMaterializationReceipt)
    @Query("DELETE FROM NativeMaterializationReceipt WHERE individualId = :id")
    suspend fun deleteReceipt(id: String)
    @Query("SELECT EXISTS(SELECT 1 FROM GameImportItemReceipt WHERE individualId = :id AND contentRevision = :revision AND formatProfile = :profile AND codecVersion = :codec AND recordKey = :record)")
    suspend fun wasAcquired(id: String, revision: String, profile: String, codec: Int, record: String): Boolean
    @Query("SELECT EXISTS(SELECT 1 FROM WorldSpawn WHERE individualId = :id) OR EXISTS(SELECT 1 FROM WatchTransfer WHERE individualId = :id) OR EXISTS(SELECT 1 FROM GameEndpointPresence WHERE individualId = :id)")
    suspend fun isAway(id: String): Boolean
    @Query("SELECT * FROM GameEndpointPresence WHERE individualId = :id")
    suspend fun presence(id: String): GameEndpointPresence?
    @Query("SELECT u.id AS characterId, p.individualId AS individualId, p.sourceId AS sourceId FROM UserCharacter u JOIN GameEndpointPresence p ON p.individualId = u.individualId")
    fun observeAwayCharacters(): Flow<List<AwayCharacterRow>>
    @Upsert suspend fun savePresence(presence: GameEndpointPresence)
    @Query("DELETE FROM GameEndpointPresence WHERE individualId = :id")
    suspend fun clearPresence(id: String)
}
