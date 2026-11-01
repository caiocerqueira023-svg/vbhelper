package com.tamerslab.transfer.data.gameSave

import androidx.room.withTransaction
import com.tamerslab.data.database.AppDatabase
import com.tamerslab.domain.device_data.DigimonIndividual
import com.tamerslab.domain.device_data.IndividualOrigin
import com.tamerslab.domain.personality.DigimonPersonalityTraits
import com.tamerslab.domain.transfer.*
import com.tamerslab.gamesave.*
import com.tamerslab.gamesave.NdsStoryProfiles
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.UUID

enum class WorldDsMoveBlocker { FORMAT, REVIEW, LAST_PARTY_MEMBER, COPY_DIVERGENCE, ONLY_IN_OLDER_COPY, DESTINATION_FULL, WRITE_ACCESS, STALE }
class WorldDsMoveRecoveryRequired : IllegalStateException("Native move requires recovery; retained backups are available")
data class ExperimentalWorldDsMoveResult(val individualId: String, val materialized: Boolean)
data class GameMoveBackup(val operationId: String, val sourceLabel: String?, val createdAt: Long)

/**
 * The newest valid copy (or an explicit user-reviewed copy) is the authority. Older copies may be
 * stale, carry different bytes, or omit the individual; they are repaired or cleared on write but
 * never decide whether the move is allowed. With no defensible authority, the legacy rule remains:
 * all populated copies that contain the identity must agree exactly.
 */
internal object WorldDsMoveCopyPolicy {
    fun resolve(observation: SaveObservation, record: ObservedDigimon): ObservedDigimon? {
        val identity = record.nativeIdentity ?: return null
        val authority = WorldDsCopySelection.authoritativeCopy(observation)
        if (authority != null) {
            return WorldDsCopySelection.recordsForCopy(observation, authority)
                .singleOrNull { it.nativeIdentity == identity && !it.nativeRecordBase64.isNullOrEmpty() }
        }
        return record.takeIf { consistent(observation, it) }
    }

    /** True only when the identity exists elsewhere but the authoritative copy no longer owns it. */
    fun onlyOutsideAuthority(observation: SaveObservation, record: ObservedDigimon): Boolean {
        val identity = record.nativeIdentity ?: return false
        val authority = WorldDsCopySelection.authoritativeCopy(observation) ?: return false
        return WorldDsCopySelection.recordsForCopy(observation, authority).none { it.nativeIdentity == identity } &&
            observation.records.any { it.nativeIdentity == identity }
    }

    /** Legacy all-copy agreement, used when no newest/reviewed copy can be determined. */
    fun consistent(observation: SaveObservation, record: ObservedDigimon): Boolean {
        val identity = record.nativeIdentity ?: return false
        val bytes = record.nativeRecordBase64 ?: return false
        val counterparts = observation.records.filter { it.nativeIdentity == identity }
        if (counterparts.isEmpty()) return false
        val base64 = counterparts.mapNotNull { it.nativeRecordBase64 }
        return counterparts.all { it.container == record.container } &&
            base64.size == counterparts.size && base64.distinct().size == 1 && base64.contains(bytes) &&
            counterparts.any { it.nativeParameters?.saveBlock == record.nativeParameters?.saveBlock }
    }
}

/**
 * Which records the storage move accepts: any party member or unreferenced storage record whose live
 * ownership is ordinary. Flags that only describe the source image (copy ambiguity, unusual values,
 * unmapped name glyphs) never block a move; the exact bytes are preserved either way.
 */
internal object WorldDsMoveRecordPolicy {
    private val imageOnlyIssues = setOf(RecordIssue.AMBIGUOUS_SAVE_BLOCK, RecordIssue.IMPLAUSIBLE_VALUES,
        RecordIssue.UNKNOWN_NAME_CODES)

    fun removable(record: ObservedDigimon): Boolean {
        if (record.nativeIdentity == null) return false
        val content = record.issues.filterNot { it in imageOnlyIssues }
        return when (record.container) {
            SaveContainer.PARTY -> content.isEmpty()
            SaveContainer.UNASSIGNED -> content.isNotEmpty() && content.all { it == RecordIssue.UNASSIGNED_LOCATION }
            else -> false
        }
    }

