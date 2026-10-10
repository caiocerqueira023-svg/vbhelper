package com.github.nacabaro.vbhelper.battle

import android.content.Context
import android.content.ContextWrapper
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.components.FeedbackDestination
import com.github.nacabaro.vbhelper.components.showAppFeedback
import com.github.nacabaro.vbhelper.di.VBHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Callback compatibility for the battle UI, with lifecycle-owned, non-blocking requests. */
class RetrofitHelper(private val ownerScope: CoroutineScope? = null) {
    private fun scope(context: Context): CoroutineScope {
        ownerScope?.let { return it }
        var current = context
        while (current is ContextWrapper) {
            if (current is LifecycleOwner) return current.lifecycleScope
            val base = current.baseContext
            if (base === current) break
            current = base
        }
        return (context.applicationContext as VBHelper).applicationScope
    }

    private suspend fun retrofit(token: String? = null): Retrofit = withContext(Dispatchers.IO) {
        val client = OkHttpClient.Builder().retryOnConnectionFailure(false)
        if (token != null) client.addInterceptor(AuthInterceptor(token))
        Retrofit.Builder().baseUrl("http://battle.io-void.com:8080/")
            .client(client.build()).addConverterFactory(GsonConverterFactory.create()).build()
    }

    private suspend fun authenticatedRetrofit(context: Context): Retrofit? {
        val auth = BattleAuthContainer(context.applicationContext).authRepository
        val token = auth.sessionToken.first()?.takeIf { it.isNotBlank() }
            ?: auth.authToken.first()?.takeIf { it.isNotBlank() }
        if (token == null) {
            context.showAppFeedback(R.string.ui_auth_required, destination = FeedbackDestination.BATTLES, important = true)
            return null
        }
        return retrofit(token)
    }

    private fun request(context: Context, block: suspend () -> Unit): Job =
        scope(context).launch(Dispatchers.Main.immediate) {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { context.showAppFeedback(R.string.app_request_recovery, important = true) }
        }

    private suspend fun reportError(context: Context, code: Int) {
        when (code) {
            401, 403 -> {
                BattleAuthContainer(context.applicationContext).authRepository.logout()
                context.showAppFeedback(R.string.app_session_expired, destination = FeedbackDestination.BATTLES, important = true)
            }
            429 -> context.showAppFeedback(R.string.ui_too_many_requests, important = true)
            else -> context.showAppFeedback(R.string.app_request_recovery, important = true)
        }
    }

    fun getOpponents(context: Context, stage: String, callback: (OpponentsDataModel) -> Unit): Job = request(context) {
        val api = authenticatedRetrofit(context) ?: return@request
        val response = api.create(OpponentService::class.java).getopponents(stage).awaitResponse()
        val body = response.body()
        if (response.isSuccessful && body != null) callback(body)
        else reportError(context, response.code())
    }

    fun getPVPWinner(
        context: Context, apiStage: Int, playerID: Long, playerDigi: String, playerStage: Int,
        critBar: Int, opponentDigi: String, opponentStage: Int, callback: (PVPDataModel) -> Unit,
    ): Job = getPVPWinner(context, apiStage, playerID, playerDigi, playerStage, critBar, opponentDigi, opponentStage, null, callback)

    fun getPVPWinner(
        context: Context, apiStage: Int, playerID: Long, playerDigi: String, playerStage: Int,
        critBar: Int, opponentDigi: String, opponentStage: Int, action: String?, callback: (PVPDataModel) -> Unit,
    ): Job = request(context) {
        val api = authenticatedRetrofit(context) ?: return@request
        val response = api.create(PVPService::class.java)
            .getwinner(apiStage, playerID, playerDigi, playerStage, critBar, opponentDigi, opponentStage, action).awaitResponse()
        val body = response.body()
        if (response.isSuccessful && body != null) callback(body)
        else reportError(context, response.code())
    }

    fun authenticate(context: Context, token: String, callback: (AuthenticateResponse) -> Unit): Job =
        scope(context).launch(Dispatchers.Main.immediate) {
            try {
                if (token.isBlank()) {
                    callback(AuthenticateResponse(false, context.getString(R.string.ui_auth_token_empty)))
                    return@launch
                }
                val response = retrofit().create(AuthService::class.java).login(AuthenticateRequest(token)).awaitResponse()
                val body = response.body()
                callback(if (response.isSuccessful && body != null) body else
                    AuthenticateResponse(false, context.getString(R.string.app_auth_recovery), failureCode = response.code()))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { callback(AuthenticateResponse(false, context.getString(R.string.app_auth_recovery))) }
        }
}

internal suspend fun <T> Call<T>.awaitResponse(): Response<T> = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback<T> {
        override fun onResponse(call: Call<T>, response: Response<T>) {
            if (continuation.isActive) continuation.resume(response)
        }
        override fun onFailure(call: Call<T>, failure: Throwable) {
            if (continuation.isActive) continuation.resumeWithException(failure)
        }
    })
}
