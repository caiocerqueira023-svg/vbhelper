package com.github.nacabaro.vbhelper.source.gameSave

import androidx.room.withTransaction
import com.github.nacabaro.vbhelper.database.AppDatabase
import com.github.nacabaro.vbhelper.domain.transfer.ExternalIndividualBinding
import com.github.nacabaro.vbhelper.domain.transfer.GameEndpointPresence
import com.github.nacabaro.vbhelper.domain.transfer.NativeAppProjection
import com.github.nacabaro.vbhelper.domain.transfer.NativeCanonicalMapping
import com.github.nacabaro.vbhelper.domain.transfer.NativeGameSnapshot
import com.github.nacabaro.vbhelper.gamesave.*
import com.github.nacabaro.vbhelper.species.CanonicalSpeciesCatalog
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import android.util.Log

enum class WorldDsSendBlocker { SNAPSHOT_UNAVAILABLE, CONVERSION_UNAVAILABLE, ALREADY_AWAY, FORMAT, REVIEW, STALE, WRITE_ACCESS, IDENTITY_TAKEN, DESTINATION_FULL }
class WorldDsSendRecoveryRequired : IllegalStateException("Native send requires recovery; retained backups are available")
data class ExperimentalWorldDsSendResult(val individualId: String, val destinationSourceId: String)

private class PreparedSend(val candidateBytes: ByteArray, val candidateRevision: String,
    val recordKey: String, val nativeIdentity: String)

/**
 * Which retained snapshots can be sent back into a game: any exact native record that still carries
 * its bytes and identity, whether it was captured from the party or from the game's storage side.
 */
internal object WorldDsSendPolicy {
    /** Flags that describe the reviewed image, not live ownership of the individual. */
    private val imageIssues = setOf(
        RecordIssue.AMBIGUOUS_SAVE_BLOCK,
        RecordIssue.IMPLAUSIBLE_VALUES,
        RecordIssue.UNKNOWN_NAME_CODES,
    )

    fun nativeReturnable(record: ObservedDigimon): Boolean =
        record.nativeRecordBase64 != null && record.nativeIdentity != null &&
            record.container in setOf(SaveContainer.PARTY, SaveContainer.UNASSIGNED) &&
            record.issues.all { it in imageIssues || it == RecordIssue.UNASSIGNED_LOCATION }
}

/**
 * User-requested experimental return of one exact retained native record into a World DS save.
 * Structural/readback verification is implemented; retail game-load/checksum acceptance is unverified.
 */
