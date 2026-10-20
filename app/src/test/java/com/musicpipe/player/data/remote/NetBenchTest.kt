package com.musicpipe.player.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** v1.5.0 设置页网络测试的纯计算部分。 */
class NetBenchTest {

    @Test
    fun medianOfOddSamples() {
        assertEquals(15L, median(listOf(12L, 15L, 88L)))
    }

    @Test
    fun medianIgnoresInputOrder() {
        // 偶发一次 900ms 抖动不该把结论带偏 —— 这正是用中位数而非平均值的理由
        assertEquals(20L, median(listOf(900L, 20L, 18L)))
    }

    @Test
    fun medianOfEmptyIsZero() {
        assertEquals(0L, median(emptyList()))
    }

    @Test
    fun humanBytesScales() {
        assertEquals("512 B", humanBytes(512))
        assertEquals("1 KB", humanBytes(1024))
        assertEquals("2.0 MB", humanBytes(2L * 1024 * 1024))
    }

    @Test
    fun humanRateScales() {
        assertEquals("500 B/s", humanRate(500.0))
        assertEquals("1 KB/s", humanRate(1024.0))
        assertEquals("2.0 MB/s", humanRate(2.0 * 1024 * 1024))
    }

    @Test
    fun rateIsBytesPerSecond() {
        // 1MB / 500ms = 2MB/s
        val r = rate(1024L * 1024, 500)
        assertTrue(r > 2.0 * 1024 * 1024 - 1 && r < 2.0 * 1024 * 1024 + 1)
    }

    @Test
    fun rateGuardsAgainstZeroElapsed() {
        // 0ms 不能让结果为 Infinity
        assertEquals(0.0, rate(0, 0), 0.0001)
        assertTrue(rate(1024, 0).isFinite())
    }

    @Test
    fun formatSamplesJoinsWithUnit() {
        assertEquals("12ms / 15ms / 88ms", formatSamples(listOf(12L, 15L, 88L)))
    }

    @Test
    fun formatSamplesHandlesSingleAndEmpty() {
        assertEquals("7ms", formatSamples(listOf(7L)))
        assertEquals("", formatSamples(emptyList()))
    }
}
