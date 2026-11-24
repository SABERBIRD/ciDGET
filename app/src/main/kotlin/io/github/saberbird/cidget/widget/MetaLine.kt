package io.github.saberbird.cidget.widget

import android.content.Context
import io.github.saberbird.cidget.R
import io.github.saberbird.cidget.core.AppSettings
import io.github.saberbird.cidget.core.expiryWarningDays
import io.github.saberbird.cidget.core.formatAge
import io.github.saberbird.cidget.core.isStale
import io.github.saberbird.cidget.data.RepoSnapshot
import io.github.saberbird.cidget.github.ApiError
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Terminal-style text of an [ApiError] for the widget's meta line (GITHUB_API.md §5). */
internal fun ApiError.widgetText(context: Context): String = when (this) {
    ApiError.Offline -> context.getString(R.string.w_err_offline)
    ApiError.Timeout -> context.getString(R.string.w_err_timeout)
    ApiError.TokenInvalid -> context.getString(R.string.w_err_token)
    ApiError.NoToken -> context.getString(R.string.w_no_token)
    is ApiError.RateLimited -> {
        val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
            .withZone(ZoneId.systemDefault()).format(until.toJavaInstant())
        context.getString(R.string.w_err_rate_limit, time)
    }
    ApiError.Forbidden -> context.getString(R.string.w_err_forbidden)
    ApiError.NotFound -> context.getString(R.string.w_err_not_found)
    is ApiError.Server -> context.getString(R.string.w_err_server, code)
    is ApiError.Http -> context.getString(R.string.w_err_http, code)
    ApiError.Parse -> context.getString(R.string.w_err_parse)
    ApiError.Unexpected -> context.getString(R.string.w_err_unexpected)
}

/**
 * Parts of the meta line (WIDGET.md §2): age of data (`stale 2h` once older than the stale threshold, D13), the
 * current error, then the token expiry warning; joined with ` · `.
 */
internal fun metaLine(
    context: Context,
    snapshot: RepoSnapshot?,
    tokenPresent: Boolean,
    tokenInvalid: Boolean,
    now: Instant,
    app: AppSettings = AppSettings(),
    tokenExpiresAt: Instant? = null,
): String {
    val parts = mutableListOf<String>()
    when {
        !tokenPresent -> parts += context.getString(R.string.w_no_token)
        tokenInvalid -> parts += context.getString(R.string.w_err_token)
        else -> {
            snapshot?.fetchedAt?.let {
                val age = formatAge(it, now)
                parts += context.getString(if (isStale(it, now, app)) R.string.w_stale else R.string.w_age_ago, age)
            }
            snapshot?.error?.let { parts += it.widgetText(context) }
            expiryWarningDays(tokenExpiresAt, now, app)?.let { parts += expiryText(context, it) }
        }
    }
    return parts.joinToString(" · ")
}

/** `! token expires in 3d` (`today` when less than a day is left). */
internal fun expiryText(context: Context, days: Int): String =
    context.getString(R.string.w_token_expires, if (days == 0) context.getString(R.string.w_today) else "${days}d")
