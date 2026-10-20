package com.musicpipe.player.data.remote

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Rewrites every request onto the current dynamic base URL (from settings).
 * Retrofit is built against a fixed placeholder host; this interceptor swaps
 * scheme/host/port at request time while preserving path + query.
 */
class DynamicBaseInterceptor(
    private val placeholderHost: String = PLACEHOLDER_HOST,
    private val baseProvider: () -> String
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        // Absolute URLs (e.g. CDN download links) pass through untouched; only
        // requests built against the Retrofit placeholder host get re-based.
        if (original.url.host != placeholderHost) return chain.proceed(original)
        val base = baseProvider().toHttpUrlOrNull() ?: return chain.proceed(original)
        val newUrl = original.url.newBuilder()
            .scheme(base.scheme)
            .host(base.host)
            .port(base.port)
            .build()
        return chain.proceed(original.newBuilder().url(newUrl).build())
    }

    companion object { const val PLACEHOLDER_HOST = "placeholder.local" }
}
