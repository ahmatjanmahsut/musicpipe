package com.musicpipe.player.ui.theme

import androidx.compose.ui.graphics.Color

/** DsoMusic palette — docs/03-UI风格规范.md (values/colors.xml extraction). */
object DsoColors {
    val AppRed = Color(0xFFDB2C1F)        // colorAppThemeColor
    val HighlightBlue = Color(0xFF0470E6)
    val PageWhite = Color(0xFFFFFFFF)
    val PageBg = Color(0xFFF9F9F9)
    val TextPrimary = Color(0xFF050505)
    val TextSecondary = Color(0xFF787878)
    val TextWeak = Color(0xFF969696)
    val TextHint = Color(0xFFC8C8C8)
    val Divider = Color(0xFFE6E6E6)
    val InputBg = Color(0xFFF5F5F5)

    /**
     * v1.10.0：封面占位与 CD 圆盘改成**分主题**常量。
     *
     * 之前这两个值是单份的浅色（#F3F3F3 / 0x80FFFFFF），被直接写死在 5 个 Composable 里，
     * 深色主题下每张未加载的封面都是近白方块、播放页圆盘变成一块中灰大圆。
     * 现在由 [DsoPalette] 按主题提供（见 Theme.kt 的 LocalDsoPalette）。
     */
    val CoverPlaceholderLight = Color(0xFFF3F3F3)
    val CoverPlaceholderDark = Color(0xFF262626)

    /** 播放页 CDs 圆盘：浅色靠极浅灰区分于页面底色，深色靠略亮的表面色。 */
    val CdPlateLight = Color(0xFFF0F0F0)
    val CdPlateDark = Color(0xFF242424)

    /** 深色下"红底白字"的对比色；浅色沿用纯白。 */
    val OnRedLight = Color(0xFFFFFFFF)
    val OnRedDark = Color(0xFFFFFFFF)

    // Dark theme (docs/03)
    val DarkBg = Color(0xFF121212)
    val DarkSurface = Color(0xFF1E1E1E)
    val DarkTextPrimary = Color(0xFFFFFFFF)
    val DarkTextSecondary = Color(0xFFB0B0B0)
    val DarkDivider = Color(0xFF2C2C2C)
    val DarkInputBg = Color(0xFF2A2A2A)
    val DarkSurfaceHigh = Color(0xFF262626)
    val DarkSurfaceLow = Color(0xFF1A1A1A)
    val DarkOutline = Color(0xFF3A3A3A)
}
