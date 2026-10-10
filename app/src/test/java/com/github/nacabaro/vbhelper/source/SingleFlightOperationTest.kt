package com.github.nacabaro.vbhelper.source

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SingleFlightOperationTest {
    @Test fun repeatedTapsDoNotStartAnotherExportAndResultIsConsumedOnce() = runTest {
        val finish = CompletableDeferred<String>()
        var calls = 0
        val operation = SingleFlightOperation(this) { _: Long -> calls++; finish.await() }
        assertTrue(operation.start(7))
        assertFalse(operation.start(7))
        runCurrent()
        assertEquals(1, calls)
        finish.complete("export-7")
        runCurrent()
        assertFalse(operation.start(8)) // Do not overwrite an unconsumed share intent.
        assertEquals("export-7", operation.takeResult())
        assertNull(operation.takeResult())
        assertTrue(operation.start(8))
        runCurrent()
    }

    @Test fun failureAllowsAnExplicitRetryWithoutPublishingAResult() = runTest {
        var calls = 0
        val operation = SingleFlightOperation(this) { _: Long ->
            if (++calls == 1) error("unavailable") else "ready"
        }
        operation.start(1)
        runCurrent()
        assertTrue(operation.state.value is OperationState.Failed)
        assertNull(operation.takeResult())
        assertTrue(operation.start(1))
        runCurrent()
        assertEquals("ready", operation.takeResult())
    }

    @Test fun cancelledOwnerDoesNotPublishAShareResult() = runTest {
        val owner = launch { kotlinx.coroutines.awaitCancellation() }
        val operation = SingleFlightOperation(kotlinx.coroutines.CoroutineScope(coroutineContext + owner)) { _: Long ->
            kotlinx.coroutines.awaitCancellation()
        }
        operation.start(1)
        runCurrent()
        owner.cancelAndJoin()
        assertNull(operation.takeResult())
        assertTrue(operation.state.value is OperationState.Idle)
    }
}
