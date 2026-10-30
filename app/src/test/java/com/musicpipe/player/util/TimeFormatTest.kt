package com.musicpipe.player.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/**
 * v1.14.0：`format` 从 String.format 改为手写补零（滚动期分配优化）。
 * 行为必须逐字等价 —— 这里的用例锁住输出格式，避免优化改坏显示。
 */
class TimeFormatTest {

    @Test
    fun zeroAndNegativeBecomeZeroClock() {
        assertEquals("00:00", TimeFormat.format(0))
        assertEquals("00:00", TimeFormat.format(-1))
    }

    @Test
    fun underOneMinuteKeepsTwoDigitSeconds() {
        assertEquals("00:05", TimeFormat.format(5_000))
        assertEquals("00:59", TimeFormat.format(59_999))
    }

    @Test
    fun minutesArePadded() {
        assertEquals("01:00", TimeFormat.format(60_000))
        assertEquals("09:07", TimeFormat.format(9 * 60_000 + 7_000))
        assertEquals("59:59", TimeFormat.format(59 * 60_000 + 59_000))
    }

    @Test
    fun hourFormDropsPadOnHoursButKeepsMinutesSeconds() {
        assertEquals("1:00:00", TimeFormat.format(3_600_000))
        assertEquals("1:02:03", TimeFormat.format(3_600_000 + 2 * 60_000 + 3_000))
        assertEquals("10:00:00", TimeFormat.format(10 * 3_600_000))
    }

    @Test
    fun digitsStayAsciiUnderArabicLocale() {
        // v1.10.0（C5）的原始诉求：换成阿拉伯-印度数字的系统语言下也不能出现 ٠٣:٢٥。
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ar-EG"))
            assertEquals("03:25", TimeFormat.format(3 * 60_000 + 25_000))
            assertEquals("1:02:03", TimeFormat.format(3_600_000 + 2 * 60_000 + 3_000))
        } finally {
            Locale.setDefault(original)
        }
    }
}
