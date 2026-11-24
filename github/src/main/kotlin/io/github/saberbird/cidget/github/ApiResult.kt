package io.github.saberbird.cidget.github

import kotlinx.datetime.Instant

/** Rate-limit headers of the last response. */
public data class RateLimit(
    val limit: Int?,
    val remaining: Int?,
    val resetAt: Instant?,
    val resource: String?,
)

/** Facts read from response headers, present on successes and errors alike. */
public data class ResponseMeta(
    val rateLimit: RateLimit? = null,
    /** From `github-authentication-token-expiration`; null = no expiry or header absent. */
    val tokenExpiresAt: Instant? = null,
    /** True when the answer came from the cache after a `304`. */
    val notModified: Boolean = false,
) {
    /** Newer facts win; absent values keep the older ones. [notModified] is true only if both are. */
    internal fun merge(newer: ResponseMeta): ResponseMeta = ResponseMeta(
        rateLimit = newer.rateLimit ?: rateLimit,
        tokenExpiresAt = newer.tokenExpiresAt ?: tokenExpiresAt,
        notModified = newer.notModified && (this == ResponseMeta() || notModified),
    )
}

/** Result of an API call. Errors are values. */
public sealed interface ApiResult<out T> {
    public val meta: ResponseMeta

    /** Success. */
    public data class Ok<out T>(val value: T, override val meta: ResponseMeta) : ApiResult<T>

    /** Failure. */
    public data class Err(val error: ApiError, override val meta: ResponseMeta) : ApiResult<Nothing>
}
