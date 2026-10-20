package com.musicpipe.player.util

import com.musicpipe.player.data.model.Song
import com.musicpipe.player.data.remote.ServerConfig
import com.musicpipe.player.data.remote.dto.TrackDto

/**
 * Gateway trackBrief -> domain Song (docs/路由器整合报告.md §5).
 * duration arrives in SECONDS, the domain model uses milliseconds.
 * streamUrl / coverUrl are server-relative and must be signed with ?token= for media3/Coil.
 */
object SongMapper {

    const val STREAM_PATH_PREFIX = "/api/v1/stream/"
    const val COVER_PATH_PREFIX = "/api/v1/cover/"

    fun streamPath(songId: String): String = STREAM_PATH_PREFIX + songId

    fun coverPath(songId: String): String = COVER_PATH_PREFIX + songId

    fun fromTrack(track: TrackDto, token: String): Song {
        val id = track.id
        return Song(
            id = id,
            title = track.title?.takeIf { it.isNotBlank() } ?: "未知歌曲",
            artist = track.artist?.takeIf { it.isNotBlank() } ?: "未知艺术家",
            album = track.album?.takeIf { it.isNotBlank() } ?: "",
            durationMs = (track.duration ?: 0L) * 1000L,
            coverUrl = ServerConfig.signed(track.coverUrl, token),
            streamUrl = ServerConfig.signed(track.streamUrl ?: streamPath(id), token).orEmpty(),
            platform = track.platform?.takeIf { it.isNotBlank() } ?: "musicpipe"
        )
    }

    /**
     * Rebuild a Song from a locally cached row (history / favorites).
     * [rawCover] / [rawStream] hold gateway-relative paths; absolute URLs persisted by older
     * versions of the app are ignored and rebuilt from the track id instead.
     */
    fun fromCached(
        songId: String,
        title: String,
        artist: String,
        album: String,
        durationMs: Long,
        rawCover: String?,
        rawStream: String?,
        platform: String,
        token: String
    ): Song {
        val cover = if (isRelativePath(rawCover)) rawCover else coverPath(songId)
        val stream = if (isRelativePath(rawStream)) rawStream else streamPath(songId)
        return Song(
            id = songId,
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            coverUrl = ServerConfig.signed(cover, token),
            streamUrl = ServerConfig.signed(stream, token).orEmpty(),
            platform = platform
        )
    }

    fun isRelativePath(value: String?): Boolean =
        !value.isNullOrBlank() && value.startsWith("/api/")
}
