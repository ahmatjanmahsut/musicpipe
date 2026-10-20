package com.musicpipe.player.ui.share

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.musicpipe.player.data.remote.dto.ShareListItem
import com.musicpipe.player.data.remote.dto.ShareSongDto
import com.musicpipe.player.di.LocalAppContainer
import com.musicpipe.player.ui.components.AppTextField
import com.musicpipe.player.ui.components.EmptyBlock
import com.musicpipe.player.ui.components.ErrorBanner
import com.musicpipe.player.ui.components.LoadingBlock
import com.musicpipe.player.ui.theme.DsoColors
import com.musicpipe.player.util.GatewayUtil
import com.musicpipe.player.util.TimeFormat

private fun platformLabel(code: String?): String =
    GatewayUtil.PLATFORMS.find { it.first == code }?.second ?: (code ?: "-")

private fun shareStatusLabel(status: String?): String = when (status) {
    "pending" -> "待确认"
    "active" -> "已订阅"
    "failed" -> "同步失败"
    "done" -> "已完成"
    null, "" -> "-"
    else -> status
}

/** 分享导入: playlist URL → parse → select → commit; plus subscription history. */
@Composable
fun ShareImportScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: ShareImportViewModel = viewModel(factory = ShareImportViewModel.factory(container))
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var deleteTarget by remember { mutableStateOf<ShareListItem?>(null) }

    LaunchedEffect(state.toast) {
        state.toast?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            vm.consumeToast()
        }
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除分享") },
            text = { Text("删除「" + (target.title ?: target.url ?: target.id) + "」？未完成的排队任务会被取消。") },
            confirmButton = {
                TextButton(onClick = { vm.delete(target.id); deleteTarget = null }) {
                    Text("删除", color = DsoColors.AppRed)
                }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } }
        )
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
                "分享导入", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface
            )
        }

        // tabs
        Row(Modifier.fillMaxWidth()) {
            listOf("链接导入", "分享历史").forEachIndexed { i, label ->
                val active = state.tab == i
                Column(
                    Modifier.weight(1f).clickable { vm.setTab(i) }.padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        color = if (active) DsoColors.AppRed else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier.width(36.dp).height(3.dp)
                            .background(if (active) DsoColors.AppRed else MaterialTheme.colorScheme.surfaceVariant)
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        if (state.tab == 0) {
            ImportTab(state = state, vm = vm)
        } else {
            HistoryTab(
                state = state, vm = vm,
                onDeleteRequest = { deleteTarget = it }
            )
        }
    }
}

@Composable
private fun ImportTab(state: ShareImportViewModel.UiState, vm: ShareImportViewModel) {
    var qualityMenu by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppTextField(
                value = state.url,
                onValueChange = vm::onUrlChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("粘贴网易云 / QQ音乐 歌单分享链接") }
            )
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
            Button(
                onClick = { vm.parse() },
                enabled = !state.parsing && state.url.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = DsoColors.AppRed)
            ) { Text(if (state.parsing) "解析中…" else "解析链接") }
        }

        if (state.parsing) {
            Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DsoColors.AppRed)
            }
        }
        if (state.parseError != null) ErrorBanner(state.parseError!!)

        state.preview?.let { p ->
            val link = p.link
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    link?.title ?: "歌单",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val sub = buildList {
                    add(platformLabel(link?.platform))
                    link?.shareUser?.takeIf { it.isNotBlank() }?.let { add(it) }
                    add((link?.songCount ?: p.songs.size).toString() + " 首")
                    link?.status?.let { add(shareStatusLabel(it)) }
                }.joinToString(" · ")
                Text(
                    sub, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val selectableCount = p.songs.count {
                it.inLib == 0 && (it.state == null || it.state == "preview" || it.state == "failed")
            }
            Row(
                Modifier.fillMaxWidth().clickable { vm.selectAll() }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = state.selected.isNotEmpty() && state.selected.size == selectableCount,
                    onCheckedChange = { vm.selectAll() },
                    colors = CheckboxDefaults.colors(checkedColor = DsoColors.AppRed)
                )
                Text(
                    "全选", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "已选 " + state.selected.size + " 首",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 12.dp)
                )
            }

            p.songs.forEach { song ->
                SongCheckRow(
                    song = song,
                    checked = song.extKey in state.selected,
                    onToggle = { vm.toggleSong(song.extKey) }
                )
            }

            Button(
                onClick = { vm.commit() },
                enabled = state.selected.isNotEmpty() && !state.committing,
                colors = ButtonDefaults.buttonColors(containerColor = DsoColors.AppRed),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(if (state.committing) "提交中…" else "导入所选（" + state.selected.size + "）")
            }
        }
        Spacer(Modifier.height(90.dp))
    }
}

