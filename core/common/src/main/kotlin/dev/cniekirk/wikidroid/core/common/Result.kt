package dev.cniekirk.wikidroid.core.common

import kotlin.coroutines.cancellation.CancellationException

/** Success or a typed failure. Unlike `kotlin.Result`, the error side is part of the type. */
sealed interface Result<out T, out E> {
    data class Success<out T>(
        val data: T,
    ) : Result<T, Nothing>

    data class Failure<out E>(
        val error: E,
    ) : Result<Nothing, E>
}

val <T, E> Result<T, E>.isSuccess: Boolean get() = this is Result.Success

fun <T, E> Result<T, E>.getOrNull(): T? = (this as? Result.Success)?.data

fun <T, E> Result<T, E>.errorOrNull(): E? = (this as? Result.Failure)?.error

inline fun <T, E, R> Result<T, E>.map(transform: (T) -> R): Result<R, E> =
    when (this) {
        is Result.Success -> Result.Success(transform(data))
        is Result.Failure -> this
    }

inline fun <T, E, F> Result<T, E>.mapError(transform: (E) -> F): Result<T, F> =
    when (this) {
        is Result.Success -> this
        is Result.Failure -> Result.Failure(transform(error))
    }

inline fun <T, E, R> Result<T, E>.flatMap(transform: (T) -> Result<R, E>): Result<R, E> =
    when (this) {
        is Result.Success -> transform(data)
        is Result.Failure -> this
    }

inline fun <T, E, R> Result<T, E>.fold(
    onSuccess: (T) -> R,
    onFailure: (E) -> R,
): R =
    when (this) {
        is Result.Success -> onSuccess(data)
        is Result.Failure -> onFailure(error)
    }

inline fun <T, E> Result<T, E>.onSuccess(action: (T) -> Unit): Result<T, E> {
    if (this is Result.Success) action(data)
    return this
}

inline fun <T, E> Result<T, E>.onFailure(action: (E) -> Unit): Result<T, E> {
    if (this is Result.Failure) action(error)
    return this
}

/**
 * Runs [block], turning exceptions into [Result.Failure] via [Throwable.toDataError].
 * Cancellation is rethrown so structured concurrency keeps working.
 */
inline fun <T> dataResultOf(
    mapError: (Throwable) -> DataError = Throwable::toDataError,
    block: () -> T,
): Result<T, DataError> =
    try {
        Result.Success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (
        @Suppress("TooGenericExceptionCaught") throwable: Throwable,
    ) {
        Result.Failure(mapError(throwable))
    }
