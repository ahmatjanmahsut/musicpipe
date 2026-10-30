package com.musicpipe.player.media.car

import com.musicpipe.player.data.model.LyricLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.16.0 车载歌词的纯逻辑回归。
 *
 * 这两件事必须钉死，否则用户看到的现象会非常难解释：
 *  1. **没歌词时绝不能覆盖真实歌名**（车机是唯一屏幕，显示"暂无歌词"等于把歌名弄丢）。
 *  2. 三种字段布局各自的映射不能串（写错就等于歌词出现在专辑行、而歌名跑到歌手行）。
 */
class CarLyricsFormatTest {

    private fun fields(line: String?, layout: CarLyricsLayout, maxChars: Int = 48) =
        CarLyricsFormatter.fields("七里香", "周杰伦", "七里香", line, layout, maxChars)

    @Test
    fun withoutLyricLineRealMetadataIsKept() {
        for (layout in CarLyricsLayout.entries) {
            val f = fields(null, layout)
            assertEquals("七里香", f.title)
            assertEquals("周杰伦", f.artist)
            assertEquals("七里香", f.album)
        }
    }

    @Test
    fun blankLyricLineIsTreatedAsNoLyric() {
        val f = fields("   ", CarLyricsLayout.TITLE)
        assertEquals("七里香", f.title)
        assertEquals("周杰伦", f.artist)
    }

    @Test
    fun titleLayoutPutsLineOnTitleAndKeepsSongNameOnArtist() {
        val f = fields("窗外的麻雀 在电线杆上多嘴", CarLyricsLayout.TITLE)
        assertEquals("窗外的麻雀 在电线杆上多嘴", f.title)
        // 歌名不能丢：挪到第二行，拼成「歌名 · 歌手」
        assertEquals("七里香 · 周杰伦", f.artist)
        assertEquals("七里香", f.album)
    }

    @Test
    fun artistLayoutKeepsTitleAndPutsLineOnArtist() {
        val f = fields("窗外的麻雀", CarLyricsLayout.ARTIST)
        assertEquals("七里香", f.title)
        assertEquals("窗外的麻雀", f.artist)
        assertEquals("七里香", f.album)
    }

    @Test
    fun albumLayoutKeepsBothAndPutsLineOnAlbum() {
        val f = fields("窗外的麻雀", CarLyricsLayout.ALBUM)
        assertEquals("七里香", f.title)
        assertEquals("周杰伦", f.artist)
        assertEquals("窗外的麻雀", f.album)
    }

    @Test
    fun missingArtistDoesNotProduceDanglingSeparator() {
        val f = CarLyricsFormatter.fields("歌名", "", "", "一句歌词", CarLyricsLayout.TITLE, 48)
        assertEquals("歌名", f.artist)
    }

    @Test
    fun longLinesAreClippedWithEllipsis() {
        val long = "一".repeat(80)
        val clipped = CarLyricsFormatter.clip(long, 20)
        assertEquals(20, clipped.length)
        assertTrue(clipped.endsWith("…"))
        // 短行原样返回
        assertEquals("短句", CarLyricsFormatter.clip("短句", 20))
    }

    @Test
    fun clipFloorProtectsAgainstAbsurdLimits() {
        val clipped = CarLyricsFormatter.clip("一二三四五六七八九十", 1)
        assertEquals(4, clipped.length)
    }

    @Test
    fun newlinesAndExtraSpacesAreCollapsed() {
        assertEquals("a b c", CarLyricsFormatter.clean("  a\nb\r\n   c  "))
        val f = CarLyricsFormatter.fields("t", "a", "b", "第一行\n第二行", CarLyricsLayout.ARTIST, 48)
        assertEquals("第一行 第二行", f.artist)
    }

    @Test
    fun cursorResolvesLinesWithOffset() {
        val lines = listOf(
            LyricLine(5_000, "第一句"),
            LyricLine(10_000, "第二句"),
            LyricLine(15_000, "第三句")
        )
        val cursor = CarLyricCursor(lines, offsetMs = 0)
        assertNull(cursor.lineAt(4_999))
        assertEquals("第一句", cursor.lineAt(5_000))
        assertEquals("第一句", cursor.lineAt(9_999))
        assertEquals("第二句", cursor.lineAt(10_000))
        assertEquals("第三句", cursor.lineAt(500_000))
        assertEquals(2, cursor.indexAt(15_000))
    }

    @Test
    fun positiveOffsetSendsLinesEarlier() {
        val lines = listOf(LyricLine(10_000, "第二句"))
        // 正数 = 提前：播到 9.8 秒时就把 10 秒那句推给车机（补偿蓝牙延迟）
        assertEquals("第二句", CarLyricCursor(lines, offsetMs = 250).lineAt(9_800))
        assertNull(CarLyricCursor(lines, offsetMs = 0).lineAt(9_800))
        // 负数 = 延后
        assertNull(CarLyricCursor(lines, offsetMs = -500).lineAt(10_200).let { it ?: null })
    }

    @Test
    fun emptyCursorNeverProducesALine() {
        val cursor = CarLyricCursor(emptyList(), 300)
        assertTrue(cursor.isEmpty)
        assertEquals(0, cursor.size)
        assertNull(cursor.lineAt(123_456))
        assertEquals(-1, cursor.indexAt(0))
    }

    @Test
    fun modeActivationRules() {
        val car = AudioOutputState(AudioOutputKind.BT_CAR, "Mazda")
        val headphone = AudioOutputState(AudioOutputKind.BT_HEADPHONE, "WH-1000XM5")
        assertTrue(CarLyricsSettings(mode = CarLyricsMode.AUTO).isActive(car))
        assertTrue(!CarLyricsSettings(mode = CarLyricsMode.AUTO).isActive(headphone))
        assertTrue(CarLyricsSettings(mode = CarLyricsMode.ALWAYS).isActive(headphone))
        assertTrue(!CarLyricsSettings(mode = CarLyricsMode.OFF).isActive(car))
    }

    @Test
    fun settingsParsersFallBackToDefaults() {
        assertEquals(CarLyricsMode.AUTO, CarLyricsMode.fromId(null))
        assertEquals(CarLyricsMode.AUTO, CarLyricsMode.fromId("nonsense"))
        assertEquals(CarLyricsMode.ALWAYS, CarLyricsMode.fromId("always"))
        assertEquals(CarLyricsLayout.TITLE, CarLyricsLayout.fromId(null))
        assertEquals(CarLyricsLayout.ALBUM, CarLyricsLayout.fromId("album"))
    }
}
