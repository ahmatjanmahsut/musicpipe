package com.musicpipe.player.util

object VersionUtil {
    /**
     * Update decision per docs/01 §3.4: latest.versionCode > current → update;
     * equal or smaller → already latest. Pure function, unit-testable.
     */
    fun needsUpdate(currentVersionCode: Int, latestVersionCode: Int): Boolean =
        latestVersionCode > currentVersionCode

    fun humanReadableSize(bytes: Long): String = when {
        bytes >= 1024L * 1024 * 1024 -> String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024))
        bytes >= 1024L * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024))
        bytes >= 1024L -> String.format("%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}
