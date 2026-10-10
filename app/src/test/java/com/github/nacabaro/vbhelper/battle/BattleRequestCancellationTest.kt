package com.github.nacabaro.vbhelper.battle

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.Request
import okio.Timeout
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class BattleRequestCancellationTest {
    @Test fun leavingTheOwnerCancelsTheNetworkCallAndIgnoresLateResponses() = runTest {
        val call = ControlledCall()
        val pending = async { call.awaitResponse() }
        runCurrent()
        pending.cancelAndJoin()
        assertTrue(call.cancelled)
        call.callback!!.onResponse(call, Response.success("late"))
        assertTrue(pending.isCancelled)
    }

    @Test fun aCompletedRequestReturnsItsBodyWithoutBlockingTheCaller() = runTest {
        val call = ControlledCall()
        val pending = async { call.awaitResponse() }
        runCurrent()
        assertFalse(pending.isCompleted)
        call.callback!!.onResponse(call, Response.success("ready"))
        assertEquals("ready", pending.await().body())
        assertFalse(call.cancelled)
    }

    private class ControlledCall : Call<String> {
        var callback: Callback<String>? = null
        var cancelled = false
        override fun enqueue(callback: Callback<String>) { this.callback = callback }
        override fun cancel() { cancelled = true }
        override fun isCanceled() = cancelled
        override fun isExecuted() = callback != null
        override fun execute(): Response<String> = error("The UI must enqueue requests")
        override fun clone(): Call<String> = ControlledCall()
        override fun request(): Request = Request.Builder().url("https://example.invalid/").build()
        override fun timeout(): Timeout = Timeout.NONE
    }
}
