package com.musicpipe.player.data.remote

/** Mutable runtime endpoints, kept in sync from DataStore by AppContainer / AuthRepository. */
object ServerConfig {
    /** MusicPipe gateway base, e.g. http://192.168.1.10:7800 (no trailing slash). */
    @Volatile var restBaseUrl: String = "http://192.168.1.10:7800"

    /** Absolute URL for a server-relative path (trackBrief.streamUrl / coverUrl / downloadUrl). */
    fun absolute(path: String?): String? {
        if (path.isNullOrBlank()) return null
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        val base = restBaseUrl.trimEnd('/')
        return if (path.startsWith("/")) base + path else base + "/" + path
    }

    /** Append ?token=<session> (or &token=): media3 / Coil / APK download cannot send headers. */
    fun withToken(url: String, token: String): String {
        if (token.isBlank()) return url
        val sep = if (url.contains('?')) '&' else '?'
        return url + sep + "token=" + token
    }

    /** Convenience: absolute + token in one step. */
    fun signed(path: String?, token: String): String? {
        val abs = absolute(path) ?: return null
        return withToken(abs, token)
    }
}
