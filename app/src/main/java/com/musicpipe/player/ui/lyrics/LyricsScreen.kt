package com.musicpipe.player.ui.lyrics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.musicpipe.player.di.LocalAppContainer
import com.musicpipe.player.ui.components.EmptyBlock
import com.musicpipe.player.ui.components.ErrorBanner
import com.musicpipe.player.ui.components.LoadingBlock
import com.musicpipe.player.ui.theme.DsoColors
import com.musicpipe.player.util.LrcParser

/**
 * Synced lyrics page (requirement 3, docs/03 §歌词页): auto-scroll with the
 * current line highlighted (#050505 bold), others #6C6C6C. When matching fails
 * an explicit degrade banner is shown and playback is NOT affected.
 */
@Composable
fun LyricsScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: LyricsViewModel = viewModel(factory = LyricsViewModel.factory(container))
    val ui by vm.state.collectAsStateWithLifecycle()
    val playerState by container.playerRepository.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = MaterialTheme.colorScheme.onSurface)
            }
            Text(
                "歌词", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface
            )
        }

        when (val s = ui) {
            LyricsViewModel.UiState.Idle -> EmptyBlock("暂无播放中的歌曲")
            LyricsViewModel.UiState.Loading -> LoadingBlock()
            LyricsViewModel.UiState.NotFound -> Column {
                ErrorBanner("未匹配到《${playerState.currentSong?.title ?: ""}》的歌词，播放不受影响")
                Spacer(Modifier.height(8.dp))
                EmptyBlock("纯音乐或歌词缺失")
            }
            is LyricsViewModel.UiState.Error -> Column {
                ErrorBanner("歌词加载失败：${s.message}（播放不受影响）")
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TextButton(onClick = { vm.retry() }) { Text("重试", color = DsoColors.AppRed) }
                }
            }
            is LyricsViewModel.UiState.Found -> {
                val activeIndex = LrcParser.activeIndex(s.lines, playerState.positionMs)
                LaunchedEffect(activeIndex) {
                    if (activeIndex >= 0) {
                        // +1: LazyColumn item 0 is the 220dp top spacer
                        listState.animateScrollToItem(activeIndex + 1)
                    }
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    item { Spacer(Modifier.height(220.dp)) }
                    itemsIndexed(s.lines) { index, line ->
                        val active = index == activeIndex
                        Text(
                            text = line.text,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 32.dp, vertical = 8.dp),
                            textAlign = TextAlign.Center,
                            fontSize = if (active) 18.sp else 15.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                            color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    item { Spacer(Modifier.height(260.dp)) }
                }
            }
        }
    }
}
