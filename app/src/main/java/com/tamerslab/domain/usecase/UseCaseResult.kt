package com.tamerslab.domain.usecase

import kotlinx.coroutines.CancellationException

/**
 * Typed outcome of a use case: a success carrying a domain value, or a failure carrying
 * a user-presentable message and the original cause. Prefer this over bare
 * `Result<T>` so callers cannot accidentally swallow a typed failure.
 */
sealed interface UseCaseResult<out T> {
    data class Success<T>(val value: T) : UseCaseResult<T>
    data class Failure(val message: String, val cause: Throwable? = null) : UseCaseResult<Nothing>
}

/**
 * Returns the success value or throws the recorded [UseCaseResult.Failure] cause.
 * Use only at orchestration boundaries that already handle exceptions.
 */
fun <T> UseCaseResult<T>.getOrThrow(): T = when (this) {
    is UseCaseResult.Success -> value
    is UseCaseResult.Failure -> throw cause ?: IllegalStateException(message)
}

/** Runs [block], converting non-cancellation failures into [UseCaseResult.Failure]. */
internal suspend fun <T> useCaseResult(block: suspend () -> T): UseCaseResult<T> = try {
    UseCaseResult.Success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (failure: Throwable) {
    UseCaseResult.Failure(failure.message ?: "The operation failed.", failure)
}
