package com.tamerslab.digiline.native

import com.tamerslab.domain.personality.DigimonPersonalityType
import com.tamerslab.gamesave.RecordMembership
import com.tamerslab.gamesave.SaveContainer
import com.tamerslab.gamesave.TimeStrangerEdits
import com.tamerslab.gamesave.TimeStrangerSaveReader

/**
 * Mapping between the app's 16 social personality types and the Time Stranger save personality
 * byte (1..16 at `nameOffset + 0xEE`).
 *
 * Evidence: the controlled fixtures used by the transfer tests map native id 16 to COMPASSIONATE
 * and native id 14 to FRIENDLY, which matches the declaration order of
 * [DigimonPersonalityType]. The identity mapping is therefore the only mapping supported today;
 * it is kept in one place so a future verified remap never duplicates logic. If the game mapping
 * is ever re-verified as non-identity, change this object only.
 */
object NativePersonalityMapping {

    /** True when a game-side mapping has been independently verified for the shipped build. */
    const val MAPPING_VERIFIED: Boolean = true

    fun toNativeId(type: DigimonPersonalityType): Int? =
        if (MAPPING_VERIFIED) type.number.takeIf { it in 1..16 } else null

    fun fromNativeId(nativeId: Int?): DigimonPersonalityType? =
        nativeId?.let { id -> DigimonPersonalityType.entries.firstOrNull { it.number == id } }
}

/** Result of preparing one pending app-side personality shift for the native save. */
sealed interface NativePersonalitySyncOutcome {
    data class Ready(val candidateBytes: ByteArray, val nativeId: Int) : NativePersonalitySyncOutcome

    enum class Refusal {
        MAPPING_UNVERIFIED,
        RECORD_NOT_FOUND,
        RECORD_NOT_EDITABLE,
        FARM_RECORD_UNSUPPORTED,
        NATIVE_ALREADY_TARGET,
        NATIVE_DOCUMENT_INVALID,
        INTERNAL_ERROR,
    }

    data class Refused(val reason: Refusal, val detail: String? = null) : NativePersonalitySyncOutcome
}

/**
 * Pure preparation step for the "app shift reaches the game" decision (D4).
 *
 * It never writes files: it takes the exact source document bytes and one record key and returns a
 * verified candidate produced by [TimeStrangerEdits.setPersonality]. The caller owns the guarded
 * write flow (journal, backup, readback) and decides when a real save write happens.
 */
object TimeStrangerPersonalitySync {

    fun prepare(
        sourceBytes: ByteArray,
        recordKey: String,
        target: DigimonPersonalityType
    ): NativePersonalitySyncOutcome {
        val nativeId = NativePersonalityMapping.toNativeId(target)
            ?: return NativePersonalitySyncOutcome.Refused(NativePersonalitySyncOutcome.Refusal.MAPPING_UNVERIFIED)

        val observation = try {
            TimeStrangerSaveReader().read(sourceBytes)
        } catch (failure: Exception) {
            return NativePersonalitySyncOutcome.Refused(
                NativePersonalitySyncOutcome.Refusal.NATIVE_DOCUMENT_INVALID,
                failure.message
            )
        }
        val record = observation.records.singleOrNull { it.recordKey == recordKey }
            ?: return NativePersonalitySyncOutcome.Refused(NativePersonalitySyncOutcome.Refusal.RECORD_NOT_FOUND)
        if (record.container == SaveContainer.FARM) {
            return NativePersonalitySyncOutcome.Refused(NativePersonalitySyncOutcome.Refusal.FARM_RECORD_UNSUPPORTED)
        }
        if (record.membership != RecordMembership.ACTIVE_RECORD) {
            return NativePersonalitySyncOutcome.Refused(NativePersonalitySyncOutcome.Refusal.RECORD_NOT_EDITABLE)
        }
        if (record.personalityId == nativeId) {
            return NativePersonalitySyncOutcome.Refused(NativePersonalitySyncOutcome.Refusal.NATIVE_ALREADY_TARGET)
        }

        return try {
            NativePersonalitySyncOutcome.Ready(
                candidateBytes = TimeStrangerEdits.setPersonality(sourceBytes, recordKey, nativeId),
                nativeId = nativeId
            )
        } catch (failure: Exception) {
            NativePersonalitySyncOutcome.Refused(
                NativePersonalitySyncOutcome.Refusal.INTERNAL_ERROR,
                failure.message
            )
        }
    }
}
