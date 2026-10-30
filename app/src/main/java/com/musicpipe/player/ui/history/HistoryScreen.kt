package com.musicpipe.player.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.musicpipe.player.di.LocalAppContainer
import com.musicpipe.player.ui.components.SongListItem
import com.musicpipe.player.ui.design.AwwEmpty
import com.musicpipe.player.ui.design.AwwLabel
import com.musicpipe.player.ui.design.AwwMono
import com.musicpipe.player.ui.design.AwwPageHeader
import com.musicpipe.player.ui.theme.AwwColors
import com.musicpipe.player.ui.theme.AwwSpace
import com.musicpipe.player.ui.theme.AwwTheme
import com.musicpipe.player.util.TimeFormat
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * 播放历史页（需求2：本地持久化 / 单删 / 清空 / 重启不丢）。
 *
 * v1.12.0 视觉：页眉统一走 [AwwPageHeader]；行尾不用图标按钮，
 * 改成"mono 时间戳 + ×"——列表右侧应该是信息而不是按钮阵列。
 * 清空入口放在页眉右侧的 uppercase 小标签，危险操作走确认框。
 *
 * 性能约定（v1.4.0 起，勿破坏）：只订阅当前曲目 id，500ms 心跳不得带动列表重组。
 */
@Composable
fun HistoryScreen() {
    val container = LocalAppContainer.current
    val vm: HistoryViewModel = viewModel(factory = HistoryViewModel.factory(container))
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val currentSongId by remember {
        container.playerRepository.state
            .map { it.currentSong?.id }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = null)
    var showClearConfirm by remember { mutableStateOf(false) }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            containerColor = MaterialTheme.colorScheme.background,
            shape = com.musicpipe.player.ui.theme.AwwShapes.Square,
            title = { Text("清空播放历史", style = AwwTheme.type.h3) },
            text = {
                Text(
                    "确定要清空全部播放历史吗？此操作不可恢复。",
                    style = AwwTheme.type.bodySmall,
                    color = AwwColors.Ink2
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.clearAll(); showClearConfirm = false }) {
                    AwwLabel(text = "清空", color = AwwColors.Accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    AwwLabel(text = "取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    Column(Modifier.fillMaxSize()) {
        AwwPageHeader(
            index = "04",
            label = "HISTORY",
            title = "播放历史",
            meta = if (tracks.isEmpty()) "—" else tracks.size.toString().padStart(2, '0') + " 条记录",
            trailing = {
                if (tracks.isNotEmpty()) {
                    AwwLabel(
                        text = "清空",
                        color = AwwColors.Accent,
                        modifier = Modifier
                            .padding(start = AwwSpace.s4)
                            .clickable { showClearConfirm = true }
                    )
                }
            }
        )
        if (tracks.isEmpty()) {
            AwwEmpty(index = "—", title = "暂无记录", hint = "播放过的歌曲会留在这里")
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(
                    tracks,
                    key = { it.id },
                    contentType = { "track" }
                ) { track ->
                    // v1.14.0：toSong() 每次调用都要 new Song + 走两遍 ServerConfig.signed
                    // （判前缀、trimEnd、拼 /、拼 ?token= —— 每次 3~4 个中间字符串），
                    // 而 LazyColumn 每帧都为可见行重组。用 remember(track) 钉住结果：
                    // 轨道数据不变就不再重算。
                    val song = remember(track) { container.historyRepository.toSong(track) }
                    SongListItem(
                        song = song,
                        isCurrentPlaying = currentSongId == track.songId,
                        onClick = { vm.play(track) },
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AwwMono(
                                    text = TimeFormat.formatDateTime(track.playedAt),
                                    color = AwwColors.Ink3
                                )
                                IconButton(
                                    onClick = { vm.delete(track) },
                                    modifier = Modifier.padding(start = AwwSpace.s2)
                                ) {
                                    Icon(
                                        Icons.Filled.Close, "删除",
                                        tint = AwwColors.Ink3,
                                        modifier = Modifier.height(16.dp)
                                    )
                                }
                            }
                        }
                    )
                }
                item(key = "bottom_pad", contentType = "spacer") {
                    Spacer(Modifier.height(AwwSpace.s24))
                }
            }
        }
    }
}
