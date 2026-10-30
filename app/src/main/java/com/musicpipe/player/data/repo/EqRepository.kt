package com.musicpipe.player.data.repo

import com.musicpipe.player.data.prefs.PrefsRepository
import com.musicpipe.player.media.car.AudioOutputKind
import com.musicpipe.player.media.car.AudioOutputState
import com.musicpipe.player.media.car.OutputDeviceMonitor
import com.musicpipe.player.media.eq.EqBands
import com.musicpipe.player.media.eq.EqController
import com.musicpipe.player.media.eq.EqPreset
import com.musicpipe.player.media.eq.EqPresets
import com.musicpipe.player.media.eq.EqProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 均衡器的"用户意图"层（v1.16.0）。
 *
 * 三层分工，别混：
 *   - **DataStore**（[PrefsRepository]）：用户选了什么，进程死了也要记得。
 *   - **[EqRepository]**（本类）：把"设置 + 输出设备 + 效果器能不能用"合成一个 UI 状态，
 *     并在设置变化时推给 [EqController]。
 *   - **[EqController] / [com.musicpipe.player.media.eq.EqualizerEngine]**：真正跟
 *     android.media.audiofx 打交道的那一层（由 PlaybackService 提供音频会话）。
 *
 * 推荐逻辑刻意只做"建议 + 一键应用"，不自动改：eqAutoSwitch 打开后才跟随设备切换。
 * 这是被用户骂过很多次的经典坑 —— 播放中自动换预设，人会以为"音质突然变了"。
 */
