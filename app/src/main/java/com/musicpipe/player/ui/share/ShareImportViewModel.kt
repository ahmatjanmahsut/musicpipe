package com.musicpipe.player.ui.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.musicpipe.player.data.remote.dto.SharePreviewResponse
import com.musicpipe.player.data.remote.dto.ShareSongDto
import com.musicpipe.player.data.remote.dto.ShareListItem
import com.musicpipe.player.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 分享歌单导入: parse a NetEase/QQ playlist share URL (POST /api/v1/shares),
 * preview the track list, commit selected keys into the download queue, and
 * manage subscriptions (list / force-sync / songs / delete).
 */
class ShareImportViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val tab: Int = 0,
        val url: String = "",
        val quality: String = "flac",
        val parsing: Boolean = false,
        val parseError: String? = null,
        val preview: SharePreviewResponse? = null,
        val selected: Set<String> = emptySet(),
        val committing: Boolean = false,
        val history: List<ShareListItem> = emptyList(),
        val historyLoading: Boolean = false,
        val historyError: String? = null,
        val expandedId: String? = null,
        val expandedSongs: List<ShareSongDto> = emptyList(),
        val busyId: String? = null,
        val toast: String? = null
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    init { loadHistory() }

    fun setTab(i: Int) { _state.value = _state.value.copy(tab = i) }
    fun onUrlChange(u: String) { _state.value = _state.value.copy(url = u) }
    fun setQuality(q: String) { _state.value = _state.value.copy(quality = q) }
    fun consumeToast() { _state.value = _state.value.copy(toast = null) }

    /** ext_keys eligible for commit: not already in library and not yet queued/done. */
    private fun selectableKeys(preview: SharePreviewResponse?): Set<String> =
        (preview?.songs ?: emptyList())
            .filter { it.inLib == 0 && (it.state == null || it.state == "preview" || it.state == "failed") }
            .map { it.extKey }.toSet()

    fun parse() {
        val url = _state.value.url.trim()
        if (url.isEmpty() || _state.value.parsing) return
        _state.value = _state.value.copy(parsing = true, parseError = null)
        viewModelScope.launch {
            try {
                val link = container.netRepository.createShare(url, _state.value.quality)
                val preview = container.netRepository.sharePreview(link.id)
                _state.value = _state.value.copy(
                    parsing = false, preview = preview, selected = selectableKeys(preview),
                    parseError = if (preview.songs.isEmpty()) "该链接未解析出歌曲" else null
                )
                loadHistory()
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    parsing = false, parseError = t.message ?: "解析失败",
                    preview = null, selected = emptySet()
                )
            }
        }
    }

    fun toggleSong(key: String) {
        val cur = _state.value.selected
        _state.value = _state.value.copy(selected = if (key in cur) cur - key else cur + key)
    }

    fun selectAll() {
        val s = _state.value
        val all = selectableKeys(s.preview)
        _state.value = s.copy(selected = if (s.selected.size == all.size && all.isNotEmpty()) emptySet() else all)
    }

    fun commit() {
        val s = _state.value
        val id = s.preview?.link?.id ?: return
        if (s.selected.isEmpty() || s.committing) return
        _state.value = s.copy(committing = true)
        viewModelScope.launch {
            try {
                val ack = container.netRepository.commitShare(id, s.selected.toList(), s.quality)
                _state.value = _state.value.copy(committing = false, toast = "已入队 " + ack.queued + " 首下载")
                refreshPreview(id)
                loadHistory()
            } catch (t: Throwable) {
                _state.value = _state.value.copy(committing = false, toast = t.message ?: "提交失败")
            }
        }
    }

    private fun refreshPreview(id: String) {
        viewModelScope.launch {
            try {
                val preview = container.netRepository.sharePreview(id)
                val keys = selectableKeys(preview)
                _state.value = _state.value.copy(
                    preview = preview,
                    selected = _state.value.selected.intersect(keys)
                )
            } catch (_: Throwable) {
                // stale preview is fine — history tab shows the truth
            }
        }
    }

    fun loadHistory() {
        _state.value = _state.value.copy(historyLoading = true)
        viewModelScope.launch {
            try {
                val list = container.netRepository.shareList()
                _state.value = _state.value.copy(historyLoading = false, history = list, historyError = null)
            } catch (t: Throwable) {
                _state.value = _state.value.copy(historyLoading = false, historyError = t.message ?: "加载失败")
            }
        }
    }

    fun toggleDetail(id: String) {
        if (_state.value.expandedId == id) {
            _state.value = _state.value.copy(expandedId = null, expandedSongs = emptyList())
            return
        }
        _state.value = _state.value.copy(expandedId = id, expandedSongs = emptyList(), busyId = id)
        viewModelScope.launch {
            try {
                val songs = container.netRepository.shareSongs(id)
                _state.value = _state.value.copy(expandedSongs = songs, busyId = null)
            } catch (t: Throwable) {
                _state.value = _state.value.copy(busyId = null, toast = t.message ?: "曲目加载失败")
            }
        }
    }

    fun sync(id: String) {
        if (_state.value.busyId != null) return
        _state.value = _state.value.copy(busyId = id)
        viewModelScope.launch {
            try {
                val r = container.netRepository.syncShare(id, force = true)
                val msg = if (r.ok) "同步完成：新增 " + r.added + " / 已在库 " + r.exists + " / 共 " + r.total
                else (r.error ?: "同步失败")
                _state.value = _state.value.copy(busyId = null, toast = msg)
                loadHistory()
            } catch (t: Throwable) {
                _state.value = _state.value.copy(busyId = null, toast = t.message ?: "同步失败")
            }
        }
    }

    fun delete(id: String) {
        if (_state.value.busyId != null) return
        _state.value = _state.value.copy(busyId = id)
        viewModelScope.launch {
            try {
                container.netRepository.deleteShare(id)
                _state.value = _state.value.copy(
                    busyId = null, toast = "已删除",
                    expandedId = if (_state.value.expandedId == id) null else _state.value.expandedId,
                    expandedSongs = if (_state.value.expandedId == id) emptyList() else _state.value.expandedSongs
                )
                loadHistory()
            } catch (t: Throwable) {
                _state.value = _state.value.copy(busyId = null, toast = t.message ?: "删除失败")
            }
        }
    }

    companion object {
        fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ShareImportViewModel(container) as T
        }
    }
}
