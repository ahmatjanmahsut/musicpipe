package com.musicpipe.player.di

import android.content.Context
import com.musicpipe.player.data.local.AppDatabase
import com.musicpipe.player.data.prefs.PrefsRepository
import com.musicpipe.player.data.remote.ApiClientFactory
import com.musicpipe.player.data.remote.AuthTokens
import com.musicpipe.player.data.remote.ServerConfig
import com.musicpipe.player.data.repo.AuthRepository
import com.musicpipe.player.data.repo.CarLyricsRepository
import com.musicpipe.player.data.repo.EqRepository
import com.musicpipe.player.data.repo.FavoriteRepository
import com.musicpipe.player.data.repo.HistoryRepository
import com.musicpipe.player.data.repo.LyricsRepository
import com.musicpipe.player.data.repo.NetRepository
import com.musicpipe.player.data.repo.PlayerRepository
import com.musicpipe.player.data.repo.SongRepository
import com.musicpipe.player.data.repo.UpdateRepository
import com.musicpipe.player.data.repo.UserRepository
import com.musicpipe.player.media.MusicPlayerController
import com.musicpipe.player.media.car.OutputDeviceMonitor
import com.musicpipe.player.media.eq.EqController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Manual DI container — created once by MusicPipeApplication. */
class AppContainer(private val context: Context) {

    /**
     * 全局后台作用域（v1.10.0 / P9）。
     *
     * 原来这里是 Dispatchers.Main.immediate：所有 prefs 收集、token 同步、曲库加载
     * 都跑在主线程上，一旦有一个稍重的操作（DataStore 首次读盘、Room 查询）就会抢主线程。
     * 现在改成 Default，数据类工作真正离开主线程。
     *
     * **播放控制不能跟着走**：MediaController / MediaSessionService 必须在主线程访问
     * （方法调用、回调、currentPosition 都要求与创建时同一 looper），所以另开 [playerScope]。
     */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 播放器专用主线程作用域：MediaController 的硬性要求，不要改成 Default。 */
    val playerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val prefs = PrefsRepository(context)
    private val db = AppDatabase.get(context)

    val api = ApiClientFactory.musicPipeApi(prefs)
    val downloadClient = ApiClientFactory.downloadClient(prefs)

    val authRepository = AuthRepository(api, prefs)
    val userRepository = UserRepository(api)
    val songRepository = SongRepository(api, prefs, db.librarySnapshotDao())
    val historyRepository = HistoryRepository(db.historyDao())
    val favoriteRepository = FavoriteRepository(db.favoriteDao())
    val lyricsRepository = LyricsRepository(api, db.lyricsCacheDao())
    val updateRepository = UpdateRepository(api, downloadClient, context, prefs)
    val netRepository = NetRepository(api)

    /** v1.7.0：封面/歌词预热器（曲库加载后由 HomeViewModel 触发）。 */
    val prewarmer = com.musicpipe.player.data.image.Prewarmer(context, lyricsRepository)

    /**
     * v1.16.0：均衡器 + 输出设备监视。
     *
     * 顺序有依赖：EqRepository 要读输出设备状态来做"建议预设"，所以监视器先建。
     * 监视器只做 AudioDeviceCallback 注册（系统回调，成本可忽略），
     * 放在 AppContainer 而不是 Service 里，界面上"当前输出：蓝牙车机"才不会要等播放开始才出现。
     */
    val outputMonitor = OutputDeviceMonitor(context)
    val eqController = EqController()
    val eqRepository = EqRepository(prefs, eqController, outputMonitor, appScope)
    val carLyricsRepository = CarLyricsRepository(prefs, outputMonitor, appScope)

    val playerController = MusicPlayerController(context, playerScope)
    val playerRepository = PlayerRepository(playerController, historyRepository, appScope)

    /**
     * v1.8.0：首帧之后才绑定播放服务（见 [MusicPlayerController] 的 pendingAction 说明）。
     * 重复调用安全：connect() 内部有 isConnected 短路。
     */
    fun startPlaybackEngine() {
        // 绑定必须在主线程发起（MediaController.Builder），故走 playerScope。
        playerScope.launch { runCatching { playerController.connect() } }
    }

    init {
        // Keep the runtime base URL in sync with DataStore (settings page can change it live).
        appScope.launch { prefs.serverUrl.collect { ServerConfig.restBaseUrl = it.trimEnd('/') } }
        // Mirror the persisted bearer token into memory so TokenInterceptor / ?token= URLs see it
        // before the first login on a cold start (getValue() is a synchronous StateFlow read).
        appScope.launch { AuthTokens.set(prefs.authTokenFlow.value) }
        appScope.launch { prefs.loadAuthToken().let { AuthTokens.set(it) } }
        appScope.launch { prefs.loadDownloadQuality() }
        // v1.16.0：开始监视输出设备（蓝牙车机/耳机判定 + EQ 推荐都靠它）。
        runCatching { outputMonitor.start() }
        // v1.8.0：**不再**在这里绑 MediaController。
        // 绑定会拉起 PlaybackService.onCreate -> 创建 ExoPlayer，是冷启动主线程上最贵的一步；
        // 而它在启动时毫无用处（用户还没点播放）。改由 MainActivity 在首帧后再调用
        // [startPlaybackEngine]，把这段时间让给首屏。
        // v1.7.0：切歌即把这一首的歌词读进内存（通常命中 SQLite 缓存，1~3ms）。
        // 于是用户切歌后立刻打开歌词页不再需要任何等待；未命中时这里只是一次后台请求，
        // 与 LyricsScreen 自己的加载并发，不会拖慢播放。
        // v1.10.0（C1）：登出（含 401 自动登出）后清掉本地曲库快照与歌词缓存，
        // 避免下一个会话首屏画的是上一个会话的数据。
        authRepository.onLogoutCleanup = {
            runCatching { songRepository.clearSnapshot() }
            runCatching { lyricsRepository.clear() }
        }
        playerRepository.onSongChangedHook = { song ->
            appScope.launch { runCatching { lyricsRepository.prewarm(listOf(song.id)) } }
        }
    }
}
