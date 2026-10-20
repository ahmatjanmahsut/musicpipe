package com.musicpipe.player.ui.favorite

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.lifecycle.viewmodel.compose.viewModel
import com.musicpipe.player.di.LocalAppContainer
import com.musicpipe.player.ui.components.EmptyBlock
import com.musicpipe.player.ui.components.SongListItem
import com.musicpipe.player.ui.theme.DsoColors

/** 我喜欢 page (requirement 4): local Room list, works fully offline. */
@Composable
fun FavoriteScreen() {
    val container = LocalAppContainer.current
    val vm: FavoriteViewModel = viewModel(factory = FavoriteViewModel.factory(container))
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    // Only the current song id (distinct): the 500ms position ticks must not
    // recompose this list while playing (v1.4.0 jank fix).
    val currentSongId by remember {
        container.playerRepository.state
            .map { it.currentSong?.id }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = null)

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Favorite, null, tint = DsoColors.AppRed, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                "我喜欢", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp)
            )
            Spacer(Modifier.weight(1f))
            Text(
                "${favorites.size} 首",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (favorites.isEmpty()) {
            EmptyBlock("还没有收藏歌曲\n在播放页点击 ♥ 即可收藏到本地")
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(favorites, key = { _, t -> t.songId }) { index, track ->
                    SongListItem(
                        song = container.favoriteRepository.toSong(track),
                        isCurrentPlaying = currentSongId == track.songId,
                        onClick = { vm.play(index) },
                        trailing = {
                            IconButton(onClick = { vm.remove(track) }) {
                                Icon(Icons.Filled.Favorite, "取消收藏", tint = DsoColors.AppRed)
                            }
                        }
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}
