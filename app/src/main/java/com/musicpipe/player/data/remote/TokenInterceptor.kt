package com.musicpipe.player.data.remote

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds `Authorization: Bearer <token>` to every /api/v1 request
 * (docs/路由器整合报告.md §3 — the App talks to the router gateway with a session token;
 * media3 / Coil / the APK download instead use ?token= via [ServerConfig.withToken]).
 *
 * The provider is non-suspending and reads [AuthTokens] (mirrored from DataStore by
 * PrefsRepository) — the same synchronous pattern as DynamicBaseInterceptor. Requests made
 * before login simply carry no header.
 */
class TokenInterceptor(
    private val tokenProvider: () -> String
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val token = tokenProvider()
        if (token.isBlank() || !request.url.encodedPath.startsWith(API_PREFIX)) {
            return chain.proceed(request)
        }
        // ?token= is equally valid server-side; never send two kinds of credentials.
        if (request.url.queryParameter("token") != null || request.header("Authorization") != null) {
            return chain.proceed(request)
        }
        return chain.proceed(
            request.newBuilder().header("Authorization", "Bearer " + token).build()
        )
    }

    companion object {
        const val API_PREFIX = "/api/v1"
    }
}
