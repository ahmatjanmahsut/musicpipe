package com.musicpipe.player.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * 字号表 —— 由当前风格的 [StyleType] 生成。
 *
 * 设计上保持不变的部分：**流式字号**。规范里的 `clamp(3.5rem, 11vw, 11rem)` 是随视口
 * 线性插值并有上下界的字号，Android 没有 vw 单位，这里用 [LocalConfiguration.screenWidthDp]
 * 换算：`(screenWidthDp * pct).coerceIn(min, max).sp`。这样同一个标题在 360dp 小屏不会
 * 被撑破、在 800dp 平板也不会显得小，且**跟随系统字号缩放**（sp 会乘 fontScale）。
 *
 * 跟随风格变的部分：字重、字距、行高、大写、整体缩放、等宽族。
 * 例如 Brutalist 的标题是 Black 900 + 收紧字距 + 行高 1.0（贴死），
 * Material 是 Medium 500 + 零字距 + 行高 1.15（官方 baseline 的克制感）。
 */
@Immutable
data class AwwType(
    val hero: TextStyle,
    val display: TextStyle,
    val h2: TextStyle,
    val h3: TextStyle,
    val body: TextStyle,
    val bodySmall: TextStyle,
    val small: TextStyle,
    val label: TextStyle,
    val mono: TextStyle,
    val monoMeta: TextStyle,
    /**
     * 小标签是否强制全大写。
     *
     * v1.13.0：Editorial 与 Brutalist 的小标签是全大写的（规范原文 uppercase + .12em），
     * 但 Apple / Google / shadcn 的官方语言**都不做全大写**（材料设计明确反对 all-caps 标签）。
     * 组件层据此决定是否 uppercase，页面不感知。
     */
    val uppercaseLabels: Boolean = false
)

/** 流式字号：clamp(min, vw%, max)，单位 sp。 */
private fun fluid(screenWidthDp: Int, min: Float, vwPct: Float, max: Float): TextUnit {
    val raw = screenWidthDp.coerceAtLeast(1) * (vwPct / 100f)
    return raw.coerceIn(min, max).sp
}

/**
 * 大标题的排版细节：line-height 与 letter-spacing 按风格。
 * 中文字形没有西文的小写降部，行高过紧会粘行，所以下限取 0.92。
 */
private fun displayStyle(
    size: TextUnit,
    lineHeight: Float,
    trackingEm: Float,
    weight: FontWeight,
    family: FontFamily
) = TextStyle(
    fontFamily = family,
    fontWeight = weight,
    fontSize = size,
    lineHeight = size * lineHeight.coerceAtLeast(0.92f),
    letterSpacing = size * trackingEm,
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None
    )
)

/**
 * 按风格构造字号表。
 *
 * 注意 [StyleType.sizeScale] 只缩放**正文与小字**，不动 hero/display ——
 * 大标题本来就已经是流式的，再乘缩放会在大屏上失控。
 */
