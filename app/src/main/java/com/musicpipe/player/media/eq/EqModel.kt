package com.musicpipe.player.media.eq

import kotlin.math.abs
import kotlin.math.log10

/**
 * 均衡器的**纯逻辑层**（v1.16.0）。
 *
 * 为什么单独抽出来：频段映射、增益钳位、预设编解码这些是最容易算错、也最值得回归测试的部分，
 * 而 android.media.audiofx.Equalizer 在 JVM 单测里根本跑不起来（android.jar 是桩）。
 * 所以这里不碰任何 Android API，设备侧只负责「把算好的 dB 塞进效果器」。
 */

/** 一个频段的定义：中心频率 + 显示用的短标签。 */
data class EqBandSpec(val hz: Int, val label: String)

object EqBands {

    const val COUNT = 5

    /** 增益上下限（dB）。±12dB 是消费级均衡器里最常用、也不会一耳朵失真的区间。 */
    const val MIN_DB = -12f
    const val MAX_DB = 12f

    /** 增益补偿（makeup gain）上限：EQ 削掉能量后用 LoudnessEnhancer 补回来，最多 +12dB。 */
    const val MAX_MAKEUP_DB = 12f

    /** Android AudioEffect 的等级单位是 millibel：1 dB = 100 mB。 */
    const val MB_PER_DB = 100f

    /**
     * 标准 5 段（与绝大多数 Android 设备的 Equalizer 默认频段一致）。
     *
     * 预设曲线一律按这 5 段书写，运行时再按设备真实频段重采样 ——
     * 于是"预设"与"设备能力"解耦：换一台 10 段的机器，曲线形状仍然一样。
     */
    val canonical: List<EqBandSpec> = listOf(
        EqBandSpec(60, "60"),
        EqBandSpec(230, "230"),
        EqBandSpec(910, "910"),
        EqBandSpec(3600, "3.6k"),
        EqBandSpec(14000, "14k"),
    )

    val zero: List<Float> get() = List(COUNT) { 0f }

    /** 钳到 ±12dB，并把 abs<0.05 的灰值归一成 0（避免浮点噪声让 UI 显示 "+0.0"）。 */
    fun clampGain(db: Float): Float {
        if (db.isNaN()) return 0f
        val v = db.coerceIn(MIN_DB, MAX_DB)
        return if (abs(v) < 0.05f) 0f else v
    }

    fun clampMakeup(db: Float): Float =
        if (db.isNaN()) 0f else db.coerceIn(0f, MAX_MAKEUP_DB)

    /** 规整到 canonical 长度：多余截断、缺位补 0，并逐段 clamp。 */
    fun normalize(gains: List<Float>): List<Float> {
        if (gains.isEmpty()) return zero
        return List(COUNT) { i -> clampGain(gains.getOrElse(i) { 0f }) }
    }

    /**
     * 把 canonical 曲线重采样到设备频段。
     *
     * 用**对数频率**线性插值而不是最近邻：人耳对频率的感知本来就是对数的，
     * 最近邻在 3.6k→14k 这种大跨度上会把整段曲线压成台阶。
     * 超出两端就取端点值（不做外推，避免高频端被无限放大）。
     */
    fun resample(gains: List<Float>, deviceHz: List<Int>): List<Float> {
        val ys = normalize(gains)
        if (deviceHz.isEmpty()) return ys
        val xs = canonical.map { log10(it.hz.toFloat()) }
        return deviceHz.map { hz -> interpolate(xs, ys, log10(hz.coerceAtLeast(1).toFloat())) }
    }

    private fun interpolate(xs: List<Float>, ys: List<Float>, x: Float): Float {
        if (xs.isEmpty() || ys.isEmpty()) return 0f
        if (x <= xs.first()) return ys.first()
        if (x >= xs.last()) return ys.last()
        for (i in 0 until minOf(xs.size, ys.size) - 1) {
            if (x >= xs[i] && x <= xs[i + 1]) {
                val span = xs[i + 1] - xs[i]
                if (span <= 0f) return ys[i]
                val t = (x - xs[i]) / span
                return ys[i] + (ys[i + 1] - ys[i]) * t
            }
        }
        return ys.last()
    }

    fun dbToMillibel(db: Float): Int = (db * MB_PER_DB).toInt()

    fun millibelToDb(mb: Int): Float = mb / MB_PER_DB
}
