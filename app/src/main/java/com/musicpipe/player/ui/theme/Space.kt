package com.musicpipe.player.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 间距 token（8pt 网格）—— 按当前风格的 density 缩放。
 *
 * 为什么间距也要跟着风格变：Editorial 是宽松的杂志排版（大留白），
 * shadcn 是紧凑的密度 0.9（桌面组件库的信息密度），Cupertino 略松 1.05。
 * 用同一套档位乘密度，页面里仍只写档位名，节奏自动跟着风格走。
 *
 * 最小 1dp 兜底：density 再小也不能把间距算成 0，否则元素会粘在一起。
 */
object AwwSpace {

    private fun s(base: Float): Dp {
        val v = base * StyleState.spec.density
        return if (v < 1f) 1.dp else v.dp
    }

    val s1: Dp get() = s(4f)
    val s2: Dp get() = s(8f)
    val s3: Dp get() = s(12f)
    val s4: Dp get() = s(16f)
    val s6: Dp get() = s(24f)
    val s8: Dp get() = s(32f)
    val s12: Dp get() = s(48f)
    val s16: Dp get() = s(64f)
    val s24: Dp get() = s(96f)
    val s32: Dp get() = s(128f)
    val s48: Dp get() = s(192f)

    /** 左右安全边距：clamp(16px, 4vw, 48px) 的 Compose 等价（按屏宽换算）。 */
    fun gutter(screenWidthDp: Int): Dp {
        val v = screenWidthDp * 0.04f
        return (v.coerceIn(16f, 48f) * StyleState.spec.density).dp
    }

    /** 区块垂直节奏：clamp(96px, 12vw, 192px)。 */
    fun sectionPadding(screenWidthDp: Int): Dp {
        val v = screenWidthDp * 0.12f
        return (v.coerceIn(96f, 192f) * StyleState.spec.density).dp
    }
}