package com.musicpipe.player.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.musicpipe.player.data.remote.ApiClientFactory
import com.musicpipe.player.data.remote.NetBench
import com.musicpipe.player.data.remote.ServerConfig
import com.musicpipe.player.data.remote.dto.SystemDto
import com.musicpipe.player.di.AppContainer
import com.musicpipe.player.ui.theme.AppStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    /** Router-merged contract: a single gateway URL only. */
    data class Settings(
        val serverUrl: String = "",
        val themeMode: String = "system",
        /** v1.13.0：界面风格 id，见 AppStyle。 */
        val appStyle: String = "awwwards"
    )

    /** v1.5.0：设置页「网络测试」的完整状态。 */
    data class NetworkTest(
        val running: Boolean = false,
        val rtt: NetBench.RttResult? = null,
        val speed: NetBench.SpeedResult? = null,
        /** 失败原因（测速取不到歌曲 / 请求异常）；成功时为 null。 */
        val error: String? = null,
        /** 最近一次测完的时间戳（毫秒），UI 用来显示"刚测过"。 */
        val testedAt: Long? = null
    )

    val settings: StateFlow<Settings> = combine(
        container.prefs.serverUrl,
        container.prefs.themeMode,
        container.prefs.appStyle
    ) { url, theme, style -> Settings(url, theme, style) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Settings())

    /** GET /api/v1/system snapshot: counts of library tracks / queued tasks / imported. */
    private val _systemInfo = MutableStateFlow<SystemDto?>(null)
    val systemInfo: StateFlow<SystemDto?> = _systemInfo

    private val _systemBusy = MutableStateFlow(false)
    val systemBusy: StateFlow<Boolean> = _systemBusy

    private val _netTest = MutableStateFlow(NetworkTest())
    val netTest: StateFlow<NetworkTest> = _netTest

    init { refreshSystem() }

    fun refreshSystem() {
        if (_systemBusy.value) return
        _systemBusy.value = true
        viewModelScope.launch {
            try {
                _systemInfo.value = container.netRepository.system()
            } catch (_: Throwable) {
                // keep the last snapshot on failure
            }
            _systemBusy.value = false
        }
    }

    /**
     * 设置页「网络测试」：延迟 + 下载带宽一次跑完。
     *
     * 顺序有讲究：先测 RTT（3 次小请求，快），再测带宽。反过来的话带宽测试把连接全占满，
     * RTT 会被自己的大流量干扰，测出虚假的高延迟。
     *
     * 带宽测试需要一首真实歌曲：优先用当前正在播放的，否则从曲库取第一首。取不到歌
     * （空曲库 / 未登录）时只显示延迟，误差文案说明原因，而不是整块失败。
     */
    fun runNetworkTest() {
        if (_netTest.value.running) return
        _netTest.value = NetworkTest(running = true)
        viewModelScope.launch {
            var error: String? = null
            var rtt: NetBench.RttResult? = null
            var speed: NetBench.SpeedResult? = null
            try {
                // 用自己的 client（与 App 请求同一条连接池），测出来的才是真实体感
                val client = ApiClientFactory.sharedClient
                rtt = NetBench.measureRtt(client, ServerConfig.restBaseUrl)

                val song = container.playerRepository.state.value.currentSong
                    ?: runCatching { container.songRepository.songs(limit = 1).firstOrNull() }.getOrNull()
                if (song == null) {
                    error = "曲库为空，仅完成延迟测试"
                } else {
                    speed = runCatching {
                        NetBench.measureSpeed(client, song.streamUrl)
                    }.getOrElse {
                        error = "下载测速失败：${it.message ?: "网络异常"}"
                        null
                    }
                }
            } catch (t: Throwable) {
                error = "测试失败：${t.message ?: "网络异常"}"
            }
            _netTest.value = NetworkTest(
                running = false,
                rtt = rtt,
                speed = speed,
                error = error,
                testedAt = System.currentTimeMillis()
            )
        }
    }

    /** POST /api/v1/scan — asks the router to start a Navidrome library scan. */
    fun triggerScan() {
        if (_systemBusy.value) return
        _systemBusy.value = true
        viewModelScope.launch {
            try {
                container.netRepository.scan()
                _systemInfo.value = container.netRepository.system()
            } catch (_: Throwable) {
                // scan trigger is best-effort
            }
            _systemBusy.value = false
        }
    }

    fun setServerUrl(url: String) { viewModelScope.launch { container.prefs.setServerUrl(url) } }
    fun setThemeMode(mode: String) { viewModelScope.launch { container.prefs.setThemeMode(mode) } }

    /** v1.13.0：切换界面风格。写入 DataStore 后由 MainActivity 的 collect 驱动整棵树重建。 */
    fun setAppStyle(style: AppStyle) {
        viewModelScope.launch { container.prefs.setAppStyle(style.id) }
    }
    fun logout() {
        viewModelScope.launch {
            container.lyricsRepository.clear()
            container.authRepository.logout()
        }
    }

    companion object {
        fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = SettingsViewModel(container) as T
        }
    }
}
