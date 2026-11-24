package io.github.saberbird.cidget.core

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

/**
 * App-wide settings (ARCHITECTURE.md §3, D13). Background refresh is one periodic job for all widgets, so these
 * are not per widget.
 */
@Serializable
public data class AppSettings(
    /** Minutes between background refreshes (one of [INTERVALS]); null = background refresh off. */
    val refreshMinutes: Int? = DEFAULT_REFRESH_MINUTES,
    /** Refresh only on unmetered networks (Wi-Fi). */
    val unmeteredOnly: Boolean = false,
    /** Skip background refresh while the battery is low. */
    val batteryNotLow: Boolean = true,
    /** Warn this many days before a token expires (D13). */
    val expiryWarnDays: Int = DEFAULT_EXPIRY_WARN_DAYS,
) {
    /** Data older than this is marked stale: twice the refresh interval, or never when refresh is off (D13). */
    public fun staleAfter(): Duration? = refreshMinutes?.let { (it * 2).minutes }

    /** Copy with the interval moved one step along [INTERVALS] ([delta] -1 shorter, +1 longer); `off` is past the longest. */
    public fun withRefreshStep(delta: Int): AppSettings {
        val steps: List<Int?> = INTERVALS + listOf(null)
        val i = steps.indexOf(refreshMinutes).takeIf { it >= 0 } ?: steps.indexOf(DEFAULT_REFRESH_MINUTES)
        return copy(refreshMinutes = steps[(i + delta).coerceIn(0, steps.lastIndex)])
    }

    /** Copy with the expiry warning moved by [delta] days, kept within 1–30. */
    public fun withExpiryWarnStep(delta: Int): AppSettings =
        copy(expiryWarnDays = (expiryWarnDays + delta).coerceIn(MIN_EXPIRY_WARN_DAYS, MAX_EXPIRY_WARN_DAYS))

    /** Repairs values read from storage. */
    public fun normalized(): AppSettings = copy(
        refreshMinutes = refreshMinutes?.let { m -> INTERVALS.minByOrNull { kotlin.math.abs(it - m) } },
        expiryWarnDays = expiryWarnDays.coerceIn(MIN_EXPIRY_WARN_DAYS, MAX_EXPIRY_WARN_DAYS),
    )

    public companion object {
        /** Allowed intervals in minutes, 15–360 (WorkManager's minimum period is 15 minutes). */
        public val INTERVALS: List<Int> = listOf(15, 30, 60, 120, 180, 360)
        public const val DEFAULT_REFRESH_MINUTES: Int = 30
        public const val DEFAULT_EXPIRY_WARN_DAYS: Int = 7
        public const val MIN_EXPIRY_WARN_DAYS: Int = 1
        public const val MAX_EXPIRY_WARN_DAYS: Int = 30
    }
}

/** Whether data fetched at [fetchedAt] is stale at [now] under [settings] (D13). */
public fun isStale(fetchedAt: Instant?, now: Instant, settings: AppSettings): Boolean {
    val limit = settings.staleAfter() ?: return false
    return fetchedAt != null && now - fetchedAt > limit
}

/**
 * Whole days until a token expiring at [expiresAt] expires, when that is within the warning window of
 * [settings]; null when there is nothing to warn about. 0 means it expires today; a negative value is not
 * returned (an expired token answers 401, which is shown instead).
 */
public fun expiryWarningDays(expiresAt: Instant?, now: Instant, settings: AppSettings): Int? {
    if (expiresAt == null || expiresAt <= now) return null
    val left = expiresAt - now
    return if (left <= settings.expiryWarnDays.days) left.inWholeDays.toInt() else null
}
