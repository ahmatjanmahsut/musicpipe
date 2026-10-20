package com.musicpipe.player.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = DsoColors.AppRed,
    onPrimary = DsoColors.PageWhite,
    primaryContainer = DsoColors.AppRed.copy(alpha = 0.12f),
    onPrimaryContainer = DsoColors.AppRed,
    secondary = DsoColors.HighlightBlue,
    background = DsoColors.PageBg,
    onBackground = DsoColors.TextPrimary,
    surface = DsoColors.PageWhite,
    onSurface = DsoColors.TextPrimary,
    surfaceVariant = DsoColors.InputBg,
    onSurfaceVariant = DsoColors.TextSecondary,
    surfaceContainerHigh = DsoColors.PageWhite,
    outlineVariant = DsoColors.Divider,
    error = DsoColors.AppRed
)

private val DarkColors = darkColorScheme(
    primary = DsoColors.AppRed,
    onPrimary = DsoColors.DarkTextPrimary,
    secondary = DsoColors.HighlightBlue,
    background = DsoColors.DarkBg,
    onBackground = DsoColors.DarkTextPrimary,
    surface = DsoColors.DarkSurface,
    onSurface = DsoColors.DarkTextPrimary,
    surfaceVariant = DsoColors.DarkInputBg,
    onSurfaceVariant = DsoColors.DarkTextSecondary,
    surfaceContainerHigh = DsoColors.DarkSurfaceHigh,
    outlineVariant = DsoColors.DarkDivider,
    error = DsoColors.AppRed
)

/** themeMode: "system" | "light" | "dark" (DataStore theme_mode, docs/01 §5). */
@Composable
fun MusicPipeTheme(themeMode: String = "system", content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        content = content
    )
}