class ExperimentalWorldDsSendRepository(
    private val db: AppDatabase,
    private val sources: LinkedGameSaveRepository,
    private val writer: SaveDocumentWrite,
    journalDirectory: File,
    private val speciesTable: WorldDsSpeciesTable? = null,
    private val catalogProvider: suspend () -> CanonicalSpeciesCatalog? = { null },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val journal = ExperimentalEditJournal(journalDirectory, ExperimentalSendRecord::class.java)
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

    /**
     * A retained record can return as long as its exact bytes and identity are present; the review
     * flags describe the source image's copy layout, never the record's content, and the writer
     * revalidates the bytes before insertion.
     */
    suspend fun retainedRecord(individualId: String): ObservedDigimon? {
        val snapshotId = db.nativeMaterializationDao().latestSnapshotId(individualId) ?: return null
        val snapshot = db.nativeMaterializationDao().snapshot(snapshotId) ?: return null
        val binding = db.nativeMaterializationDao().binding(snapshot.bindingId) ?: return null
        if (!profile.matches(binding.gameId, binding.formatProfile, snapshot.codecVersion)
            || binding.identityScheme != profile.identityScheme) return null
        val record = runCatching { gson.fromJson(snapshot.recordJson, ObservedDigimon::class.java) }.getOrNull() ?: return null
        return record.takeIf(WorldDsSendPolicy::nativeReturnable)
    }

    /** The destination must have room: a non-full visible party and a free record slot in every populated copy. */
    private fun destinationFull(observation: SaveObservation): Boolean {
        val blocks = observation.records.mapNotNull { it.nativeParameters?.saveBlock }.distinct()
        if (blocks.isEmpty()) return true
        return blocks.any { block ->
            val records = observation.records.filter { it.nativeParameters?.saveBlock == block }
            records.count { it.container == SaveContainer.PARTY } >= WorldDsPartyEdits.PARTY_CAP || records.size >= 40
        }
    }

    /** A defective retained record may be sent again so the same endpoint record is repaired in place. */
    suspend fun awayBlocker(individualId: String): WorldDsSendBlocker? =
        if (db.nativeMaterializationDao().isAway(individualId) &&
            !needsSpeciesRepair(retainedRecord(individualId))) WorldDsSendBlocker.ALREADY_AWAY else null

    /**
     * The record this send would write: the exact retained bytes when the app partner still matches
     * its last native checkpoint (or has no app representation yet), otherwise a synthesized record
     * projected from current app state with the retained bytes kept as a seed for untouched fields.
     */
    suspend fun outbound(individualId: String): ObservedDigimon? {
        val retained = retainedRecord(individualId)
        val state = appOutboundState(individualId) ?: return retained
        val projection = db.transferContinuityDao().projection(individualId)
        // Existing records written before species defaults were generated stay unusable until the
        // record itself is regenerated; matchBaseline alone would keep returning the broken bytes.
        if (retained != null && !needsSpeciesRepair(retained) && projection != null &&
            WorldDsOutboundConversion.matchesBaseline(state, projection) &&
            WorldDsOutboundConversion.retainedCoversAppLevel(state, retained)) return retained
        val speciesId = retained?.nativeSpeciesId ?: state.dsSpeciesId
        val defaults = speciesTable?.defaults(speciesId)
        val lineage = speciesTable?.lineage(speciesId).orEmpty()
        return WorldDsOutboundConversion.generate(state, retained?.nativeRecordBase64, defaults, lineage)
    }

    private fun needsSpeciesRepair(record: ObservedDigimon?): Boolean {
        val encoded = record?.nativeRecordBase64 ?: return false
        return runCatching { WorldDsRecordCodec.needsSpeciesRepair(WorldDsRecordCodec.decodeBase64(encoded)) }
            .getOrDefault(false)
    }

    /** Why [outbound] is null: no app representation for the individual, or a species this game cannot name. */
    suspend fun outboundBlocker(individualId: String): WorldDsSendBlocker? {
        if (outbound(individualId) != null) return null
        val table = speciesTable ?: return WorldDsSendBlocker.CONVERSION_UNAVAILABLE
        val catalog = runCatching { catalogProvider() }.getOrNull() ?: return WorldDsSendBlocker.CONVERSION_UNAVAILABLE
        val individual = db.digimonIndividualDao().getIndividual(individualId) ?: return WorldDsSendBlocker.SNAPSHOT_UNAVAILABLE
        val reading = WorldDsAppStatsReader.read(db, individualId)
        val canonical = listOfNotNull(
            individual.speciesName?.trim()?.takeIf { it.isNotEmpty() },
            reading?.speciesId?.trim()?.takeIf { it.isNotEmpty() },
            individual.nickname?.trim()?.takeIf { it.isNotEmpty() },
        ).firstNotNullOfOrNull { catalog.resolve(it) }
        if (canonical == null || WorldDsSpeciesCrosswalk.dsIdsByCanonical(table, catalog)[canonical.id] == null)
            return WorldDsSendBlocker.CONVERSION_UNAVAILABLE
        return WorldDsSendBlocker.SNAPSHOT_UNAVAILABLE
    }

    private suspend fun appOutboundState(individualId: String): WorldDsOutboundState? {
        val table = speciesTable ?: return null
        val catalog = catalogProvider() ?: return null
        val individual = db.digimonIndividualDao().getIndividual(individualId) ?: return null
        val reading = WorldDsAppStatsReader.read(db, individualId) ?: return null
        // Watch-imported partners often have no individual species name yet; fall back to the card's species.
        val canonical = listOfNotNull(
            individual.speciesName?.trim()?.takeIf { it.isNotEmpty() },
            reading.speciesId?.trim()?.takeIf { it.isNotEmpty() },
            individual.nickname?.trim()?.takeIf { it.isNotEmpty() },
        ).firstNotNullOfOrNull { catalog.resolve(it) } ?: return null
        val dsSpeciesId = WorldDsSpeciesCrosswalk.dsIdsByCanonical(table, catalog)[canonical.id] ?: return null
        val fallback = table.name(dsSpeciesId) ?: return null
        val displayName = WorldDsOutboundConversion.recordName(individual.nickname, fallback)
        return WorldDsOutboundState(dsSpeciesId, canonical.id, displayName, fallback,
            reading.stage, reading.baseHp, reading.baseBp, reading.baseAp, reading.appStats)
    }

    suspend fun blocker(view: LinkedSaveView, individualId: String, retained: ObservedDigimon): WorldDsSendBlocker? {
        val observation = view.observation ?: return WorldDsSendBlocker.FORMAT
        if (!profile.matches(observation)) return WorldDsSendBlocker.FORMAT
        if (view.source.lastError != null) return WorldDsSendBlocker.STALE
        if (!writer.canWrite(view.source.documentUri, view.source.grantUri)) return WorldDsSendBlocker.WRITE_ACCESS
        // A synthesized record has no native identity yet; the destination assigns a fresh one on write.
        val identity = retained.nativeIdentity ?: run {
            if (destinationFull(observation)) return WorldDsSendBlocker.DESTINATION_FULL
            return null
        }
        val onDisk = observation.records.filter { it.nativeIdentity == identity }
        if (onDisk.isNotEmpty()) {
            // An existing identity may only be rewritten when every copy of it is one of the broken
            // pre-defaults records and the archive binding proves it belongs to this individual.
            val bindings = db.nativeGameArchiveDao().bindings(view.source.sourceId, view.source.documentKey,
                observation.gameId, observation.formatProfile, profile.identityScheme, listOf(identity))
            if (bindings.any { it.individualId != individualId }) return WorldDsSendBlocker.REVIEW
            val populated = observation.records.mapNotNull { it.nativeParameters?.saveBlock }.distinct()
            if (bindings.isEmpty() || !populated.all { block -> onDisk.any { it.nativeParameters?.saveBlock == block } } ||
                !onDisk.all { needsSpeciesRepair(it) }) return WorldDsSendBlocker.IDENTITY_TAKEN
            return null
        }
        if (destinationFull(observation)) return WorldDsSendBlocker.DESTINATION_FULL
        val identities = listOf(profile.nativeIdentity(retained))
        val existing = db.nativeGameArchiveDao().bindings(view.source.sourceId, view.source.documentKey, observation.gameId,
            observation.formatProfile, profile.identityScheme, identities)
        if (existing.any { it.individualId != individualId }) return WorldDsSendBlocker.REVIEW
        return null
    }

    /** Exact native insertion after a durable journal/backup, with a fresh readback verification. */
    suspend fun send(individualId: String, sourceId: String, expectedRevision: String): ExperimentalWorldDsSendResult =
        withContext(Dispatchers.IO) { mutex.withLock { sources.withMoveSourceLock(sourceId) { capture ->
            reconcile(sourceId, capture)
            val source = requireNotNull(db.linkedGameSaveDao().get(sourceId))
            // A finalized pending operation already committed presence; a retry is success. A broken
            // retained record stays sendable so the same identity can be repaired in place.
            val presence = db.nativeMaterializationDao().presence(individualId)
            if (presence != null) {
                require(presence.sourceId == sourceId || presence.documentKey == source.documentKey) {
                    "The individual is away in another game"
                }
                if (!needsSpeciesRepair(retainedRecord(individualId))) {
                    return@withMoveSourceLock ExperimentalWorldDsSendResult(individualId, sourceId)
                }
            }
            val outbound = requireNotNull(outbound(individualId)) { "No native record is available for sending" }
            val recordBase64 = requireNotNull(outbound.nativeRecordBase64)
            val generated = outbound.nativeIdentity == null
            capture().use { captured ->
                val observed = captured.observation
                if (observed.contentRevision != expectedRevision) throw ImportSourceChangedException()
                require(blocker(LinkedSaveView(source, observed), individualId, outbound) == null) {
                    "Native send is unavailable for this destination"
                }
                val original = requireNotNull(captured.container).copyOf()
                val replacing = !generated && observed.records.any { it.nativeIdentity == outbound.nativeIdentity }
                val edit = when {
                    generated -> WorldDsPartyEdits.insertGeneratedRecord(original, observed.contentRevision, recordBase64)
                        .let { PreparedSend(it.candidateBytes, it.candidateRevision, it.recordKey, it.nativeIdentity) }
                    replacing -> WorldDsPartyEdits.replaceRetainedRecord(original, observed.contentRevision, recordBase64)
                        .let { PreparedSend(it.candidateBytes, it.candidateRevision, it.recordKey, it.nativeIdentity) }
                    else -> WorldDsPartyEdits.insertRetainedRecord(original, observed.contentRevision, recordBase64)
                        .let { PreparedSend(it.candidateBytes, it.candidateRevision, it.recordKey, it.nativeIdentity) }
                }
                val candidate = edit.candidateBytes
                try {
                    var operation = ExperimentalSendRecord(UUID.randomUUID().toString(), sourceId, source.documentKey,
                        observed.contentRevision, edit.candidateRevision, observed,
                        outbound.copy(recordKey = edit.recordKey, nativeIdentity = edit.nativeIdentity),
                        individualId, UUID.randomUUID().toString(), null, clock(), generated, replacing)
                    journal.prepare(operation, original, candidate)
                    val backup = journal.original(operation.operationId)
                    try { check(hash(backup) == operation.sourceRevision) } finally { backup.fill(0) }
                    operation = operation.copy(phase = "BACKED_UP"); journal.update(operation)
                    // Fresh provider read immediately before the write, while relinks and other app moves are locked.
                    capture().use { check(it.observation.contentRevision == operation.sourceRevision) { "Destination changed before write" } }
                    operation = operation.copy(phase = "WRITING"); journal.update(operation)
                    try {
                        writer.write(source.documentUri, candidate)
                        capture().use { reread ->
                            check(reread.observation.contentRevision == operation.candidateRevision)
                            check(requireNotNull(reread.container).contentEquals(candidate))
                            check(reread.observation.records.any { it.nativeIdentity == operation.record.nativeIdentity })
                        }
                        operation = operation.copy(phase = "DESTINATION_VERIFIED"); journal.update(operation)
                        commit(operation, original, candidate)
                        journal.update(operation.copy(phase = "ACKNOWLEDGED"))
                        ExperimentalWorldDsSendResult(individualId, sourceId)
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) {
                        Log.e("WorldDsSend", "write or commit failed", failure)
                        journal.update(operation.copy(phase = "RECOVERY_REQUIRED"))
                        throw WorldDsSendRecoveryRequired()
                    }
                } finally { original.fill(0); candidate.fill(0) }
            }
        } } }

    /** Resume finalization only after exact candidate persistence; never overwrite a newer external save. */
    suspend fun recover(sourceId: String) = withContext(Dispatchers.IO) {
        mutex.withLock { sources.withMoveSourceLock(sourceId) { capture -> reconcile(sourceId, capture) } }
    }

    private suspend fun reconcile(sourceId: String, capture: suspend () -> CapturedSave) {
        for (operation in journal.pending(sourceId)) {
            if (operation.phase in setOf("PREPARED", "BACKED_UP")) {
                journal.update(operation.copy(phase = "ABORTED"))
                continue // No external write can precede the durable WRITING checkpoint.
            }
            val source = requireNotNull(db.linkedGameSaveDao().get(sourceId))
            if (source.documentKey != operation.documentKey) throw WorldDsSendRecoveryRequired()
            val revision = capture().use { it.observation.contentRevision }
            when (revision) {
                operation.sourceRevision -> journal.update(operation.copy(phase = "ABORTED"))
                operation.candidateRevision -> {
                    val original = journal.original(operation.operationId)
                    val candidate = journal.candidate(operation.operationId)
                    try {
                        check(hash(original) == operation.sourceRevision && hash(candidate) == operation.candidateRevision)
                        val recordBytes = requireNotNull(operation.record.nativeRecordBase64)
                        val rebuiltBytes = when {
                            operation.generated -> WorldDsPartyEdits.insertGeneratedRecord(original, operation.sourceRevision, recordBytes).candidateBytes
                            operation.replaced -> WorldDsPartyEdits.replaceRetainedRecord(original, operation.sourceRevision, recordBytes).candidateBytes
                            else -> WorldDsPartyEdits.insertRetainedRecord(original, operation.sourceRevision, recordBytes).candidateBytes
                        }
                        check(rebuiltBytes.contentEquals(candidate))
                        commit(operation, original, candidate)
                        journal.update(operation.copy(phase = "ACKNOWLEDGED"))
                    } finally { original.fill(0); candidate.fill(0) }
                }
                else -> throw WorldDsSendRecoveryRequired()
            }
        }
    }

    /** Binding + destination snapshot + away presence are one durable transaction. */
    private suspend fun commit(operation: ExperimentalSendRecord, original: ByteArray, candidateBytes: ByteArray) = db.withTransaction {
        val decoded = WorldDsSaveReader().read(original).let { pre ->
            check(profile.matches(pre) && pre.contentRevision == operation.sourceRevision)
            pre
        }
        val sentIdentity = requireNotNull(operation.record.nativeIdentity)
        if (operation.replaced) {
            check(decoded.records.any { it.nativeIdentity == sentIdentity }) {
                "A repair requires the original destination to own the identity"
            }
        } else {
            check(decoded.records.none { it.nativeIdentity == sentIdentity })
        }
        val candidate = WorldDsSaveReader().read(candidateBytes)
        check(candidate.contentRevision == operation.candidateRevision)
        // Every populated copy received the exact record; a deterministic copy represents the insertion
        // or the repaired record, which keeps its original party/storage container.
        val insertedOnDisk = candidate.records.filter { it.nativeIdentity == sentIdentity &&
            (operation.replaced || it.container == SaveContainer.PARTY) }
            .minByOrNull { requireNotNull(it.nativeParameters).saveBlock }
            ?: throw WorldDsSendRecoveryRequired()
        check(WorldDsSendPolicy.nativeReturnable(insertedOnDisk))
        // The stored snapshot keeps the record itself; the storage review state is never carried over.
        val inserted = insertedOnDisk.copy(membership = RecordMembership.ACTIVE_RECORD, issues = emptyList())
        check(db.digimonIndividualDao().getIndividual(operation.individualId) != null) { "The sent individual no longer exists" }
        val dao = db.nativeGameArchiveDao()
        val identity = profile.nativeIdentity(inserted)
        val recorded = dao.bindings(operation.sourceId, operation.documentKey, operation.observation.gameId,
            operation.observation.formatProfile, profile.identityScheme, listOf(identity))
        check(recorded.size <= 1)
        val binding = recorded.singleOrNull() ?: ExternalIndividualBinding(operation.bindingId, operation.sourceId,
            operation.documentKey, operation.observation.gameId, operation.observation.formatProfile,
            profile.identityScheme, identity, operation.individualId, operation.createdAt, "PROVISIONAL").also {
            dao.insertBinding(it)
        }
        check(binding.individualId == operation.individualId && binding.nativeIdentity == identity)
        val latest = dao.latestSnapshots(listOf(binding.bindingId)).singleOrNull()
        val snapshotId = if (latest?.contentRevision == operation.candidateRevision && latest.recordKey == inserted.recordKey) latest.id
        else {
            val personality = requireNotNull(db.digimonIndividualDao().getPersonality(operation.individualId)) {
                "The sent individual has no app personality"
            }
            dao.insertSnapshot(NativeGameSnapshot(individualId = operation.individualId, bindingId = binding.bindingId,
                sourceLabel = requireNotNull(db.linkedGameSaveDao().get(operation.sourceId)).label,
                contentRevision = operation.candidateRevision, codecVersion = operation.observation.codecVersion,
                recordKey = inserted.recordKey, recordJson = gson.toJson(inserted), nativePersonalityId = inserted.personalityId,
                mappedPersonality = personality.personalityType.name, importedAt = operation.createdAt))
        }
        if (db.transferContinuityDao().containerForSnapshot(snapshotId) == null)
            NativeContainerRepository(db).retain(CapturedSave(operation.observation, original), listOf(snapshotId))
        appOutboundState(operation.individualId)?.let { state ->
            if (db.nativeMaterializationDao().mapping(snapshotId) == null)
                db.nativeMaterializationDao().saveMapping(NativeCanonicalMapping(snapshotId, inserted.nativeSpeciesId, state.canonicalSpeciesId,
                    "GAME_TABLE", operation.createdAt))
            db.transferContinuityDao().saveProjection(NativeAppProjection(operation.individualId, snapshotId,
                WorldDsStatConversion.VERSION, "APP_SENT_VALUES", state.stage, state.baseHp, state.baseBp, state.baseAp,
                state.appStats.trainingHp, state.appStats.trainingAp, state.appStats.trainingBp, state.appStats.vitalPoints,
                state.appStats.wins, state.appStats.losses, db.transferContinuityDao().lastDelta(operation.individualId),
                operation.createdAt, db.transferContinuityDao().projection(operation.individualId)?.originBasisPoints ?: 10000))
        }
        db.nativeMaterializationDao().savePresence(GameEndpointPresence(operation.individualId, operation.sourceId,
            operation.documentKey, operation.observation.gameId, identity, operation.operationId, operation.createdAt))
        snapshotId
    }

    private fun hash(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }
}
