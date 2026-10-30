package com.musicpipe.player.ui

import androidx.compose.foundation.background
import androidx.compose.animation.animateColorAsState
import com.musicpipe.player.ui.theme.AwwMotion
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.musicpipe.player.ui.theme.LocalReduceMotion
import androidx.compose.ui.unit.dp
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
import com.musicpipe.player.ui.car.CarLyricsScreen
import com.musicpipe.player.ui.eq.EqScreen
import com.musicpipe.player.ui.auth.LoginScreen
import com.musicpipe.player.ui.components.LoadingBlock
import com.musicpipe.player.ui.components.MiniPlayer
import com.musicpipe.player.ui.design.AwwLabel
import com.musicpipe.player.ui.design.AwwMono
import com.musicpipe.player.ui.design.AwwRule
import com.musicpipe.player.ui.design.GrainOverlay
import com.musicpipe.player.ui.design.PressScale
import com.musicpipe.player.ui.favorite.FavoriteScreen
import com.musicpipe.player.ui.history.HistoryScreen
import com.musicpipe.player.ui.home.HomeScreen
import com.musicpipe.player.ui.lyrics.LyricsScreen
import com.musicpipe.player.ui.nav.NavGraphSpec
import com.musicpipe.player.ui.nav.Routes
import com.musicpipe.player.ui.netsearch.NetSearchScreen
import com.musicpipe.player.ui.player.PlayerScreen
import com.musicpipe.player.ui.search.SearchScreen
import com.musicpipe.player.ui.settings.SettingsScreen
import com.musicpipe.player.ui.share.ShareImportScreen
import com.musicpipe.player.ui.tasks.TasksScreen
import com.musicpipe.player.ui.theme.AwwColors
import com.musicpipe.player.ui.theme.AwwShapes
import com.musicpipe.player.ui.theme.AwwSpace
import com.musicpipe.player.ui.theme.AwwTheme
import com.musicpipe.player.ui.theme.TabIndicator
import kotlin.math.roundToInt

