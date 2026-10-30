package com.musicpipe.player.media.car

import com.musicpipe.player.data.model.LyricLine
import com.musicpipe.player.util.LrcParser

/**
 * 车机端能看到的三个文本字段。
 */
data class CarLyricFields(val title: String, val artist: String, val album: String)

/**
 * 把"当前歌词行 + 真实元数据"拼成要发给车机的三个字段（纯函数，可单测）。
 *
 * 设计约定：
 *  - 没有歌词（未匹配 / 纯音乐 / 播放器未就绪）时**原样返回真实元数据**，
 *    绝不写"暂无歌词"这种假内容去覆盖歌名 —— 车机是用户唯一的屏幕，信息优先级最高。
 *  - clip 只作用在**歌词行**上；真实歌名/歌手不截断（车机自己会滚）。
 *  - 所有字段都去掉换行：AVRCP 的文本是单行的，换行会让部分头单元显示成方块。
 */
object CarLyricsFormatter {

    const val DEFAULT_MAX_CHARS = 48

    fun fields(
        songTitle: String,
        songArtist: String,
        songAlbum: String,
        line: String?,
        layout: CarLyricsLayout,
        maxChars: Int = DEFAULT_MAX_CHARS
    ): CarLyricFields {
        val realTitle = clean(songTitle)
        val realArtist = clean(songArtist)
        val realAlbum = clean(songAlbum)
        val text = line?.let { clip(it, maxChars) }
        if (text.isNullOrBlank()) {
            return CarLyricFields(realTitle, realArtist, realAlbum)
        }
        return when (layout) {
            CarLyricsLayout.TITLE -> CarLyricFields(
                title = text,
                // 歌名不能丢：挪到第二行，拼成「歌名 · 歌手」。车机第二行放得下。
                artist = listOf(realTitle, realArtist).filter { it.isNotBlank() }.joinToString(" · "),
                album = realAlbum
            )
            CarLyricsLayout.ARTIST -> CarLyricFields(
                title = realTitle,
                artist = text,
                album = realAlbum
            )
            CarLyricsLayout.ALBUM -> CarLyricFields(
                title = realTitle,
                artist = realArtist,
                album = text
            )
        }
    }

    /** 折叠空白 + 去首尾空格。 */
    fun clean(raw: String): String =
        raw.replace('\r', ' ').replace('\n', ' ').replace(Regex("\\s+"), " ").trim()

    /**
     * 截断到 maxChars（含省略号）。
     *
     * 用字符数而不是显示宽度：车机的上限本来就是"多少字"，中英混排时按字符算最接近直觉，
     * 而且我们无法知道车机用的字形宽度。
     */
    fun clip(raw: String, maxChars: Int = DEFAULT_MAX_CHARS): String {
        val s = clean(raw)
        val limit = maxChars.coerceAtLeast(4)
        if (s.length <= limit) return s
        return s.take(limit - 1) + "…"
    }
}

/**
 * 播放位置 → 当前歌词行（纯函数，可单测）。
 *
 * 为什么要有这一层：LrcParser 只回答"第几行"，而车载场景还需要
 * 延迟补偿（[offsetMs]）与"越界返回 null"。把这些规则钉在一个地方，
 * 引擎与测试都用同一套语义，不会出现"页面高亮那行、车机显示另一行"。
 */
class CarLyricCursor(
    private val lines: List<LyricLine>,
    private val offsetMs: Long = 0L
) {
    /** 生效位置 = 播放位置 + 补偿（正数表示提前）。 */
    fun effectivePosition(positionMs: Long): Long = positionMs + offsetMs

    fun indexAt(positionMs: Long): Int =
        if (lines.isEmpty()) -1 else LrcParser.activeIndex(lines, effectivePosition(positionMs))

    fun lineAt(positionMs: Long): String? {
        val i = indexAt(positionMs)
        return if (i >= 0) lines[i].text else null
    }

    val isEmpty: Boolean get() = lines.isEmpty()
    val size: Int get() = lines.size
}
