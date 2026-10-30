package com.musicpipe.player.ui.lyrics

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.musicpipe.player.data.model.LyricLine
import com.musicpipe.player.di.LocalAppContainer
import com.musicpipe.player.ui.components.LoadingBlock
import com.musicpipe.player.ui.design.AwwLabel
import com.musicpipe.player.ui.design.AwwMono
import com.musicpipe.player.ui.design.AwwRule
import com.musicpipe.player.ui.design.AwwEmpty
import com.musicpipe.player.ui.theme.AwwColors
import com.musicpipe.player.ui.theme.AwwSpace
import com.musicpipe.player.ui.theme.AwwTheme
import com.musicpipe.player.util.LrcParser
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * 同步歌词页（需求3）。自动滚动、当前行高亮，匹配失败时显式降级提示且**不影响播放**。
 *
 * v1.10.0（P1）两条性能约定保留（勿破坏）：
 * 1. 位置以 `State<Long>` 下发给每行，每行用 derivedStateOf 只推导"我是不是当前行"，
 *    于是 500ms 心跳只会让**变化的两行**重组，而不是一屏所有行。
 * 2. 当前行滚到视口垂直中点（用 scrollOffset 反推），220dp/260dp 两个 Spacer 只服务第 0 行。
 *
 * v1.12.0 视觉：标题改为"编号 + 大写标签"，当前行用强调色 + 左侧 2dp 竖线，
 * 其余行降为低对比灰 —— 歌词页的主体是文字本身，不需要任何容器装饰。
 */
@Composable
fun LyricsScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: LyricsViewModel = viewModel(factory = LyricsViewModel.factory(container))
    val ui by vm.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    val positionMs: State<Long> = remember {
        container.playerRepository.state
            .map { it.positionMs }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = 0L)

    val currentTitle by remember {
        container.playerRepository.state
            .map { it.currentSong?.title }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = null)

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = AwwSpace.s6, vertical = AwwSpace.s2),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "‹",
                style = AwwTheme.type.h3,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.clickable(onClick = onBack).padding(end = AwwSpace.s4)
            )
            AwwLabel(text = "LYRICS", color = AwwColors.Ink3)
            Spacer(Modifier.weight(1f))
            AwwMono(text = "03", color = AwwColors.Ink3)
        }
        AwwRule()

        when (val s = ui) {
            LyricsViewModel.UiState.Idle ->
                AwwEmpty(index = "—", title = "暂无播放", hint = "从音乐库选一首开始")
            LyricsViewModel.UiState.Loading -> LoadingBlock()
            LyricsViewModel.UiState.NotFound -> Column(Modifier.padding(horizontal = AwwSpace.s6)) {
                Spacer(Modifier.height(AwwSpace.s12))
                AwwEmpty(
                    index = "404",
                    title = "未匹配到歌词",
                    hint = "《" + (currentTitle ?: "") + "》可能纯音乐或歌词缺失。播放不受影响。"
                )
            }
            is LyricsViewModel.UiState.Error -> Column(Modifier.padding(horizontal = AwwSpace.s6)) {
                Spacer(Modifier.height(AwwSpace.s12))
                AwwEmpty(
                    index = "ERR",
                    title = "歌词加载失败",
                    hint = s.message + "（播放不受影响）"
                )
                Spacer(Modifier.height(AwwSpace.s6))
                Text(
                    text = "重试",
                    style = AwwTheme.type.label,
                    color = AwwColors.Accent,
                    modifier = Modifier.clickable { vm.retry() }.padding(vertical = AwwSpace.s3)
                )
            }
            is LyricsViewModel.UiState.Found -> {
                LyricsList(lines = s.lines, positionMs = positionMs, listState = listState)
            }
        }
    }
}

@Composable
private fun LyricsList(
    lines: List<LyricLine>,
    positionMs: State<Long>,
    listState: LazyListState
) {
    val activeIndex by remember(lines) {
        derivedStateOf { LrcParser.activeIndex(lines, positionMs.value) }
    }
    LaunchedEffect(activeIndex, lines) {
        if (activeIndex >= 0) {
            // +1 跳过顶部 Spacer。用负偏移把目标行推到视口中间：
            // scrollOffset 必须是负数才能"向上露出"，取视口高度 1/3 的负值让当前行落在中线附近。
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
 * 单行歌词。只接收**稳定**的 positionMs State，因此播放位置变化时 LazyColumn 的
 * item lambda 不会重启；真正重组的是 [active] 变化的两行。
 */
@Composable
private fun LyricRow(lines: List<LyricLine>, index: Int, line: LyricLine, positionMs: State<Long>) {
    val active by remember(lines, index) {
        derivedStateOf { LrcParser.isActive(lines, index, positionMs.value) }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = AwwSpace.s6, vertical = AwwSpace.s2),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 当前行左侧 2dp 强调竖线；其余行留同宽空位，避免文字左右跳动。
        ActiveMarker(active)
        Text(
            text = line.text,
            modifier = Modifier
                .weight(1f)
                .padding(start = AwwSpace.s3),
            textAlign = TextAlign.Start,
            style = if (active) AwwTheme.type.body else AwwTheme.type.bodySmall,
            color = if (active) MaterialTheme.colorScheme.onSurface else AwwColors.Ink3
        )
    }
}

/**
 * 当前行左侧标记线：固定 2dp 宽，激活时强调色、否则透明 —— 宽度恒定，
 * 所以文字不会在"变成当前行"时左右跳动。
 */
@Composable
private fun ActiveMarker(active: Boolean) {
    Box(
        Modifier
            .width(2.dp)
            .height(20.dp)
            .background(if (active) AwwColors.Accent else Color.Transparent)
    )
}
