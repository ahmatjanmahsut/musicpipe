package com.musicpipe.player.ui.share

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.musicpipe.player.data.remote.dto.ShareListItem
import com.musicpipe.player.data.remote.dto.ShareSongDto
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

/**
 * 分享导入（v1.12.0 按 DESIGN.md 重构）：歌单链接 → 解析 → 勾选 → 提交；外加订阅历史。
 *
 * 视觉改动：tab 从「粗体 + 粗下划线」换成「mono 编号 + 大写标签 + 1dp 选中线」；
 * Checkbox 全部换成 16dp 方框（选中 = 强调色实心 + ✓），与主页管理态的选择框统一；
 * 提交按钮从 M3 filled Button 换成满宽块状按钮。
 *
 * 业务逻辑与 v1.10.0 完全一致，尤其这两条不能动：
 * - 方案 P4：解析结果必须用 LazyColumn（100+ 首的 Column 会一次性组合全部行，首帧卡顿）。
 * - 可选性判据：inLib == 0 且 state ∈ {null, preview, failed}（已入库/进行中的不可再选）。
 */
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
            containerColor = MaterialTheme.colorScheme.background,
            shape = AwwShapes.Square,
            title = { Text("删除分享", style = AwwTheme.type.h3) },
            text = {
                Text(
                    "删除「" + (target.title ?: target.url ?: target.id) + "」？未完成的排队任务会被取消。",
                    style = AwwTheme.type.bodySmall,
                    color = AwwColors.Ink2
                )
            },
            confirmButton = {
                AwwLabel(
                    text = "删除",
                    color = AwwColors.Accent,
                    modifier = Modifier.clickable { vm.delete(target.id); deleteTarget = null }.padding(AwwSpace.s3)
                )
            },
            dismissButton = {
                AwwLabel(
                    text = "取消",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { deleteTarget = null }.padding(AwwSpace.s3)
                )
            }
        )
    }

    Column(Modifier.fillMaxSize()) {
        AwwPageHeader(
            index = "09",
            label = "SHARE IMPORT",
            title = "分享导入",
            meta = if (state.tab == 0) "粘贴链接导入歌单" else state.history.size.toString().padStart(3, '0') + " 条订阅记录",
            onBack = onBack
        )

        Row(Modifier.fillMaxWidth().padding(horizontal = AwwSpace.s6)) {
            listOf("链接导入", "分享历史").forEachIndexed { i, label ->
                val active = state.tab == i
                Column(
                    Modifier.weight(1f).clickable { vm.setTab(i) }.padding(vertical = AwwSpace.s3),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AwwMono(
                            text = (i + 1).toString().padStart(2, '0'),
                            color = if (active) AwwColors.Accent else AwwColors.Ink3
                        )
                        Spacer(Modifier.width(AwwSpace.s2))
                        AwwLabel(
                            text = label,
                            color = if (active) MaterialTheme.colorScheme.onSurface else AwwColors.Ink3
                        )
                    }
                    Spacer(Modifier.height(AwwSpace.s1))
                    Box(
                        Modifier
                            .width(if (active) 28.dp else 0.dp)
                            .height(1.dp)
                            .background(if (active) AwwColors.Accent else Color.Transparent)
                    )
                }
            }
        }
        AwwRule()

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

/** 16dp 方框选择器（选中 = 强调色实心 + ✓），与主页管理态一致。 */
@Composable
private fun AwwCheck(checked: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    Box(
        Modifier
            .size(16.dp)
            .background(if (checked) AwwColors.Accent else Color.Transparent, AwwShapes.Square)
            .border(
                1.dp,
                if (checked) AwwColors.Accent else AwwColors.Line,
                AwwShapes.Square
            )
            .clickable(enabled = enabled) { onToggle() },
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Text("✓", style = AwwTheme.type.small, color = AwwColors.OnAccent)
        }
    }
}