/**
 * App 根：会话门（登录 / 加载 / 主框架）+ 按角色注册的 NavHost。
 *
 * 需求1 的角色隔离仍然靠 `key(user.role)` 重建整个导航栈：ADMIN_USERS 只给 admin 注册，
 * 普通用户该路由**根本不存在**（深链或代码跳转都落空），菜单项也一并消失。
 *
 * v1.12.0 按 DESIGN.md 重做外壳：
 * - 底栏从 M3 NavigationBar（自带 indicator 药丸 + 图标 + 阴影）换成"编号 + 大写标签 + 选中项
 *   1dp 强调下划线"的编辑式页脚 —— 规范负面清单禁止药丸指示器与大圆角。
 * - 整棵树最外层叠 [GrainOverlay]，模拟胶片颗粒（pointer-events 穿透，不影响交互）。
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

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            when {
                !restoreReady -> LoadingBlock(Modifier.fillMaxSize())
                currentUser == null -> LoginScreen(onLoggedIn = { /* state flow drives recomposition */ })
                else -> MainScaffold(currentUser!!)
            }
            // v1.13.0：噪点只是 Editorial/Brutalist 的材质语言。
            // Material / Cupertino / shadcn 都要求"干净的平面"，叠噪点会显得脏。
            if (AwwTheme.chrome.grain) {
                GrainOverlay(Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun MainScaffold(user: AppUser) {
    // Scaffold + NavController 按角色 key：换角色会重建整个导航栈，
    // 被移除的管理路由不可能残留在返回栈里。
    key(user.role) { MainScaffoldContent(user) }
}

@Composable
private fun MainScaffoldContent(user: AppUser) {
    val container = LocalAppContainer.current
    val navController = rememberNavController()
    // Scaffold 层只需要"当前歌曲的身份"，500ms 的位置变化由 MiniPlayerHost 内部收集，
    // 否则播放中每 500ms 会重整棵界面树（v1.4.0 掉帧修复）。
    val currentSong by remember {
        container.playerRepository.state
            .map { it.currentSong }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = null)
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val wideCar = com.musicpipe.player.ui.theme.ResponsiveLayout.isWideCar(configuration.screenWidthDp, configuration.screenHeightDp)
    val isFullPage = currentRoute == Routes.PLAYER || currentRoute == Routes.LYRICS || currentRoute == Routes.EQ || currentRoute == Routes.CAR_LYRICS
    val showMini = currentSong != null && !isFullPage
    val tabRoutes = NavGraphSpec.mainTabs.map { it.route }
    val showTabs = currentRoute in tabRoutes

    Row(Modifier.fillMaxSize()) {
        if (wideCar && !isFullPage) {
            CarNavigationRail(
                currentRoute = currentRoute,
                routes = tabRoutes,
                onSelect = { route ->
                    if (currentRoute != route) navController.navigate(route) {
                        popUpTo(Routes.HOME) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
        Box(Modifier.weight(1f)) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (wideCar) {
                if (showMini) MiniPlayerHost(onOpenPlayer = { navController.navigate(Routes.PLAYER) { launchSingleTop = true } })
                return@Scaffold
            }
            // v1.10.0（V8）：进入 SEARCH / NET_SEARCH / TASKS / SHARE_IMPORT / ADMIN_USERS 时
            // 既没有 mini player 也没有 tab 栏，bottomBar 整个为空 —— 而 Scaffold 只有在
            // bottomBar 存在时才把系统导航条的 inset 让给内容，于是这些页面内容会整体下移
            // 到导航栏底下、和其它页面的留白不一致（切页时肉眼可见一跳）。
            // 这里在"有 tab 但没有 mini player"或"两者都没有"时补一个等高 Spacer，
            // 保证 bottomBar 始终占位、底部 inset 恒定。
            if (!isFullPage && !(showMini || showTabs)) {
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
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
                        // 网易云式图标 + 文字底栏
                        AwwBottomBar(
                            currentRoute = currentRoute,
                            routes = tabRoutes,
                            onSelect = { route ->
                                if (currentRoute != route) {
                                    navController.navigate(route) {
                                        popUpTo(Routes.HOME) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { padding ->
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
            // v1.16.0：音效（均衡器）与车载歌词设置页
            composable(Routes.EQ) { EqScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.CAR_LYRICS) { CarLyricsScreen(onBack = { navController.popBackStack() }) }
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
    }
}

/**
 * 编辑式页脚导航（DESIGN.md：编号 + 大写标签 + 1dp 强调线）。
 *
 * 刻意不用 M3 NavigationBar：
 * - 它自带 indicator 药丸（规范禁大圆角/药丸容器）；
 * - 它把图标做成主要信息，而规范的编辑式语言里文字标签才是主体，图标只是可选装饰。
 * 这里保留图标（小、1dp 描边感的默认矢量图），但位置退到标签上方且限高 18dp。
 */
@Composable
private fun CarNavigationRail(currentRoute: String?, routes: List<String>, onSelect: (String) -> Unit) {
    Column(Modifier.width(88.dp).fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(vertical = AwwSpace.s2)) {
        routes.forEach { route ->
            val tab = NavGraphSpec.mainTabs.first { it.route == route }
            val selected = currentRoute == route
            val icon = when (route) {
                Routes.HOME -> Icons.Filled.Home
                Routes.FAVORITE -> Icons.Filled.Favorite
                Routes.HISTORY -> Icons.Filled.History
                else -> Icons.Filled.Person
            }
            Column(Modifier.fillMaxWidth().clip(AwwShapes.Image).clickable { onSelect(route) }.padding(vertical = AwwSpace.s3),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(icon, tab.label, tint = if (selected) AwwColors.Accent else AwwColors.Ink3, modifier = Modifier.size(28.dp))
                Text(tab.label, fontSize = 12.sp, color = if (selected) AwwColors.Accent else AwwColors.Ink3)
            }
        }
    }
}

@Composable
private fun AwwBottomBar(
    currentRoute: String?,
    routes: List<String>,
    onSelect: (String) -> Unit
) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        Spacer(Modifier.fillMaxWidth().height(1.dp).background(AwwColors.Line))
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = AwwSpace.s2),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            routes.forEach { route ->
                val index = NavGraphSpec.mainTabs.indexOfFirst { it.route == route }
                val tab = NavGraphSpec.mainTabs[index]
                val selected = currentRoute == route
                val icon = when (route) {
                    Routes.HOME -> Icons.Filled.Home
                    Routes.FAVORITE -> Icons.Filled.Favorite
                    Routes.HISTORY -> Icons.Filled.History
                    else -> Icons.Filled.Person
                }
                val tint by androidx.compose.animation.animateColorAsState(
                    targetValue = if (selected) AwwColors.Accent else AwwColors.Ink3,
                    animationSpec = tween(if (LocalReduceMotion.current) 0 else AwwMotion.DurFast),
                    label = "tabTint"
                )
                Column(
                    modifier = Modifier.weight(1f).clip(AwwShapes.Image).clickable { onSelect(route) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(icon, tab.label, tint = tint, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.height(AwwSpace.s1))
                    Text(tab.label, fontSize = 12.sp, color = tint)
                    Spacer(Modifier.height(AwwSpace.s2))
                }
            }
        }
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

/**
 * Mini player 单独收集完整播放状态（500ms 位置心跳），
 * 于是心跳只重组这条迷你栏，而不波及整个 Scaffold 树。
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
