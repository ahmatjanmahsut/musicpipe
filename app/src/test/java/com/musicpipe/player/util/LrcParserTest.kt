package com.musicpipe.player.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {

    @Test
    fun `parses basic mm ss xx lines and sorts by time`() {
        val lrc = """
            [00:12.50]second line
            [00:01.10]first line
        """.trimIndent()
        val lines = LrcParser.parse(lrc)
        assertEquals(2, lines.size)
        assertEquals(1100L, lines[0].timeMs)
        assertEquals("first line", lines[0].text)
        assertEquals(12500L, lines[1].timeMs)
        assertEquals("second line", lines[1].text)
    }

    @Test
    fun `supports two and three digit fractions and no fraction`() {
        val lines = LrcParser.parse("[00:01.12]a\n[00:02.123]b\n[00:03]c")
        assertEquals(listOf(1120L, 2123L, 3000L), lines.map { it.timeMs })
    }

    @Test
    fun `multiple time tags on one line produce one entry each`() {
        val lines = LrcParser.parse("[00:05.00][01:05.00]chorus")
        assertEquals(2, lines.size)
        assertEquals(5000L, lines[0].timeMs)
        assertEquals(65000L, lines[1].timeMs)
        assertTrue(lines.all { it.text == "chorus" })
    }

    @Test
    fun `meta tags are ignored and offset shifts times`() {
        val lrc = """
            [ti:Title]
            [ar:Artist]
            [offset:+500]
            [00:10.00]hello
        """.trimIndent()
        val lines = LrcParser.parse(lrc)
        assertEquals(1, lines.size)
        assertEquals(10500L, lines[0].timeMs)
        assertEquals("hello", lines[0].text)
    }

    @Test
    fun `negative offset clamps at zero`() {
        val lines = LrcParser.parse("[offset:-3000]\n[00:01.00]x")
        assertEquals(0L, lines[0].timeMs)
    }

    @Test
    fun `null blank and tagless input parse to empty`() {
        assertEquals(0, LrcParser.parse(null).size)
        assertEquals(0, LrcParser.parse("   ").size)
        assertEquals(0, LrcParser.parse("no tags here\n[ti:only meta]").size)
    }

    @Test
    fun `activeIndex binary search boundaries`() {
        val lines = LrcParser.parse("[00:05.00]a\n[00:10.00]b\n[00:15.00]c")
        assertEquals(-1, LrcParser.activeIndex(lines, 4999))
        assertEquals(0, LrcParser.activeIndex(lines, 5000))
        assertEquals(0, LrcParser.activeIndex(lines, 9999))
        assertEquals(1, LrcParser.activeIndex(lines, 10000))
        assertEquals(2, LrcParser.activeIndex(lines, 999999))
        assertEquals(-1, LrcParser.activeIndex(emptyList(), 1000))
    }
}
