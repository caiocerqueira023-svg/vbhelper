package com.github.nacabaro.vbhelper.components

import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class AppFeedbackTest {
    @Test fun feedbackProducedBeforeTheHostExistsIsDeliveredOnceInOrder() = runTest {
        val feedback = AppFeedback()
        feedback.show(AppFeedbackMessage(text = "imported"))
        feedback.show(AppFeedbackMessage(text = "saved"))
        assertEquals(listOf("imported", "saved"), feedback.messages.take(2).toList().map { it.text })
        feedback.show(AppFeedbackMessage(text = "next"))
        assertEquals("next", feedback.messages.take(1).toList().single().text)
    }

    @Test fun transferStatusRemainsAvailableAfterTheTransientMessageWasConsumed() = runTest {
        val feedback = AppFeedback()
        feedback.transfer("not confirmed")
        assertTrue(feedback.messages.take(1).toList().single().important)
        assertEquals("not confirmed", feedback.transferStatus.value)
        feedback.clearTransfer()
        assertNull(feedback.transferStatus.value)
    }
}
