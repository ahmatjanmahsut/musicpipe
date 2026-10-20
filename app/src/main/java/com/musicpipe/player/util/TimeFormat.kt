package com.musicpipe.player.util

object TimeFormat {
    /** mm:ss for player UI; h:mm:ss beyond an hour. */
    fun format(durationMs: Long): String {
        if (durationMs <= 0) return "00:00"
        val totalSec = durationMs / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
        else String.format("%02d:%02d", m, s)
    }

    /** yyyy-MM-dd HH:mm for history/settings. */
    fun formatDateTime(unixMs: Long): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(unixMs))
    }
}
