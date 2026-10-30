package com.musicpipe.player.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * 圆角 token —— 跟随当前风格。
 *
 * Editorial 是 2dp 硬边（DESIGN.md 禁大圆角），Classic 是 12dp 卡片，
 * Material 是 16dp，Cupertino 是 12dp，shadcn 是 6px，Brutalist 是 0。
 * 页面里写 AwwShapes.Square 就能自动拿到当前风格该用的那个值。
 *
 * 缓存原因：Compose 用 equals 判断参数是否变化，RoundedCornerShape 是值相等的，
 * 新建对象不会破坏跳过；但每个滚动帧给 30 张卡片各建一个 Shape 是没必要的分配。
 * 这里按风格缓存一组，切风格时才重建。
 */
object AwwShapes {

    private class Bundle(
        val card: RoundedCornerShape,
        val input: RoundedCornerShape,
        val button: RoundedCornerShape,
        val pill: RoundedCornerShape
    )

    @Volatile private var cachedStyle: AppStyle? = null
    @Volatile private var cached: Bundle? = null

    private fun bundle(): Bundle {
        val style = StyleState.spec.style
        val c = cached
        if (c != null && cachedStyle == style) return c
        val shape = StyleState.spec.shape
        val b = Bundle(
            card = RoundedCornerShape(shape.card),
            input = RoundedCornerShape(shape.input),
            button = RoundedCornerShape(shape.button),
            pill = RoundedCornerShape(shape.pill)
        )
        cached = b
        cachedStyle = style
        return b
    }

    /** 卡片/封面/输入框底座 —— 页面最常用的那个。 */
    val Square: RoundedCornerShape get() = bundle().card
    /** 封面图（和卡片一致，保留独立名字方便以后分化）。 */
    val Image: RoundedCornerShape get() = bundle().card
    /** 略大一点的软圆角。 */
    val Soft: RoundedCornerShape get() = bundle().card
    /** 输入框。 */
    val Input: RoundedCornerShape get() = bundle().input
    /** 按钮。 */
    val Button: RoundedCornerShape get() = bundle().button
    /** 药丸（标签/开关）。 */
    val Pill: RoundedCornerShape get() = bundle().pill
    /** 强制直角：不受风格影响，用于"必须硬边"的地方（如进度条）。 */
    val None: RoundedCornerShape = RoundedCornerShape(0.dp)

    /**
     * 卡片的描边宽度 —— Brutalist 的整套语言都建立在 2dp 粗描边上，
     * 其它风格是 0（Editorial 只用 1dp 细线做分组，不给卡片描边）。
     */
    val borderWidth: androidx.compose.ui.unit.Dp get() = StyleState.spec.shape.border
}

/** 旧名兼容别名（v1.12.0 前使用，已无页面引用）。 */
object DsoShapes {
    val Small: RoundedCornerShape get() = AwwShapes.Square
    val Input: RoundedCornerShape get() = AwwShapes.Input
    val Badge: RoundedCornerShape get() = AwwShapes.Square
    val SmallSurface: RoundedCornerShape get() = AwwShapes.Soft
    val Surface: RoundedCornerShape get() = AwwShapes.Soft
    val Pill: RoundedCornerShape get() = AwwShapes.Pill
    val LoginButton: RoundedCornerShape get() = AwwShapes.Button
}