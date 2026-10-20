package com.musicpipe.player.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * v1.10.0：补齐 Material3 语义色。
 *
 * 之前只设了 15 个 role，其余（surfaceContainer / surfaceContainerLow / secondaryContainer /
 * tertiary / outline / inverseSurface / surfaceBright / surfaceDim）全部落回 M3 **紫色基线**，
 * 于是 DropdownMenu、FilterChip、Checkbox 未选中态、Snackbar 等在 Dso 红白配色里显出一层紫。
 * 这里把用得到的 role 一次补全。
 */
private val LightColors = lightColorScheme(
    primary = DsoColors.AppRed,
    onPrimary = DsoColors.PageWhite,
    primaryContainer = DsoColors.AppRed.copy(alpha = 0.12f),
    onPrimaryContainer = DsoColors.AppRed,
    inversePrimary = DsoColors.AppRed.copy(alpha = 0.35f),
    secondary = DsoColors.HighlightBlue,
    onSecondary = DsoColors.PageWhite,
    secondaryContainer = DsoColors.HighlightBlue.copy(alpha = 0.12f),
    onSecondaryContainer = DsoColors.HighlightBlue,
    tertiary = DsoColors.TextSecondary,
    onTertiary = DsoColors.PageWhite,
    background = DsoColors.PageBg,
    onBackground = DsoColors.TextPrimary,
    surface = DsoColors.PageWhite,
    onSurface = DsoColors.TextPrimary,
    surfaceVariant = DsoColors.InputBg,
    onSurfaceVariant = DsoColors.TextSecondary,
    surfaceContainerLowest = DsoColors.PageWhite,
    surfaceContainerLow = DsoColors.PageWhite,
    surfaceContainer = DsoColors.PageBg,
    surfaceContainerHigh = DsoColors.PageWhite,
    surfaceContainerHighest = DsoColors.InputBg,
    surfaceBright = DsoColors.PageWhite,
    surfaceDim = DsoColors.PageBg,
    outline = DsoColors.TextHint,
    outlineVariant = DsoColors.Divider,
    error = DsoColors.AppRed,
    onError = DsoColors.PageWhite,
    inverseSurface = DsoColors.TextPrimary,
    inverseOnSurface = DsoColors.PageWhite
)

private val DarkColors = darkColorScheme(
    primary = DsoColors.AppRed,
    onPrimary = DsoColors.OnRedDark,
    primaryContainer = DsoColors.AppRed.copy(alpha = 0.20f),
    onPrimaryContainer = DsoColors.AppRed,
    inversePrimary = DsoColors.AppRed.copy(alpha = 0.35f),
    secondary = DsoColors.HighlightBlue,
    onSecondary = DsoColors.PageWhite,
    secondaryContainer = DsoColors.HighlightBlue.copy(alpha = 0.20f),
    onSecondaryContainer = DsoColors.HighlightBlue,
    tertiary = DsoColors.DarkTextSecondary,
    onTertiary = DsoColors.DarkBg,
    background = DsoColors.DarkBg,
    onBackground = DsoColors.DarkTextPrimary,
    surface = DsoColors.DarkSurface,
    onSurface = DsoColors.DarkTextPrimary,
    surfaceVariant = DsoColors.DarkInputBg,
    onSurfaceVariant = DsoColors.DarkTextSecondary,
    surfaceContainerLowest = DsoColors.DarkBg,
    surfaceContainerLow = DsoColors.DarkSurfaceLow,
    surfaceContainer = DsoColors.DarkSurface,
    surfaceContainerHigh = DsoColors.DarkSurfaceHigh,
    surfaceContainerHighest = DsoColors.DarkInputBg,
    surfaceBright = DsoColors.DarkInputBg,
    surfaceDim = DsoColors.DarkBg,
    outline = DsoColors.DarkOutline,
    outlineVariant = DsoColors.DarkDivider,
    error = DsoColors.AppRed,
    onError = DsoColors.PageWhite,
    inverseSurface = DsoColors.DarkTextPrimary,
    inverseOnSurface = DsoColors.DarkBg
)

/**
 * v1.10.0 排版基线。
 *
 * 之前全 App 有 22 处硬编码 `fontSize = N.sp`（HomeScreen 18 处、LoginScreen 2 处、
 * TasksScreen / PlayerScreen / LyricsScreen 各 1 处），而 Typography 从未被覆写 ——
 * 结果是**用户把系统字号调大时，这些文字不会跟着放大**，页面出现大小混排。
 * 这里定义一套和 Dso 视觉一致的缩放基线，并把关键档位按 1.15x 抬一档，
 * 让 "把字调大" 在所有页面一致生效。
 */
private fun dsoTypography(): Typography {
    val base = Typography()
    return base.copy(
        displaySmall = base.displaySmall.copy(fontSize = 34.sp, fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.copy(fontSize = 15.sp),
        bodyMedium = base.bodyMedium.copy(fontSize = 14.sp),
        bodySmall = base.bodySmall.copy(fontSize = 13.sp),
        labelLarge = base.labelLarge.copy(fontSize = 14.sp),
        labelMedium = base.labelMedium.copy(fontSize = 12.sp),
        labelSmall = base.labelSmall.copy(fontSize = 11.sp)
    )
}

/**
 * v1.10.0：少量"离散档位"的文字样式。
 *
 * M3 Typography 的 15 个档位里没有 9sp / 18sp 这类尺寸，但卡片角标与歌词高亮行确实需要。
 * 与其散落在各页写 `fontSize = 9.sp`（22 处，且不跟系统字号缩放），
 * 同样收敛到这里，跟 Typography 一起做统一缩放源。
 */
object DsoTextStyles {
    /** 卡片左上角"歌单/专辑"角标。 */
    val Badge = TextStyle(fontSize = 9.sp, lineHeight = 12.sp, fontWeight = FontWeight.Medium)
    /** 歌词当前行。 */
    val LyricActive = TextStyle(fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold)
    /** 歌词普通行。 */
    val LyricIdle = TextStyle(fontSize = 15.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal)
}

/**
 * v1.10.0：主题相关的"非 M3 语义"颜色（封面占位、CD 圆盘、红底字色）。
 *
 * M3 的 ColorScheme 没有对应 role，之前这些值被硬编码成浅色常量写在 Composable 里，
 * 深色主题直接失效。用 CompositionLocal 提供，页面里读 `LocalDsoPalette.current.coverPlaceholder`。
 */
@Immutable
data class DsoPalette(
    val coverPlaceholder: Color,
    val cdPlate: Color,
    val onRed: Color
)

private val LightPalette = DsoPalette(
    coverPlaceholder = DsoColors.CoverPlaceholderLight,
    cdPlate = DsoColors.CdPlateLight,
    onRed = DsoColors.OnRedLight
)

private val DarkPalette = DsoPalette(
    coverPlaceholder = DsoColors.CoverPlaceholderDark,
    cdPlate = DsoColors.CdPlateDark,
    onRed = DsoColors.OnRedDark
)

val LocalDsoPalette = staticCompositionLocalOf { LightPalette }

/** themeMode: "system" | "light" | "dark" (DataStore theme_mode, docs/01 §5). */
@Composable
fun MusicPipeTheme(themeMode: String = "system", content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    CompositionLocalProvider(LocalDsoPalette provides if (dark) DarkPalette else LightPalette) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = dsoTypography(),
            content = content
        )
    }
}
