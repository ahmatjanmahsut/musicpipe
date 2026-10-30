package com.musicpipe.player.media.eq

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 进程级 EQ 控制器（v1.16.0）。
 *
 * 为什么是一个全局对象而不是放在 Service 里：
 *  - **播放器在 Service 里**（ExoPlayer 在 PlaybackService.onCreate 创建），
 *    UI 在 Activity 里，两边需要共享同一份"当前 EQ 设置"；
 *  - 同一进程，直接共享对象比走 MediaController 的 custom command 简单得多，
 *    也不需要序列化 5 个浮点数。
 *
 * 只负责三件事：记住"用户想要的设置"、记住"当前挂上的音频会话"、
 * 在会话出现/变化时把设置推给 [EqualizerEngine]。
 * 持久化不在这里（见 data/repo/EqRepository），这样控制器不依赖 DataStore。
 */
class EqController {

    /**
     * 运行时状态（给 UI 显示"设备 N 段 / 已生效 / 不支持"）。
     * 与"用户设置"分开：设置是用户的意图，这里描述的是本机实际能不能做到。
     */
    data class RuntimeState(
        val attached: Boolean = false,
        val bandHz: List<Int> = emptyList(),
        val minDb: Float = EqBands.MIN_DB,
        val maxDb: Float = EqBands.MAX_DB,
        /** 挂载失败的原因（本机不支持 / 播放器还没起来时为 null）。 */
        val error: String? = null
    )

    private val engine = EqualizerEngine()
    private val _state = MutableStateFlow(RuntimeState())
    val state: StateFlow<RuntimeState> = _state.asStateFlow()

    /** 用户意图（没有会话时先记住，等会话起来再应用）。 */
    @Volatile private var enabled = false
    @Volatile private var gains: List<Float> = EqBands.zero
    @Volatile private var makeupDb = 0f

    /** PlaybackService 在 ExoPlayer 建好之后调用（含"当前会话 id"这一事实）。 */
    fun onSession(sessionId: Int) {
        val info = engine.attach(sessionId)
        _state.value = if (info == null) {
            RuntimeState(error = "本机未能挂上系统均衡器（模拟器/部分机型不支持）")
        } else {
            RuntimeState(attached = true, bandHz = info.bandHz, minDb = info.minDb, maxDb = info.maxDb)
        }
        engine.apply(enabled, gains, makeupDb)
    }

    /** 应用（并记住）一套设置；还没挂上会话时只记住。 */
    fun apply(enabled: Boolean, gains: List<Float>, makeupDb: Float) {
        this.enabled = enabled
        this.gains = EqBands.normalize(gains)
        this.makeupDb = EqBands.clampMakeup(makeupDb)
        engine.apply(this.enabled, this.gains, this.makeupDb)
    }

    /** 服务销毁时调用：释放效果器，避免下次 attach 撞上"同会话已有实例"。 */
    fun detach() {
        engine.release()
        _state.value = RuntimeState()
    }
}
