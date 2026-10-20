package com.musicpipe.player.media

import com.musicpipe.player.data.model.Song

/** UI-facing playback state exposed by MusicPlayerController. */
data class PlaybackState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val queueIndex: Int = -1,
    val queueSize: Int = 0
)
