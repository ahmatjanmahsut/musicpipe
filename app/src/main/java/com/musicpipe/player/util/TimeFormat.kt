package com.musicpipe.player.util

object TimeFormat {
    /** mm:ss for player UI; h:mm:ss beyond an hour. */
    fun format(durationMs: Long): String {
        if (durationMs <= 0) return "00:00"
        val totalSec = durationMs / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        // v1.10.0（C5）：必须显式给 Locale.ROOT。
        // 缺省重载用 Locale.getDefault()，在把数字系统换成阿拉伯-印度数字
        // （ar-EG / fa-IR / bn-IN 等）的系统语言下，String.format 会输出 ٠٣:٢٥ 这种
        // 本地数字，进度条/时长就直接显示成看不懂的符号。
        // 时间与大小这类"技术数值"要用固定格式，不跟随界面语言。
        return if (h > 0) String.format(java.util.Locale.ROOT, "%d:%02d:%02d", h, m, s)
        else String.format(java.util.Locale.ROOT, "%02d:%02d", m, s)
    }

    /** yyyy-MM-dd HH:mm for history/settings. */
    fun formatDateTime(unixMs: Long): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(unixMs))
    }
}
