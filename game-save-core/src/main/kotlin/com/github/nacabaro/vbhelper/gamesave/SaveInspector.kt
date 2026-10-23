package com.github.nacabaro.vbhelper.gamesave

import java.nio.file.Files
import java.nio.file.Path

/** PC-side read-only inspection entry point. Prints counts only, not private names or candidate IDs. */
fun main(args: Array<String>) {
    require(args.size == 1 || (args.size == 3 && args[0] in setOf("--compare", "--preview-destination", "--write-rehearsal")) ||
        (args.size == 5 && args[0] == "--edit-rename") ||
        (args.size == 4 && args[0] == "--edit-activate") ||
        (args.size == 2 && args[0] == "--inspect-world-ds")) {
        "Usage: game-save-core <save-file> | --compare <before-save> <after-save> | --preview-destination <origin-save> <destination-save> | --inspect-world-ds <save-file> | --write-rehearsal <save-file> <output-dir> | --edit-rename <save-file> <output-dir> <party:N|bank:N> <name-file> | --edit-activate <save-file> <output-dir> <inactive:N>"
    }
    if (args.size == 2) {
        Files.newInputStream(Path.of(args[1])).use { WorldDsResearchInspector.inspect(it) }.summary().forEach(::println)
        return
    }
    fun read(path: String) = Files.newInputStream(Path.of(path)).use { SaveReaderRegistry().read(it) }
    if (args.size == 5 && args[0] == "--edit-rename") {
        editRenameSummary(args[1], args[2], args[3], args[4]).forEach(::println)
        return
    }
    if (args.size == 4 && args[0] == "--edit-activate") {
        editActivateSummary(args[1], args[2], args[3]).forEach(::println)
        return
    }
    if (args.size == 3 && args[0] == "--write-rehearsal") {
        writeRehearsalSummary(args[1], args[2]).forEach(::println)
        return
    }
    if (args.size == 3 && args[0] == "--preview-destination") {
        destinationPlanningSummary(read(args[1]), read(args[2])).forEach(::println)
        return
    }
    if (args.size == 3) {
        val result = IdentityEvidenceComparison.compare(read(args[1]), read(args[2]))
        println("same_revision=${result.sameRevision} candidate_pairs=${result.candidates.size} ambiguous_candidates=${result.ambiguousCandidates}")
        println("only_before=${result.onlyBefore} only_after=${result.onlyAfter} missing_before=${result.missingCandidatesBefore} missing_after=${result.missingCandidatesAfter}")
        println("review_before=${result.reviewRecordsBefore} review_after=${result.reviewRecordsAfter}")
        ObservedField.entries.forEach { field ->
            println("changed_${field.name.lowercase()}=${result.candidates.count { field in it.changedFields }}")
        }
        println("identity=unverified; candidate pairs do not establish continuity, ownership or source lineage")
        return
    }
    val observation = read(args[0])
    println("game=${observation.gameId} profile=${observation.formatProfile} codec=${observation.codecVersion}")
    println("active=${observation.activeCount} review=${observation.reviewCount}")
    SaveContainer.entries.forEach { container ->
        val records = observation.records.filter { it.container == container }
        println("${container.name.lowercase()}: active=${records.count { it.membership == RecordMembership.ACTIVE_RECORD }} review=${records.count { it.membership == RecordMembership.NEEDS_REVIEW }}")
    }
    println(if (observation.capabilities.provisionalSnapshotCopy)
        "capabilities=provisional-snapshot-copy; import-policy-restrictions-apply; identity-review-required; game-writes-disabled"
        else "capabilities=view-only; identity and numeric species mapping require validation")
}

/**
 * Desktop-only rung-2 candidate builder: applies one validated rename edit to a copy of the
 * document in the caller-provided output directory. It never addresses the source file, prints
 * no names (the name file supplies exact UTF-8 bytes) and reports aggregate counters only.
 */
