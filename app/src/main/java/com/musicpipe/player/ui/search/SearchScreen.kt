package com.musicpipe.player.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.lifecycle.viewmodel.compose.viewModel
import com.musicpipe.player.di.LocalAppContainer
import com.musicpipe.player.ui.components.AppTextField
import com.musicpipe.player.ui.components.EmptyBlock
import com.musicpipe.player.ui.components.ErrorBanner
import com.musicpipe.player.ui.components.LoadingBlock
import com.musicpipe.player.ui.components.SongListItem
import com.musicpipe.player.ui.theme.DsoColors
import com.musicpipe.player.ui.theme.DsoShapes

/** Search page (docs/01 §4 search3.view): rounded #F5F5F5 input, DsoMusic style. */
@Composable
fun SearchScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: SearchViewModel = viewModel(factory = SearchViewModel.factory(container))
    val state by vm.state.collectAsStateWithLifecycle()
    // Only the current song id (distinct): the 500ms position ticks must not
    // recompose this list while playing (v1.4.0 jank fix).
    val currentSongId by remember {
        container.playerRepository.state
            .map { it.currentSong?.id }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = null)

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = MaterialTheme.colorScheme.onSurface)
            }
            AppTextField(
                value = state.query,
                onValueChange = vm::onQueryChange,
                placeholder = { Text("搜索歌曲、歌手") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.search(state.query) }),
                shape = DsoShapes.Pill,
                modifier = Modifier.weight(1f)
            )
        }
        when {
            state.searching && state.results.isEmpty() -> LoadingBlock()
            state.error != null -> ErrorBanner("搜索失败：${state.error}")
            state.searched && state.results.isEmpty() -> EmptyBlock("没有找到相关歌曲")
            !state.searched -> EmptyBlock("输入关键词开始搜索")
            else -> LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(
                    state.results,
                    key = { i, s -> "${s.id}-$i" },
                    // v1.10.0（P10）：声明 contentType，让 Lazy 只复用同类槽位；
                    // 缺省时所有条目同类型，滚动中 reuse 更频繁、组合开销更大。
                    contentType = { _, _ -> "song" }
                ) { index, song ->
                    SongListItem(
                        song = song,
                        isCurrentPlaying = currentSongId == song.id,
                        onClick = { vm.play(index) }
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}