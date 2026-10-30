package com.musicpipe.player.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 六套风格的规格表 —— 唯一真相来源。
 *
 * 每套都是真实存在、被广泛使用的设计语言，不是随手调的配色：
 *  1. Editorial  —— 本项目 v1.12.0 按 DESIGN.md 做的编辑杂志风（暗底/噪点/硬边）
 *  2. Classic    —— v1.12.0 之前的 MusicPipe（DsoMusic 式红白 + 圆角卡片）
 *  3. Material You —— Google 官方 M3：色调表面（tonal surface）、大圆角、无描边
 *  4. Cupertino  —— Apple HIG：分组列表、系统蓝、大标题、0.5pt 发丝线
 *  5. Shadcn     —— GitHub star 最多的组件库 shadcn/ui：中性 zinc、6px 圆角、1px 边框、单色强调
 *  6. Brutalist  —— Neubrutalism：粗描边、零圆角、高对比、等宽字形
 *
 * 这里**只允许**出现色值字面量：它是 token 的源头，其它任何文件都必须引用 token。
 * 注意不要在本文件里引用 AwwColors —— StyleState 的初始化读本文件，会循环初始化。
 */
object StyleSpecs {

    // ---------------- 0. 网易云（默认）----------------
    private val NeDark = StylePalette(
        paper = Color(0xFF101012), paper2 = Color(0xFF1A1A1C),
        ink = Color(0xFFECECEA), ink2 = Color(0xB3ECECEA), ink3 = Color(0x80ECECEA),
        line = Color(0x1FECECEA), accent = Color(0xFFEC4141),
        accentSoft = Color(0x24EC4141), onAccent = Color(0xFFFFFFFF)
    )
    private val NeLight = StylePalette(
        paper = Color(0xFFFAFAFA), paper2 = Color(0xFFF0F0F1),
        ink = Color(0xFF1A1A1C), ink2 = Color(0xA61A1A1C), ink3 = Color(0x801A1A1C),
        line = Color(0x141A1A1C), accent = Color(0xFFEC4141),
        accentSoft = Color(0x14EC4141), onAccent = Color(0xFFFFFFFF)
    )

    // ---------------- 1. Editorial（DESIGN.md / Awwwards）----------------
    private val EwDark = StylePalette(
        paper = Color(0xFF0D0D0F), paper2 = Color(0xFF141417),
        ink = Color(0xFFF2F0EC), ink2 = Color(0xB8F2F0EC), ink3 = Color(0x6EF2F0EC),
        line = Color(0x1FF2F0EC), accent = Color(0xFFFF4D2E),
        accentSoft = Color(0x29FF4D2E), onAccent = Color(0xFF0D0D0F)
    )
    private val EwLight = StylePalette(
        paper = Color(0xFFF4F2ED), paper2 = Color(0xFFEAE7E0),
        ink = Color(0xFF101012), ink2 = Color(0xB8101012), ink3 = Color(0x6E101012),
        line = Color(0x24101012), accent = Color(0xFFFF4D2E),
        accentSoft = Color(0x29FF4D2E), onAccent = Color(0xFFFFFFFF)
    )

    // ---------------- 2. Classic（v1.12.0 之前的 DsoMusic 红白）----------------
    private val ClDark = StylePalette(
        paper = Color(0xFF121212), paper2 = Color(0xFF1E1E1E),
        ink = Color(0xFFF5F5F5), ink2 = Color(0xB3F5F5F5), ink3 = Color(0x66F5F5F5),
        line = Color(0x1FFFFFFF), accent = Color(0xFFFF6B5E),
        accentSoft = Color(0x29FF6B5E), onAccent = Color(0xFF1A1A1A)
    )
    private val ClLight = StylePalette(
        paper = Color(0xFFF9F9F9), paper2 = Color(0xFFFFFFFF),
        ink = Color(0xFF1A1A1A), ink2 = Color(0x991A1A1A), ink3 = Color(0x661A1A1A),
        line = Color(0x1F000000), accent = Color(0xFFE53935),
        accentSoft = Color(0x1FE53935), onAccent = Color(0xFFFFFFFF)
    )

