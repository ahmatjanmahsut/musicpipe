package com.musicpipe.player.ui.nav

/**
 * Pure role→visibility spec (unit tested: RoleNavTest).
 * Requirement 1: for role "user" the admin entry is REMOVED from both the
 * nav graph (route not registered) and every menu (menuItemsFor omits it).
 */
object NavGraphSpec {

    /** Bottom-nav tabs (DsoMusic-style main entries). */
    data class Tab(val route: String, val label: String)

    val mainTabs = listOf(
        Tab(Routes.HOME, "主页"),
        Tab(Routes.FAVORITE, "我喜欢"),
        Tab(Routes.HISTORY, "历史"),
        Tab(Routes.SETTINGS, "设置")
    )

    fun routesFor(role: String?): Set<String> {
        val base = mutableSetOf(
            Routes.LOGIN, Routes.HOME, Routes.SEARCH, Routes.PLAYER,
            Routes.LYRICS, Routes.HISTORY, Routes.FAVORITE, Routes.SETTINGS,
            Routes.NET_SEARCH, Routes.TASKS, Routes.SHARE_IMPORT
        )
        if (role == "admin") base.add(Routes.ADMIN_USERS)
        return base
    }

    /** Extra menu entries on the settings page; admin-only entry included only for admin. */
    fun settingsEntries(role: String?): List<Tab> {
        val list = mutableListOf<Tab>()
        if (role == "admin") list.add(Tab(Routes.ADMIN_USERS, "用户管理"))
        return list
    }

    fun canNavigate(role: String?, route: String): Boolean = route in routesFor(role)
}