@Composable
private fun SongCheckRow(song: ShareSongDto, checked: Boolean, onToggle: () -> Unit) {
    val enabled = song.inLib == 0 && (song.state == null || song.state == "preview" || song.state == "failed")
    Row(
        Modifier.fillMaxWidth()
            .then(if (enabled) Modifier.clickable(onClick = onToggle) else Modifier)
            .padding(horizontal = 10.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { if (enabled) onToggle() },
            enabled = enabled,
            colors = CheckboxDefaults.colors(checkedColor = DsoColors.AppRed)
        )
        Column(Modifier.weight(1f)) {
            Text(
                (song.title ?: "未知歌曲"),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!song.artist.isNullOrBlank()) {
                Text(
                    song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (!enabled) {
            Text(
                GatewayUtil.shareSongStateLabel(song.state, song.inLib),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 12.dp)
            )
        }
    }
}

@Composable
private fun HistoryTab(
    state: ShareImportViewModel.UiState,
    vm: ShareImportViewModel,
    onDeleteRequest: (ShareListItem) -> Unit
) {
    when {
        state.historyLoading && state.history.isEmpty() -> LoadingBlock(Modifier.fillMaxSize())
        state.history.isEmpty() -> EmptyBlock(
            state.historyError ?: "暂无分享记录\n在「链接导入」解析歌单后会出现在这里",
            Modifier.fillMaxSize()
        )
        else -> LazyColumn(Modifier.fillMaxSize()) {
            if (state.historyError != null) item(key = "err") { ErrorBanner(state.historyError!!) }
            items(state.history, key = { it.id }) { item ->
                HistoryRow(
                    item = item,
                    expanded = state.expandedId == item.id,
                    busy = state.busyId == item.id,
                    songs = if (state.expandedId == item.id) state.expandedSongs else emptyList(),
                    onSync = { vm.sync(item.id) },
                    onToggleDetail = { vm.toggleDetail(item.id) },
                    onDelete = { onDeleteRequest(item) }
                )
            }
            item(key = "bottom_pad") { Spacer(Modifier.height(90.dp)) }
        }
    }
}

@Composable
private fun HistoryRow(
    item: ShareListItem,
    expanded: Boolean,
    busy: Boolean,
    songs: List<ShareSongDto>,
    onSync: () -> Unit,
    onToggleDetail: () -> Unit,
    onDelete: () -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    item.title ?: item.url ?: item.id,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val sub = buildList {
                    add(platformLabel(item.platform))
                    add(shareStatusLabel(item.status))
                    add(item.songCount.toString() + " 首")
                    if (item.inLibrary > 0) add("在库 " + item.inLibrary)
                    item.quality?.let { add(GatewayUtil.qualityLabel(it)) }
                    item.addedAt?.takeIf { it > 0 }?.let { add(TimeFormat.formatDateTime(it)) }
                }.joinToString(" · ")
                Text(
                    sub, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!item.lastResult.isNullOrBlank()) {
                    Text(
                        item.lastResult, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onSync, enabled = !busy) {
                Icon(
                    Icons.Filled.Sync, contentDescription = "同步",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onToggleDetail) {
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = "曲目",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete, enabled = !busy) {
                Icon(
                    Icons.Filled.DeleteOutline, contentDescription = "删除",
                    tint = DsoColors.AppRed
                )
            }
        }
        if (expanded) {
            if (busy && songs.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = DsoColors.AppRed, strokeWidth = 2.dp)
                }
            }
            songs.forEach { s ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        ((s.title ?: "未知歌曲")),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    if (!s.artist.isNullOrBlank()) {
                        Text(
                            s.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                    Text(
                        GatewayUtil.shareSongStateLabel(s.state, s.inLib),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (!busy && songs.isEmpty()) {
                Text(
                    "无曲目", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
                )
            }
        }
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}
