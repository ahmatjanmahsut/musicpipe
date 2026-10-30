package com.musicpipe.player.media.car

import com.musicpipe.player.media.eq.EqTargetDevice

/**
 * 当前"声音去哪儿了"的分类（v1.16.0）。
 *
 * 车载歌词与 EQ 预设推荐都靠它：车机要显示歌词、车机要一套车内补偿曲线，
 * 而耳机要的是另一套。分类只依赖设备**名称**与系统给的设备类型，
 * 不需要任何蓝牙权限（见 [OutputDeviceMonitor]）。
 */
enum class AudioOutputKind {
    /** 没有任何输出设备信息（服务还没起来 / 系统不给）。 */
    UNKNOWN,
    /** 机身外放。 */
    SPEAKER,
    /** 有线耳机 / 3.5mm / USB 音频。 */
    WIRED,
    /** 判定为车机（头单元 / Mazda Connect 之类）。 */
    BT_CAR,
    /** 蓝牙耳机（含 TWS）。 */
    BT_HEADPHONE,
    /** 其它蓝牙输出（音箱、无法判定）。 */
    BT_OTHER;

    val isBluetooth: Boolean get() = this == BT_CAR || this == BT_HEADPHONE || this == BT_OTHER

    /** 中文短标签，UI 直接显示。 */
    val label: String
        get() = when (this) {
            UNKNOWN -> "未检测"
            SPEAKER -> "机身外放"
            WIRED -> "有线耳机"
            BT_CAR -> "蓝牙车机"
            BT_HEADPHONE -> "蓝牙耳机"
            BT_OTHER -> "蓝牙音频"
        }

    fun toEqTarget(): EqTargetDevice = when (this) {
        BT_CAR -> EqTargetDevice.CAR
        BT_HEADPHONE, WIRED -> EqTargetDevice.HEADPHONE
        else -> EqTargetDevice.GENERIC
    }
}

/** 一次采样得到的输出状态。 */
data class AudioOutputState(
    val kind: AudioOutputKind = AudioOutputKind.UNKNOWN,
    /** 设备名（车机通常就是 "Mazda"/"MZD Connect"，取不到时为空）。 */
    val name: String = "",
    /** 调试用的补充说明（系统给的 productName / 类型名）。 */
    val detail: String = ""
) {
    val isCar: Boolean get() = kind == AudioOutputKind.BT_CAR
}

/**
 * 纯函数分类器（可单测）。
 *
 * 规则很朴素：**先看系统给的类型，再看设备名关键词**。
 * 宁可判错也不要"不判"——判错用户能手动改成"始终开启"，
 * 不判则车机永远不显示歌词。
 */
object AudioOutputClassifier {

    /** 车机关键词：覆盖 Mazda 以及常见后装/前装头单元的叫法。 */
    private val CAR_KEYS = listOf(
        "mazda", "mzd", "mazda connect", "cmu", "head unit", "audio unit",
        "carplay", "android auto", "toyota", "lexus", "honda", "nissan", "infiniti",
        "bmw", "mercedes", "benz", "audi", "volkswagen", "vw ", "porsche", "volvo",
        "ford", "chevrolet", "buick", "cadillac", "hyundai", "kia", "subaru", "mitsubishi",
        "byd", "geely", "chery", "changan", "nio", "xpeng", "li auto", "tesla", "lynk",
        "车机", "车载", "汽车", "导航"
    )

    /** 耳机关键词。 */
    private val HEADPHONE_KEYS = listOf(
        "headphone", "headset", "earphone", "earbud", "buds", "airpods", "beats",
        "sony", "wh-1000", "wh1000", "wf-1000", "wf1000", "bose", "sennheiser",
        "jbl", "soundcore", "anker", "harman", "akg", "audio-technica", "铁三角",
        "edifier", "漫步者", "huawei freebuds", "freebuds", "mi buds", "redmi buds",
        "耳机", "耳塞"
    )

    /**
     * @param typeName 系统给出的输出类型名（AudioDeviceInfo.type 的可读名，用于兜底判断）
     * @param deviceName 设备名（可能为空）
     * @param isA2dp 是否为蓝牙 A2DP 输出
     */
    fun classify(typeName: String, deviceName: String, isA2dp: Boolean): AudioOutputState {
        val n = deviceName.lowercase().trim()
        val t = typeName.lowercase()
        if (!isA2dp) {
            val kind = when {
                t.contains("speaker") || t.contains("builtin_speaker") -> AudioOutputKind.SPEAKER
                t.contains("wired") || t.contains("headset") || t.contains("usb") -> AudioOutputKind.WIRED
                t.contains("bluetooth") -> AudioOutputKind.BT_OTHER
                deviceName.isBlank() -> AudioOutputKind.UNKNOWN
                else -> AudioOutputKind.UNKNOWN
            }
            return AudioOutputState(kind, deviceName, typeName)
        }
        // 蓝牙：先看名字像不像车机，再看耳机，最后兜底 BT_OTHER
        val car = CAR_KEYS.any { n.contains(it) }
        val phone = HEADPHONE_KEYS.any { n.contains(it) }
        val kind = when {
            car -> AudioOutputKind.BT_CAR
            phone -> AudioOutputKind.BT_HEADPHONE
            else -> AudioOutputKind.BT_OTHER
        }
        return AudioOutputState(kind, deviceName, typeName)
    }

    /** 仅从名称猜（用于"手动指定输出到车机"这类场景与单测）。 */
    fun guessFromName(deviceName: String, isA2dp: Boolean = true): AudioOutputKind =
        classify("bluetooth_a2dp", deviceName, isA2dp).kind
}
