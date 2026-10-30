package com.musicpipe.player.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 时间格式化 —— 列表每行都要调，所以这里刻意做成**零构造**实现。
 *
 * v1.14.0 性能修复（滚动卡顿）：
 * 旧 [formatDateTime] 每次调用都 `new SimpleDateFormat(...)`。SimpleDateFormat 的构造
 * 要解析 pattern、建 DateFormatSymbols、填 calendar 字段，实测量级是几十微秒 ——
 * 放在播放历史/用户管理这种列表行里，一次滚动组合十几行就是几十次构造，
 * 是典型的"滚动期分配"。改为 ThreadLocal 缓存实例。
 *
 * 注意：SimpleDateFormat 本身**不是线程安全的**（内部 calendar 可变），
 * 所以不能做成单个 static 字段；ThreadLocal 是标准解法，每个线程一份互不干扰。
 */
object TimeFormat {

    /** 格式串常量化的 ThreadLocal 实例。列表只在主线程组合，通常是单实例命中。 */
    private val dateTimeFormat = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    }

    /** mm:ss for player UI; h:mm:ss beyond an hour. */
    fun format(durationMs: Long): String {
        if (durationMs <= 0) return "00:00"
        val totalSec = durationMs / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        // v1.10.0（C5）：数字必须是 ASCII。
        // String.format 的缺省重载用 Locale.getDefault()，在把数字系统换成阿拉伯-印度数字
        // （ar-EG / fa-IR / bn-IN 等）的系统语言下会输出 ٠٣:٢٥ 这种本地数字，
        // 进度条/时长就变成看不懂的符号 —— 所以旧版显式传了 Locale.ROOT。
        //
        // v1.14.0：干脆不用 String.format。它每次调用都要新建 Formatter + 解析格式串
        // （实测量级微秒），而这里是**列表每一行**的时长显示，滚动时每秒几十次。
        // 手写补零既避免了分配，也天然只有 ASCII 数字（不读任何 locale），
        // 顺带把 C5 那类本地数字问题从根上消除。
        val sb = StringBuilder(8)
        if (h > 0) {
            sb.append(h).append(':')
            pad2(sb, m)
        } else {
            pad2(sb, m)
        }
        sb.append(':')
        pad2(sb, s)
        return sb.toString()
    }

    /** 追加两位补零的十进制数，不产生中间 String。 */
    private fun pad2(sb: StringBuilder, v: Long) {
        if (v < 10) sb.append('0')
        sb.append(v)
    }

    /** yyyy-MM-dd HH:mm for history/settings. */
    fun formatDateTime(unixMs: Long): String {
        return dateTimeFormat.get()!!.format(Date(unixMs))
    }
}
