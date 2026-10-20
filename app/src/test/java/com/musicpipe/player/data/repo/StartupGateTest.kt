package com.musicpipe.player.data.repo

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 锁住冷启动首屏放行规则。
 *
 * 背景：v1.8.0 之前 UI 等 restoreSession()（含 /me 网络往返）跑完才放行，
 * 网络慢时就是"长时间白屏"。这些用例保证规则不会被改回"等网络"。
 */
class StartupGateTest {

    @Test
    fun noTokenShowsLoginImmediatelyWithoutNetwork() {
        assertTrue(StartupGate.canShowUiWithoutNetwork(hasToken = false, hasCachedIdentity = false))
    }

    @Test
    fun tokenWithCachedIdentityShowsUiWithoutNetwork() {
        // 这是修复的核心：本地有身份就不该等网络。
        assertTrue(StartupGate.canShowUiWithoutNetwork(hasToken = true, hasCachedIdentity = true))
    }

    @Test
    fun tokenWithoutCachedIdentityMustWaitForNetwork() {
        // 唯一必须等网络的情况：知道有 token，但不知道角色，无法构建导航图。
        assertFalse(StartupGate.canShowUiWithoutNetwork(hasToken = true, hasCachedIdentity = false))
    }

    @Test
    fun noTokenIsNeverBlockedEvenWithStaleIdentity() {
        assertTrue(StartupGate.canShowUiWithoutNetwork(hasToken = false, hasCachedIdentity = true))
    }
}
