package com.github.nacabaro.vbhelper.daos

import androidx.room.*
import com.github.nacabaro.vbhelper.domain.transfer.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TransferContinuityDao {
    @Query("SELECT * FROM CompanionConnection WHERE connectionId = :id") suspend fun connection(id: String): CompanionConnection?
    @Query("SELECT * FROM CompanionConnection WHERE serverId = :id") suspend fun byServer(id: String): CompanionConnection?
    @Upsert suspend fun saveConnection(connection: CompanionConnection)
    @Query("DELETE FROM CompanionConnection WHERE connectionId = :id") suspend fun forgetConnection(id: String)
    @Query("SELECT * FROM LinkedSaveLocation ORDER BY linkedAt") fun locations(): Flow<List<LinkedSaveLocation>>
    @Query("SELECT * FROM LinkedSaveLocation ORDER BY linkedAt") suspend fun allLocations(): List<LinkedSaveLocation>
    @Query("SELECT * FROM LinkedSaveLocation WHERE kind = :kind AND locator = :locator") suspend fun location(kind: String, locator: String): LinkedSaveLocation?
    @Upsert suspend fun saveLocation(location: LinkedSaveLocation)
    @Query("UPDATE LinkedSaveLocation SET lastScanAt = :time, lastError = :error, truncated = :truncated WHERE locationId = :id")
    suspend fun locationHealth(id: String, time: Long, error: String?, truncated: Boolean)
    @Query("DELETE FROM LinkedSaveLocation WHERE locationId = :id") suspend fun forgetLocation(id: String)
    @Query("SELECT documentKey FROM SaveLocationExclusion WHERE locationId = :id") suspend fun exclusions(id: String): List<String>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun exclude(exclusion: SaveLocationExclusion)
    @Query("DELETE FROM SaveLocationExclusion WHERE documentKey = :key") suspend fun include(key: String)
    @Query("SELECT EXISTS(SELECT 1 FROM SaveLocationExclusion WHERE documentKey = :key)") suspend fun isExcluded(key: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun event(event: IndividualLifeEvent)
    @Query("SELECT * FROM IndividualLifeEvent WHERE individualId = :id ORDER BY occurredAt DESC, eventId DESC LIMIT 500")
    fun history(id: String): Flow<List<IndividualLifeEvent>>
    @Query("SELECT * FROM IndividualLifeEvent WHERE eventId = 'materialized:' || :individualId")
    suspend fun materializedEvent(individualId: String): IndividualLifeEvent?
    @Query("DELETE FROM IndividualLifeEvent WHERE eventId = 'materialized:' || :individualId")
    suspend fun deleteMaterializedEvent(individualId: String)
    @Query("SELECT * FROM NativeAppProjection WHERE individualId = :id") suspend fun projection(id: String): NativeAppProjection?
    @Upsert suspend fun saveProjection(projection: NativeAppProjection)
    @Query("SELECT COALESCE(MAX(id),0) FROM AppProgressDelta WHERE individualId = :id") suspend fun lastDelta(id: String): Long
    @Query("SELECT * FROM AppProgressDelta WHERE individualId = :id ORDER BY id DESC LIMIT 500")
    fun deltas(id: String): Flow<List<AppProgressDelta>>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun container(container: NativeSaveContainer): Long
    @Insert suspend fun chunks(chunks: List<NativeSaveContainerChunk>)
    @Insert suspend fun bindContainers(bindings: List<NativeSnapshotContainer>)
    @Query("SELECT c.* FROM NativeSaveContainer c JOIN NativeSnapshotContainer b ON b.revision=c.revision WHERE b.snapshotId=:snapshot")
    suspend fun containerForSnapshot(snapshot: Long): NativeSaveContainer?
    @Query("SELECT * FROM NativeSaveContainerChunk WHERE revision=:revision ORDER BY chunkIndex")
    suspend fun chunks(revision: String): List<NativeSaveContainerChunk>
    @Query("SELECT * FROM UniversalJourneyAccount WHERE individualId=:id") suspend fun journey(id: String): UniversalJourneyAccount?
    @Upsert suspend fun saveJourney(journey: UniversalJourneyAccount)
    @Query("SELECT * FROM AppProgressDelta WHERE individualId=:id AND id>:after ORDER BY id") suspend fun pendingAppDeltas(id: String, after: Long): List<AppProgressDelta>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun progressReceipt(receipt: UniversalProgressReceipt): Long
    @Query("SELECT EXISTS(SELECT 1 FROM UniversalProgressReceipt WHERE eventId=:id)") suspend fun progressSeen(id: String): Boolean
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun pendingNative(progress: PendingNativeProgress)
    @Query("SELECT COUNT(*) FROM PendingNativeProgress WHERE individualId=:id") suspend fun pendingNativeCount(id: String): Int
    @Query("SELECT * FROM PendingNativeProgress WHERE individualId=:id ORDER BY snapshotId DESC LIMIT 32")
    suspend fun recentPendingNative(id: String): List<PendingNativeProgress>
    @Query("SELECT snapshotId FROM PendingNativeProgress WHERE individualId=:id ORDER BY snapshotId DESC LIMIT 32")
    suspend fun recentPendingNativeIds(id: String): List<Long>
    @Insert suspend fun projectionGuard(guard: ProgressProjectionGuard)
    @Query("DELETE FROM ProgressProjectionGuard WHERE individualId=:id") suspend fun clearProjectionGuard(id: String)
}
