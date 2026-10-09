package com.github.nacabaro.vbhelper.dtos

data class DigimonScanRewardDetails(
    val cardCharacterId: Long,
    val percentageBefore: Int,
    val percentageAfter: Int,
    val speciesName: String?,
    val charaIndex: Int,
    val cardName: String,
    val spriteIdle: ByteArray,
    val spriteWidth: Int,
    val spriteHeight: Int,
)
