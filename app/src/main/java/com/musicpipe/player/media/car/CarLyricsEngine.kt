package com.musicpipe.player.media.car

import androidx.media3.common.Player
import com.musicpipe.player.data.model.LyricLine
import com.musicpipe.player.data.repo.LyricsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 车载歌词引擎（v1.16.0，跑在 PlaybackService 里）。
 *
 * 职责很窄：**每 200ms 看一次"现在播到哪儿了"，算出该让车机显示哪一行**，
 * 变化时通过 [publish] 交给 [CarLyricsPlayer]。
 *
 * 几个刻意的取舍：
 *  1. **按行变化推送，而不是每帧推**。车机端 AVRCP 更新有节流，频繁推会让部分头单元
 *     出现"刷屏/闪烁"，也白白耗蓝牙带宽。
 *  2. **只监听位置，不订阅歌词页**。歌词页可能根本没打开过，车载歌词必须独立工作。
 *  3. **歌词从 [LyricsRepository] 取**（内存 + SQLite + 网络三级缓存），
 *     切歌后通常在 1~3ms 内就有行，不会出现"前 10 秒车机没歌词"。
 *  4. 没歌词 / 未开启 / 不是车机时一律推 null —— 让车机显示真实歌名，
 *     绝不显示"暂无歌词"这种假信息。
 */
class CarLyricsEngine(
    private val scope: CoroutineScope,
    private val player: Player,
    private val lyrics: LyricsRepository,
    private val settings: StateFlow<CarLyricsSettings>,
    private val output: StateFlow<AudioOutputState>,
    private val publish: (line: String?) -> Unit
) {

    private var loop: Job? = null
    private var loader: Job? = null

    @Volatile private var loadedSongId: String? = null
    @Volatile private var lines: List<LyricLine> = emptyList()

    private var lastLine: String? = null
    private var lastActive = false
    private var lastSong: String? = null

    fun start() {
        if (loop?.isActive == true) return
        loop = scope.launch {
            while (isActive) {
                runCatching { tick() }
                delay(TICK_MS)
            }
        }
    }

    fun stop() {
        loop?.cancel()
        loop = null
        loader?.cancel()
        loader = null
        emit(null, active = false, song = null)
    }

    private fun tick() {
        val s = settings.value
        val out = output.value
        if (!s.isActive(out)) {
            emit(null, active = false, song = null)
            return
        }

        val item = player.currentMediaItem
        val songId = item?.mediaId
        if (songId != loadedSongId) {
            loadedSongId = songId
            lines = emptyList()
            loader?.cancel()
            if (songId != null) {
                loader = scope.launch {
                    val result = runCatching { lyrics.fetchById(songId) }.getOrNull()
                    lines = if (result is LyricsRepository.LyricsResult.Found) result.lines else emptyList()
                }
            }
        }

        val text = CarLyricCursor(lines, s.offsetMs)
            .lineAt(player.currentPosition)
            ?.let { CarLyricsFormatter.clip(it, s.maxChars) }

        emit(text, active = text != null, song = item?.mediaMetadata?.title?.toString())
    }

    private fun emit(line: String?, active: Boolean, song: String?) {
        if (line == lastLine && active == lastActive && song == lastSong) return
        lastLine = line
        lastActive = active
        lastSong = song
        CarAudioBus.publish(line, active, song)
        publish(line)
    }

    private companion object {
        /**
         * 采样周期。歌词行平均 3~5 秒一换，200ms 采样足够跟手，
         * 又不会让主线程（MediaSession 必须主线程）变成轮询机器。
         */
        const val TICK_MS = 200L
    }
}