private fun buildAwwType(screenWidthDp: Int, spec: StyleType): AwwType {
    val hero = fluid(screenWidthDp, 44f, 13f, 108f)
    val display = fluid(screenWidthDp, 30f, 7.2f, 64f)
    val h2 = fluid(screenWidthDp, 24f, 4.2f, 40f)
    val bodyBase = fluid(screenWidthDp, 15f, 1.6f, 18f)
    val body = (bodyBase.value * spec.sizeScale).sp
    val bodySmall = ((bodyBase.value - 2f) * spec.sizeScale).sp

    val titleFamily = spec.family
    val monoFamily = spec.monoFamily

    return AwwType(
        hero = displayStyle(hero, spec.titleLineHeight, spec.titleTracking, spec.titleWeight, titleFamily),
        display = displayStyle(display, spec.titleLineHeight + 0.04f, spec.titleTracking, spec.titleWeight, titleFamily),
        h2 = displayStyle(h2, spec.titleLineHeight + 0.07f, spec.titleTracking * 0.7f, spec.titleWeight, titleFamily),
        h3 = displayStyle(24.sp, spec.titleLineHeight + 0.12f, spec.titleTracking * 0.5f, spec.titleWeight, titleFamily),
        body = TextStyle(
            fontFamily = titleFamily,
            fontSize = body,
            lineHeight = body * spec.bodyLineHeight,
            letterSpacing = 0.sp,
            fontWeight = spec.bodyWeight
        ),
        bodySmall = TextStyle(
            fontFamily = titleFamily,
            fontSize = bodySmall,
            lineHeight = bodySmall * (spec.bodyLineHeight - 0.05f),
            fontWeight = spec.bodyWeight
        ),
        small = TextStyle(
            fontFamily = titleFamily,
            fontSize = (13f * spec.sizeScale).sp,
            lineHeight = (20f * spec.sizeScale).sp,
            fontWeight = spec.bodyWeight
        ),
        label = TextStyle(
            fontFamily = titleFamily,
            fontSize = (11f * spec.sizeScale).sp,
            lineHeight = (14f * spec.sizeScale).sp,
            fontWeight = if (spec.titleWeight.weight >= 700) spec.titleWeight else FontWeight.Medium,
            letterSpacing = spec.labelTracking.sp
        ),
        mono = TextStyle(
            fontFamily = monoFamily,
            fontSize = (12f * spec.sizeScale).sp,
            lineHeight = (16f * spec.sizeScale).sp,
            fontWeight = FontWeight.Normal
        ),
        monoMeta = TextStyle(
            fontFamily = monoFamily,
            fontSize = (10f * spec.sizeScale).sp,
            lineHeight = (14f * spec.sizeScale).sp,
            fontWeight = FontWeight.Normal,
            letterSpacing = 0.4.sp
        ),
        uppercaseLabels = spec.uppercaseLabels
    )
}

/** 当前视口的流式字号表。页面里用 `AwwTheme.type.hero` 取。 */
val LocalAwwType = androidx.compose.runtime.staticCompositionLocalOf {
    buildAwwType(360, StyleSpecs.of(AppStyle.NETEASE).type)
}

/**
 * 旧名兼容别名（v1.12.0 前散落在 LyricsScreen / HomeScreen 的离散档位）。
 * 新代码请用 [AwwTheme.type] 或 [AwwType]。
 */
object DsoTextStyles {
    val Badge = TextStyle(fontSize = 10.sp, lineHeight = 13.sp, fontWeight = FontWeight.Normal, letterSpacing = 1.1.sp)
    val LyricActive = TextStyle(fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.3).sp)
    val LyricIdle = TextStyle(fontSize = 15.sp, lineHeight = 26.sp, fontWeight = FontWeight.Normal)
}

@Composable
@ReadOnlyComposable
fun rememberAwwType(): AwwType {
    val w = LocalConfiguration.current.screenWidthDp
    return buildAwwType(w, StyleState.spec.type)
}

/**
 * M3 Typography 也要接上，否则仍用 Material 组件的默认字形
 * （Button/TextField/Dialog 内部读的是 MaterialTheme.typography）。
 */
internal fun m3Typography(t: AwwType): Typography {
    val base = Typography()
    return base.copy(
        displayLarge = t.hero,
        displayMedium = t.display,
        displaySmall = t.h2,
        headlineLarge = t.h2,
        headlineMedium = t.h2,
        headlineSmall = t.h3,
        titleLarge = t.h3,
        titleMedium = t.body.copy(fontWeight = FontWeight.Medium),
        titleSmall = t.small.copy(fontWeight = FontWeight.Medium),
        bodyLarge = t.body,
        bodyMedium = t.body,
        bodySmall = t.bodySmall,
        labelLarge = t.small.copy(fontWeight = FontWeight.Medium),
        labelMedium = t.label.copy(letterSpacing = 0.sp),
        labelSmall = t.label.copy(letterSpacing = 0.sp)
    )
}