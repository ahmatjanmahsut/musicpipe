package com.musicpipe.player.util

object VersionUtil {
    /**
     * Update decision per docs/01 §3.4: latest.versionCode > current → update;
     * equal or smaller → already latest. Pure function, unit-testable.
     */
    fun needsUpdate(currentVersionCode: Int, latestVersionCode: Int): Boolean =
        latestVersionCode > currentVersionCode

    /**
     * v1.10.0（C5）：固定用 Locale.ROOT。
     *
     * 缺省重载按 Locale.getDefault() 走：在 de-DE 下小数点是逗号（"23,1 MB"），
     * 在阿拉伯语系下还会换成阿拉伯-印度数字。安装包体积是技术数值，
     * 不该随界面语言变形 —— 而且我们的单测里已经踩过坑（size 断言前要 replace(',', '.')）。
     */
    fun humanReadableSize(bytes: Long): String = when {
        bytes >= 1024L * 1024 * 1024 ->
            String.format(java.util.Locale.ROOT, "%.2f GB", bytes / (1024.0 * 1024 * 1024))
        bytes >= 1024L * 1024 ->
            String.format(java.util.Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024))
        bytes >= 1024L -> String.format(java.util.Locale.ROOT, "%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}
