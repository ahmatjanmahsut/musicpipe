package com.musicpipe.player.ui.netsearch

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.musicpipe.player.data.model.NetSong
import com.musicpipe.player.di.LocalAppContainer
import com.musicpipe.player.ui.components.AppTextField
import com.musicpipe.player.ui.components.EmptyBlock
import com.musicpipe.player.ui.components.ErrorBanner
import com.musicpipe.player.ui.components.LoadingBlock
import com.musicpipe.player.ui.theme.DsoColors
import com.musicpipe.player.ui.theme.DsoShapes
import com.musicpipe.player.util.GatewayUtil
import com.musicpipe.player.util.TimeFormat

/** 网络搜索: multi-platform (酷我/酷狗/QQ音乐/网易) search + one-tap enqueue download. */
@OptIn(ExperimentalMaterial3Api::class)
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
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                "网络搜索", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface
            )
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppTextField(
                value = state.query,
                onValueChange = vm::onQueryChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("歌名 / 歌手") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.search() })
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { vm.search() },
                enabled = !state.searching && state.query.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = DsoColors.AppRed)
            ) { Text("搜索") }
        }

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GatewayUtil.PLATFORMS.forEach { (code, name) ->
                FilterChip(
                    selected = code in state.selectedPlatforms,
                    onClick = { vm.togglePlatform(code) },
                    label = { Text(name) }
                )
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                TextButton(onClick = { qualityMenu = true }) {
                    Text(
                        "音质：" + GatewayUtil.qualityLabel(state.quality),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Icon(
                        Icons.Filled.ArrowDropDown, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(expanded = qualityMenu, onDismissRequest = { qualityMenu = false }) {
                    GatewayUtil.QUALITIES.forEach { q ->
                        DropdownMenuItem(
                            text = { Text(GatewayUtil.qualityLabel(q)) },
                            onClick = { vm.setQuality(q); qualityMenu = false }
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = { vm.downloadAll() },
                // v1.6.0：入队过程中禁用，避免连点触发多轮批量入队（旧实现会把路由器打满）。
                enabled = state.groups.any { it.songs.isNotEmpty() } && !state.downloadingAll
            ) {
                Icon(
                    Icons.Filled.Download, contentDescription = null,
                    tint = if (state.downloadingAll) DsoColors.TextWeak else DsoColors.AppRed
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    if (state.downloadingAll) "入队中…" else "全部下载",
                    color = if (state.downloadingAll) DsoColors.TextWeak else DsoColors.AppRed
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        if (state.error != null) ErrorBanner(state.error!!)

        when {
            state.searching -> LoadingBlock(Modifier.weight(1f))
            !state.searched -> EmptyBlock(
                "输入歌名或歌手，搜索酷我 / 酷狗 / QQ音乐 / 网易\n选中结果一键下载到路由器曲库",
                Modifier.weight(1f)
            )
            state.groups.all { it.songs.isEmpty() } && state.error == null ->
                EmptyBlock("没有找到相关歌曲", Modifier.weight(1f))
            else -> LazyColumn(Modifier.weight(1f)) {
                state.groups.forEach { group ->
                    item(key = "header_" + group.platform) { GroupHeader(group) }
                    if (!group.ok && !group.error.isNullOrBlank()) {
                        item(key = "err_" + group.platform) { ErrorBanner(group.name + "：" + group.error) }
                    }
                    items(group.songs, key = { group.platform + "_" + it.key }) { song ->
                        NetSongRow(
                            song = song,
                            downloading = song.key in state.downloadingKeys,
                            onOpen = { vm.openDetail(song) }
                        )
                    }
                }
                item(key = "load_more") {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (state.loadingMore) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = DsoColors.AppRed, strokeWidth = 2.dp
                            )
                        } else {
                            TextButton(onClick = { vm.loadMore() }) { Text("加载更多") }
                        }
                    }
                }
                item(key = "bottom_pad") { Spacer(Modifier.height(80.dp)) }
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
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            group.name.ifBlank { group.platform },
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.width(8.dp))
        Text(
            if (group.ok) group.songs.size.toString() + " 首" else "获取失败",
            style = MaterialTheme.typography.bodySmall,
            color = if (group.ok) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun NetSongRow(song: NetSong, downloading: Boolean, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = !downloading, onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = song.img,
            contentDescription = null,
            modifier = Modifier.size(46.dp).clip(DsoShapes.SmallSurface)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.name.ifBlank { "未知歌曲" },
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            val sub = buildList {
                if (song.singer.isNotBlank()) add(song.singer)
                if (song.album.isNotBlank() && song.album != song.singer) add(song.album)
                if (song.durationSec > 0) add(TimeFormat.format(song.durationSec * 1000))
                song.qualityTypes.firstOrNull()?.let { add(it.uppercase()) }
            }.joinToString(" · ")
            if (sub.isNotBlank()) {
                Text(
                    sub, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        IconButton(onClick = onOpen, enabled = !downloading) {
            if (downloading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp), color = DsoColors.AppRed, strokeWidth = 2.dp
                )
            } else {
                Icon(
                    Icons.Filled.Download, contentDescription = "下载",
                    tint = DsoColors.AppRed
                )
            }
        }
    }
}

/**
 * 下载确认弹窗：曲目详情 + 音质可用性。
 *
 * 旧版点一下就直接入队，用户既看不到这首歌有哪些音质可选，
 * 也不知道服务端最终拿到了什么；连续下载时更是连点几下就派出多个任务。
 * 现在每次下载都经过这里，列出该平台实际支持的音质（NetSong.supportsQuality），
 * 不可用的音质置灰并注明原因，从源头避免"选了无损却拿到 mp3"。
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
        title = { Text(song.name.ifBlank { "未知歌曲" }, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    buildList {
                        if (song.singer.isNotBlank()) add(song.singer)
                        if (song.album.isNotBlank()) add(song.album)
                        if (song.durationSec > 0) add(TimeFormat.format(song.durationSec * 1000))
                    }.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    "下载音质", style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "音源实际能提供的品质可能与所选不同；服务端会在下载前校验真实格式，" +
                        "若音源降级会自动纠正扩展名，不会再出现格式不符的报错。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                GatewayUtil.QUALITIES.forEach { q ->
                    val ok = song.supportsQuality(q)
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(DsoShapes.Input)
                            .clickable(enabled = ok) { onSelect(q) }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selected == q, onClick = { onSelect(q) }, enabled = ok)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            GatewayUtil.qualityLabel(q),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (ok) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        if (!ok) {
                            Spacer(Modifier.weight(1f))
                            Text(
                                "该平台不提供",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDownload) { Text("下载", color = DsoColors.AppRed) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}