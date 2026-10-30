package com.musicpipe.player.media.car

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 播放服务 → UI 的单向状态广播（v1.16.0）。
 *
 * 车载歌词是"服务侧算、界面侧看"的功能：当前歌词行由 PlaybackService 里的
 * [CarLyricsEngine] 算出并注入媒体元数据，设置页需要把**同一行**显示出来做预览，
 * 让用户在车里之前就能确认"待会儿车机会显示什么"。
 *
 * 用进程内单例而不是 Binder：同一进程、单向几字节的数据，走 Binder 只是增加失败面。
 */
object CarAudioBus {

    private val _line = MutableStateFlow<String?>(null)

    /** 当前注入到车机的歌词行（null = 没有注入，车机看到的是真实歌名）。 */
    val line: StateFlow<String?> = _line.asStateFlow()

    private val _active = MutableStateFlow(false)

    /** 注入是否正在生效（模式命中 + 有歌词行）。 */
    val active: StateFlow<Boolean> = _active.asStateFlow()

    /** 正在注入的歌名（用于设置页"当前注入到：xxx"）。 */
    private val _song = MutableStateFlow<String?>(null)
    val song: StateFlow<String?> = _song.asStateFlow()

    fun publish(line: String?, active: Boolean, song: String? = null) {
        _line.value = line
        _active.value = active
        _song.value = song
    }
}
