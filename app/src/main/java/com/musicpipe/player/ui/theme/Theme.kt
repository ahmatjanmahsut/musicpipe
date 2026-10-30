package com.musicpipe.player.ui.theme

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight

/**
 * 把当前风格的调色板铺进 M3 ColorScheme。
 *
 * 关键：**不用**动态取色（Material You 的 wallpaper colors）。理由：
 * 用户选的是"Material You 风格"，期望看到的是 Google 官方那套紫调 baseline；
 * 如果取壁纸色，同一台机器上换壁纸 App 就变色，风格就不再是"一种设计"了。
 * 想接动态色以后可以加一个开关，这里不做。
 */
private fun scheme(dark: Boolean) = if (dark) {
    darkColorScheme(
        primary = AwwColors.Accent,
        onPrimary = AwwColors.OnAccent,
        primaryContainer = AwwColors.AccentSoft,
        onPrimaryContainer = AwwColors.Ink,
        inversePrimary = AwwColors.Accent,
        secondary = AwwColors.Ink2,
        onSecondary = AwwColors.Paper,
        secondaryContainer = AwwColors.Paper2,
        onSecondaryContainer = AwwColors.Ink,
        tertiary = AwwColors.Ink2,
        onTertiary = AwwColors.Paper,
        background = AwwColors.Paper,
        onBackground = AwwColors.Ink,
        surface = AwwColors.Paper,
        onSurface = AwwColors.Ink,
        surfaceVariant = AwwColors.Paper2,
        onSurfaceVariant = AwwColors.Ink2,
        surfaceContainerLowest = AwwColors.Paper,
        surfaceContainerLow = AwwColors.Paper,
        surfaceContainer = AwwColors.Paper2,
        surfaceContainerHigh = AwwColors.Paper2,
        surfaceContainerHighest = AwwColors.Paper2,
        surfaceBright = AwwColors.Paper2,
        surfaceDim = AwwColors.Paper,
        outline = AwwColors.Line,
        outlineVariant = AwwColors.Line,
        error = AwwColors.Accent,
        onError = AwwColors.OnAccent,
        inverseSurface = AwwColors.Ink,
        inverseOnSurface = AwwColors.Paper,
        scrim = Color(0xCC000000)
    )
} else {
    lightColorScheme(
        primary = AwwColors.Accent,
        onPrimary = AwwColors.OnAccent,
        primaryContainer = AwwColors.AccentSoft,
        onPrimaryContainer = AwwColors.Accent,
        inversePrimary = AwwColors.Accent,
        secondary = AwwColors.Ink2Light,
        onSecondary = AwwColors.PaperLight,
        secondaryContainer = AwwColors.Paper2Light,
        onSecondaryContainer = AwwColors.InkLight,
        tertiary = AwwColors.Ink2Light,
        onTertiary = AwwColors.PaperLight,
        background = AwwColors.PaperLight,
        onBackground = AwwColors.InkLight,
        surface = AwwColors.PaperLight,
        onSurface = AwwColors.InkLight,
        surfaceVariant = AwwColors.Paper2Light,
        onSurfaceVariant = AwwColors.Ink2Light,
        surfaceContainerLowest = AwwColors.PaperLight,
        surfaceContainerLow = AwwColors.PaperLight,
        surfaceContainer = AwwColors.Paper2Light,
        surfaceContainerHigh = AwwColors.Paper2Light,
        surfaceContainerHighest = AwwColors.Paper2Light,
        surfaceBright = AwwColors.PaperLight,
        surfaceDim = AwwColors.Paper2Light,
        outline = AwwColors.LineLight,
        outlineVariant = AwwColors.LineLight,
        error = AwwColors.Accent,
        onError = AwwColors.OnAccent,
        inverseSurface = AwwColors.InkLight,
        inverseOnSurface = AwwColors.PaperLight,
        scrim = Color(0xCC000000)
    )
}

/**
 * 主题相关的"非 M3 语义"颜色。
 *
 * 只保留几个真正需要按主题区分的位置：封面占位（禁灰块，见 §8 的 LQIP 要求）、
 * CD 圆盘、强调色之上的文字。其余一律走 [MaterialTheme.colorScheme]。
 */
