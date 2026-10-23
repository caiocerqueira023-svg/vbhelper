package com.github.nacabaro.vbhelper.chat

import com.github.nacabaro.vbhelper.domain.chat.ChatMessageEntity

/** Read flags may change while a request is pending; conversation content/identity may not. */
object ChatBranchPolicy {
    fun sameConversation(expected: List<ChatMessageEntity>, actual: List<ChatMessageEntity>): Boolean =
        expected.size == actual.size && expected.zip(actual).all { (a, b) ->
            a.id == b.id && a.individualId == b.individualId && a.role == b.role && a.content == b.content && a.timestamp == b.timestamp
        }

    fun promptHistory(history: List<ChatMessageEntity>, messageId: Long, text: String): List<ChatMessageEntity> {
        require(history.any { it.id == messageId && it.role == "user" && it.content == text }) { "The conversation changed. Try again." }
        return history.takeWhile { it.id <= messageId }
    }
}
