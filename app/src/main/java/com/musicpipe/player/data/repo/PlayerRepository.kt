package com.musicpipe.player.data.repo

import com.musicpipe.player.data.model.Song
import com.musicpipe.player.media.MusicPlayerController
import com.musicpipe.player.media.PlaybackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Glue: playback events -> local history (requirement 2). The router gateway exposes no
 * scrobble endpoint, so only the local Room history is written.
 */
class PlayerRepository(
    private val player: MusicPlayerController,
    private val history: HistoryRepository,
    private val scope: CoroutineScope
) {
    val state: StateFlow<PlaybackState> get() = player.state

    /**
     * v1.7.0：切歌后的"下一步预热"钩子。
     *
     * 由 AppContainer 注入（避免 PlayerRepository 依赖歌词仓库造成循环依赖）：
     * 收到的是**队列里即将播放的下一首**的 id，用来提前把它的歌词读进内存 ——
     * 这样用户点「下一首」时歌词已经是热的，而不是再等一次网络往返。
     */
    var onSongChangedHook: ((Song) -> Unit)? = null

    init {
        player.onSongChanged = { song, _ ->
            scope.launch { history.record(song) }
            // 切歌后立刻把这一首的歌词读进内存（通常已在 SQLite 里，1~3ms），
            // 于是紧接着打开歌词页不需要任何等待。
            onSongChangedHook?.invoke(song)
        }
    }

    fun playQueue(songs: List<Song>, startIndex: Int) = player.playQueue(songs, startIndex)
    fun playSong(song: Song, context: List<Song>? = null) = player.playSong(song, context)
    fun togglePlayPause() = player.togglePlayPause()
    fun seekTo(ms: Long) = player.seekTo(ms)
    fun next() = player.next()
    fun previous() = player.previous()
}