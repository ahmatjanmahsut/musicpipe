package com.musicpipe.player.ui.nav

object Routes {
    const val LOGIN = "login"
    const val HOME = "home"
    const val SEARCH = "search"
    const val PLAYER = "player"
    const val LYRICS = "lyrics"
    const val HISTORY = "history"
    const val FAVORITE = "favorite"
    const val SETTINGS = "settings"
    /** v1.2.0 gateway features — available to every logged-in role. */
    const val NET_SEARCH = "net_search"
    const val TASKS = "tasks"
    const val SHARE_IMPORT = "share_import"
    /** Registered ONLY when role==admin — requirement 1 (彻底隐藏: 无菜单+无路由). */
    const val ADMIN_USERS = "admin_users"
}
