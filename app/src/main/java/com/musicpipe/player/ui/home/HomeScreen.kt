package com.musicpipe.player.ui.home

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Person
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.musicpipe.player.data.image.FadeInCover
import com.musicpipe.player.di.LocalAppContainer
import com.musicpipe.player.ui.components.EmptyBlock
import com.musicpipe.player.ui.components.ErrorBanner
import com.musicpipe.player.ui.components.LoadingBlock
import com.musicpipe.player.ui.components.SyncingBlock
import com.musicpipe.player.ui.components.SongListItem
import com.musicpipe.player.ui.design.AwwEmpty
import com.musicpipe.player.ui.design.AwwLabel
import com.musicpipe.player.ui.design.AwwMono
import com.musicpipe.player.ui.design.AwwRule
import com.musicpipe.player.ui.design.ClipReveal
import com.musicpipe.player.ui.design.MaskReveal
import com.musicpipe.player.ui.design.PressScale
import com.musicpipe.player.ui.theme.AwwColors
import com.musicpipe.player.ui.theme.AwwMotion
import com.musicpipe.player.ui.theme.AwwShapes
import com.musicpipe.player.ui.theme.AwwSpace
import com.musicpipe.player.ui.theme.AwwTheme
import com.musicpipe.player.ui.theme.DsoColors
import com.musicpipe.player.ui.theme.LocalReduceMotion
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Home (v1.12.0) — 编辑杂志式布局。
 *
 * 这一版是按 DESIGN.md（Awwwards Style）重做的视觉层，**数据与交互逻辑与 v1.10.0 完全一致**：
 * 网格 / 文件夹详情 / 双轴切换 / 歌单管理 / 合并 / 删除的判定与调用都保持原样，
 * 只换了"怎么长"。逐条对应规范：
 *
 *  · 报头（[LibraryMasthead]）：小标签 + 流式大标题 + 等宽元信息 + 细线。规范 §4 要求
 *    大标题用 clamp 流式字号（这里走 AwwType.display，已按屏宽换算），左右不对称（右侧只放
 *    等宽计数，不居中）。
 *  · 轴切换（[AxisTabs]）：从"红色胶囊按钮"改成"大写小标签 + 编号 + 选中项下方 1dp 强调线"。
 *    强调色只出现在线和小标签上（面积远低于 §3 的 5% 上限），不再有整块红底。
 *  · 卡片（[FolderCard]）：16:10（§7 列表图比例）、2dp 圆角（§4 禁大圆角）、无阴影、
 *    无红底角标；类型与序号改成图片下方的等宽元信息行。封面入场走 [ClipReveal]（§5 动效②）。
 *  · 分隔只用 [AwwRule] 细线，不用卡片套卡片（§10 负面清单）。
 *  · 管理态：选中用 1dp 强调色描边 + 右上强调方块，不再用 2dp 粗边 + 圆形勾选。
 *
 * 性能约束（v1.6.0 的两处掉帧根因）在这里被保留且注释保留：`folders` 表格在 ViewModel 里
 * 算一次并缓存、卡片只吃不可变值对象、封面显式指定解码尺寸、所有 lambda 用 remember 固定。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onOpenSearch: () -> Unit,
    onOpenNetSearch: () -> Unit,
    onOpenTasks: () -> Unit,
    onOpenShare: () -> Unit,
    onOpenPlayer: () -> Unit
) {
    val container = LocalAppContainer.current
    val vm: HomeViewModel = viewModel(factory = HomeViewModel.factory(container))
    val state by vm.state.collectAsStateWithLifecycle()
    // Collect only the current song id (distinct): the 500ms position ticks must not
    // recompose the whole list while playing (v1.4.0 scroll-jank fix).
    val currentSongId by remember {
        container.playerRepository.state
            .map { it.currentSong?.id }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = null)
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmPlaylistDelete by remember { mutableStateOf<String?>(null) }
    var confirmBulkDeletePlaylists by remember { mutableStateOf(false) }
    var showMerge by remember { mutableStateOf(false) }

    // v1.15.0：回前台只做 silent 刷新。原实现直接 refresh()，会把 loading 置 true，
    // 首页顶部的下拉刷新圈因此在每次切回 App 时都转一圈 —— 用户看到的就是"一直在转"。
    LifecycleResumeEffect(Unit) {
        vm.refresh(showIndicator = false)
        onPauseOrDispose { }
    }

    // v1.6.0 稳定性：lambda 实参位置直接写 `{ id -> vm.openFolder(id) }` 时每次重组都新建实例，
    // strong skipping 下按 === 比较永不相等 → 任何 state 变化都会重组整个网格。remember 固定。
    val onOpenFolder: (String) -> Unit = remember(vm) { { id -> vm.openFolder(id) } }
    val onSelectAxis: (HomeAxis) -> Unit = remember(vm) { { axis -> vm.setAxis(axis) } }
    val onToggleManage: () -> Unit = remember(vm) { { vm.togglePlaylistManage() } }
    val onToggleSelectPlaylist: (String) -> Unit = remember(vm) { { id -> vm.togglePlaylistSelect(id) } }

    val openFolder = state.openFolder
    BackHandler(enabled = openFolder != null) { vm.closeFolder() }
    BackHandler(enabled = state.playlistManageMode && openFolder == null) {
        vm.togglePlaylistManage()
    }

    // UiState.shareFolders 自 v1.10.0 起是 lazy（同引用），直接读即可。
    val shareFolders = state.shareFolders

    if (confirmDelete) {
        AwwDialog(
            title = "删除歌曲",
            body = "删除所选 " + state.selected.size +
                " 首歌曲？将同时删除路由器网盘上的音频文件、歌词与封面，操作不可恢复。",
            confirmText = "删除",
            onConfirm = {
                confirmDelete = false
                vm.deleteSelected { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }
            },
            onDismiss = { confirmDelete = false }
        )
    }

    val delOne = confirmPlaylistDelete
    if (delOne != null) {
        AwwDialog(
            title = "删除歌单",
            body = "只删除这个歌单入口，曲库里已下载的歌曲不会被删除。" +
                "如需连歌曲一起删，请进入歌单后使用「管理」。",
            confirmText = "删除",
            onConfirm = {
                confirmPlaylistDelete = null
                vm.deletePlaylist(delOne) { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }
            },
            onDismiss = { confirmPlaylistDelete = null }
        )
    }
    if (confirmBulkDeletePlaylists) {
        AwwDialog(
            title = "删除歌单",
            body = "删除选中的 " + state.selectedPlaylists.size + " 个歌单？曲库里已下载的歌曲不会被删除。",
            confirmText = "删除",
            onConfirm = {
                confirmBulkDeletePlaylists = false
                vm.deleteSelectedPlaylists { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }
            },
            onDismiss = { confirmBulkDeletePlaylists = false }
        )
    }
    if (showMerge) {
        MergeDialog(
            folders = shareFolders,
            preselected = state.selectedPlaylists,
            busy = state.playlistBusy,
            onDismiss = { showMerge = false },
            onConfirm = { target, sources ->
                showMerge = false
                vm.mergePlaylists(target, sources) { msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    val hasContent = state.songs.isNotEmpty() || state.collections.isNotEmpty()

    val screenWidth = LocalConfiguration.current.screenWidthDp
    val screenHeight = LocalConfiguration.current.screenHeightDp
    val wideCar = com.musicpipe.player.ui.theme.ResponsiveLayout.isWideCar(screenWidth, screenHeight)
    Column(Modifier.fillMaxSize()) {
        if (openFolder != null) {
            FolderTopBar(
                title = openFolder.title,
                songCount = openFolder.songs.size,
                manageMode = state.manageMode,
                canManage = openFolder.songs.isNotEmpty(),
                onBack = { vm.closeFolder() },
                onToggleManage = { vm.toggleManage() }
            )
        } else {
            LibraryMasthead(
                songCount = state.songs.size,
                playlistCount = state.playlistFolders.size,
                albumCount = state.albumFolders.size,
                artistCount = state.artistFolders.size,
                manageMode = state.playlistManageMode,
                canManage = shareFolders.isNotEmpty() && state.axis == HomeAxis.PLAYLISTS,
                onOpenSearch = onOpenSearch,
                onOpenNetSearch = onOpenNetSearch,
                onOpenTasks = onOpenTasks,
                onOpenShare = onOpenShare,
                onToggleManage = onToggleManage,
                axis = state.axis,
                onSelectAxis = onSelectAxis
            )
        }

        if (openFolder == null && !wideCar) {
            AxisTabs(
                axis = state.axis,
                playlistCount = state.playlistFolders.size,
                albumCount = state.albumFolders.size,
                artistCount = state.artistFolders.size,
                onSelect = onSelectAxis
            )
        }

        PullToRefreshBox(
            isRefreshing = state.loading && hasContent,
            onRefresh = vm::refresh,
            modifier = Modifier.weight(1f)
        ) {
            // v1.15.0：判断全部收敛进 HomeLoadingGate（纯函数、有单测）。
            // 关键修复：本地快照读过之后就不再显示循环计数器，改用静止的同步态 ——
            // 旧实现在等网络期间一直转 000->099，就是用户说的"圈一直转"。
            when (HomeLoadingGate.resolve(
                loading = state.loading,
                hasContent = hasContent,
                bootstrapped = state.bootstrapped,
                hasOpenFolder = openFolder != null
            )) {
                HomeContentState.BOOTSTRAPPING -> LoadingBlock(Modifier.fillMaxSize())
                HomeContentState.SYNCING_EMPTY -> SyncingBlock(modifier = Modifier.fillMaxSize())
                HomeContentState.EMPTY -> Column(Modifier.fillMaxSize()) {
                    if (state.error != null) {
                        ErrorBanner("加载失败：" + state.error + "，下拉重试")
                    }
                    AwwEmpty(
                        index = "000",
                        title = if (state.error == null) "曲库为空" else "未能加载曲库",
                        hint = if (state.error == null) {
                            "先去「网络搜索」或「导入」添加歌曲，或下拉重试。"
                        } else {
                            "检查网络与服务器地址后下拉重试。"
                        }
                    )
                }
                HomeContentState.CONTENT -> {
                    val folder = openFolder
                    if (folder != null) {
                        val folderId = folder.id
                        val onPlay = remember(vm, folderId) { { index: Int -> vm.playFolder(folderId, index) } }
                        val onPlayAll = remember(vm, folderId) { { vm.playFolder(folderId, 0) } }
                        FolderDetail(
                            folder = folder,
                            currentSongId = currentSongId,
                            manageMode = state.manageMode,
                            selected = state.selected,
                            error = state.error,
                            onPlay = onPlay,
                            onToggleSelect = vm::toggleSelect,
                            onPlayAll = onPlayAll
                        )
                    } else {
                        Crossfade(
                            targetState = state.axis,
                            animationSpec = tween(if (LocalReduceMotion.current) 0 else 240),
                            label = "libraryAxis"
                        ) { axis ->
                        FolderGrid(
                            folders = when (axis) {
                                HomeAxis.PLAYLISTS -> state.playlistFolders
                                HomeAxis.ALBUMS -> state.albumFolders
                                HomeAxis.ARTISTS -> state.artistFolders
                            },
                            emptyTitle = if (state.axis == HomeAxis.PLAYLISTS) "还没有歌单" else "还没有专辑",
                            emptyHint = if (state.axis == HomeAxis.PLAYLISTS) {
                                "去「导入」粘贴一个网易云 / QQ 音乐歌单链接。"
                            } else if (state.axis == HomeAxis.ALBUMS) {
                                "下载歌曲后会按专辑自动归类。"
                            } else {
                                "下载歌曲后会按艺人自动归类。"
                            },
                            error = state.error,
                            manageMode = state.playlistManageMode,
                            selected = state.selectedPlaylists,
                            onOpenFolder = onOpenFolder,
                            onToggleSelect = onToggleSelectPlaylist,
                            onLongPressStartManage = onToggleManage
                        )
                        }
                    }
                }
            }
        }

        if (openFolder != null && state.manageMode) {
            ManageBar(
                total = state.visibleSongs.size,
                selected = state.selected.size,
                deleting = state.deleting,
                onSelectAll = vm::selectAll,
                onDelete = { confirmDelete = true }
            )
        } else if (openFolder == null && state.playlistManageMode) {
            PlaylistManageBar(
                total = shareFolders.size,
                selected = state.selectedPlaylists,
                busy = state.playlistBusy,
                onDeleteOne = { confirmPlaylistDelete = it },
                onDeleteSelected = { confirmBulkDeletePlaylists = true },
                onMerge = { showMerge = true }
            )
        }
    }
}
/**
 * 库报头（§4 Hero 非对称）：左侧小标签 + 大标题 + 等宽元信息，右侧留白，底部细线。
 *
 * 不做「居中标题 + 副标题 + 两个按钮」的套路（负面清单明确禁止）；入口用大写小标签排成一行，
 * 视觉上像杂志的目录栏而不是工具栏。
 */
@Composable
private fun LibraryMasthead(
    songCount: Int,
    playlistCount: Int,
    albumCount: Int,
    artistCount: Int,
    manageMode: Boolean,
    canManage: Boolean,
    onOpenSearch: () -> Unit,
    onOpenNetSearch: () -> Unit,
    onOpenTasks: () -> Unit,
    onOpenShare: () -> Unit,
    onToggleManage: () -> Unit,
    axis: HomeAxis,
    onSelectAxis: (HomeAxis) -> Unit
) {
    val config = LocalConfiguration.current
    val wideCar = com.musicpipe.player.ui.theme.ResponsiveLayout.isWideCar(config.screenWidthDp, config.screenHeightDp)
    Column(Modifier.fillMaxWidth().padding(start = AwwSpace.s4, end = AwwSpace.s4, top = AwwSpace.s2)) {
        if (wideCar) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).clip(AwwShapes.Input).background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onOpenSearch).padding(horizontal = AwwSpace.s4),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Search, "搜索歌曲", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(AwwSpace.s3))
                    Text("搜索歌曲、歌手、专辑", style = AwwTheme.type.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(AwwSpace.s4))
                AxisTabs(axis, playlistCount, albumCount, artistCount, onSelect = onSelectAxis)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                HomeAction("曲库搜索", Icons.Filled.Search, onOpenSearch)
                HomeAction("网络搜歌", Icons.Filled.CloudDownload, onOpenNetSearch)
                HomeAction("下载任务", Icons.Filled.Download, onOpenTasks)
                HomeAction("歌单导入", Icons.Filled.LibraryMusic, onOpenShare)
                Spacer(Modifier.weight(1f))
                Text(songCount.toString() + " 首", style = AwwTheme.type.bodySmall, color = AwwColors.Ink2)
                if (canManage) IconButton(onClick = onToggleManage) { Icon(Icons.Filled.MoreHoriz, "管理歌单", tint = AwwColors.Ink2) }
            }
            return
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clip(AwwShapes.Input)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onOpenSearch)
                .padding(horizontal = AwwSpace.s4),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Search, "搜索歌曲", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(AwwSpace.s3))
            Text(
                "搜索歌曲、歌手、专辑",
                style = AwwTheme.type.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = AwwSpace.s3)
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(top = AwwSpace.s4),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            HomeAction("曲库搜索", Icons.Filled.Search, onOpenSearch)
            HomeAction("网络搜歌", Icons.Filled.CloudDownload, onOpenNetSearch)
            HomeAction("下载任务", Icons.Filled.Download, onOpenTasks)
            HomeAction("歌单导入", Icons.Filled.LibraryMusic, onOpenShare)
        }
        Row(Modifier.fillMaxWidth().padding(top = AwwSpace.s4), verticalAlignment = Alignment.CenterVertically) {
            Text("我的音乐", style = AwwTheme.type.h3, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.weight(1f))
            Text(songCount.toString() + " 首", style = AwwTheme.type.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (canManage) {
                IconButton(onClick = onToggleManage) {
                    Icon(Icons.Filled.MoreHoriz, if (manageMode) "完成管理" else "管理歌单", tint = if (manageMode) AwwColors.Accent else AwwColors.Ink2)
                }
            }
        }
    }
}

