package com.github.nacabaro.vbhelper.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Embedded
import com.github.nacabaro.vbhelper.domain.personality.DigimonPersonalityType
import com.github.nacabaro.vbhelper.domain.transfer.ExternalIndividualBinding
import com.github.nacabaro.vbhelper.domain.transfer.NativeGameSnapshot
import kotlinx.coroutines.flow.Flow
import com.github.nacabaro.vbhelper.source.gameSave.NativeSpeciesHint

data class NativeRefreshAppRow(val individualId: String, val nickname: String?, val personalityType: DigimonPersonalityType,
    val generatedAt: Long, val systemVersion: Int)
data class NativeArchiveRecord(@Embedded val snapshot: NativeGameSnapshot, val appNickname: String?, val appPersonality: DigimonPersonalityType?, val appCharacterId: Long?, val identityConfidence: String)
data class NativeAnchorRow(val snapshotId: Long, val contentRevision: String, val codecVersion: Int, val gameId: String, val formatProfile: String, val recordJson: String)

@Dao
interface NativeGameArchiveDao {
    @Query("SELECT b.sourceId, b.documentKey, b.gameId, b.formatProfile, s.codecVersion, b.nativeIdentity, m.nativeSpeciesId, m.canonicalSpeciesId FROM NativeCanonicalMapping m JOIN NativeGameSnapshot s ON s.id=m.snapshotId JOIN ExternalIndividualBinding b ON b.bindingId=s.bindingId WHERE (b.sourceId=:sourceId OR b.documentKey=:documentKey) AND m.authority='USER_CONFIRMED' AND NOT EXISTS (SELECT 1 FROM NativeGameSnapshot n WHERE n.bindingId=s.bindingId AND n.id>s.id)")
    suspend fun presentationMappings(sourceId: String, documentKey: String): List<NativeSpeciesHint>

    @Query("SELECT * FROM ExternalIndividualBinding WHERE bindingId=:id")
    suspend fun binding(id: String): ExternalIndividualBinding?

    @Query("SELECT * FROM ExternalIndividualBinding WHERE individualId=:individualId")
    suspend fun bindingsForIndividual(individualId: String): List<ExternalIndividualBinding>

    @Query("SELECT * FROM ExternalIndividualBinding")
    suspend fun allBindings(): List<ExternalIndividualBinding>

    @Query("SELECT s.* FROM NativeGameSnapshot s WHERE s.id=:id AND NOT EXISTS (SELECT 1 FROM NativeGameSnapshot newer WHERE newer.individualId=s.individualId AND newer.id>s.id) AND NOT EXISTS (SELECT 1 FROM WorldSpawn w WHERE w.individualId=s.individualId) AND NOT EXISTS (SELECT 1 FROM WatchTransfer t WHERE t.individualId=s.individualId) AND NOT EXISTS (SELECT 1 FROM GameEndpointPresence g WHERE g.individualId=s.individualId)")
    suspend fun planningSnapshot(id: Long): NativeGameSnapshot?

    @Query("SELECT * FROM ExternalIndividualBinding WHERE (sourceId = :source OR documentKey = :document) AND gameId = :game AND formatProfile = :profile AND identityScheme = :scheme AND nativeIdentity IN (:identities)")
    suspend fun bindings(source: String, document: String, game: String, profile: String, scheme: String, identities: List<String>): List<ExternalIndividualBinding>
    @Query("SELECT * FROM ExternalIndividualBinding WHERE gameId=:game AND formatProfile=:profile AND identityScheme=:scheme AND nativeIdentity IN (:identities)")
    suspend fun possibleBindings(game: String, profile: String, scheme: String, identities: List<String>): List<ExternalIndividualBinding>
    @Insert suspend fun insertBinding(binding: ExternalIndividualBinding)
    @Insert suspend fun insertSnapshot(snapshot: NativeGameSnapshot): Long

    @Query("SELECT * FROM NativeGameSnapshot s WHERE bindingId IN (:bindings) AND NOT EXISTS (SELECT 1 FROM NativeGameSnapshot n WHERE n.bindingId = s.bindingId AND n.id > s.id)")
    suspend fun latestSnapshots(bindings: List<String>): List<NativeGameSnapshot>

    @Query("SELECT * FROM NativeGameSnapshot WHERE bindingId IN (:bindings) AND contentRevision = :revision")
    suspend fun snapshotsForRevision(bindings: List<String>, revision: String): List<NativeGameSnapshot>

    @Query("SELECT s.id AS snapshotId, s.contentRevision AS contentRevision, s.codecVersion AS codecVersion, b.gameId AS gameId, b.formatProfile AS formatProfile, s.recordJson AS recordJson FROM NativeGameSnapshot s JOIN ExternalIndividualBinding b ON b.bindingId = s.bindingId WHERE s.individualId = :id ORDER BY s.id DESC LIMIT 16")
    suspend fun nativeAnchors(id: String): List<NativeAnchorRow>

    @Query("SELECT i.individualId, i.nickname, p.personalityType, p.generatedAt, p.systemVersion FROM DigimonIndividual i JOIN DigimonPersonalityTraits p ON p.individualId = i.individualId WHERE i.individualId IN (:ids)")
    suspend fun appValues(ids: List<String>): List<NativeRefreshAppRow>

    @Query("UPDATE DigimonIndividual SET nickname = :nickname WHERE individualId = :id")
    suspend fun updateNickname(id: String, nickname: String?)

    @Query("SELECT s.*, i.nickname AS appNickname, p.personalityType AS appPersonality, u.id AS appCharacterId, b.identityConfidence FROM NativeGameSnapshot s JOIN ExternalIndividualBinding b ON b.bindingId=s.bindingId JOIN DigimonIndividual i ON i.individualId = s.individualId LEFT JOIN DigimonPersonalityTraits p ON p.individualId = s.individualId LEFT JOIN UserCharacter u ON u.individualId = s.individualId WHERE NOT EXISTS (SELECT 1 FROM NativeGameSnapshot newer WHERE newer.individualId = s.individualId AND newer.id > s.id) AND NOT EXISTS (SELECT 1 FROM WorldSpawn w WHERE w.individualId = s.individualId) AND NOT EXISTS (SELECT 1 FROM WatchTransfer t WHERE t.individualId = s.individualId) AND NOT EXISTS (SELECT 1 FROM GameEndpointPresence g WHERE g.individualId = s.individualId) ORDER BY s.id DESC")
    fun observeArchives(): Flow<List<NativeArchiveRecord>>
}
