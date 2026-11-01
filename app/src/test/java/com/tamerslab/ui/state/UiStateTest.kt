package com.tamerslab.ui.state

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class UiStateTest {
    @Test fun requestStateKeepsSuccessAndFailureExplicit() {
        assertEquals(RequestState.Loading, RequestState.Loading)
        assertEquals(RequestState.Success("ready"), RequestState.Success("ready"))
        assertEquals("failed", (RequestState.Failure("failed") as RequestState.Failure).message)
    }

    @Test fun oneShotQueueDeliversEachEventOnceInOrder() = runTest {
        val queue = OneShotEventQueue<String>()
        queue.emit("first")
        queue.emit("second")
        assertEquals("first", queue.events.first())
        assertEquals("second", queue.events.first())
    }
}