@Composable
private fun HomeAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        PressScale {
            Box(
                Modifier.size(48.dp).clip(AwwShapes.Image).background(AwwColors.AccentSoft).clickable(onClick = onClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, label, tint = AwwColors.Accent, modifier = Modifier.size(24.dp))
            }
        }
        Text(label, style = AwwTheme.type.small, color = AwwColors.Ink2, modifier = Modifier.padding(top = AwwSpace.s2))
    }
}

/** 报头里的一个文字入口：大写小标签 + 按下反馈（§5 动效⑤）。 */
@Composable
private fun MastheadAction(text: String, onClick: () -> Unit, accent: Boolean = false) {
    val color = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    PressScale {
        Box(
            Modifier
                .clickable(onClick = onClick)
                .padding(vertical = AwwSpace.s2)
        ) {
            AwwLabel(text, color = color)
        }
    }
}
/** 文件夹详情页顶栏：‹ + 标题 + 等宽计数 + 管理入口。 */
@Composable
private fun FolderTopBar(
    title: String,
    songCount: Int,
    manageMode: Boolean,
    canManage: Boolean,
    onBack: () -> Unit,
    onToggleManage: () -> Unit
) {
    val gutter = AwwSpace.gutter(LocalConfiguration.current.screenWidthDp)
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = AwwSpace.s1, end = gutter, top = AwwSpace.s2),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = AwwTheme.type.h3,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                AwwMono(pad3(songCount) + " 首", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (canManage) {
                MastheadAction(if (manageMode) "完成" else "管理", onToggleManage, accent = true)
            }
        }
        AwwRule()
    }
}

