package com.musicpipe.player.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Router-merged gateway DTOs (/api/v1/...), mirroring docs/路由器整合报告.md §4–§5.
 * Error bodies are {"error":"<machine code>"} - "message" is usually absent.
 */
data class ErrorDto(val error: String, val message: String? = null)

// ---- accounts (§4.1) ----

/** accounts/register (201) + accounts/me + users POST response. */
data class AuthUserDto(
    val id: Long = 0,
    val username: String = "",
    val role: String = "user",
    @SerializedName(value = "createdAt", alternate = ["created_at"])
    val createdAt: Long = 0
)

data class RegisterRequest(val username: String, val password: String)
data class LoginRequest(val username: String, val password: String)

/** accounts/login: {ok,token,expiresIn,id,username,role,user}. Session = Bearer token. */
data class LoginDto(
    val ok: Boolean = true,
    val token: String = "",
    val expiresIn: Long = 0,
    val id: Long = 0,
    val username: String = "",
    val role: String = "user",
    val user: AuthUserDto? = null
)

/** {ok:true} ack used by logout / users PUT / users DELETE. */
data class OkDto(val ok: Boolean = true)

// ---- user management (§4.2) ----

/** Admin user table row (both createdAt and created_at are sent; camelCase preferred). */
data class UserRowDto(
    val id: Long = 0,
    val username: String = "",
    val role: String = "user",
    @SerializedName(value = "createdAt", alternate = ["created_at"])
    val createdAt: Long = 0
)

data class PagedUsers(
    val items: List<UserRowDto> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val limit: Int = 20
)

data class CreateUserRequest(val username: String, val password: String, val role: String)
data class UpdateUserRequest(val password: String? = null, val role: String? = null)

// ---- library (§5) ----

/**
 * trackBrief. id is a string hash; duration is in SECONDS (verified against the live
 * router: "duration":124 for a 2:04 FLAC); streamUrl/coverUrl/lyricsUrl are server-relative.
 */
data class TrackDto(
    val id: String = "",
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val quality: String? = null,
    val duration: Long? = null,
    val size: Long? = null,
    val path: String? = null,
    val status: String? = null,
    val platform: String? = null,
    val importedAt: Long? = null,
    @SerializedName("created_at")
    val createdAt: Long? = null,
    val streamUrl: String? = null,
    val coverUrl: String? = null,
    val lyricsUrl: String? = null
)

/** GET /api/v1/library response. */
data class TracksResponse(
    val total: Int = 0,
    val offset: Int = 0,
    val limit: Int = 0,
    val tracks: List<TrackDto> = emptyList()
)

// ---- lyrics (§5) ----

/** GET /api/v1/lyrics/<id>: lines are pre-parsed (start in milliseconds). */
data class LyricsDto(
    val id: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val synced: Boolean = false,
    val lyrics: String? = null,
    val lines: List<LyricLineDto>? = null
)

data class LyricLineDto(val start: Long = 0, val line: String? = null)

// ---- APK releases (§4.3) ----

/** releaseRow (camelCase); createdAt is a millisecond timestamp. */
data class LatestReleaseDto(
    val id: Long = 0,
    val versionName: String = "",
    val versionCode: Int = 0,
    val md5: String = "",
    val size: Long = 0,
    val changelog: String? = null,
    val downloadUrl: String = "",
    @SerializedName(value = "createdAt", alternate = ["publishedAt"])
    val createdAt: Long = 0
)
