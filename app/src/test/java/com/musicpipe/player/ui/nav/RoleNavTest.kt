package com.musicpipe.player.ui.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Requirement 1 (role visibility): admin gets the ADMIN_USERS route + settings
 * entry; 普通用户 gets NEITHER (彻底隐藏 = removed, not disabled).
 */
class RoleNavTest {

    @Test
    fun `admin routes include ADMIN_USERS`() {
        assertTrue(Routes.ADMIN_USERS in NavGraphSpec.routesFor("admin"))
        assertTrue(NavGraphSpec.canNavigate("admin", Routes.ADMIN_USERS))
    }

    @Test
    fun `user routes exclude ADMIN_USERS entirely`() {
        assertFalse(Routes.ADMIN_USERS in NavGraphSpec.routesFor("user"))
        assertFalse(NavGraphSpec.canNavigate("user", Routes.ADMIN_USERS))
        assertFalse(Routes.ADMIN_USERS in NavGraphSpec.routesFor(null))
        assertFalse(NavGraphSpec.canNavigate(null, Routes.ADMIN_USERS))
    }

    @Test
    fun `settings entries only for admin`() {
        assertEquals(
            listOf(NavGraphSpec.Tab(Routes.ADMIN_USERS, "用户管理")),
            NavGraphSpec.settingsEntries("admin")
        )
        assertTrue(NavGraphSpec.settingsEntries("user").isEmpty())
        assertTrue(NavGraphSpec.settingsEntries(null).isEmpty())
    }

    @Test
    fun `both roles keep all common routes`() {
        for (role in listOf("admin", "user")) {
            val routes = NavGraphSpec.routesFor(role)
            for (r in listOf(
                Routes.HOME, Routes.SEARCH, Routes.PLAYER, Routes.LYRICS,
                Routes.HISTORY, Routes.FAVORITE, Routes.SETTINGS,
                Routes.NET_SEARCH, Routes.TASKS, Routes.SHARE_IMPORT
            )) assertTrue("$role missing $r", r in routes)
        }
    }

    @Test
    fun `main tabs are home favorite history settings`() {
        assertEquals(
            listOf(Routes.HOME, Routes.FAVORITE, Routes.HISTORY, Routes.SETTINGS),
            NavGraphSpec.mainTabs.map { it.route }
        )
    }
}
