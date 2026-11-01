package com.tamerslab.chat

import com.tamerslab.domain.chat.ChatMessageEntity
import org.junit.Assert.*
import org.junit.Test

class ChatBranchPolicyTest {
    private fun message(id: Long, role: String, text: String) = ChatMessageEntity(id, "partner", role, text, id * 100)
    private val history = listOf(message(1, "user", "Hello"), message(2, "assistant", "Hi"),
        message(3, "user", "How are you?"), message(4, "assistant", "Ready to explore"))

    @Test fun regenerationPromptExcludesTheLaterBranchWithoutMutatingIt() {
        val prompt = ChatBranchPolicy.promptHistory(history, 1, "Hello")
        assertEquals(listOf(1L), prompt.map { it.id })
        assertEquals(listOf(1L, 2L, 3L, 4L), history.map { it.id })
    }
    @Test fun retainsAllEarlierDialogueAndTheOriginalUserIdentity() {
        assertEquals(listOf(1L, 2L, 3L), ChatBranchPolicy.promptHistory(history, 3, "How are you?").map { it.id })
    }
    @Test fun readReceiptsDoNotInvalidateAnOtherwiseUnchangedConversation() {
        assertTrue(ChatBranchPolicy.sameConversation(history, history.map { it.copy(isRead = true) }))
    }
    @Test fun newIncomingMessagesPreventAReplacementFromDeletingNewerWork() {
        assertFalse(ChatBranchPolicy.sameConversation(history, history + message(5, "assistant", "One more thing")))
    }
    @Test fun deletionsAndContentChangesInvalidateAPendingReplacement() {
        assertFalse(ChatBranchPolicy.sameConversation(history, history.dropLast(1)))
        assertFalse(ChatBranchPolicy.sameConversation(history, history.map { if (it.id == 1L) it.copy(content = "Changed") else it }))
    }
    @Test fun anotherIndividualCannotMatchTheSameMessageIds() {
        assertFalse(ChatBranchPolicy.sameConversation(history, history.map { it.copy(individualId = "someone-else") }))
    }
    @Test(expected = IllegalArgumentException::class) fun cannotRegenerateAnAssistantMessage() {
        ChatBranchPolicy.promptHistory(history, 2, "Hi")
    }
    @Test(expected = IllegalArgumentException::class) fun staleSelectedTextCannotRegenerateAnotherRevision() {
        ChatBranchPolicy.promptHistory(history, 1, "Old text")
    }
    @Test(expected = IllegalArgumentException::class) fun missingMessageIsRejected() {
        ChatBranchPolicy.promptHistory(history, 99, "Hello")
    }
}