    /** Only the authoritative copy decides last-member safety; stale copies never mask or create a block. */
    fun keepsUsableRecord(observation: SaveObservation, record: ObservedDigimon): Boolean {
        val authority = WorldDsCopySelection.authoritativeCopy(observation)
        val scoped = if (authority != null) WorldDsCopySelection.recordsForCopy(observation, authority) else observation.records
        return keepsUsableRecord(scoped, record)
    }

    /** An admission needs a free visible party slot in the authoritative copy. */
    fun hasFreePartySlot(observation: SaveObservation, record: ObservedDigimon): Boolean {
        val authority = WorldDsCopySelection.authoritativeCopy(observation)
        val scoped = if (authority != null) WorldDsCopySelection.recordsForCopy(observation, authority) else observation.records
        return scoped.count { it.container == SaveContainer.PARTY } < WorldDsPartyEdits.PARTY_CAP
    }

    /** The game must keep one usable Digimon after a move; a party removal additionally keeps the party non-empty. */
    fun keepsUsableRecord(records: List<ObservedDigimon>, record: ObservedDigimon): Boolean {
        val usableParty = records.filter {
            it.container == SaveContainer.PARTY && it.issues.all { issue -> issue == RecordIssue.AMBIGUOUS_SAVE_BLOCK }
        }
        // Every populated save copy repeats each record; usable Digimon are counted by identity, never per copy.
        val party = usableParty.mapNotNull { it.nativeIdentity }.distinct().size
        if (record.container == SaveContainer.PARTY) return party > 1
        val storage = records.filter {
            it.container == SaveContainer.UNASSIGNED &&
                it.issues.all { issue -> issue == RecordIssue.AMBIGUOUS_SAVE_BLOCK || issue == RecordIssue.UNASSIGNED_LOCATION }
        }.mapNotNull { it.nativeIdentity }.distinct().size
        return party + storage > 1
    }

    /** Admission accepts exactly the storage records removal accepts; farm residency and live
     *  ownership flags stay writer guards that need their own semantics. */
    fun admissible(record: ObservedDigimon): Boolean =
        record.container == SaveContainer.UNASSIGNED && removable(record)
}

/**
 * User-requested experimental writer. Performs real source removal and exact native acquisition.
 * Structural/readback verification is implemented; retail game-load/checksum acceptance is unverified.
 */
