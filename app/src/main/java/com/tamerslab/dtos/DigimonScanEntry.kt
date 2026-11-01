package com.tamerslab.dtos

import androidx.room.Embedded

data class DigimonScanEntry(
    @Embedded val character: CharacterDtos.CardCharaProgress,
    val percentage: Int,
    val cardName: String,
    val speciesName: String?,
)
