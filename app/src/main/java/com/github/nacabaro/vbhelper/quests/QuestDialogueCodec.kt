package com.github.nacabaro.vbhelper.quests

import org.json.JSONObject

/** A proposal can select only a saved quest and must cite the current player turn. */
object QuestDialogueCodec {
    fun parse(raw: String, giverId: String, quests: List<QuestInstance>, userMessageId: Long,
              availablePartnerIds: Set<String> = emptySet()): QuestDialogueAction? {
        if (raw.length > 12_000) return null
        return runCatching {
            val json = JSONObject(raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim())
            val action = json.optJSONObject("questAction") ?: return@runCatching null
            val id = action.getString("questId")
            val quest = quests.single { it.id == id && it.giverId == giverId }
            val revision = action.getLong("revision")
            val evidence = action.getString("evidenceId")
            require(revision == quest.revision && evidence == "private:$userMessageId")
            val partnerId = if (action.has("partnerId") && !action.isNull("partnerId")) action.getString("partnerId") else null
            require(partnerId == null || partnerId in availablePartnerIds)
            val type = QuestActionType.valueOf(action.getString("type"))
            require(partnerId == null || type == QuestActionType.ACCEPT)
            QuestDialogueAction(type, id, revision, evidence, partnerId)
        }.getOrNull()
    }
}
