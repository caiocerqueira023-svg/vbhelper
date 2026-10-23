package com.github.nacabaro.vbhelper.pccompanion

import com.github.nacabaro.vbhelper.companionprotocol.CompanionProtocol
import com.github.nacabaro.vbhelper.gamesave.*
import com.google.gson.Gson
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes

enum class ControlledChange { BASELINE, RESAVE, CROSS_SLOT, RENAME, PERSONALITY, REORDER, PARTY_BANK, FARM, EVOLUTION, PROGRESSION, DUPLICATES, RESTORE_BRANCH }
data class ControlledCaptureSummary(val session: String, val label: String, val sourceFileName: String, val expectedChange: ControlledChange,
    val gameId: String, val formatProfile: String, val codecVersion: Int, val contentRevision: String, val byteCount: Int,
    val capturedAt: Long, val activeCandidates: Int, val reviewRecords: Int)
data class ControlledComparisonReport(val gameId: String, val formatProfile: String, val codecVersion: Int,
    val expectedChange: ControlledChange, val expectedChangeObserved: Boolean, val identicalDocuments: Boolean,
    val activeCandidatePairs: Int, val ambiguousCandidates: Int, val researchUniqueCandidatePairs: Int,
    val researchFarmPersistenceCandidates: Int, val duplicateCandidateValuesBefore: Int, val duplicateCandidateValuesAfter: Int,
    val missingIdentityRecordsBefore: Int, val missingIdentityRecordsAfter: Int, val renamedCandidates: Int,
    val personalityChangedCandidates: Int, val speciesChangedCandidates: Int, val reorderedCandidates: Int,
    val containerChangedCandidates: Int, val progressionChangedCandidates: Int, val regressionCandidates: Int,
    val activeBefore: Int, val activeAfter: Int, val reviewBefore: Int, val reviewAfter: Int,
    val nativeRecordBytesChangedCandidates: Int,
    val analysisDiffersFromCapture: Boolean,
    val establishesNativeIdentity: Boolean = false)

/** Private game-written evidence captures only. No source edits, native markers or ownership promotion. */
class ControlledSamples(private val state: Path, private val registry: SaveReaderRegistry = SaveReaderRegistry()) {
    private val gson = Gson()
    private val segment = Regex("[A-Za-z0-9_-]{1,64}")
    private fun directory(session: String, create: Boolean): Path {
        require(segment.matches(session))
        if (create) Files.createDirectories(state)
        val base = state.toRealPath()
        val captures = base.resolve("captures")
        require(!Files.isSymbolicLink(captures))
        if (create && !Files.exists(captures)) Files.createDirectory(captures)
        val directory = captures.resolve(session)
        require(!Files.isSymbolicLink(directory))
        if (create && !Files.exists(directory)) Files.createDirectory(directory)
        require(directory.toRealPath().startsWith(base))
        return directory
    }
    private fun path(session: String, label: String, suffix: String, create: Boolean = false): Path {
        require(segment.matches(label))
        return directory(session, create).resolve("capture-$label.$suffix")
    }

    fun capture(roots: List<Path>, session: String, label: String, sourceFileName: String, expected: ControlledChange): ControlledCaptureSummary {
        require(roots.size == 1) { "A capture needs one explicit source root" }
        require(sourceFileName.matches(Regex("[0-9]{4}\\.bin", RegexOption.IGNORE_CASE)))
        val root = roots.single().toRealPath()
        val intendedState = state.toAbsolutePath().normalize()
        var ancestor = intendedState
        while (!Files.exists(ancestor)) ancestor = requireNotNull(ancestor.parent)
        val physicalState = ancestor.toRealPath().resolve(ancestor.relativize(intendedState)).normalize()
        require(!physicalState.startsWith(root)) { "Keep private capture state outside the active save folder" }
        val destination = path(session, label, "bin", true)
        val metadata = path(session, label, "json")
        require(!Files.exists(destination) && !Files.exists(metadata)) { "Capture labels are immutable; choose a new label" }
        val source = root.resolve(sourceFileName)
        require(!Files.isSymbolicLink(source))
        val real = source.toRealPath()
        require(real.startsWith(root) && Files.isRegularFile(real))
        val before = Files.readAttributes(real, BasicFileAttributes::class.java)
        val captured = Files.newInputStream(real, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS).use(registry::capture)
        captured.use {
            val after = Files.readAttributes(real, BasicFileAttributes::class.java)
            require(before.size() == after.size() && before.lastModifiedTime() == after.lastModifiedTime() && before.fileKey() == after.fileKey()) { "Save changed during capture" }
            val observation = captured.observation
            val bytes = requireNotNull(captured.container)
            val summary = ControlledCaptureSummary(session, label, sourceFileName, expected, observation.gameId,
                observation.formatProfile, observation.codecVersion, observation.contentRevision, bytes.size,
                System.currentTimeMillis(), observation.activeCount, observation.reviewCount)
            Files.write(destination, bytes, StandardOpenOption.CREATE_NEW)
            try { Files.write(metadata, gson.toJson(summary).toByteArray(Charsets.UTF_8), StandardOpenOption.CREATE_NEW) }
            catch (failure: Exception) { Files.deleteIfExists(destination); throw failure }
            return summary
        }
    }