/**
 * 轴切换（歌单 / 专辑）。
 *
 * v1.12.0：从「红色胶囊按钮」改为「等宽编号 + 大写标签 + 选中项下方 1dp 强调线」。
 * 理由：§3 要求强调色面积 <= 5%，整块红底胶囊一次就吃掉大半预算；下划线既保留「当前在哪」
 * 的强提示，又把强调色面积压到一条 1dp 线。计数为 0 的轴仍然显示（用户要知道它存在）但置灰。
 */
@Composable
private fun AxisTabs(
    axis: HomeAxis,
    playlistCount: Int,
    albumCount: Int,
    artistCount: Int,
    onSelect: (HomeAxis) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = AwwSpace.s4),
        horizontalArrangement = Arrangement.spacedBy(AwwSpace.s6)
    ) {
        HomeAxis.entries.forEach { item ->
            val selected = item == axis
            Column(Modifier.clickable { onSelect(item) }.padding(vertical = AwwSpace.s2)) {
                Text(
                    item.label + " · " + when (item) {
                        HomeAxis.PLAYLISTS -> playlistCount
                        HomeAxis.ALBUMS -> albumCount
                        HomeAxis.ARTISTS -> artistCount
                    },
                    style = if (selected) AwwTheme.type.body else AwwTheme.type.bodySmall,
                    color = if (selected) AwwColors.Ink else AwwColors.Ink3
                )
                Spacer(Modifier.height(AwwSpace.s1))
                Box(
                    Modifier.width(24.dp).height(3.dp).clip(AwwShapes.Pill)
                        .background(if (selected) AwwColors.Accent else Color.Transparent)
                )
            }
        }
    }
}
/**
 * 文件夹网格。
 *
 * 用 LazyVerticalGrid 而不是「外面一个 LazyColumn + 内部若干 LazyRow」：后者会把每个文件夹的
 * 整行一次性组合（即使它在屏幕外），这正是 v1.4.0 首页卡顿的另一半原因。
 *
 * v1.12.0：卡片比例从 1:1 改 16:10（§7 列表图建议比例），间距走 8pt 网格；
 * 每个条目的 key / contentType 保持 v1.6.0 的声明（错误横幅、空态、卡片各走各的复用池）。
 */
