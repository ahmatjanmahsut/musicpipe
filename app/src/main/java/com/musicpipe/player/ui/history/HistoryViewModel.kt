package com.musicpipe.player.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.musicpipe.player.data.local.HistoryTrack
import com.musicpipe.player.di.AppContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Requirement 2: local history list / single delete / clear-all (Room-persisted). */
class HistoryViewModel(private val container: AppContainer) : ViewModel() {

    val tracks: StateFlow<List<HistoryTrack>> = container.historyRepository.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun delete(track: HistoryTrack) {
        viewModelScope.launch { container.historyRepository.deleteById(track.id) }
    }

    fun clearAll() {
        viewModelScope.launch { container.historyRepository.clear() }
    }

    fun play(track: HistoryTrack) {
        container.playerRepository.playSong(container.historyRepository.toSong(track))
    }

    companion object {
        fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = HistoryViewModel(container) as T
        }
    }
}
