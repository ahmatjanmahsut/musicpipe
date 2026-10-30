package com.musicpipe.player.ui.theme

/** 宝骏悦也 1920×720 等超宽横屏使用车机布局；手机竖屏保持原布局。 */
object ResponsiveLayout {
    fun isWideCar(widthDp: Int, heightDp: Int): Boolean =
        widthDp >= 640 && heightDp > 0 && widthDp.toFloat() / heightDp.toFloat() >= 1.6f
}
