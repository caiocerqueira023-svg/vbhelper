package com.github.nacabaro.vbhelper.source.gameSave

import androidx.room.withTransaction
import com.github.nacabaro.vbhelper.database.AppDatabase
import com.github.nacabaro.vbhelper.domain.card.CardCharacter
import com.github.nacabaro.vbhelper.domain.device_data.TransformationHistory
import com.github.nacabaro.vbhelper.domain.transfer.*
import com.github.nacabaro.vbhelper.gamesave.ObservedDigimon
import com.github.nacabaro.vbhelper.gamesave.WorldDsSaveReader
import com.github.nacabaro.vbhelper.gamesave.WorldDsStatConversion
import com.github.nacabaro.vbhelper.species.*
import com.github.nacabaro.vbhelper.utils.DeviceType
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first

object NativeCanonicalMappingPolicy {
    fun target(catalog: CanonicalSpeciesCatalog, canonicalId: String): CanonicalSpeciesDto {
        val species = requireNotNull(catalog.speciesById[canonicalId]) { "An exact canonical ID is required" }
        require(species.kind == "DIGIMON")
        return species
    }
}

class NativeMaterializationStaleException : IllegalStateException("Native snapshot or mapping changed")
data class NativeMaterializationResult(val receipt: NativeMaterializationReceipt, val created: Boolean)
enum class NativeMaterializationReadiness { READY, PROFILE_UNVERIFIED, ALREADY_MATERIALIZED, ORIGIN_STATS_UNMAPPED }

