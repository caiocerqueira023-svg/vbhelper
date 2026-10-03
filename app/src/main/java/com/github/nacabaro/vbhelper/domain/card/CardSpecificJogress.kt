package com.github.nacabaro.vbhelper.domain.card

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    primaryKeys = ["fromCharaId", "toCharaId", "partnerCardNumber", "partnerCharaIndex"],
    foreignKeys = [
        ForeignKey(entity = CardCharacter::class, parentColumns = ["id"], childColumns = ["fromCharaId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = CardCharacter::class, parentColumns = ["id"], childColumns = ["toCharaId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("fromCharaId"), Index("toCharaId")],
)
data class CardSpecificJogress(
    val fromCharaId: Long,
    val toCharaId: Long,
    /** Device DiM/BEM number, not the app's local Card row ID. */
    val partnerCardNumber: Int,
    val partnerCharaIndex: Int,
)
