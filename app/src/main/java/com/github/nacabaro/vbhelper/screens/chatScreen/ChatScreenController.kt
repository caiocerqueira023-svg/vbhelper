package com.github.nacabaro.vbhelper.screens.chatScreen

import com.github.nacabaro.vbhelper.domain.chat.ChatMessageEntity
import com.github.nacabaro.vbhelper.domain.species.SpeciesProfile
import kotlinx.coroutines.flow.Flow

interface ChatScreenController {
    fun getHistory(characterId: Long): Flow<List<ChatMessageEntity>>
    fun sendMessage(characterId: Long, text: String, onResult: (Result<String>) -> Unit)
    fun getSpeciesContext(characterId: Long, onResult: (SpeciesContext) -> Unit)
    fun saveManualSpeciesProfile(
        cardCharacterId: Long,
        name: String,
        level: String?,
        type: String?,
        profile: String?,
        specialMoves: List<String>,
        onSaved: () -> Unit
    )
}

data class SpeciesContext(
    val cardCharacterId: Long,
    val cardName: String,
    val existingProfile: SpeciesProfile?
)
