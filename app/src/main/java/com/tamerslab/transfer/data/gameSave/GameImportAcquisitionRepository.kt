package com.tamerslab.transfer.data.gameSave

import androidx.room.withTransaction
import com.tamerslab.data.database.AppDatabase
import com.tamerslab.domain.device_data.DigimonIndividual
import com.tamerslab.domain.device_data.IndividualOrigin
import com.tamerslab.domain.personality.DigimonPersonalityTraits
import com.tamerslab.domain.personality.DigimonPersonalityType
import com.tamerslab.domain.transfer.*
import com.tamerslab.gamesave.*
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import java.security.MessageDigest

/** Compiled format/copy policy. Provisional candidate matching is distinct from verified native identity. */
class VerifiedSnapshotImportProfile(
    val gameId: String,
    val formatProfile: String,
    val codecVersion: Int,
    val identityScheme: String,
    personalityMap: Map<Int, DigimonPersonalityType>,
    val nativeLevelRange: IntRange? = null,
    val provisionalIdentity: Boolean = false,
    val allowedSnapshotContainers: Set<SaveContainer> = SaveContainer.entries.toSet(),
    val appPersonalityOnly: Boolean = false,
    val crossSourceIdentityReview: Boolean = true,
) {
    private val personalityMap = personalityMap.toMap()
    init {
        require(gameId.isNotBlank() && formatProfile.isNotBlank() && codecVersion > 0 && identityScheme.isNotBlank())
        require(if (appPersonalityOnly) personalityMap.isEmpty() else personalityMap.isNotEmpty())
        require(nativeLevelRange == null || nativeLevelRange.first < nativeLevelRange.last)
    }
    fun matches(observation: SaveObservation) = matches(observation.gameId, observation.formatProfile, observation.codecVersion)
    fun matches(game: String, profile: String, codec: Int) = gameId == game && formatProfile == profile && codecVersion == codec
    fun personality(record: ObservedDigimon): DigimonPersonalityType = requireNotNull(personalityMap[record.personalityId]) { "Unverified personality mapping" }
    fun supportsPersonality(record: ObservedDigimon): Boolean =
        if (appPersonalityOnly) record.personalityId == null else personalityMap.containsKey(record.personalityId)

    /** Initialized once from the new app UUID, not inferred from native stats or rerolled on refresh. */
    fun initialAppPersonality(record: ObservedDigimon, individualId: String): DigimonPersonalityType {
        require(supportsPersonality(record) && individualId.isNotBlank())
        if (!appPersonalityOnly) return personality(record)
        val seed = MessageDigest.getInstance("SHA-256").digest("app-personality-v1:$individualId".toByteArray(Charsets.UTF_8))
        return DigimonPersonalityType.entries[(seed[0].toInt() and 255) % DigimonPersonalityType.entries.size]
    }
    fun nativeIdentity(record: ObservedDigimon): String {
        record.nativeIdentity?.let {
            require(it.isNotBlank() && it.length <= 256 && it.none(Char::isISOControl))
            return "native:$it"
        }
        require(record.identityCandidate > 0)
        return record.identityCandidate.toString()
    }
}

class ImportIdentityReviewRequiredException : IllegalStateException("Existing native identity requires reviewed refresh")
class ImportPreviewInvalidException : IllegalStateException("Import preview is no longer current")
data class SnapshotAcquisitionResult(val receipts: List<GameImportItemReceipt>, val createdCount: Int, val refreshedCount: Int = 0)
data class NativeArchiveView(val snapshot: NativeGameSnapshot, val record: ObservedDigimon?, val nickname: String? = null,
    val personality: DigimonPersonalityType? = null, val characterId: Long? = null, val identityConfidence: String = "PROVISIONAL")

