package com.musicpipe.player.ui.favorite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.musicpipe.player.data.local.FavoriteTrack
import com.musicpipe.player.di.AppContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Requirement 4: 「我喜欢」 local favourites, offline-capable, unfavourite supported. */
class FavoriteViewModel(private val container: AppContainer) : ViewModel() {

    val favorites: StateFlow<List<FavoriteTrack>> = container.favoriteRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun remove(track: FavoriteTrack) {
        viewModelScope.launch { container.favoriteRepository.remove(track.songId) }
    }

    fun play(index: Int) {
        val rows = favorites.value
        if (index !in rows.indices) return
        val songs = rows.map { container.favoriteRepository.toSong(it) }
        container.playerRepository.playQueue(songs, index)
    }

    companion object {
        fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = FavoriteViewModel(container) as T
        }
    }
}
