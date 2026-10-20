package com.musicpipe.player.ui.home

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import com.musicpipe.player.ui.components.SongListItem
import com.musicpipe.player.ui.theme.DsoColors
import com.musicpipe.player.ui.theme.DsoShapes
import com.musicpipe.player.ui.theme.DsoTextStyles
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Home (v1.5.0) — 文件夹式布局。
 *
 * 与 v1.4.0 的分栏列表相比有三点变化：
 *
 *  1. **文件夹网格**（本文件 [FolderGrid]）：首页是一屏 2 列的文件夹卡片，每个卡片是一个
 *     分享歌单或一张专辑，带 2x2 封面拼图。点进去看曲目（[FolderDetail]）。
 *  2. **掉帧修复**：v1.4.0 把最多 500 条 [SongListItem] 塞进同一个 LazyColumn —— 滚动时
 *     每帧都要测量/排版大量含 AsyncImage 的行，正是"上下滑动动画掉帧"的根源。现在顶层只
 *     组合**可见的文件夹卡片**（通常 6-12 个），500 首歌只在进入某个文件夹后才逐行渲染，
 *     滚动负载从 O(500) 降到 O(可见卡片数)。
 *  3. **封面请求量下降**：卡片只取前 4 张封面做拼图（[HomeFolders.coverUrls]），而不是
 *     每行一张；叠加 Coil 与 API 共用连接池（见 MusicPipeApplication），首屏图片请求
 *     从「几十次独立握手」变成「同一连接上连续取几张」。
 *
 * v1.6.0（"滑动还是不顺"之后查到的真正两处原因，均已修）：
 *
 *  A. **组合期在做重活**。旧版 UiState.folders 是个 `get()` 属性，每次读取都重跑
 *     HomeFolders.build()（分组 68 首歌 + 排序 + 拼副标题 + 抽封面 URL + 分配新 List）。
 *     LazyGrid 每个滚动帧都会读它 → 每个滚动帧都重算一遍整个表格。现在表格在
 *     HomeViewModel.refresh() 里算一次并缓存在 state 上，滚动期零计算。
 *  B. **封面按原图解码**。旧版裸 AsyncImage 没有尺寸约束，Coil 以原图分辨率解码
 *     (服务端封面上千像素)，一屏 6 张拼图 ≈ 数十 MB 位图，快速滑动时反复分配/回收
 *     触发 GC —— 表现为"一滑就卡一下"。现在统一走 [FadeInCover]，显式 size +
 *     关闭 crossfade（crossfade 会给每张新出现的封面开一段动画，快速滑动时动画
 *     互相堆叠，本身也是掉帧来源）。
 *
 * 参考的同类实现思路（DsoMusic / Apple Music / Spotify 的通用做法）：
 * 列表项只吃不可变小对象、封面按显示尺寸降采样解码、滚动期不做任何数据加工。
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
    // v1.6.0 歌单管理：confirmPlaylistDelete = 待删除的 shareId；mergeTarget = 打开合并对话框时选中的目标
    var confirmPlaylistDelete by remember { mutableStateOf<String?>(null) }
    var confirmBulkDeletePlaylists by remember { mutableStateOf(false) }
    var showMerge by remember { mutableStateOf(false) }

    // Refresh whenever home becomes visible again (returning from 下载任务 / 分享导入 /
    // 网络搜索, or app foregrounded); the VM loading guard dedupes in-flight loads.
    LifecycleResumeEffect(Unit) {
        vm.refresh()
        onPauseOrDispose { }
    }

    // v1.6.0 稳定性：`{ id -> vm.openFolder(id) }` 写在 FolderGrid 的实参位置时，每次重组都会
    // 新建一个捕获 vm 的 lambda。strong skipping 下 lambda 参数按 === 比较，新旧永不相等，
    // 于是每次 state 变化（例如管理态切换、loading 置位）都会把整个网格连同全部可见卡片重组一遍。
    // 这里 remember(vm) 固定成同一个实例，网格才能正常跳过。
    val onOpenFolder: (String) -> Unit = remember(vm) { { id -> vm.openFolder(id) } }
    // 同上：切轴 lambda 也 remember，避免每次重组新建导致分段控件无法跳过。
    val onSelectAxis: (HomeAxis) -> Unit = remember(vm) { { axis -> vm.setAxis(axis) } }

    // 文件夹二级页面：系统返回键先回到网格，而不是退出 App
    val openFolder = state.openFolder
    BackHandler(enabled = openFolder != null) { vm.closeFolder() }

    // v1.6.0 稳定性细节：UiState.shareFolders 是 getter，每次读取都 map+filter 出一个**新的 List**。
    // 它在下面被读了三次（操作条 / 合并对话框 / 顶部栏可见性），等于每次重组都新建 3 个 List，
    // 并且任何以它为参数的子组合都无法跳过。这里按 folders 缓存一次，folders 是值类型且
    // 只在 refresh 成功后变化，所以缓存键的相等比较代价极低。
    // v1.10.0（P7）：不再需要 remember 包一层 —— UiState.shareFolders 现在是 lazy，
    // 同一份 state 永远返回同一个 List 引用，直接读即可（原来 remember 的 key 用的是
    // state.folders，而取值的字段来自 playlistFolders，键值不一致，切轴时不会重建）。
    val shareFolders = state.shareFolders

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除歌曲") },
            text = {
                Text("删除所选 " + state.selected.size + " 首歌曲？\n将同时删除路由器网盘上的音频文件、歌词与封面，操作不可恢复。")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.deleteSelected { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }
                }) { Text("删除", color = DsoColors.AppRed) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } }
        )
    }

    // 删除单个歌单（也用于批量确认后的单条路径）
    val delOne = confirmPlaylistDelete
    if (delOne != null) {
        AlertDialog(
            onDismissRequest = { confirmPlaylistDelete = null },
            title = { Text("删除歌单") },
            text = { Text("只删除这个歌单入口，曲库里已下载的歌曲不会被删除。\n如需连歌曲一起删，请进入歌单后使用「管理」。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmPlaylistDelete = null
                    vm.deletePlaylist(delOne) { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }
                }) { Text("删除", color = DsoColors.AppRed) }
            },
            dismissButton = { TextButton(onClick = { confirmPlaylistDelete = null }) { Text("取消") } }
        )
    }
    if (confirmBulkDeletePlaylists) {
        AlertDialog(
            onDismissRequest = { confirmBulkDeletePlaylists = false },
            title = { Text("删除歌单") },
            text = { Text("删除选中的 " + state.selectedPlaylists.size + " 个歌单？曲库里已下载的歌曲不会被删除。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmBulkDeletePlaylists = false
                    vm.deleteSelectedPlaylists { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }
                }) { Text("删除", color = DsoColors.AppRed) }
            },
            dismissButton = { TextButton(onClick = { confirmBulkDeletePlaylists = false }) { Text("取消") } }
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

    // 管理态下按系统返回键先退出管理，而不是直接退出应用/返回上一个 tab。
    BackHandler(enabled = state.playlistManageMode && openFolder == null) {
        vm.togglePlaylistManage()
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = openFolder?.title ?: "音乐库",
            folderMode = openFolder != null,
            songCount = openFolder?.songs?.size,
            canManage = if (openFolder != null) {
                openFolder.songs.isNotEmpty()
            } else {
                // 网格态：长按卡片也能进入，这里是显式入口（更易发现）。
                // v1.9.0：专辑轴没有可管理的实体（专辑是曲库投影），管理入口只在歌单轴出现。
                shareFolders.isNotEmpty() && state.axis == HomeAxis.PLAYLISTS
            },
            manageMode = if (openFolder != null) state.manageMode else state.playlistManageMode,
            onBack = { vm.closeFolder() },
            onOpenSearch = onOpenSearch,
            onOpenNetSearch = onOpenNetSearch,
            onOpenTasks = onOpenTasks,
            onOpenShare = onOpenShare,
            onToggleManage = {
                if (openFolder != null) vm.toggleManage() else vm.togglePlaylistManage()
            }
        )

        // v1.9.0：歌单 / 专辑两个轴分段切换。同一屏只展示一个轴，
        // 从根上消除"同一个歌单与它的专辑各占一张卡片"的重复感。
        if (openFolder == null && (state.playlistFolders.isNotEmpty() || state.albumFolders.isNotEmpty())) {
            AxisTabs(
                axis = state.axis,
                playlistCount = state.playlistFolders.size,
                albumCount = state.albumFolders.size,
                onSelect = onSelectAxis
            )
        }

        PullToRefreshBox(
            isRefreshing = state.loading && hasContent,
            onRefresh = vm::refresh,
            modifier = Modifier.weight(1f)
        ) {
            when {
                state.loading && !hasContent -> LoadingBlock(Modifier.fillMaxSize())
                !hasContent -> Column(Modifier.fillMaxSize()) {
                    if (state.error != null) {
                        ErrorBanner("加载失败：" + state.error + "，下拉重试")
                    }
                    EmptyBlock(
                        if (state.error == null) "曲库为空：先在「网络搜索」或「分享导入」添加歌曲，或下拉重试"
                        else "未能加载曲库，下拉重试"
                    )
                }
                openFolder != null -> {
                    // 同 onOpenFolder：详情页的两个 lambda 也 remember 住，否则列表每滚动一次
                    // 都要重组全部可见歌曲行
                    val folderId = openFolder.id
                    val onPlay = remember(vm, folderId) { { index: Int -> vm.playFolder(folderId, index) } }
                    val onPlayAll = remember(vm, folderId) { { vm.playFolder(folderId, 0) } }
                    FolderDetail(
                        folder = openFolder,
                        currentSongId = currentSongId,
                        manageMode = state.manageMode,
                        selected = state.selected,
                        error = state.error,
                        onPlay = onPlay,
                        onToggleSelect = vm::toggleSelect,
                        onPlayAll = onPlayAll
                    )
                }
                else -> FolderGrid(
                    folders = state.folders,
                    emptyText = if (state.axis == HomeAxis.PLAYLISTS) {
                        "还没有歌单：去「分享导入」粘贴一个网易云/QQ音乐歌单链接"
                    } else {
                        "曲库里还没有专辑信息：去「网络搜索」下载歌曲后会按专辑归类"
                    },
                    error = state.error,
                    manageMode = state.playlistManageMode,
                    selected = state.selectedPlaylists,
                    onOpenFolder = onOpenFolder,
                    onToggleSelect = vm::togglePlaylistSelect,
                    onLongPressStartManage = vm::togglePlaylistManage
                )
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

/** 顶栏：网格态显示「音乐库」+ 三个入口；文件夹态显示 ‹ + 文件夹名 + 歌曲数。 */
@Composable
private fun TopBar(
    title: String,
    folderMode: Boolean,
    songCount: Int?,
    canManage: Boolean,
    manageMode: Boolean,
    onBack: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenNetSearch: () -> Unit,
    onOpenTasks: () -> Unit,
    onOpenShare: () -> Unit,
    onToggleManage: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(start = if (folderMode) 0.dp else 16.dp, end = 4.dp, top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (folderMode) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                title, style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            if (folderMode && songCount != null) {
                Text(
                    songCount.toString() + " 首",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (!folderMode) {
            IconButton(onClick = onOpenNetSearch) {
                Icon(
                    Icons.Filled.Public, contentDescription = "网络搜索",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onOpenTasks) {
                Icon(
                    Icons.Filled.Download, contentDescription = "下载任务",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onOpenShare) {
                Icon(
                    Icons.Filled.Share, contentDescription = "分享导入",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            "搜索",
            color = DsoColors.AppRed,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .clickable(onClick = onOpenSearch)
                .padding(horizontal = 6.dp, vertical = 4.dp)
        )
        if (canManage) {
            Text(
                if (manageMode) "完成" else "管理",
                color = DsoColors.AppRed,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .clickable(onClick = onToggleManage)
                    .padding(start = 6.dp, end = 10.dp, top = 4.dp, bottom = 4.dp)
            )
        }
    }
}

/**
 * v1.9.0 轴切换分段控件：歌单 / 专辑。
 *
 * 用自绘胶囊而不是 Material3 SegmentedButton，原因有二：
 *  1) 本项目其余筛选控件（管理态胶囊、歌词按钮）都是同一种胶囊样式，保持视觉一致；
 *  2) SegmentedButton 在小屏上左对齐留白较大，两个中文标签的宽度差异会让整个控件跳动。
 *
 * 形状/颜色都 remember 住：分段控件在每次 state 变化时都会重组（它读 axis 与两个计数），
 * 组合期新建 shape/color 会让它永远无法跳过。
 */
@Composable
private fun AxisTabs(
    axis: HomeAxis,
    playlistCount: Int,
    albumCount: Int,
    onSelect: (HomeAxis) -> Unit
) {
    val pillShape = CircleShape
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HomeAxis.entries.forEach { item ->
            val selected = item == axis
            val count = if (item == HomeAxis.PLAYLISTS) playlistCount else albumCount
            // 0 个的轴仍然显示（用户要知道它存在），但置灰提示当前没有内容
            val enabled = count > 0 || selected
            Box(
                Modifier
                    .clip(pillShape)
                    .background(
                        when {
                            selected -> DsoColors.AppRed
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                    .clickable(enabled = enabled && !selected) { onSelect(item) }
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = item.label + " " + count,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = when {
                        selected -> Color.White
                        enabled -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                    }
                )
            }
        }
    }
}

/**
 * 文件夹网格总览。
 *
 * 用 [LazyVerticalGrid] 而不是"外面一个 LazyColumn + 内部若干 LazyRow"：后者会把每个
 * 文件夹的整行一次性组合（即使它在屏幕外），这正是 v1.4.0 首页卡顿的另一半原因。
 * 网格只为可见卡片分配组合，且卡片本身是不可见的惰性项。
 */
@Composable
private fun FolderGrid(
    folders: List<HomeFolderGrid>,
    emptyText: String,
    error: String?,
    manageMode: Boolean,
    selected: Set<String>,
    onOpenFolder: (String) -> Unit,
    onToggleSelect: (String) -> Unit,
    onLongPressStartManage: () -> Unit
) {
    // v1.6.0：滚动性能相关配置都集中在这里，理由见文件头注释。
    //
    //  · 每一格都是固定 key（folders 自带稳定 id），滚动时不会重建已经滑出屏幕的卡片状态；
    //  · 网格只有 2 列，一屏通常 4~6 张卡，本来就把测量量压到了 O(可见卡片数)，
    //    因此这里不需要额外的 prefetch/固定高度技巧 —— 真正的瓶颈在封面解码与组合期计算，
    //    两处都已修（见文件头 A/B 与 FadeInCover 的说明）。
    LazyVerticalGrid(
        // v1.10.0（V7）：原来是全项目唯一一处写死的 GridCells.Fixed(2)。
        // 手机竖屏下 Adaptive(150.dp) 仍恰好排 2 列（与原来一致），
        // 但横屏、折叠屏展开、平板会自动变成 3~4 列，不必再单独写一套布局。
        // 跨列条目改用 maxLineSpan（见下），否则新列数下横幅只会占 2 格、右边露白。
        columns = GridCells.Adaptive(minSize = 150.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        if (error != null) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "error_banner", contentType = "error") {
                ErrorBanner("加载失败：" + error + "，下拉重试")
            }
        }
        if (folders.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "empty", contentType = "empty") {
                Text(
                    emptyText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
        }
        // v1.6.0 payload 类型：网格里同时存在三种量级差别很大的条目（错误横幅/空态提示/文件夹卡片）。
        // 不声明 contentType 时，LazyGrid 会把它们当作同一种可复用槽位，一旦列表在
        // "有错误→无错误"或"空→非空"之间切换，就会让一个 Text 的组合槽被拿去组合 FolderCard，
        // 触发昂贵的重新组合；声明类型后每种条目各走各的复用池，滚动与状态切换都更稳。
        items(folders, key = { it.id }, contentType = { "folder" }) { item ->
            FolderCard(
                item = item,
                manageMode = manageMode,
                // v1.7.0 修复：selected 集合装的是 shareId（= sharelinks.id，如 "mv6kz..."），
                // 而 item.id 是网格内部带前缀的 folderId（"share:mv6kz..."）。用 item.id 判断
                // 永远为 false —— 点击其实已经写进集合，但卡片看不出任何变化，用户感受就是
                // "管理态下点不动"。判定收到 PlaylistManageRules.isSelected 一处，便于单测。
                selected = PlaylistManageRules.isSelected(item.folder, selected),
                onClick = {
                    val shareId = item.folder.shareId
                    if (manageMode) {
                        // 专辑/未分类是曲库的投影，没有"歌单"可删，管理态下点击无效果
                        if (shareId != null) onToggleSelect(shareId)
                    } else {
                        onOpenFolder(item.id)
                    }
                },
                onLongClick = {
                    val shareId = item.folder.shareId
                    // 长按进入歌单管理：比先找"管理"按钮再点更接近微信/网易云的删除歌单习惯。
                    // v1.7.0：已经在管理态时，长按与点击同样切换选中 —— 之前这里直接
                    // 什么都不做，用户长按没有反馈，会以为卡片"点不动"。
                    if (shareId != null) {
                        if (manageMode) onToggleSelect(shareId) else onLongPressStartManage()
                    }
                }
            )
        }
    }
}

/**
 * 单个文件夹卡片：2x2 封面拼图 + 名称 + 数量角标。
 *
 * v1.6.0 性能改动（滚动掉帧的第二半，第一半是 HomeViewModel 里缓存 folders 表格）：
 *  · 参数只有 [HomeFolderGrid]（值类型）+ 两个 lambda，封面与副标题都是预先算好的字符串，
 *    组合期不再调用 HomeFolders.coverUrls / 不再拼字符串 —— 滚动时零分配。
 *  · 封面走 [FadeInCover]，显式指定解码尺寸；旧实现用裸 AsyncImage 且无尺寸约束，
 *    Coil 会按原图分辨率解码上千像素的封面，一屏 6 张拼图就是几十 MB 位图，滚动时
 *    反复分配/回收触发 GC —— 这正是"一滑就卡一下"的来源。
 *  · 封面格子用 `aspectRatio(1f)` 固定高度，父级的测量阶段因此不需要在滚动中反复测量高度。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderCard(
    item: HomeFolderGrid,
    manageMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val folder = item.folder
    val canManage = folder.shareId != null
    val interaction = remember { MutableInteractionSource() }
    // v1.6.0：形状/颜色/图标在组合期是全新对象，每次 recomposition 都重建会让
    // 卡片永远无法跳过 —— 滚动时每张重生的卡片都在分配。用 remember 共享常量。
    // v1.10.0（V5）：形状改用单例常量（等价但不再依赖 remember）。
    val cardShape = DsoShapes.Surface
    val badgeShape = DsoShapes.SmallSurface
    val translucentBlack = remember { Color.Black.copy(alpha = 0.35f) }
    val badgeRed = remember { DsoColors.AppRed.copy(alpha = 0.85f) }
    Column(
        Modifier
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .then(
                if (manageMode && canManage) {
                    Modifier.border(
                        width = 2.dp,
                        color = if (selected) DsoColors.AppRed else Color.Transparent,
                        shape = cardShape
                    )
                } else Modifier
            )
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(cardShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (item.covers.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = folderIcon(folder.kind), contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(44.dp)
                    )
                }
            } else {
                CoverCollage(item.covers)
            }
            if (manageMode && canManage) {
                // 管理态：右上角勾选圆圈（长按/进入管理后即为可选项）
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) DsoColors.AppRed else translucentBlack
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (selected) {
                        Icon(
                            Icons.Filled.Check, contentDescription = null,
                            tint = Color.White, modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
            // 类型角标，让"分享歌单"和"专辑"一眼可分
            Row(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .clip(badgeShape)
                    .background(badgeRed)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = folderIcon(folder.kind), contentDescription = null,
                    tint = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.size(11.dp)
                )
                Spacer(Modifier.width(3.dp))
                Text(folderKindLabel(folder.kind), style = DsoTextStyles.Badge, color = androidx.compose.ui.graphics.Color.White)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            folder.title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        Text(
            item.subtitle, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * 2x2 封面拼图；只有 1 张时铺满，2 张时左右分栏，3-4 张时 2x2。
 *
 * v1.6.0：这里传的 dpSize 只是**解码采样尺寸**（半屏宽卡片 ≈200dp，四分格 ≈100dp），
 * 实际布局由父容器的 aspectRatio(1f) + weight(1f)/fillMaxHeight 决定 ——
 * FadeInCover 不再强行 .size()，所以拼图四宫格会正确铺满整卡而不是缩成方块。
 */
@Composable
private fun CoverCollage(covers: List<String>) {
    when (covers.size) {
        1 -> FadeInCover(
            model = covers[0], contentDescription = null,
            dpSize = 200, modifier = Modifier.fillMaxSize()
        )
        else -> Column(Modifier.fillMaxSize()) {
            Row(Modifier.weight(1f)) {
                CollageCell(covers[0])
                CollageCell(covers.getOrNull(1) ?: covers[0])
            }
            Row(Modifier.weight(1f)) {
                CollageCell(covers.getOrNull(2) ?: covers[0])
                CollageCell(covers.getOrNull(3) ?: covers.getOrNull(2) ?: covers[0])
            }
        }
    }
}

/**
 * 拼图单元格。dpSize=100 对应卡片 200dp 宽时的四分之一，
 * 让 Coil 只解码到"显示所需"的像素（约 100x100），而不是封面原图。
 */
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
    if (folder.songs.isEmpty()) {
        Column(Modifier.fillMaxSize()) {
            EmptyBlock(
                if (folder.missing > 0) folder.missing.toString() + " 首曲目尚未入库：在「分享导入」提交下载后自动出现"
                else "此文件夹暂无曲目"
            )
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        if (error != null) {
            item(key = "error_banner", contentType = "error") { ErrorBanner("加载失败：" + error + "，下拉重试") }
        }
        // v1.6.0 payload 类型：列表里有 header/song/spacer 三种差异极大的条目。
        // 特别是 SongListItem 带封面位图与可选 Checkbox，若与 header/footer 共用复用槽，
        // 在"管理态切换"（trailing 由 null 变 Checkbox）时会发生整行重建。
        item(key = "play_all", contentType = "header") {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    buildFolderSubtitleText(folder), Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = onPlayAll) {
                    Icon(
                        Icons.Filled.PlayArrow, contentDescription = null,
                        tint = DsoColors.AppRed, modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(2.dp))
                    Text("播放全部", color = DsoColors.AppRed, style = MaterialTheme.typography.bodySmall)
                }
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
                            colors = CheckboxDefaults.colors(checkedColor = DsoColors.AppRed)
                        )
                    }
                } else null
            )
        }
        item(key = "bottom_pad", contentType = "spacer") { Spacer(Modifier.height(90.dp)) }
    }
}

/** 管理模式下方的全选 / 删除条。 */
@Composable
private fun ManageBar(
    total: Int,
    selected: Int,
    deleting: Boolean,
    onSelectAll: () -> Unit,
    onDelete: () -> Unit
) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = selected > 0 && selected == total,
            onCheckedChange = { onSelectAll() },
            colors = CheckboxDefaults.colors(checkedColor = DsoColors.AppRed)
        )
        Text(
            "全选", color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.clickable(onClick = onSelectAll)
        )
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onDelete, enabled = selected > 0 && !deleting) {
            Text(
                if (deleting) "删除中…" else "删除(" + selected + ")",
                color = if (selected > 0 && !deleting) DsoColors.AppRed
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * v1.6.0 歌单管理条：删除（选中/全选后批量）与合并。
 *
 * 放在底栏而不是长按菜单里，是因为"合并歌单"必须先选中 ≥2 个再指定目标 —— 批量选择
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
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (selected.isEmpty()) "长按或点击卡片选择歌单"
            else "已选 " + selected.size + " / " + total,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(start = 8.dp)
        )
        Spacer(Modifier.weight(1f))
        if (selected.size == 1) {
            TextButton(onClick = { onDeleteOne(selected.first()) }, enabled = !busy) {
                Text("删除", color = DsoColors.AppRed, style = MaterialTheme.typography.bodySmall)
            }
        }
        TextButton(onClick = onDeleteSelected, enabled = selected.isNotEmpty() && !busy) {
            Text(
                if (busy) "处理中…" else "删除(" + selected.size + ")",
                color = if (selected.isNotEmpty() && !busy) DsoColors.AppRed
                else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
        TextButton(onClick = onMerge, enabled = selected.size >= 2 && !busy) {
            Text(
                "合并",
                color = if (selected.size >= 2 && !busy) DsoColors.AppRed
                else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/**
 * 合并对话框：选目标歌单 + 显示将要并入的来源。
 *
 * 交互设计上的取舍：目标默认取"选中项里排在最前的一个"，来源是其余选中项 ——
 * 这样用户只要勾选 2 个以上歌单再点合并，默认行为就是"把后面几个并进第一个"，
 * 但目标可以在下拉里改成任意未选中的歌单（例如新建的空歌单收编几个旧歌单）。
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
        title = { Text("合并歌单") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "把选中的歌单并入目标歌单，合并后来源歌单会被删除，曲库里的歌曲不受影响。",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text("目标歌单", style = MaterialTheme.typography.labelMedium, color = DsoColors.AppRed, fontWeight = FontWeight.Medium)
                folders.filter { it.shareId != null }.forEach { f ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { target = f.shareId }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = f.shareId == target,
                            onClick = { target = f.shareId },
                            colors = RadioButtonDefaults.colors(selectedColor = DsoColors.AppRed)
                        )
                        Text(
                            f.title + "（" + f.songs.size + " 首）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (chosen.isEmpty()) "未选择要并入的来源（返回后勾选 ≥2 个歌单）"
                    else "将并入 " + chosen.size + " 个歌单：" + folders.filter { it.shareId in chosen }
                        .joinToString("、") { it.title },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { target?.let { onConfirm(it, chosen) } },
                enabled = target != null && chosen.isNotEmpty() && !busy
            ) { Text("合并", color = DsoColors.AppRed) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

private fun folderIcon(kind: HomeFolder.Kind) = when (kind) {
    HomeFolder.Kind.SHARE -> Icons.Filled.Folder
    HomeFolder.Kind.ALBUM -> Icons.Filled.Album
    HomeFolder.Kind.UNCATEGORIZED -> Icons.Filled.MusicNote
}

private fun folderKindLabel(kind: HomeFolder.Kind) = when (kind) {
    HomeFolder.Kind.SHARE -> "歌单"
    HomeFolder.Kind.ALBUM -> "专辑"
    HomeFolder.Kind.UNCATEGORIZED -> "未分类"
}