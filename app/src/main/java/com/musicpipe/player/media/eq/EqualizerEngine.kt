package com.musicpipe.player.media.eq

import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.util.Log

/**
 * 系统均衡器（AudioEffect）的薄封装 —— 全部 Android API 都收在这里（v1.16.0）。
 *
 * 为什么用 `android.media.audiofx.Equalizer` 而不是在 ExoPlayer 里自己做
 * AudioProcessor 链路：一是 Android 的 5/10 段硬件均衡器本来就是系统给的标准能力，
 * 自己写 FFT 既增加延迟又要处理位深转换；二是 A2DP / 耳机 / 外放都走同一个音频会话，
 * 挂在这里对"蓝牙车机"和"蓝牙耳机"是同一套代码，无需区别对待。
 *
 * 稳定性约定：**任何一次系统调用都可能抛**（部分机型/模拟器不支持某个效果、会话没起来），
 * 所以每个调用都包了 runCatching；失败只让 [attach] 返回 null，绝不让播放服务崩掉。
 */
class EqualizerEngine {

    /** 挂上之后设备的能力自述（频段数、中心频率、可行增益范围）。 */
    data class Info(
        val bandCount: Int,
        /** 各频段中心频率（Hz）。 */
        val bandHz: List<Int>,
        val minDb: Float,
        val maxDb: Float
    )

    private var equalizer: Equalizer? = null
    private var makeup: LoudnessEnhancer? = null
    private var info: Info? = null

    /** 当前是否已挂上一个有效会话。 */
    val attached: Boolean get() = equalizer != null

    val attachedInfo: Info? get() = info

    /**
     * 挂到指定音频会话。返回 null 表示本机不支持（或被别的效果器占满优先级）。
     *
     * 调用方（PlaybackService）在 ExoPlayer 建好之后立刻调用一次；
     * 重复调用会先释放旧的，所以换会话是安全的。
     */
    fun attach(sessionId: Int): Info? {
        release()
        if (sessionId <= 0) return null
        return try {
            val eq = Equalizer(0, sessionId)
            val n = eq.numberOfBands.toInt()
            if (n <= 0) {
                runCatching { eq.release() }
                return null
            }
            val range = eq.bandLevelRange          // [min, max]，单位 millibel
            val hz = (0 until n).map { band ->
                runCatching { eq.getCenterFreq(band.toShort()) / 1000 }.getOrDefault(0)
            }
            info = Info(
                bandCount = n,
                bandHz = hz,
                minDb = EqBands.millibelToDb(range[0].toInt()),
                maxDb = EqBands.millibelToDb(range[1].toInt())
            )
            equalizer = eq
            // 增益补偿：EQ 削掉能量后整体会变轻，用 LoudnessEnhancer 补回来（只增不减，0 = 关闭）。
            makeup = runCatching { LoudnessEnhancer(sessionId) }.getOrNull()
            info
        } catch (t: Throwable) {
            Log.w(TAG, "Equalizer attach failed for session " + sessionId, t)
            null
        }
    }

    /**
     * 应用一套设置。canonical 5 段曲线会先重采样到设备真实频段再下发。
     *
     * 注意：即使 [enabled] 为 false 也要能被调用（用于关闭），此时只关效果器。
     */
    fun apply(enabled: Boolean, gains: List<Float>, makeupDb: Float) {
        val eq = equalizer ?: return
        val i = info ?: return
        try {
            if (!enabled) {
                runCatching { eq.enabled = false }
                runCatching { makeup?.enabled = false }
                return
            }
            val levels = EqBands.resample(gains, i.bandHz)
            val minMb = EqBands.dbToMillibel(i.minDb)
            val maxMb = EqBands.dbToMillibel(i.maxDb)
            levels.forEachIndexed { band, db ->
                val mb = EqBands.dbToMillibel(db).coerceIn(minMb, maxMb)
                runCatching { eq.setBandLevel(band.toShort(), mb.toShort()) }
            }
            runCatching { eq.enabled = true }
            val target = EqBands.dbToMillibel(EqBands.clampMakeup(makeupDb))
            makeup?.let { m ->
                runCatching {
                    m.setTargetGain(target)
                    m.enabled = target > 0
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Equalizer apply failed", t)
        }
    }

    fun release() {
        runCatching { equalizer?.enabled = false }
        runCatching { makeup?.enabled = false }
        runCatching { equalizer?.release() }
        runCatching { makeup?.release() }
        equalizer = null
        makeup = null
        info = null
    }

    private companion object {
        const val TAG = "MusicPipeEq"
    }
}
