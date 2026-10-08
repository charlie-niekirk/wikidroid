package dev.cniekirk.wikidroid.core.common

import java.io.IOException

/** Failures the data layer reports to the UI. Anything unexpected ends up as [Unknown]. */
sealed interface DataError {
    /** No connection, timeout, or a non-2xx HTTP status. */
    data class Network(
        val httpCode: Int? = null,
    ) : DataError

    /** MediaWiki answered 200 with an `error` body. */
    data class Api(
        val code: String,
        val info: String?,
    ) : DataError

    /** The response arrived but couldn't be decoded or parsed. */
    data object Parse : DataError

    data object NotFound : DataError

    data class Unknown(
        val cause: Throwable? = null,
    ) : DataError
}

fun Throwable.toDataError(): DataError =
    when (this) {
        is IOException -> DataError.Network()
        else -> DataError.Unknown(this)
    }
