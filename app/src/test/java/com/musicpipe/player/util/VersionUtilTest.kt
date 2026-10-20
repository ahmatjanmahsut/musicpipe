package com.musicpipe.player.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionUtilTest {

    @Test
    fun `needsUpdate strictly greater versionCode`() {
        assertTrue(VersionUtil.needsUpdate(1, 2))
        assertTrue(VersionUtil.needsUpdate(99, 100))
        assertFalse(VersionUtil.needsUpdate(2, 2))  // 版本一致 → 已最新
        assertFalse(VersionUtil.needsUpdate(3, 2))  // 本地更新（不应提示降级）
    }

    private fun size(bytes: Long) = VersionUtil.humanReadableSize(bytes).replace(',', '.')

    @Test
    fun `humanReadableSize buckets`() {
        assertEquals("512 B", size(512))
        assertEquals("1.0 KB", size(1024))
        assertEquals("2.0 KB", size(2048))
        assertEquals("5.0 MB", size(5L * 1024 * 1024))
        assertEquals("123.4 MB", size(129_394_278L))
        assertEquals("2.00 GB", size(2L * 1024 * 1024 * 1024))
    }
}
