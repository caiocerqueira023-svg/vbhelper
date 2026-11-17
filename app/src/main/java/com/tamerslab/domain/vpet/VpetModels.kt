package com.tamerslab.domain.vpet

import kotlinx.coroutines.flow.Flow

/**
 * One decoded V-Pet read, before any species identity is resolved.
 *
 * [modelKey] is the protocol wire key (`dm20`, `dmx`, `pen20`, `penz`, `dmc`, `penc`) and
 * [payloadHex] is the raw payload kept for display/logging only (PLAN.md D4). [valid] carries
 * the decoder checksum verdict; reads are persisted even when the species is unknown (D7).
 */
data class VpetReadSnapshot(
    val modelKey: String,
    val modelLabel: String,
    val version: Int,
    val index: Int,
    val name: String?,
    val stage: Int?,
    val attribute: Int?,
    val power: Int?,
    val payloadHex: String,
    val valid: Boolean,
    val readAtMillis: Long,
)

/** Why a decoded read could not become a Storage partner yet (PLAN.md D7). */
enum class VpetPendingReason {
    /** The bundled catalog does not know the decoded name; the user can map it later. */
    UNKNOWN_SPECIES,

    /** The species is known but the collection participation gate blocks the import. */
    PARTICIPATION_BLOCKED,
}

/**
 * A decoded read kept outside Room until the user maps it to a canonical species or discards it.
 * [blockedSpeciesName] preserves the decoded (or resolved) name shown as provenance.
 */
data class VpetPendingRead(
    val id: String,
    val snapshot: VpetReadSnapshot,
    val reason: VpetPendingReason,
    val blockedSpeciesName: String,
)

/** One selectable canonical species for manual mapping of a pending read. */
data class VpetSpeciesChoice(val canonicalId: String, val name: String, val level: String?)

/** Result of importing one decoded read into Storage as an APP partner (PLAN.md D3). */
sealed class VpetImportOutcome {
    data class Imported(
        val individualId: String,
        val characterId: Long,
        val speciesName: String?,
    ) : VpetImportOutcome()

    data class Pending(val pending: VpetPendingRead) : VpetImportOutcome()

    data class Failed(val message: String) : VpetImportOutcome()
}

/**
 * V-Pet read ingestion for the transfer feature.
 *
 * Imports decoded reads as APP partners with `IMPORTED` origin (D3), keeps reads whose species
 * cannot be resolved (or that the participation gate blocks) as pending entries in a DataStore
 * list (D7), and never deduplicates reads (D4: every import creates a new individual).
 *
 * Domain boundary only: implementations own Room/DataStore access; this interface stays free of
 * Android and Room types.
 */
interface VpetRepository {
    val pendingReads: Flow<List<VpetPendingRead>>

    suspend fun import(snapshot: VpetReadSnapshot): VpetImportOutcome

    suspend fun mapPending(id: String, canonicalId: String): VpetImportOutcome

    suspend fun discardPending(id: String)

    suspend fun searchSpecies(query: String, limit: Int = 20): List<VpetSpeciesChoice>
}