class ExperimentalWorldDsMoveRepository(
    private val db: AppDatabase,
    private val sources: LinkedGameSaveRepository,
    private val writer: SaveDocumentWrite,
    journalDirectory: File,
    private val materialization: NativeMaterializationRepository? = null,
    private val presentations: NativeSpeciesPresentationRepository? = null,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val journal = ExperimentalEditJournal(journalDirectory, ExperimentalMoveRecord::class.java)
    private val admissions = ExperimentalEditJournal(File(journalDirectory, "admissions"), ExperimentalAdmitRecord::class.java)
    private val mutex = Mutex()
    private val gson = Gson()
    private val profile = GameImportAcquisitionRepository.productionProfiles.single {
        it.matches(NdsStoryProfiles.WORLD_DS.gameId, NdsStoryProfiles.WORLD_DS.formatProfile, NdsStoryProfiles.WORLD_DS.codecVersion)
    }

    suspend fun backups(): List<GameMoveBackup> = journal.records().sortedByDescending { it.createdAt }.map {
        GameMoveBackup(it.operationId, db.linkedGameSaveDao().get(it.sourceId)?.label, it.createdAt)
    }

    suspend fun backupBytes(operationId: String): ByteArray {
        val operation = requireNotNull(journal.records().singleOrNull { it.operationId == operationId })
        val bytes = journal.original(operationId)
        require(hash(bytes) == operation.sourceRevision)
        return bytes
    }

    fun blocker(view: LinkedSaveView, record: ObservedDigimon): WorldDsMoveBlocker? {
        val observation = view.observation ?: return WorldDsMoveBlocker.FORMAT
        if (!profile.matches(observation)) return WorldDsMoveBlocker.FORMAT
        if (view.source.lastError != null) return WorldDsMoveBlocker.STALE
        val resolved = WorldDsMoveCopyPolicy.resolve(observation, record)
            ?: return if (WorldDsMoveCopyPolicy.onlyOutsideAuthority(observation, record))
                WorldDsMoveBlocker.ONLY_IN_OLDER_COPY else WorldDsMoveBlocker.COPY_DIVERGENCE
        if (!WorldDsMoveRecordPolicy.removable(resolved)) return WorldDsMoveBlocker.REVIEW
        if (!WorldDsMoveRecordPolicy.keepsUsableRecord(observation, resolved)) return WorldDsMoveBlocker.LAST_PARTY_MEMBER
        if (!writer.canWrite(view.source.documentUri, view.source.grantUri)) return WorldDsMoveBlocker.WRITE_ACCESS
        return null
    }

    fun admissionBlocker(view: LinkedSaveView, record: ObservedDigimon): WorldDsMoveBlocker? {
        val observation = view.observation ?: return WorldDsMoveBlocker.FORMAT
        if (!profile.matches(observation)) return WorldDsMoveBlocker.FORMAT
        if (view.source.lastError != null) return WorldDsMoveBlocker.STALE
        val resolved = WorldDsMoveCopyPolicy.resolve(observation, record)
            ?: return if (WorldDsMoveCopyPolicy.onlyOutsideAuthority(observation, record))
                WorldDsMoveBlocker.ONLY_IN_OLDER_COPY else WorldDsMoveBlocker.COPY_DIVERGENCE
        if (!WorldDsMoveRecordPolicy.admissible(resolved)) return WorldDsMoveBlocker.REVIEW
        if (!WorldDsMoveRecordPolicy.hasFreePartySlot(observation, resolved)) return WorldDsMoveBlocker.DESTINATION_FULL
        if (!writer.canWrite(view.source.documentUri, view.source.grantUri)) return WorldDsMoveBlocker.WRITE_ACCESS
        return null
    }

    /** Admits an unreferenced storage record to the party in place: same durable journal choreography
     *  as a move, but no vault acquisition, snapshot or materialization — the save keeps the record. */
    suspend fun admit(sourceId: String, expectedRevision: String, recordKey: String) =
        withContext(Dispatchers.IO) { mutex.withLock { sources.withMoveSourceLock(sourceId) { capture ->
            reconcileAdmissions(sourceId, capture)
            capture().use { captured ->
                val observed = captured.observation
                if (observed.contentRevision != expectedRevision) throw ImportSourceChangedException()
                val source = requireNotNull(db.linkedGameSaveDao().get(sourceId))
                val requested = requireNotNull(observed.records.singleOrNull { it.recordKey == recordKey })
                val record = requireNotNull(WorldDsMoveCopyPolicy.resolve(observed, requested)) { "The authoritative copy does not contain this record" }
                require(admissionBlocker(LinkedSaveView(source, observed), record) == null) { "Native admission is unavailable for this record" }
                val original = requireNotNull(captured.container).copyOf()
                val admission = WorldDsPartyEdits.admitStorageRecordToParty(original, observed.contentRevision, record.recordKey)
                val candidate = admission.candidateBytes
                try {
                    var operation = ExperimentalAdmitRecord(UUID.randomUUID().toString(), sourceId, source.documentKey,
                        observed.contentRevision, admission.candidateRevision, observed, record, clock())
                    admissions.prepare(operation, original, candidate)
                    val backup = admissions.original(operation.operationId)
                    try { check(hash(backup) == operation.sourceRevision) } finally { backup.fill(0) }
                    operation = operation.copy(phase = "BACKED_UP"); admissions.update(operation)
                    // Fresh provider read immediately before truncation/write, while relinks and other app moves are locked.
                    capture().use { check(it.observation.contentRevision == operation.sourceRevision) { "Source changed before write" } }
                    operation = operation.copy(phase = "WRITING"); admissions.update(operation)
                    try {
                        writer.write(source.documentUri, candidate)
                        capture().use { reread ->
                            check(reread.observation.contentRevision == operation.candidateRevision)
                            check(requireNotNull(reread.container).contentEquals(candidate))
                            check(requireNotNull(reread.observation.records.singleOrNull { it.recordKey == recordKey }).container == SaveContainer.PARTY)
                        }
                        operation = operation.copy(phase = "ACKNOWLEDGED"); admissions.update(operation)
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) {
                        admissions.update(operation.copy(phase = "RECOVERY_REQUIRED"))
                        throw WorldDsMoveRecoveryRequired()
                    }
                } finally { original.fill(0); candidate.fill(0) }
            }
        } } }

    suspend fun move(sourceId: String, expectedRevision: String, recordKey: String): ExperimentalWorldDsMoveResult =
        withContext(Dispatchers.IO) { mutex.withLock { sources.withMoveSourceLock(sourceId) { capture ->
            reconcile(sourceId, capture)
            capture().use { captured ->
                val observed = captured.observation
                if (observed.contentRevision != expectedRevision) throw ImportSourceChangedException()
                val source = requireNotNull(db.linkedGameSaveDao().get(sourceId))
                val requested = requireNotNull(observed.records.singleOrNull { it.recordKey == recordKey })
                val record = requireNotNull(WorldDsMoveCopyPolicy.resolve(observed, requested)) { "The authoritative copy does not contain this record" }
                require(blocker(LinkedSaveView(source, observed), record) == null) { "Native move is unavailable for this record" }
                val original = requireNotNull(captured.container).copyOf()
                val removal = WorldDsPartyEdits.removeForResearch(original, observed.contentRevision, record.recordKey)
                val candidate = removal.candidateBytes
                try {
                    val bindings = db.nativeGameArchiveDao().bindings(sourceId, source.documentKey, observed.gameId,
                        observed.formatProfile, profile.identityScheme, listOf(profile.nativeIdentity(record)))
                    require(bindings.size <= 1)
                    val binding = bindings.singleOrNull()
                    if (binding != null) {
                        // A matching presence is the return journey; any other away state blocks the move.
                        if (db.nativeMaterializationDao().isAway(binding.individualId)) {
                            val presence = db.nativeMaterializationDao().presence(binding.individualId)
                            require(presence != null && (presence.sourceId == sourceId || presence.documentKey == source.documentKey))
                        }
                        // The individual's in-game state may legitimately change between acquisitions; acquire()
                        // keeps a fresh snapshot for this exact source revision instead of rejecting the move.
                    }
                    val presented = presentations?.sources(LinkedSaveView(source, observed))?.get(record.recordKey)
                    var operation = ExperimentalMoveRecord(UUID.randomUUID().toString(), sourceId, source.documentKey,
                        observed.contentRevision, removal.candidateRevision, observed, record,
                        binding?.individualId ?: UUID.randomUUID().toString(), binding?.bindingId ?: UUID.randomUUID().toString(),
                        presented?.species?.id, clock(), presented?.authority)
                    journal.prepare(operation, original, candidate)
                    val backup = journal.original(operation.operationId)
                    try { check(hash(backup) == operation.sourceRevision) } finally { backup.fill(0) }
                    operation = operation.copy(phase = "BACKED_UP"); journal.update(operation)
                    // Fresh provider read immediately before truncation/write, while relinks and other app moves are locked.
                    capture().use { check(it.observation.contentRevision == operation.sourceRevision) { "Source changed before write" } }
                    operation = operation.copy(phase = "WRITING"); journal.update(operation)
                    try {
                        writer.write(source.documentUri, candidate)
                        capture().use { reread ->
                            check(reread.observation.contentRevision == operation.candidateRevision)
                            check(requireNotNull(reread.container).contentEquals(candidate))
                            check(reread.observation.records.none { it.nativeIdentity == record.nativeIdentity })
                        }
                        operation = operation.copy(phase = "SOURCE_VERIFIED"); journal.update(operation)
                        val snapshot = acquire(operation, original)
                        operation = operation.copy(phase = "ACKNOWLEDGED"); journal.update(operation)
                        ExperimentalWorldDsMoveResult(operation.individualId,
                            materialize(snapshot, operation.canonicalSpeciesId, operation.canonicalAuthority))
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) {
                        journal.update(operation.copy(phase = "RECOVERY_REQUIRED"))
                        throw WorldDsMoveRecoveryRequired()
                    }
                } finally { original.fill(0); candidate.fill(0) }
            }
        } } }

    /** Resume acquisition only after exact candidate persistence; never overwrite a newer external save. */
    suspend fun recover(sourceId: String) = withContext(Dispatchers.IO) {
        mutex.withLock { sources.withMoveSourceLock(sourceId) { capture ->
            reconcile(sourceId, capture)
            reconcileAdmissions(sourceId, capture)
        } }
    }

    /** An admission holds no vault artifact: the save on disk is the only truth. Interrupted
     *  operations are verified when the candidate landed, then abandoned either way. */
    private suspend fun reconcileAdmissions(sourceId: String, capture: suspend () -> CapturedSave) {
        for (operation in admissions.pending(sourceId)) {
            if (operation.phase == "RECOVERY_REQUIRED" &&
                db.linkedGameSaveDao().get(sourceId)?.documentKey == operation.documentKey) {
                val revision = capture().use { it.observation.contentRevision }
                if (revision == operation.candidateRevision) {
                    val original = admissions.original(operation.operationId)
                    val candidate = admissions.candidate(operation.operationId)
                    try {
                        check(hash(original) == operation.sourceRevision && hash(candidate) == operation.candidateRevision)
                        val rebuilt = WorldDsPartyEdits.admitStorageRecordToParty(original, operation.sourceRevision, operation.record.recordKey)
                        check(rebuilt.candidateBytes.contentEquals(candidate))
                    } finally { original.fill(0); candidate.fill(0) }
                }
            }
            admissions.update(operation.copy(phase = "ABORTED"))
        }
    }

    private suspend fun reconcile(sourceId: String, capture: suspend () -> CapturedSave) {
        for (operation in journal.pending(sourceId)) {
            if (operation.phase in setOf("PREPARED", "BACKED_UP")) {
                journal.update(operation.copy(phase = "ABORTED"))
                continue // No external write can precede the durable WRITING checkpoint.
            }
            val source = requireNotNull(db.linkedGameSaveDao().get(sourceId))
            if (source.documentKey != operation.documentKey) throw WorldDsMoveRecoveryRequired()
            val revision = capture().use { it.observation.contentRevision }
            when (revision) {
                operation.sourceRevision -> journal.update(operation.copy(phase = "ABORTED"))
                operation.candidateRevision -> {
                    val original = journal.original(operation.operationId)
                    val candidate = journal.candidate(operation.operationId)
                    try {
                        check(hash(original) == operation.sourceRevision && hash(candidate) == operation.candidateRevision)
                        val rebuilt = WorldDsPartyEdits.removeForResearch(original, operation.sourceRevision, operation.record.recordKey)
                        check(rebuilt.candidateBytes.contentEquals(candidate))
                        val snapshot = acquire(operation, original)
                        journal.update(operation.copy(phase = "ACKNOWLEDGED"))
                        materialize(snapshot, operation.canonicalSpeciesId, operation.canonicalAuthority)
                    } finally { original.fill(0); candidate.fill(0) }
                }
                else -> throw WorldDsMoveRecoveryRequired()
            }
        }
    }

    private suspend fun acquire(operation: ExperimentalMoveRecord, original: ByteArray): Long = db.withTransaction {
        val decoded = WorldDsSaveReader().read(original)
        check(profile.matches(decoded) && decoded.contentRevision == operation.sourceRevision)
        // The journaled record was reviewed against a selected copy; the raw image may still flag its
        // blocks as ambiguous. Only the record's own content must match, never its review state.
        val decodedRecord = decoded.records.singleOrNull { it.recordKey == operation.record.recordKey }
        check(decodedRecord != null && decodedRecord.nativeRecordBase64 == operation.record.nativeRecordBase64 &&
            decodedRecord.nativeIdentity == operation.record.nativeIdentity &&
            decodedRecord.container == operation.record.container && decodedRecord.position == operation.record.position)
        val dao = db.nativeGameArchiveDao()
        val record = operation.record
        var binding = dao.binding(operation.bindingId)
        if (binding == null) {
            check(db.digimonIndividualDao().getIndividual(operation.individualId) == null)
            check(db.digimonIndividualDao().insert(DigimonIndividual(operation.individualId, operation.createdAt,
                nickname = record.storedName.takeIf(String::isNotEmpty), originKind = IndividualOrigin.IMPORTED)) != -1L)
            val personality = profile.initialAppPersonality(record, operation.individualId)
            db.digimonIndividualDao().upsertPersonality(DigimonPersonalityTraits(operation.individualId, personality, operation.createdAt))
            binding = ExternalIndividualBinding(operation.bindingId, operation.sourceId, operation.documentKey, operation.observation.gameId,
                operation.observation.formatProfile, profile.identityScheme, profile.nativeIdentity(record), operation.individualId,
                operation.createdAt, "PROVISIONAL")
            dao.insertBinding(binding)
        }
        check(binding.individualId == operation.individualId && binding.nativeIdentity == profile.nativeIdentity(record) &&
            binding.gameId == operation.observation.gameId && binding.formatProfile == operation.observation.formatProfile &&
            (binding.sourceId == operation.sourceId || binding.documentKey == operation.documentKey))
        val individual = requireNotNull(db.digimonIndividualDao().getIndividual(operation.individualId))
        if (individual.nickname == null && record.storedName.isNotEmpty()) dao.updateNickname(operation.individualId, record.storedName)
        val latest = dao.latestSnapshots(listOf(binding.bindingId)).singleOrNull()
        val snapshotId = if (latest?.contentRevision == operation.sourceRevision && latest.recordKey == record.recordKey) latest.id else {
            val personality = requireNotNull(db.digimonIndividualDao().getPersonality(operation.individualId))
            dao.insertSnapshot(NativeGameSnapshot(individualId = operation.individualId, bindingId = binding.bindingId,
                sourceLabel = requireNotNull(db.linkedGameSaveDao().get(operation.sourceId)).label, contentRevision = operation.sourceRevision,
                codecVersion = operation.observation.codecVersion, recordKey = record.recordKey, recordJson = gson.toJson(record),
                nativePersonalityId = record.personalityId, mappedPersonality = personality.personalityType.name, importedAt = operation.createdAt))
        }
        val receipts = db.saveImportDraftDao().receipts(operation.sourceId, operation.sourceRevision,
            operation.observation.formatProfile, operation.observation.codecVersion)
        val receipt = receipts.singleOrNull { it.recordKey == record.recordKey }
        if (receipt == null) db.saveImportDraftDao().insertReceipt(GameImportItemReceipt(UUID.randomUUID().toString(), operation.sourceId,
            operation.sourceRevision, operation.observation.formatProfile, operation.observation.codecVersion, record.recordKey,
            operation.individualId, operation.createdAt))
        else check(receipt.individualId == operation.individualId)
        if (db.transferContinuityDao().containerForSnapshot(snapshotId) == null)
            NativeContainerRepository(db).retain(CapturedSave(operation.observation, original), listOf(snapshotId))
        // The exact native record is verified back inside Storage; the endpoint no longer holds it.
        db.nativeMaterializationDao().clearPresence(operation.individualId)
        snapshotId
    }

    private suspend fun materialize(snapshotId: Long, canonicalId: String?, authority: String?): Boolean {
        val repository = materialization ?: return false
        repository.applyWorldDsReturn(snapshotId, canonicalId, authority ?: "GAME_TABLE")
        if (canonicalId == null) return false
        return try {
            val mapping = repository.mapping(snapshotId) ?: repository.saveMapping(snapshotId, canonicalId,
                authority ?: "USER_CONFIRMED")
            when (repository.readiness(mapping)) {
                NativeMaterializationReadiness.READY -> { repository.materialize(mapping); true }
                NativeMaterializationReadiness.ALREADY_MATERIALIZED -> true
                else -> false
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { false } // Exact native acquisition is already durable even if appearance is unavailable.
    }

    private fun hash(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }
}

