package com.tamerslab.transfer.data.gameSave

import com.tamerslab.domain.transfer.StoryProgressDomain
import androidx.room.withTransaction
import com.tamerslab.data.database.AppDatabase
import com.tamerslab.domain.transfer.ExternalIndividualBinding
import com.tamerslab.domain.transfer.GameEndpointPresence
import com.tamerslab.domain.transfer.NativeAppProjection
import com.tamerslab.domain.transfer.NativeCanonicalMapping
import com.tamerslab.domain.transfer.NativeGameSnapshot
import com.tamerslab.gamesave.*
import com.tamerslab.species.CanonicalSpeciesCatalog
import com.tamerslab.species.CanonicalSpeciesDto
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

enum class WorldDsSendBlocker {
    SNAPSHOT_UNAVAILABLE, CONVERSION_UNAVAILABLE, ALREADY_AWAY, FORMAT, REVIEW, STALE, WRITE_ACCESS,
    IDENTITY_TAKEN, DESTINATION_FULL,
    /** The individual has no canonical species, so no game can name it. */
    SPECIES_UNKNOWN,
    /** The individual has a species that the target title's own table does not contain. */
    SPECIES_NOT_IN_GAME,
}
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

    fun nativeReturnable(record: ObservedDigimon, allowFarm: Boolean = false): Boolean =
        record.nativeRecordBase64 != null && record.nativeIdentity != null &&
            record.container in (if (allowFarm) setOf(SaveContainer.PARTY, SaveContainer.FARM,
                SaveContainer.UNASSIGNED) else setOf(SaveContainer.PARTY, SaveContainer.UNASSIGNED)) &&
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
    private val speciesTables: NdsStorySpeciesTables? = null,
    private val catalogProvider: suspend () -> CanonicalSpeciesCatalog? = { null },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val journal = ExperimentalEditJournal(journalDirectory, ExperimentalSendRecord::class.java)
    private val mutex = Mutex()
    private val gson = Gson()
    /** The registered snapshot-import profile matching one observation's game/profile/codec. */
    private fun profileFor(observation: SaveObservation): VerifiedSnapshotImportProfile? =
        GameImportAcquisitionRepository.productionProfiles.firstOrNull { it.matches(observation) }

    private fun profileFor(gameId: String, profile: String, codec: Int): VerifiedSnapshotImportProfile? =
        GameImportAcquisitionRepository.productionProfiles.firstOrNull { it.matches(gameId, profile, codec) }

    /** The verified native editor for one observation's layout (World DS or Dawn/Dusk). */
    private fun editorFor(observation: SaveObservation): NdsStorySaveEditor =
        requireNotNull(NdsStorySaveEditors.forObservation(observation)) { "No native editor for this game save" }

    private fun isDs2Game(gameId: String?): Boolean = gameId == NdsStoryProfiles.DAWN_DUSK.gameId
    private fun isLeGame(gameId: String?): Boolean = gameId == NdsStoryProfiles.LOST_EVOLUTION.gameId

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
        val profile = profileFor(binding.gameId, binding.formatProfile, snapshot.codecVersion) ?: return null
        if (binding.identityScheme != profile.identityScheme) return null
        val record = runCatching { gson.fromJson(snapshot.recordJson, ObservedDigimon::class.java) }.getOrNull() ?: return null
        return record.takeIf(WorldDsSendPolicy::nativeReturnable)
    }

    /**
     * The destination must have room for the requested container: party sends need a free visible
     * party slot, farm/bank sends only need a free record slot in every populated copy.
     */
    private fun destinationFull(observation: SaveObservation,
        destination: SaveContainer? = null): Boolean {
        val blocks = observation.records.mapNotNull { it.nativeParameters?.saveBlock }.distinct()
        if (blocks.isEmpty()) return true
        val profile = NdsStoryProfiles.forObservation(observation) ?: return true
        return blocks.any { block ->
            val records = observation.records.filter { it.nativeParameters?.saveBlock == block }
            val noRecordSlot = records.size >= profile.recordRegion.count
            val wantsParty = destination == null || destination == SaveContainer.PARTY
            noRecordSlot || (wantsParty && records.count { it.container == SaveContainer.PARTY } >= profile.partyVisibleCap)
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
    suspend fun outbound(individualId: String): ObservedDigimon? =
        outbound(individualId, retainedGameId(individualId))

    /**
     * The record a send to [targetGameId] would write.
     *
     * The individual lives in Storage, so the destination does not have to be the game it came
     * from: a different title gets a freshly synthesized record for that title's layout (native
     * bytes cannot cross layouts), while the same title prefers the exact retained record. With no
     * target the individual's own layout decides, falling back to World DS for an app-only preview.
     */
    suspend fun outbound(individualId: String, targetGameId: String?): ObservedDigimon? {
        val sourceGameId = retainedGameId(individualId)
        // No target and no source game: the partner may not exist in World DS at all, so pick the
        // first wired story title that can actually project it instead of assuming World DS.
        if (targetGameId == null && sourceGameId == null) {
            for (candidate in listOf(
                NdsStoryProfiles.WORLD_DS.gameId,
                NdsStoryProfiles.DAWN_DUSK.gameId,
                NdsStoryProfiles.LOST_EVOLUTION.gameId,
            )) {
                val record = outbound(individualId, candidate)
                if (record != null) return record
            }
            return null
        }
        val target = targetGameId ?: sourceGameId ?: NdsStoryProfiles.WORLD_DS.gameId
        // A seed is only meaningful inside its own layout; another title is synthesized fresh.
        val sameLayout = sourceGameId == null || sourceGameId == target
        val retained = retainedRecord(individualId)?.takeIf { sameLayout }
        val projection = db.transferContinuityDao().projection(individualId)
        // Tracked story progress is the authority for every story-series destination; a retained
        // record is only reused while no story block has been established yet.
        var story = com.tamerslab.transfer.data.ProgressDomainRepository(db.progressDomainDao())
            .story(individualId)
        // A partner imported before tracking existed still has real story progress in its retained
        // native snapshot; backfill the block from it before any bootstrap is considered.
        if (story == null) {
            story = previewStoryFromRetained(individualId)
        }
        // First entry into a story game seeds the shared block once. Dawn/Dusk has the game-native
        // created-at-level provider (recovered engine growth formula), so it can bootstrap now.
        if (story == null && isDs2Game(target)) {
            story = previewDs2Story(individualId)
        }
        if (story == null && isLeGame(target)) {
            story = previewLeStory(individualId)
        }
        if (story == null && !isDs2Game(target) && !isLeGame(target)) {
            story = previewDs1Story(individualId)
        }
        if (isDs2Game(target)) {
            val state = ds2OutboundState(individualId) ?: return retained
            // Existing records written before the species fields existed stay unusable until the
            // record itself is regenerated; matchBaseline alone would keep returning broken bytes.
            if (story == null && retained != null && !needsSpeciesRepair(retained) && projection != null &&
                Ds2OutboundConversion.matchesBaseline(state, projection) &&
                Ds2OutboundConversion.retainedCoversAppLevel(state, retained)) return retained
            // A seeded record keeps its native species: the app species mapping only matters for a
            // brand-new record (identity and evolution state stay the game's authority).
            val seeded = if (retained == null) state else {
                val table = speciesTables?.ds2
                val defaults = table?.let { Ds2OutboundConversion.defaultsOf(it, retained.nativeSpeciesId) }
                    ?: state.defaults
                state.copy(dsSpeciesId = retained.nativeSpeciesId, defaults = defaults,
                    lineage = table?.lineage(retained.nativeSpeciesId).orEmpty(), fallbackName = state.fallbackName)
            }
            return Ds2OutboundConversion.generate(seeded, retained?.nativeRecordBase64,
                nativeStats = story?.let(StoryProgressTracking::toDs2Native),
                expCounters = story?.let { StoryProgressTracking.expChannels(it.expTotal, Ds2StatConversion.EXP_CHANNELS) }
                    .orEmpty())
        }
        if (isLeGame(target)) {
            val state = leOutboundState(individualId) ?: return retained
            if (story == null && retained != null && !needsSpeciesRepair(retained) && projection != null &&
                LeOutboundConversion.matchesBaseline(state, projection) &&
                LeOutboundConversion.retainedCoversAppLevel(state, retained)) return retained
            // A seeded record keeps its native species and identity; only a brand-new record uses
            // the app species mapping.
            val seeded = if (retained == null) state else {
                val table = speciesTables?.le
                state.copy(dsSpeciesId = retained.nativeSpeciesId,
                    defaults = table?.defaults(retained.nativeSpeciesId) ?: state.defaults,
                    fallbackName = table?.name(retained.nativeSpeciesId) ?: state.fallbackName)
            }
            return LeOutboundConversion.generate(seeded, retained?.nativeRecordBase64,
                nativeStats = story?.let(StoryProgressTracking::toLeNative),
                expTotal = story?.expTotal ?: 0L)
        }
        val state = appOutboundState(individualId) ?: return retained
        if (story == null && retained != null && !needsSpeciesRepair(retained) && projection != null &&
            WorldDsOutboundConversion.matchesBaseline(state, projection) &&
            WorldDsOutboundConversion.retainedCoversAppLevel(state, retained)) return retained
        val speciesId = retained?.nativeSpeciesId ?: state.dsSpeciesId
        val defaults = speciesTables?.ds1?.defaults(speciesId)
        val lineage = speciesTables?.ds1?.lineage(speciesId).orEmpty()
        return WorldDsOutboundConversion.generate(state, retained?.nativeRecordBase64, defaults, lineage,
            nativeStats = story?.let(StoryProgressTracking::toWorldDsNative))
    }

    /** Computes the story block from the individual's retained native record without saving it. */
    private suspend fun previewStoryFromRetained(individualId: String): StoryProgressDomain? {
        val snapshotId = db.nativeMaterializationDao().latestSnapshotId(individualId) ?: return null
        val snapshot = db.nativeMaterializationDao().snapshot(snapshotId) ?: return null
        val binding = db.nativeMaterializationDao().binding(snapshot.bindingId) ?: return null
        val record = runCatching {
            com.google.gson.Gson().fromJson(snapshot.recordJson, ObservedDigimon::class.java)
        }.getOrNull() ?: return null
        val observed = StoryProgressTracking.fromRecord(record, binding.gameId) ?: return null
        return StoryProgressTracking.toDomain(observed, individualId, System.currentTimeMillis(),
            com.tamerslab.domain.transfer.ProgressSeedSource.IMPORT)
    }

    /**
     * One-time World DS bootstrap using the DS1 created-at-level formula
     * (`FUN_020a1c70`: base species block scaled by level and growth row).
     */
    private suspend fun previewDs1Story(individualId: String): StoryProgressDomain? {
        val table = speciesTables?.ds1 ?: return null
        val state = runCatching { appOutboundState(individualId) }.getOrNull() ?: return null
        val level = StoryBootstrapPolicy.levelFor(state.appStats.vitalPoints)
        val stats = WorldDsCreationStats(table).statsFor(state.dsSpeciesId, level) ?: return null
        val now = System.currentTimeMillis()
        val seeded = StoryProgressDomain(
            individualId = individualId,
            level = stats.level,
            expTotal = 0L,
            hp = stats.hp,
            maxHp = stats.maxHp,
            mp = stats.mp,
            maxMp = stats.maxMp,
            attack = stats.attack,
            defense = stats.defense,
            spirit = stats.spirit,
            speed = stats.speed,
            aptitude = null,
            seededAt = now,
            seedSource = com.tamerslab.domain.transfer.ProgressSeedSource.VB,
            revision = 0L,
            updatedAt = now,
        )
        return seeded
    }

    /**
     * One-time Lost Evolution bootstrap, mirroring the Dawn/Dusk seed with the LE creation formula.
     */
    private suspend fun previewLeStory(individualId: String): StoryProgressDomain? {
        val table = speciesTables?.le ?: return null
        val state = runCatching { leOutboundState(individualId) }.getOrNull() ?: return null
        val level = StoryBootstrapPolicy.levelFor(state.appStats.vitalPoints)
        val stats = LeCreationStats(table).statsFor(state.dsSpeciesId, level) ?: return null
        val now = System.currentTimeMillis()
        val seeded = StoryProgressDomain(
            individualId = individualId,
            level = stats.level,
            expTotal = 0L,
            hp = stats.hp,
            maxHp = stats.maxHp,
            mp = stats.mp,
            maxMp = stats.maxMp,
            attack = stats.attack,
            defense = stats.defense,
            spirit = stats.spirit,
            speed = stats.speed,
            aptitude = null,
            seededAt = now,
            seedSource = com.tamerslab.domain.transfer.ProgressSeedSource.VB,
            revision = 0L,
            updatedAt = now,
        )
        return seeded
    }

    /**
     * One-time Dawn/Dusk bootstrap: level from the app/VB vital points, stats from the game's own
     * created-at-level formula. The written block is tagged `VB` so later story imports never
     * rewrite its provenance, and the send immediately uses it.
     */
    private suspend fun previewDs2Story(individualId: String): StoryProgressDomain? {
        val table = speciesTables?.ds2 ?: return null
        val state = runCatching { ds2OutboundState(individualId) }.getOrNull() ?: return null
        val level = StoryBootstrapPolicy.levelFor(state.appStats.vitalPoints)
        val stats = Ds2CreationStats(table).statsFor(state.dsSpeciesId, level) ?: return null
        val now = System.currentTimeMillis()
        val seeded = StoryProgressDomain(
            individualId = individualId,
            level = stats.level,
            expTotal = 0L,
            hp = stats.hp,
            maxHp = stats.maxHp,
            mp = stats.mp,
            maxMp = stats.maxMp,
            attack = stats.attack,
            defense = stats.defense,
            spirit = stats.spirit,
            speed = stats.speed,
            aptitude = null,
            seededAt = now,
            seedSource = com.tamerslab.domain.transfer.ProgressSeedSource.VB,
            revision = 0L,
            updatedAt = now,
        )
        return seeded
    }

    /**
     * Spends the one-time story seed after a write has been verified and committed. Previews and
     * blockers never call this, so an abandoned transfer leaves no story block behind.
     */
    private suspend fun persistStorySeedIfMissing(individualId: String, targetGameId: String) {
        val repository = com.tamerslab.transfer.data.ProgressDomainRepository(db.progressDomainDao())
        if (repository.story(individualId) != null) return
        val preview = previewStoryFromRetained(individualId) ?: when {
            isDs2Game(targetGameId) -> previewDs2Story(individualId)
            isLeGame(targetGameId) -> previewLeStory(individualId)
            else -> previewDs1Story(individualId)
        }
        val seeded = preview ?: return
        runCatching { repository.recordStory(seeded) }
    }

    /**
     * The species a transfer should use: the individual's own species, refined by the assigned card
     * character when that card carries a variant of the same Digimon (Agumon -> Agumon (Black)).
     */
    private fun canonicalSpecies(catalog: CanonicalSpeciesCatalog, speciesName: String?,
        nickname: String?, cardSpeciesId: String?): CanonicalSpeciesDto? {
        val base = listOfNotNull(
            speciesName?.trim()?.takeIf { it.isNotEmpty() },
            cardSpeciesId?.trim()?.takeIf { it.isNotEmpty() },
            nickname?.trim()?.takeIf { it.isNotEmpty() },
        ).firstNotNullOfOrNull { catalog.resolve(it) }
        val refined = cardSpeciesId?.trim()?.takeIf { it.isNotEmpty() }?.let(catalog::resolve)
        return WorldDsSpeciesCrosswalk.preferRefined(catalog, base, refined)
    }

    /** The game the newest archived snapshot of this individual came from, if known. */
    private suspend fun retainedGameId(individualId: String): String? {
        val snapshotId = db.nativeMaterializationDao().latestSnapshotId(individualId) ?: return null
        val snapshot = db.nativeMaterializationDao().snapshot(snapshotId) ?: return null
        return db.nativeMaterializationDao().binding(snapshot.bindingId)?.gameId
    }

    private fun needsSpeciesRepair(record: ObservedDigimon?): Boolean {
        val encoded = record?.nativeRecordBase64 ?: return false
        return runCatching { WorldDsRecordCodec.needsSpeciesRepair(WorldDsRecordCodec.decodeBase64(encoded)) }
            .getOrDefault(false)
    }

    /** Why [outbound] is null: no app representation for the individual, or a species this game cannot name. */
    suspend fun outboundBlocker(individualId: String): WorldDsSendBlocker? {
        // Any wired destination counts: a partner may be sendable to World DS, Dawn/Dusk or both.
        if (outbound(individualId) != null) return null
        if (outbound(individualId, NdsStoryProfiles.DAWN_DUSK.gameId) != null) return null
        if (outbound(individualId, NdsStoryProfiles.LOST_EVOLUTION.gameId) != null) return null
        val tables = speciesTables ?: return WorldDsSendBlocker.CONVERSION_UNAVAILABLE
        val catalog = runCatching { catalogProvider() }.getOrNull() ?: return WorldDsSendBlocker.CONVERSION_UNAVAILABLE
        val individual = db.digimonIndividualDao().getIndividual(individualId) ?: return WorldDsSendBlocker.SNAPSHOT_UNAVAILABLE
        val reading = WorldDsAppStatsReader.read(db, individualId)
        val canonical = canonicalFor(individual, reading, catalog)
            ?: return WorldDsSendBlocker.SPECIES_UNKNOWN
        if (WorldDsSpeciesCrosswalk.dsIdsByCanonical(tables.ds1, catalog)[canonical.id] != null ||
            WorldDsSpeciesCrosswalk.ds2IdsByCanonical(tables.ds2, catalog)[canonical.id] != null ||
            WorldDsSpeciesCrosswalk.leIdsByCanonical(tables.le, catalog)[canonical.id] != null) {
            return WorldDsSendBlocker.SNAPSHOT_UNAVAILABLE
        }
        return WorldDsSendBlocker.SPECIES_NOT_IN_GAME
    }

    private suspend fun appOutboundState(individualId: String): WorldDsOutboundState? {
        val table = speciesTables?.ds1 ?: return null
        val catalog = catalogProvider() ?: return null
        val individual = db.digimonIndividualDao().getIndividual(individualId) ?: return null
        val reading = WorldDsAppStatsReader.read(db, individualId)
        // Every individual must be projectable: derive the species from the retained native record
        // when the app representation is incomplete, and use a bundled default as the last resort.
        val canonical = canonicalFor(individual, reading, catalog) ?: return null
        val dsSpeciesId = WorldDsSpeciesCrosswalk.dsIdsByCanonical(table, catalog)[canonical.id]
            ?: return null
        val fallback = table.name(dsSpeciesId) ?: return null
        val effective = reading ?: fallbackReading()
        val displayName = WorldDsOutboundConversion.recordName(individual.nickname, fallback)
        return WorldDsOutboundState(dsSpeciesId, canonical.id, displayName, fallback,
            effective.stage, effective.baseHp, effective.baseBp, effective.baseAp, effective.appStats)
    }

    /** Dawn/Dusk projection state for a partner that has no exact retained record for that title. */
    private suspend fun ds2OutboundState(individualId: String): Ds2OutboundState? {
        val table = speciesTables?.ds2 ?: return null
        val catalog = catalogProvider() ?: return null
        val individual = db.digimonIndividualDao().getIndividual(individualId) ?: return null
        val reading = WorldDsAppStatsReader.read(db, individualId)
        val canonical = canonicalFor(individual, reading, catalog) ?: return null
        val dsSpeciesId = WorldDsSpeciesCrosswalk.ds2IdsByCanonical(table, catalog)[canonical.id]
            ?: return null
        val fallback = table.name(dsSpeciesId) ?: return null
        val defaults = Ds2OutboundConversion.defaultsOf(table, dsSpeciesId) ?: return null
        val displayName = Ds2OutboundConversion.recordName(individual.nickname, fallback)
        return Ds2OutboundState(dsSpeciesId, canonical.id, displayName, fallback,
            (reading ?: fallbackReading()).appStats, defaults, table.lineage(dsSpeciesId))
    }

    /** Lost Evolution projection state for a partner that has no exact retained record. */
    private suspend fun leOutboundState(individualId: String): LeOutboundState? {
        val table = speciesTables?.le ?: return null
        val catalog = catalogProvider() ?: return null
        val individual = db.digimonIndividualDao().getIndividual(individualId) ?: return null
        val reading = WorldDsAppStatsReader.read(db, individualId)
        val canonical = canonicalFor(individual, reading, catalog) ?: return null
        val speciesId = WorldDsSpeciesCrosswalk.leIdsByCanonical(table, catalog)[canonical.id]
            ?: return null
        val fallback = table.name(speciesId) ?: return null
        val defaults = table.defaults(speciesId)
        val displayName = LeOutboundConversion.recordName(individual.nickname, fallback)
        val aptitude = retainedRecord(individualId)?.nativeParameters?.aptitude?.takeIf { it in 1..100 } ?: 100
        return LeOutboundState(speciesId, canonical.id, displayName, fallback,
            (reading ?: fallbackReading()).appStats, defaults, aptitude)
    }

    /**
     * Canonical identity for a projection. Falls back to the retained record's own native species
     * name (read through that game's table) when the individual has no usable app species field,
     * and finally to the catalog's default so a record can always be synthesized.
     */
    private suspend fun canonicalFor(individual: com.tamerslab.domain.device_data.DigimonIndividual,
        reading: WorldDsAppReading?, catalog: CanonicalSpeciesCatalog): CanonicalSpeciesDto? {
        canonicalSpecies(catalog, individual.speciesName, individual.nickname, reading?.speciesId)
            ?.let { return it }
        val tables = speciesTables
        val retained = retainedRecord(individual.individualId)
        if (tables != null && retained != null) {
            runCatching {
                val snapshotId = db.nativeMaterializationDao().latestSnapshotId(individual.individualId)
                val snapshot = snapshotId?.let { db.nativeMaterializationDao().snapshot(it) }
                val binding = snapshot?.let { db.nativeMaterializationDao().binding(it.bindingId) }
                if (snapshot != null && binding != null) {
                    tables.name(binding.gameId, binding.formatProfile, snapshot.codecVersion,
                        retained.nativeSpeciesId)
                } else {
                    null
                }
            }.getOrNull()?.let { name -> catalog.resolve(name)?.let { return it } }
        }
        return null
    }

    /**
     * Why this individual cannot be written into [targetGameId] because of its species:
     * null when the target table can name it, [WorldDsSendBlocker.SPECIES_UNKNOWN] when the
     * individual has no canonical species, and [WorldDsSendBlocker.SPECIES_NOT_IN_GAME] when the
     * species exists in the app catalog but not in that title.
     */
    suspend fun speciesBlocker(individualId: String, targetGameId: String): WorldDsSendBlocker? {
        val tables = speciesTables ?: return null
        val catalog = runCatching { catalogProvider() }.getOrNull() ?: return null
        val individual = db.digimonIndividualDao().getIndividual(individualId)
            ?: return WorldDsSendBlocker.SNAPSHOT_UNAVAILABLE
        val reading = WorldDsAppStatsReader.read(db, individualId)
        val canonical = canonicalFor(individual, reading, catalog)
            ?: return WorldDsSendBlocker.SPECIES_UNKNOWN
        val mapped = when {
            isDs2Game(targetGameId) ->
                WorldDsSpeciesCrosswalk.ds2IdsByCanonical(tables.ds2, catalog)[canonical.id] != null
            isLeGame(targetGameId) ->
                WorldDsSpeciesCrosswalk.leIdsByCanonical(tables.le, catalog)[canonical.id] != null
            else -> WorldDsSpeciesCrosswalk.dsIdsByCanonical(tables.ds1, catalog)[canonical.id] != null
        }
        return if (mapped) null else WorldDsSendBlocker.SPECIES_NOT_IN_GAME
    }

    /** Minimal valid reading used when an individual has no app character to read stats from. */
    private fun fallbackReading(): WorldDsAppReading = WorldDsAppReading(
        appStats = WorldDsStatConversion.AppStats(stage = 1, baseHp = 10, baseBp = 5, baseAp = 5),
        stage = 1, baseHp = 10, baseBp = 5, baseAp = 5, speciesId = null,
    )

    suspend fun blocker(view: LinkedSaveView, individualId: String, retained: ObservedDigimon,
        destination: SaveContainer? = null): WorldDsSendBlocker? {
        val observation = view.observation ?: return WorldDsSendBlocker.FORMAT
        val profile = profileFor(observation) ?: return WorldDsSendBlocker.FORMAT
        if (view.source.lastError != null) return WorldDsSendBlocker.STALE
        if (!writer.canWrite(view.source.documentUri, view.source.grantUri)) return WorldDsSendBlocker.WRITE_ACCESS
        // Species problems are the user's actionable reason; explain them before conversion.
        speciesBlocker(individualId, observation.gameId)?.let { return it }
        // Re-project for this destination: a generated record only exists once the layout is known.
        val candidate = outbound(individualId, observation.gameId)
            ?: return WorldDsSendBlocker.CONVERSION_UNAVAILABLE
        // A synthesized record has no native identity yet; the destination assigns a fresh one on write.
        val identity = candidate.nativeIdentity ?: run {
            if (destinationFull(observation, destination)) return WorldDsSendBlocker.DESTINATION_FULL
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
        if (destinationFull(observation, destination)) return WorldDsSendBlocker.DESTINATION_FULL
        val identities = listOf(profile.nativeIdentity(candidate))
        val existing = db.nativeGameArchiveDao().bindings(view.source.sourceId, view.source.documentKey, observation.gameId,
            observation.formatProfile, profile.identityScheme, identities)
        if (existing.any { it.individualId != individualId }) return WorldDsSendBlocker.REVIEW
        return null
    }

    /** Exact native insertion after a durable journal/backup, with a fresh readback verification. */
    suspend fun send(individualId: String, sourceId: String, expectedRevision: String,
        destination: SaveContainer? = null): ExperimentalWorldDsSendResult =
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
            capture().use { captured ->
                val observed = captured.observation
                val outbound = requireNotNull(outbound(individualId, observed.gameId)) {
                    "No native record is available for this destination"
                }
                val recordBase64 = requireNotNull(outbound.nativeRecordBase64)
                val generated = outbound.nativeIdentity == null
                if (observed.contentRevision != expectedRevision) throw ImportSourceChangedException()
                require(blocker(LinkedSaveView(source, observed), individualId, outbound, destination) == null) {
                    "Native send is unavailable for this destination"
                }
                val original = requireNotNull(captured.container).copyOf()
                // Only the authoritative copy matters: an older slot legitimately still contains the
                // pre-move roster, and rewriting it is never required for the game to load our copy.
                val authority = WorldDsCopySelection.authoritativeCopy(observed)
                val replacing = !generated && outbound.nativeIdentity != null &&
                    observed.records.any {
                        it.nativeIdentity == outbound.nativeIdentity &&
                            (authority == null || it.nativeParameters?.saveBlock == authority)
                    }
                val edit = when {
                    generated -> editorFor(observed).insertGenerated(original, observed.contentRevision, recordBase64, destination)
                        .let { PreparedSend(it.candidateBytes, it.candidateRevision, it.recordKey, requireNotNull(it.nativeIdentity)) }
                    replacing -> editorFor(observed).replaceRetained(original, observed.contentRevision, recordBase64)
                        .let { PreparedSend(it.candidateBytes, it.candidateRevision, it.recordKey, requireNotNull(it.nativeIdentity)) }
                    else -> editorFor(observed).insertRetained(original, observed.contentRevision, recordBase64, destination)
                        .let { PreparedSend(it.candidateBytes, it.candidateRevision, it.recordKey, requireNotNull(it.nativeIdentity)) }
                }
                val candidate = edit.candidateBytes
                try {
                    var operation = ExperimentalSendRecord(UUID.randomUUID().toString(), sourceId, source.documentKey,
                        observed.contentRevision, edit.candidateRevision, observed,
                        outbound.copy(recordKey = edit.recordKey, nativeIdentity = edit.nativeIdentity),
                        individualId, UUID.randomUUID().toString(), null, clock(), generated, replacing,
                        destination?.name)
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
                        // The one-time story seed is only spent once the transfer is committed.
                        persistStorySeedIfMissing(individualId, observed.gameId)
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
                        val requested = operation.destination?.let { runCatching { SaveContainer.valueOf(it) }.getOrNull() }
                        val rebuiltBytes = when {
                            operation.generated -> editorFor(operation.observation).insertGenerated(original, operation.sourceRevision, recordBytes, requested).candidateBytes
                            operation.replaced -> editorFor(operation.observation).replaceRetained(original, operation.sourceRevision, recordBytes).candidateBytes
                            else -> editorFor(operation.observation).insertRetained(original, operation.sourceRevision, recordBytes, requested).candidateBytes
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
        val decoded = editorFor(operation.observation).read(original).let { pre ->
            check(pre.contentRevision == operation.sourceRevision)
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
        val candidate = editorFor(operation.observation).read(candidateBytes)
        check(candidate.contentRevision == operation.candidateRevision)
        // Every populated copy received the exact record; a deterministic copy represents the insertion
        // or the repaired record, which keeps its original party/farm container.
        val requested = operation.destination?.let { runCatching { SaveContainer.valueOf(it) }.getOrNull() }
        val expectedContainers = when (requested) {
            null -> setOf(SaveContainer.PARTY, SaveContainer.FARM, SaveContainer.BANK, SaveContainer.UNASSIGNED)
            SaveContainer.PARTY -> setOf(SaveContainer.PARTY)
            SaveContainer.FARM -> setOf(SaveContainer.FARM)
            SaveContainer.BANK -> setOf(SaveContainer.BANK, SaveContainer.UNASSIGNED)
            else -> emptySet()
        }
        val insertedOnDisk = candidate.records.filter { it.nativeIdentity == sentIdentity &&
            (operation.replaced || it.container in expectedContainers) }
            .minByOrNull { requireNotNull(it.nativeParameters).saveBlock }
            ?: throw WorldDsSendRecoveryRequired()
        check(insertedOnDisk.nativeRecordBase64 != null && insertedOnDisk.nativeIdentity != null)
        // The stored snapshot keeps the record itself; the storage review state is never carried over.
        val inserted = insertedOnDisk.copy(membership = RecordMembership.ACTIVE_RECORD, issues = emptyList())
        check(db.digimonIndividualDao().getIndividual(operation.individualId) != null) { "The sent individual no longer exists" }
        val profile = requireNotNull(profileFor(operation.observation))
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
        // The projection checkpoint records the app state this send wrote. A Dawn/Dusk destination
        // uses that layout's own state; a World DS destination keeps the original path.
        val leState = if (isLeGame(operation.observation.gameId)) leOutboundState(operation.individualId) else null
        val ds2State = if (leState == null && isDs2Game(operation.observation.gameId))
            ds2OutboundState(operation.individualId) else null
        val ds1State = if (ds2State == null && leState == null) appOutboundState(operation.individualId) else null
        val canonical = leState?.canonicalSpeciesId ?: ds2State?.canonicalSpeciesId ?: ds1State?.canonicalSpeciesId
        val app = leState?.appStats ?: ds2State?.appStats ?: ds1State?.appStats
        if (canonical != null && app != null) {
            if (db.nativeMaterializationDao().mapping(snapshotId) == null)
                db.nativeMaterializationDao().saveMapping(NativeCanonicalMapping(snapshotId, inserted.nativeSpeciesId,
                    canonical, "GAME_TABLE", operation.createdAt))
            db.transferContinuityDao().saveProjection(NativeAppProjection(operation.individualId, snapshotId,
                when {
                    leState != null -> LeStatConversion.VERSION
                    ds2State != null -> Ds2StatConversion.VERSION
                    else -> WorldDsStatConversion.VERSION
                },
                "APP_SENT_VALUES", app.stage, app.baseHp, app.baseBp, app.baseAp,
                app.trainingHp, app.trainingAp, app.trainingBp, app.vitalPoints,
                app.wins, app.losses, db.transferContinuityDao().lastDelta(operation.individualId),
                operation.createdAt, db.transferContinuityDao().projection(operation.individualId)?.originBasisPoints ?: 10000))
        }
        db.nativeMaterializationDao().savePresence(GameEndpointPresence(operation.individualId, operation.sourceId,
            operation.documentKey, operation.observation.gameId, identity, operation.operationId, operation.createdAt))
        snapshotId
    }

    private fun hash(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }
}
