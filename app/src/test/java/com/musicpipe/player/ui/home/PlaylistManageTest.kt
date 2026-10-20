package com.musicpipe.player.ui.home

import com.musicpipe.player.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.6.0 需求3：主页歌单管理的纯逻辑约束。
 *
 * 这些测试锁住的是"UI 允许做什么"的边界 —— 删歌单绝不碰曲库音频、合并方向必须唯一、
 * 单选时不能进合并流程。真正的网络调用在 NetRepository，不在这里测。
 */
class PlaylistManageTest {

    private fun folder(id: String, title: String, kind: HomeFolder.Kind = HomeFolder.Kind.SHARE): HomeFolder {
        val song = Song(
            id = "t_" + id, title = title + " 的歌", artist = "a", album = "b",
            durationMs = 1000, coverUrl = null, platform = "wy", streamUrl = ""
        )
        return HomeFolder(id = id, title = title, kind = kind, songs = listOf(song), shareId = id)
    }

    /** 构造分享歌单列表：folderId 带 "share:" 前缀，shareId 是裸 id（服务端要的那个）。 */
    private fun shares(vararg ids: String) =
        ids.map { folder("share:" + it, "歌单 " + it).copy(shareId = it) }

    @Test
    fun deleteIsOnlyEnabledWhenSomethingSelected() {
        assertFalse(PlaylistManageRules.canDelete(emptySet()))
        assertTrue(PlaylistManageRules.canDelete(setOf("share:a")))
        assertTrue(PlaylistManageRules.canDelete(setOf("share:a", "share:b")))
    }

    @Test
    fun mergeNeedsAtLeastTwoSelections() {
        assertFalse(PlaylistManageRules.canMerge(emptySet()))
        assertFalse(PlaylistManageRules.canMerge(setOf("share:a")))
        assertTrue(PlaylistManageRules.canMerge(setOf("share:a", "share:b")))
    }

    @Test
    fun mergeTargetDefaultsToFirstSelected() {
        // folderList 的顺序决定默认目标，与 Set 迭代顺序无关。
        // v1.7.0：选中集合装的是裸 shareId（sharelinks.id），不是带 "share:" 的 folderId。
        // 旧用例用带前缀的键，锁住的正是导致"管理态点不动"的那个错误约定，这里一并改正。
        val list = shares("a", "b")
        assertEquals("a", PlaylistManageRules.mergeTarget(list, setOf("b", "a")))
        assertEquals("a", PlaylistManageRules.mergeTarget(list, setOf("a")))
        assertEquals("b", PlaylistManageRules.mergeTarget(list, setOf("b")))
    }

    @Test
    fun mergeTargetIsNullWhenSelectionCollapses() {
        assertNull(PlaylistManageRules.mergeTarget(folderList = shares("a"), selected = emptySet()))
    }

    @Test
    fun mergeSourcesExcludeTheTargetAndDropPrefix() {
        val sel = setOf("a", "b", "c")
        val sources = PlaylistManageRules.mergeSources("a", sel)
        // 服务端 /shares/:id/merge 要的是 sharelinks.id（集合本身就是这个形态）。
        assertEquals(listOf("b", "c"), sources)
        assertFalse("a" in sources)
    }

    @Test
    fun onlyShareFoldersAreManageable() {
        val album = folder("album:x", "专辑 X", HomeFolder.Kind.ALBUM)
        val other = folder("__uncategorized__", "未分类", HomeFolder.Kind.UNCATEGORIZED)
        val share = folder("share:z", "歌单 Z")
        assertFalse(PlaylistManageRules.isManageable(album))
        assertFalse(PlaylistManageRules.isManageable(other))
        assertTrue(PlaylistManageRules.isManageable(share))
    }

    @Test
    fun selectedIdsSurviveAfterDeletion() {
        // 删除后剩下的选择集只应保留仍然存在的歌单，避免 UI 底栏显示"已选 2"但其实只剩 1 个。
        val remaining = shares("a", "c").map { it.id }.toSet()
        val kept = PlaylistManageRules.pruneSelection(setOf("share:a", "share:b", "share:c"), remaining)
        assertEquals(setOf("share:a", "share:c"), kept)
    }

    @Test
    fun selectionIsIgnoredForNonShareFolders() {
        // 管理态下若因为数据刷新混入了专辑卡片，点击不应把它加入合并集合。
        val kept = PlaylistManageRules.pruneSelection(
            setOf("share:a", "album:x"),
            setOf("share:a", "album:x")
        )
        assertEquals(setOf("share:a"), kept)
    }
}