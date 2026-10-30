package com.musicpipe.player.service

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import androidx.media3.common.AudioAttributes
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.musicpipe.player.MusicPipeApplication
import com.musicpipe.player.media.car.CarAudioBus
import com.musicpipe.player.media.car.CarLyricsEngine
import com.musicpipe.player.media.car.CarLyricsPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Media3 MediaSessionService (docs/01 §7: FOREGROUND_SERVICE_MEDIA_PLAYBACK)。
 *
 * v1.16.0 的两处结构性改动：
 *
 * **1）播放器被一层 [CarLyricsPlayer] 包住**（车机显示歌词）。
 * MediaSession 面向的是包装层，于是 `getMediaMetadata()` 返回的 title/artist/album
 * 可以带上"当前歌词行"——这是蓝牙 AVRCP 上唯一能到达车机屏幕的通道。
 * 队列、进度、播放控制全部原样透传，App 自己的界面读的是曲库里的 Song，不受影响。
 *
 * **2）音频会话 id 由我们自己生成**（均衡器）。
 * ExoPlayer 默认在音频输出初始化时才分配会话 id，而 AudioEffect 必须挂在已知会话上。
 * 这里先用 AudioManager.generateAudioSessionId() 取一个 id 交给播放器，
 * 效果器就能在服务创建时立刻挂好，用户点开均衡器开关时不需要等"下一条音频流"。
 */
// ExoPlayer.setAudioSessionId / getAudioSessionId 属于 Media3 的 unstable API（lint: UnsafeOptInUsageError）。
// 这里消费掉这个 opt-in，避免它传染给整个服务类。
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class PlaybackService : MediaSessionService() {

    private var player: ExoPlayer? = null
    private var injectingPlayer: CarLyricsPlayer? = null
    private var mediaSession: MediaSession? = null
    private var carLyrics: CarLyricsEngine? = null

    /**
     * 服务自己的作用域（主线程）：MediaSession / Player 的回调都要求与创建时同一个 looper，
     * 所以这里必须是 Main.immediate，不能换成 Default。
     */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val container by lazy { (application as MusicPipeApplication).container }

    override fun onCreate() {
        super.onCreate()
        val exo = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.DEFAULT, /* handleAudioFocus= */ true)
            .setHandleAudioBecomingNoisy(true)
            .build()
        val wrapper = CarLyricsPlayer(exo)
        player = exo
        injectingPlayer = wrapper

        attachEqualizer(exo)

        mediaSession = MediaSession.Builder(this, wrapper).build()

        // v1.16.0：车载歌词引擎。改设置不需要通知：它自己 collect 同一份 DataStore 流。
        carLyrics = CarLyricsEngine(
            scope = serviceScope,
            player = exo,
            lyrics = container.lyricsRepository,
            settings = container.carLyricsRepository.settings,
            output = container.outputMonitor.state,
            publish = { line ->
                val s = container.carLyricsRepository.settings.value
                wrapper.setInjection(line, s.layout, s.maxChars)
            }
        ).also { it.start() }

        // 换"字段位置/单行字数"时要立刻按新配置重推一次元数据，
        // 否则用户改完设置得等下一句歌词才生效（会以为设置没生效）。
        serviceScope.launch {
            container.carLyricsRepository.settings.collect { s ->
                wrapper.setInjection(CarAudioBus.line.value, s.layout, s.maxChars)
            }
        }
    }

    /** 把均衡器挂到本服务的音频会话上（失败只让 UI 显示"本机不支持"，不影响播放）。 */
    private fun attachEqualizer(exo: ExoPlayer) {
        val generated = runCatching {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.generateAudioSessionId()
        }.getOrDefault(0)
        if (generated > 0) {
            // setAudioSessionId 必须在 prepare 之前调用，否则 ExoPlayer 会抛 IllegalStateException。
            runCatching { exo.setAudioSessionId(generated) }
        }
        val sessionId = if (generated > 0) generated else exo.audioSessionId
        runCatching { container.eqController.onSession(sessionId) }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        // 顺序：先停"每 200ms 采样"的引擎，再放开效果器，最后释放会话与播放器。
        runCatching { carLyrics?.stop() }
        carLyrics = null
        runCatching { container.eqController.detach() }
        mediaSession?.let { runCatching { it.release() } }
        mediaSession = null
        runCatching { player?.release() }
        player = null
        injectingPlayer = null
        serviceScope.cancel()
        super.onDestroy()
    }
}
