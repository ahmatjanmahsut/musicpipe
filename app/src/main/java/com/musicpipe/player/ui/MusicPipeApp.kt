package com.musicpipe.player.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import com.musicpipe.player.data.model.AppUser
import com.musicpipe.player.di.LocalAppContainer
import com.musicpipe.player.ui.admin.AdminUsersScreen
import com.musicpipe.player.ui.auth.LoginScreen
import com.musicpipe.player.ui.components.LoadingBlock
import com.musicpipe.player.ui.components.MiniPlayer
import com.musicpipe.player.ui.favorite.FavoriteScreen
import com.musicpipe.player.ui.history.HistoryScreen
import com.musicpipe.player.ui.home.HomeScreen
import com.musicpipe.player.ui.lyrics.LyricsScreen
import com.musicpipe.player.ui.nav.NavGraphSpec
import com.musicpipe.player.ui.netsearch.NetSearchScreen
import com.musicpipe.player.ui.nav.Routes
import com.musicpipe.player.ui.player.PlayerScreen
import com.musicpipe.player.ui.search.SearchScreen
import com.musicpipe.player.ui.settings.SettingsScreen
import com.musicpipe.player.ui.share.ShareImportScreen
import com.musicpipe.player.ui.tasks.TasksScreen
import com.musicpipe.player.ui.theme.DsoColors

/**
 * App root: session gate (login / loading / main scaffold) + role-keyed NavHost.
 * Requirement 1: key(user.role) rebuilds the graph on role change; ADMIN_USERS
 * is registered ONLY for admin — for 普通用户 the route does not exist at all
 * (deep-link/programmatic navigation lands nowhere; menu entry omitted too).
 */
@Composable
fun MusicPipeApp() {
    val container = LocalAppContainer.current
    val currentUser by container.authRepository.currentUser.collectAsStateWithLifecycle()
    // v1.8.0 冷启动白屏修复：门看的是"本地是否已能决定首屏去向"。
    // 以前是 `LaunchedEffect { restoreSession(); restored = true }` —— 而 restoreSession 内部要等
    // /me 网络往返（读超时 30s），于是本地明明有身份也要陪着网络一起等，网络慢/无网就是长时间白屏。
    val restoreReady by container.authRepository.restoreReady.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { container.authRepository.restoreSession() }

    // Surface gives every page (including the pre-Scaffold login screen) the theme
    // background; without it dark-theme white text sat on the white window background.
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when {
            !restoreReady -> LoadingBlock(Modifier.fillMaxSize())
            currentUser == null -> LoginScreen(onLoggedIn = { /* state flow drives recomposition */ })
            else -> MainScaffold(currentUser!!)
        }
    }
}

@Composable
private fun MainScaffold(user: AppUser) {
    // Scaffold + NavController are role-keyed: a role change rebuilds the whole
    // navigation stack, so a removed admin route can never linger in the back stack.
    key(user.role) { MainScaffoldContent(user) }
}

@Composable
private fun MainScaffoldContent(user: AppUser) {
    val container = LocalAppContainer.current
    val navController = rememberNavController()
    // The scaffold only needs the identity of the current song. The 500ms position
    // ticks are collected inside MiniPlayerHost, so they recompose just the mini bar
    // instead of the whole screen tree (v1.4.0 scroll-jank fix).
    val currentSong by remember {
        container.playerRepository.state
            .map { it.currentSong }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = null)
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val isFullPage = currentRoute == Routes.PLAYER || currentRoute == Routes.LYRICS
    val showMini = currentSong != null && !isFullPage
    val tabRoutes = NavGraphSpec.mainTabs.map { it.route }
    val showTabs = currentRoute in tabRoutes

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (!isFullPage && (showMini || showTabs)) {
                Column {
                    if (showMini) {
                        MiniPlayerHost(
                            onOpenPlayer = {
                                navController.navigate(Routes.PLAYER) { launchSingleTop = true }
                            }
                        )
                    }
                    if (showTabs) {
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                            NavGraphSpec.mainTabs.forEach { tab ->
                                NavigationBarItem(
                                    selected = currentRoute == tab.route,
                                    onClick = {
                                        if (currentRoute != tab.route) {
                                            navController.navigate(tab.route) {
                                                popUpTo(Routes.HOME) { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    },
                                    icon = { Icon(tabIcon(tab.route), contentDescription = tab.label) },
                                    label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = DsoColors.AppRed,
                                        selectedTextColor = DsoColors.AppRed,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        indicatorColor = DsoColors.AppRed.copy(alpha = 0.10f)
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        // role-keyed at MainScaffold level (whole scaffold incl. NavController)
            NavHost(
                navController = navController,
                startDestination = Routes.HOME,
                modifier = Modifier.padding(padding)
            ) {
                composable(Routes.HOME) {
                    HomeScreen(
                        onOpenSearch = { navController.navigate(Routes.SEARCH) },
                        onOpenNetSearch = { navController.navigate(Routes.NET_SEARCH) },
                        onOpenTasks = { navController.navigate(Routes.TASKS) },
                        onOpenShare = { navController.navigate(Routes.SHARE_IMPORT) },
                        onOpenPlayer = {
                            navController.navigate(Routes.PLAYER) { launchSingleTop = true }
                        }
                    )
                }
                composable(Routes.SEARCH) {
                    SearchScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.NET_SEARCH) {
                    NetSearchScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.TASKS) {
                    TasksScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.SHARE_IMPORT) {
                    ShareImportScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.PLAYER) {
                    PlayerScreen(
                        onBack = { navController.popBackStack() },
                        onOpenLyrics = { navController.navigate(Routes.LYRICS) }
                    )
                }
                composable(Routes.LYRICS) {
                    LyricsScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.HISTORY) { HistoryScreen() }
                composable(Routes.FAVORITE) { FavoriteScreen() }
                composable(Routes.SETTINGS) {
                    SettingsScreen(onNavigate = { route ->
                        if (NavGraphSpec.canNavigate(user.role, route)) {
                            navController.navigate(route)
                        }
                    })
                }
                if (user.role == "admin") {
                    composable(Routes.ADMIN_USERS) {
                        AdminUsersScreen(onBack = { navController.popBackStack() })
                    }
                }
            }
    }
}

/**
 * Mini player collects the FULL player state (500ms position ticks) in isolation,
 * so the ticks recompose only this bar instead of the whole Scaffold tree.
 */
@Composable
private fun MiniPlayerHost(onOpenPlayer: () -> Unit) {
    val container = LocalAppContainer.current
    val playerState by container.playerRepository.state.collectAsStateWithLifecycle()
    val song = playerState.currentSong ?: return
    MiniPlayer(
        song = song,
        isPlaying = playerState.isPlaying,
        progress = if (playerState.durationMs > 0) {
            playerState.positionMs.toFloat() / playerState.durationMs.toFloat()
        } else 0f,
        onPlayPauseClick = { container.playerRepository.togglePlayPause() },
        onClick = onOpenPlayer
    )
}

private fun tabIcon(route: String): ImageVector = when (route) {
    Routes.HOME -> Icons.Filled.Home
    Routes.FAVORITE -> Icons.Filled.Favorite
    Routes.HISTORY -> Icons.Filled.History
    Routes.SETTINGS -> Icons.Filled.Settings
    else -> Icons.Filled.MusicNote
}
