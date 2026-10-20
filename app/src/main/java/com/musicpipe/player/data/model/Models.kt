package com.musicpipe.player.data.model

/** Domain models per docs/01 §5. */
data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    /** Absolute, token-signed cover URL (or null when the track has no artwork). */
    val coverUrl: String?,
    val platform: String,
    /** Absolute, token-signed stream URL for media3. */
    val streamUrl: String
)

data class LyricLine(val timeMs: Long, val text: String)

/** role: "admin" | "user" */
data class AppUser(val id: Long, val username: String, val role: String) {
    val isAdmin: Boolean get() = role == "admin"
}
