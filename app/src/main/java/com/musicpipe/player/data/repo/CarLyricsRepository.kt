package com.musicpipe.player.data.repo

import com.musicpipe.player.data.prefs.PrefsRepository
import com.musicpipe.player.media.car.AudioOutputState
import com.musicpipe.player.media.car.CarAudioBus
import com.musicpipe.player.media.car.CarLyricsLayout
import com.musicpipe.player.media.car.CarLyricsMode
import com.musicpipe.player.media.car.CarLyricsSettings
import com.musicpipe.player.media.car.OutputDeviceMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 车载歌词的读写入口（v1.16.0）。
 *
 * 读：DataStore 设置 + 输出设备监视 + 播放服务广播的"当前注入行"，合成一个 UI 状态。
 * 写：只写 DataStore —— **服务侧自己 collect 同一份设置**，所以设置页改完立刻生效，
 * 不需要任何进程间通知。
 */
class CarLyricsRepository(
    private val prefs: PrefsRepository,
    private val monitor: OutputDeviceMonitor,
    private val scope: CoroutineScope
) {

    data class Ui(
        val settings: CarLyricsSettings = CarLyricsSettings(),
        val output: AudioOutputState = AudioOutputState(),
        /** 正在向车机注入歌词（模式命中 + 有歌词行）。 */
        val active: Boolean = false,
        /** 当前注入的歌词行（设置页做"车机会显示什么"的预览）。 */
        val line: String? = null,
        val song: String? = null
    ) {
        val shouldBeActive: Boolean get() = settings.isActive(output)
    }

    /** 服务侧（CarLyricsEngine）直接消费这一条。 */
    val settings: StateFlow<CarLyricsSettings> =
        prefs.carLyricsSettings.stateIn(scope, SharingStarted.Eagerly, CarLyricsSettings())

    val state: StateFlow<Ui> = combine(
        settings,
        monitor.state,
        CarAudioBus.line,
        CarAudioBus.active,
        CarAudioBus.song
    ) { s, out, line, active, song ->
        Ui(settings = s, output = out, active = active, line = line, song = song)
    }.stateIn(scope, SharingStarted.Eagerly, Ui())

    fun setMode(mode: CarLyricsMode) {
        scope.launch { prefs.setCarLyricsMode(mode) }
    }

    fun setLayout(layout: CarLyricsLayout) {
        scope.launch { prefs.setCarLyricsLayout(layout) }
    }

    fun setOffsetMs(ms: Long) {
        scope.launch { prefs.setCarLyricsOffset(ms) }
    }

    fun setMaxChars(n: Int) {
        scope.launch { prefs.setCarLyricsMaxChars(n) }
    }

    /** 手动重采一次输出设备（用户刚插上/连上蓝牙时点一下）。 */
    fun refreshOutput() = monitor.refresh()
}
