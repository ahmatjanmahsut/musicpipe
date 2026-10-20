package com.musicpipe.player.di

import android.content.Context
import com.musicpipe.player.data.local.AppDatabase
import com.musicpipe.player.data.prefs.PrefsRepository
import com.musicpipe.player.data.remote.ApiClientFactory
import com.musicpipe.player.data.remote.AuthTokens
import com.musicpipe.player.data.remote.ServerConfig
import com.musicpipe.player.data.repo.AuthRepository
import com.musicpipe.player.data.repo.FavoriteRepository
import com.musicpipe.player.data.repo.HistoryRepository
import com.musicpipe.player.data.repo.LyricsRepository
import com.musicpipe.player.data.repo.NetRepository
import com.musicpipe.player.data.repo.PlayerRepository
import com.musicpipe.player.data.repo.SongRepository
import com.musicpipe.player.data.repo.UpdateRepository
import com.musicpipe.player.data.repo.UserRepository
import com.musicpipe.player.media.MusicPlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Manual DI container — created once by MusicPipeApplication. */
class AppContainer(private val context: Context) {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

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

    val playerController = MusicPlayerController(context, appScope)
    val playerRepository = PlayerRepository(playerController, historyRepository, appScope)

    /**
     * v1.8.0：首帧之后才绑定播放服务（见 [MusicPlayerController] 的 pendingAction 说明）。
     * 重复调用安全：connect() 内部有 isConnected 短路。
     */
    fun startPlaybackEngine() {
        appScope.launch { runCatching { playerController.connect() } }
    }

    init {
        // Keep the runtime base URL in sync with DataStore (settings page can change it live).
        appScope.launch { prefs.serverUrl.collect { ServerConfig.restBaseUrl = it.trimEnd('/') } }
        // Mirror the persisted bearer token into memory so TokenInterceptor / ?token= URLs see it
        // before the first login on a cold start (getValue() is a synchronous StateFlow read).
        appScope.launch { AuthTokens.set(prefs.authTokenFlow.value) }
        appScope.launch { prefs.loadAuthToken().let { AuthTokens.set(it) } }
        appScope.launch { prefs.loadDownloadQuality() }
        // v1.8.0：**不再**在这里绑 MediaController。
        // 绑定会拉起 PlaybackService.onCreate -> 创建 ExoPlayer，是冷启动主线程上最贵的一步；
        // 而它在启动时毫无用处（用户还没点播放）。改由 MainActivity 在首帧后再调用
        // [startPlaybackEngine]，把这段时间让给首屏。
        // v1.7.0：切歌即把这一首的歌词读进内存（通常命中 SQLite 缓存，1~3ms）。
        // 于是用户切歌后立刻打开歌词页不再需要任何等待；未命中时这里只是一次后台请求，
        // 与 LyricsScreen 自己的加载并发，不会拖慢播放。
        playerRepository.onSongChangedHook = { song ->
            appScope.launch { runCatching { lyricsRepository.prewarm(listOf(song.id)) } }
        }
    }
}