internal fun editRenameSummary(sourceFile: String, outputDirectory: String, selector: String, nameFile: String): List<String> {
    val sourcePath = Path.of(sourceFile).toAbsolutePath().normalize()
    val output = Path.of(outputDirectory).toAbsolutePath().normalize()
    require(!output.startsWith(sourcePath.parent ?: sourcePath)) { "Output directory must be outside the source directory" }
    Files.createDirectories(output)

    val registry = SaveReaderRegistry()
    val captured = Files.newInputStream(sourcePath).use { registry.capture(it) }
    val observation = captured.observation
    val source = requireNotNull(captured.container) { "Edit requires exact source bytes" }.copyOf()
    captured.close()
    val recordKey = resolveSelector(observation, selector)
    val name = Files.readAllBytes(Path.of(nameFile))
    val candidate = try {
        TimeStrangerEdits.renameIndividual(source, recordKey, name)
    } finally {
        name.fill(0)
    }
    val candidatePath = output.resolve("edited-candidate.bin")
    Files.write(candidatePath, candidate)

    val decodedSource = TimeStrangerContainer.decode(source)
    val decodedCandidate = TimeStrangerContainer.decode(candidate)
    var diffBytes = 0
    var outsideWindow = 0
    val window = TimeStrangerEdits.NAME_WINDOW_BYTES
    val nameOffset = recordKey.substringAfter(':').toInt() + 4
    for (index in decodedCandidate.indices) {
        if (decodedCandidate[index] == decodedSource[index]) continue
        if (index in nameOffset until (nameOffset + window)) diffBytes++ else outsideWindow++
    }
    decodedSource.fill(0)
    decodedCandidate.fill(0)
    source.fill(0)
    candidate.fill(0)

    val reread = Files.newInputStream(candidatePath).use { registry.read(it) }
    val rereadRecord = reread.records.singleOrNull { it.recordKey == recordKey }
    return listOf(
        "edit=rename selector=$selector window_bytes=$window diff_bytes=$diffBytes diff_outside_window=$outsideWindow",
        "reread_name_present=${rereadRecord != null && rereadRecord.container != SaveContainer.FARM} records=${reread.records.size}",
        "writes_to_source=false output_files=1",
    )
}

/**
 * Desktop-only experimental candidate builder: flips the stored flag of one inactive
 * bank-tail record on a copy. Same output/refusal contract as the rename builder; the
 * candidate is an experiment awaiting in-game acceptance, not observed game output.
 */
internal fun editActivateSummary(sourceFile: String, outputDirectory: String, selector: String): List<String> {
    val sourcePath = Path.of(sourceFile).toAbsolutePath().normalize()
    val output = Path.of(outputDirectory).toAbsolutePath().normalize()
    require(!output.startsWith(sourcePath.parent ?: sourcePath)) { "Output directory must be outside the source directory" }
    Files.createDirectories(output)

    val registry = SaveReaderRegistry()
    val captured = Files.newInputStream(sourcePath).use { registry.capture(it) }
    val observation = captured.observation
    val source = requireNotNull(captured.container) { "Edit requires exact source bytes" }.copyOf()
    captured.close()
    val recordKey = resolveSelector(observation, selector)
    val candidate = TimeStrangerEdits.activateInactiveIndividual(source, recordKey)

    val candidatePath = output.resolve("edited-candidate.bin")
    Files.write(candidatePath, candidate)

    val decodedSource = TimeStrangerContainer.decode(source)
    val decodedCandidate = TimeStrangerContainer.decode(candidate)
    val flagOffset = recordKey.substringAfter(':').toInt() + 4 + 0x140
    var diffBytes = 0
    var outsideFlag = 0
    for (index in decodedCandidate.indices) {
        if (decodedCandidate[index] == decodedSource[index]) continue
        if (index in flagOffset until flagOffset + 4) diffBytes++ else outsideFlag++
    }
    decodedSource.fill(0)
    decodedCandidate.fill(0)
    val reread = Files.newInputStream(candidatePath).use { registry.read(it) }
    val rereadRecord = reread.records.singleOrNull { it.recordKey == recordKey }
    source.fill(0)
    candidate.fill(0)

    return listOf(
        "edit=activate selector=$selector flag_bytes=4 diff_bytes=$diffBytes diff_outside_window=$outsideFlag",
        "reread_active=${rereadRecord?.membership == RecordMembership.ACTIVE_RECORD} records=${reread.records.size}",
        "writes_to_source=false output_files=1",
    )
}

