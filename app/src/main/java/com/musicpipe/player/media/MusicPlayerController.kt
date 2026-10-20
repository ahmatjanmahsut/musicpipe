package com.musicpipe.player.media

import android.content.ComponentName
import android.content.Context
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.musicpipe.player.data.model.Song
import com.musicpipe.player.service.PlaybackService
import com.musicpipe.player.util.SongMapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * App-level wrapper around a Media3 MediaController bound to PlaybackService.
 * Single connection shared by the whole app (created in AppContainer).
 */
class MusicPlayerController(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private var controller: MediaController? = null
    private var positionJob: Job? = null

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state

    /** Current play queue (source of Song mapping for MediaItems). */
    @Volatile var queue: List<Song> = emptyList()
        private set

    /** Fired on every media-item transition (auto or manual). Used for history/scrobble. */
    @Volatile var onSongChanged: ((song: Song, isAuto: Boolean) -> Unit)? = null

    /**
     * v1.8.0：控制器还没连上时暂存的"播放意图"。
     *
     * 冷启动把 MediaController 绑定（= PlaybackService 里创建 ExoPlayer，启动最贵的一步）
     * 推迟到首帧之后，于是存在"用户已经点了播放、而服务还没绑好"的窗口。
     * 直接 `controller ?: return` 会把这次点击悄悄吞掉 —— 存下来，连上后补执行。
     */
    private var pendingAction: ((MediaController) -> Unit)? = null

    private fun withController(block: (MediaController) -> Unit) {
        val mc = controller
        if (mc != null) block(mc) else pendingAction = block
    }

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            refresh()
            val song = mediaItem?.let { findSong(it.mediaId) }
            if (song != null) {
                onSongChanged?.invoke(song, reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            refresh()
            if (isPlaying) startPositionPolling() else stopPositionPolling()
        }

        override fun onPlaybackStateChanged(playbackState: Int) { refresh() }
    }

    suspend fun connect() {
        if (controller?.isConnected == true) return
        val c = suspendCancellableCoroutine { cont ->
            val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            val future = MediaController.Builder(context, token).buildAsync()
            future.addListener({
                try {
                    val mc = future.get()
                    if (cont.isActive) cont.resume(mc)
                } catch (t: Throwable) {
                    if (cont.isActive) cont.resume(null)
                }
            }, { it.run() })
        }
        controller = c ?: return
        c?.addListener(listener)
        refresh()
        if (c?.isPlaying == true) startPositionPolling()
        // 首帧后延迟绑定期间用户可能已经点了播放：这里补执行，不能丢。
        pendingAction?.let { action -> c?.let(action) }
        pendingAction = null
    }

    fun playQueue(songs: List<Song>, startIndex: Int = 0) {
        if (songs.isEmpty()) return
        // 队列先记下来：findSong(mediaId) 依赖它，控制器连上时也要用它映射当前曲目。
        queue = songs
        withController { mc ->
            val items = songs.map { it.toMediaItem() }
            mc.setMediaItems(items, startIndex.coerceIn(0, songs.size - 1), 0)
            mc.prepare()
            mc.play()
        }
    }

    fun playSong(song: Song, queueContext: List<Song>? = null) {
        val list = queueContext?.takeIf { it.any { s -> s.id == song.id } } ?: listOf(song)
        playQueue(list, list.indexOfFirst { it.id == song.id }.coerceAtLeast(0))
    }

    fun togglePlayPause() = withController { mc ->
        if (mc.isPlaying) mc.pause() else { mc.prepare(); mc.play() }
    }

    fun seekTo(positionMs: Long) { withController { it.seekTo(positionMs) }; refresh() }
    fun next() = withController { it.seekToNextMediaItem(); it.prepare() }
    fun previous() = withController { it.seekToPreviousMediaItem(); it.prepare() }

    fun release() {
        stopPositionPolling()
        controller?.removeListener(listener)
        controller?.release()
        controller = null
        _state.value = PlaybackState()
    }

    private fun findSong(mediaId: String): Song? = queue.firstOrNull { it.id == mediaId }

    private fun Song.toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .setUri(streamUrl.toUri())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(coverUrl?.toUri())
                .build()
        )
        .build()

    private fun refresh() {
        val mc = controller ?: return
        val song = mc.currentMediaItem?.let { findSong(it.mediaId) } ?: _state.value.currentSong
        _state.value = PlaybackState(
            currentSong = song,
            isPlaying = mc.isPlaying,
            isBuffering = mc.playbackState == Player.STATE_BUFFERING,
            positionMs = mc.currentPosition.coerceAtLeast(0),
            durationMs = if (mc.duration > 0) mc.duration else (song?.durationMs ?: 0),
            queueIndex = mc.currentMediaItemIndex,
            queueSize = mc.mediaItemCount
        )
    }

    private fun startPositionPolling() {
        if (positionJob?.isActive == true) return
        positionJob = scope.launch {
            while (isActive) {
                refresh()
                delay(500)
            }
        }
    }

    private fun stopPositionPolling() {
        positionJob?.cancel()
        positionJob = null
    }
}
