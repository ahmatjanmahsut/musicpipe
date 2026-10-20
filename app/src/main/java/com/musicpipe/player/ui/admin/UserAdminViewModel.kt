package com.musicpipe.player.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.musicpipe.player.data.remote.ApiException
import com.musicpipe.player.data.remote.dto.UserRowDto
import com.musicpipe.player.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Admin user CRUD + role assignment (requirement 1, docs/01 §3.3). */
class UserAdminViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val loading: Boolean = false,
        val items: List<UserRowDto> = emptyList(),
        val total: Int = 0,
        val page: Int = 1,
        val limit: Int = 20,
        val error: String? = null
    ) {
        val pages: Int get() = if (total <= 0) 1 else ((total + limit - 1) / limit)
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    init { load(1) }

    fun load(page: Int) {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                val r = container.userRepository.list(page, _state.value.limit)
                _state.value = _state.value.copy(loading = false, items = r.items, total = r.total, page = page)
            } catch (t: Throwable) {
                _state.value = _state.value.copy(loading = false, error = humanize(t, "加载失败"))
            }
        }
    }

    fun create(username: String, password: String, role: String, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                container.userRepository.create(username.trim(), password, role)
                onDone(null); load(_state.value.page)
            } catch (t: Throwable) { onDone(humanize(t, "创建失败")) }
        }
    }

    fun update(id: Long, password: String?, role: String?, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                container.userRepository.update(id, password?.takeIf { it.isNotBlank() }, role)
                onDone(null); load(_state.value.page)
            } catch (t: Throwable) { onDone(humanize(t, "更新失败")) }
        }
    }

    fun delete(id: Long, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                container.userRepository.delete(id)
                onDone(null); load(_state.value.page)
            } catch (t: Throwable) { onDone(humanize(t, "删除失败")) }
        }
    }

    /** Gateway machine codes (accounts / users routes) -> readable hints. */
    private fun humanize(t: Throwable, fallback: String): String = when {
        t is ApiException -> when (t.code) {
            "username_exists" -> "用户名已被占用"
            "invalid_username" -> "用户名不合法（3-32 位字母、数字、下划线或中文）"
            "invalid_password" -> "密码至少 6 位"
            "self_delete" -> "不能删除当前登录的账号"
            "forbidden" -> "需要管理员权限"
            "unauthorized" -> "登录状态已失效，请重新登录"
            "user_not_found" -> "用户不存在"
            else -> t.message
        }
        else -> t.message ?: fallback
    }

    companion object {
        fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = UserAdminViewModel(container) as T
        }
    }
}
