package com.musicpipe.player.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.7.0 回归测试：主页卡片在管理态下"点不动"的 bug。
 *
 * 根因是同一个"选中集合"被两种键索引：
 *  - [HomeFolder.id]        = "share:mv6kz..."（网格内部带前缀的 folderId）
 *  - [HomeFolder.shareId]   = "mv6kz..."      （sharelinks.id，服务端与 VM 都认这个）
 *
 * UI 用 folder.id 判断选中，而 selectedPlaylists 里装的是 shareId → 恒为 false，
 * 点击虽然写进了集合，卡片却毫无变化。这些用例把"必须用 shareId"固化下来。
 */
class HomePlaylistRulesTest {

    private fun shareFolder(id: String) = HomeFolder(
        id = "share:" + id,
        title = "P-" + id,
        kind = HomeFolder.Kind.SHARE,
        songs = emptyList(),
        missing = 0,
        shareId = id
    )

    private fun albumFolder(name: String) = HomeFolder(
        id = "album:" + name,
        title = name,
        kind = HomeFolder.Kind.ALBUM,
        songs = emptyList()
    )

    @Test
    fun selectionUsesShareIdNotPrefixedFolderId() {
        val folder = shareFolder("mv6kz")
        // VM 里存的是不带前缀的 shareId
        assertTrue(PlaylistManageRules.isSelected(folder, setOf("mv6kz")))
        // 之前 UI 传的是带前缀的 folder.id，正是这个组合恒为 false
        assertFalse(PlaylistManageRules.isSelected(folder, setOf("share:mv6kz")))
    }

    @Test
    fun albumAndUncategorizedFoldersAreNeverSelected() {
        // 专辑/未分类没有 shareId，点它们不该有任何选中态
        assertFalse(PlaylistManageRules.isSelected(albumFolder("B"), setOf("B", "album:B")))
    }

    @Test
    fun mergeTargetPrefersFirstSelectedInFolderOrder() {
        val list = listOf(shareFolder("a"), shareFolder("b"), shareFolder("c"))
        // 选中 b 与 c，按 folder 顺序第一个被选中的是 b
        assertEquals("b", PlaylistManageRules.mergeTarget(list, setOf("c", "b")))
    }

    @Test
    fun mergeTargetIsNullWhenSelectionUsesPrefixedIds() {
        // 用带前缀的键永远匹配不到 —— 保留这个用例防止回退到旧写法
        val list = listOf(shareFolder("a"))
        assertNull(PlaylistManageRules.mergeTarget(list, setOf("share:a")))
    }

    @Test
    fun mergeSourcesExcludesTargetAndKeepsRealShareIds() {
        val list = listOf(shareFolder("a"), shareFolder("b"), shareFolder("c"))
        val selected = setOf("a", "b", "c")
        val target = PlaylistManageRules.mergeTarget(list, selected)
        assertEquals("a", target)
        assertEquals(listOf("b", "c"), PlaylistManageRules.mergeSources(target, selected))
    }
}
