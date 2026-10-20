package com.musicpipe.player.ui.lyrics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.musicpipe.player.data.model.LyricLine
import com.musicpipe.player.data.model.Song
import com.musicpipe.player.data.repo.LyricsRepository
import com.musicpipe.player.di.AppContainer
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Requirement 3: auto-match network lyrics for the current song; degrade gracefully. */
class LyricsViewModel(private val container: AppContainer) : ViewModel() {

    sealed class UiState {
        data object Idle : UiState()
        data object Loading : UiState()
        data class Found(val lines: List<LyricLine>, val source: String) : UiState()
        /** No lyrics matched — explicit degrade banner, playback unaffected. */
        data object NotFound : UiState()
        data class Error(val message: String) : UiState()
    }

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state

    private var loadedFor: String? = null
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            container.playerRepository.state
                .map { it.currentSong }
                .distinctUntilChanged()
                .collect { song -> if (song != null) load(song) }
        }
    }

    fun load(song: Song) {
        if (loadedFor == song.id && _state.value is UiState.Found) return
        loadedFor = song.id
        // v1.7.0：先别急着切到 Loading。歌词现在有内存 + SQLite 两级缓存，
        // 命中时 fetch 会立刻返回，此时若先置 Loading 反而会让用户看到一帧转圈再跳到内容
        // （视觉上像"卡了一下"）。这里保留上一次的内容直到新结果到达：切歌瞬间仍是旧歌词，
        // 通常一两毫秒后就被替换 —— 比闪一下 Loading 干净得多。
        if (_state.value !is UiState.Found) _state.value = UiState.Loading
        loadJob?.cancel()  // rapid song switching: never let a stale fetch overwrite
        loadJob = viewModelScope.launch {
            val target = song.id
            val r = container.lyricsRepository.fetch(song)
            if (loadedFor != target) return@launch
            _state.value = when (r) {
                is LyricsRepository.LyricsResult.Found -> UiState.Found(r.lines, r.source)
                LyricsRepository.LyricsResult.NotFound -> UiState.NotFound
                is LyricsRepository.LyricsResult.Error -> UiState.Error(r.message)
            }
        }
    }

    fun retry() {
        val song = container.playerRepository.state.value.currentSong ?: return
        loadedFor = null
        load(song)
    }

    companion object {
        fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = LyricsViewModel(container) as T
        }
    }
}
