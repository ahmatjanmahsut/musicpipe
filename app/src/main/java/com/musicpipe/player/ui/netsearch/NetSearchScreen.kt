package com.musicpipe.player.ui.netsearch

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.musicpipe.player.data.image.FadeInCover
import com.musicpipe.player.data.model.NetSong
import com.musicpipe.player.di.LocalAppContainer
import com.musicpipe.player.ui.components.ErrorBanner
import com.musicpipe.player.ui.components.LoadingBlock
import com.musicpipe.player.ui.design.AwwEmpty
import com.musicpipe.player.ui.design.AwwLabel
import com.musicpipe.player.ui.design.AwwMono
import com.musicpipe.player.ui.design.AwwPageHeader
import com.musicpipe.player.ui.design.AwwRule
import com.musicpipe.player.ui.theme.AwwColors
import com.musicpipe.player.ui.theme.AwwShapes
import com.musicpipe.player.ui.theme.AwwSpace
import com.musicpipe.player.ui.theme.AwwTheme
import com.musicpipe.player.util.GatewayUtil
import com.musicpipe.player.util.metaText
import com.musicpipe.player.util.TimeFormat

/**
 * 网络搜索（v1.12.0 按 DESIGN.md 重构）：多平台（酷我/酷狗/QQ/网易）搜索 + 一键入队下载。
 *
 * 视觉改动：平台筛选从 FilterChip 换成「mono 编号 + 大写标签 + 选中下划线」；
 * 音质选择从 TextButton+DropdownMenu 换成一行内联的 mono 值 + 箭头；
 * 每行曲目 = 46dp 方形封面（2dp 圆角）+ body 曲名 + mono 细节 + 右侧下载箭头。
 *
 * 业务逻辑与 v1.10.0 完全一致：
 * - v1.6.0 需求2：downloadAll 内部已按 10 首分片 + 限流，这里只负责禁用重复点击。
 * - 下载前必进详情弹窗（列出该平台真实支持的音质，不可用的置灰）。
 */