    fun summary(session: String, label: String): ControlledCaptureSummary = Files.newInputStream(path(session, label, "json"), StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS).use {
        gson.fromJson(CompanionProtocol.bounded(it, 16384).toString(Charsets.UTF_8), ControlledCaptureSummary::class.java).also { info ->
            require(info.session == session && info.label == label && info.byteCount in 1..registry.maxInputBytes)
        }
    }

    private fun read(session: String, label: String): Pair<ControlledCaptureSummary, SaveObservation> {
        val metadata = summary(session, label)
        val file = path(session, label, "bin")
        require(!Files.isSymbolicLink(file))
        val observation = Files.newInputStream(file, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS).use(registry::read)
        require(observation.contentRevision == metadata.contentRevision && Files.size(file) == metadata.byteCount.toLong() &&
            observation.gameId == metadata.gameId) { "Captured evidence does not match its manifest" }
        return metadata to observation
    }

    fun compare(session: String, beforeLabel: String, afterLabel: String): ControlledComparisonReport {
        val (beforeInfo, before) = read(session, beforeLabel)
        val (afterInfo, after) = read(session, afterLabel)
        val safe = IdentityEvidenceComparison.compare(before, after)
        fun groups(observation: SaveObservation) = observation.records.filter { it.identityEvidenceKey() != null }.groupBy { requireNotNull(it.identityEvidenceKey()) }
        val old = groups(before)
        val new = groups(after)
        // Research-only matching also inspects farm/review rows. It never turns these into owned/verified bindings.
        val pairs = (old.keys intersect new.keys).filter { old.getValue(it).size == 1 && new.getValue(it).size == 1 }
            .map { old.getValue(it).single() to new.getValue(it).single() }
        val renamed = pairs.count { (a,b) -> a.storedName != b.storedName }
        val personalities = pairs.count { (a,b) -> a.personalityId != b.personalityId }
        val species = pairs.count { (a,b) -> a.nativeSpeciesId != b.nativeSpeciesId }
        val reordered = pairs.count { (a,b) -> a.position != b.position || a.recordKey != b.recordKey }
        val containers = pairs.count { (a,b) -> a.container != b.container }
        val farms = pairs.count { (a,b) -> a.container != b.container && (a.container == SaveContainer.FARM || b.container == SaveContainer.FARM) }
        val progressed = pairs.count { (a,b) -> a.level != b.level || a.totalExp != b.totalExp || a.talentRaw != b.talentRaw || a.bondRaw != b.bondRaw }
        val regressions = pairs.count { (a,b) ->
            val oldExp = a.totalExp
            val newExp = b.totalExp
            b.level < a.level || (oldExp != null && newExp != null && newExp < oldExp)
        }
        val rawChanged = pairs.count { (a,b) -> a.nativeRecordBase64 != b.nativeRecordBase64 }
        val expectedObserved = when (afterInfo.expectedChange) {
            ControlledChange.BASELINE -> true
            ControlledChange.RESAVE -> pairs.isNotEmpty() && renamed == 0 && personalities == 0 && species == 0 && progressed == 0 && rawChanged == 0
            ControlledChange.CROSS_SLOT -> beforeInfo.sourceFileName != afterInfo.sourceFileName && pairs.isNotEmpty()
            ControlledChange.RENAME -> renamed > 0
            ControlledChange.PERSONALITY -> personalities > 0
            ControlledChange.REORDER -> reordered > 0
            ControlledChange.PARTY_BANK -> pairs.any { (a,b) -> setOf(a.container,b.container) == setOf(SaveContainer.PARTY,SaveContainer.BANK) }
            ControlledChange.FARM -> farms > 0
            ControlledChange.EVOLUTION -> species > 0
            ControlledChange.PROGRESSION -> progressed > 0
            ControlledChange.DUPLICATES -> after.records.groupingBy { it.nativeSpeciesId }.eachCount().any { it.value > 1 }
            ControlledChange.RESTORE_BRANCH -> regressions > 0
        }
        return ControlledComparisonReport(before.gameId, before.formatProfile, before.codecVersion, afterInfo.expectedChange,
            expectedObserved, safe.sameRevision, safe.candidates.size, safe.ambiguousCandidates, pairs.size, farms,
            old.values.count { it.size > 1 }, new.values.count { it.size > 1 }, safe.missingCandidatesBefore, safe.missingCandidatesAfter,
            renamed, personalities, species, reordered, containers, progressed, regressions, before.activeCount, after.activeCount,
            before.reviewCount, after.reviewCount, rawChanged,
            beforeInfo.codecVersion != before.codecVersion || afterInfo.codecVersion != after.codecVersion ||
                beforeInfo.formatProfile != before.formatProfile || afterInfo.formatProfile != after.formatProfile)
    }
}
