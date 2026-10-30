package com.musicpipe.player.ui.favorite

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.musicpipe.player.di.LocalAppContainer
import com.musicpipe.player.ui.components.SongListItem
import com.musicpipe.player.ui.design.AwwEmpty
import com.musicpipe.player.ui.design.AwwLabel
import com.musicpipe.player.ui.design.AwwPageHeader
import com.musicpipe.player.ui.theme.AwwColors
import com.musicpipe.player.ui.theme.AwwSpace
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * 我喜欢（需求4：本地 Room，离线完全可用）。
 *
 * v1.12.0 视觉：页眉走 [AwwPageHeader]；取消收藏从"红♥图标按钮"改成
 * 行尾的 uppercase 小标签 —— 一屏十几个实心红♥会把唯一强调色的面积预算（≤5%）打穿。
 *
 * 性能约定（v1.4.0 起，勿破坏）：只订阅当前曲目 id。
 */
@Composable
fun FavoriteScreen() {
    val container = LocalAppContainer.current
    val vm: FavoriteViewModel = viewModel(factory = FavoriteViewModel.factory(container))
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val currentSongId by remember {
        container.playerRepository.state
            .map { it.currentSong?.id }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = null)

    Column(Modifier.fillMaxSize()) {
        AwwPageHeader(
            index = "05",
            label = "FAVORITES",
            title = "我喜欢",
            meta = if (favorites.isEmpty()) "—" else favorites.size.toString().padStart(2, '0') + " 首 · 本地保存，离线可播"
        )
        if (favorites.isEmpty()) {
            AwwEmpty(
                index = "—",
                title = "还没有收藏",
                hint = "在播放页点击 ♥ 即可收藏，收藏只存在本机，离线也能看到。"
            )
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = AwwSpace.s6)) {
                itemsIndexed(
                    favorites,
                    key = { _, t -> t.songId },
                    contentType = { _, _ -> "favorite" }
                ) { index, track ->
                    // v1.14.0：同 HistoryScreen —— 每帧重建 Song 与带 token 的 URL 太重。
                    val song = remember(track) { container.favoriteRepository.toSong(track) }
                    SongListItem(
                        song = song,
                        isCurrentPlaying = currentSongId == track.songId,
                        onClick = { vm.play(index) },
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.Favorite, null,
                                    tint = AwwColors.Accent,
                                    modifier = Modifier.height(14.dp)
                                )
                                AwwLabel(
                                    text = "移除",
                                    color = AwwColors.Ink3,
                                    modifier = Modifier
                                        .padding(start = AwwSpace.s2)
                                        .clickable { vm.remove(track) }
                                )
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
