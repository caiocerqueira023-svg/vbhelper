package com.github.nacabaro.vbhelper.domain.identity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.github.cfogrady.vbnfc.data.NfcCharacter
import com.github.cfogrady.vbnfc.vb.VBNfcCharacter

/** A legacy individual ID or an unknown/replayed token must never restore identity. */
fun resolveReturningIndividual(
    character: NfcCharacter,
    findTransfer: (String) -> WatchTransfer?,
    individualExists: (String) -> Boolean,
    isPresentLocally: (String) -> Boolean,
): String? {
    val token = IndividualIdentity.decode(character.appReserved1) ?: return null
    val transfer = findTransfer(token) ?: return null
    return transfer.individualId.takeIf {
        transfer.token == token && transfer.matches(character) &&
            individualExists(it) && !isPresentLocally(it)
    }
}

/** A single successful export, consumed when its character returns. */
@Entity(indices = [Index(value = ["individualId"], unique = true)])
data class WatchTransfer(
    @PrimaryKey val token: String,
    val individualId: String,
    val deviceType: Int,
    val dimId: Int,
    val generation: Int?,
    val ageInDays: Int,
    val totalBattlesWon: Int,
    val totalBattlesLost: Int,
    val history: String,
    val sourceCharacterId: Long? = null,
    val cardId: Long? = null,
    val sourceFingerprint: String? = null,
    val deviceKey: String = "",
) {
    fun matches(character: NfcCharacter): Boolean {
        val incoming = capture(token, individualId, character)
        // Reserved app bytes can survive death/rebirth. Require continuity in the
        // actual character data as well; species alone never establishes identity.
        return deviceType == incoming.deviceType && dimId == incoming.dimId &&
            generation == incoming.generation && ageInDays <= incoming.ageInDays &&
            totalBattlesWon <= incoming.totalBattlesWon && totalBattlesLost <= incoming.totalBattlesLost &&
            history.isNotEmpty() && incoming.history.startsWith(history) &&
            character.transformationHistory.lastOrNull { it.toCharIndex != UByte.MAX_VALUE }
                ?.toCharIndex?.toInt() == character.charIndex.toInt()
    }

    companion object {
        fun capture(token: String, individualId: String, character: NfcCharacter) = WatchTransfer(
            token = token,
            individualId = individualId,
            deviceType = character.getMatchingDeviceTypeId().toInt(),
            dimId = character.dimId.toInt(),
            generation = (character as? VBNfcCharacter)?.generation?.toInt(),
            ageInDays = character.ageInDays.toInt() and 0xFF,
            totalBattlesWon = character.totalBattlesWon.toInt(),
            totalBattlesLost = character.totalBattlesLost.toInt(),
            history = character.transformationHistory
                .filter { it.toCharIndex != UByte.MAX_VALUE }
                .joinToString("") { "${it.toCharIndex}:${it.year}:${it.month}:${it.day};" },
        )
    }
}