@androidx.compose.runtime.Immutable
data class DsoPalette(
    val coverPlaceholder: Color,
    val cdPlate: Color,
    val onRed: Color,
    val accent: Color,
    val line: Color,
    val ink3: Color
)

private fun paletteOf(dark: Boolean): DsoPalette {
    val p = StyleState.spec.palette(dark)
    return DsoPalette(
        coverPlaceholder = p.paper2,
        cdPlate = p.paper2,
        onRed = p.onAccent,
        accent = p.accent,
        line = p.line,
        ink3 = p.ink3
    )
}

val LocalDsoPalette = androidx.compose.runtime.staticCompositionLocalOf {
    DsoPalette(
        coverPlaceholder = Color(0xFFEAE7E0), cdPlate = Color(0xFFEAE7E0),
        onRed = Color(0xFFFFFFFF), accent = Color(0xFFFF4D2E),
        line = Color(0x24101012), ink3 = Color(0x6E101012)
    )
}

/**
 * 读取系统的"移除动画"开关（DESIGN.md §5 的 prefers-reduced-motion 等价物）。
 *
 * Settings.Global.ANIMATOR_DURATION_SCALE == 0 即用户在无障碍设置里关掉了动画。
 */
@Composable
private fun rememberReduceMotion(): Boolean {
    val ctx = LocalContext.current
    return remember(ctx) {
        val v = runCatching {
            Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }.getOrDefault(1f)
        v == 0f
    }
}

/**
 * App 主题入口。
 *
 * @param themeMode "system" | "light" | "dark"（DataStore theme_mode）
 * @param appStyle  用户选的界面风格（DataStore app_style），见 [AppStyle]
 *
 * 风格切换的机制：先把选中的风格写进 [StyleState]，再用 `key(appStyle)` 重建整棵子树。
 * 为什么必须 key()：token 现在是普通 getter（不是 snapshot state），Compose 不会因为它们
 * 变化而自动重组。key() 让整棵界面在换风格时丢弃旧组合、按新规格重新构建 —— 这正好是
 * 我们想要的语义（换的是整套设计，不是某个值），而且一次性、无残留。
 *
 * 顺带处理 system 模式下明暗切换：isDark 也进 key，系统切换时会重建。
 */
@Composable
fun MusicPipeTheme(
    themeMode: String = "system",
    appStyle: AppStyle = AppStyle.NETEASE,
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    val reduceMotion = rememberReduceMotion()

    // 在组合任何子项之前同步好全局规格，子项里的 token getter 才能读到正确的值。
    // 这不是副作用写到 UI 状态：StyleState 只是一张只读规格表，写入后本帧立即生效。
    StyleState.apply(appStyle, dark)

    key(appStyle, dark) {
        val type = rememberAwwType()
        CompositionLocalProvider(
            LocalDsoPalette provides paletteOf(dark),
            LocalAwwType provides type,
            LocalReduceMotion provides reduceMotion
        ) {
            MaterialTheme(
                colorScheme = scheme(dark),
                typography = m3Typography(type),
                content = content
            )
        }
    }
}

/** 便捷读取当前的流式字号表：`AwwTheme.type.display`。 */
object AwwTheme {
    val type: AwwType
        @Composable
        @androidx.compose.runtime.ReadOnlyComposable
        get() = LocalAwwType.current

    /** 当前风格的形态开关（按钮/分隔线/噪点等）。 */
    val chrome: StyleChrome get() = StyleState.spec.chrome

    /** 当前风格枚举，设置页用来显示选中项。 */
    val style: AppStyle get() = StyleState.spec.style
}

/** 读取当前风格的一个色值，仅供极少数无法走 MaterialTheme 的地方。 */
val AwwThemePalette: StylePalette
    get() = StyleState.palette

// 抑制未使用告警：FontWeight 供外部按需引用
@Suppress("unused")
private val fwRef: FontWeight = FontWeight.Normal