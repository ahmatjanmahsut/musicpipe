package com.musicpipe.player.ui.search

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.musicpipe.player.di.LocalAppContainer
import com.musicpipe.player.ui.components.LoadingBlock
import com.musicpipe.player.ui.components.SongListItem
import com.musicpipe.player.ui.design.AwwEmpty
import com.musicpipe.player.ui.design.AwwLabel
import com.musicpipe.player.ui.design.AwwMono
import com.musicpipe.player.ui.design.AwwRule
import com.musicpipe.player.ui.theme.AwwColors
import com.musicpipe.player.ui.theme.AwwSpace
import com.musicpipe.player.ui.theme.AwwTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * 曲库搜索（v1.12.0 按 DESIGN.md 重构）。
 *
 * 设计取舍：
 * - 输入框不用 M3 的填充胶囊（规范禁大圆角 + 灰底药丸），改成"下划线式"编辑排版：
 *   1dp 底边 + mono 前缀编号 + 无容器。这样焦点态只需把底边换成强调色，符合 §6 焦点态。
 * - 结果列表沿用 [SongListItem]（已含 1dp 细线分隔），页面本身不加任何卡片。
 *
 * 性能约定（v1.4.0 起，勿破坏）：只订阅当前曲目 id，500ms 位置心跳不得带动列表重组。
 */
@Composable
fun SearchScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: SearchViewModel = viewModel(factory = SearchViewModel.factory(container))
    val state by vm.state.collectAsStateWithLifecycle()
    val currentSongId by remember {
        container.playerRepository.state
            .map { it.currentSong?.id }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = null)

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
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
                modifier = Modifier
                    .padding(end = AwwSpace.s4)
                    .clickable(onClick = onBack)
            )
            AwwLabel(text = "SEARCH", color = AwwColors.Ink3)
            Spacer(Modifier.weight(1f))
            AwwMono(text = "02", color = AwwColors.Ink3)
        }
        AwwRule()

        // 下划线输入框
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = AwwSpace.s6, vertical = AwwSpace.s4),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AwwMono(text = ">", color = AwwColors.Accent)
            Box(Modifier.weight(1f).padding(start = AwwSpace.s3)) {
                if (state.query.isEmpty()) {
                    Text(
                        text = "歌曲、歌手",
                        style = AwwTheme.type.body,
                        color = AwwColors.Ink3
                    )
                }
                BasicTextField(
                    value = state.query,
                    onValueChange = vm::onQueryChange,
                    singleLine = true,
                    textStyle = AwwTheme.type.body.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(AwwColors.Accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { vm.search(state.query) }),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        AwwRule()

        when {
            state.searching && state.results.isEmpty() -> LoadingBlock()
            state.error != null -> Column(Modifier.padding(horizontal = AwwSpace.s6)) {
                Spacer(Modifier.height(AwwSpace.s12))
                AwwEmpty(index = "ERR", title = "搜索失败", hint = state.error ?: "")
            }
            state.searched && state.results.isEmpty() -> Column(Modifier.padding(horizontal = AwwSpace.s6)) {
                Spacer(Modifier.height(AwwSpace.s12))
                AwwEmpty(index = "000", title = "没有找到", hint = "换个关键词试试")
            }
            !state.searched -> Column(Modifier.padding(horizontal = AwwSpace.s6)) {
                Spacer(Modifier.height(AwwSpace.s12))
                AwwEmpty(index = "—", title = "输入关键词", hint = "支持歌名与歌手")
            }
            else -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = AwwSpace.s6)) {
                itemsIndexed(
                    state.results,
                    key = { i, s -> s.id + "-" + i },
                    contentType = { _, _ -> "song" }
                ) { index, song ->
                    SongListItem(
                        song = song,
                        isCurrentPlaying = currentSongId == song.id,
                        onClick = { vm.play(index) }
                    )
                }
                item(key = "bottom_pad", contentType = "spacer") {
                    Spacer(Modifier.height(AwwSpace.s24))
                }
            }
        }
    }
}