    // ---------------- 3. Material You（Google M3 baseline）----------------
    // 色调表面：surface 比 background 亮一档，靠 tonal elevation 区分层级，不用描边。
    private val MtDark = StylePalette(
        paper = Color(0xFF141218), paper2 = Color(0xFF211F26),
        ink = Color(0xFFE6E0E9), ink2 = Color(0xB3E6E0E9), ink3 = Color(0x80E6E0E9),
        line = Color(0x1FCAC4D0), accent = Color(0xFFD0BCFF),
        accentSoft = Color(0x29D0BCFF), onAccent = Color(0xFF381E72)
    )
    private val MtLight = StylePalette(
        paper = Color(0xFFFFFBFE), paper2 = Color(0xFFF3EDF7),
        ink = Color(0xFF1C1B1F), ink2 = Color(0xB31C1B1F), ink3 = Color(0x801C1B1F),
        line = Color(0x1F49454F), accent = Color(0xFF6750A4),
        accentSoft = Color(0x1F6750A4), onAccent = Color(0xFFFFFFFF)
    )

    // ---------------- 4. Cupertino（Apple HIG）----------------
    // systemGroupedBackground = #F2F2F7 / 分组卡片纯白 / 系统蓝 #007AFF。
    private val CpDark = StylePalette(
        paper = Color(0xFF000000), paper2 = Color(0xFF1C1C1E),
        ink = Color(0xFFFFFFFF), ink2 = Color(0x99FFFFFF), ink3 = Color(0x4DFFFFFF),
        line = Color(0x1F545458), accent = Color(0xFF0A84FF),
        accentSoft = Color(0x290A84FF), onAccent = Color(0xFFFFFFFF)
    )
    private val CpLight = StylePalette(
        paper = Color(0xFFF2F2F7), paper2 = Color(0xFFFFFFFF),
        ink = Color(0xFF000000), ink2 = Color(0x993C3C43), ink3 = Color(0x4D3C3C43),
        line = Color(0x1F3C3C43), accent = Color(0xFF007AFF),
        accentSoft = Color(0x1F007AFF), onAccent = Color(0xFFFFFFFF)
    )

    // ---------------- 5. Shadcn（zinc 中性 + 6px + 1px 边框）----------------
    // shadcn/ui 的默认主题是**单色**：primary 就是 zinc-900 / zinc-50，没有彩色强调。
    // 这里照此实现，强调色 = 反相黑，正好适合"播放中"这种需要强对比的场景。
    private val ShDark = StylePalette(
        paper = Color(0xFF09090B), paper2 = Color(0xFF18181B),
        ink = Color(0xFFFAFAFA), ink2 = Color(0xB3FAFAFA), ink3 = Color(0x70FAFAFA),
        line = Color(0xFF27272A), accent = Color(0xFFFAFAFA),
        accentSoft = Color(0x1FFAFAFA), onAccent = Color(0xFF09090B)
    )
    private val ShLight = StylePalette(
        paper = Color(0xFFFFFFFF), paper2 = Color(0xFFF4F4F5),
        ink = Color(0xFF09090B), ink2 = Color(0xB309090B), ink3 = Color(0x7009090B),
        line = Color(0xFFE4E4E7), accent = Color(0xFF18181B),
        accentSoft = Color(0x1F18181B), onAccent = Color(0xFFFAFAFA)
    )

