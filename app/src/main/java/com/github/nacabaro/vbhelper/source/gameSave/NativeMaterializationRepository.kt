package com.github.nacabaro.vbhelper.source.gameSave

import androidx.room.withTransaction
import com.github.nacabaro.vbhelper.database.AppDatabase
import com.github.nacabaro.vbhelper.domain.card.CardCharacter
import com.github.nacabaro.vbhelper.domain.device_data.IndividualCharacters
import com.github.nacabaro.vbhelper.domain.device_data.TransformationHistory
import com.github.nacabaro.vbhelper.domain.species.SpeciesProfile
import com.github.nacabaro.vbhelper.domain.species.SpeciesSource
import com.github.nacabaro.vbhelper.domain.transfer.*
import com.github.nacabaro.vbhelper.gamesave.NdsStoryProfiles
import com.github.nacabaro.vbhelper.gamesave.ObservedDigimon
import com.github.nacabaro.vbhelper.gamesave.WorldDsSaveReader
import com.github.nacabaro.vbhelper.gamesave.WorldDsStatConversion
import com.github.nacabaro.vbhelper.gamesave.WorldDsSpeciesStages
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

    /** World DS layout gate; the conversion below is DS1-policy-specific until its own adapter lands. */
    private fun isWorldDsBinding(binding: ExternalIndividualBinding): Boolean =
        binding.gameId == NdsStoryProfiles.WORLD_DS.gameId &&
            binding.formatProfile == NdsStoryProfiles.WORLD_DS.formatProfile

    /**
     * First materialization of a World DS record seeds the app partner with the reverse-converted
     * values. Wrapped in the projection guard so the seed is never counted as fresh app progress.
     */
    private suspend fun applyWorldDsInitialStats(snapshot: NativeGameSnapshot, binding: ExternalIndividualBinding,
        definition: CardCharacter) {
        if (!isWorldDsBinding(binding)) return
        val record = gson.fromJson(snapshot.recordJson, ObservedDigimon::class.java)
        val native = WorldDsReceiveConversion.nativeStats(record) ?: return
        val reading = WorldDsAppStatsReader.read(db, snapshot.individualId) ?: return
        val converted = WorldDsStatConversion.toApp(native, reading.appStats.copy(
            stage = definition.stage.coerceIn(0, 5), baseHp = definition.baseHp, baseBp = definition.baseBp, baseAp = definition.baseAp))
        UniversalJourneyRepository(db).projection(snapshot.individualId, "world-ds-initial:${snapshot.id}") {
            val character = db.userCharacterDao().getByIndividualIdSync(snapshot.individualId)
                .let(IndividualCharacters::representative) ?: return@projection
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
        if (!isWorldDsBinding(binding)) return@withContext false
        val record = gson.fromJson(snapshot.recordJson, ObservedDigimon::class.java)
        val current = WorldDsReceiveConversion.nativeStats(record) ?: return@withContext false
        val reading = WorldDsAppStatsReader.read(db, snapshot.individualId)
        val projection = db.transferContinuityDao().projection(snapshot.individualId)
        val previous = projection?.takeIf { it.nativeSnapshotId != snapshot.id }?.let { saved ->
            dao.snapshot(saved.nativeSnapshotId)?.let { gson.fromJson(it.recordJson, ObservedDigimon::class.java) }
        }
        val previousStats = previous?.let(WorldDsReceiveConversion::nativeStats)
        val existingMapping = dao.mapping(snapshot.id)?.takeIf { it.nativeSpeciesId == record.nativeSpeciesId }
        val tableCanonical = NativeSpeciesResolution.tableIdentity(binding.gameId, binding.formatProfile,
            snapshot.codecVersion, record.nativeSpeciesId, worldDsSpecies, catalog())
        // The current native record is the authority on the form.
        // 1) If the game's own species table resolves a catalog species, use it (unless user explicitly confirmed otherwise).
        // 2) If the table has no entry for this DS form, create a synthetic definition for the DS name.
        // 3) Only fall back to a caller-supplied canonical if the table has no entry AND it's a user confirmation.
        val canonical: String? = when {
            tableCanonical != null && canonicalAuthority != "USER_CONFIRMED" -> tableCanonical
            tableCanonical != null -> tableCanonical
            canonicalSpeciesId != null && canonicalAuthority == "USER_CONFIRMED" -> canonicalSpeciesId
            else -> null
        }
        val target = canonical?.let { id -> runCatching { NativeCanonicalMappingPolicy.target(catalog(), id) }.getOrNull() }
        val definition = target?.let { species ->
            runCatching { prepareDefinition(species.id) }.getOrNull()
                ?: db.characterDao().getById(ExpandedCatalogRepository.definitionId(species.id))
                ?: ensureCatalogDefinition(species)
        }
        val dsName = worldDsSpecies?.name(record.nativeSpeciesId)
        val syntheticDefinition = if (definition == null && target == null && dsName != null) {
            ensureSyntheticDefinition(dsName, record.nativeSpeciesId, reading?.appStats)
        } else null
        val effectiveDefinition = definition ?: syntheticDefinition
        val effectiveTargetId = target?.id ?: syntheticDefinition?.speciesId
        val updated = reading?.let { appReading ->
            val bases = effectiveDefinition?.let {
                appReading.appStats.copy(stage = it.stage.coerceIn(0, 5), baseHp = it.baseHp,
                    baseBp = it.baseBp, baseAp = it.baseAp)
            } ?: appReading.appStats
            if (previousStats != null) {
                val delta = WorldDsStatConversion.applyDelta(previousStats, current, bases)
                if (previous.nativeSpeciesId != record.nativeSpeciesId) {
                    // A DS evolution may change the stored level; never let it erase the level the
                    // app partner had before the form change.
                    delta.copy(vitalPoints = maxOf(bases.vitalPoints,
                        WorldDsStatConversion.vitalPointsForLevel(current.level)))
                } else delta
            } else {
                // No checkpoint exists yet (for example a partner created outside the transfer
                // baseline). Seed from the native record exactly like first materialization instead
                // of leaving an unrelated app level behind.
                val seeded = WorldDsStatConversion.toApp(current, bases)
                bases.copy(trainingHp = seeded.trainingHp, trainingBp = seeded.trainingBp,
                    trainingAp = seeded.trainingAp, vitalPoints = seeded.vitalPoints, mood = seeded.mood,
                    trophies = seeded.trophies, wins = seeded.wins, losses = seeded.losses,
                    activityLevel = seeded.activityLevel)
            }
        }
        var changed = false
        db.withTransaction {
            if (updated != null) {
                UniversalJourneyRepository(db).projection(snapshot.individualId, "world-ds-return:${snapshot.id}") {
                    val characters = db.userCharacterDao().getByIndividualIdSync(snapshot.individualId)
                    val character = IndividualCharacters.representative(characters)
                        ?: return@projection
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
            val characters = db.userCharacterDao().getByIndividualIdSync(snapshot.individualId)
            val character = IndividualCharacters.representative(characters)
            val individual = db.digimonIndividualDao().getIndividual(snapshot.individualId)
            if (effectiveDefinition != null && character != null && effectiveTargetId != null) {
                // If the partner is associated with a DIM/BEM card, prefer the evolved form on that same card.
                // When the card has no such form, fall back to the non-DIM catalog definition.
                val currentDefinition = db.characterDao().getById(character.charId)
                val associatedCardId = currentDefinition?.cardId
                    ?: character.exportCardCharacterId?.let { db.characterDao().getById(it)?.cardId }
                val dimSlot = associatedCardId?.let { cardId ->
                    db.characterDao().getAllCharacters().firstOrNull { slot ->
                        slot.cardId == cardId &&
                            (slot.speciesId == effectiveTargetId || slot.catalogSpeciesId == effectiveTargetId)
                    }
                }
                // An APP partner keeps its catalog definition and only points its DIM export at the slot.
                // A device partner is the DIM slot itself, so it moves onto the matching slot when present.
                val isApp = character.characterType == DeviceType.APP
                val appliedDefinition = if (isApp) effectiveDefinition else (dimSlot ?: effectiveDefinition)
                val newExportTarget = if (isApp) {
                    when {
                        dimSlot != null -> dimSlot.id
                        character.exportCardCharacterId == null -> null
                        character.exportCardCharacterId == character.charId -> effectiveDefinition.id
                        else -> null
                    }
                } else character.exportCardCharacterId
                val speciesChanged = character.charId != appliedDefinition.id || individual?.speciesName != effectiveTargetId
                if (speciesChanged) {
                    db.userCharacterDao().updateCharacter(character.copy(charId = appliedDefinition.id,
                        exportCardCharacterId = newExportTarget))
                    db.digimonIndividualDao().updateSpeciesName(snapshot.individualId, effectiveTargetId)
                    if (character.charId != appliedDefinition.id) {
                        db.userCharacterDao().insertTransformationHistory(TransformationHistory(
                            monId = character.id, stageId = appliedDefinition.id, transformationDate = clock()))
                    }
                    changed = true
                }
                if (target != null && (existingMapping == null || (existingMapping.authority != "USER_CONFIRMED" &&
                        existingMapping.canonicalSpeciesId != effectiveTargetId))) {
                    dao.saveMapping(NativeCanonicalMapping(snapshot.id, record.nativeSpeciesId, effectiveTargetId,
                        canonicalAuthority, clock()))
                }
                dao.receipt(snapshot.individualId)?.let { receipt ->
                    if (receipt.snapshotId != snapshot.id || receipt.canonicalSpeciesId != effectiveTargetId ||
                        receipt.definitionId != effectiveDefinition.id || receipt.characterId != character.id) {
                        dao.updateReceipt(receipt.copy(snapshotId = snapshot.id, canonicalSpeciesId = effectiveTargetId,
                            definitionId = effectiveDefinition.id, characterId = character.id))
                        changed = true
                    }
                }
            }
            val stats = updated
            val progress = db.appPartnerStateDao().state(snapshot.individualId)
            val latestCharacter = db.userCharacterDao().getByIndividualIdSync(snapshot.individualId)
                .let(IndividualCharacters::representative)
            val stage = effectiveDefinition?.stage ?: reading?.stage ?: 0
            val baseHp = effectiveDefinition?.baseHp ?: reading?.baseHp ?: 0
            val baseBp = effectiveDefinition?.baseBp ?: reading?.baseBp ?: 0
            val baseAp = effectiveDefinition?.baseAp ?: reading?.baseAp ?: 0
            val needsProjection = stats != null || effectiveDefinition != null &&
                (projection == null || projection.stage != stage || projection.baseHp != baseHp ||
                    projection.baseBp != baseBp || projection.baseAp != baseAp ||
                    projection.nativeSnapshotId != snapshot.id)
            if (needsProjection) {
                val vitalPoints = latestCharacter?.vitalPoints ?: 0
                val wins = latestCharacter?.totalBattlesWon ?: 0
                val losses = latestCharacter?.totalBattlesLost ?: 0
                val trainingHp = stats?.trainingHp ?: progress?.trainingHp ?: 0
                val trainingAp = stats?.trainingAp ?: progress?.trainingAp ?: 0
                val trainingBp = stats?.trainingBp ?: progress?.trainingBp ?: 0
                val baselineDeltaId = db.transferContinuityDao().lastDelta(snapshot.individualId)
                if (projection == null) {
                    db.transferContinuityDao().saveProjection(NativeAppProjection(snapshot.individualId,
                        snapshot.id, WorldDsStatConversion.VERSION, "APP_RETURN_VALUES", stage, baseHp, baseBp,
                        baseAp, trainingHp, trainingAp, trainingBp, vitalPoints, wins, losses, baselineDeltaId,
                        clock(), progress?.originBasisPoints ?: 10000))
                } else {
                    db.transferContinuityDao().saveProjection(projection.copy(nativeSnapshotId = snapshot.id,
                        stage = stage, baseHp = baseHp, baseBp = baseBp, baseAp = baseAp,
                        trainingHp = trainingHp, trainingAp = trainingAp, trainingBp = trainingBp,
                        vitalPoints = vitalPoints, wins = wins, losses = losses,
                        baselineDeltaId = baselineDeltaId, recordedAt = clock(),
                        originBasisPoints = projection.originBasisPoints))
                }
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
        val bindings = db.nativeGameArchiveDao().allBindings().filter(::isWorldDsBinding)
        if (bindings.isEmpty()) return@withContext 0
        val byId = bindings.associateBy { it.bindingId }
        var repaired = 0
        for (snapshot in db.nativeGameArchiveDao().latestSnapshots(bindings.map { it.bindingId })) {
            val binding = byId[snapshot.bindingId] ?: continue
            val characters = db.userCharacterDao().getByIndividualIdSync(snapshot.individualId)
            val character = IndividualCharacters.representative(characters)
                ?: continue
            val individual = db.digimonIndividualDao().getIndividual(snapshot.individualId) ?: continue
            val record = runCatching { gson.fromJson(snapshot.recordJson, ObservedDigimon::class.java) }.getOrNull()
                ?: continue
            val mapping = dao.mapping(snapshot.id)
            if (mapping?.authority == "USER_CONFIRMED" && mapping.nativeSpeciesId == record.nativeSpeciesId) continue
            // Only a catalog-resolvable form can be repaired automatically. An unresolved name may
            // have been mapped by the user to a real species; never replace that with a placeholder.
            val canonical = NativeSpeciesResolution.tableIdentity(binding.gameId, binding.formatProfile,
                snapshot.codecVersion, record.nativeSpeciesId, worldDsSpecies, catalog()) ?: continue
            val definition = runCatching { prepareDefinition(canonical) }.getOrNull()
                ?: db.characterDao().getById(ExpandedCatalogRepository.definitionId(canonical))
                ?: ensureCatalogDefinition(requireNotNull(NativeCanonicalMappingPolicy.target(catalog(), canonical)))
            val speciesId = canonical
            val currentDefinition = db.characterDao().getById(character.charId)
            val associatedCardId = currentDefinition?.cardId
                ?: character.exportCardCharacterId?.let { db.characterDao().getById(it)?.cardId }
            val dimSlot = associatedCardId?.let { cardId ->
                db.characterDao().getAllCharacters().firstOrNull { slot ->
                    slot.cardId == cardId && (slot.speciesId == speciesId || slot.catalogSpeciesId == speciesId)
                }
            }
            val isApp = character.characterType == DeviceType.APP
            val appliedDefinition = if (isApp) definition else (dimSlot ?: definition)
            if (character.charId == appliedDefinition.id && individual.speciesName == speciesId) continue
            db.withTransaction {
                val current = db.userCharacterDao().getByIndividualIdSync(snapshot.individualId)
                    .let(IndividualCharacters::representative)
                    ?: return@withTransaction
                val newExportTarget = if (isApp) {
                    when {
                        dimSlot != null -> dimSlot.id
                        current.exportCardCharacterId == null -> null
                        current.exportCardCharacterId == current.charId -> appliedDefinition.id
                        else -> null
                    }
                } else current.exportCardCharacterId
                val speciesChanged = current.charId != appliedDefinition.id || individual.speciesName != speciesId
                if (speciesChanged) {
                    db.userCharacterDao().updateCharacter(current.copy(charId = appliedDefinition.id,
                        exportCardCharacterId = newExportTarget))
                    db.digimonIndividualDao().updateSpeciesName(snapshot.individualId, speciesId)
                    if (current.charId != appliedDefinition.id) {
                        db.userCharacterDao().insertTransformationHistory(TransformationHistory(
                            monId = current.id, stageId = appliedDefinition.id, transformationDate = clock()))
                    }
                }
                if (canonical != null && mapping?.authority != "USER_CONFIRMED") {
                    dao.saveMapping(NativeCanonicalMapping(snapshot.id, record.nativeSpeciesId, canonical,
                        "GAME_TABLE", clock()))
                }
                dao.receipt(snapshot.individualId)?.let { receipt ->
                    if (receipt.snapshotId != snapshot.id || receipt.canonicalSpeciesId != speciesId ||
                        receipt.definitionId != appliedDefinition.id || receipt.characterId != current.id) {
                        dao.updateReceipt(receipt.copy(snapshotId = snapshot.id, canonicalSpeciesId = speciesId,
                            definitionId = appliedDefinition.id, characterId = current.id))
                    }
                }
                db.transferContinuityDao().projection(snapshot.individualId)?.let { saved ->
                    if (saved.nativeSnapshotId != snapshot.id || saved.stage != appliedDefinition.stage ||
                        saved.baseHp != appliedDefinition.baseHp || saved.baseBp != appliedDefinition.baseBp ||
                        saved.baseAp != appliedDefinition.baseAp) {
                        db.transferContinuityDao().saveProjection(saved.copy(nativeSnapshotId = snapshot.id,
                            stage = appliedDefinition.stage, baseHp = appliedDefinition.baseHp,
                            baseBp = appliedDefinition.baseBp, baseAp = appliedDefinition.baseAp, recordedAt = clock()))
                    }
                }
            }
            repaired++
        }
        repaired
    }

    /**
     * Minimal catalog identity used when the normal artwork preparation is unavailable. The
     * deterministic catalog id and species binding let a later catalog pass upgrade it in place.
     */
    private suspend fun ensureCatalogDefinition(species: CanonicalSpeciesDto): CardCharacter {
        val id = ExpandedCatalogRepository.definitionId(species.id)
        db.characterDao().getById(id)?.let { return it }
        val stats = CatalogGameplayProfile.stats(species)
        val model = CardCharacter(
            id = id,
            cardId = null,
            spriteId = PlaceholderSprite.ID.toLong(),
            charaIndex = 0,
            stage = CatalogGameplayProfile.tier(species),
            attribute = com.github.cfogrady.vbnfc.data.NfcCharacter.Attribute.None,
            baseHp = stats.first,
            baseBp = stats.second,
            baseAp = stats.third,
            nameSprite = byteArrayOf(0xe0.toByte(), 7),
            nameWidth = 1,
            nameHeight = 1,
            speciesId = species.id,
            catalogSpeciesId = species.id
        )
        db.characterDao().insertCharacter(model)
        val inserted = requireNotNull(db.characterDao().getById(id))
        if (db.speciesProfileDao().getByCardCharacterId(id) == null) {
            db.speciesProfileDao().upsert(SpeciesProfile(id, species.name, species.name, species.level,
                species.type, source = SpeciesSource.OFFICIAL_MATCHED))
        }
        return inserted
    }

    /**
     * A genuine DS form the bundled catalog does not know still has to be shown as itself. The row is
     * persisted so the partner's character reference is valid; it never claims a catalog identity.
     */
    private suspend fun ensureSyntheticDefinition(dsName: String, nativeSpeciesId: Long,
        bases: WorldDsStatConversion.AppStats?): CardCharacter {
        val syntheticId = NativeSpeciesResolution.unresolvedIdentity(dsName)
        db.characterDao().getBySpeciesId(syntheticId)?.let { return it }
        val model = CardCharacter(
            id = 0,
            cardId = null,
            spriteId = PlaceholderSprite.ID.toLong(),
            charaIndex = 0,
            stage = WorldDsSpeciesStages.recordStage(nativeSpeciesId).coerceIn(0, 5),
            attribute = com.github.cfogrady.vbnfc.data.NfcCharacter.Attribute.None,
            baseHp = bases?.baseHp ?: 0,
            baseBp = bases?.baseBp ?: 0,
            baseAp = bases?.baseAp ?: 0,
            nameSprite = dsName.toByteArray(),
            nameWidth = dsName.length,
            nameHeight = 1,
            speciesId = syntheticId,
            catalogSpeciesId = null
        )
        val rowId = db.characterDao().insertCharacterReturning(model)
        val inserted = requireNotNull(db.characterDao().getById(rowId))
        if (db.speciesProfileDao().getByCardCharacterId(inserted.id) == null) {
            db.speciesProfileDao().upsert(SpeciesProfile(inserted.id, dsName, dsName, source = SpeciesSource.MANUAL))
        }
        return inserted
    }
}
