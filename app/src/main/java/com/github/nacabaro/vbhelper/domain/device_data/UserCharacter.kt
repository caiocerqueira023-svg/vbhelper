package com.github.nacabaro.vbhelper.domain.device_data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.github.cfogrady.vbnfc.data.NfcCharacter
import com.github.nacabaro.vbhelper.utils.DeviceType
import com.github.nacabaro.vbhelper.domain.card.CardCharacter
import com.github.nacabaro.vbhelper.domain.identity.IndividualIdentity

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = CardCharacter::class,
            parentColumns = ["id"],
            childColumns = ["charId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["charId"])]
)
/**
 * UserCharacter represents and instance of a character. The charId should map to a Character
 */
data class UserCharacter (
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Permanent app identity; physical-watch transfers use a separate one-time token. */
    val individualId: String = IndividualIdentity.generate(),
    var charId: Long,
    var ageInDays: Int,
    var mood: Int,
    var vitalPoints: Int,
    var transformationCountdown: Int,
    var injuryStatus: NfcCharacter.InjuryStatus,
    var trophies: Int,
    var currentPhaseBattlesWon: Int,
    var currentPhaseBattlesLost: Int,
    var totalBattlesWon: Int,
    var totalBattlesLost: Int,
    var activityLevel: Int,
    var heartRateCurrent: Int,
    var characterType: DeviceType,
    var isActive: Boolean
)
