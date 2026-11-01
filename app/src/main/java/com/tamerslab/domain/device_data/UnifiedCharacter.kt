package com.tamerslab.domain.device_data

import com.tamerslab.utils.DeviceType

/**
 * UnifiedCharacter provides a single access point for common character data
 * and bridges the specific data for VB and BE characters.
 */
data class UnifiedCharacter(
    val userCharacter: UserCharacter,
    val vbData: VBCharacterData? = null,
    val beData: BECharacterData? = null,
    val appData: com.tamerslab.domain.species.AppPartnerProgress? = null,
) {
    val characterType: DeviceType get() = userCharacter.characterType

    fun isVB() = characterType == DeviceType.VBDevice
    fun isBE() = characterType == DeviceType.BEDevice
    fun isApp() = characterType == DeviceType.APP

    // Unified accessors for core stats
    val trophies: Int get() = userCharacter.trophies
    val ageInDays: Int get() = userCharacter.ageInDays
    val mood: Int get() = userCharacter.mood
    val vitalPoints: Int get() = userCharacter.vitalPoints
    val injuryStatus = userCharacter.injuryStatus
    
    // BE specific accessors with defaults
    val trainingHp: Int get() = if (isApp()) appData?.trainingHp ?: 0 else beData?.trainingHp ?: 0
    val trainingAp: Int get() = if (isApp()) appData?.trainingAp ?: 0 else beData?.trainingAp ?: 0
    val trainingBp: Int get() = if (isApp()) appData?.trainingBp ?: 0 else beData?.trainingBp ?: 0
    val generation: Int get() = if (isApp()) appData?.generation ?: 1 else vbData?.generation ?: 1
    val totalTrophies: Int get() = if (isApp()) appData?.totalTrophies ?: userCharacter.trophies else vbData?.totalTrophies ?: userCharacter.trophies
    val abilityRarity = beData?.abilityRarity
}
