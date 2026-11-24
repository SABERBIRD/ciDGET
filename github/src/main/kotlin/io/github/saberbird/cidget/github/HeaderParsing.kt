package io.github.saberbird.cidget.github

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.toInstant
import okhttp3.Headers

private val nextLink = Regex("<([^>]*)>\\s*;\\s*rel=\"next\"")

/** URL of the `rel="next"` page in a `Link` header, or null. */
internal fun parseLinkNext(headers: Headers): String? =
    headers.values("Link").firstNotNullOfOrNull { nextLink.find(it)?.groupValues?.get(1) }

/**
 * Parses `github-authentication-token-expiration`: `2026-12-24 15:32:55 UTC` (observed), also with a
 * numeric offset (`+0000`, `+02:00`) or `Z`. Returns null for absent or unparseable values.
 */
internal fun parseTokenExpiry(value: String?): Instant? {
    val parts = value?.trim()?.split(Regex("\\s+")) ?: return null
    if (parts.size < 2) return null
    return try {
        val local = LocalDateTime.parse("${parts[0]}T${parts[1]}")
        val zone = parts.getOrNull(2)
        val offset = when {
            zone == null || zone == "UTC" || zone == "GMT" || zone == "Z" -> UtcOffset.ZERO
            else -> UtcOffset.parse(zone.takeIf { it.contains(':') } ?: zone.insertColon())
        }
        local.toInstant(offset)
    } catch (_: IllegalArgumentException) {
        null
    }
}

private fun String.insertColon(): String =
    if (length == 5 && (this[0] == '+' || this[0] == '-')) "${substring(0, 3)}:${substring(3)}" else this

internal fun parseRateLimit(headers: Headers): RateLimit? {
    val limit = headers["x-ratelimit-limit"]?.toIntOrNull()
    val remaining = headers["x-ratelimit-remaining"]?.toIntOrNull()
    val reset = headers["x-ratelimit-reset"]?.toLongOrNull()?.let { Instant.fromEpochSeconds(it) }
    val resource = headers["x-ratelimit-resource"]
    if (limit == null && remaining == null && reset == null && resource == null) return null
    return RateLimit(limit, remaining, reset, resource)
}

internal fun parseMeta(headers: Headers, notModified: Boolean): ResponseMeta = ResponseMeta(
    rateLimit = parseRateLimit(headers),
    tokenExpiresAt = parseTokenExpiry(headers["github-authentication-token-expiration"]),
    notModified = notModified,
)