@Composable
fun NetSearchScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: NetSearchViewModel = viewModel(factory = NetSearchViewModel.factory(container))
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var qualityMenu by remember { mutableStateOf(false) }

    LaunchedEffect(state.toast) {
        state.toast?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            vm.consumeToast()
        }
    }

    Column(Modifier.fillMaxSize()) {
        AwwPageHeader(
            index = "08",
            label = "NET SEARCH",
            title = "网络搜索",
            meta = if (state.groups.all { it.songs.isEmpty() }) "四个平台" else
                state.groups.sumOf { it.songs.size }.toString().padStart(3, '0') + " 个结果",
            onBack = onBack
        )

        // 搜索输入：'>' 前缀 + 裸输入框 + 底线，与搜索页保持同一语言。
        Row(
            Modifier.fillMaxWidth().padding(horizontal = AwwSpace.s6, vertical = AwwSpace.s2),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AwwMono(text = ">", color = AwwColors.Accent)
            Spacer(Modifier.width(AwwSpace.s2))
            Box(Modifier.weight(1f)) {
                if (state.query.isEmpty()) {
                    Text("歌名 / 歌手", style = AwwTheme.type.body, color = AwwColors.Ink3)
                }
                BasicTextField(
                    value = state.query,
                    onValueChange = vm::onQueryChange,
                    singleLine = true,
                    textStyle = AwwTheme.type.body.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(AwwColors.Accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { vm.search() }),
                    modifier = Modifier.fillMaxWidth().padding(vertical = AwwSpace.s2)
                )
            }
            Spacer(Modifier.width(AwwSpace.s2))
            val canSearch = !state.searching && state.query.isNotBlank()
            AwwLabel(
                text = "搜索",
                color = if (canSearch) AwwColors.Accent else AwwColors.Ink3,
                modifier = Modifier
                    .clickable(enabled = canSearch) { vm.search() }
                    .padding(AwwSpace.s2)
            )
        }
        AwwRule()

        // 平台筛选：mono 编号 + 标签 + 选中下划线（替代 FilterChip 的圆角药丸）
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                .padding(horizontal = AwwSpace.s6, vertical = AwwSpace.s3),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(AwwSpace.s6)
        ) {
            GatewayUtil.PLATFORMS.forEachIndexed { index, (code, name) ->
                val on = code in state.selectedPlatforms
                Column(
                    Modifier.clickable { vm.togglePlatform(code) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AwwMono(
                            text = (index + 1).toString().padStart(2, '0'),
                            color = if (on) AwwColors.Accent else AwwColors.Ink3
                        )
                        Spacer(Modifier.width(AwwSpace.s1))
                        AwwLabel(
                            text = name,
                            color = if (on) MaterialTheme.colorScheme.onSurface else AwwColors.Ink3
                        )
                    }
                    Spacer(Modifier.height(AwwSpace.s1))
                    Box(
                        Modifier
                            .width(if (on) 24.dp else 0.dp)
                            .height(1.dp)
                            .background(if (on) AwwColors.Accent else androidx.compose.ui.graphics.Color.Transparent)
                    )
                }
            }
        }

        // 音质 + 全部下载
        Row(
            Modifier.fillMaxWidth().padding(horizontal = AwwSpace.s6),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                Row(
                    Modifier.clickable { qualityMenu = true }.padding(vertical = AwwSpace.s2),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AwwLabel(text = "音质", color = AwwColors.Ink3)
                    Spacer(Modifier.width(AwwSpace.s2))
                    AwwMono(text = GatewayUtil.qualityLabel(state.quality), color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.width(AwwSpace.s1))
                    AwwMono(text = "v", color = AwwColors.Ink3)
                }
                DropdownMenu(
                    expanded = qualityMenu,
                    onDismissRequest = { qualityMenu = false },
                    containerColor = MaterialTheme.colorScheme.background
                ) {
                    GatewayUtil.QUALITIES.forEach { q ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    GatewayUtil.qualityLabel(q),
                                    style = AwwTheme.type.body,
                                    color = if (q == state.quality) AwwColors.Accent else MaterialTheme.colorScheme.onSurface
                                )
                            },
                            onClick = { vm.setQuality(q); qualityMenu = false }
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            // v1.6.0：入队过程中禁用，避免连点触发多轮批量入队。
            val canDownloadAll = state.groups.any { it.songs.isNotEmpty() } && !state.downloadingAll
            AwwLabel(
                text = if (state.downloadingAll) "入队中…" else "全部下载 →",
                color = if (canDownloadAll) AwwColors.Accent else AwwColors.Ink3,
                modifier = Modifier
                    .clickable(enabled = canDownloadAll) { vm.downloadAll() }
                    .padding(vertical = AwwSpace.s2)
            )
        }
        AwwRule()

        if (state.error != null) ErrorBanner(state.error!!)

        when {
            state.searching -> LoadingBlock(Modifier.weight(1f))
            !state.searched -> AwwEmpty(
                index = "000",
                title = "搜索全网",
                hint = "输入歌名或歌手，搜索酷我 / 酷狗 / QQ音乐 / 网易，选中结果一键下载到路由器曲库",
                modifier = Modifier.weight(1f)
            )
            state.groups.all { it.songs.isEmpty() } && state.error == null -> AwwEmpty(
                index = "—",
                title = "没有找到相关歌曲",
                hint = "换个关键词，或只勾选其中一两个平台再试",
                modifier = Modifier.weight(1f)
            )
            else -> LazyColumn(Modifier.weight(1f)) {
                state.groups.forEach { group ->
                    item(key = "header_" + group.platform, contentType = "header") {
                        GroupHeader(group)
                    }
                    if (!group.ok && !group.error.isNullOrBlank()) {
                        item(key = "err_" + group.platform, contentType = "error") {
                            ErrorBanner(group.name + "：" + group.error)
                        }
                    }
                    items(
                        group.songs,
                        key = { group.platform + "_" + it.key },
                        contentType = { "song" }
                    ) { song ->
                        NetSongRow(
                            song = song,
                            downloading = song.key in state.downloadingKeys,
                            onOpen = { vm.openDetail(song) }
                        )
                        AwwRule()
                    }
                }
                item(key = "load_more", contentType = "footer") {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = AwwSpace.s6),
                        contentAlignment = Alignment.Center
                    ) {
                        if (state.loadingMore) {
                            AwwMono(text = "加载中…", color = AwwColors.Accent)
                        } else {
                            AwwLabel(
                                text = "加载更多 →",
                                color = AwwColors.Accent,
                                modifier = Modifier
                                    .clickable { vm.loadMore() }
                                    .padding(AwwSpace.s2)
                            )
                        }
                    }
                }
                item(key = "bottom_pad", contentType = "spacer") {
                    Spacer(Modifier.height(AwwSpace.s24))
                }
            }
        }
    }

    val detail = state.detail
    if (detail != null) {
        NetSongDetailDialog(
            song = detail,
            selected = state.detailQuality,
            onSelect = vm::setDetailQuality,
            onDownload = { vm.download(detail, state.detailQuality) },
            onDismiss = vm::closeDetail
        )
    }
}

@Composable
private fun GroupHeader(group: NetSearchViewModel.PlatformGroup) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = AwwSpace.s6, vertical = AwwSpace.s3),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AwwLabel(
            text = group.name.ifBlank { group.platform },
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.width(AwwSpace.s3))
        AwwMono(
            text = if (group.ok) group.songs.size.toString().padStart(2, '0') else "ERR",
            color = if (group.ok) AwwColors.Ink3 else AwwColors.Accent
        )
    }
}

