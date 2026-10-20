package com.musicpipe.player.data.remote

import java.net.URLEncoder

/**
 * In-memory copy of the current bearer token (router-merged backend).
 * DataStore (PrefsRepository key "auth_token") is the source of truth; AppContainer
 * loads it into memory on startup and keeps it in sync, AuthRepository writes it on
 * login / clears it on logout. TokenInterceptor reads this for OkHttp calls; media3
 * and Coil build URLs themselves, so use [signedUrl] to append the token as a query
 * parameter (gateway authHook accepts ?token= as well as the Authorization header).
 */
object AuthTokens {
    @Volatile private var value: String = ""

    fun set(token: String?) { value = token?.trim() ?: "" }

    fun clear() { value = "" }

    fun get(): String = value

    val hasToken: Boolean get() = value.isNotEmpty()

    /**
     * Inverse of [signedUrl]: the gateway-relative path of a URL that points at our own
     * base (null for foreign / legacy absolute URLs). Used when caching rows locally.
     */
    fun pathOf(url: String?): String? {
        val u = url?.trim().orEmpty()
        if (u.isEmpty()) return null
        if (!u.startsWith("http://") && !u.startsWith("https://")) return if (u.startsWith("/")) u else null
        val base = ServerConfig.restBaseUrl.trimEnd('/')
        if (!u.startsWith(base + "/")) return null
        val rest = u.substring(base.length)
        val q = rest.indexOf('?')
        val path = if (q >= 0) rest.substring(0, q) else rest
        return path.ifEmpty { null }
    }

    /**
     * Turn a gateway-relative URL such as "/api/v1/cover/x" into an absolute URL
     * carrying the current token. Already-absolute URLs only get the token appended.
     */
    fun signedUrl(path: String?): String? {
        val p = path?.trim().orEmpty()
        if (p.isEmpty()) return null
        val absolute = when {
            p.startsWith("http://") || p.startsWith("https://") -> p
            else -> ServerConfig.restBaseUrl.trimEnd('/') + (if (p.startsWith("/")) p else "/" + p)
        }
        val token = value
        if (token.isEmpty()) return absolute
        val sep = if (absolute.contains("?")) "&" else "?"
        return absolute + sep + "token=" + URLEncoder.encode(token, "UTF-8")
    }
}
