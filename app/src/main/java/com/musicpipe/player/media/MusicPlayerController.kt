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

    /**
     * v1.10.0（C3）：除"播放意图"之外还要保留**顺序**。
     *
     * 原来只有 pendingAction 一个槽位，绑定窗口内连点会覆盖前一次操作
     * （例如用户点了「下一首」再点「播放」，第一个动作直接丢失）。
     * 现在按入队顺序在连上后依次执行；pendingAction 单独留一个槽位，
     * 因为"要播放/要换队列"这类意图只需要最后一次生效。
     */
    private val pendingQueue = ArrayDeque<(MediaController) -> Unit>()

    private fun withController(block: (MediaController) -> Unit) {
        val mc = controller
        if (mc != null) {
            block(mc)
        } else {
            pendingAction = block
            pendingQueue.addLast(block)
            // 防御：极端情况下（服务一直连不上）不让队列无限增长。
            while (pendingQueue.size > MAX_PENDING) pendingQueue.removeFirst()
        }
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
        // 首帧后延迟绑定期间用户可能已经点了播放：按顺序补执行，不能丢。
        val pending = pendingQueue.toList()
        pendingQueue.clear()
        pendingAction = null
        if (c != null) pending.forEach { action -> runCatching { action(c) } }
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

    /**
     * v1.10.0（P8）：拖动进度条后不能立刻用 currentPosition 覆盖 UI。
     *
     * ExoPlayer 的 seekTo 是**异步**的：返回时 currentPosition 仍是旧值，
     * 原来紧接着的 refresh() 会把滑到的位置又写回旧值 → 进度条"弹回去一下再跳过来"。
     * 现在先把目标位置乐观写进 state（用户手指位置立即生效），
     * 再在短暂延迟后 refresh 一次拿真实位置（seek 通常几十毫秒内完成）。
     */
    fun seekTo(positionMs: Long) {
        val target = positionMs.coerceAtLeast(0)
        _state.value = _state.value.copy(positionMs = target)
        withController { it.seekTo(target) }
        scope.launch {
            delay(SEEK_SETTLE_MS)
            refresh()
        }
    }
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

    private companion object {
        /** 连上之前最多暂存多少个操作（远超正常人手速，仅防泄漏）。 */
        const val MAX_PENDING = 16
        /** seek 之后等多久再回读真实位置，避免把乐观值又覆盖成旧值。 */
        const val SEEK_SETTLE_MS = 220L
    }
}