/** Native archival acquisition only. It never creates an eligible gameplay/watch representation. */
class GameImportAcquisitionRepository(
    private val db: AppDatabase,
    private val sources: LinkedGameSaveRepository,
    private val previews: SaveImportPreviewRepository,
    profiles: List<VerifiedSnapshotImportProfile> = productionProfiles,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newIndividualId: () -> String = { UUID.randomUUID().toString() },
) {
    private val profiles = profiles.toList()
    private val gson = Gson()
    private val archive get() = db.nativeGameArchiveDao()
    val archives = archive.observeArchives().map { snapshots -> snapshots.map { row ->
        val snapshot = row.snapshot
        NativeArchiveView(snapshot, runCatching { gson.fromJson(snapshot.recordJson, ObservedDigimon::class.java) }.getOrNull(),
            row.appNickname, row.appPersonality, row.appCharacterId, row.identityConfidence)
    } }.flowOn(Dispatchers.IO)

    fun canCommit(preview: SnapshotImportPreview): Boolean {
        if (!preview.canCommit || preview.plannerVersion != SnapshotImportPlanner.VERSION) return false
        val profile = profiles.singleOrNull { it.matches(preview.gameId, preview.formatProfile, preview.codecVersion) } ?: return false
        if (profile.provisionalIdentity != preview.requiresIdentityReview || preview.items.any { it.record.container !in profile.allowedSnapshotContainers }) return false
        return preview.items.filter { it.action == ImportItemAction.NEW_SNAPSHOT }
            .all { profile.supportsPersonality(it.record) }
    }

    fun supportsNativeRepresentation(game: String, profile: String, codec: Int, identityScheme: String): Boolean =
        profiles.singleOrNull { it.matches(game, profile, codec) && it.identityScheme == identityScheme } != null

    fun originBasisPoints(game: String, profile: String, codec: Int, record: ObservedDigimon): Int {
        val range = profiles.singleOrNull { it.matches(game, profile, codec) }?.nativeLevelRange
            ?: error("Origin stat projection requires a declared source normalization policy")
        return OriginStatInfluence.fromKnownLevel(record.level, range.first, range.last)
    }
    fun hasOriginProjection(game: String, profile: String, codec: Int): Boolean =
        profiles.singleOrNull { it.matches(game, profile, codec) }?.nativeLevelRange != null

    suspend fun hasNativeArchive(individualId: String): Boolean = withContext(Dispatchers.IO) {
        archive.bindingsForIndividual(individualId).isNotEmpty()
    }

    /** Deletes an imported individual and every native archive row acquired for it, so deleting
     *  a Digimon from Storage never leaves a placeholder card behind. */
    suspend fun forgetIndividual(individualId: String) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val sql = db.openHelper.writableDatabase
            val snapshots = mutableListOf<Long>()
            sql.query("SELECT id FROM NativeGameSnapshot WHERE individualId = ?", arrayOf(individualId)).use { rows ->
                while (rows.moveToNext()) snapshots += rows.getLong(0)
            }
            sql.execSQL("DELETE FROM NativeMaterializationReceipt WHERE individualId = ?", arrayOf(individualId))
            if (snapshots.isNotEmpty()) {
                val marks = snapshots.joinToString(",") { "?" }
                val arguments = snapshots.map { it as Any? }.toTypedArray()
                sql.execSQL("DELETE FROM NativeCanonicalMapping WHERE snapshotId IN ($marks)", arguments)
                sql.execSQL("DELETE FROM PendingNativeProgress WHERE snapshotId IN ($marks)", arguments)
                sql.execSQL("DELETE FROM NativeSnapshotContainer WHERE snapshotId IN ($marks)", arguments)
            }
            sql.execSQL("DELETE FROM GameImportItemReceipt WHERE individualId = ?", arrayOf(individualId))
            sql.execSQL("DELETE FROM NativeGameSnapshot WHERE individualId = ?", arrayOf(individualId))
            sql.execSQL("DELETE FROM ExternalIndividualBinding WHERE individualId = ?", arrayOf(individualId))
            sql.execSQL("DELETE FROM UserCharacter WHERE individualId = ?", arrayOf(individualId))
            sql.execSQL("DELETE FROM DigimonIndividual WHERE individualId = ?", arrayOf(individualId))
            sql.execSQL("DELETE FROM NativeSaveContainer WHERE NOT EXISTS (SELECT 1 FROM NativeSnapshotContainer c WHERE c.revision = NativeSaveContainer.revision)")
        }
    }

    suspend fun commit(draftId: String): SnapshotAcquisitionResult = withContext(Dispatchers.IO) {
        val saved = requireNotNull(previews.load(draftId))
        check(canCommit(saved.preview)) { "Snapshot acquisition is not supported for this preview" }
        val draft = saved.draft
        val profile = profiles.single { it.matches(draft.gameId, draft.formatProfile, draft.codecVersion) }
        sources.readCapturedFresh(draft.sourceId).use { captured ->
        val fresh = captured.observation
        if (fresh.contentRevision != draft.contentRevision || !profile.matches(fresh)) throw ImportSourceChangedException()
        db.withTransaction {
            val source = requireNotNull(db.linkedGameSaveDao().get(draft.sourceId))
            check(source.documentKey == draft.documentKey)
            if (WorldDsCopySelection.apply(fresh, source.reviewedCopyRevision, source.reviewedSaveCopy) != fresh)
                throw ImportSourceChangedException()
            val receiptsDao = db.saveImportDraftDao()
            val keys = saved.preview.items.map { it.record.recordKey }
            val existing = receiptsDao.receipts(draft.sourceId, fresh.contentRevision, fresh.formatProfile, fresh.codecVersion)
                .filter { it.recordKey in keys }.associateBy { it.recordKey }
            if (keys.all { it in existing }) return@withTransaction SnapshotAcquisitionResult(keys.map { existing.getValue(it) }, 0)
            val current = SnapshotImportPlanner.preview(fresh, keys, existing.keys)
            if (!current.canCommit || current.fingerprint != draft.fingerprint ||
                current.items.map { it.record } != saved.preview.items.map { it.record }) throw ImportPreviewInvalidException()
            val pending = current.items.filter { it.action == ImportItemAction.NEW_SNAPSHOT }
            // Validate the entire selection before creating any permanent data.
            check(pending.all { profile.supportsPersonality(it.record) })
            val identities = pending.map { profile.nativeIdentity(it.record) }
            val previous = if (profile.provisionalIdentity && profile.crossSourceIdentityReview) archive.possibleBindings(fresh.gameId, fresh.formatProfile, profile.identityScheme, identities)
                else archive.bindings(source.sourceId, source.documentKey, fresh.gameId, fresh.formatProfile, profile.identityScheme, identities)
            if (previous.isNotEmpty()) throw ImportIdentityReviewRequiredException()
            val now = clock()
            val snapshotIds = mutableListOf<Long>()
            val added = pending.map { item ->
                val record = item.record
                val individualId = newIndividualId()
                check(individualId.isNotBlank())
                // The native stored name is preserved in its own representation; it is not guessed to be a nickname.
                check(db.digimonIndividualDao().insert(DigimonIndividual(individualId, now,
                    originKind = IndividualOrigin.IMPORTED)) != -1L) { "Individual identity collision" }
                val personality = profile.initialAppPersonality(record, individualId)
                db.digimonIndividualDao().upsertPersonality(DigimonPersonalityTraits(individualId, personality, now))
                val bindingId = UUID.randomUUID().toString()
                archive.insertBinding(ExternalIndividualBinding(bindingId, source.sourceId, source.documentKey, fresh.gameId,
                    fresh.formatProfile, profile.identityScheme, profile.nativeIdentity(record), individualId, now,
                    identityConfidence = if (profile.provisionalIdentity) "PROVISIONAL" else "VERIFIED"))
                snapshotIds += archive.insertSnapshot(NativeGameSnapshot(individualId = individualId, bindingId = bindingId,
                    sourceLabel = source.label, contentRevision = fresh.contentRevision, codecVersion = fresh.codecVersion,
                    recordKey = record.recordKey, recordJson = gson.toJson(record), nativePersonalityId = record.personalityId,
                    mappedPersonality = personality.name, importedAt = now))
                GameImportItemReceipt(UUID.randomUUID().toString(), source.sourceId, fresh.contentRevision,
                    fresh.formatProfile, fresh.codecVersion, record.recordKey, individualId, now).also { receiptsDao.insertReceipt(it) }
            }
            NativeContainerRepository(db).retain(captured, snapshotIds)
            SnapshotAcquisitionResult((existing.values + added).sortedBy { it.recordKey }, added.size)
        }
        }
    }

    suspend fun prepareRefresh(draftId: String): NativeRefreshReview = withContext(Dispatchers.IO) {
        val saved = requireNotNull(previews.load(draftId))
        check(canCommit(saved.preview))
        val fresh = sources.readFresh(saved.draft.sourceId)
        db.withTransaction { refreshReviewLocked(saved, fresh) }
    }

    private suspend fun refreshReviewLocked(saved: SavedImportPreview, fresh: SaveObservation): NativeRefreshReview {
        val draft = saved.draft
        val profile = profiles.singleOrNull { it.matches(fresh) } ?: throw ImportPreviewInvalidException()
        if (fresh.contentRevision != draft.contentRevision || !profile.matches(draft.gameId, draft.formatProfile, draft.codecVersion)) throw ImportSourceChangedException()
        val source = requireNotNull(db.linkedGameSaveDao().get(draft.sourceId))
        if (source.documentKey != draft.documentKey) throw ImportPreviewInvalidException()
        if (WorldDsCopySelection.apply(fresh, source.reviewedCopyRevision, source.reviewedSaveCopy) != fresh)
            throw ImportSourceChangedException()
        val keys = saved.preview.items.map { it.record.recordKey }
        val imported = db.saveImportDraftDao().importedKeys(source.sourceId, fresh.contentRevision, fresh.formatProfile, fresh.codecVersion).toSet()
        val current = SnapshotImportPlanner.preview(fresh, keys, imported)
        if (!current.canCommit || current.fingerprint != draft.fingerprint || current.items.map { it.record } != saved.preview.items.map { it.record }) throw ImportPreviewInvalidException()
        if (current.items.any { it.action != ImportItemAction.NEW_SNAPSHOT }) throw ImportIdentityReviewRequiredException()
        val keysToReview = current.items.map { profile.nativeIdentity(it.record) }
        val possible = if (profile.provisionalIdentity && profile.crossSourceIdentityReview) archive.possibleBindings(fresh.gameId, fresh.formatProfile, profile.identityScheme, keysToReview)
            else archive.bindings(source.sourceId, source.documentKey, fresh.gameId, fresh.formatProfile, profile.identityScheme, keysToReview)
        val bindings = possible.groupBy { it.nativeIdentity }
        val ids = bindings.values.flatten().map { it.bindingId }
        val snapshots = archive.latestSnapshots(ids).associateBy { it.bindingId }
        val app = archive.appValues(bindings.values.flatten().map { it.individualId }).associateBy { it.individualId }
        val items = current.items.map { item ->
            // Mixed new/known identities or conflicting bindings require a new explicit selection.
            val binding = bindings[profile.nativeIdentity(item.record)]?.singleOrNull() ?: throw ImportIdentityReviewRequiredException()
            val snapshot = snapshots[binding.bindingId] ?: throw ImportPreviewInvalidException()
            check(snapshot.individualId == binding.individualId)
            val before = gson.fromJson(snapshot.recordJson, ObservedDigimon::class.java)
            check(profile.nativeIdentity(before) == binding.nativeIdentity)
            val values = requireNotNull(app[binding.individualId]).let { RefreshAppValues(it.nickname, it.personalityType.name, it.systemVersion, it.generatedAt) }
            check(profile.supportsPersonality(item.record))
            val incomingPersonality = if (profile.appPersonalityOnly) values.personality else profile.personality(item.record).name
            val baselinePersonality = if (profile.appPersonalityOnly) values.personality else snapshot.mappedPersonality
            NativeRefreshItemReview(binding, snapshot, before, item.record, values, incomingPersonality,
                NativeRefreshPlanner.fields(before.storedName, item.record.storedName, baselinePersonality, incomingPersonality, values))
        }
        val canonical = "native-refresh-v1:${draft.draftId}:${gson.toJson(items)}".toByteArray(Charsets.UTF_8)
        val fingerprint = MessageDigest.getInstance("SHA-256").digest(canonical).joinToString("") { "%02x".format(it.toInt() and 255) }
        return NativeRefreshReview(draft.draftId, fingerprint, items)
    }

    suspend fun commitRefresh(review: NativeRefreshReview, choices: Map<String, RefreshChoices>, identityConfirmed: Boolean = false): SnapshotAcquisitionResult = withContext(Dispatchers.IO) {
        require(!review.requiresIdentityConfirmation || identityConfirmed) { "Provisional identity continuity requires explicit review" }
        val saved = requireNotNull(previews.load(review.draftId))
        check(canCommit(saved.preview))
        require(choices.keys == review.items.map { it.incoming.recordKey }.toSet())
        sources.readCapturedFresh(saved.draft.sourceId).use { captured ->
        val fresh = captured.observation
        if (fresh.contentRevision != saved.draft.contentRevision || profiles.none { it.matches(fresh) &&
                it.matches(saved.draft.gameId, saved.draft.formatProfile, saved.draft.codecVersion) }) throw ImportSourceChangedException()
        db.withTransaction {
            val draft = saved.draft
            check(db.linkedGameSaveDao().get(draft.sourceId)?.documentKey == draft.documentKey)
            val keys = review.items.map { it.incoming.recordKey }
            require(keys.toSet() == saved.preview.items.map { it.record.recordKey }.toSet() && keys.distinct().size == keys.size)
            val existing = db.saveImportDraftDao().receipts(draft.sourceId, fresh.contentRevision, fresh.formatProfile, fresh.codecVersion)
                .filter { it.recordKey in keys }.associateBy { it.recordKey }
            if (existing.isNotEmpty()) {
                val processed = archive.snapshotsForRevision(review.items.map { it.binding.bindingId }, fresh.contentRevision)
                val matches = review.items.all { item -> processed.any { snapshot ->
                    snapshot.bindingId == item.binding.bindingId && snapshot.recordKey == item.incoming.recordKey &&
                        existing[item.incoming.recordKey]?.individualId == item.binding.individualId &&
                        runCatching { gson.fromJson(snapshot.reviewAuditJson, NativeRefreshAudit::class.java) }.getOrNull()?.let {
                            it.reviewFingerprint == review.fingerprint && it.choices == choices[item.incoming.recordKey]
                        } == true
                } }
                if (existing.keys.containsAll(keys) && matches) return@withTransaction SnapshotAcquisitionResult(keys.map { existing.getValue(it) }, 0)
                throw ImportPreviewInvalidException()
            }
            val current = refreshReviewLocked(saved, fresh)
            if (current != review) throw ImportPreviewInvalidException() // Native baseline or app-edited fields changed after review.
            val now = clock()
            val snapshotIds = mutableListOf<Long>()
            val resolved = current.items.associate { item -> item.incoming.recordKey to NativeRefreshPlanner.resolve(item.fields,
                choices.getValue(item.incoming.recordKey), item.incoming.storedName, item.incomingPersonality, item.appValues, now) }
            val receipts = current.items.map { item ->
                val key = item.incoming.recordKey
                val after = resolved.getValue(key)
                if (after.nickname != item.appValues.nickname) archive.updateNickname(item.binding.individualId, after.nickname)
                if (after.personality != item.appValues.personality) db.digimonIndividualDao().upsertPersonality(DigimonPersonalityTraits(
                    item.binding.individualId, DigimonPersonalityType.valueOf(after.personality), after.personalityUpdatedAt, after.personalitySystemVersion))
                val refreshedSnapshotId = archive.insertSnapshot(NativeGameSnapshot(individualId = item.binding.individualId, bindingId = item.binding.bindingId,
                    sourceLabel = draft.sourceLabel, contentRevision = fresh.contentRevision, codecVersion = fresh.codecVersion,
                    recordKey = key, recordJson = gson.toJson(item.incoming), nativePersonalityId = item.incoming.personalityId,
                    mappedPersonality = item.incomingPersonality, importedAt = now,
                    reviewAuditJson = gson.toJson(NativeRefreshAudit(review.fingerprint, item.previousSnapshot.id, draft.sourceId,
                        choices.getValue(key), item.appValues, after, identityContinuityConfirmed = identityConfirmed))))
                snapshotIds += refreshedSnapshotId
                NativeMappingContinuity.carried(db.nativeMaterializationDao().mapping(item.previousSnapshot.id),
                    item.incoming.nativeSpeciesId, refreshedSnapshotId)?.let { db.nativeMaterializationDao().saveMapping(it) }
                if (item.incoming != item.previousRecord) db.transferContinuityDao().pendingNative(PendingNativeProgress(
                    refreshedSnapshotId, item.binding.individualId, item.previousSnapshot.id, "GAME_PROGRESS_POLICY_REQUIRED", now))
                GameImportItemReceipt(UUID.randomUUID().toString(), draft.sourceId, fresh.contentRevision, fresh.formatProfile,
                    fresh.codecVersion, key, item.binding.individualId, now).also { db.saveImportDraftDao().insertReceipt(it) }
            }
            NativeContainerRepository(db).retain(captured, snapshotIds)
            SnapshotAcquisitionResult(receipts, 0, receipts.size)
        }
        }
    }

    companion object {
        /** Main-array snapshot copies only. This does not register a native writer or globally verified identity. */
        val productionProfiles: List<VerifiedSnapshotImportProfile> get() = listOf(VerifiedSnapshotImportProfile(
            TimeStrangerSaveReader.GAME_ID, TimeStrangerSaveReader.PROFILE, 1, "time-stranger-main-candidate-v1",
            DigimonPersonalityType.entries.associateBy { it.number }, nativeLevelRange = 1..99,
            provisionalIdentity = true, allowedSnapshotContainers = setOf(SaveContainer.PARTY, SaveContainer.BANK)),
            VerifiedSnapshotImportProfile(WorldDsSaveReader.GAME_ID, WorldDsSaveReader.PROFILE, 1, "world-ds-source-local-v1",
                emptyMap(), nativeLevelRange = 1..99, provisionalIdentity = true, allowedSnapshotContainers = setOf(SaveContainer.PARTY),
                appPersonalityOnly = true, crossSourceIdentityReview = false))
    }
}
