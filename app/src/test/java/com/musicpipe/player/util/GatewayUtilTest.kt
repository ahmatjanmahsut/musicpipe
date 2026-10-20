package com.musicpipe.player.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-function tests for the /api/v1 gateway helpers. */
class GatewayUtilTest {

    @Test
    fun platformsParamIsNullWhenEmptyOrAllSelected() {
        assertNull(GatewayUtil.platformsParam(emptySet()))
        assertNull(GatewayUtil.platformsParam(setOf("kw", "kg", "tx", "wy")))
        assertNull(GatewayUtil.platformsParam(setOf("zz"))) // only invalid codes
    }

    @Test
    fun platformsParamSortsAndFiltersCodes() {
        assertEquals("kw,tx", GatewayUtil.platformsParam(setOf("tx", "kw")))
        assertEquals("kg,kw", GatewayUtil.platformsParam(setOf("kw", "kg")))
        assertEquals("kw", GatewayUtil.platformsParam(setOf("kw", "zz")))
    }

    @Test
    fun statusLabelCoversAllTaskStates() {
        assertEquals("排队中", GatewayUtil.statusLabel("pending"))
        assertEquals("排队中", GatewayUtil.statusLabel("queued"))
        assertEquals("下载中", GatewayUtil.statusLabel("downloading"))
        assertEquals("整理中", GatewayUtil.statusLabel("organizing"))
        assertEquals("已完成", GatewayUtil.statusLabel("done"))
        assertEquals("已入库", GatewayUtil.statusLabel("imported"))
        assertEquals("失败", GatewayUtil.statusLabel("failed"))
        assertEquals("已取消", GatewayUtil.statusLabel("cancelled"))
        assertEquals("-", GatewayUtil.statusLabel(null))
        assertEquals("weird", GatewayUtil.statusLabel("weird"))
    }

    @Test
    fun activeAndRemovableAreDisjointAndComplete() {
        for (s in listOf("pending", "queued", "downloading", "organizing")) {
            assertTrue(s, GatewayUtil.isActive(s))
            assertFalse(s, GatewayUtil.isRemovable(s))
        }
        for (s in listOf("done", "imported", "failed", "cancelled")) {
            assertTrue(s, GatewayUtil.isRemovable(s))
            assertFalse(s, GatewayUtil.isActive(s))
        }
        assertFalse(GatewayUtil.isActive(null))
        assertFalse(GatewayUtil.isRemovable("unknown"))
    }

    @Test
    fun qualityLabelMapsKnownQualities() {
        assertEquals("自动", GatewayUtil.qualityLabel(null))
        assertEquals("自动", GatewayUtil.qualityLabel(""))
        assertEquals("自动", GatewayUtil.qualityLabel("auto"))
        assertEquals("无损FLAC", GatewayUtil.qualityLabel("flac"))
        assertEquals("320kbps", GatewayUtil.qualityLabel("320k"))
        assertEquals("128kbps", GatewayUtil.qualityLabel("128k"))
        assertEquals("ogg", GatewayUtil.qualityLabel("ogg"))
    }

    @Test
    fun shareSongStateLabelPrefersInLibrary() {
        assertEquals("已在库", GatewayUtil.shareSongStateLabel("queued", 1))
        assertEquals("已在库", GatewayUtil.shareSongStateLabel(null, 1))
        assertEquals("排队中", GatewayUtil.shareSongStateLabel("queued", 0))
        assertEquals("已导入", GatewayUtil.shareSongStateLabel("done", 0))
        assertEquals("失败", GatewayUtil.shareSongStateLabel("failed", 0))
        assertEquals("已存在", GatewayUtil.shareSongStateLabel("exists", 0))
        assertEquals("待导入", GatewayUtil.shareSongStateLabel("preview", 0))
        assertEquals("待导入", GatewayUtil.shareSongStateLabel(null, 0))
    }

    // ── v1.6.0 需求2：下载失败提示的翻译 ────────────────────────────────────

    @Test
    fun humanizeTaskErrorExplainsQualityMismatch() {
        // 用户实际看到的原文（旧版直接展示这句，用户只会反复重试）
        val raw = "校验失败: 文件头(mp3)与扩展名(flac)不符"
        val msg = GatewayUtil.humanizeTaskError(raw)!!
        assertTrue(msg, msg.contains("音质"))
        assertTrue(msg, msg.contains("自动"))
        assertFalse("不应再把用户引向'文件损坏'", msg.contains("校验失败"))
    }

    @Test
    fun humanizeTaskErrorExplainsUnknownHeader() {
        // v1.6.0 嗅探生效后服务端的新措辞
        val msg = GatewayUtil.humanizeTaskError("校验失败: 无法识别的音频文件头")!!
        assertTrue(msg, msg.contains("128kbps") || msg.contains("自动"))
    }

    @Test
    fun humanizeTaskErrorExplainsPreviewOnlyAndSources() {
        assertTrue(GatewayUtil.humanizeTaskError("文件过小，疑似试听片段")!!.contains("试听"))
        assertTrue(GatewayUtil.humanizeTaskError("全部 2 个音源取链失败: timeout")!!.contains("音源"))
        assertTrue(GatewayUtil.humanizeTaskError("WebDAV 网盘未配置（设置页）")!!.contains("网盘"))
    }

    @Test
    fun humanizeTaskErrorPassesThroughUnknownAndHandlesBlank() {
        assertNull(GatewayUtil.humanizeTaskError(null))
        assertNull(GatewayUtil.humanizeTaskError("   "))
        assertEquals("某个未知错误", GatewayUtil.humanizeTaskError("某个未知错误"))
    }
}
