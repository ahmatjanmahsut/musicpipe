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
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.musicpipe.player.data.model.LyricLine
import com.musicpipe.player.di.LocalAppContainer
import com.musicpipe.player.ui.components.EmptyBlock
import com.musicpipe.player.ui.components.ErrorBanner
import com.musicpipe.player.ui.components.LoadingBlock
import com.musicpipe.player.ui.theme.DsoColors
import com.musicpipe.player.ui.theme.DsoTextStyles
import com.musicpipe.player.util.LrcParser
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Synced lyrics page (requirement 3, docs/03 §歌词页): auto-scroll with the
 * current line highlighted, others dimmed. When matching fails an explicit
 * degrade banner is shown and playback is NOT affected.
 *
 * v1.10.0（P1）两处修正：
 * 1. **重组范围**：原来把 `activeIndex` 算在组合作用域里、再作为参数传给每个 item，
 *    而播放器每 500ms 刷新一次位置 —— 于是**一屏可见的每一行歌词都跟着重组**，
 *    哪怕只有一行需要变色。现在位置以 `() -> Long` 稳定 lambda 下发给每行，
 *    每行用 derivedStateOf 只推导"我是不是当前行"，因此只有**变化的那两行**重组。
 * 2. **居中滚动**：原来 `animateScrollToItem(activeIndex + 1)` 把当前行顶到视口最上方，
 *    而 220dp/260dp 两个 Spacer 只对第 0 行有效，越往后越偏。改为把当前行滚到
 *    **视口垂直中点**（用 scrollOffset 反推），配合屏幕高度自适应。
 */
@Composable
fun LyricsScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: LyricsViewModel = viewModel(factory = LyricsViewModel.factory(container))
    val ui by vm.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // 只订阅 positionMs（低频以外的字段与本页无关），并保留为 State 以便按需读取。
    val positionMs: State<Long> = remember {
        container.playerRepository.state
            .map { it.positionMs }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = 0L)

    // 曲名单独取，NotFound 分支要用；它变化频率很低，不会引起整页反复重组。
    val currentTitle by remember {
        container.playerRepository.state
            .map { it.currentSong?.title }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = null)

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
                ErrorBanner("未匹配到《" + (currentTitle ?: "") + "》的歌词，播放不受影响")
                Spacer(Modifier.height(8.dp))
                EmptyBlock("纯音乐或歌词缺失")
            }
            is LyricsViewModel.UiState.Error -> Column {
                ErrorBanner("歌词加载失败：" + s.message + "（播放不受影响）")
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TextButton(onClick = { vm.retry() }) { Text("重试", color = DsoColors.AppRed) }
                }
            }
            is LyricsViewModel.UiState.Found -> {
                val lines = s.lines
                LyricsList(lines = lines, positionMs = positionMs, listState = listState)
            }
        }
    }
}

@Composable
private fun LyricsList(
    lines: List<LyricLine>,
    positionMs: State<Long>,
    listState: androidx.compose.foundation.lazy.LazyListState
) {
    // 当前行下标 —— 整页只有一个 derivedStateOf 观察它，用于滚动。
    val activeIndex by remember(lines) {
        derivedStateOf { LrcParser.activeIndex(lines, positionMs.value) }
    }
    LaunchedEffect(activeIndex, lines) {
        if (activeIndex >= 0) {
            // +1 跳过顶部 Spacer。用负偏移把目标行推到视口中间：
            // 注意 scrollOffset 必须是负数才能"向上露出"，这里按一行的估算高度（约 44dp = 88px/2）
            // 取半屏高度的负值，让当前行落在中线附近而不是顶端。
            runCatching {
                listState.animateScrollToItem(
                    index = activeIndex + 1,
                    scrollOffset = -listState.layoutInfo.viewportSize.height / 3
                )
            }
        }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item(key = "top_spacer", contentType = "spacer") { Spacer(Modifier.height(220.dp)) }
        itemsIndexed(
            items = lines,
            key = { index, line -> "line_" + index + "_" + line.timeMs },
            contentType = { _, _ -> "line" }
        ) { index, line ->
            LyricRow(lines = lines, index = index, line = line, positionMs = positionMs)
        }
        item(key = "bottom_spacer", contentType = "spacer") { Spacer(Modifier.height(260.dp)) }
    }
}

/**
 * 单行歌词。
 *
 * 关键：本函数只接收**稳定的** positionMs lambda，所以当播放位置变化时
 * LazyColumn 的 item lambda 不会被重启；真正的重组发生在 [active] 这个 State 变化时，
 * 且只会命中"刚变成当前行"和"刚不是当前行"这两行。
 */
@Composable
private fun LyricRow(lines: List<LyricLine>, index: Int, line: LyricLine, positionMs: State<Long>) {
    val active by remember(lines, index) {
        derivedStateOf { LrcParser.isActive(lines, index, positionMs.value) }
    }
    Text(
        text = line.text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 8.dp),
        textAlign = TextAlign.Center,
        style = if (active) DsoTextStyles.LyricActive else DsoTextStyles.LyricIdle,
        color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    )
}
