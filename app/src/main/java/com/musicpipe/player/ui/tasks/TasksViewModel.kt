package com.musicpipe.player.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.musicpipe.player.data.remote.dto.TaskDto
import com.musicpipe.player.di.AppContainer
import com.musicpipe.player.util.GatewayUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Download queue over GET /api/v1/tasks + bulk cancel/delete (screen polls every 3s). */
class TasksViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val loading: Boolean = false,
        val tasks: List<TaskDto> = emptyList(),
        val error: String? = null,
        val busy: Boolean = false,
        val toast: String? = null
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    init { refresh() }

    fun refresh() {
        if (_state.value.tasks.isEmpty() && !_state.value.loading) {
            _state.value = _state.value.copy(loading = true)
        }
        viewModelScope.launch {
            try {
                val tasks = container.netRepository.tasks()
                _state.value = _state.value.copy(loading = false, tasks = tasks, error = null)
            } catch (t: Throwable) {
                _state.value = _state.value.copy(loading = false, error = t.message ?: "加载失败")
            }
        }
    }

    fun cancelActive() = bulk(_state.value.tasks.filter { GatewayUtil.isActive(it.status) }.map { it.id }, cancel = true)

    fun clearFinished() = bulk(_state.value.tasks.filter { GatewayUtil.isRemovable(it.status) }.map { it.id }, cancel = false)

    fun cancelOne(id: String) = bulk(listOf(id), cancel = true)

    fun removeOne(id: String) = bulk(listOf(id), cancel = false)

    private fun bulk(ids: List<String>, cancel: Boolean) {
        if (ids.isEmpty() || _state.value.busy) return
        _state.value = _state.value.copy(busy = true)
        viewModelScope.launch {
            try {
                val msg = if (cancel) {
                    "已取消 " + container.netRepository.cancelTasks(ids).cancelled + " 个任务"
                } else {
                    "已清除 " + container.netRepository.deleteTasks(ids).deleted + " 条记录"
                }
                _state.value = _state.value.copy(busy = false, toast = msg)
                refresh()
            } catch (t: Throwable) {
                _state.value = _state.value.copy(busy = false, toast = t.message ?: "操作失败")
            }
        }
    }

    fun consumeToast() { _state.value = _state.value.copy(toast = null) }

    companion object {
        fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = TasksViewModel(container) as T
        }
    }
}
