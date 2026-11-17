package com.tamerslab.transfer.vpet

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tamerslab.domain.vpet.VpetPendingRead
import com.tamerslab.domain.vpet.VpetPendingReason
import com.tamerslab.domain.vpet.VpetReadSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.decodeFromJsonElement
import java.util.UUID

private const val VPET_PENDING_READS_STORE_NAME = "vpet_pending_reads"

/** Preference key required by the implementation contract (PLAN.md D7). */
internal const val VPET_PENDING_READS_PREFERENCE = "vpet_pending_reads"

/**
 * Preferences DataStore holding the V-Pet reads that are waiting for manual species mapping.
 * Kept separate from Room so D7 needs no schema change or migration.
 */
val Context.vpetPendingReadsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = VPET_PENDING_READS_STORE_NAME
)

/**
 * JSON codec for the pending list.
 *
 * Decoding is deliberately forgiving: a missing, blank or structurally corrupt value yields an
 * empty list, and a malformed entry is dropped without losing the remaining reads. [toModel]
 * additionally drops rows whose reason this build no longer understands, so an older build can
 * still read a list written by a newer one.
 */
internal object VpetPendingReadCodec {
    private const val SCHEMA_VERSION = 1

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    fun encode(reads: List<VpetPendingRead>): String = json.encodeToString(reads.map(::toDto))

    fun decode(encoded: String?): List<VpetPendingRead> {
        val raw = encoded?.takeIf { it.isNotBlank() } ?: return emptyList()
        val rows = runCatching { json.parseToJsonElement(raw) }.getOrNull() as? JsonArray
            ?: return emptyList()
        return rows.mapNotNull { element ->
            runCatching { json.decodeFromJsonElement<VpetPendingReadDto>(element) }
                .getOrNull()
                ?.let(::toModel)
        }
    }

    private fun toDto(read: VpetPendingRead): VpetPendingReadDto = VpetPendingReadDto(
        schemaVersion = SCHEMA_VERSION,
        id = read.id,
        modelKey = read.snapshot.modelKey,
        modelLabel = read.snapshot.modelLabel,
        version = read.snapshot.version,
        index = read.snapshot.index,
        name = read.snapshot.name,
        stage = read.snapshot.stage,
        attribute = read.snapshot.attribute,
        power = read.snapshot.power,
        payloadHex = read.snapshot.payloadHex,
        valid = read.snapshot.valid,
        readAtMillis = read.snapshot.readAtMillis,
        reason = read.reason.name,
        blockedSpeciesName = read.blockedSpeciesName,
    )

    private fun toModel(dto: VpetPendingReadDto): VpetPendingRead? {
        if (dto.id.isBlank()) return null
        val reason = VpetPendingReason.entries.firstOrNull { it.name == dto.reason } ?: return null
        return VpetPendingRead(
            id = dto.id,
            snapshot = VpetReadSnapshot(
                modelKey = dto.modelKey,
                modelLabel = dto.modelLabel,
                version = dto.version,
                index = dto.index,
                name = dto.name,
                stage = dto.stage,
                attribute = dto.attribute,
                power = dto.power,
                payloadHex = dto.payloadHex,
                valid = dto.valid,
                readAtMillis = dto.readAtMillis,
            ),
            reason = reason,
            blockedSpeciesName = dto.blockedSpeciesName,
        )
    }
}

/** Persisted shape of one pending read; field names are the DataStore JSON schema. */
@Serializable
private data class VpetPendingReadDto(
    val schemaVersion: Int = 1,
    val id: String,
    val modelKey: String = "",
    val modelLabel: String = "",
    val version: Int = 0,
    val index: Int = 0,
    val name: String? = null,
    val stage: Int? = null,
    val attribute: Int? = null,
    val power: Int? = null,
    val payloadHex: String = "",
    val valid: Boolean = false,
    val readAtMillis: Long = 0L,
    val reason: String = "",
    val blockedSpeciesName: String = "",
)

/**
 * Persists [VpetPendingRead] entries as a single JSON preference.
 *
 * New reads are appended with a fresh UUID (D7); every read keeps its own provenance, so two
 * reads of the same species produce two entries (D4 has no dedupe at any layer).
 */
class VpetPendingReadStore(private val dataStore: DataStore<Preferences>) {

    val pendingReads: Flow<List<VpetPendingRead>> = dataStore.data
        .map { preferences -> VpetPendingReadCodec.decode(preferences[PENDING_READS_KEY]) }
        .distinctUntilChanged()

    /** Appends a new pending read and returns it with its generated id. */
    suspend fun add(
        snapshot: VpetReadSnapshot,
        reason: VpetPendingReason,
        blockedSpeciesName: String,
    ): VpetPendingRead {
        val pending = VpetPendingRead(
            id = UUID.randomUUID().toString(),
            snapshot = snapshot,
            reason = reason,
            blockedSpeciesName = blockedSpeciesName,
        )
        dataStore.edit { preferences ->
            val current = VpetPendingReadCodec.decode(preferences[PENDING_READS_KEY])
            preferences[PENDING_READS_KEY] = VpetPendingReadCodec.encode(current + pending)
        }
        return pending
    }

    /** Removes one pending read; a missing id is a no-op. */
    suspend fun remove(id: String) {
        dataStore.edit { preferences ->
            val current = VpetPendingReadCodec.decode(preferences[PENDING_READS_KEY])
            val remaining = current.filterNot { it.id == id }
            if (remaining.isEmpty()) {
                preferences.remove(PENDING_READS_KEY)
            } else {
                preferences[PENDING_READS_KEY] = VpetPendingReadCodec.encode(remaining)
            }
        }
    }

    suspend fun find(id: String): VpetPendingRead? =
        pendingReads.first().firstOrNull { it.id == id }

    private companion object {
        val PENDING_READS_KEY = stringPreferencesKey(VPET_PENDING_READS_PREFERENCE)
    }
}
