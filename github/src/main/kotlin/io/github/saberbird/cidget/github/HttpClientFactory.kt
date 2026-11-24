package io.github.saberbird.cidget.github

import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** GitHub REST API version header value (GITHUB_API.md §1). */
public const val API_VERSION: String = "2022-11-28"

private const val CACHE_BYTES: Long = 10L * 1024 * 1024

/** Thrown by the auth interceptor when no token is stored; mapped to [ApiError.NoToken]. Carries no secrets. */
internal class MissingTokenException : IOException("no token")

/** Adds `Authorization` from [tokenProvider]. The token is read per request and never stored here. */
internal class AuthInterceptor(private val tokenProvider: () -> String?) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokenProvider() ?: throw MissingTokenException()
        return chain.proceed(chain.request().newBuilder().header("Authorization", "Bearer $token").build())
    }
}

/** Adds the fixed headers, including `Cache-Control: max-age=0` so OkHttp revalidates with `If-None-Match`. */
internal class HeadersInterceptor(private val userAgent: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response = chain.proceed(
        chain.request().newBuilder()
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", API_VERSION)
            .header("User-Agent", userAgent)
            .header("Cache-Control", "max-age=0")
            .build(),
    )
}

/**
 * Builds the OkHttp client for [GitHubClient].
 *
 * @param cacheDir directory for the 10 MB ETag cache (`cacheDir/http` on Android).
 * @param userAgent `ciDGET/<versionName>`.
 * @param tokenProvider returns the decrypted token, or null when none is stored.
 */
public fun createHttpClient(
    cacheDir: File,
    userAgent: String,
    tokenProvider: () -> String?,
    base: OkHttpClient = OkHttpClient(),
): OkHttpClient = base.newBuilder()
    .cache(Cache(cacheDir, CACHE_BYTES))
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .callTimeout(30, TimeUnit.SECONDS)
    .retryOnConnectionFailure(true)
    .addInterceptor(HeadersInterceptor(userAgent))
    .addInterceptor(AuthInterceptor(tokenProvider))
    .build()

/**
 * A copy of this client that authenticates with [tokenProvider] instead of the default one, keeping the
 * shared cache. Used for one saved token among several (D23). The provider is called per request.
 */
public fun OkHttpClient.withTokenProvider(tokenProvider: () -> String?): OkHttpClient = newBuilder().apply {
    interceptors().removeAll { it is AuthInterceptor }
    addInterceptor(AuthInterceptor(tokenProvider))
}.build()

/**
 * Like [withTokenProvider] for a fixed [token], but without a cache. Used to validate a token before it is
 * saved; nothing it fetches is cached.
 */
public fun OkHttpClient.withToken(token: String): OkHttpClient =
    withTokenProvider { token }.newBuilder().cache(null).build()
