package com.github.nacabaro.vbhelper.dtos

/** CardCharacter IDs, not the device's character indices or card numbers. */
data class CardEvolutionLink(
    val fromId: Long,
    val toId: Long,
    val fusionAttribute: String? = null,
    val isJogress: Boolean = fusionAttribute != null,
)

data class CardEvolutionGraph(
    val cardId: Long,
    val characters: List<CharacterDtos.CardCharaProgress>,
    val links: List<CardEvolutionLink>,
)

data class CardSpecificJogressDetails(
    val charaId: Long,
    val spriteIdle: ByteArray,
    val spriteWidth: Int,
    val spriteHeight: Int,
    val discoveredOn: Long?,
    val partnerCardNumber: Int,
    val partnerCharaIndex: Int,
)