/** Selectors are resolved from the fresh observation, so container positions never depend on guesses. */
private fun resolveSelector(observation: SaveObservation, selector: String): String {
    val parts = selector.split(':')
    require(parts.size == 2 && parts[1].toIntOrNull() != null) { "Use party:N, bank:N or inactive:N" }
    val number = parts[1].toInt()
    return when (parts[0]) {
        "party", "bank" -> {
            val container = if (parts[0] == "party") SaveContainer.PARTY else SaveContainer.BANK
            observation.records.singleOrNull {
                it.container == container && it.position == number && it.membership == RecordMembership.ACTIVE_RECORD
            }?.recordKey ?: throw IllegalArgumentException("No active $selector record in this document")
        }
        "inactive" -> {
            val reactivatable = observation.records.filter {
                it.container == SaveContainer.BANK && it.issues == listOf(RecordIssue.INACTIVE_BOUNDARY)
            }
            require(number in 1..reactivatable.size) { "No inactive:$number record in this document" }
            reactivatable[number - 1].recordKey
        }
        else -> throw IllegalArgumentException("Use party:N, bank:N or inactive:N")
    }
}

/**
 * Desktop rehearsal of the section 11 pipeline on copies only: plans a byte-preserving rewrite,
 * verifies a backup, rechecks the source immediately before "writing", writes only into the
 * caller-provided output directory outside the source folder, then replays the journal through
 * readback verification. Formats with a container codec must re-encode their decoded bytes
 * byte-identically (rung 1); raw formats copy bytes directly. The game file itself is never
 * written. Hashes stay out of the output.
 */