@Composable
private fun ImportTab(state: ShareImportViewModel.UiState, vm: ShareImportViewModel) {
    var qualityMenu by remember { mutableStateOf(false) }

    // v1.10.0（P4）：必须 LazyColumn —— 100+ 首的 Column 会一次性组合全部行，首帧明显卡顿。
    LazyColumn(Modifier.fillMaxSize()) {
        item(key = "url_input", contentType = "header") {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = AwwSpace.s6, vertical = AwwSpace.s2),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AwwMono(text = ">", color = AwwColors.Accent)
                Spacer(Modifier.width(AwwSpace.s2))
                Box(Modifier.weight(1f)) {
                    if (state.url.isEmpty()) {
                        Text(
                            "粘贴网易云 / QQ音乐 歌单分享链接",
                            style = AwwTheme.type.bodySmall,
                            color = AwwColors.Ink3
                        )
                    }
                    BasicTextField(
                        value = state.url,
                        onValueChange = vm::onUrlChange,
                        singleLine = true,
                        textStyle = AwwTheme.type.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(AwwColors.Accent),
                        modifier = Modifier.fillMaxWidth().padding(vertical = AwwSpace.s2)
                    )
                }
            }
        }
        item(key = "url_rule", contentType = "header") { AwwRule() }
        item(key = "actions", contentType = "header") {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = AwwSpace.s6, vertical = AwwSpace.s2),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    Row(
                        Modifier.clickable { qualityMenu = true }.padding(vertical = AwwSpace.s2),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AwwLabel(text = "音质", color = AwwColors.Ink3)
                        Spacer(Modifier.width(AwwSpace.s2))
                        AwwMono(
                            text = GatewayUtil.qualityLabel(state.quality),
                            color = MaterialTheme.colorScheme.onSurface
                        )
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
                                        color = if (q == state.quality) AwwColors.Accent
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                onClick = { vm.setQuality(q); qualityMenu = false }
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                val canParse = !state.parsing && state.url.isNotBlank()
                AwwLabel(
                    text = if (state.parsing) "解析中…" else "解析链接 →",
                    color = if (canParse) AwwColors.Accent else AwwColors.Ink3,
                    modifier = Modifier
                        .clickable(enabled = canParse) { vm.parse() }
                        .padding(vertical = AwwSpace.s2)
                )
            }
        }
        item(key = "actions_rule", contentType = "header") { AwwRule() }
        if (state.parsing) {
            item(key = "parsing", contentType = "loading") {
                Box(
                    Modifier.fillMaxWidth().padding(vertical = AwwSpace.s6),
                    contentAlignment = Alignment.Center
                ) { AwwMono(text = "解析中…", color = AwwColors.Accent) }
            }
        }
        state.parseError?.let { msg ->
            item(key = "parse_error", contentType = "error") { ErrorBanner(msg) }
        }

        val p = state.preview
        if (p != null) {
            item(key = "preview_head", contentType = "header") {
                val link = p.link
                Column(Modifier.padding(horizontal = AwwSpace.s6, vertical = AwwSpace.s3)) {
                    Text(
                        link?.title ?: "歌单",
                        style = AwwTheme.type.h3,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val sub = metaText {
                        add(platformLabel(link?.platform))
                        add(link?.shareUser)
                        add(((link?.songCount ?: p.songs.size).toString()) + " 首")
                        link?.status?.let { add(shareStatusLabel(it)) }
                    }
                    AwwMono(text = sub, color = AwwColors.Ink3)
                }
            }
            item(key = "select_all", contentType = "header") {
                val selectableCount = p.songs.count {
                    it.inLib == 0 && (it.state == null || it.state == "preview" || it.state == "failed")
                }
                Row(
                    Modifier.fillMaxWidth().clickable { vm.selectAll() }
                        .padding(horizontal = AwwSpace.s6, vertical = AwwSpace.s2),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AwwCheck(
                        checked = state.selected.isNotEmpty() && state.selected.size == selectableCount,
                        enabled = true,
                        onToggle = { vm.selectAll() }
                    )
                    Spacer(Modifier.width(AwwSpace.s3))
                    AwwLabel(
                        text = "全选",
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    AwwMono(
                        text = state.selected.size.toString().padStart(2, '0') + " / " +
                            selectableCount.toString().padStart(2, '0'),
                        color = AwwColors.Ink3
                    )
                }
                AwwRule()
            }
            itemsIndexed(
                items = p.songs,
                key = { _, song -> "song_" + song.extKey },
                contentType = { _, _ -> "song" }
            ) { _, song ->
                SongCheckRow(
                    song = song,
                    checked = song.extKey in state.selected,
                    onToggle = { vm.toggleSong(song.extKey) }
                )
                AwwRule()
            }
            item(key = "commit", contentType = "footer") {
                val canCommit = state.selected.isNotEmpty() && !state.committing
                Row(
                    Modifier.fillMaxWidth()
                        .padding(horizontal = AwwSpace.s6, vertical = AwwSpace.s4),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(52.dp)
                            .background(if (canCommit) AwwColors.Accent else AwwColors.Paper2)
                            .clickable(enabled = canCommit) { vm.commit() },
                        contentAlignment = Alignment.Center
                    ) {
                        AwwMono(
                            text = if (state.committing) "提交中…"
                            else "导入所选（" + state.selected.size + "）",
                            // 强调色块上的文字用 OnAccent，各风格强调色明暗不同（深色模式下
                            // Material 是浅紫底、shadcn 是纯白底），写死 Paper 会变成浅底浅字。
                            color = if (canCommit) AwwColors.OnAccent else AwwColors.Ink3,
                            style = AwwTheme.type.label
                        )
                    }
                }
            }
        }
        item(key = "bottom_pad", contentType = "spacer") { Spacer(Modifier.height(AwwSpace.s24)) }
    }
}

