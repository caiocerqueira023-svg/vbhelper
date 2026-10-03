package com.github.nacabaro.vbhelper.source

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class CardDocumentReadTest {
    @Test fun successfulParsingClosesTheSourceBeforePersistenceCanBegin() = runTest {
        var closed = false
        val stream = object : ByteArrayInputStream(byteArrayOf(1)) {
            override fun close() { closed = true; super.close() }
        }
        val parsed = readCardDocument(open = { stream }, parse = { it.read() })
        assertEquals(1, parsed)
        assertTrue(closed)
    }

    @Test fun aCloseFailureDoesNotReachThePersistenceStep() = runTest {
        var persisted = false
        val stream = object : ByteArrayInputStream(byteArrayOf(1)) {
            override fun close() { throw IOException("Provider close failed") }
        }
        try {
            readCardDocument(open = { stream }, parse = { it.read() })
            persisted = true
            fail("Closing must succeed before import can commit")
        } catch (_: IOException) { }
        assertFalse(persisted)
    }

    @Test fun cancellingAStalledReadClosesItsStreamAndUnblocksTheWorker() = runTest {
        val entered = CountDownLatch(1)
        val released = CountDownLatch(1)
        val stream = object : InputStream() {
            override fun read(): Int { entered.countDown(); released.await(); throw IOException("Closed") }
            override fun close() { released.countDown() }
        }
        val job = launch(Dispatchers.IO) { readCardDocument(open = { stream }, parse = { it.read() }) }
        assertTrue(entered.await(2, TimeUnit.SECONDS))
        job.cancelAndJoin()
        assertEquals(0, released.count)
        assertTrue(job.isCancelled)
    }

    @Test fun aStreamThatOpensAfterCancellationIsStillClosed() = runTest {
        val entered = CountDownLatch(1)
        val released = CountDownLatch(1)
        val closed = CountDownLatch(1)
        val stream = object : ByteArrayInputStream(byteArrayOf(1)) {
            override fun close() { closed.countDown() }
        }
        val job = launch(Dispatchers.IO) {
            readCardDocument(open = { entered.countDown(); released.await(); stream }, parse = { it.read() })
        }
        assertTrue(entered.await(2, TimeUnit.SECONDS))
        job.cancel()
        released.countDown()
        job.join()
        assertEquals(0, closed.count)
    }
}
