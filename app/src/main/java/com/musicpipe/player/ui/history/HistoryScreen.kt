package com.musicpipe.player.ui.history

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.lifecycle.viewmodel.compose.viewModel
import com.musicpipe.player.di.LocalAppContainer
import com.musicpipe.player.ui.components.EmptyBlock
import com.musicpipe.player.ui.components.SongListItem
import com.musicpipe.player.ui.theme.DsoColors
import com.musicpipe.player.util.TimeFormat

/** 播放历史 page: list + per-row delete + clear-all confirm (requirement 2). */
@Composable
fun HistoryScreen() {
    val container = LocalAppContainer.current
    val vm: HistoryViewModel = viewModel(factory = HistoryViewModel.factory(container))
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    // Only the current song id (distinct): the 500ms position ticks must not
    // recompose this list while playing (v1.4.0 jank fix).
    val currentSongId by remember {
        container.playerRepository.state
            .map { it.currentSong?.id }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = null)
    var showClearConfirm by remember { mutableStateOf(false) }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清空播放历史") },
            text = { Text("确定要清空全部播放历史吗？此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = { vm.clearAll(); showClearConfirm = false }) {
                    Text("清空", color = DsoColors.AppRed)
                }
            },
            dismissButton = { TextButton(onClick = { showClearConfirm = false }) { Text("取消") } }
        )
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "播放历史", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)
            )
            if (tracks.isNotEmpty()) {
                TextButton(onClick = { showClearConfirm = true }) {
                    Text("清空", color = DsoColors.AppRed)
                }
            }
        }
        if (tracks.isEmpty()) {
            EmptyBlock("暂无播放记录")
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(
                    tracks,
                    key = { it.id },
                    contentType = { "track" } // v1.10.0（P10）
                ) { track ->
                    SongListItem(
                        song = container.historyRepository.toSong(track),
                        isCurrentPlaying = currentSongId == track.songId,
                        onClick = { vm.play(track) },
                        trailing = {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    TimeFormat.formatDateTime(track.playedAt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                IconButton(onClick = { vm.delete(track) }) {
                                    Icon(
                                        Icons.Filled.DeleteOutline, "删除",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(0.dp)
                                    )
                                }
                            }
                        }
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}
