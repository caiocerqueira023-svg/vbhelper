package com.tamerslab.digiline.freechat.gateway

import com.tamerslab.digiline.freechat.data.FreeChatRepository

/**
 * Default, fully offline Free Talk gateway (contract §3/§4).
 *
 * Thin delegate over [FreeChatRepository]: the repository owns content loading, the authored engine,
 * persistence-before-display and the conservative mood credit; the gateway exists so the UI/DI
 * depends on a stable contract instead of the data layer.
 */
class ScriptedFreeChatGateway(
    private val repository: FreeChatRepository,
) : FreeChatGateway {

    override fun isAvailable(): Boolean = repository.isAvailable()

    override suspend fun open(characterId: Long): FreeChatResult = repository.open(characterId)

    override suspend fun send(characterId: Long, text: String): FreeChatResult =
        repository.send(characterId, text)

    override suspend fun clear(characterId: Long) {
        repository.clear(characterId)
    }
}