internal fun writeRehearsalSummary(sourceFile: String, outputDirectory: String): List<String> {
    val sourcePath = Path.of(sourceFile).toAbsolutePath().normalize()
    val output = Path.of(outputDirectory).toAbsolutePath().normalize()
    require(!output.startsWith(sourcePath.parent ?: sourcePath)) { "Output directory must be outside the source directory" }
    Files.createDirectories(output)

    val registry = SaveReaderRegistry()
    val captured = Files.newInputStream(sourcePath).use { registry.capture(it) }
    val observation = captured.observation
    val sourceCopy = requireNotNull(captured.container) { "Rehearsal requires exact source bytes" }.copyOf()
    captured.close()
    val codecRoundTrip = observation.gameId == TimeStrangerSaveReader.GAME_ID
    val candidate = if (codecRoundTrip) {
        val decoded = TimeStrangerContainer.decode(sourceCopy)
        try { TimeStrangerContainer.encode(decoded) } finally { decoded.fill(0) }
    } else sourceCopy.copyOf()
    val roundTripVerified = candidate.contentEquals(sourceCopy)
    try {
        if (!roundTripVerified) {
            return recoverySummary(listOf(TransferJournalState.PREPARED, TransferJournalState.RECOVERY_REQUIRED),
                "codec-roundtrip-mismatch")
        }
        val plan = TransferCommitPlanBuilder.bytePreservingRewrite("rehearsal:${observation.gameId}", candidate, observation.contentRevision)
        val transitions = mutableListOf(TransferJournalState.PREPARED)
        var record = TransferJournalRecord(plan)

        val backupPath = output.resolve("rehearsal-original.backup")
        Files.write(backupPath, candidate)
        val backupVerified = revision(Files.readAllBytes(backupPath)) == plan.sourceRevision
        if (!backupVerified) return recoverySummary(transitions + TransferJournalState.RECOVERY_REQUIRED, "backup-mismatch")
        record = record.advance(TransferJournalState.BACKED_UP, backupRevision = plan.sourceRevision)
        transitions += TransferJournalState.BACKED_UP

        record = record.advance(TransferJournalState.CANDIDATE_READY)
        transitions += TransferJournalState.CANDIDATE_READY

        val preflight = Files.newInputStream(sourcePath).use { revision(readBoundedSaveBytes(it, registry.maxInputBytes)) }
        if (preflight != plan.sourceRevision) {
            return recoverySummary(transitions + TransferJournalState.RECOVERY_REQUIRED, "preflight-source-changed")
        }

        val candidatePath = output.resolve("rehearsal-candidate.bin")
        Files.write(candidatePath, candidate)
        record = record.advance(TransferJournalState.WRITTEN)
        transitions += TransferJournalState.WRITTEN

        val readback = Files.readAllBytes(candidatePath)
        val readbackVerified = revision(readback) == record.candidateRevision
        val reread = if (readbackVerified) Files.newInputStream(candidatePath).use { registry.read(it) } else null
        val rereadEquivalent = reread?.contentRevision == observation.contentRevision
        readback.fill(0)
        if (!readbackVerified || !rereadEquivalent) {
            return recoverySummary(transitions + TransferJournalState.RECOVERY_REQUIRED,
                if (!readbackVerified) "readback-mismatch" else "reread-mismatch")
        }
        record = record.advance(TransferJournalState.VERIFIED)
        transitions += TransferJournalState.VERIFIED
        record = record.advance(TransferJournalState.ACKNOWLEDGED)
        transitions += TransferJournalState.ACKNOWLEDGED

        return listOf(
            "rehearsal=verified documents=${plan.affectedDocuments.size} intent=${plan.intent.name.lowercase()}",
            "codec_roundtrip=${if (codecRoundTrip) "verified" else "not-applicable"}",
            "backup_verified=true preflight_source_unchanged=true readback_verified=true reread_equivalent=true",
            "journal=${transitions.joinToString(">") { it.name }}",
            "writes_to_source=false output_files=2 source_release_allowed=${record.sourceReleaseAllowed}",
        )
    } finally {
        candidate.fill(0)
        sourceCopy.fill(0)
    }
}

private fun recoverySummary(transitions: List<TransferJournalState>, detail: String): List<String> = listOf(
    "rehearsal=recovery_required reason=$detail",
    "journal=${transitions.joinToString(">") { it.name }}",
    "writes_to_source=false output_files=1",
)

/** Desktop planning probe uses synthetic local keys, never creates real app identities or import receipts. */
internal fun destinationPlanningSummary(origin: SaveObservation, destination: SaveObservation): List<String> {
    val records = origin.records.filter { it.container != SaveContainer.FARM &&
        it.membership == RecordMembership.ACTIVE_RECORD && it.issues.isEmpty() &&
        it.identityEvidenceKey() != null && !it.nativeRecordBase64.isNullOrBlank() }
    val previews = records.mapIndexed { index, record ->
        DestinationPreviewPlanner.preview(NativeTransferOrigin("probe:$index", "probe:$index", origin.contentRevision,
            origin.gameId, origin.formatProfile, origin.codecVersion, record, true), "probe-destination", destination, SaveContainer.BANK)
    }
    return buildList {
        add("planning_only=true eligible_main=${records.size} farm_excluded=${origin.records.count { it.container == SaveContainer.FARM }}")
        add("same_format=${previews.count { it.sameNativeFormat }} with_possible_matches=${previews.count { it.possibleNativeMatches > 0 }} executable=${previews.count { it.canExecute }}")
        DestinationBlocker.entries.forEach { blocker -> add("blocked_${blocker.name.lowercase()}=${previews.count { blocker in it.blockers }}") }
        add("writes=disabled; probe creates no Storage individuals, progress or transfer receipts")
    }
}
