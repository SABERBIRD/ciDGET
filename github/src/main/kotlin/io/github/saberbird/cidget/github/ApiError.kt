package io.github.saberbird.cidget.github

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/** Everything that can go wrong with a request (GITHUB_API.md §5). No exception crosses the module boundary. */
@Serializable
public sealed interface ApiError {
    /** No network, DNS failure or connection refused. */
    @Serializable public data object Offline : ApiError

    /** Connect, read or call timeout. */
    @Serializable public data object Timeout : ApiError

    /** HTTP 401: the token is revoked, expired or wrong. */
    @Serializable public data object TokenInvalid : ApiError

    /** No token is stored; nothing was sent. */
    @Serializable public data object NoToken : ApiError

    /** Primary or secondary rate limit; requests must wait until [until]. */
    @Serializable public data class RateLimited(val until: Instant) : ApiError

    /** HTTP 403 that is not a rate limit. */
    @Serializable public data object Forbidden : ApiError

    /** HTTP 404 (GitHub also answers 404 for private repos the token cannot see). */
    @Serializable public data object NotFound : ApiError

    /** HTTP 5xx. */
    @Serializable public data class Server(val code: Int) : ApiError

    /** Any other 4xx. */
    @Serializable public data class Http(val code: Int) : ApiError

    /** The body could not be parsed. */
    @Serializable public data object Parse : ApiError

    /** A bug or an unexpected exception was caught at the boundary. */
    @Serializable public data object Unexpected : ApiError
}