    // ---------------- 6. Brutalist（Neubrutalism）----------------
    // 特征：黑描边 2dp、零圆角、高饱和单色强调、无中间灰（要么黑要么白）。
    private val BrDark = StylePalette(
        paper = Color(0xFF0A0A0A), paper2 = Color(0xFF1A1A1A),
        ink = Color(0xFFFAFAFA), ink2 = Color(0xB3FAFAFA), ink3 = Color(0x80FAFAFA),
        line = Color(0xFFFAFAFA), accent = Color(0xFFFF6B35),
        accentSoft = Color(0x33FF6B35), onAccent = Color(0xFF0A0A0A)
    )
    private val BrLight = StylePalette(
        paper = Color(0xFFF5F0E1), paper2 = Color(0xFFFFFFFF),
        ink = Color(0xFF000000), ink2 = Color(0xB3000000), ink3 = Color(0x80000000),
        line = Color(0xFF000000), accent = Color(0xFFFF4911),
        accentSoft = Color(0x33FF4911), onAccent = Color(0xFFFFFFFF)
    )

    // ---------------- 规格装配 ----------------
    private val specs: Map<AppStyle, StyleSpec> = mapOf(

        AppStyle.NETEASE to StyleSpec(
            style = AppStyle.NETEASE, dark = NeDark, light = NeLight,
            shape = StyleShape(card = 12.dp, input = 12.dp, button = 12.dp, pill = 999.dp, border = 0.dp),
            type = StyleType(
                family = FontFamily.Default, titleWeight = FontWeight.SemiBold,
                titleTracking = -0.02f, uppercaseLabels = false, labelTracking = 0f,
                bodyWeight = FontWeight.Normal, sizeScale = 1f,
                bodyLineHeight = 1.65f, titleLineHeight = 1.2f, monoFamily = FontFamily.Monospace
            ),
            motion = StyleMotion(fast = 160, base = 260, slow = 420),
            chrome = StyleChrome(
                filledButtons = true, ruleHeight = 0.dp, grain = false,
                cardElevation = 0.dp, largeHeaders = false, tabIndicator = TabIndicator.TINT
            ), density = 1f
        ),

        AppStyle.AWWWARDS to StyleSpec(
            style = AppStyle.AWWWARDS, dark = EwDark, light = EwLight,
            shape = StyleShape(card = 2.dp, input = 2.dp, button = 2.dp, pill = 999.dp, border = 1.dp),
            type = StyleType(
                family = FontFamily.Default, titleWeight = FontWeight.Medium,
                titleTracking = -0.03f, uppercaseLabels = true, labelTracking = 1.32f,
                bodyWeight = FontWeight.Normal, sizeScale = 1f,
                bodyLineHeight = 1.8f, titleLineHeight = 0.98f, monoFamily = FontFamily.Monospace
            ),
            motion = StyleMotion(fast = 200, base = 450, slow = 800),
            chrome = StyleChrome(
                filledButtons = false, ruleHeight = 1.dp, grain = true,
                cardElevation = 0.dp, largeHeaders = true,
                tabIndicator = TabIndicator.UNDERLINE
            ),
            density = 1f
        ),

        AppStyle.CLASSIC to StyleSpec(
            style = AppStyle.CLASSIC, dark = ClDark, light = ClLight,
            shape = StyleShape(card = 12.dp, input = 10.dp, button = 999.dp, pill = 999.dp, border = 0.dp),
            type = StyleType(
                family = FontFamily.Default, titleWeight = FontWeight.SemiBold,
                titleTracking = -0.01f, uppercaseLabels = false, labelTracking = 0.2f,
                bodyWeight = FontWeight.Normal, sizeScale = 1f,
                bodyLineHeight = 1.5f, titleLineHeight = 1.15f, monoFamily = FontFamily.Default
            ),
            motion = StyleMotion(fast = 150, base = 300, slow = 500),
            chrome = StyleChrome(
                filledButtons = true, ruleHeight = 0.dp, grain = false,
                cardElevation = 2.dp, largeHeaders = false,
                tabIndicator = TabIndicator.PILL
            ),
            density = 1f
        ),

        AppStyle.MATERIAL to StyleSpec(
            style = AppStyle.MATERIAL, dark = MtDark, light = MtLight,
            shape = StyleShape(card = 16.dp, input = 4.dp, button = 999.dp, pill = 999.dp, border = 0.dp),
            type = StyleType(
                family = FontFamily.Default, titleWeight = FontWeight.Medium,
                titleTracking = 0f, uppercaseLabels = false, labelTracking = 0.5f,
                bodyWeight = FontWeight.Normal, sizeScale = 1f,
                bodyLineHeight = 1.5f, titleLineHeight = 1.15f, monoFamily = FontFamily.Default
            ),
            motion = StyleMotion(fast = 200, base = 300, slow = 500),
            chrome = StyleChrome(
                filledButtons = true, ruleHeight = 0.dp, grain = false,
                cardElevation = 0.dp, largeHeaders = false,
                tabIndicator = TabIndicator.PILL
            ),
            density = 1f
        ),

        AppStyle.CUPPERTINO to StyleSpec(
            style = AppStyle.CUPPERTINO, dark = CpDark, light = CpLight,
            shape = StyleShape(card = 12.dp, input = 10.dp, button = 12.dp, pill = 999.dp, border = 0.dp),
            type = StyleType(
                family = FontFamily.Default, titleWeight = FontWeight.SemiBold,
                titleTracking = -0.02f, uppercaseLabels = false, labelTracking = 0f,
                bodyWeight = FontWeight.Normal, sizeScale = 1f,
                bodyLineHeight = 1.4f, titleLineHeight = 1.12f, monoFamily = FontFamily.Default
            ),
            motion = StyleMotion(fast = 250, base = 350, slow = 600),
            chrome = StyleChrome(
                filledButtons = true, ruleHeight = 0.5.dp, grain = false,
                cardElevation = 0.dp, largeHeaders = true,
                tabIndicator = TabIndicator.TINT
            ),
            density = 1.05f
        ),

        AppStyle.SHADCN to StyleSpec(
            style = AppStyle.SHADCN, dark = ShDark, light = ShLight,
            shape = StyleShape(card = 6.dp, input = 6.dp, button = 6.dp, pill = 999.dp, border = 1.dp),
            type = StyleType(
                family = FontFamily.Default, titleWeight = FontWeight.SemiBold,
                titleTracking = -0.025f, uppercaseLabels = false, labelTracking = 0f,
                bodyWeight = FontWeight.Normal, sizeScale = 0.95f,
                bodyLineHeight = 1.5f, titleLineHeight = 1.15f, monoFamily = FontFamily.Monospace
            ),
            motion = StyleMotion(fast = 150, base = 200, slow = 300),
            chrome = StyleChrome(
                filledButtons = true, ruleHeight = 1.dp, grain = false,
                cardElevation = 0.dp, largeHeaders = false,
                tabIndicator = TabIndicator.TINT
            ),
            density = 0.9f
        ),

        AppStyle.BRUTAL to StyleSpec(
            style = AppStyle.BRUTAL, dark = BrDark, light = BrLight,
            shape = StyleShape(card = 0.dp, input = 0.dp, button = 0.dp, pill = 0.dp, border = 2.dp),
            type = StyleType(
                family = FontFamily.Default, titleWeight = FontWeight.Black,
                titleTracking = -0.03f, uppercaseLabels = true, labelTracking = 1.5f,
                bodyWeight = FontWeight.Medium, sizeScale = 1f,
                bodyLineHeight = 1.5f, titleLineHeight = 1.0f, monoFamily = FontFamily.Monospace
            ),
            motion = StyleMotion(fast = 120, base = 200, slow = 320),
            chrome = StyleChrome(
                filledButtons = true, ruleHeight = 2.dp, grain = true,
                cardElevation = 0.dp, largeHeaders = true,
                tabIndicator = TabIndicator.UNDERLINE
            ),
            density = 1f
        )
    )

    fun of(style: AppStyle): StyleSpec = specs[style] ?: specs.getValue(AppStyle.NETEASE)
}