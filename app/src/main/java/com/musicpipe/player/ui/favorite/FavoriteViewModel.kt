package com.musicpipe.player.ui.favorite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.musicpipe.player.data.local.FavoriteTrack
import com.musicpipe.player.data.model.Song
import com.musicpipe.player.di.AppContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Requirement 4: 我喜欢 local favourites, offline-capable, unfavourite supported. */
class FavoriteViewModel(private val container: AppContainer) : ViewModel() {

    /**
     * v1.10.0（P5）：一次算好「行 + 可播放 Song 队列」，点播放时不再全表转换。
     *
     * 原来 play(index) 里每次都 rows.map { toSong(it) }，收藏有几百首时点一下就要
     * 转换几百个对象+拼 stream URL，点击到出声之间能感觉到延迟。
     * 现在在数据源变化时算一次，播放时直接用现成队列（队列本身仍然完整，
     * 上一首/下一首才能跨整个收藏列表工作）。
     *
     * 注意只用**一个** stateIn 订阅 Room：favorites 由它派生，避免开两个订阅
     * 让同一张表被查两遍。
     */
    private data class FavState(val rows: List<FavoriteTrack>, val songs: List<Song>)

    private val favState: StateFlow<FavState> = container.favoriteRepository.observeAll()
        .map { rows -> FavState(rows, rows.map { container.favoriteRepository.toSong(it) }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FavState(emptyList(), emptyList()))

    val favorites: StateFlow<List<FavoriteTrack>> = favState
        .map { it.rows }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun remove(track: FavoriteTrack) {
        viewModelScope.launch { container.favoriteRepository.remove(track.songId) }
    }

    fun play(index: Int) {
        val state = favState.value
        if (index !in state.songs.indices) return
        container.playerRepository.playQueue(state.songs, index)
    }

    companion object {
        fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = FavoriteViewModel(container) as T
        }
    }
}
