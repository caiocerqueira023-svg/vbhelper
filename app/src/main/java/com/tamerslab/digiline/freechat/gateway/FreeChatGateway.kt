package com.tamerslab.digiline.freechat.gateway

import com.tamerslab.digiline.freechat.model.FreeChatChip

/**
 * Render-ready Free Talk scene (contract §3).
 *
 * The gateway/repository returns only this Android-free shape; UI layers render [lines], [chips] and
 * the optional [closeReason] notice. `closeReason == "SAFETY"` means the session was ended by the
 * safety filter (S2/S3/PII) and the UI shows the authored closed-session notice.
 *
 * @property lines assistant lines of the just-persisted turn (already written to history)
 * @property chips follow-up chip suggestions (max 3); empty on a safety close
 * @property closeReason `null` for a normal turn, `"SAFETY"` when the session ended
 * @property partnerTone authored tone of the reply (`warm|dry|courage|...`), when known
 */
data class FreeChatSceneView(
    val lines: List<String> = emptyList(),
    val chips: List<FreeChatChip> = emptyList(),
    val closeReason: String? = null,
    val partnerTone: String? = null,
)

/** Result of a Free Talk gateway action; [error] is a short, non-echoing message for the UI. */
data class FreeChatResult(
    val scene: FreeChatSceneView? = null,
    val error: String? = null,
)

/**
 * Authored, fully offline Free Talk façade used by the chat screen (contract §3/§4).
 *
 * The default implementation routes to [com.tamerslab.digiline.freechat.data.FreeChatRepository];
 * the feature never uses the legacy LLM chat path. `isAvailable()` is the synchronous composer gate
 * (kill switch + shipped content), so the UI can hide free talk without loading anything.
 */
interface FreeChatGateway {
    fun isAvailable(): Boolean

    /** Opens the thread for [characterId]: persists a greeting when due, returns chips. */
    suspend fun open(characterId: Long): FreeChatResult

    /** Sends one clamped free-text message and returns the persisted authored reply scene. */
    suspend fun send(characterId: Long, text: String): FreeChatResult

    /** Clears only this feature's conversation for [characterId]; never deletes the contact. */
    suspend fun clear(characterId: Long)
}
