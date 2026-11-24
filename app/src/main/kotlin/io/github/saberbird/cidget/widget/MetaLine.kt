package io.github.saberbird.cidget.widget

import android.content.Context
import io.github.saberbird.cidget.R
import io.github.saberbird.cidget.core.formatAge
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

/** Parts of the meta line: age of data, then the current error, joined with ` · `. */
internal fun metaLine(context: Context, snapshot: RepoSnapshot?, tokenPresent: Boolean, tokenInvalid: Boolean, now: Instant): String {
    val parts = mutableListOf<String>()
    when {
        !tokenPresent -> parts += context.getString(R.string.w_no_token)
        tokenInvalid -> parts += context.getString(R.string.w_err_token)
        else -> {
            snapshot?.fetchedAt?.let { parts += context.getString(R.string.w_age_ago, formatAge(it, now)) }
            snapshot?.error?.let { parts += it.widgetText(context) }
        }
    }
    return parts.joinToString(" · ")
}
