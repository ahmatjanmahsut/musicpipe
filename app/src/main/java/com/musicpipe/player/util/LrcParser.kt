package com.musicpipe.player.util

import com.musicpipe.player.data.model.LyricLine

/**
 * LRC parser (docs/01 §5 LyricLine). Supports multiple time tags per line,
 * [mm:ss.xx] / [mm:ss.xxx] / [mm:ss], meta tags ([ti:][ar:][al:][by:][offset:])
 * and the offset shift. Pure Kotlin — unit tested.
 */
object LrcParser {

    private val TIME_TAG = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val META_OFFSET = Regex("""\[offset:\s*([+-]?\d+)\s*]""", RegexOption.IGNORE_CASE)

    fun parse(lrc: String?): List<LyricLine> {
        if (lrc.isNullOrBlank()) return emptyList()
        var offsetMs = 0L
        META_OFFSET.find(lrc)?.let { offsetMs = it.groupValues[1].toLongOrNull() ?: 0L }

        val lines = ArrayList<LyricLine>()
        for (raw in lrc.split("\n")) {
            val matches = TIME_TAG.findAll(raw).toList()
            if (matches.isEmpty()) continue
            val text = raw.substring(matches.last().range.last + 1).trim()
            for (m in matches) {
                val min = m.groupValues[1].toLong()
                val sec = m.groupValues[2].toLong()
                val fracRaw = m.groupValues[3]
                val fracMs = when (fracRaw.length) {
                    0 -> 0L
                    1 -> fracRaw.toLong() * 100
                    2 -> fracRaw.toLong() * 10
                    else -> fracRaw.take(3).toLong()
                }
                val timeMs = (min * 60_000 + sec * 1000 + fracMs + offsetMs).coerceAtLeast(0)
                lines.add(LyricLine(timeMs, text))
            }
        }
        return lines.sortedBy { it.timeMs }
    }

    /**
     * 单行是否处于高亮态（v1.10.0）。
     *
     * 歌词页每行各自用 derivedStateOf 调它来判断"我是不是当前行"，
     * 这样播放位置推进时只有跨越边界的两行会重组，而不是整屏可见行。
     * 判定与 [activeIndex] 严格等价：该行已开始、且下一行尚未开始（或已是最后一行）。
     * 传入 lines 是为了拿到下一行的起始时间。
     */
    fun isActive(lines: List<LyricLine>, index: Int, positionMs: Long): Boolean {
        if (index < 0 || index >= lines.size) return false
        val start = lines[index].timeMs
        if (positionMs < start) return false
        val next = if (index + 1 < lines.size) lines[index + 1].timeMs else Long.MAX_VALUE
        return positionMs < next
    }

    /** Index of the active line at positionMs, or -1 (before first line / empty). */
    fun activeIndex(lines: List<LyricLine>, positionMs: Long): Int {
        if (lines.isEmpty()) return -1
        var lo = 0
        var hi = lines.size - 1
        var ans = -1
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            if (lines[mid].timeMs <= positionMs) { ans = mid; lo = mid + 1 } else hi = mid - 1
        }
        return ans
    }
}
