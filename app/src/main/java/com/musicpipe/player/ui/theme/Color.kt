package com.musicpipe.player.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 色彩 token —— **当前风格**的唯一真相来源。
 *
 * v1.13.0 改造要点：这些成员从 val 常量变成了 getter，读的是 [StyleState.palette]
 * （由设置页选择的风格 + 当前明暗模式决定）。
 *
 * 为什么这样改：18 个页面里有 277 处 AwwColors 引用。如果让每套风格去改页面，
 * 就是 800+ 处 x 6 套，且以后每加一套都要再来一遍。改成 getter 后
 * **页面代码一行不动**，换风格自然就换了整套外观。
 *
 * 代价：每次读取都会走一次 StyleState（两个 @Volatile 读 + 一次 data class 引用），
 * 开销远小于一次重组，可忽略。页面在组合期读到的是当帧的值，切换风格时
 * MusicPipeTheme 用 key(style){} 重建整棵子树，所以不存在读到旧值的问题。
 *
 * 规则（所有风格都必须遵守）：
 * - 禁纯黑/禁纯白是 Editorial 的约束；其它风格按各自设计语言取值。
 * - 强调色面积必须小（<= 5%）：只用于选中态/下划线/CTA，不做大面积底色。
 * - 文字写在强调色之上时用 [onAccent]，不要用 [Paper] —— 深色强调色上写浅字才对。
 */
object AwwColors {

    /** 页面底色。 */
    val Paper: Color get() = StyleState.palette.paper
    /** 抬升一档的面（卡片/输入框/对话框）。 */
    val Paper2: Color get() = StyleState.palette.paper2
    /** 主文字。 */
    val Ink: Color get() = StyleState.palette.ink
    /** 次级文字。 */
    val Ink2: Color get() = StyleState.palette.ink2
    /** 弱化文字（提示/元信息/禁用）。 */
    val Ink3: Color get() = StyleState.palette.ink3
    /** 分隔线。 */
    val Line: Color get() = StyleState.palette.line
    /** 唯一强调色。 */
    val Accent: Color get() = StyleState.palette.accent
    /** 强调色的低透明度底（选中行/标签底）。 */
    val AccentSoft: Color get() = StyleState.palette.accentSoft
    /** 写在强调色之上的文字/图标。 */
    val OnAccent: Color get() = StyleState.palette.onAccent

    // ---- 显式取亮色/暗色版本：只给 Theme.kt 构造 M3 scheme 用 ----
    // 页面里不要用这四个，直接用上面的 Paper/Ink 即可（它们已经跟随明暗模式）。
    val PaperLight: Color get() = StyleState.spec.light.paper
    val Paper2Light: Color get() = StyleState.spec.light.paper2
    val InkLight: Color get() = StyleState.spec.light.ink
    val Ink2Light: Color get() = StyleState.spec.light.ink2
    val Ink3Light: Color get() = StyleState.spec.light.ink3
    val LineLight: Color get() = StyleState.spec.light.line
}

/**
 * v1.12.0 之前的旧名兼容别名。
 *
 * 现在已无页面引用（v1.12.0 重写时清干净了），仅保留以免外部代码/测试断裂。
 * 新代码请直接用 [AwwColors] 或 [material3.MaterialTheme.colorScheme]。
 */
object DsoColors {
    val AppRed: Color get() = AwwColors.Accent
    val HighlightBlue: Color get() = AwwColors.Ink2
    val PageWhite: Color get() = AwwColors.Paper2
    val PageBg: Color get() = AwwColors.Paper
    val TextPrimary: Color get() = AwwColors.Ink
    val TextSecondary: Color get() = AwwColors.Ink2
    val TextWeak: Color get() = AwwColors.Ink3
    val TextHint: Color get() = AwwColors.Ink3
    val Divider: Color get() = AwwColors.Line
    val InputBg: Color get() = AwwColors.Paper2
    val CoverPlaceholderLight: Color get() = AwwColors.Paper2
    val CoverPlaceholderDark: Color get() = AwwColors.Paper2
    val CdPlateLight: Color get() = AwwColors.Paper2
    val CdPlateDark: Color get() = AwwColors.Paper2
    val OnRedLight: Color get() = AwwColors.OnAccent
    val OnRedDark: Color get() = AwwColors.OnAccent
    val DarkBg: Color get() = AwwColors.Paper
    val DarkSurface: Color get() = AwwColors.Paper2
    val DarkTextPrimary: Color get() = AwwColors.Ink
    val DarkTextSecondary: Color get() = AwwColors.Ink2
    val DarkDivider: Color get() = AwwColors.Line
    val DarkInputBg: Color get() = AwwColors.Paper2
    val DarkSurfaceHigh: Color get() = AwwColors.Paper2
    val DarkSurfaceLow: Color get() = AwwColors.Paper
    val DarkOutline: Color get() = AwwColors.Line
}