@Composable
private fun NetSongRow(song: NetSong, downloading: Boolean, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = !downloading, onClick = onOpen)
            .padding(horizontal = AwwSpace.s6, vertical = AwwSpace.s2),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // v1.14.0：统一走 FadeInCover（解码尺寸 46dp + 统一缓存策略 + 无封面占位），
        // 代替裸 AsyncImage。搜索结果可长可短，滚动时同样每帧重组。
        FadeInCover(
            model = song.img,
            contentDescription = null,
            dpSize = 46,
            modifier = Modifier
                .width(46.dp)
                .height(46.dp)
                .clip(AwwShapes.Image)
                .background(AwwColors.Paper2),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.width(AwwSpace.s3))
        Column(Modifier.weight(1f)) {
            Text(
                song.name.ifBlank { "未知歌曲" },
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = AwwTheme.type.body,
                color = MaterialTheme.colorScheme.onSurface
            )
            val sub = metaText {
                add(song.singer)
                if (song.album != song.singer) add(song.album)
                if (song.durationSec > 0) add(TimeFormat.format(song.durationSec * 1000))
                add(song.qualityTypes.firstOrNull()?.uppercase())
            }
            if (sub.isNotBlank()) {
                AwwMono(
                    text = sub,
                    color = AwwColors.Ink3,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        Spacer(Modifier.width(AwwSpace.s2))
        if (downloading) {
            AwwMono(text = "···", color = AwwColors.Accent)
        } else {
            AwwMono(
                text = "↓",
                color = AwwColors.Accent,
                modifier = Modifier.width(24.dp)
            )
        }
    }
}

/**
 * 下载确认弹窗：曲目详情 + 音质可用性。
 *
 * 旧版点一下就直接入队，用户既看不到这首歌有哪些音质可选，也不知道服务端最终拿到了什么；
 * 连续下载时更是连点几下就派出多个任务。现在每次下载都经过这里，列出该平台实际支持的音质
 * （NetSong.supportsQuality），不可用的音质置灰并注明原因，从源头避免"选了无损却拿到 mp3"。
 */
@Composable
private fun NetSongDetailDialog(
    song: NetSong,
    selected: String,
    onSelect: (String) -> Unit,
    onDownload: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        shape = AwwShapes.Square,
        title = {
            Text(
                song.name.ifBlank { "未知歌曲" },
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = AwwTheme.type.h3
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                AwwMono(
                    text = metaText {
                        add(song.singer)
                        add(song.album)
                        if (song.durationSec > 0) add(TimeFormat.format(song.durationSec * 1000))
                    },
                    color = AwwColors.Ink3
                )
                Spacer(Modifier.height(AwwSpace.s4))
                AwwRule()
                Spacer(Modifier.height(AwwSpace.s3))
                AwwLabel(text = "下载音质", color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(AwwSpace.s2))
                Text(
                    "音源实际能提供的品质可能与所选不同；服务端会在下载前校验真实格式，" +
                        "若音源降级会自动纠正扩展名，不会再出现格式不符的报错。",
                    style = AwwTheme.type.bodySmall,
                    color = AwwColors.Ink3
                )
                Spacer(Modifier.height(AwwSpace.s3))
                GatewayUtil.QUALITIES.forEach { q ->
                    val ok = song.supportsQuality(q)
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable(enabled = ok) { onSelect(q) }
                            .padding(vertical = AwwSpace.s2),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 选中用 2dp 竖线标记，不用 RadioButton 圆点
                        Box(
                            Modifier
                                .width(2.dp)
                                .height(16.dp)
                                .background(
                                    if (selected == q) AwwColors.Accent
                                    else androidx.compose.ui.graphics.Color.Transparent
                                )
                        )
                        Spacer(Modifier.width(AwwSpace.s3))
                        Text(
                            GatewayUtil.qualityLabel(q),
                            style = AwwTheme.type.body,
                            color = if (ok) MaterialTheme.colorScheme.onSurface else AwwColors.Ink3
                        )
                        if (!ok) {
                            Spacer(Modifier.weight(1f))
                            AwwMono(text = "该平台不提供", color = AwwColors.Ink3)
                        }
                    }
                }
            }
        },
        confirmButton = {
            AwwLabel(
                text = "下载",
                color = AwwColors.Accent,
                modifier = Modifier.clickable { onDownload() }.padding(AwwSpace.s3)
            )
        },
        dismissButton = {
            AwwLabel(
                text = "取消",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable { onDismiss() }.padding(AwwSpace.s3)
            )
        }
    )
}
