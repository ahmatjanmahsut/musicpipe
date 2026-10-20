package com.musicpipe.player.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.musicpipe.player.data.model.Song
import com.musicpipe.player.di.AppContainer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SearchViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val query: String = "",
        val searching: Boolean = false,
        val results: List<Song> = emptyList(),
        val searched: Boolean = false,
        val error: String? = null
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state
    private var job: Job? = null

    fun onQueryChange(q: String) {
        _state.value = _state.value.copy(query = q)
        job?.cancel()
        if (q.isBlank()) {
            _state.value = UiState(query = q)
            return
        }
        job = viewModelScope.launch {
            delay(400) // debounce
            search(q)
        }
    }

    fun search(q: String) {
        if (q.isBlank()) return
        _state.value = _state.value.copy(searching = true, error = null)
        viewModelScope.launch {
            try {
                val songs = container.songRepository.search(q.trim())
                _state.value = _state.value.copy(searching = false, results = songs, searched = true)
            } catch (t: Throwable) {
                _state.value = _state.value.copy(searching = false, error = t.message ?: "搜索失败", searched = true)
            }
        }
    }

    fun play(index: Int) {
        val songs = _state.value.results
        if (index in songs.indices) container.playerRepository.playQueue(songs, index)
    }

    companion object {
        fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = SearchViewModel(container) as T
        }
    }
}
