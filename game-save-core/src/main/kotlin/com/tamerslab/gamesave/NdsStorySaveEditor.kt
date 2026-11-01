package com.tamerslab.gamesave

/**
 * A revision-bound candidate produced by an [NdsStorySaveEditor]. The editor never writes a file
 * and never mutates caller-owned bytes; a writer layer owns backups, journals and readback.
 */
interface NdsStoryPendingEdit {
    val sourceRevision: String
    val candidateRevision: String
    val recordKey: String
    val candidateBytes: ByteArray
}

/**
 * Per-layout native editor seam. Writer repositories resolve one through [NdsStorySaveEditors]
 * instead of hardcoding a game id, so a second layout can never reach the World DS byte windows by
 * accident. [Ds1SaveEditor] wraps the audited World DS implementation unchanged.
 */
interface NdsStorySaveEditor {
    val profile: NdsStoryProfile

    fun read(bytes: ByteArray): SaveObservation
    fun removeRecord(source: ByteArray, observedRevision: String, recordKey: String): NdsStoryPendingEdit
    fun insertRetained(destination: ByteArray, observedRevision: String, recordBase64: String): NdsStoryPendingEdit
    fun insertGenerated(destination: ByteArray, observedRevision: String, recordBase64: String): NdsStoryPendingEdit
    fun replaceRetained(destination: ByteArray, observedRevision: String, recordBase64: String): NdsStoryPendingEdit
    fun admitToParty(source: ByteArray, observedRevision: String, recordKey: String): NdsStoryPendingEdit
    fun restore(candidate: ByteArray, edit: NdsStoryPendingEdit): ByteArray
}

/** Resolves the audited editor for one layout. A layout without a verified editor returns null. */
object NdsStorySaveEditors {
    val all: List<NdsStorySaveEditor> = listOf(Ds1SaveEditor)

    fun forProfile(profile: NdsStoryProfile): NdsStorySaveEditor? =
        all.firstOrNull { it.profile.gameId == profile.gameId && it.profile.formatProfile == profile.formatProfile &&
            it.profile.codecVersion == profile.codecVersion }

    fun forObservation(observation: SaveObservation): NdsStorySaveEditor? =
        NdsStoryProfiles.forObservation(observation)?.let(::forProfile)
}

/** World DS editor facade over the audited [WorldDsPartyEdits] byte windows. */
object Ds1SaveEditor : NdsStorySaveEditor {
    override val profile: NdsStoryProfile get() = NdsStoryProfiles.WORLD_DS

    override fun read(bytes: ByteArray): SaveObservation = WorldDsSaveReader().read(bytes)

    override fun removeRecord(source: ByteArray, observedRevision: String, recordKey: String): NdsStoryPendingEdit =
        WorldDsPartyEdits.removeForResearch(source, observedRevision, recordKey)

    override fun insertRetained(destination: ByteArray, observedRevision: String, recordBase64: String): NdsStoryPendingEdit =
        WorldDsPartyEdits.insertRetainedRecord(destination, observedRevision, recordBase64)

    override fun insertGenerated(destination: ByteArray, observedRevision: String, recordBase64: String): NdsStoryPendingEdit =
        WorldDsPartyEdits.insertGeneratedRecord(destination, observedRevision, recordBase64)

    override fun replaceRetained(destination: ByteArray, observedRevision: String, recordBase64: String): NdsStoryPendingEdit =
        WorldDsPartyEdits.replaceRetainedRecord(destination, observedRevision, recordBase64)

    override fun admitToParty(source: ByteArray, observedRevision: String, recordKey: String): NdsStoryPendingEdit =
        WorldDsPartyEdits.admitStorageRecordToParty(source, observedRevision, recordKey)

    override fun restore(candidate: ByteArray, edit: NdsStoryPendingEdit): ByteArray = when (edit) {
        is WorldDsResearchRemoval -> WorldDsPartyEdits.restoreResearchRemoval(candidate, edit)
        is WorldDsResearchInsertion -> WorldDsPartyEdits.restoreResearchInsertion(candidate, edit)
        is WorldDsResearchReplacement -> WorldDsPartyEdits.restoreResearchReplacement(candidate, edit)
        is WorldDsResearchAdmission -> WorldDsPartyEdits.restoreResearchAdmission(candidate, edit)
        else -> throw IllegalArgumentException("Edit does not belong to the World DS layout")
    }
}