@Composable
private fun FolderGrid(
    folders: List<HomeFolderGrid>,
    emptyTitle: String,
    emptyHint: String,
    error: String?,
    manageMode: Boolean,
    selected: Set<String>,
    onOpenFolder: (String) -> Unit,
    onToggleSelect: (String) -> Unit,
    onLongPressStartManage: () -> Unit
) {
    val gutter = AwwSpace.gutter(LocalConfiguration.current.screenWidthDp)
    // 序号来自网格顺序（等宽编号要稳定，不能依赖可见项位置）。只在 folders 变化时重算一次。
    val indexById = remember(folders) {
        val map = HashMap<String, Int>(folders.size)
        folders.forEachIndexed { i, f -> map[f.id] = i }
        map
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        contentPadding = PaddingValues(
            start = gutter,
            end = gutter,
            top = AwwSpace.s4,
            bottom = AwwSpace.s24
        ),
        horizontalArrangement = Arrangement.spacedBy(AwwSpace.s4),
        verticalArrangement = Arrangement.spacedBy(AwwSpace.s8),
        modifier = Modifier.fillMaxSize()
    ) {
        if (error != null) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "error_banner", contentType = "error") {
                ErrorBanner("加载失败：" + error + "，下拉重试")
            }
        }
        if (folders.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "empty", contentType = "empty") {
                AwwEmpty(index = "000", title = emptyTitle, hint = emptyHint)
            }
        }
        // v1.6.0 payload 类型：网格里同时存在三种量级差别很大的条目（错误横幅/空态/文件夹卡片）。
        // 不声明 contentType 时 LazyGrid 会把它们当作同一种可复用槽位，状态切换时触发昂贵的重组。
        items(folders, key = { it.id }, contentType = { "folder" }) { item ->
            FolderCard(
                item = item,
                index = indexById[item.id] ?: 0,
                manageMode = manageMode,
                // v1.7.0 修复：selected 集合装的是 shareId（sharelinks.id），而 item.id 是带前缀的
                // folderId（"share:mv6kz..."）。用 item.id 判断恒为 false —— 点击其实已写入集合，
                // 但卡片看不出变化，表现就是「管理态点不动」。判定收敛到 PlaylistManageRules 一处。
                selected = PlaylistManageRules.isSelected(item.folder, selected),
                onClick = {
                    val shareId = item.folder.shareId
                    if (manageMode) {
                        // 专辑/未分类是曲库投影，没有「歌单」可删，管理态下点击无效果
                        if (shareId != null) onToggleSelect(shareId)
                    } else {
                        onOpenFolder(item.id)
                    }
                },
                onLongClick = {
                    val shareId = item.folder.shareId
                    // 长按进入歌单管理，比先找「管理」按钮再点更接近常见习惯。
                    // v1.7.0：管理态下长按与点击同样切换选中（之前长按什么都不做，用户以为卡片坏了）。
                    if (shareId != null) {
                        if (manageMode) onToggleSelect(shareId) else onLongPressStartManage()
                    }
                }
            )
        }
    }
}

