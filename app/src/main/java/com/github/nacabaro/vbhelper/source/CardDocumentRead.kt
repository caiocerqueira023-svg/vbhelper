package com.github.nacabaro.vbhelper.source

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.atomic.AtomicReference

/** Parse and close before persistence. Cancellation closes a stalled provider stream. */
suspend fun <T> readCardDocument(open: () -> InputStream?, parse: (InputStream) -> T): T = coroutineScope {
    val active = AtomicReference<InputStream?>(null)
    val closeOnCancellation = launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
        try { awaitCancellation() }
        finally {
            withContext(NonCancellable + Dispatchers.IO) {
                try { active.getAndSet(null)?.close() } catch (_: Exception) { }
            }
        }
    }
    try {
        val stream = open() ?: throw IOException("Document could not be opened")
        active.set(stream)
        currentCoroutineContext().ensureActive()
        val parsed = stream.use(parse)
        active.compareAndSet(stream, null)
        currentCoroutineContext().ensureActive()
        parsed
    } catch (e: Exception) {
        currentCoroutineContext().ensureActive()
        throw e
    } finally {
        withContext(NonCancellable) {
            closeOnCancellation.cancelAndJoin()
            // Opening can finish after the cancellation watcher already ran.
            withContext(Dispatchers.IO) {
                try { active.getAndSet(null)?.close() } catch (_: Exception) { }
            }
        }
    }
}
