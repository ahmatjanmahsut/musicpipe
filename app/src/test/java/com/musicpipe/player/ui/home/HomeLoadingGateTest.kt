package com.musicpipe.player.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * v1.15.0 回归：启动后"一直转圈"的成因就是 [HomeLoadingGate.resolve] 缺少 bootstrapped 维度。
 * 这里把每种组合都钉住，避免以后有人把 SYNCING_EMPTY 合并回 BOOTSTRAPPING。
 */
class HomeLoadingGateTest {

    @Test
    fun coldStartBeforeSnapshotReadShowsLoadingIndicator() {
        // 本地快照还没读完 —— 这一瞬间允许出现加载指示器（它自带 220ms 延迟门，
        // 快路径下根本不会真的显示出来）。
        assertEquals(
            HomeContentState.BOOTSTRAPPING,
            HomeLoadingGate.resolve(loading = true, hasContent = false, bootstrapped = false, hasOpenFolder = false)
        )
    }

    @Test
    fun emptyLibraryWhileSyncingMustNotUseSpinningCounter() {
        // 这一条是本次修复的核心：本地已读过（确实没有歌），正在等网络。
        // 旧实现会一路显示 000->099 循环，直到网络返回 —— 用户口中的"圈一直转"。
        assertEquals(
            HomeContentState.SYNCING_EMPTY,
            HomeLoadingGate.resolve(loading = true, hasContent = false, bootstrapped = true, hasOpenFolder = false)
        )
    }

    @Test
    fun contentWinsOverLoading() {
        // 有内容就渲染内容；刷新只在顶部显示细小的下拉指示器，不遮挡界面。
        assertEquals(
            HomeContentState.CONTENT,
            HomeLoadingGate.resolve(loading = true, hasContent = true, bootstrapped = true, hasOpenFolder = false)
        )
    }

    @Test
    fun contentIsShownEvenBeforeBootstrapWhenAvailable() {
        assertEquals(
            HomeContentState.CONTENT,
            HomeLoadingGate.resolve(loading = false, hasContent = true, bootstrapped = false, hasOpenFolder = false)
        )
    }

    @Test
    fun idleAndEmptyShowsEmptyState() {
        assertEquals(
            HomeContentState.EMPTY,
            HomeLoadingGate.resolve(loading = false, hasContent = false, bootstrapped = true, hasOpenFolder = false)
        )
    }

    @Test
    fun openFolderNeverShowsLoadingGate() {
        assertEquals(
            HomeContentState.CONTENT,
            HomeLoadingGate.resolve(loading = true, hasContent = false, bootstrapped = false, hasOpenFolder = true)
        )
    }
}
