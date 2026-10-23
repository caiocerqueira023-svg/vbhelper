package com.github.nacabaro.vbhelper.source

import com.github.nacabaro.vbhelper.database.AppDatabase
import com.github.nacabaro.vbhelper.domain.identity.TransferFingerprint
import com.github.nacabaro.vbhelper.domain.identity.WatchTransfer
import com.github.nacabaro.vbhelper.domain.identity.WatchTransferSafety
import com.github.nacabaro.vbhelper.domain.identity.WatchRepresentationSnapshot
import android.database.Cursor
import com.google.gson.Gson

class WatchTransferRepository(private val db: AppDatabase) {
    /** Capture received native values atomically with the import; retries keep the first baseline. */
    fun retainReceived(fingerprint: String, characterId: Long, character: com.github.cfogrady.vbnfc.data.NfcCharacter) {
        check(db.inTransaction())
        val source = requireNotNull(db.userCharacterDao().getCharacterSync(characterId))
        val observation = WatchTransfer.capture("receive:$fingerprint", source.individualId, character)
            .copy(sourceCharacterId = characterId)
        db.watchTransferDao().retainSnapshot(WatchRepresentationSnapshot(observation.token, source.individualId,
            characterId, observation.deviceType, observation.dimId, System.currentTimeMillis(),
            stateJson = captureState(observation), nativeJson = Gson().toJson(character)))
    }

    /** Durable before any NFC write. A failed/uncertain send keeps both source and receipt. */
    fun prepare(transfer: WatchTransfer) = db.runInTransaction {
        validateSource(transfer)
        val previous = db.watchTransferDao().getByIndividualId(transfer.individualId)
        check(previous == null || previous == transfer) { "An unfinished transfer already exists for this individual." }
        WatchTransferSafety.requireSingleExport(transfer, db.watchTransferDao().getPendingForWatch(transfer.deviceKey))
        val state = captureState(transfer)
        val retained = db.watchTransferDao().snapshot(transfer.token)
        if (retained == null) db.watchTransferDao().retainSnapshot(WatchRepresentationSnapshot(
            transfer.token, transfer.individualId, requireNotNull(transfer.sourceCharacterId), transfer.deviceType,
            transfer.dimId, System.currentTimeMillis(), stateJson = state))
        else check(retained.individualId == transfer.individualId && retained.version == 1 && retained.stateJson == state) {
            "The retained device baseline differs from the source. Its Storage representation has been preserved."
        }
        db.watchTransferDao().record(transfer)
    }

    /** Called only after the transport confirms success; independent of Compose/lifecycle. */
    fun complete(transfer: WatchTransfer) = db.runInTransaction {
        check(db.watchTransferDao().get(transfer.token) == transfer) { "Transfer receipt is missing or changed." }
        validateSource(transfer)
        val retained = requireNotNull(db.watchTransferDao().snapshot(transfer.token)) {
            "The source device baseline must be retained before completing this export."
        }
        check(retained.version == 1 && retained.individualId == transfer.individualId && retained.stateJson == captureState(transfer)) {
            "Device state changed after transfer preparation. Its Storage representation has been preserved."
        }
        db.userCharacterDao().deleteCharacterById(requireNotNull(transfer.sourceCharacterId))
    }

    /** Exact known fields in their original units; never a conversion or a growth award. */
    private fun captureState(transfer: WatchTransfer): String {
        check(db.inTransaction())
        val source = requireNotNull(db.userCharacterDao().getCharacterSync(requireNotNull(transfer.sourceCharacterId)))
        val sql = db.openHelper.readableDatabase
        fun rows(query: String, argument: Any?): List<Map<String, Any?>> = sql.query(query, arrayOf(argument)).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.columnNames.indices.associateTo(linkedMapOf()) { index ->
                    cursor.getColumnName(index) to when (cursor.getType(index)) {
                        Cursor.FIELD_TYPE_NULL -> null
                        Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index)
                        Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(index)
                        Cursor.FIELD_TYPE_BLOB -> java.util.Base64.getEncoder().encodeToString(cursor.getBlob(index))
                        else -> cursor.getString(index)
                    }
                })
            }
        }
        val tables = linkedMapOf(
            "UserCharacter" to rows("SELECT * FROM UserCharacter WHERE id=?", source.id),
            "CardCharacter" to rows("SELECT * FROM CardCharacter WHERE id=?", source.charId),
            "ExportCardCharacter" to rows("SELECT * FROM CardCharacter WHERE id=?", source.exportCardCharacterId),
            "VBCharacterData" to rows("SELECT * FROM VBCharacterData WHERE id=?", source.id),
            "BECharacterData" to rows("SELECT * FROM BECharacterData WHERE id=?", source.id),
            "AppPartnerProgress" to rows("SELECT * FROM AppPartnerProgress WHERE individualId=?", source.individualId),
            "TransformationHistory" to rows("SELECT * FROM TransformationHistory WHERE monId=? ORDER BY id", source.id),
            "VitalsHistory" to rows("SELECT * FROM VitalsHistory WHERE charId=? ORDER BY id", source.id),
            "SpecialMissions" to rows("SELECT * FROM SpecialMissions WHERE characterId=? ORDER BY id", source.id),
        )
        return Gson().toJson(mapOf("version" to 1, "tables" to tables))
    }

    private fun validateSource(transfer: WatchTransfer) {
        val source = db.userCharacterDao().getCharacterSync(requireNotNull(transfer.sourceCharacterId))
        check(source != null && source.individualId == transfer.individualId &&
            TransferFingerprint.of(source) == transfer.sourceFingerprint) {
            "The Digimon changed after transfer preparation. Its storage copy has been preserved."
        }
    }
}
