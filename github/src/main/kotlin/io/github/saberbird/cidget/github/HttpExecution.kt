package io.github.saberbird.cidget.github

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.datetime.Instant
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Headers
import okhttp3.Response
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.seconds

internal suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            if (cont.isActive) cont.resume(response) else response.close()
        }

        override fun onFailure(call: Call, e: IOException) {
            if (cont.isActive) cont.resumeWithException(e)
        }
    })
}

/** Maps a thrown exception to an [ApiError]. Never rethrows [CancellationException] — callers must do that. */
internal fun mapException(e: Throwable): ApiError = when (e) {
    is MissingTokenException -> ApiError.NoToken
    is SocketTimeoutException, is InterruptedIOException -> ApiError.Timeout
    is UnknownHostException, is ConnectException -> ApiError.Offline
    is IOException -> ApiError.Offline
    is kotlinx.serialization.SerializationException, is IllegalArgumentException -> ApiError.Parse
    else -> ApiError.Unexpected
}

/** Maps a non-2xx response to an [ApiError] (GITHUB_API.md §5). */
internal fun mapHttpError(code: Int, headers: Headers, now: Instant): ApiError {
    if (code == 401) return ApiError.TokenInvalid
    if (code == 403 || code == 429) {
        val retryAfter = headers["retry-after"]?.trim()?.toLongOrNull()?.let { now + it.seconds }
        val limit = parseRateLimit(headers)
        val reset = if (limit?.remaining == 0) limit.resetAt else null
        val until = listOfNotNull(retryAfter, reset).maxOrNull()
        return when {
            until != null -> ApiError.RateLimited(until)
            code == 429 -> ApiError.RateLimited(now + 60.seconds)
            else -> ApiError.Forbidden
        }
    }
    return when (code) {
        404 -> ApiError.NotFound
        in 500..599 -> ApiError.Server(code)
        else -> ApiError.Http(code)
    }
}