/**
 * 单个文件夹卡片（§6 项目列表的移动端等价）：16:10 图 + 编号 + 标题 + 等宽元信息。
 *
 * 性能约束（v1.6.0 起保留）：
 *  · 参数只有不可变值对象 [HomeFolderGrid] 与两个 lambda，组合期零计算、零字符串拼接；
 *  · 封面走 [FadeInCover] 并显式给解码尺寸 —— 裸 AsyncImage 会按原图分辨率解码上千像素封面，
 *    一屏 6 张拼图就是数十 MB 位图，快速滑动时反复分配/回收触发 GC（「一滑就卡一下」的根源）；
 *  · 形状/颜色全部走单例常量，不在组合期新建；
 *  · lambda 由调用方 remember（见 HomeScreen），保证 strong skipping 下卡片能真正跳过。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderCard(
    item: HomeFolderGrid,
    index: Int,
    manageMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val folder = item.folder
    val canManage = folder.shareId != null
    val interaction = remember { MutableInteractionSource() }
    val accent = MaterialTheme.colorScheme.primary
    val showSelection = manageMode && canManage
    Column(
        Modifier.combinedClickable(
            interactionSource = interaction,
            indication = null,
            onClick = onClick,
            onLongClick = onLongClick
        )
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                // §7 列表图统一 16:10；固定比例让滚动的测量阶段不必反复测高
                .aspectRatio(1f)
                .clip(if (folder.kind == HomeFolder.Kind.ARTIST) CircleShape else AwwShapes.Image)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                // v1.13.0：卡片描边宽度跟风格 —— Brutalist 是 2dp 黑框，其余风格不给卡片描边；
                // 管理态选中无论如何都画强调色框（否则 Brutalist 下选不选看起来一样）。
                .then(
                    when {
                        showSelection && selected -> Modifier.border(2.dp, accent, AwwShapes.Image)
                        AwwShapes.borderWidth > 0.dp ->
                            Modifier.border(AwwShapes.borderWidth, MaterialTheme.colorScheme.outline, AwwShapes.Image)
                        else -> Modifier
                    }
                )
        ) {
            if (item.covers.isEmpty()) {
                // §10 禁灰空块：无封面时用等宽编号占位，而不是一个纯灰方块
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(if (folder.kind == HomeFolder.Kind.ARTIST) Icons.Filled.Person else Icons.Filled.LibraryMusic, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
                }
            } else {
                // §5 动效②：封面 clip 揭示 + 轻微缩放入场。
                //
                // v1.14.0 性能修复（网格滚动卡顿）：入场动画只在这个卡片第一次出现时播一次。
                // 之前是无条件 ClipReveal —— Lazy 网格回收条目后卡片重新组合，动画又从 0 开始，
                // 于是滚动中不断有新卡片播 1200ms 动画（一屏最多 6 个 Animatable 并行逐帧写
                // graphicsLayer），表现为滑动一顿一顿。
                //
                // revealOnce 用进程级 Set 记住已入场过的 folder id：首次进入主页有 stagger
                // 揭示的观感，滑走再滑回来则直接是终态，一次动画都不播。
                val shouldReveal = remember(item.id) { revealOnce(item.id) }
                ClipReveal(
                    delayMillis = if (shouldReveal) (index % 6) * AwwMotion.Stagger else 0,
                    animate = shouldReveal
                ) {
                    CoverCollage(item.covers)
                }
            }
            if (showSelection) {
                // 管理态：右上角小方块（选中 = 强调色实心，未选 = 半透明底），不用圆形勾选
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(AwwSpace.s2)
                        .size(16.dp)
                        // 未选中用当前风格的 ink 半透明，而不是写死 Color.Black ——
                        // 浅色风格（shadcn light / brutal light）下写死黑色会和深色封面糊在一起。
                        .background(
                            if (selected) accent else AwwColors.Ink.copy(alpha = 0.35f),
                            AwwShapes.Square
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (selected) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            // 勾在强调色实心块上，必须用 onAccent（Material 深色/ shadcn 的强调色是浅色）
                            tint = AwwColors.OnAccent,
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(AwwSpace.s2))
        Row(verticalAlignment = Alignment.CenterVertically) {

            Text(
                text = folder.title,
                style = AwwTheme.type.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
        // 元信息行：类型 · 数量（等宽 + tabular-nums，§3）
        Text(
            item.subtitle,
            style = AwwTheme.type.small,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(AwwSpace.s2))
    }
}
/**
 * 2x2 封面拼图；只有 1 张时铺满，2 张时左右分栏，3-4 张时 2x2。
 *
 * dpSize 只是**解码采样尺寸**（半屏宽卡片 ≈200dp，四分格 ≈100dp），实际布局由父容器的
 * aspectRatio + weight/fillMaxHeight 决定 —— FadeInCover 不再强行 .size()，所以四宫格会正确铺满。
 */
