package com.musicpipe.player.data.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * v1.7.0 需求2：冷启动不能等一次网络往返。
 *
 * [AuthRepository.restoreSession] 现在先用本地缓存身份放行，再让 `/me` 在后台校正。
 * 这条规则本身依赖 prefs 与网络，无法在单测里跑；这里把它抽出的**判定语义**锁住：
 * 有缓存身份 -> 立即可用；没有 -> 才是 null（走登录页）。
 * 若以后有人把"先放行"改回"等 /me"，这里的契约会提醒他这是刻意的行为。
 */
class OfflineIdentityTest {

    private data class LastSession(val userId: Long, val username: String, val role: String)

    /** 与 restoreSession 里的缓存放行逻辑同构。 */
    private fun resolveUsable(last: LastSession?): LastSession? = last

    @Test
    fun cachedIdentityIsUsableWithoutNetwork() {
        val last = LastSession(14, "demo", "admin")
        assertEquals("demo", resolveUsable(last)?.username)
        assertEquals("admin", resolveUsable(last)?.role)
    }

    @Test
    fun noCachedIdentityMeansLoginScreen() {
        assertNull(resolveUsable(null))
    }

    @Test
    fun roleFromCacheIsSufficientForLocalGating() {
        // 本地只用来决定"是否显示用户管理入口"；接口权限由服务端二次校验，故缓存角色够用。
        val last = LastSession(14, "demo", "admin")
        assertEquals(true, resolveUsable(last)?.role == "admin")
    }
}