/** 解析结果里的一行：勾选框 + 序号 + 曲名/歌手 + 状态尾巴。 */
@Composable
private fun SongCheckRow(song: ShareSongDto, checked: Boolean, onToggle: () -> Unit) {
    val selectable = song.inLib == 0 &&
        (song.state == null || song.state == "preview" || song.state == "failed")
    Row(
        Modifier.fillMaxWidth()
            .clickable(enabled = selectable) { onToggle() }
            .padding(horizontal = AwwSpace.s6, vertical = AwwSpace.s2),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AwwCheck(checked = checked, enabled = selectable, onToggle = onToggle)
        Spacer(Modifier.width(AwwSpace.s3))
        AwwMono(
            text = song.no.toString().padStart(2, '0'),
            color = AwwColors.Ink3,
            modifier = Modifier.width(AwwSpace.s8)
        )
        Spacer(Modifier.width(AwwSpace.s2))
        Column(Modifier.weight(1f)) {
            Text(
                text = song.title ?: "(未知曲目)",
                style = AwwTheme.type.bodySmall,
                color = if (selectable) MaterialTheme.colorScheme.onSurface else AwwColors.Ink3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val meta = metaText {
                add(song.artist)
                if (song.inLib == 1) add("已在库") else {
                    when (song.state) {
                        "done" -> add("已入库")
                        "exists" -> add("已存在")
                        "queued", "downloading", "pending" -> add("下载中")
                        "failed" -> add("失败，可重试")
                        else -> {}
                    }
                }
            }
            if (meta.isNotEmpty()) AwwMono(text = meta, color = AwwColors.Ink3)
        }
    }
}

@Composable
private fun HistoryTab(
    state: ShareImportViewModel.UiState,
    vm: ShareImportViewModel,
    onDeleteRequest: (ShareListItem) -> Unit
) {
    // 同样用 LazyColumn：订阅列表可能上百条，展开详情还会追加子行。
    LazyColumn(Modifier.fillMaxSize()) {
        state.historyError?.let { msg ->
            item(key = "history_error", contentType = "error") { ErrorBanner(msg) }
        }
        if (state.historyLoading && state.history.isEmpty()) {
            item(key = "history_loading", contentType = "loading") {
                Box(
                    Modifier.fillMaxWidth().padding(vertical = AwwSpace.s8),
                    contentAlignment = Alignment.Center
                ) { AwwMono(text = "读取中…", color = AwwColors.Accent) }
            }
        }
        if (!state.historyLoading && state.history.isEmpty()) {
            item(key = "history_empty", contentType = "empty") {
                AwwEmpty(
                    index = "000",
                    title = "还没有订阅的歌单",
                    hint = "在「链接导入」里粘贴歌单链接后，这里会保留订阅记录"
                )
            }
        }
        itemsIndexed(
            items = state.history,
            key = { _, item -> "h_" + item.id },
            contentType = { _, _ -> "history" }
        ) { index, item ->
            val expanded = state.expandedId == item.id
            val busy = state.busyId == item.id
            Column(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth()
                        .clickable { vm.toggleDetail(item.id) }
                        .padding(horizontal = AwwSpace.s6, vertical = AwwSpace.s3),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AwwMono(
                        text = (index + 1).toString().padStart(2, '0'),
                        color = AwwColors.Ink3,
                        modifier = Modifier.width(AwwSpace.s8)
                    )
                    Spacer(Modifier.width(AwwSpace.s2))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = item.title ?: item.url ?: item.id,
                            style = AwwTheme.type.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val meta = metaText {
                            add(platformLabel(item.platform))
                            add(item.shareUser)
                            add(item.songCount.toString() + " 首")
                            add("在库 " + item.inLibrary)
                            item.status?.let { add(shareStatusLabel(it)) }
                        }
                        AwwMono(text = meta, color = AwwColors.Ink3)
                    }
                    Spacer(Modifier.width(AwwSpace.s2))
                    if (busy) {
                        AwwMono(text = "···", color = AwwColors.Accent)
                    } else {
                        Icon(
                            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = if (expanded) "收起" else "展开",
                            tint = AwwColors.Ink3,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                if (expanded) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = AwwSpace.s6, bottom = AwwSpace.s2),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AwwLabel(
                            text = "强制同步",
                            color = AwwColors.Accent,
                            modifier = Modifier.clickable(enabled = !busy) { vm.sync(item.id) }
                                .padding(AwwSpace.s2)
                        )
                        Spacer(Modifier.width(AwwSpace.s4))
                        AwwLabel(
                            text = "删除",
                            color = AwwColors.Ink2,
                            modifier = Modifier.clickable(enabled = !busy) { onDeleteRequest(item) }
                                .padding(AwwSpace.s2)
                        )
                    }
                    val songs = state.expandedSongs
                    if (songs.isEmpty() && busy) {
                        Box(
                            Modifier.fillMaxWidth().padding(vertical = AwwSpace.s3),
                            contentAlignment = Alignment.Center
                        ) { AwwMono(text = "读取曲目…", color = AwwColors.Ink3) }
                    }
                    songs.forEach { song ->
                        Row(
                            Modifier.fillMaxWidth()
                                .padding(start = AwwSpace.s12, end = AwwSpace.s6, bottom = AwwSpace.s1),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AwwMono(
                                text = song.no.toString().padStart(2, '0'),
                                color = AwwColors.Ink3,
                                modifier = Modifier.width(AwwSpace.s6)
                            )
                            Spacer(Modifier.width(AwwSpace.s2))
                            Text(
                                text = (song.title ?: "-") + "  " + (song.artist ?: ""),
                                style = AwwTheme.type.small,
                                color = AwwColors.Ink2,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            AwwMono(
                                text = if (song.inLib == 1) "IN" else (song.state ?: "-"),
                                color = if (song.inLib == 1) AwwColors.Accent else AwwColors.Ink3
                            )
                        }
                    }
                }
                AwwRule()
            }
        }
        item(key = "history_pad", contentType = "spacer") { Spacer(Modifier.height(AwwSpace.s24)) }
    }
}