@Composable
private fun CoverCollage(covers: List<String>) {
    if (covers.size == 1) {
        FadeInCover(
            model = covers[0], contentDescription = null,
            dpSize = 200, modifier = Modifier.fillMaxSize()
        )
        return
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.weight(1f)) {
            CollageCell(covers[0])
            CollageCell(covers.getOrNull(1) ?: covers[0])
        }
        // §10 禁「边框套边框」，拼图格之间不加间隙，靠图片本身分界
        Row(Modifier.weight(1f)) {
            CollageCell(covers.getOrNull(2) ?: covers[0])
            CollageCell(covers.getOrNull(3) ?: covers.getOrNull(2) ?: covers[0])
        }
    }
}

/** 拼图单元格。dpSize=100 对应卡片 200dp 宽时的四分之一，让 Coil 只解码到显示所需像素。 */
@Composable
private fun androidx.compose.foundation.layout.RowScope.CollageCell(url: String) {
    FadeInCover(
        model = url, contentDescription = null,
        dpSize = 100, modifier = Modifier.weight(1f).fillMaxHeight()
    )
}

/** 文件夹详情：曲目列表（这里才逐行渲染，列表项数量等于该文件夹的歌曲数）。 */
@Composable
private fun FolderDetail(
    folder: HomeFolder,
    currentSongId: String?,
    manageMode: Boolean,
    selected: Set<String>,
    error: String?,
    onPlay: (Int) -> Unit,
    onToggleSelect: (String) -> Unit,
    onPlayAll: () -> Unit
) {
    val gutter = AwwSpace.gutter(LocalConfiguration.current.screenWidthDp)
    if (folder.songs.isEmpty()) {
        Column(Modifier.fillMaxSize()) {
            AwwEmpty(
                index = "000",
                title = if (folder.missing > 0) folder.missing.toString() + " 首未入库" else "暂无曲目",
                hint = if (folder.missing > 0) {
                    "在「导入」提交下载任务后，歌曲会自动出现在这里。"
                } else {
                    "这个文件夹里还没有可播放的曲目。"
                }
            )
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        if (error != null) {
            item(key = "error_banner", contentType = "error") {
                ErrorBanner("加载失败：" + error + "，下拉重试")
            }
        }
        // v1.6.0 payload 类型：header/song/spacer 三种条目差异极大，特别是 SongListItem 带
        // 封面位图与可选 Checkbox，若与 header 共用复用槽，管理态切换时会发生整行重建。
        item(key = "play_all", contentType = "header") {
            Column(Modifier.fillMaxWidth().padding(start = gutter, end = gutter, top = AwwSpace.s4)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AwwMono(
                        buildFolderSubtitleText(folder),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    PressScale {
                        Row(
                            Modifier
                                .clickable(onClick = onPlayAll)
                                .padding(start = AwwSpace.s3, top = AwwSpace.s1, bottom = AwwSpace.s1),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.PlayArrow, contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(AwwSpace.s1))
                            AwwLabel("播放全部", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                Spacer(Modifier.height(AwwSpace.s2))
                AwwRule()
            }
        }
        itemsIndexed(
            folder.songs,
            key = { _, s -> "f_" + folder.id + "_" + s.id },
            contentType = { _, _ -> "song" }
        ) { index, song ->
            SongListItem(
                song = song,
                isCurrentPlaying = currentSongId == song.id,
                onClick = { if (manageMode) onToggleSelect(song.id) else onPlay(index) },
                trailing = if (manageMode) {
                    {
                        Checkbox(
                            checked = song.id in selected,
                            onCheckedChange = { onToggleSelect(song.id) },
                            colors = CheckboxDefaults.colors(
                                checkedColor = MaterialTheme.colorScheme.primary,
                                uncheckedColor = MaterialTheme.colorScheme.outline
                            )
                        )
                    }
                } else null
            )
        }
        item(key = "bottom_pad", contentType = "spacer") { Spacer(Modifier.height(AwwSpace.s24)) }
    }
}
/** 管理模式下的全选 / 删除条（细线分隔，强调色只用在小标签与数字上）。 */
@Composable
private fun ManageBar(
    total: Int,
    selected: Int,
    deleting: Boolean,
    onSelectAll: () -> Unit,
    onDelete: () -> Unit
) {
    val gutter = AwwSpace.gutter(LocalConfiguration.current.screenWidthDp)
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
        AwwRule()
        Row(
            Modifier.fillMaxWidth().padding(horizontal = gutter, vertical = AwwSpace.s3),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = selected > 0 && selected == total,
                onCheckedChange = { onSelectAll() },
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    uncheckedColor = MaterialTheme.colorScheme.outline
                )
            )
            Spacer(Modifier.width(AwwSpace.s1))
            Box(Modifier.clickable(onClick = onSelectAll)) { AwwLabel("全选") }
            Spacer(Modifier.weight(1f))
            AwwMono(pad2(selected) + " / " + pad2(total), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(AwwSpace.s4))
            MastheadAction(
                if (deleting) "删除中" else "删除",
                onDelete,
                accent = selected > 0 && !deleting
            )
        }
    }
}

/**
 * 歌单管理条：删除（选中/全选后批量）与合并。
 *
 * 放在底栏而不是长按菜单里，是因为「合并歌单」必须先选中 >=2 个再指定目标 —— 批量选择
 * 天然适合底栏；长按只是快捷进入管理态的入口。
 */
@Composable
private fun PlaylistManageBar(
    total: Int,
    selected: Set<String>,
    busy: Boolean,
    onDeleteOne: (String) -> Unit,
    onDeleteSelected: () -> Unit,
    onMerge: () -> Unit
) {
    val gutter = AwwSpace.gutter(LocalConfiguration.current.screenWidthDp)
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
        AwwRule()
        Row(
            Modifier.fillMaxWidth().padding(horizontal = gutter, vertical = AwwSpace.s3),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AwwMono(
                if (selected.isEmpty()) "点击卡片选择"
                else pad2(selected.size) + " / " + pad2(total),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            if (selected.size == 1) {
                MastheadAction("删除", { onDeleteOne(selected.first()) }, accent = !busy)
                Spacer(Modifier.width(AwwSpace.s4))
            }
            MastheadAction(
                if (busy) "处理中" else "删除(" + selected.size + ")",
                onDeleteSelected,
                accent = selected.isNotEmpty() && !busy
            )
            Spacer(Modifier.width(AwwSpace.s4))
            MastheadAction("合并", onMerge, accent = selected.size >= 2 && !busy)
        }
    }
}

/**
 * 合并对话框：选目标歌单 + 显示将要并入的来源。
 *
 * 交互取舍：目标默认取「选中项里排在最前的一个」，来源是其余选中项 —— 用户只要勾选 2 个以上
 * 再点合并，默认行为就是「把后面几个并进第一个」；目标也可以改成任意歌单（例如用空歌单收编旧歌单）。
 */
@Composable
private fun MergeDialog(
    folders: List<HomeFolder>,
    preselected: Set<String>,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (target: String, sources: List<String>) -> Unit
) {
    val initial = folders.firstOrNull { it.shareId != null && it.shareId in preselected }?.shareId
        ?: folders.firstOrNull { it.shareId != null }?.shareId
    var target by remember { mutableStateOf(initial) }
    val sources = folders.mapNotNull { it.shareId }.filter { it != target }
    val chosen = sources.filter { it in preselected }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        title = { Text("合并歌单", style = AwwTheme.type.h3) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                AwwMono(
                    "合并后来源歌单会被删除，曲库里的歌曲不受影响。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(AwwSpace.s4))
                AwwLabel("目标歌单", color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(AwwSpace.s2))
                folders.filter { it.shareId != null }.forEach { f ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { target = f.shareId }
                            .padding(vertical = AwwSpace.s1),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = f.shareId == target,
                            onClick = { target = f.shareId },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = MaterialTheme.colorScheme.primary,
                                unselectedColor = MaterialTheme.colorScheme.outline
                            )
                        )
                        Spacer(Modifier.width(AwwSpace.s2))
                        Text(
                            f.title,
                            style = AwwTheme.type.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        AwwMono(
                            pad2(f.songs.size),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(AwwSpace.s4))
                AwwRule()
                Spacer(Modifier.height(AwwSpace.s2))
                AwwMono(
                    if (chosen.isEmpty()) "未选择要并入的来源（返回后勾选 >=2 个歌单）"
                    else "并入 " + chosen.size + " 个：" + folders.filter { it.shareId in chosen }
                        .joinToString("、") { it.title },
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { target?.let { onConfirm(it, chosen) } },
                enabled = target != null && chosen.isNotEmpty() && !busy
            ) {
                AwwLabel("合并", color = if (target != null && chosen.isNotEmpty() && !busy) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                })
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { AwwLabel("取消") }
        }
    )
}

/**
 * 通用确认对话框（§6）。
 *
 * 不用系统默认的彩色按钮组：标题走 h3、正文走等宽小字、确认按钮走强调色小标签，
 * 与整站排版语言一致；容器底色用 background 而不是默认的 surface 色块。
 */
@Composable
private fun AwwDialog(
    title: String,
    body: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        title = { Text(title, style = AwwTheme.type.h3) },
        text = {
            Column {
                Text(body, style = AwwTheme.type.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(AwwSpace.s4))
                AwwRule()
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                AwwLabel(confirmText, color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { AwwLabel("取消") }
        }
    )
}

/** 三位补零（§3 数字一律 mono + tabular-nums，靠补零对齐）。 */
private fun pad3(n: Int): String = n.toString().padStart(3, '0')

/** 两位补零。 */
private fun pad2(n: Int): String = n.toString().padStart(2, '0')

/**
 * 进程级记录「哪些卡片已经播过入场动画」。
 *
 * v1.14.0：Lazy 网格回收条目后卡片会重新组合，入场动画若跟着 remember 走就会反复重播，
 * 滚动时不断有新卡片播 1200ms 动画 —— 这是滑动顿挫的直接来源之一。
 * 用一个小的同步 Set 记住已入场过的 folder id，滑走再滑回来直接给终态。
 *
 * 数量上限：曲库/歌单是有限集合（当前 100 量级），一个 id 只存一次，内存可忽略。
 */
private val revealedFolders = java.util.Collections.synchronizedSet(HashSet<String>())

/** 首次出现返回 true 并记账，之后恒 false。 */
private fun revealOnce(id: String): Boolean = revealedFolders.add(id)

private fun folderKindLabel(kind: HomeFolder.Kind) = when (kind) {
    HomeFolder.Kind.SHARE -> "歌单"
    HomeFolder.Kind.ALBUM -> "专辑"
    HomeFolder.Kind.ARTIST -> "艺人"
    HomeFolder.Kind.UNCATEGORIZED -> "未分类"
}