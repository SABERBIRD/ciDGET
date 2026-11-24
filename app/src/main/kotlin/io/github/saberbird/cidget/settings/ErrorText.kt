package io.github.saberbird.cidget.settings

import androidx.annotation.StringRes
import io.github.saberbird.cidget.R
import io.github.saberbird.cidget.github.ApiError

/** String resource and optional argument for an [ApiError] shown in settings. */
data class ErrorText(@StringRes val res: Int, val arg: Int? = null)

fun ApiError.toErrorText(): ErrorText = when (this) {
    ApiError.Offline -> ErrorText(R.string.err_offline)
    ApiError.Timeout -> ErrorText(R.string.err_timeout)
    ApiError.TokenInvalid -> ErrorText(R.string.err_unauthorized)
    ApiError.NoToken -> ErrorText(R.string.err_no_token)
    is ApiError.RateLimited -> ErrorText(R.string.err_rate_limited)
    ApiError.Forbidden -> ErrorText(R.string.err_forbidden)
    ApiError.NotFound -> ErrorText(R.string.err_not_found)
    is ApiError.Server -> ErrorText(R.string.err_server, code)
    is ApiError.Http -> ErrorText(R.string.err_http, code)
    ApiError.Parse -> ErrorText(R.string.err_parse)
    ApiError.Unexpected -> ErrorText(R.string.err_unexpected)
}
