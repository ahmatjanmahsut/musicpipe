package com.musicpipe.player.media.car

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 输出设备监视器（v1.16.0）。
 *
 * 只用 [AudioManager] 的公开 API：**不需要任何蓝牙权限**，因此也不会在开启车载歌词时
 * 弹一个用户看不懂的运行时授权框。代价是拿到的名字是 HAL 给的产品名
 * （对车机通常是 "Mazda"/"MZD Connect"，偶尔为空）——为空时分类会落到
 * BT_OTHER，用户可以手动把车载歌词切成"始终开启"兜底。
 *
 * 数据来源选择理由：AudioManager.getDevices(GET_DEVICES_OUTPUTS) 是"当前路由表"，
 * 比 BluetoothAdapter 的已连接列表更贴近"声音真的从哪儿出来"这件事
 * （车机连着蓝牙但音频仍走外放的情况是存在的）。
 */
class OutputDeviceMonitor(
    private val context: Context,
    private val onChanged: () -> Unit = {}
) {

    private val _state = MutableStateFlow(AudioOutputState())
    val state: StateFlow<AudioOutputState> = _state.asStateFlow()

    private var registered = false

    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            refresh()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            refresh()
        }
    }

    private fun audioManager(): AudioManager? =
        runCatching { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }.getOrNull()

    /** 注册回调 + 立即采一次。重复调用安全。 */
    fun start() {
        if (!registered) {
            val am = audioManager()
            if (am != null) {
                val ok = runCatching {
                    am.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
                }.isSuccess
                registered = ok
            }
        }
        refresh()
    }

    fun stop() {
        if (!registered) return
        runCatching { audioManager()?.unregisterAudioDeviceCallback(callback) }
        registered = false
    }

    /** 主动采样一次（UI 进设置页时也可以踢一下，保证显示的是最新路由）。 */
    fun refresh() {
        val am = audioManager() ?: return
        val devices = runCatching {
            am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()
        }.getOrNull() ?: return
        val next = pick(devices)
        if (next != _state.value) {
            _state.value = next
            runCatching { onChanged() }
        }
    }

    private fun pick(devices: List<AudioDeviceInfo>): AudioOutputState {
        // 优先级：蓝牙 A2DP > USB/有线耳机 > 内置外放。
        // 蓝牙优先是刻意的：车机场景用户最关心的就是"现在是不是走车机"。
        devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }?.let { d ->
            return AudioOutputClassifier.classify("bluetooth_a2dp", nameOf(d), isA2dp = true)
        }
        devices.firstOrNull { d ->
            // TYPE_USB_HEADSET 是 API 26 才有的常量（这里是内联常量，API 24 上比较也不会崩，
            // 但为了不误判还是显式按系统版本收窄）。
            val usbHeadset = android.os.Build.VERSION.SDK_INT >= 26 &&
                d.type == AudioDeviceInfo.TYPE_USB_HEADSET
            usbHeadset ||
                d.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                d.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                d.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
        }?.let { d ->
            return AudioOutputClassifier.classify("wired_headset", nameOf(d), isA2dp = false)
        }
        devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }?.let { d ->
            return AudioOutputClassifier.classify("builtin_speaker", nameOf(d), isA2dp = false)
        }
        return AudioOutputState()
    }

    private fun nameOf(d: AudioDeviceInfo): String = runCatching {
        d.productName?.toString()?.trim().orEmpty()
    }.getOrElse {
        Log.w("MusicPipeOut", "productName unavailable", it)
        ""
    }
}