class EqRepository(
    private val prefs: PrefsRepository,
    private val controller: EqController,
    private val monitor: OutputDeviceMonitor,
    private val scope: CoroutineScope
) {

    data class UiState(
        val enabled: Boolean = false,
        /** null = 用户手动改过曲线，不对应任何预设（UI 显示"自定义"）。 */
        val presetId: String? = EqPresets.FLAT_ID,
        val presetName: String = "平直",
        val gains: List<Float> = EqBands.zero,
        val makeupDb: Float = 0f,
        val customPresets: List<EqPreset> = emptyList(),
        val autoSwitch: Boolean = false,
        /** 效果器运行时能力（本机是否真的挂上了）。 */
        val runtime: EqController.RuntimeState = EqController.RuntimeState(),
        val output: AudioOutputState = AudioOutputState(),
        /** 按当前输出设备给出的建议预设（已经就是当前预设时为 null）。 */
        val recommendation: EqPreset? = null
    ) {
        val presets: List<EqPreset> get() = EqPresets.builtIns + customPresets
        val isCustomEdit: Boolean get() = presetId == null
    }

    private data class Bag(
        val enabled: Boolean,
        val profile: EqProfile,
        val autoSwitch: Boolean,
        val custom: List<EqPreset>,
        val output: AudioOutputState
    )

    private val custom = MutableStateFlow<List<EqPreset>>(emptyList())

    val state: StateFlow<UiState> = combine(
        prefs.eqEnabled,
        prefs.eqProfile,
        prefs.eqAutoSwitch,
        custom,
        monitor.state
    ) { enabled, profile, autoSwitch, customList, output ->
        Bag(enabled, profile, autoSwitch, customList, output)
    }.combine(controller.state) { bag, runtime ->
        val all = EqPresets.builtIns + bag.custom
        val current = all.firstOrNull { it.id == bag.profile.presetId }
        UiState(
            enabled = bag.enabled,
            presetId = bag.profile.presetId,
            presetName = current?.name ?: "自定义",
            gains = bag.profile.gains,
            makeupDb = bag.profile.makeupDb,
            customPresets = bag.custom,
            autoSwitch = bag.autoSwitch,
            runtime = runtime,
            output = bag.output,
            recommendation = recommendationFor(bag.profile.presetId, bag.output)
        )
    }.stateIn(scope, SharingStarted.Eagerly, UiState())

    init {
        // 设置一变就推到效果器（未挂会话时 controller 会先记住，等会话起来再应用）。
        scope.launch {
            state.collect { ui -> controller.apply(ui.enabled, ui.gains, ui.makeupDb) }
        }
        // 自定义预设列表：DataStore -> 内存（UI 只读 state.customPresets）。
        scope.launch {
            prefs.eqCustomPresets.collect { custom.value = it }
        }
        // 跟随设备自动切换（默认关闭）。只在"目标预设与当前不同"时切换，避免来回抖。
        scope.launch {
            combine(monitor.state, prefs.eqAutoSwitch) { out, auto -> out to auto }
                .collect { (out, auto) ->
                    if (!auto) return@collect
                    if (out.kind == AudioOutputKind.UNKNOWN || out.kind == AudioOutputKind.SPEAKER) return@collect
                    val target = EqPresets.forOutput(out.kind.toEqTarget(), out.name)
                    if (target.id != state.value.presetId) selectPreset(target.id)
                }
        }
    }

    /** 建议只在"有蓝牙/有线输出"时给：外放给建议没有意义。 */
    private fun recommendationFor(presetId: String?, output: AudioOutputState): EqPreset? {
        if (output.kind == AudioOutputKind.UNKNOWN || output.kind == AudioOutputKind.SPEAKER) return null
        val suggestion = EqPresets.forOutput(output.kind.toEqTarget(), output.name)
        return if (suggestion.id == presetId) null else suggestion
    }

    // ---------------- 交互 ----------------

    fun setEnabled(enabled: Boolean) {
        scope.launch { prefs.setEqEnabled(enabled) }
    }

    /**
     * 选预设。
     *
     * **顺手把总开关打开**：用户点了"Mazda 车机"却听不出任何变化，第一反应是"这功能坏了"，
     * 而不是"我忘了开总开关"。反过来（选预设却自动关掉）没有这种直觉，所以不做。
     */
    fun selectPreset(id: String) {
        val preset = state.value.presets.firstOrNull { it.id == id } ?: return
        val needEnable = !state.value.enabled
        scope.launch {
            prefs.setEqProfile(EqProfile(preset.id, preset.gains, preset.makeupDb))
            if (needEnable) prefs.setEqEnabled(true)
        }
    }

    /** 应用"建议预设"。 */
    fun applyRecommendation() {
        state.value.recommendation?.let { selectPreset(it.id) }
    }

    /**
     * 拖动中的实时试听：只推给效果器，不落盘。
     *
     * 分开的理由很实在：一次拖动会产生几十次回调，每次都写 DataStore 就是几十次
     * 磁盘事务 + 几十次 UI 状态刷新，手感直接变迟钝。
     */
    fun previewBand(index: Int, db: Float) {
        val ui = state.value
        controller.apply(ui.enabled, ui.gains.withBand(index, db), ui.makeupDb)
    }

    fun previewMakeup(db: Float) {
        val ui = state.value
        controller.apply(ui.enabled, ui.gains, EqBands.clampMakeup(db))
    }

    /** 松手后落盘（presetId 置 null：这段曲线已经是用户自己的了）。 */
    fun setBand(index: Int, db: Float) {
        val ui = state.value
        val gains = ui.gains.withBand(index, db)
        scope.launch { prefs.setEqProfile(EqProfile(null, gains, ui.makeupDb)) }
    }

    fun setMakeup(db: Float) {
        val ui = state.value
        scope.launch { prefs.setEqProfile(EqProfile(null, ui.gains, EqBands.clampMakeup(db))) }
    }

    fun resetBands() {
        val ui = state.value
        scope.launch { prefs.setEqProfile(EqProfile(null, EqBands.zero, ui.makeupDb)) }
    }

    fun setAutoSwitch(on: Boolean) {
        scope.launch { prefs.setEqAutoSwitch(on) }
    }

    /** 把当前曲线存成自定义预设并立刻选中。 */
    fun saveCustomPreset(name: String) {
        val ui = state.value
        val clean = name.trim().ifBlank { "我的预设" }
        val preset = EqPreset(
            id = "user_" + System.currentTimeMillis(),
            name = clean.take(24),
            tagline = "自定义 · " + gainsLabel(ui.gains),
            gains = ui.gains,
            makeupDb = ui.makeupDb,
            builtIn = false
        )
        val next = ui.customPresets + preset
        scope.launch {
            prefs.setEqCustomPresets(next)
            prefs.setEqProfile(EqProfile(preset.id, preset.gains, preset.makeupDb))
        }
    }

    fun deleteCustomPreset(id: String) {
        val ui = state.value
        val next = ui.customPresets.filterNot { it.id == id }
        scope.launch {
            prefs.setEqCustomPresets(next)
            // 删的正是当前这条：回落到平直，避免"显示着一个不存在的预设"。
            if (ui.presetId == id) {
                val flat = EqPresets.default()
                prefs.setEqProfile(EqProfile(flat.id, flat.gains, flat.makeupDb))
            }
        }
    }

    private companion object {
        fun List<Float>.withBand(index: Int, db: Float): List<Float> {
            if (index !in indices) return this
            return toMutableList().also { it[index] = EqBands.clampGain(db) }
        }

        /** 曲线的一行摘要（"60-3 / 230-1.5 / …"），给预设列表当副标题。 */
        fun gainsLabel(gains: List<Float>): String =
            EqBands.canonical.zip(EqBands.normalize(gains)).joinToString(" ") { (spec, db) ->
                spec.label + (if (db > 0f) "+" else "") + trim(db)
            }

        fun trim(v: Float): String {
            val r = Math.round(v * 10f) / 10f
            return if (r == r.toInt().toFloat()) r.toInt().toString() else r.toString()
        }
    }
}
