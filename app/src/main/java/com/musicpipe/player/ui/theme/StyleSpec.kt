package com.musicpipe.player.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 一套 UI 风格（design system skin）的完整规格。
 *
 * 设计原则：**页面代码不认识风格**。
 * 18 个页面里散落着 277 处 AwwColors、272 处 AwwSpace、141 处 MaterialTheme.colorScheme、
 * 86 处 AwwTheme.type —— 如果让每套风格去改页面，就是 800 多处 × 6 套的维护地狱，
 * 而且以后每加一套都要再改一遍。
 *
 * 所以这里把风格收进一张规格表，AwwColors / AwwShapes / AwwSpace / AwwTheme.type / AwwMotion
 * 全部改成**从当前规格读取**的 getter。页面一行不用动就能换整套外观。
 */

/** 可选风格。id 会持久化到 DataStore，不要改已发布风格的 id。 */
enum class AppStyle(val id: String, val displayName: String, val tagline: String) {
    /** 默认：网易云式红白音乐界面。 */
    NETEASE("netease", "网易云", "红白配色 · 圆角封面 · 黑胶播放"),
    /** 默认：DESIGN.md 的 Awwwards 编辑风（暗底 + 噪点 + 硬边 + 单一强调色）。 */
    AWWWARDS("awwwards", "Editorial", "暗底 · 噪点 · 硬边 · 编辑排版"),
    /** 旧版 MusicPipe（DsoMusic 红白）—— 用户说的「之前的算一种」。 */
    CLASSIC("classic", "Classic", "红白配色 · 圆角卡片 · 旧版 MusicPipe"),
    /** Google 官方 Material 3 / Material You。 */
    MATERIAL("material", "Material You", "Google 官方 · 色调表面 · 大圆角"),
    /** Apple iOS Human Interface Guidelines。 */
    CUPPERTINO("cupertino", "Cupertino", "Apple 风格 · 分组列表 · 系统蓝"),
    /** shadcn/ui —— GitHub 上 star 最多的组件库，中性极简 + 细边框。 */
    SHADCN("shadcn", "Shadcn", "GitHub 最多 star · 中性 · 细边框"),
    /** Neubrutalism —— 粗描边 + 零圆角 + 高对比。 */
    BRUTAL("brutal", "Brutalist", "粗描边 · 零圆角 · 高对比");

    companion object {
        fun fromId(raw: String?): AppStyle =
            entries.firstOrNull { it.id == (raw ?: "").trim() } ?: NETEASE
    }
}

/** 一套风格在某一明暗模式下的全部颜色。 */
@androidx.compose.runtime.Immutable
data class StylePalette(
    val paper: Color,
    val paper2: Color,
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    val line: Color,
    val accent: Color,
    val accentSoft: Color,
    val onAccent: Color
)

/** 圆角与描边。 */
@androidx.compose.runtime.Immutable
data class StyleShape(
    /** 卡片/封面等大面。 */
    val card: Dp,
    /** 输入框。 */
    val input: Dp,
    /** 按钮。 */
    val button: Dp,
    /** 药丸（标签/开关）。 */
    val pill: Dp,
    /** 卡片描边宽度，0 = 不描边（靠底色或阴影区分）。 */
    val border: Dp
) {
    val cardShape: RoundedCornerShape get() = RoundedCornerShape(card)
    val inputShape: RoundedCornerShape get() = RoundedCornerShape(input)
    val buttonShape: RoundedCornerShape get() = RoundedCornerShape(button)
    val pillShape: RoundedCornerShape get() = RoundedCornerShape(pill)
}

/** 字号与字形。 */
@androidx.compose.runtime.Immutable
data class StyleType(
    val family: FontFamily,
    /** 标题字重。 */
    val titleWeight: FontWeight,
    /** 标题字距（em，负数为收紧）。 */
    val titleTracking: Float,
    /** 小标签是否强制大写。 */
    val uppercaseLabels: Boolean,
    /** 小标签字距（sp）。 */
    val labelTracking: Float,
    /** 正文字重。 */
    val bodyWeight: FontWeight,
    /** 正文字号整体缩放。 */
    val sizeScale: Float,
    /** 正文行高倍数。 */
    val bodyLineHeight: Float,
    /** 标题行高倍数（大标题压缩感）。 */
    val titleLineHeight: Float,
    /** 是否使用等宽数字风格（编号/时间戳）。 */
    val monoFamily: FontFamily
)

/** 动效节奏。 */
@androidx.compose.runtime.Immutable
data class StyleMotion(val fast: Int, val base: Int, val slow: Int)

/**
 * 底栏选中态的样式 —— 三种真实设计语言各自的做法，不能只留一种。
 *
 * 编辑风用"下划线"，Material 与旧版用"药丸底"，Apple 与 shadcn 只给文字上色（不给容器）。
 */
enum class TabIndicator {
    /** 选中项下方一条短线（编辑风 / Brutalist）。 */
    UNDERLINE,
    /** 选中项整块加底色药丸（Material / 旧版）。 */
    PILL,
    /** 只换文字颜色，不加任何容器（Apple / shadcn）。 */
    TINT
}

/** 共享组件层的形态开关 —— 让按钮/分隔线/页眉这些「骨架件」也随风格变。 */
@androidx.compose.runtime.Immutable
data class StyleChrome(
    /** 主按钮是实心填充还是描边。 */
    val filledButtons: Boolean,
    /** 分隔线粗细，0 = 不用线条（Material/Cupertino 靠间距和底色分组）。 */
    val ruleHeight: Dp,
    /** 是否叠 grain 噪点。 */
    val grain: Boolean,
    /** 卡片是否带阴影（Material 用 tonal elevation）。 */
    val cardElevation: Dp,
    /** 页眉标题是否走大字（编辑风）还是中等（系统风）。 */
    val largeHeaders: Boolean,
    /** 底栏选中态的画法。 */
    val tabIndicator: TabIndicator = TabIndicator.UNDERLINE
)

/** 一套风格的完整定义。 */
@androidx.compose.runtime.Immutable
data class StyleSpec(
    val style: AppStyle,
    val dark: StylePalette,
    val light: StylePalette,
    val shape: StyleShape,
    val type: StyleType,
    val motion: StyleMotion,
    val chrome: StyleChrome,
    /** 间距整体缩放（8pt 网格的密度感）。 */
    val density: Float
) {
    fun palette(forDark: Boolean): StylePalette = if (forDark) dark else light
}

/** 全局当前风格。
 *
 * 用普通 @Volatile 而非 snapshot state：风格切换时由 MusicPipeTheme 用 key(style){} 重建整棵
 * 子树，子组合自然读到新值，不需要每个 token 读取都触发一次重组。
 * 用 snapshot state 反而会在组合期写入，导致重组循环。
 */
object StyleState {
    @Volatile var spec: StyleSpec = StyleSpecs.of(AppStyle.AWWWARDS)
        private set
    @Volatile var isDark: Boolean = true
        private set

    val palette: StylePalette get() = spec.palette(isDark)

    fun apply(style: AppStyle, dark: Boolean) {
        spec = StyleSpecs.of(style)
        isDark = dark
    }
}