class NativeMaterializationRepository(
    private val db: AppDatabase,
    private val acquisition: GameImportAcquisitionRepository,
    private val catalog: suspend () -> CanonicalSpeciesCatalog,
    private val prepareDefinition: suspend (String) -> CardCharacter,
    private val worldDsSpecies: WorldDsSpeciesTable? = null,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val dao get() = db.nativeMaterializationDao()
    private val gson = Gson()
    val mappings = dao.mappings()

    suspend fun candidates(): List<CanonicalSpeciesDto> = withContext(Dispatchers.IO) {
        catalog().data.species.filter { it.kind == "DIGIMON" }.sortedBy { it.name.lowercase(java.util.Locale.ROOT) }
    }

    suspend fun mapping(snapshotId: Long): NativeCanonicalMapping? = dao.mapping(snapshotId)

    /** Upgrade existing locally acquired/mapped individuals into the ordinary playable model. */
    suspend fun materializeRetainedMappings(): Int = withContext(Dispatchers.IO) {
        reconcileOrphanedDeletions()
        reconcileReturnedSpecies()
        var created = 0
        for (archive in acquisition.archives.first().filter { it.characterId == null }) {
            val mapping = dao.mapping(archive.snapshot.id) ?: continue
            if (mapping.authority != "USER_CONFIRMED" || readiness(mapping) != NativeMaterializationReadiness.READY) continue
            try {
                if (materialize(mapping).created) created++
            } catch (_: NativeMaterializationStaleException) {
                // A concurrently updated archive will be considered on the next startup.
            }
        }
        created
    }

    /**
     * Repairs archive entries left behind by the former delete behavior: the playable character was
     * deleted but the materialization receipt remained. A newer acquisition means the Digimon was
     * re-imported after deletion and is restored; otherwise the orphaned archive rows are removed.
     */
    suspend fun reconcileOrphanedDeletions(): Int = withContext(Dispatchers.IO) {
        var repaired = 0
        for (archive in acquisition.archives.first().filter { it.characterId == null }) {
            val individualId = archive.snapshot.individualId
            val receipt = dao.receipt(individualId)
            if (db.userCharacterDao().getByIndividualIdSync(individualId).isNotEmpty()) continue
            val latest = dao.latestSnapshotId(individualId) ?: continue
            if (receipt == null) {
                // A reappearing receipt-less archive whose individual was materialized before
                // means an interrupted restore cleared the receipt; recover it.
                val previous = db.transferContinuityDao().materializedEvent(individualId) ?: continue
                val speciesId = previous.speciesId ?: continue
                try {
                    val mapping = dao.mapping(latest) ?: saveMapping(latest, speciesId)
                    materialize(mapping)
                    repaired++
                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (failure: Exception) {
                    android.util.Log.e("NativeMaterialization", "Could not recover $individualId", failure)
                }
                continue
            }
            if (latest == receipt.snapshotId) {
                acquisition.forgetIndividual(individualId)
            } else {
                val authority = dao.mapping(receipt.snapshotId)?.authority ?: "USER_CONFIRMED"
                try {
                    val mapping = dao.mapping(latest) ?: saveMapping(latest, receipt.canonicalSpeciesId, authority)
                    materialize(mapping)
                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (failure: Exception) {
                    android.util.Log.e("NativeMaterialization", "Could not restore $individualId", failure)
                    // Keep the old receipt so a later startup retries instead of stranding a placeholder.
                    dao.insertReceipt(receipt)
                }
            }
            repaired++
        }
        repaired
    }

    suspend fun readiness(mapping: NativeCanonicalMapping): NativeMaterializationReadiness = withContext(Dispatchers.IO) {
        val snapshot = requireNotNull(dao.snapshot(mapping.snapshotId))
        if (dao.receipt(snapshot.individualId) != null) return@withContext NativeMaterializationReadiness.ALREADY_MATERIALIZED
        val binding = requireNotNull(dao.binding(snapshot.bindingId))
        if (!acquisition.supportsNativeRepresentation(binding.gameId, binding.formatProfile, snapshot.codecVersion, binding.identityScheme))
            return@withContext NativeMaterializationReadiness.PROFILE_UNVERIFIED
        val origin = requireNotNull(dao.originSnapshot(snapshot.individualId))
        val originBinding = requireNotNull(dao.binding(origin.bindingId))
        if (!acquisition.hasOriginProjection(originBinding.gameId, originBinding.formatProfile, origin.codecVersion)) return@withContext NativeMaterializationReadiness.ORIGIN_STATS_UNMAPPED
        NativeMaterializationReadiness.READY
    }

    /** The authority records who chose the identity: the user's review or the game's own species table. */
    suspend fun saveMapping(snapshotId: Long, canonicalId: String, authority: String = "USER_CONFIRMED"): NativeCanonicalMapping = withContext(Dispatchers.IO) {
        require(authority == "USER_CONFIRMED" || authority == "GAME_TABLE")
        val target = NativeCanonicalMappingPolicy.target(catalog(), canonicalId)
        db.withTransaction {
            val snapshot = requireNotNull(dao.snapshot(snapshotId))
            checkLatest(snapshot)
            if (dao.receipt(snapshot.individualId)?.let { it.snapshotId != snapshotId } == true &&
                db.userCharacterDao().getByIndividualIdSync(snapshot.individualId).isEmpty())
                dao.deleteReceipt(snapshot.individualId)
            check(dao.receipt(snapshot.individualId) == null) { "Already materialized" }
            val record = gson.fromJson(snapshot.recordJson, ObservedDigimon::class.java)
            dao.mapping(snapshotId)?.takeIf { it.canonicalSpeciesId == target.id } ?: NativeCanonicalMapping(
                snapshotId, record.nativeSpeciesId, target.id, authority, clock()).also { dao.saveMapping(it) }
        }
    }

    private suspend fun checkLatest(snapshot: NativeGameSnapshot) {
        if (dao.latestSnapshotId(snapshot.individualId) != snapshot.id) throw NativeMaterializationStaleException()
        val binding = requireNotNull(dao.binding(snapshot.bindingId))
        check(binding.individualId == snapshot.individualId)
        check(dao.wasAcquired(snapshot.individualId, snapshot.contentRevision, binding.formatProfile, snapshot.codecVersion, snapshot.recordKey))
    }

    suspend fun materialize(mapping: NativeCanonicalMapping, useSourceName: Boolean = false): NativeMaterializationResult = withContext(Dispatchers.IO) {
        val initial = requireNotNull(dao.snapshot(mapping.snapshotId))
        val existing = dao.receipt(initial.individualId)
        if (existing != null) {
            val represented = db.userCharacterDao().getByIndividualIdSync(initial.individualId).isNotEmpty()
            // A receipt for this same snapshot survives representation deletion by design; a newer
            // acquisition replaces it, because the Digimon was re-imported after that deletion.
            if (represented || existing.snapshotId == initial.id)
                return@withContext NativeMaterializationResult(existing, false)
            dao.deleteReceipt(initial.individualId)
        }
        checkLatest(initial)
        if (dao.mapping(initial.id) != mapping) throw NativeMaterializationStaleException()
        val initialBinding = requireNotNull(dao.binding(initial.bindingId))
        check(acquisition.supportsNativeRepresentation(initialBinding.gameId, initialBinding.formatProfile, initial.codecVersion, initialBinding.identityScheme))
        val reference = catalog()
        val target = NativeCanonicalMappingPolicy.target(reference, mapping.canonicalSpeciesId)
        // Decode/install only the selected trusted artwork before the acquisition transaction.
        val prepared = prepareDefinition(target.id)
        db.withTransaction {
            dao.receipt(initial.individualId)?.let { return@withTransaction NativeMaterializationResult(it, false) }
            val snapshot = requireNotNull(dao.snapshot(mapping.snapshotId))
            checkLatest(snapshot)
            if (dao.mapping(snapshot.id) != mapping || mapping.authority !in setOf("USER_CONFIRMED", "GAME_TABLE")) throw NativeMaterializationStaleException()
            val record = gson.fromJson(snapshot.recordJson, ObservedDigimon::class.java)
            check(record.nativeSpeciesId == mapping.nativeSpeciesId)
            val binding = requireNotNull(dao.binding(snapshot.bindingId))
            check(acquisition.supportsNativeRepresentation(binding.gameId, binding.formatProfile, snapshot.codecVersion, binding.identityScheme))
            check(!dao.isAway(snapshot.individualId)) { "Individual is away at another endpoint" }
            check(db.userCharacterDao().getByIndividualIdSync(snapshot.individualId).isEmpty())
            val individual = requireNotNull(db.digimonIndividualDao().getIndividual(snapshot.individualId))
            if (useSourceName) check(individual.nickname == null && record.storedName.isNotEmpty()) { "Preserve the existing app nickname" }
            val definition = requireNotNull(db.characterDao().getById(prepared.id))
            check(definition.id == ExpandedCatalogRepository.definitionId(target.id) && definition.cardId == null &&
                definition.catalogSpeciesId == target.id && definition.speciesId == target.id)
            val now = clock()
            val characterId = AppPartnerFactory.materializeImportedLocked(db, definition, snapshot.individualId, now)
            applyWorldDsInitialStats(snapshot, binding, definition)
            val origin = requireNotNull(dao.originSnapshot(snapshot.individualId))
            val originBinding = requireNotNull(dao.binding(origin.bindingId))
            val originRecord = gson.fromJson(origin.recordJson, ObservedDigimon::class.java)
            val bias = acquisition.originBasisPoints(originBinding.gameId, originBinding.formatProfile, origin.codecVersion, originRecord)
            val appState = requireNotNull(db.appPartnerStateDao().state(snapshot.individualId))
            db.appPartnerStateDao().update(appState.copy(originBasisPoints = bias))
            if (useSourceName) db.nativeGameArchiveDao().updateNickname(snapshot.individualId, record.storedName)
            val receipt = NativeMaterializationReceipt(snapshot.individualId, snapshot.id, target.id,
                definition.id, characterId, com.github.nacabaro.vbhelper.gamesave.OriginStatInfluence.VERSION, now)
            db.transferContinuityDao().deleteMaterializedEvent(snapshot.individualId)
            dao.insertReceipt(receipt)
            val progress = requireNotNull(db.appPartnerStateDao().state(snapshot.individualId))
            val character = requireNotNull(db.userCharacterDao().getByIndividualIdSync(snapshot.individualId).firstOrNull())
            db.transferContinuityDao().saveProjection(NativeAppProjection(snapshot.individualId, snapshot.id, WorldDsStatConversion.VERSION,
                "APP_STARTING_VALUES", definition.stage, definition.baseHp, definition.baseBp, definition.baseAp,
                progress.trainingHp, progress.trainingAp, progress.trainingBp, character.vitalPoints,
                character.totalBattlesWon, character.totalBattlesLost,
                db.transferContinuityDao().lastDelta(snapshot.individualId), now, bias))
            val normalized = com.github.nacabaro.vbhelper.gamesave.ProgressVector.of(mapOf("training.hp" to progress.trainingHp.toLong(),
                "training.ap" to progress.trainingAp.toLong(), "training.bp" to progress.trainingBp.toLong(),
                "trophies" to progress.totalTrophies.toLong()))
            UniversalJourneyRepository(db).acceptVerifiedEntry(snapshot.individualId, "vbhelper-storage", "app-state-v1",
                gson.toJson(normalized).toByteArray(Charsets.UTF_8), normalized)
            NativeMaterializationResult(receipt, true)
        }
    }

    /**
     * First materialization of a World DS record seeds the app partner with the reverse-converted
     * values. Wrapped in the projection guard so the seed is never counted as fresh app progress.
     */
    private suspend fun applyWorldDsInitialStats(snapshot: NativeGameSnapshot, binding: ExternalIndividualBinding,
        definition: CardCharacter) {
        if (binding.gameId != WorldDsSaveReader.GAME_ID) return
        val record = gson.fromJson(snapshot.recordJson, ObservedDigimon::class.java)
        val native = WorldDsReceiveConversion.nativeStats(record) ?: return
        val reading = WorldDsAppStatsReader.read(db, snapshot.individualId) ?: return
        val converted = WorldDsStatConversion.toApp(native, reading.appStats.copy(
            stage = definition.stage.coerceIn(0, 5), baseHp = definition.baseHp, baseBp = definition.baseBp, baseAp = definition.baseAp))
        UniversalJourneyRepository(db).projection(snapshot.individualId, "world-ds-initial:${snapshot.id}") {
            val character = db.userCharacterDao().getByIndividualIdSync(snapshot.individualId)
                .firstOrNull { it.characterType == DeviceType.APP } ?: return@projection
            db.userCharacterDao().updateCharacter(character.copy(mood = converted.mood, vitalPoints = converted.vitalPoints,
                trophies = converted.trophies, totalBattlesWon = converted.wins, totalBattlesLost = converted.losses,
                activityLevel = converted.activityLevel))
            db.appPartnerStateDao().state(snapshot.individualId)?.let { progress ->
                db.appPartnerStateDao().update(progress.copy(trainingHp = converted.trainingHp, trainingBp = converted.trainingBp,
                    trainingAp = converted.trainingAp, totalTrophies = converted.trophies))
            }
        }
    }

    /**
     * Returning from a World DS endpoint: the newest snapshot's native deltas are added to the
     * individual's existing app fields, and the projection baseline is moved to the new snapshot so a
     * subsequent send returns the exact retained bytes unchanged.
     */
    suspend fun applyWorldDsReturn(snapshotId: Long, canonicalSpeciesId: String? = null,
        canonicalAuthority: String = "GAME_TABLE"): Boolean = withContext(Dispatchers.IO) {
        val snapshot = dao.snapshot(snapshotId) ?: return@withContext false
        val binding = dao.binding(snapshot.bindingId) ?: return@withContext false
        if (binding.gameId != WorldDsSaveReader.GAME_ID) return@withContext false
        val record = gson.fromJson(snapshot.recordJson, ObservedDigimon::class.java)
        val current = WorldDsReceiveConversion.nativeStats(record) ?: return@withContext false
        val reading = WorldDsAppStatsReader.read(db, snapshot.individualId) ?: return@withContext false
        val projection = db.transferContinuityDao().projection(snapshot.individualId)
        val previous = projection?.takeIf { it.nativeSnapshotId != snapshot.id }?.let { saved ->
            dao.snapshot(saved.nativeSnapshotId)?.let { gson.fromJson(it.recordJson, ObservedDigimon::class.java) }
        }
        val previousStats = previous?.let(WorldDsReceiveConversion::nativeStats)
        val updated = previousStats?.let { WorldDsStatConversion.applyDelta(it, current, reading.appStats) }
        val existingMapping = dao.mapping(snapshot.id)?.takeIf { it.nativeSpeciesId == record.nativeSpeciesId }
        val canonical = canonicalSpeciesId
            ?: existingMapping?.canonicalSpeciesId
            ?: NativeSpeciesResolution.tableIdentity(binding.gameId, binding.formatProfile, snapshot.codecVersion,
                record.nativeSpeciesId, worldDsSpecies, catalog())
        val target = canonical?.let { id -> runCatching { NativeCanonicalMappingPolicy.target(catalog(), id) }.getOrNull() }
        val definition = target?.let { species -> runCatching { prepareDefinition(species.id) }.getOrNull() }
        var changed = false
        db.withTransaction {
            if (updated != null) {
                UniversalJourneyRepository(db).projection(snapshot.individualId, "world-ds-return:${snapshot.id}") {
                    val character = db.userCharacterDao().getByIndividualIdSync(snapshot.individualId)
                        .firstOrNull { it.characterType == DeviceType.APP } ?: return@projection
                    db.userCharacterDao().updateCharacter(character.copy(vitalPoints = updated.vitalPoints,
                        trophies = updated.trophies, totalBattlesWon = updated.wins, totalBattlesLost = updated.losses,
                        activityLevel = updated.activityLevel))
                    db.appPartnerStateDao().state(snapshot.individualId)?.let { progress ->
                        db.appPartnerStateDao().update(progress.copy(trainingHp = updated.trainingHp,
                            trainingBp = updated.trainingBp, trainingAp = updated.trainingAp,
                            totalTrophies = updated.trophies))
                    }
                }
                changed = true
            }
            val character = db.userCharacterDao().getByIndividualIdSync(snapshot.individualId)
                .firstOrNull { it.characterType == DeviceType.APP }
            val individual = db.digimonIndividualDao().getIndividual(snapshot.individualId)
            if (definition != null && target != null && character != null) {
                val speciesChanged = character.charId != definition.id || individual?.speciesName != target.id
                if (speciesChanged) {
                    db.userCharacterDao().updateCharacter(character.copy(charId = definition.id,
                        exportCardCharacterId = if (character.exportCardCharacterId == character.charId)
                            definition.id else character.exportCardCharacterId))
                    db.digimonIndividualDao().updateSpeciesName(snapshot.individualId, target.id)
                    if (character.charId != definition.id) {
                        db.userCharacterDao().insertTransformationHistory(TransformationHistory(
                            monId = character.id, stageId = definition.id, transformationDate = clock()))
                    }
                    changed = true
                }
                if (existingMapping == null || (existingMapping.authority != "USER_CONFIRMED" &&
                        existingMapping.canonicalSpeciesId != target.id)) {
                    dao.saveMapping(NativeCanonicalMapping(snapshot.id, record.nativeSpeciesId, target.id,
                        canonicalAuthority, clock()))
                }
                dao.receipt(snapshot.individualId)?.let { receipt ->
                    if (receipt.snapshotId != snapshot.id || receipt.canonicalSpeciesId != target.id ||
                        receipt.definitionId != definition.id || receipt.characterId != character.id) {
                        dao.updateReceipt(receipt.copy(snapshotId = snapshot.id, canonicalSpeciesId = target.id,
                            definitionId = definition.id, characterId = character.id))
                        changed = true
                    }
                }
            }
            if (projection != null && (updated != null || (definition != null &&
                    (projection.stage != definition.stage || projection.baseHp != definition.baseHp ||
                        projection.baseBp != definition.baseBp || projection.baseAp != definition.baseAp ||
                        projection.nativeSnapshotId != snapshot.id)))) {
                val stats = updated
                val progress = db.appPartnerStateDao().state(snapshot.individualId)
                val latestCharacter = db.userCharacterDao().getByIndividualIdSync(snapshot.individualId)
                    .firstOrNull { it.characterType == DeviceType.APP }
                val stage = definition?.stage ?: reading.stage
                val baseHp = definition?.baseHp ?: reading.baseHp
                val baseBp = definition?.baseBp ?: reading.baseBp
                val baseAp = definition?.baseAp ?: reading.baseAp
                db.transferContinuityDao().saveProjection(projection.copy(nativeSnapshotId = snapshot.id,
                    stage = stage, baseHp = baseHp, baseBp = baseBp, baseAp = baseAp,
                    trainingHp = stats?.trainingHp ?: progress?.trainingHp ?: 0,
                    trainingAp = stats?.trainingAp ?: progress?.trainingAp ?: 0,
                    trainingBp = stats?.trainingBp ?: progress?.trainingBp ?: 0,
                    vitalPoints = latestCharacter?.vitalPoints ?: 0,
                    wins = latestCharacter?.totalBattlesWon ?: 0,
                    losses = latestCharacter?.totalBattlesLost ?: 0,
                    baselineDeltaId = db.transferContinuityDao().lastDelta(snapshot.individualId),
                    recordedAt = clock(), originBasisPoints = projection.originBasisPoints))
                changed = true
            }
        }
        changed
    }

    /**
     * Repairs app cards after an earlier DS evolution return stored the new native snapshot but left
     * the old card/species in place. User-confirmed mappings are authoritative and are never replaced.
     */
    suspend fun reconcileReturnedSpecies(): Int = withContext(Dispatchers.IO) {
        if (worldDsSpecies == null) return@withContext 0
        val bindings = db.nativeGameArchiveDao().allBindings().filter { it.gameId == WorldDsSaveReader.GAME_ID }
        if (bindings.isEmpty()) return@withContext 0
        val byId = bindings.associateBy { it.bindingId }
        var repaired = 0
        for (snapshot in db.nativeGameArchiveDao().latestSnapshots(bindings.map { it.bindingId })) {
            val binding = byId[snapshot.bindingId] ?: continue
            val character = db.userCharacterDao().getByIndividualIdSync(snapshot.individualId)
                .firstOrNull { it.characterType == DeviceType.APP } ?: continue
            val individual = db.digimonIndividualDao().getIndividual(snapshot.individualId) ?: continue
            val record = runCatching { gson.fromJson(snapshot.recordJson, ObservedDigimon::class.java) }.getOrNull()
                ?: continue
            val mapping = dao.mapping(snapshot.id)
            if (mapping?.authority == "USER_CONFIRMED" && mapping.nativeSpeciesId == record.nativeSpeciesId) continue
            val canonical = NativeSpeciesResolution.tableIdentity(binding.gameId, binding.formatProfile,
                snapshot.codecVersion, record.nativeSpeciesId, worldDsSpecies, catalog()) ?: continue
            if (character.charId == ExpandedCatalogRepository.definitionId(canonical) && individual.speciesName == canonical)
                continue
            val definition = runCatching { prepareDefinition(canonical) }.getOrNull() ?: continue
            db.withTransaction {
                val current = db.userCharacterDao().getByIndividualIdSync(snapshot.individualId)
                    .firstOrNull { it.characterType == DeviceType.APP } ?: return@withTransaction
                val speciesChanged = current.charId != definition.id || individual.speciesName != canonical
                if (speciesChanged) {
                    db.userCharacterDao().updateCharacter(current.copy(charId = definition.id,
                        exportCardCharacterId = if (current.exportCardCharacterId == current.charId)
                            definition.id else current.exportCardCharacterId))
                    db.digimonIndividualDao().updateSpeciesName(snapshot.individualId, canonical)
                    if (current.charId != definition.id) {
                        db.userCharacterDao().insertTransformationHistory(TransformationHistory(
                            monId = current.id, stageId = definition.id, transformationDate = clock()))
                    }
                }
                if (mapping?.authority != "USER_CONFIRMED") {
                    dao.saveMapping(NativeCanonicalMapping(snapshot.id, record.nativeSpeciesId, canonical,
                        "GAME_TABLE", clock()))
                }
                dao.receipt(snapshot.individualId)?.let { receipt ->
                    if (receipt.snapshotId != snapshot.id || receipt.canonicalSpeciesId != canonical ||
                        receipt.definitionId != definition.id || receipt.characterId != current.id) {
                        dao.updateReceipt(receipt.copy(snapshotId = snapshot.id, canonicalSpeciesId = canonical,
                            definitionId = definition.id, characterId = current.id))
                    }
                }
                db.transferContinuityDao().projection(snapshot.individualId)?.let { saved ->
                    if (saved.nativeSnapshotId != snapshot.id || saved.stage != definition.stage ||
                        saved.baseHp != definition.baseHp || saved.baseBp != definition.baseBp ||
                        saved.baseAp != definition.baseAp) {
                        db.transferContinuityDao().saveProjection(saved.copy(nativeSnapshotId = snapshot.id,
                            stage = definition.stage, baseHp = definition.baseHp, baseBp = definition.baseBp,
                            baseAp = definition.baseAp, recordedAt = clock()))
                    }
                }
            }
            repaired++
        }
        repaired
    }
}
